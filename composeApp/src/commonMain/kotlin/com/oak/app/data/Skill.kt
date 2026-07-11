package com.oak.app.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A modular prompt section that can be loaded/unloaded to enhance Oak's capabilities.
 * Skills inject instructions into the system prompt and can declare required tools
 * that get enabled when the skill is active.
 */
@Serializable
@Immutable
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val content: String,
    val requiredTools: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
    val isEnabled: Boolean = true,
    val source: SkillSource = SkillSource.USER,
) {
    companion object {
        const val OAK_UI_SKILL_ID = "oak_ui"
        const val AUTOMATION_SKILL_ID = "automation"
        const val EMAIL_SKILL_ID = "email"
        const val STRUCTURED_LEARNING_SKILL_ID = "structured_learning"

        val OAK_UI_SKILL = Skill(
            id = OAK_UI_SKILL_ID,
            name = "Dynamic UI (oak-ui)",
            description = "Show interactive UI directly in chat — forms, buttons, cards, images, and more. Use this instead of plain text when the user needs input, choices, or visual content.",
            content = OAK_UI_CATALOG,
            requiredTools = emptyList(),
            isBuiltIn = true,
            isEnabled = true,
            source = SkillSource.BUILT_IN,
        )

        val AUTOMATION_SKILL = Skill(
            id = AUTOMATION_SKILL_ID,
            name = "Automation",
            description = "Schedule tasks, recurring actions, and heartbeat triggers",
            content = AUTOMATION_SKILL_CONTENT,
            requiredTools = listOf("schedule_task", "list_tasks", "cancel_task"),
            isBuiltIn = true,
            isEnabled = true,
            source = SkillSource.BUILT_IN,
        )

        val EMAIL_SKILL = Skill(
            id = EMAIL_SKILL_ID,
            name = "Email",
            description = "Email account management and sending capabilities",
            content = "",  // Dynamic — depends on connected accounts
            requiredTools = listOf("compose_email", "reply_email"),
            isBuiltIn = true,
            isEnabled = true,
            source = SkillSource.BUILT_IN,
        )

        val STRUCTURED_LEARNING_SKILL = Skill(
            id = STRUCTURED_LEARNING_SKILL_ID,
            name = "Structured Learning",
            description = "Advanced memory categorization with learning, error, and preference tracking",
            content = STRUCTURED_LEARNING_SKILL_CONTENT,
            requiredTools = listOf("memory_learn", "memory_reinforce"),
            isBuiltIn = true,
            isEnabled = true,
            source = SkillSource.BUILT_IN,
        )

        val BUILT_IN_SKILLS = listOf(OAK_UI_SKILL, AUTOMATION_SKILL, EMAIL_SKILL, STRUCTURED_LEARNING_SKILL)

        /**
         * Parse skills from JSON, merging any missing built-in skills.
         * Shared between [com.oak.app.data.RemoteDataRepository] and [com.oak.app.tools.SkillTools].
         */
        fun mergeWithBuiltIns(stored: List<Skill>): List<Skill> {
            val storedIds = stored.map { it.id }.toSet()
            val missingBuiltIns = BUILT_IN_SKILLS.filter { it.id !in storedIds }
            return if (missingBuiltIns.isNotEmpty()) missingBuiltIns + stored else stored
        }

        /**
         * Deserialize a skills JSON string, merging built-ins. Returns [BUILT_IN_SKILLS] on
         * blank input or parse failure.
         */
        fun fromJson(json: String, kotlinxJson: Json = Json): List<Skill> {
            if (json.isBlank() || json == "[]") return BUILT_IN_SKILLS
            return try {
                val stored = kotlinxJson.decodeFromString<List<Skill>>(json)
                mergeWithBuiltIns(stored)
            } catch (_: Exception) {
                BUILT_IN_SKILLS
            }
        }
    }
}

/**
 * Pre-computed oak-ui component catalog text — shared between Dynamic UI and
 * Interactive UI modes. This is the content injected when the oak-ui skill is active.
 */
internal val OAK_UI_CATALOG: String = buildString {
    append("When the user asks for input, choices, interactive elements, or structured displays — you MUST use oak-ui blocks. Do not ask in plain text if a form, selector, buttons, or cards would be more natural.\n\n")
    append("You can also show images directly in chat using the image component — use this whenever visual content would help (diagrams, screenshots, illustrations, maps, etc.).\n\n")
    append("For example, if the user asks you to help plan a trip, present destination options as buttons; if you need preferences, show a form; if presenting choices, use interactive cards. ")
    append("Use oak-ui whenever collecting data, offering choices, presenting structured information, or guiding multi-step workflows. ")
    append("You can mix oak-ui blocks with regular markdown text naturally — use markdown for explanations and oak-ui for interactive elements.\n\n")
    append("FORMAT — you MUST follow this exactly:\n")
    append("- Wrap a JSON object in ```oak-ui code fences (triple backticks with the word oak-ui).\n")
    append("- The JSON object describes the UI layout using the component types listed below.\n")
    append("- Do NOT use HTML tags like <oak-block>, <oak-ui>, or any angle-bracket syntax. The ONLY format is ```oak-ui code fences.\n")
    append("- Do NOT invent component types, properties, or behaviours not listed below.\n\n")
    append("Correct example:\n```oak-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"What is your name?\",\"style\":\"title\"},{\"type\":\"text_input\",\"id\":\"name\",\"placeholder\":\"Enter name\"},{\"type\":\"button\",\"label\":\"Submit\",\"action\":{\"type\":\"callback\",\"event\":\"submit\",\"collectFrom\":[\"name\"]}}]}\n```\n\n")
    append("WRONG — do NOT do this:\n```\n<oak-block data-type=\"ask\" data-questions='[...]'></oak-block>\n<oak-ui>{...}</oak-ui>\n```\n\n")
    append("Components: column, row, card, box, text, button, text_input, checkbox, switch, select, radio_group, slider, chip_group, table, list, divider, image, icon, code, progress, countdown, alert, tabs, accordion, quote, badge, stat, avatar.\n")
    append("- text: {\"type\":\"text\",\"value\":\"...\",\"style\":\"headline|title|body|caption\",\"bold\":true,\"color\":\"primary|secondary|error\"} — do NOT use markdown formatting (**, *, #, etc.) in text values; use the bold/italic/style properties instead\n")
    append("- button: {\"type\":\"button\",\"label\":\"...\",\"action\":{...},\"variant\":\"filled|outlined|text|tonal\"}\n")
    append("- text_input: {\"type\":\"text_input\",\"id\":\"...\",\"label\":\"...\",\"placeholder\":\"...\",\"value\":\"...\"}\n")
    append("- checkbox: {\"type\":\"checkbox\",\"id\":\"...\",\"label\":\"...\",\"checked\":false}\n")
    append("- switch: {\"type\":\"switch\",\"id\":\"...\",\"label\":\"...\",\"checked\":false}\n")
    append("- select: {\"type\":\"select\",\"id\":\"...\",\"label\":\"...\",\"options\":[\"A\",\"B\"],\"selected\":\"A\"}\n")
    append("- radio_group: {\"type\":\"radio_group\",\"id\":\"...\",\"label\":\"...\",\"options\":[\"A\",\"B\"],\"selected\":\"A\"}\n")
    append("- slider: {\"type\":\"slider\",\"id\":\"...\",\"label\":\"...\",\"value\":50,\"min\":0,\"max\":100,\"step\":10}\n")
    append("- chip_group: {\"type\":\"chip_group\",\"id\":\"...\",\"chips\":[{\"label\":\"Tag\",\"value\":\"tag\"}],\"selection\":\"single|multi|none\"} — selection mode: \"single\" (default, one at a time), \"multi\" (any number), or \"none\" (display-only tags, no interaction, id not needed). For \"single\" and \"multi\" a button must collectFrom the chip_group id to send the selection.\n")
    append("- list: {\"type\":\"list\",\"items\":[...],\"ordered\":false} — bullet (or numbered) list; the app renders bullets/numbers automatically, so do NOT include bullet characters (•, -, *) or numbering in item text\n")
    append("- table: {\"type\":\"table\",\"headers\":[\"Col1\",\"Col2\"],\"rows\":[[\"a\",\"b\"]]} — columns share equal width; keep to 3-4 columns max on mobile, use short cell values\n")
    append("- icon: {\"type\":\"icon\",\"name\":\"home|settings|search|add|delete|edit|check|check_circle|close|arrow_back|arrow_forward|star|favorite|share|info|warning|person|group|mail|phone|calendar|location|refresh|menu|more|send|notifications|trending_up|trending_down|trending_flat|thumb_up|thumb_down|visibility|lock|shopping_cart|play|pause|stop|download|upload|cloud|attachment|link|code|terminal|build|bug|lightbulb|science|school|work|account_circle|language|translate|dark_mode|light_mode|bolt|rocket|money|credit_card|receipt|inventory|category|dashboard|analytics|chart|pie_chart|show_chart|timer|alarm|task|bookmark|flag|tag|pin|copy|paste|cut|undo|redo|filter|sort|swap|sync|wifi|bluetooth|battery|speed|shield|verified|health|fitness|food|coffee|airplane|hotel|car|earth|map|compass|pet|leaf|water|weather|party|trophy|medal|premium\",\"size\":24,\"color\":\"primary|secondary|error\"} — you can also use any emoji as the name (e.g. \"name\":\"⚔️\" or \"name\":\"🗺️\"); prefer emojis when they better convey the meaning than the generic Material icons\n")
    append("- code: {\"type\":\"code\",\"code\":\"...\",\"language\":\"kotlin\"} — a copy-to-clipboard icon is rendered automatically; do NOT add your own copy button next to it.\n")
    append("- progress: {\"type\":\"progress\",\"value\":0.5,\"label\":\"50%\"} (always provide a value 0.0-1.0 to show a determinate bar; do NOT omit value to fake a loading state)\n")
    append("- countdown: {\"type\":\"countdown\",\"seconds\":300,\"label\":\"Time left\",\"action\":{\"type\":\"callback\",\"event\":\"timer_done\"}} (seconds is relative duration from render; action is optional, fires on expiry)\n")
    append("- alert: {\"type\":\"alert\",\"message\":\"...\",\"title\":\"...\",\"severity\":\"info|success|warning|error\"}\n")
    append("- tabs: {\"type\":\"tabs\",\"tabs\":[{\"label\":\"Tab 1\",\"children\":[...]},{\"label\":\"Tab 2\",\"children\":[...]}],\"selectedIndex\":0}\n")
    append("- accordion: {\"type\":\"accordion\",\"title\":\"...\",\"children\":[...],\"expanded\":false}\n")
    append("- box: {\"type\":\"box\",\"children\":[...],\"contentAlignment\":\"center|top_start|top_center|top_end|center_start|center_end|bottom_start|bottom_center|bottom_end\"}\n")
    append("- quote: {\"type\":\"quote\",\"text\":\"...\",\"source\":\"Author Name\"} — blockquote with accent border\n")
    append("- badge: {\"type\":\"badge\",\"value\":\"3\",\"color\":\"primary|secondary|error\"} — small colored pill for counts or status\n")
    append("- stat: {\"type\":\"stat\",\"value\":\"\$1,234\",\"label\":\"Revenue\",\"description\":\"12% increase\"} — large metric display\n")
    append("- avatar: {\"type\":\"avatar\",\"name\":\"John Doe\",\"imageUrl\":\"https://...\",\"size\":40} — circular image or initials (24-80dp)\n")
    append("- image: {\"type\":\"image\",\"url\":\"https://...\",\"alt\":\"Description\",\"height\":200,\"aspectRatio\":1.5} — display an image directly in chat. Use this to show the user pictures, diagrams, screenshots, or any visual content. alt is recommended for accessibility. height and aspectRatio are optional.\n")
    append("  To show a local file: use the `path` property with a path relative to /root (e.g. `\"path\":\"banner.png\"` or `\"path\":\"images/photo.jpg\"`). The app resolves it automatically. Do NOT use `file:///` or `content://` URIs — just pass the relative path.\n\n")
    append("Actions (on buttons, countdown expiry):\n")
    append("- callback: {\"type\":\"callback\",\"event\":\"event_name\",\"data\":{\"key\":\"val\"},\"collectFrom\":[\"input_id1\",\"input_id2\"]} — collects input values and sends them back as a user message (e.g. \"Pressed: event_name\" or \"Responded with: key: value\"). You then reply with text or more UI. Use callbacks for: collecting choices, submitting forms, navigating between steps, confirming actions. Do NOT create callback buttons that imply operations you cannot perform — callbacks only send a message, they do not trigger system actions like printing, file export, or downloads.\n")
    append("- toggle: {\"type\":\"toggle\",\"targetId\":\"element_id\"} — shows/hides an element locally\n")
    append("- open_url: {\"type\":\"open_url\",\"url\":\"https://...\"}\n")
    append("- copy_to_clipboard: {\"type\":\"button\",\"action\":{\"type\":\"copy_to_clipboard\",\"text\":\"...\"}} — renders as a clipboard icon button; omit the button label. Offer next to copyable content like snippets, commands, or tokens.\n\n")
    append("- Form inputs (text_input, checkbox, switch, select, radio_group, slider, chip_group) only store state locally. Their values are ONLY sent when a button's collectFrom includes their id. Always pair form inputs with a submit button that collects from them.\n\n")
    append("Layout tips:\n")
    append("- Put buttons INSIDE cards, directly below related content — never group all buttons separately at the bottom\n")
    append("- Use rows for groups of buttons or chips — rows wrap automatically, so any number of items is fine\n")
    append("- Keep button labels short (1-3 words)\n\n")
    append("Example:\n```oak-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Your name?\",\"style\":\"title\"},{\"type\":\"text_input\",\"id\":\"name\",\"placeholder\":\"Enter name\"},{\"type\":\"button\",\"label\":\"Submit\",\"action\":{\"type\":\"callback\",\"event\":\"submit\",\"collectFrom\":[\"name\"]}}]}\n```\n")
}

internal val AUTOMATION_SKILL_CONTENT: String = buildString {
    append("When the user asks you to schedule something, set a reminder, create a recurring task, or run something automatically — you MUST use the `schedule_task` tool. Do not suggest alternatives or ask them to do it manually.\n\n")
    append("The tool has three mutually exclusive triggers:\n")
    append("- `execute_at` — one-off at a specific datetime (reminders, \"check back at 3pm\").\n")
    append("- `cron` — recurring on a schedule (\"every morning at 8\", \"every 15 minutes\").\n")
    append("- `on_heartbeat: true` — appended to every heartbeat self-check. Use this when the user asks for *standing* heartbeat behaviour (e.g. \"greet me on every heartbeat\", \"always summarize new emails\", \"flag overdue tasks each check\"). These are `HEARTBEAT` trigger tasks and show up in `list_tasks` alongside time/cron tasks.\n")
    append("Each scheduled or heartbeat run starts fresh, so embed any context the prompt needs. Use `list_tasks` / `cancel_task` to inspect or remove.\n")
    append("Heartbeat itself (on/off toggle, interval, active hours) is user-controlled in Settings → Agent → Heartbeat — you cannot enable, disable, or reschedule it. If the user asks for recurring updates and heartbeat seems off, either schedule a cron task or tell them to enable Heartbeat in settings — never claim to have \"enabled\" or \"turned on\" heartbeat.")
}

internal val STRUCTURED_LEARNING_SKILL_CONTENT: String = buildString {
    append("When recording memories about user preferences, corrections, things that worked, or error resolutions — you MUST use `memory_learn` with the appropriate category. Do not use `memory_store` for learnings; `memory_store` is for general facts, `memory_learn` is for categorized lessons.\n\n")
    append("Use memory_learn to record categorized learnings:\n")
    append("- Record user corrections and preferences as PREFERENCE entries\n")
    append("- Record things that worked well as LEARNING entries\n")
    append("- Record error resolutions as ERROR entries\n")
    append("Use memory_reinforce when a stored learning produced a good outcome.")
}

/** Identifies where a skill originated. */
@Serializable
enum class SkillSource {
    BUILT_IN,
    USER,
    AI,
}
