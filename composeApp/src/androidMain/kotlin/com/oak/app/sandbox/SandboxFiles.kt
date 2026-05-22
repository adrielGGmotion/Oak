package com.oak.app.sandbox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

internal fun resolveSandboxFile(homeRoot: String, rel: String): File? {
    if (rel.isBlank() || rel.startsWith("/") || rel.startsWith("\\")) return null
    val parts = rel.split("/", "\\").filter { it.isNotEmpty() }
    if (parts.any { it == ".." }) return null
    return safeChild(File(homeRoot), parts)
}

internal fun resolveSandboxAbsolute(
    rootfsPath: String,
    homePath: String,
    sandboxPath: String,
    extraPaths: Map<String, String> = emptyMap(),
): File? {
    val normalized = sandboxPath.trim().ifEmpty { "/" }
    if (!normalized.startsWith("/")) return null
    val parts = normalized.split("/").filter { it.isNotEmpty() }
    if (parts.any { it == ".." }) return null
    val first = parts.firstOrNull()
    val (rootDir, remainder) = when {
        first == "root" -> File(homePath) to parts.drop(1)
        first != null && first in extraPaths -> File(extraPaths[first]!!) to parts.drop(1)
        else -> File(rootfsPath) to parts
    }
    return safeChild(rootDir, remainder)
}

private fun safeChild(root: File, parts: List<String>): File? {
    val candidate = if (parts.isEmpty()) root else File(root, parts.joinToString(File.separator))
    val rootCanon = root.canonicalPath
    val candidateCanon = candidate.canonicalPath
    if (candidateCanon != rootCanon && !candidateCanon.startsWith(rootCanon + File.separator)) return null
    return candidate
}

internal fun guessMimeType(filename: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}

internal data class FileOpenResult(
    val success: Boolean,
    val mimeType: String,
    val contentUri: String? = null,
    val error: String? = null,
)

internal fun openFileWithIntent(context: Context, file: File): FileOpenResult {
    val mime = guessMimeType(file.name)
    val authority = "${context.packageName}.fileprovider"

    val uri = resolveFileProviderUri(context, file, authority)
        ?: return FileOpenResult(false, mime, error = "FileProvider can't expose this path")

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return try {
        context.startActivity(intent)
        FileOpenResult(true, mime, contentUri = uri.toString())
    } catch (e: ActivityNotFoundException) {
        FileOpenResult(false, mime, error = "No app available to open $mime files")
    } catch (e: Exception) {
        FileOpenResult(false, mime, error = e.message ?: "Failed to open file")
    }
}

private fun resolveFileProviderUri(context: Context, file: File, authority: String): android.net.Uri? {
    try {
        return FileProvider.getUriForFile(context, authority, file)
    } catch (_: IllegalArgumentException) {
        // Path not covered by FileProvider — copy to cache and serve from there.
    }
    val cached = File(context.cacheDir, "external-share/${file.absolutePath.hashCode().toUInt().toString(16)}/${file.name}")
    cached.parentFile?.mkdirs()
    return try {
        file.copyTo(cached, overwrite = true)
        FileProvider.getUriForFile(context, authority, cached)
    } catch (_: Exception) {
        null
    }
}
