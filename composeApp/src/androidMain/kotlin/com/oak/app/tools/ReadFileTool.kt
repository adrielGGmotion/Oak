package com.oak.app.tools

import com.oak.app.network.tools.ParameterSchema
import com.oak.app.network.tools.Tool
import com.oak.app.network.tools.ToolInfo
import com.oak.app.network.tools.ToolSchema
import com.oak.app.sandbox.LinuxSandboxManager
import com.oak.app.sandbox.resolveSandboxAbsolute
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.tool_read_file_description
import oak.composeapp.generated.resources.tool_read_file_name
import org.koin.java.KoinJavaComponent.inject
import java.io.File

private const val MAX_FILE_SIZE = 1_000_000
private const val DEFAULT_MAX_LINES = 2000

object ReadFileTool : Tool {
    private val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)

    override val schema = ToolSchema(
        name = "read_file",
        description = "Read a file from the sandbox filesystem. Returns file contents with line numbers. Use offset and limit to read specific line ranges of large files.",
        parameters = mapOf(
            "path" to ParameterSchema("string", "Absolute sandbox path, e.g. /root/file.txt or /root/src/main.kt", true),
            "offset" to ParameterSchema("integer", "Starting line number (1-indexed, default: 1). Use to skip header boilerplate and read specific sections.", false),
            "limit" to ParameterSchema("integer", "Maximum number of lines to return (default: $DEFAULT_MAX_LINES). Use to avoid excessive output.", false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val path = args["path"] as? String
            ?: return mapOf("success" to false, "error" to "path is required")

        val file = resolveSandboxAbsolute(
            rootfsPath = sandboxManager.rootfsPath,
            homePath = sandboxManager.homePath,
            sandboxPath = path,
            extraPaths = sandboxManager.getStorageVolumeMap(),
        ) ?: return mapOf("success" to false, "error" to "Invalid path: must not contain .. segments")

        if (!file.exists()) return mapOf("success" to false, "error" to "File not found: $path")
        if (!file.isFile) return mapOf("success" to false, "error" to "Not a file: $path")
        if (!file.canRead()) return mapOf("success" to false, "error" to "Permission denied: $path")

        if (file.length() > MAX_FILE_SIZE) {
            return mapOf(
                "success" to false,
                "error" to "File too large (${file.length()} bytes). Max: ${MAX_FILE_SIZE} bytes",
            )
        }

        val offset = (args["offset"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1
        val limit = (args["limit"] as? Number)?.toInt()?.coerceAtLeast(1) ?: DEFAULT_MAX_LINES

        val lines = file.readLines()
        val totalLines = lines.size

        val startIndex = (offset - 1).coerceIn(0, totalLines)
        val endIndex = (startIndex + limit).coerceAtMost(totalLines)
        val selectedLines = lines.subList(startIndex, endIndex)

        val content = selectedLines.mapIndexed { i, line ->
            "${startIndex + i + 1}:$line"
        }.joinToString("\n")

        return mapOf(
            "success" to true,
            "path" to path,
            "content" to content,
            "line_count" to selectedLines.size,
            "total_lines" to totalLines,
            "offset" to offset,
            "limit" to limit,
        )
    }

    val toolInfo = ToolInfo(
        id = "read_file",
        name = "Read File",
        description = "Read a file from the sandbox filesystem with line numbers",
        nameRes = Res.string.tool_read_file_name,
        descriptionRes = Res.string.tool_read_file_description,
    )
}
