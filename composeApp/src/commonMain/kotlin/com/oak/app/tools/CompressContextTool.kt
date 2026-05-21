package com.oak.app.tools

import com.oak.app.network.tools.ParameterSchema
import com.oak.app.network.tools.Tool
import com.oak.app.network.tools.ToolSchema

fun compressContextTool(
    onCompress: suspend (keepRecent: Int, focus: String?) -> Map<String, Any>,
): Tool = object : Tool {
    override val schema = ToolSchema(
        name = "compress_context",
        description = "Compress older conversation history into a concise summary to free up context window space. Call this when the conversation is getting long and you need more room for detailed work. Recent exchanges are kept verbatim; older ones are summarized.",
        parameters = mapOf(
            "keep_recent" to ParameterSchema(
                type = "integer",
                description = "Number of recent user exchanges to keep verbatim (default: 5). Higher values preserve more detail but free less space.",
                required = false,
            ),
            "focus" to ParameterSchema(
                type = "string",
                description = "What to emphasize in the summary: 'all' (everything), 'decisions' (key decisions only), 'code' (code changes), 'facts' (facts and preferences). Default: 'all'.",
                required = false,
            ),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val keepRecent = (args["keep_recent"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 5
        val focus = args["focus"] as? String
        return onCompress(keepRecent, focus)
    }
}
