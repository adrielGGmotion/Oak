package com.oak.app.tools

import com.oak.app.data.AppSettings
import com.oak.app.data.Skill
import com.oak.app.data.SkillSource
import com.oak.app.network.tools.ParameterSchema
import com.oak.app.network.tools.Tool
import com.oak.app.network.tools.ToolInfo
import com.oak.app.network.tools.ToolSchema
import kotlinx.serialization.json.Json

object SkillTools {

    private const val MAX_SKILL_CONTENT_LENGTH = 10_000

    private val loadSkillToolInfo = ToolInfo(
        id = "load_skill",
        name = "Load Skill",
        description = "Load a skill to enhance your capabilities",
    )

    private val unloadSkillToolInfo = ToolInfo(
        id = "unload_skill",
        name = "Unload Skill",
        description = "Unload a previously loaded skill",
    )

    private val listSkillsToolInfo = ToolInfo(
        id = "list_skills",
        name = "List Skills",
        description = "List all available skills and their status",
    )

    private val createSkillToolInfo = ToolInfo(
        id = "create_skill",
        name = "Create Skill",
        description = "Create a new skill with custom instructions",
    )

    private val updateSkillToolInfo = ToolInfo(
        id = "update_skill",
        name = "Update Skill",
        description = "Update an existing skill's name, description, content, or required tools",
    )

    val skillToolDefinitions: List<ToolInfo> = listOf(
        loadSkillToolInfo,
        unloadSkillToolInfo,
        listSkillsToolInfo,
        createSkillToolInfo,
        updateSkillToolInfo,
    )

    fun getSkillTools(
        appSettings: AppSettings,
        getExcludedSkillIds: () -> Set<String>,
        excludeSkill: (String) -> Unit,
        includeSkill: (String) -> Unit,
    ): List<Tool> = listOf(
        createLoadSkillTool(appSettings, getExcludedSkillIds, includeSkill),
        createUnloadSkillTool(appSettings, getExcludedSkillIds, excludeSkill),
        createListSkillsTool(appSettings, getExcludedSkillIds),
        createCreateSkillTool(appSettings, getExcludedSkillIds, includeSkill),
        createUpdateSkillTool(appSettings, getExcludedSkillIds),
    )

    private fun createLoadSkillTool(
        appSettings: AppSettings,
        getExcludedSkillIds: () -> Set<String>,
        includeSkill: (String) -> Unit,
    ) = object : Tool {
        override val schema = ToolSchema(
            name = "load_skill",
            description = "Load a skill to enhance your capabilities. The skill's instructions will be active in this conversation until unloaded. Required tools will be available from the next turn.",
            parameters = mapOf(
                "skill_id" to ParameterSchema(
                    type = "string",
                    description = "The ID of the skill to load",
                    required = true,
                ),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val skillId = args["skill_id"]?.toString()
                ?: return mapOf("success" to false, "error" to "skill_id is required")

            val skills = getSkillsFromStorage(appSettings)
            val skill = skills.find { it.id == skillId }
                ?: return mapOf("success" to false, "error" to "Skill not found: $skillId")

            if (!skill.isEnabled) {
                return mapOf(
                    "success" to false,
                    "error" to "Skill '$skillId' is disabled. Enable it in Settings → Agent → Skills first.",
                )
            }

            val excludedIds = getExcludedSkillIds()
            if (skillId !in excludedIds) {
                return mapOf(
                    "success" to true,
                    "message" to "Skill '${skill.name}' is already loaded.",
                    "skill_id" to skillId,
                    "name" to skill.name,
                )
            }

            includeSkill(skillId)

            return mapOf(
                "success" to true,
                "message" to "Skill '${skill.name}' loaded successfully. Its instructions are now active.",
                "skill_id" to skillId,
                "name" to skill.name,
                "description" to skill.description,
                "required_tools" to skill.requiredTools,
                "note" to if (skill.requiredTools.isNotEmpty()) {
                    "Required tools will be available from the next turn."
                } else {
                    "No additional tools required."
                },
            )
        }
    }

    private fun createUnloadSkillTool(
        appSettings: AppSettings,
        getExcludedSkillIds: () -> Set<String>,
        excludeSkill: (String) -> Unit,
    ) = object : Tool {
        override val schema = ToolSchema(
            name = "unload_skill",
            description = "Unload a previously loaded skill. Its instructions will be removed from this conversation.",
            parameters = mapOf(
                "skill_id" to ParameterSchema(
                    type = "string",
                    description = "The ID of the skill to unload",
                    required = true,
                ),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val skillId = args["skill_id"]?.toString()
                ?: return mapOf("success" to false, "error" to "skill_id is required")

            val excludedIds = getExcludedSkillIds()
            if (skillId in excludedIds) {
                return mapOf(
                    "success" to false,
                    "error" to "Skill '$skillId' is not currently loaded.",
                )
            }

            excludeSkill(skillId)

            return mapOf(
                "success" to true,
                "message" to "Skill '$skillId' unloaded. Its instructions are no longer active.",
                "skill_id" to skillId,
            )
        }
    }

    private fun createListSkillsTool(
        appSettings: AppSettings,
        getExcludedSkillIds: () -> Set<String>,
    ) = object : Tool {
        override val schema = ToolSchema(
            name = "list_skills",
            description = "List all available skills and their status (enabled/disabled, loaded/unloaded).",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val skills = getSkillsFromStorage(appSettings)
            val excludedIds = getExcludedSkillIds()

            val skillList = skills.map { skill ->
                mapOf(
                    "id" to skill.id,
                    "name" to skill.name,
                    "description" to skill.description,
                    "is_enabled" to skill.isEnabled,
                    "is_loaded" to (skill.id !in excludedIds),
                    "is_built_in" to skill.isBuiltIn,
                    "required_tools" to skill.requiredTools,
                    "source" to skill.source,
                )
            }

            return mapOf(
                "success" to true,
                "skills" to skillList,
                "total_count" to skills.size,
                "enabled_count" to skills.count { it.isEnabled },
                "loaded_count" to skills.count { it.id !in excludedIds },
            )
        }
    }

    private fun createCreateSkillTool(
        appSettings: AppSettings,
        getExcludedSkillIds: () -> Set<String>,
        includeSkill: (String) -> Unit,
    ) = object : Tool {
        override val schema = ToolSchema(
            name = "create_skill",
            description = "Create a new skill that injects custom instructions into your system prompt. " +
                "Use this to develop reusable expertise modules for specific domains or workflows.",
            parameters = mapOf(
                "name" to ParameterSchema(
                    type = "string",
                    description = "Display name for the skill",
                    required = true,
                ),
                "description" to ParameterSchema(
                    type = "string",
                    description = "What the skill does and when to use it",
                    required = true,
                ),
                "content" to ParameterSchema(
                    type = "string",
                    description = "The full instructions/markdown to inject into the system prompt",
                    required = true,
                ),
                "required_tools" to ParameterSchema(
                    type = "string",
                    description = "JSON array of tool IDs this skill needs (e.g. [\"tool_a\", \"tool_b\"])",
                    required = false,
                ),
                "auto_load" to ParameterSchema(
                    type = "boolean",
                    description = "Whether to immediately load the skill after creation (default: true)",
                    required = false,
                ),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val name = args["name"]?.toString()?.trim()
                ?: return mapOf("success" to false, "error" to "name is required")
            val description = args["description"]?.toString()?.trim()
                ?: return mapOf("success" to false, "error" to "description is required")
            val content = args["content"]?.toString()?.trim()
                ?: return mapOf("success" to false, "error" to "content is required")

            if (name.isBlank()) {
                return mapOf("success" to false, "error" to "name cannot be blank")
            }
            if (description.isBlank()) {
                return mapOf("success" to false, "error" to "description cannot be blank")
            }
            if (content.isBlank()) {
                return mapOf("success" to false, "error" to "content cannot be blank")
            }
            if (content.length > MAX_SKILL_CONTENT_LENGTH) {
                return mapOf(
                    "success" to false,
                    "error" to "content exceeds maximum length of $MAX_SKILL_CONTENT_LENGTH characters",
                )
            }

            val requiredTools = parseStringList(args["required_tools"])
            val autoLoad = args["auto_load"]?.toString()?.toBooleanStrictOrNull() ?: true

            val existingSkills = getSkillsFromStorage(appSettings)
            val existingIds = existingSkills.map { it.id }.toSet()

            val id = generateSkillId(name, existingIds)

            val skill = Skill(
                id = id,
                name = name,
                description = description,
                content = content,
                requiredTools = requiredTools,
                isBuiltIn = false,
                isEnabled = true,
                source = SkillSource.AI,
            )

            val updatedSkills = existingSkills + skill
            appSettings.setSkillsJson(
                Json.encodeToString(updatedSkills),
            )

            if (autoLoad) {
                includeSkill(id)
            }

            return mapOf(
                "success" to true,
                "message" to "Skill '$name' created successfully." +
                    if (autoLoad) " It is now loaded and active." else "",
                "skill_id" to id,
                "name" to name,
                "description" to description,
                "required_tools" to requiredTools,
                "is_loaded" to autoLoad,
            )
        }
    }

    private fun createUpdateSkillTool(
        appSettings: AppSettings,
        getExcludedSkillIds: () -> Set<String>,
    ) = object : Tool {
        override val schema = ToolSchema(
            name = "update_skill",
            description = "Update an existing skill's name, description, content, or required tools. " +
                "Cannot modify built-in skills.",
            parameters = mapOf(
                "skill_id" to ParameterSchema(
                    type = "string",
                    description = "The ID of the skill to update",
                    required = true,
                ),
                "name" to ParameterSchema(
                    type = "string",
                    description = "New display name",
                    required = false,
                ),
                "description" to ParameterSchema(
                    type = "string",
                    description = "New description",
                    required = false,
                ),
                "content" to ParameterSchema(
                    type = "string",
                    description = "New system prompt instructions",
                    required = false,
                ),
                "required_tools" to ParameterSchema(
                    type = "string",
                    description = "New JSON array of required tool IDs",
                    required = false,
                ),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val skillId = args["skill_id"]?.toString()
                ?: return mapOf("success" to false, "error" to "skill_id is required")

            val existingSkills = getSkillsFromStorage(appSettings).toMutableList()
            val index = existingSkills.indexOfFirst { it.id == skillId }
            if (index == -1) {
                return mapOf("success" to false, "error" to "Skill not found: $skillId")
            }

            val existing = existingSkills[index]
            if (existing.isBuiltIn) {
                return mapOf(
                    "success" to false,
                    "error" to "Cannot modify built-in skill '$skillId'.",
                )
            }

            val newName = args["name"]?.toString()?.trim()
            val newDescription = args["description"]?.toString()?.trim()
            val newContent = args["content"]?.toString()?.trim()
            val newRequiredTools = if (args.containsKey("required_tools")) {
                parseStringList(args["required_tools"])
            } else {
                null
            }

            if (newName == null && newDescription == null && newContent == null && newRequiredTools == null) {
                return mapOf("success" to false, "error" to "At least one field to update is required.")
            }
            if (newName != null && newName.isBlank()) {
                return mapOf("success" to false, "error" to "name cannot be blank")
            }
            if (newDescription != null && newDescription.isBlank()) {
                return mapOf("success" to false, "error" to "description cannot be blank")
            }
            if (newContent != null && newContent.isBlank()) {
                return mapOf("success" to false, "error" to "content cannot be blank")
            }
            if (newContent != null && newContent.length > MAX_SKILL_CONTENT_LENGTH) {
                return mapOf(
                    "success" to false,
                    "error" to "content exceeds maximum length of $MAX_SKILL_CONTENT_LENGTH characters",
                )
            }

            val updated = existing.copy(
                name = newName ?: existing.name,
                description = newDescription ?: existing.description,
                content = newContent ?: existing.content,
                requiredTools = newRequiredTools ?: existing.requiredTools,
            )

            existingSkills[index] = updated
            appSettings.setSkillsJson(
                Json.encodeToString(existingSkills),
            )

            val excludedIds = getExcludedSkillIds()
            val isLoaded = updated.id !in excludedIds

            val changes = buildList {
                if (newName != null) add("name")
                if (newDescription != null) add("description")
                if (newContent != null) add("content")
                if (newRequiredTools != null) add("required_tools")
            }

            return mapOf(
                "success" to true,
                "message" to "Skill '${updated.name}' updated. Changed: ${changes.joinToString(", ")}.",
                "skill_id" to skillId,
                "is_loaded" to isLoaded,
            )
        }
    }

    // Reads skills from settings, merging built-in skills.
    private fun getSkillsFromStorage(appSettings: AppSettings): List<Skill> {
        val json = appSettings.getSkillsJson()
        return Skill.fromJson(json, Json)
    }

    private fun generateSkillId(name: String, existingIds: Set<String>): String =
        generateSkillIdFromName(name, existingIds)

    private fun parseStringList(value: Any?): List<String> {
        if (value == null) return emptyList()
        val str = value.toString().trim()
        if (str.isBlank() || str == "[]") return emptyList()
        return try {
            Json.decodeFromString<List<String>>(str)
        } catch (e: Exception) {
            println("SkillTools: failed to parse required tools list — ${e.message}")
            emptyList()
        }
    }
}

/**
 * Generates a URL-slug-style ID from a skill name, appending _2, _3, etc. on collision.
 * Shared between [SkillTools] and [com.oak.app.ui.settings.SettingsViewModel].
 */
fun generateSkillIdFromName(name: String, existingIds: Set<String>): String {
    var base = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
        .take(50)
    if (base.isBlank()) base = "skill"

    if (base !in existingIds) return base

    var counter = 2
    while ("${base}_$counter" in existingIds) counter++
    return "${base}_$counter"
}
