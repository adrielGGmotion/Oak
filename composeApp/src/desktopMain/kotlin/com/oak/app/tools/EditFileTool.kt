package com.oak.app.tools

import com.oak.app.network.tools.ParameterSchema
import com.oak.app.network.tools.Tool
import com.oak.app.network.tools.ToolInfo
import com.oak.app.network.tools.ToolSchema
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.tool_edit_file_description
import oak.composeapp.generated.resources.tool_edit_file_name
import java.io.File

private const val MAX_FILE_SIZE = 10_000_000

object EditFileTool : Tool {
    override val schema = ToolSchema(
        name = "edit_file",
        description = """Edit a file by finding and replacing exact text. 
Use read_file first to see the current content before editing.
- If old_string is provided: finds the exact text and replaces it with new_string (find-and-replace).
- If old_string is omitted: writes/overwrites the entire file with new_string.
The find-and-replace must match exactly including whitespace and indentation.""",
        parameters = mapOf(
            "path" to ParameterSchema("string", "Absolute path to the file", true),
            "old_string" to ParameterSchema(
                "string",
                "Exact text to find and replace. Omit this to write/overwrite the entire file instead.",
                false,
            ),
            "new_string" to ParameterSchema("string", "Replacement text (for edit) or new file content (for write)", true),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val path = args["path"] as? String
            ?: return mapOf("success" to false, "error" to "path is required")
        val newString = args["new_string"] as? String
            ?: return mapOf("success" to false, "error" to "new_string is required")

        val file = File(path).canonicalFile
        val oldString = args["old_string"] as? String

        return if (oldString != null) {
            doEdit(file, file.path, oldString, newString)
        } else {
            doWrite(file, file.path, newString)
        }
    }

    private fun doEdit(file: File, path: String, oldString: String, newString: String): Any {
        if (!file.exists()) return mapOf("success" to false, "error" to "File not found: $path")
        if (!file.isFile) return mapOf("success" to false, "error" to "Not a file: $path")
        if (!file.canWrite()) return mapOf("success" to false, "error" to "Permission denied: $path")

        if (file.length() > MAX_FILE_SIZE) {
            return mapOf("success" to false, "error" to "File too large for editing")
        }

        val content = file.readText()
        val index = content.indexOf(oldString)
        if (index == -1) {
            val preview = oldString.take(60)
            return mapOf(
                "success" to false,
                "error" to "old_string not found in file. First 60 chars of old_string: '$preview'",
            )
        }

        val newContent = content.replaceFirst(oldString, newString)

        // Read-edit-write is inherently non-atomic — concurrent edits may be lost.
        // The read_file/edit_file ordering contract (enforced by ToolExecutor) is the
        // primary guard against stale edits.
        val beforeLine = content.substring(0, index).count { it == '\n' } + 1
        val replacedChars = oldString.length
        val newChars = newString.length

        file.writeText(newContent)

        return mapOf(
            "success" to true,
            "path" to file.absolutePath,
            "type" to "edit",
            "replaced_at_line" to beforeLine,
            "chars_replaced" to replacedChars,
            "chars_written" to newChars,
        )
    }

    private fun doWrite(file: File, path: String, content: String): Any = try {
        file.parentFile?.mkdirs()
        file.writeText(content)
        mapOf(
            "success" to true,
            "path" to file.absolutePath,
            "type" to "write",
            "chars_written" to content.length,
        )
    } catch (e: Exception) {
        mapOf("success" to false, "error" to "Failed to write file: ${e.message}")
    }

    val toolInfo = ToolInfo(
        id = "edit_file",
        name = "Edit File",
        description = "Edit a file by finding and replacing exact text, or write new content",
        nameRes = Res.string.tool_edit_file_name,
        descriptionRes = Res.string.tool_edit_file_description,
    )
}
