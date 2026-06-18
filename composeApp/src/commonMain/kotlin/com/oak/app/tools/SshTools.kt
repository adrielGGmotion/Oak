package com.oak.app.tools

import com.oak.app.createSshClient
import com.oak.app.network.tools.ParameterSchema
import com.oak.app.network.tools.Tool
import com.oak.app.network.tools.ToolInfo
import com.oak.app.network.tools.ToolSchema
import com.oak.app.ssh.SshAuthType
import com.oak.app.ssh.SshServerConfig
import com.oak.app.ssh.SshServerManager
import kotlin.io.encoding.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val MAX_TOOL_OUTPUT = 20_000

object SshTools {

    private var serverManager: SshServerManager? = null

    fun init(manager: SshServerManager) {
        serverManager = manager
    }

    private fun requireManager(): SshServerManager = serverManager ?: error("SshTools not initialized")

    private fun resolveServerId(serverId: String?): Result<String> {
        val activeIds = requireManager().getActiveServerIds()
        if (activeIds.isEmpty()) {
            return Result.failure(Exception("No active SSH connections. Use ssh_connect first."))
        }
        val targetId = serverId ?: if (activeIds.size == 1) {
            activeIds.first()
        } else {
            return Result.failure(Exception("Multiple active SSH connections. Please specify server_id. Active: ${activeIds.joinToString()}"))
        }
        if (targetId !in activeIds) {
            return Result.failure(Exception("Not connected to: $targetId. Active: ${activeIds.joinToString()}"))
        }
        return Result.success(targetId)
    }

    private fun escapeShellArg(arg: String): String = "'${arg.replace("'", "'\\''")}'"

    private fun truncate(s: String) = s.take(MAX_TOOL_OUTPUT)

    val connectTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_connect",
            description = "Connect to a remote SSH server. If the server is already saved in config, " +
                "provide the server_id. Otherwise provide host, port, username, and authentication. " +
                "Once connected, use ssh_execute_command to run commands. " +
                "The connection persists across messages until you disconnect. " +
                "Set persistent=true to save the server config for reuse in the UI.",
            parameters = mapOf(
                "server_id" to ParameterSchema("string", "ID of a saved server config (omit if connecting ad-hoc)", false),
                "host" to ParameterSchema("string", "Remote hostname or IP address", false),
                "port" to ParameterSchema("integer", "SSH port (default 22)", false),
                "username" to ParameterSchema("string", "SSH username", false),
                "password" to ParameterSchema("string", "SSH password", false),
                "private_key_path" to ParameterSchema("string", "Path to SSH private key file", false),
                "passphrase" to ParameterSchema("string", "Passphrase for the private key", false),
                "persistent" to ParameterSchema("boolean", "Save this server as a config for reuse", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val serverId = args["server_id"] as? String

            if (serverId != null) {
                val config = requireManager().getServers().find { it.id == serverId }
                    ?: return mapOf("success" to false, "error" to "Server config not found: $serverId")
                val result = requireManager().connectServer(serverId)
                return if (result.isSuccess) {
                    mapOf("success" to true, "message" to "Connected to ${config.name} (${config.host}:${config.port})")
                } else {
                    mapOf("success" to false, "error" to (result.exceptionOrNull()?.message ?: "Connection failed"))
                }
            }

            val host = args["host"] as? String
                ?: return mapOf("success" to false, "error" to "host is required when no server_id is provided")
            val port = (args["port"] as? Number)?.toInt() ?: 22
            val username = args["username"] as? String
                ?: return mapOf("success" to false, "error" to "username is required")
            val password = args["password"] as? String ?: ""
            val privateKeyPath = args["private_key_path"] as? String ?: ""
            val passphrase = args["passphrase"] as? String ?: ""
            val persistent = args["persistent"] as? Boolean ?: false

            val authType = if (privateKeyPath.isNotBlank()) SshAuthType.Key else SshAuthType.Password

            if (persistent) {
                val config = requireManager().addServer(
                    name = host,
                    host = host,
                    port = port,
                    username = username,
                    authType = authType,
                    password = password,
                    privateKey = privateKeyPath,
                    passphrase = passphrase,
                )
                val result = requireManager().connectServer(config.id)
                return if (result.isSuccess) {
                    mapOf(
                        "success" to true,
                        "message" to "Connected and saved as '${config.name}'",
                        "server_id" to config.id,
                    )
                } else {
                    mapOf("success" to false, "error" to (result.exceptionOrNull()?.message ?: "Connection failed"))
                }
            }

            val client = createSshClient()

            @OptIn(ExperimentalUuidApi::class)
            val adhocId = "_adhoc_${host}_${port}_${Uuid.random()}"

            val tempConfig = SshServerConfig(
                id = adhocId,
                name = host,
                host = host,
                port = port,
                username = username,
                authType = authType,
                password = password,
                privateKey = privateKeyPath,
                passphrase = passphrase,
                isEnabled = true,
            )

            return try {
                client.connect(tempConfig).getOrThrow()
                requireManager().registerAdhocClient(tempConfig.id, client)
                mapOf("success" to true, "message" to "Connected to $username@$host:$port")
            } catch (e: Exception) {
                client.disconnect()
                mapOf("success" to false, "error" to (e.message ?: "Connection failed"))
            }
        }
    }

    val disconnectTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_disconnect",
            description = "Disconnect from an SSH server. If no server_id is provided, disconnects all active connections.",
            parameters = mapOf(
                "server_id" to ParameterSchema("string", "ID of the server to disconnect (disconnects all if not specified)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val serverId = args["server_id"] as? String
            if (serverId != null) {
                if (!requireManager().isConnected(serverId)) {
                    return mapOf("success" to false, "error" to "Not connected to server: $serverId")
                }
                requireManager().disconnectClient(serverId)
                return mapOf("success" to true, "message" to "Disconnected from: $serverId")
            }
            val activeIds = requireManager().getActiveServerIds()
            if (activeIds.isEmpty()) {
                return mapOf("success" to false, "error" to "No active SSH connections")
            }
            activeIds.forEach { requireManager().disconnectClient(it) }
            return mapOf("success" to true, "message" to "Disconnected ${activeIds.size} active connection(s)")
        }
    }

    val executeCommandTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_execute_command",
            description = "Execute a shell command on a connected remote SSH server. " +
                "This runs on the remote machine, not locally. " +
                "Use this for compute-heavy tasks, running scripts, managing remote servers, etc. " +
                "Each call runs a fresh shell (no state persists between calls). " +
                "Use cd && command patterns to work in a directory. " +
                "Output is capped at 20KB; pipe large output through head/tail.",
            parameters = mapOf(
                "command" to ParameterSchema("string", "The shell command to execute on the remote server", true),
                "server_id" to ParameterSchema("string", "Server ID to run on (uses the only active connection if not specified)", false),
                "timeout" to ParameterSchema("integer", "Timeout in seconds (default 30, max 120)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val command = args["command"] as? String
                ?: return mapOf("success" to false, "error" to "Command is required")
            val serverId = args["server_id"] as? String
            val timeout = ((args["timeout"] as? Number)?.toLong() ?: 30L).coerceIn(1, 120L)

            val resolved = resolveServerId(serverId)
            if (resolved.isFailure) return mapOf("success" to false, "error" to resolved.exceptionOrNull()?.message)

            val result = requireManager().executeCommand(resolved.getOrThrow(), command, timeout)
            return mapOf(
                "success" to (result.exitCode == 0),
                "exit_code" to result.exitCode,
                "stdout" to truncate(result.stdout),
                "stderr" to truncate(result.stderr),
            )
        }
    }

    val statusTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_status",
            description = "Check the status of SSH connections. Lists active connections and saved server configs.",
            parameters = mapOf(
                "server_id" to ParameterSchema("string", "Check status for a specific server only", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val serverId = args["server_id"] as? String
            val activeIds = requireManager().getActiveServerIds()
            val allServers = requireManager().getServers()

            if (serverId != null) {
                val isActive = serverId in activeIds
                val config = allServers.find { it.id == serverId }
                val info = requireManager().getConnectionInfo(serverId)
                return mapOf(
                    "server_id" to serverId,
                    "connected" to isActive,
                    "name" to (config?.name ?: "Unknown"),
                    "host" to (info?.host ?: config?.host ?: "Unknown"),
                    "username" to (info?.username ?: config?.username ?: "Unknown"),
                )
            }

            val connections = activeIds.mapNotNull { id ->
                val config = allServers.find { it.id == id }
                val info = requireManager().getConnectionInfo(id)
                mapOf(
                    "server_id" to id,
                    "name" to (config?.name ?: "Ad-hoc"),
                    "host" to (info?.host ?: config?.host ?: "Unknown"),
                    "username" to (info?.username ?: config?.username ?: "Unknown"),
                )
            }

            val savedServers = allServers.map { config ->
                mapOf(
                    "server_id" to config.id,
                    "name" to config.name,
                    "host" to config.host,
                    "enabled" to config.isEnabled,
                    "connected" to (config.id in activeIds),
                )
            }

            return mapOf(
                "active_connections" to connections.size,
                "connections" to connections,
                "saved_servers" to savedServers,
            )
        }
    }

    val transferFileTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_transfer_file",
            description = "Transfer files between local and a remote SSH server. " +
                "direction='upload' sends local to remote; direction='download' fetches remote to local. " +
                "Uses SFTP protocol. Requires an active SSH connection.",
            parameters = mapOf(
                "direction" to ParameterSchema("string", "'upload' or 'download'", true),
                "local_path" to ParameterSchema("string", "Local file path", true),
                "remote_path" to ParameterSchema("string", "Remote file path", true),
                "server_id" to ParameterSchema("string", "Server ID (uses the only active connection if not specified)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val direction = args["direction"] as? String
                ?: return mapOf("success" to false, "error" to "direction is required (upload/download)")
            val localPath = args["local_path"] as? String
                ?: return mapOf("success" to false, "error" to "local_path is required")
            val remotePath = args["remote_path"] as? String
                ?: return mapOf("success" to false, "error" to "remote_path is required")
            val serverId = args["server_id"] as? String

            val resolved = resolveServerId(serverId)
            if (resolved.isFailure) return mapOf("success" to false, "error" to resolved.exceptionOrNull()?.message)

            val result = when (direction.lowercase()) {
                "upload" -> requireManager().transferFile(resolved.getOrThrow(), "upload", localPath, remotePath)
                "download" -> requireManager().transferFile(resolved.getOrThrow(), "download", localPath, remotePath)
                else -> return mapOf("success" to false, "error" to "direction must be 'upload' or 'download'")
            }

            return if (result.isSuccess) {
                mapOf("success" to true, "message" to "File ${direction}ed successfully")
            } else {
                mapOf("success" to false, "error" to (result.exceptionOrNull()?.message ?: "Transfer failed"))
            }
        }
    }

    // ── New high-level SSH tools ─────────────────────────────────────────

    val readFileTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_read_file",
            description = "Read a file on a remote SSH server with line numbers. " +
                "Use offset and limit to read specific line ranges of large files. " +
                "Returns file content with line numbers, total line count, and metadata. " +
                "Prefer this over ssh_execute_command with cat for structured file reading.",
            parameters = mapOf(
                "path" to ParameterSchema("string", "Absolute path to the remote file", true),
                "server_id" to ParameterSchema("string", "Server ID (uses the only active connection if not specified)", false),
                "offset" to ParameterSchema("integer", "Starting line number (1-indexed, default: 1)", false),
                "limit" to ParameterSchema("integer", "Maximum number of lines to return (default: 2000)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val path = args["path"] as? String
                ?: return mapOf("success" to false, "error" to "path is required")
            val serverId = args["server_id"] as? String

            val resolved = resolveServerId(serverId)
            if (resolved.isFailure) return mapOf("success" to false, "error" to resolved.exceptionOrNull()?.message)
            val sid = resolved.getOrThrow()

            val escapedPath = escapeShellArg(path)
            val offset = (args["offset"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1
            val limit = (args["limit"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 2000

            val totalResult = requireManager().executeCommand(sid, "awk 'END{print NR}' $escapedPath 2>/dev/null || wc -l < $escapedPath 2>/dev/null || echo 0", 15)
            val totalLines = totalResult.stdout.trim().toIntOrNull() ?: 0

            if (totalLines == 0) {
                val exists = requireManager().executeCommand(sid, "test -f $escapedPath && echo 1 || echo 0", 10)
                if (exists.stdout.trim() != "1") {
                    return mapOf("success" to false, "error" to "File not found: $path")
                }
            }

            val readCmd = "awk 'NR>=$offset && NR<${offset + limit} {print NR\": \"\$0}' $escapedPath 2>/dev/null || " +
                "sed -n '$offset,${offset + limit - 1}p' $escapedPath 2>/dev/null"
            val contentResult = requireManager().executeCommand(sid, readCmd, 30)
            if (contentResult.exitCode != 0) {
                return mapOf(
                    "success" to false,
                    "error" to "Failed to read file: ${truncate(contentResult.stderr)}",
                )
            }

            val truncated = truncate(contentResult.stdout)
            val displayContent = truncated.trimEnd('\n')
            return mapOf(
                "success" to true,
                "path" to path,
                "content" to truncated,
                "line_count" to if (displayContent.isEmpty()) 0 else displayContent.count { it == '\n' } + 1,
                "total_lines" to totalLines,
                "offset" to offset,
                "limit" to limit,
            )
        }
    }

    val writeFileTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_write_file",
            description = "Write content to a file on a remote SSH server. " +
                "Creates the file if it doesn't exist, overwrites if it does. " +
                "Creates parent directories automatically. " +
                "Uses base64 encoding to safely handle special characters. " +
                "Prefer this over ssh_execute_command with heredocs for writing file content.",
            parameters = mapOf(
                "path" to ParameterSchema("string", "Absolute path to the remote file", true),
                "content" to ParameterSchema("string", "File content to write", true),
                "server_id" to ParameterSchema("string", "Server ID (uses the only active connection if not specified)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val path = args["path"] as? String
                ?: return mapOf("success" to false, "error" to "path is required")
            val content = args["content"] as? String
                ?: return mapOf("success" to false, "error" to "content is required")
            if (content.length > 14_500) {
                return mapOf("success" to false, "error" to "Content too large (${content.length} chars). Max: ~14KB (base64 output capped at 20KB)")
            }
            val serverId = args["server_id"] as? String

            val resolved = resolveServerId(serverId)
            if (resolved.isFailure) return mapOf("success" to false, "error" to resolved.exceptionOrNull()?.message)
            val sid = resolved.getOrThrow()

            val escapedPath = escapeShellArg(path)
            val encoded = Base64.encode(content.encodeToByteArray())

            val parentDir = path.substringBeforeLast('/', "")
            if (parentDir.isNotBlank()) {
                requireManager().executeCommand(sid, "mkdir -p ${escapeShellArg(parentDir)}", 15)
            }

            val writeCmd = "printf '%s' ${
                escapeShellArg(encoded)
            } | base64 -d > $escapedPath 2>/dev/null || printf '%s' ${
                escapeShellArg(encoded)
            } | base64 -D > $escapedPath 2>/dev/null"
            val result = requireManager().executeCommand(sid, writeCmd, 30)

            return if (result.exitCode == 0) {
                mapOf(
                    "success" to true,
                    "path" to path,
                    "chars_written" to content.length,
                )
            } else {
                mapOf(
                    "success" to false,
                    "error" to "Failed to write file: ${truncate(result.stderr)}",
                )
            }
        }
    }

    val editFileTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_edit_file",
            description = "Edit a remote file by finding and replacing exact text. " +
                "Uses ssh_read_file first to see the current content before editing. " +
                "Downloads the file, performs find-and-replace in memory, then uploads the result. " +
                "The old_string must match exactly including whitespace and indentation. " +
                "Files larger than ~14KB cannot be edited (base64 output capped at 20KB).",
            parameters = mapOf(
                "path" to ParameterSchema("string", "Absolute path to the remote file", true),
                "old_string" to ParameterSchema("string", "Exact text to find and replace", true),
                "new_string" to ParameterSchema("string", "Replacement text", true),
                "server_id" to ParameterSchema("string", "Server ID (uses the only active connection if not specified)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val path = args["path"] as? String
                ?: return mapOf("success" to false, "error" to "path is required")
            val oldString = args["old_string"] as? String
                ?: return mapOf("success" to false, "error" to "old_string is required")
            val newString = args["new_string"] as? String
                ?: return mapOf("success" to false, "error" to "new_string is required")
            val serverId = args["server_id"] as? String

            val resolved = resolveServerId(serverId)
            if (resolved.isFailure) return mapOf("success" to false, "error" to resolved.exceptionOrNull()?.message)
            val sid = resolved.getOrThrow()

            val escapedPath = escapeShellArg(path)

            val sizeResult = requireManager().executeCommand(sid, "wc -c < $escapedPath 2>/dev/null || echo 0", 10)
            val fileSize = sizeResult.stdout.trim().toLongOrNull() ?: 0
            if (fileSize > 14_500) {
                return mapOf("success" to false, "error" to "File too large for editing ($fileSize bytes). Max: ~14KB (limited by 20KB output cap + base64 overhead)")
            }

            val readCmd = "cat $escapedPath | base64 2>/dev/null || cat $escapedPath | base64 -e 2>/dev/null"
            val readResult = requireManager().executeCommand(sid, readCmd, 30)
            if (readResult.exitCode != 0) {
                return mapOf("success" to false, "error" to "Failed to read file: ${truncate(readResult.stderr)}")
            }

            val remoteContent = try {
                Base64.decode(readResult.stdout.trim().encodeToByteArray()).decodeToString()
            } catch (e: Exception) {
                return mapOf("success" to false, "error" to "Failed to decode remote file content: ${e.message}")
            }

            val index = remoteContent.indexOf(oldString)
            if (index == -1) {
                val preview = if (oldString.length <= 60) oldString else oldString.take(60) + "..."
                return mapOf(
                    "success" to false,
                    "error" to "old_string not found in file. First 60 chars: '$preview'",
                )
            }

            val beforeLine = remoteContent.substring(0, index).count { it == '\n' } + 1
            val newContent = remoteContent.replaceFirst(oldString, newString)
            val encodedNew = Base64.encode(newContent.encodeToByteArray())

            val writeCmd = "printf '%s' ${escapeShellArg(encodedNew)} | base64 -d > $escapedPath 2>/dev/null || " +
                "printf '%s' ${escapeShellArg(encodedNew)} | base64 -D > $escapedPath 2>/dev/null"
            val writeResult = requireManager().executeCommand(sid, writeCmd, 30)

            return if (writeResult.exitCode == 0) {
                mapOf(
                    "success" to true,
                    "path" to path,
                    "type" to "edit",
                    "replaced_at_line" to beforeLine,
                    "chars_replaced" to oldString.length,
                    "chars_written" to newString.length,
                )
            } else {
                mapOf(
                    "success" to false,
                    "error" to "Failed to write edited file: ${truncate(writeResult.stderr)}",
                )
            }
        }
    }

    val listDirectoryTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_list_directory",
            description = "List the contents of a directory on a remote SSH server. " +
                "Returns file names, types (file/dir), sizes in bytes, and permissions. " +
                "Results are capped at 200 entries.",
            parameters = mapOf(
                "path" to ParameterSchema("string", "Absolute path to the remote directory", true),
                "server_id" to ParameterSchema("string", "Server ID (uses the only active connection if not specified)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val path = args["path"] as? String
                ?: return mapOf("success" to false, "error" to "path is required")
            val serverId = args["server_id"] as? String

            val resolved = resolveServerId(serverId)
            if (resolved.isFailure) return mapOf("success" to false, "error" to resolved.exceptionOrNull()?.message)
            val sid = resolved.getOrThrow()

            val escapedPath = escapeShellArg(path)

            val cmd = "if [ ! -d $escapedPath ]; then echo ___NOT_A_DIR___; exit 0; fi;" +
                " ls -1a $escapedPath 2>/dev/null | head -n 202 | while IFS= read -r f; do" +
                " if [ -z \"\$f\" ] || [ \"\$f\" = \".\" ] || [ \"\$f\" = \"..\" ]; then continue; fi;" +
                " if [ -d \"$escapedPath/\$f\" ]; then t=dir; elif [ -f \"$escapedPath/\$f\" ]; then t=file; else t=other; fi;" +
                " s=\$(stat -c '%s' \"$escapedPath/\$f\" 2>/dev/null || stat -f '%z' \"$escapedPath/\$f\" 2>/dev/null || echo 0);" +
                " echo \"\$f|\$t|\$s\";" +
                " done"

            val listResult = requireManager().executeCommand(sid, cmd, 30)
            if (listResult.stdout.trim() == "___NOT_A_DIR___") {
                return mapOf("success" to false, "error" to "Directory not found: $path")
            }
            if (listResult.exitCode != 0) {
                return mapOf("success" to false, "error" to "Failed to list directory: ${truncate(listResult.stderr)}")
            }

            val entries = listResult.stdout.lines().filter { it.contains('|') }.map { line ->
                val parts = line.split('|', limit = 3)
                mapOf(
                    "name" to parts.getOrElse(0) { "" },
                    "type" to parts.getOrElse(1) { "other" },
                    "size" to (parts.getOrElse(2) { "0" }.toLongOrNull() ?: 0),
                )
            }

            return mapOf(
                "success" to true,
                "path" to path,
                "entry_count" to entries.size,
                "entries" to entries,
            )
        }
    }

    val deletePathTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_delete_path",
            description = "Delete a file or directory on a remote SSH server. " +
                "For directories, set recursive=true to delete non-empty directories. " +
                "Use with caution — this permanently removes the remote path.",
            parameters = mapOf(
                "path" to ParameterSchema("string", "Absolute path to the remote file or directory", true),
                "recursive" to ParameterSchema("boolean", "Recursively delete directories (rm -rf). Default: false", false),
                "server_id" to ParameterSchema("string", "Server ID (uses the only active connection if not specified)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val path = args["path"] as? String
                ?: return mapOf("success" to false, "error" to "path is required")
            val recursive = args["recursive"] as? Boolean ?: false
            val serverId = args["server_id"] as? String

            val resolved = resolveServerId(serverId)
            if (resolved.isFailure) return mapOf("success" to false, "error" to resolved.exceptionOrNull()?.message)
            val sid = resolved.getOrThrow()

            val escapedPath = escapeShellArg(path)

            val existsCmd = "test -e $escapedPath && echo 1 || echo 0"
            val existsResult = requireManager().executeCommand(sid, existsCmd, 10)
            if (existsResult.stdout.trim() != "1") {
                return mapOf("success" to false, "error" to "Path not found: $path")
            }

            val rmCmd = if (recursive) "rm -rf $escapedPath" else "rm -d $escapedPath 2>/dev/null || rmdir $escapedPath 2>/dev/null || rm -f $escapedPath"
            val result = requireManager().executeCommand(sid, rmCmd, 30)

            return if (result.exitCode == 0) {
                mapOf("success" to true, "message" to "Deleted: $path")
            } else {
                val fallback = if (!recursive) {
                    val dirCheck = requireManager().executeCommand(sid, "test -d $escapedPath && echo 1 || echo 0", 10)
                    if (dirCheck.stdout.trim() == "1") ". Use recursive=true for non-empty directories" else ""
                } else {
                    ""
                }
                mapOf("success" to false, "error" to "Failed to delete: ${truncate(result.stderr)}$fallback")
            }
        }
    }

    val makeDirectoryTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_make_directory",
            description = "Create a directory on a remote SSH server. " +
                "Creates parent directories automatically (like mkdir -p). " +
                "Succeeds silently if the directory already exists.",
            parameters = mapOf(
                "path" to ParameterSchema("string", "Absolute path to the remote directory to create", true),
                "server_id" to ParameterSchema("string", "Server ID (uses the only active connection if not specified)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val path = args["path"] as? String
                ?: return mapOf("success" to false, "error" to "path is required")
            val serverId = args["server_id"] as? String

            val resolved = resolveServerId(serverId)
            if (resolved.isFailure) return mapOf("success" to false, "error" to resolved.exceptionOrNull()?.message)
            val sid = resolved.getOrThrow()

            val escapedPath = escapeShellArg(path)
            val result = requireManager().executeCommand(sid, "mkdir -p $escapedPath", 15)

            return if (result.exitCode == 0) {
                mapOf("success" to true, "path" to path, "message" to "Directory created")
            } else {
                mapOf("success" to false, "error" to "Failed to create directory: ${truncate(result.stderr)}")
            }
        }
    }

    val fileInfoTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_file_info",
            description = "Get information about a file or directory on a remote SSH server. " +
                "Returns whether the path exists, its type (file/dir/symlink/other), size, " +
                "permissions, modification time, and owner. " +
                "Use this to check if a file exists before reading or editing.",
            parameters = mapOf(
                "path" to ParameterSchema("string", "Absolute path to the remote file or directory", true),
                "server_id" to ParameterSchema("string", "Server ID (uses the only active connection if not specified)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val path = args["path"] as? String
                ?: return mapOf("success" to false, "error" to "path is required")
            val serverId = args["server_id"] as? String

            val resolved = resolveServerId(serverId)
            if (resolved.isFailure) return mapOf("success" to false, "error" to resolved.exceptionOrNull()?.message)
            val sid = resolved.getOrThrow()

            val escapedPath = escapeShellArg(path)

            val existsResult = requireManager().executeCommand(sid, "test -e $escapedPath && echo 1 || echo 0", 10)
            if (existsResult.stdout.trim() != "1") {
                return mapOf("success" to true, "path" to path, "exists" to false)
            }

            val typeResult = requireManager().executeCommand(
                sid,
                "test -f $escapedPath && echo file || (test -d $escapedPath && echo dir || (test -L $escapedPath && echo symlink || echo other))",
                10,
            )
            val type = typeResult.stdout.trim()

            val sizeResult = requireManager().executeCommand(
                sid,
                "stat -c '%s' $escapedPath 2>/dev/null || stat -f '%z' $escapedPath 2>/dev/null || echo 0",
                10,
            )
            val size = sizeResult.stdout.trim().toLongOrNull() ?: 0

            val permResult = requireManager().executeCommand(
                sid,
                "stat -c '%a|%U|%G' $escapedPath 2>/dev/null || stat -f '%Lp|%Su|%Sg' $escapedPath 2>/dev/null || echo '||'",
                10,
            )
            val parts = permResult.stdout.trim().split('|')
            val permissions = parts.getOrElse(0) { "" }
            val owner = parts.getOrElse(1) { "" }
            val group = parts.getOrElse(2) { "" }

            return mapOf(
                "success" to true,
                "path" to path,
                "exists" to true,
                "type" to type,
                "size" to size,
                "permissions" to permissions,
                "owner" to owner,
                "group" to group,
            )
        }
    }

    val searchFilesTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_search_files",
            description = "Search for files matching a pattern on a remote SSH server. " +
                "Uses the find command with -name pattern matching. " +
                "The pattern supports wildcards like *.txt, *.kt, etc. " +
                "Results are capped at 100 entries. " +
                "Prefer this over ssh_execute_command with find for structured file search.",
            parameters = mapOf(
                "pattern" to ParameterSchema("string", "File name pattern to search for (e.g. *.kt, README*, config.*)", true),
                "path" to ParameterSchema("string", "Directory to search in (default: home directory, use ~ for home)", false),
                "server_id" to ParameterSchema("string", "Server ID (uses the only active connection if not specified)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val pattern = args["pattern"] as? String
                ?: return mapOf("success" to false, "error" to "pattern is required")
            val path = args["path"] as? String ?: "~"
            val serverId = args["server_id"] as? String

            val resolved = resolveServerId(serverId)
            if (resolved.isFailure) return mapOf("success" to false, "error" to resolved.exceptionOrNull()?.message)
            val sid = resolved.getOrThrow()

            // Resolve ~ to $HOME to avoid quoting breaking tilde expansion
            val shellPath = if (path == "~") {
                "\$HOME"
            } else if (path.startsWith("~/")) {
                "\$HOME${escapeShellArg(path.removePrefix("~"))}"
            } else {
                escapeShellArg(path)
            }
            val escapedPattern = escapeShellArg(pattern)

            val cmd = "find $shellPath -name $escapedPattern -type f 2>/dev/null | head -n 100"
            val result = requireManager().executeCommand(sid, cmd, 30)

            val files = result.stdout.lines().filter { it.isNotBlank() }

            return mapOf(
                "success" to true,
                "pattern" to pattern,
                "search_path" to path,
                "file_count" to files.size,
                "files" to files,
            )
        }
    }

    val grepTool: Tool = object : Tool {
        override val schema = ToolSchema(
            name = "ssh_grep",
            description = "Search file contents on a remote SSH server using grep. " +
                "Searches recursively for the given text pattern in files. " +
                "Returns file paths with line numbers and matching lines. " +
                "Results are capped at 100 lines and 20KB total. " +
                "Prefer this over ssh_execute_command with grep for structured content search. " +
                "BSD grep (macOS) falls back to path:line:content format; " +
                "file paths containing colons may be misparsed.",
            parameters = mapOf(
                "pattern" to ParameterSchema("string", "Text pattern to search for (regular expression)", true),
                "path" to ParameterSchema("string", "Directory or file to search in (default: current directory '.')", false),
                "server_id" to ParameterSchema("string", "Server ID (uses the only active connection if not specified)", false),
                "max_results" to ParameterSchema("integer", "Maximum number of matching lines to return (default: 100)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val pattern = args["pattern"] as? String
                ?: return mapOf("success" to false, "error" to "pattern is required")
            val path = args["path"] as? String ?: "."
            val serverId = args["server_id"] as? String
            val maxResults = ((args["max_results"] as? Number)?.toInt() ?: 100).coerceIn(1, 500)

            val resolved = resolveServerId(serverId)
            if (resolved.isFailure) return mapOf("success" to false, "error" to resolved.exceptionOrNull()?.message)
            val sid = resolved.getOrThrow()

            val escapedPath = escapeShellArg(path)
            val escapedPattern = escapeShellArg(pattern)

            val cmd = "grep -rnZ $escapedPattern $escapedPath 2>/dev/null | head -n $maxResults"
            val result = requireManager().executeCommand(sid, cmd, 60)

            val records = result.stdout.split('\n').filter { it.isNotBlank() }

            return mapOf(
                "success" to true,
                "pattern" to pattern,
                "search_path" to path,
                "match_count" to records.size,
                "matches" to records.map { record -> parseGrepMatch(record) },
            )
        }
    }

    private fun parseGrepMatch(record: String): Map<String, Any> {
        val nullIdx = record.indexOf('\u0000')
        if (nullIdx > 0) {
            val filePath = record.substring(0, nullIdx)
            val rest = record.substring(nullIdx + 1)
            val colonIdx = rest.indexOf(':')
            if (colonIdx > 0) {
                val lineNum = rest.substring(0, colonIdx)
                val content = rest.substring(colonIdx + 1)
                return mapOf(
                    "file" to filePath,
                    "line" to (lineNum.toIntOrNull() ?: 0),
                    "content" to truncate(content),
                )
            }
            return mapOf("file" to filePath, "line" to 0, "content" to truncate(rest))
        }
        // BSD grep fallback: path:line:content — find first :digits: separator
        val match = Regex(""":(\d+):""").find(record)
        if (match != null) {
            val filePath = record.substring(0, match.range.first)
            val lineNum = match.groupValues[1]
            val content = record.substring(match.range.last + 1)
            return mapOf(
                "file" to filePath,
                "line" to (lineNum.toIntOrNull() ?: 0),
                "content" to truncate(content),
            )
        }
        return mapOf("raw" to truncate(record))
    }

    val toolDefinitions: List<ToolInfo> = listOf(
        ToolInfo(
            id = "ssh_connect",
            name = "SSH Connect",
            description = "Connect to a remote SSH server",
        ),
        ToolInfo(
            id = "ssh_disconnect",
            name = "SSH Disconnect",
            description = "Disconnect from an SSH server",
        ),
        ToolInfo(
            id = "ssh_execute_command",
            name = "SSH Execute Command",
            description = "Execute a command on a remote SSH server",
        ),
        ToolInfo(
            id = "ssh_status",
            name = "SSH Status",
            description = "Check SSH connection status",
        ),
        ToolInfo(
            id = "ssh_transfer_file",
            name = "SSH Transfer File",
            description = "Upload or download files via SSH",
        ),
        ToolInfo(
            id = "ssh_read_file",
            name = "SSH Read File",
            description = "Read a file on a remote SSH server",
        ),
        ToolInfo(
            id = "ssh_write_file",
            name = "SSH Write File",
            description = "Write content to a file on a remote SSH server",
        ),
        ToolInfo(
            id = "ssh_edit_file",
            name = "SSH Edit File",
            description = "Find and replace text in a remote file",
        ),
        ToolInfo(
            id = "ssh_list_directory",
            name = "SSH List Directory",
            description = "List directory contents on a remote SSH server",
        ),
        ToolInfo(
            id = "ssh_delete_path",
            name = "SSH Delete Path",
            description = "Delete a file or directory on a remote SSH server",
        ),
        ToolInfo(
            id = "ssh_make_directory",
            name = "SSH Make Directory",
            description = "Create a directory on a remote SSH server",
        ),
        ToolInfo(
            id = "ssh_file_info",
            name = "SSH File Info",
            description = "Get metadata about a remote file or directory",
        ),
        ToolInfo(
            id = "ssh_search_files",
            name = "SSH Search Files",
            description = "Find files matching a pattern on a remote SSH server",
        ),
        ToolInfo(
            id = "ssh_grep",
            name = "SSH Grep",
            description = "Search file contents on a remote SSH server",
        ),
    )

    fun getTools(): List<Tool> = listOf(
        connectTool,
        disconnectTool,
        executeCommandTool,
        statusTool,
        transferFileTool,
        readFileTool,
        writeFileTool,
        editFileTool,
        listDirectoryTool,
        deletePathTool,
        makeDirectoryTool,
        fileInfoTool,
        searchFilesTool,
        grepTool,
    )
}
