package com.oak.app.sandbox

import com.oak.app.smartTruncate
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_OUTPUT_LENGTH = 15_000
private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val MAX_TIMEOUT_SECONDS = 180L

/** Poll interval (ms) for non-blocking stream reads so a thread blocked in native
 *  read() does not stall ART GC safepoints. */
private const val STREAM_POLL_MS = 50L

class ProotHandle internal constructor(
    private val process: Process,
    private val cancelled: AtomicBoolean,
    private val readerFutures: List<CompletableFuture<Void>>,
) {
    fun isCancelled(): Boolean = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
        process.destroyForcibly()
    }

    fun writeInput(line: String) {
        if (cancelled.get()) return
        runCatching {
            val bytes = (line + "\n").toByteArray()
            process.outputStream.write(bytes)
            process.outputStream.flush()
        }
    }

    fun awaitExit(): Int {
        while (!cancelled.get() && process.isAlive) {
            runCatching { process.waitFor(200, TimeUnit.MILLISECONDS) }
        }
        if (cancelled.get()) return -1
        readerFutures.forEach { runCatching { it.get(500, TimeUnit.MILLISECONDS) } }
        return runCatching { process.exitValue() }.getOrDefault(-1)
    }
}

class ProotExecutor(
    private val readerPool: ExecutorService = Executors.newCachedThreadPool(),
    private val prootPath: String,
    private val libDir: String,
    private val rootfsPath: String,
    private val homePath: String,
    private val tmpPath: String,
) {

    /** Shuts down the reader thread pool and cancels any in-flight reads. */
    fun shutdown() {
        readerPool.shutdownNow()
    }

    fun execute(
        command: String,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        workingDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
    ): Map<String, Any> {
        val effectiveTimeout = timeoutSeconds.coerceIn(1, MAX_TIMEOUT_SECONDS)

        return try {
            val process = Runtime.getRuntime().exec(
                buildProcessArgs(command, workingDir),
                buildEnvVars(extraEnv),
                File(rootfsPath).parentFile,
            )
            val cancelled = AtomicBoolean(false)

            // Drain stdout/stderr concurrently to avoid pipe buffer deadlock
            val stdoutFuture = CompletableFuture.supplyAsync(
                { readBounded(process.inputStream.bufferedReader(), process, cancelled) },
                readerPool,
            )
            val stderrFuture = CompletableFuture.supplyAsync(
                { readBounded(process.errorStream.bufferedReader(), process, cancelled) },
                readerPool,
            )

            val completed = process.waitFor(effectiveTimeout, TimeUnit.SECONDS)

            if (!completed) {
                cancelled.set(true)
                process.destroyForcibly()
                return mapOf(
                    "success" to false,
                    "stdout" to stdoutFuture.get(1, TimeUnit.SECONDS).smartTruncate(MAX_OUTPUT_LENGTH),
                    "stderr" to stderrFuture.get(1, TimeUnit.SECONDS).smartTruncate(MAX_OUTPUT_LENGTH),
                    "exit_code" to -1,
                    "timed_out" to true,
                )
            }

            mapOf(
                "success" to (process.exitValue() == 0),
                "stdout" to stdoutFuture.get().smartTruncate(MAX_OUTPUT_LENGTH),
                "stderr" to stderrFuture.get().smartTruncate(MAX_OUTPUT_LENGTH),
                "exit_code" to process.exitValue(),
                "timed_out" to false,
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to (e.message ?: "Failed to execute command in sandbox"),
            )
        }
    }

    fun executeStreaming(
        command: String,
        workingDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): ProotHandle {
        val process = Runtime.getRuntime().exec(
            buildProcessArgs(command, workingDir),
            buildEnvVars(extraEnv),
            File(rootfsPath).parentFile,
        )
        val cancelled = AtomicBoolean(false)
        val stdoutFuture = CompletableFuture.runAsync(
            { streamLines(process, process.inputStream, cancelled, onStdout) },
            readerPool,
        )
        val stderrFuture = CompletableFuture.runAsync(
            { streamLines(process, process.errorStream, cancelled, onStderr) },
            readerPool,
        )
        return ProotHandle(process, cancelled, listOf(stdoutFuture, stderrFuture))
    }

    private fun buildProcessArgs(command: String, workingDir: String): Array<String> = arrayOf(
        prootPath,
        "--rootfs=$rootfsPath",
        "--bind=/dev",
        "--bind=/proc",
        "--bind=/sys",
        "--bind=$homePath:/root",
        "--bind=$tmpPath:/tmp",
        "-0",
        "-w", workingDir,
        "/bin/sh", "-c", command,
    )

    private fun buildEnvVars(extraEnv: Map<String, String>): Array<String> {
        val loaderPath = File(prootPath).parent.orEmpty() + "/libproot-loader.so"
        val baseEnv = arrayOf(
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LD_LIBRARY_PATH=$libDir",
            "PROOT_TMP_DIR=$tmpPath",
            "PROOT_LOADER=$loaderPath",
        )
        return baseEnv + extraEnv.map { (k, v) -> "$k=$v" }.toTypedArray()
    }

    /**
     * Reads all output from a process into a string. Uses non-blocking polling
     * so threads can respond promptly to [cancelled] and do not stall ART GC
     * safepoints by sitting in native read() forever.
     */
    private fun readBounded(
        reader: BufferedReader,
        process: Process,
        cancelled: AtomicBoolean,
    ): String {
        val sb = StringBuilder()
        val buf = CharArray(8192)
        try {
            while (!cancelled.get()) {
                if (reader.ready()) {
                    val read = reader.read(buf)
                    if (read == -1) break
                    sb.append(buf, 0, read)
                    if (sb.length >= MAX_OUTPUT_LENGTH) {
                        // Drain remainder without storing
                        while (!cancelled.get() && reader.ready()) {
                            reader.read(buf)
                        }
                        break
                    }
                } else if (!process.isAlive) {
                    // Process died — drain what's left
                    while (reader.ready()) {
                        val read = reader.read(buf)
                        if (read == -1) break
                        sb.append(buf, 0, read)
                    }
                    break
                } else {
                    Thread.sleep(STREAM_POLL_MS)
                }
            }
        } catch (_: Exception) {
            // Stream closed or thread interrupted during shutdown
        }
        return sb.toString()
    }

    /**
     * Streams lines from a running process to [onLine]. Uses non-blocking
     * polling so threads respond to cancellation within [STREAM_POLL_MS] and
     * never stall ART GC safepoints inside native read().
     */
    private fun streamLines(
        process: Process,
        inputStream: InputStream,
        cancelled: AtomicBoolean,
        onLine: (String) -> Unit,
    ) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        try {
            while (!cancelled.get()) {
                if (reader.ready()) {
                    val line = reader.readLine()
                    if (line == null) break
                    onLine(line)
                } else if (!process.isAlive) {
                    // Drain any remaining buffered data
                    reader.readLine()?.let { onLine(it) }
                    break
                } else {
                    Thread.sleep(STREAM_POLL_MS)
                }
            }
        } catch (_: Exception) {
            // Stream closed or thread interrupted during shutdown
        } finally {
            runCatching { reader.close() }
        }
    }
}
