package com.oak.app.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.GZIPInputStream

private const val BUFFER_SIZE = 8192
private const val TAR_BLOCK_SIZE = 512
private const val TAR_NAME_OFFSET = 0
private const val TAR_MODE_OFFSET = 100
private const val TAR_SIZE_OFFSET = 124
private const val TAR_TYPE_OFFSET = 156
private const val TAR_LINK_OFFSET = 157
private const val TAR_PREFIX_OFFSET = 345

class RootfsDownloader(private val httpClient: HttpClient) {

    suspend fun download(
        config: DistroConfig,
        arch: String,
        targetFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val urls = DistroConfigs.resolveUrls(config, arch)
        var lastError: Exception? = null
        for ((index, url) in urls.withIndex()) {
            try {
                downloadFrom(url, targetFile, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (targetFile.exists()) targetFile.delete()
                if (index < urls.lastIndex) onProgress(0f)
            }
        }
        val distroName = config.distro.id
        throw IOException("All mirrors failed for $distroName: $lastError", lastError)
    }

    private suspend fun downloadFrom(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit,
    ) {
        httpClient.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw IOException("HTTP ${response.status.value} from $url")
            }
            val totalBytes = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_SIZE)
            var downloadedBytes = 0L

            FileOutputStream(targetFile).use { output ->
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead <= 0) break
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        onProgress(downloadedBytes.toFloat() / totalBytes)
                    }
                }
            }
        }
    }

    /**
     * Extract a rootfs archive into [targetDir]. Supports both tar.gz and tar.xz.
     * Automatically detects and strips a common top-level wrapper directory
     * (e.g., "debian-trixie-aarch64/") that some distro tarballs use.
     */
    fun extract(archiveFile: File, targetDir: File, compression: Compression) {
        targetDir.mkdirs()
        val decompressed: java.io.InputStream = when (compression) {
            Compression.GZIP -> GZIPInputStream(BufferedInputStream(FileInputStream(archiveFile)))
            Compression.XZ -> XZInputStream(BufferedInputStream(FileInputStream(archiveFile)))
        }
        decompressed.use { stream ->
            extractTar(stream, targetDir)
        }
        // Post-extraction: detect and flatten wrapper directory if /bin/sh is missing
        flattenWrapperDirectory(targetDir)
    }

    /**
     * After extraction, if /bin/sh is not at the root of [targetDir], look for
     * a single top-level subdirectory (the "wrapper") and move its contents up.
     * This handles termux/proot-distro tarballs that wrap everything in a
     * distro-specific directory like "debian-trixie-aarch64/".
     */
    private fun flattenWrapperDirectory(targetDir: File) {
        val shFile = File(targetDir, "bin/sh")
        if (shFile.exists() || Files.isSymbolicLink(shFile.toPath())) return

        // Find the single top-level subdirectory to use as our source
        val entries = targetDir.listFiles()?.filter { it.name != "." && it.name != ".." } ?: return
        // Allow . and lost+found alongside a single subdirectory
        val subdirs = entries.filter { it.isDirectory && it.name != "lost+found" }
        val files = entries.filter { it.isFile }

        if (subdirs.size == 1 && files.isEmpty()) {
            val wrapper = subdirs.first()
            android.util.Log.i("RootfsDownloader", "Detected wrapper directory '${wrapper.name}', flattening...")
            moveContentsUp(wrapper, targetDir)
            // Retry the check after flattening
            if (!File(targetDir, "bin/sh").exists()) {
                android.util.Log.w("RootfsDownloader", "Flattened but /bin/sh still missing")
            }
        } else if (subdirs.isEmpty() && files.isNotEmpty()) {
            android.util.Log.w("RootfsDownloader", "No wrapper dir, but /bin/sh missing")
        } else {
            android.util.Log.w("RootfsDownloader", "Ambiguous rootfs layout: ${subdirs.size} dirs, ${files.size} files")
        }
    }

    private fun moveContentsUp(source: File, target: File) {
        source.listFiles()?.forEach { child ->
            val dest = File(target, child.name)
            // Avoid overwriting target by deleting dest first
            if (dest.exists()) {
                dest.deleteRecursively()
            }
            if (!child.renameTo(dest)) {
                // renameTo can fail across devices; fall back to copy+delete
                child.copyRecursively(dest, overwrite = true)
                child.deleteRecursively()
            }
        }
    }

    private fun extractTar(inputStream: java.io.InputStream, targetDir: File) {
        val headerBuffer = ByteArray(TAR_BLOCK_SIZE)
        val dataBuffer = ByteArray(BUFFER_SIZE)

        while (true) {
            val headerBytesRead = readFully(inputStream, headerBuffer)
            if (headerBytesRead < TAR_BLOCK_SIZE) break

            val name = readTarString(headerBuffer, TAR_NAME_OFFSET, 100)
            if (name.isEmpty()) break

            val prefix = readTarString(headerBuffer, TAR_PREFIX_OFFSET, 155)
            val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name

            val sizeStr = readTarString(headerBuffer, TAR_SIZE_OFFSET, 12)
            val size = if (sizeStr.isNotEmpty()) sizeStr.toLong(8) else 0L

            val modeStr = readTarString(headerBuffer, TAR_MODE_OFFSET, 8)
            val mode = if (modeStr.isNotEmpty()) modeStr.toInt(8) else 0
            val typeFlag = headerBuffer[TAR_TYPE_OFFSET]
            val linkName = readTarString(headerBuffer, TAR_LINK_OFFSET, 100)

            val outFile = File(targetDir, fullName)

            // Path traversal protection using canonical paths
            val targetPath = targetDir.canonicalFile.toPath()
            val outPath = outFile.canonicalFile.toPath()
            if (!outPath.startsWith(targetPath)) {
                skipBytes(inputStream, alignToBlock(size))
                continue
            }

            when (typeFlag.toInt().toChar()) {
                '5', 'D' -> outFile.mkdirs()

                '2' -> {
                    outFile.parentFile?.mkdirs()
                    try {
                        if (outFile.exists()) outFile.delete()
                        java.nio.file.Files.createSymbolicLink(
                            outFile.toPath(),
                            java.nio.file.Paths.get(linkName),
                        )
                    } catch (_: Exception) {
                    }
                }

                '1' -> {
                    val linkTarget = File(targetDir, linkName)
                    outFile.parentFile?.mkdirs()
                    if (linkTarget.exists()) {
                        linkTarget.copyTo(outFile, overwrite = true)
                    }
                }

                '0', '\u0000' -> {
                    outFile.parentFile?.mkdirs()
                    // Skip if path already exists as a directory (some tarballs
                    // mislabel __pycache__ dirs as regular files)
                    if (outFile.exists() && outFile.isDirectory) {
                        skipBytes(inputStream, alignToBlock(size))
                        continue
                    }
                    FileOutputStream(outFile).use { output ->
                        var remaining = size
                        while (remaining > 0) {
                            val toRead = minOf(remaining, dataBuffer.size.toLong()).toInt()
                            val bytesRead = inputStream.read(dataBuffer, 0, toRead)
                            if (bytesRead <= 0) break
                            output.write(dataBuffer, 0, bytesRead)
                            remaining -= bytesRead
                        }
                    }
                    if (mode and 0b001_001_001 != 0) {
                        outFile.setExecutable(true, false)
                    }
                    val padding = alignToBlock(size) - size
                    if (padding > 0) skipBytes(inputStream, padding)
                    continue
                }

                else -> {}
            }

            if (size > 0 && typeFlag.toInt().toChar() != '0' && typeFlag.toInt().toChar() != '\u0000') {
                skipBytes(inputStream, alignToBlock(size))
            }
        }
    }

    private fun readTarString(buffer: ByteArray, offset: Int, length: Int): String {
        val end = minOf(offset + length, buffer.size)
        val nullIndex = (offset until end).firstOrNull { buffer[it] == 0.toByte() } ?: end
        return String(buffer, offset, nullIndex - offset, Charsets.US_ASCII).trim()
    }

    private fun readFully(inputStream: java.io.InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val bytesRead = inputStream.read(buffer, totalRead, buffer.size - totalRead)
            if (bytesRead <= 0) break
            totalRead += bytesRead
        }
        return totalRead
    }

    private fun skipBytes(inputStream: java.io.InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = inputStream.skip(remaining)
            if (skipped <= 0) {
                if (inputStream.read() < 0) break
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun alignToBlock(size: Long): Long {
        val remainder = size % TAR_BLOCK_SIZE
        return if (remainder == 0L) size else size + (TAR_BLOCK_SIZE - remainder)
    }

    fun makeWritable(rootfsDir: File) {
        rootfsDir.walkTopDown().forEach { file ->
            if (file.isDirectory && !file.canWrite()) {
                file.setWritable(true, true)
            }
        }
    }

    fun writeResolvConf(rootfsDir: File) {
        val etcDir = File(rootfsDir, "etc")
        etcDir.mkdirs()
        File(etcDir, "resolv.conf").writeText(
            "nameserver 8.8.8.8\nnameserver 8.8.4.4\n",
        )
    }

    /** Verify that a rootfs is properly extracted by checking for key files. */
    fun verifyRootfs(rootfsDir: File, config: DistroConfig): Boolean {
        if (!rootfsDir.isDirectory) return false
        val shFile = File(rootfsDir, "bin/sh")
        if (!shFile.exists() && !Files.isSymbolicLink(shFile.toPath())) return false
        return config.verificationPaths.all { path ->
            val f = File(rootfsDir, path)
            f.exists() || Files.isSymbolicLink(f.toPath())
        }
    }

    /** Calculate total size of a rootfs directory in bytes. */
    fun diskUsageBytes(rootfsDir: File): Long {
        if (!rootfsDir.isDirectory) return 0
        return rootfsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
