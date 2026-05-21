package com.oak.app.tools

import com.oak.app.createSshClient
import com.oak.app.network.tools.ParameterSchema
import com.oak.app.network.tools.Tool
import com.oak.app.network.tools.ToolInfo
import com.oak.app.network.tools.ToolSchema
import com.oak.app.ssh.SshAuthType
import com.oak.app.ssh.SshServerConfig
import com.oak.app.ssh.SshServerManager

object SshTools {

    private var serverManager: SshServerManager? = null

    fun init(manager: SshServerManager) {
        serverManager = manager
    }

    private fun requireManager(): SshServerManager =
        serverManager ?: error("SshTools not initialized")

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
            val tempConfig = SshServerConfig(
                id = "_adhoc_${host}_$port",
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

            val activeIds = requireManager().getActiveServerIds()
            if (activeIds.isEmpty()) {
                return mapOf("success" to false, "error" to "No active SSH connections. Use ssh_connect first.")
            }

            val targetId = serverId ?: activeIds.first()
            if (targetId !in activeIds) {
                return mapOf("success" to false, "error" to "Not connected to: $targetId. Active: ${activeIds.joinToString()}")
            }

            val result = requireManager().executeCommand(targetId, command, timeout)
            return mapOf(
                "success" to (result.exitCode == 0),
                "exit_code" to result.exitCode,
                "stdout" to result.stdout,
                "stderr" to result.stderr,
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
                "Uses SCP protocol. Requires an active SSH connection.",
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

            val activeIds = requireManager().getActiveServerIds()
            if (activeIds.isEmpty()) {
                return mapOf("success" to false, "error" to "No active SSH connections")
            }
            val targetId = serverId ?: activeIds.first()

            val result = when (direction.lowercase()) {
                "upload" -> requireManager().transferFile(targetId, "upload", localPath, remotePath)
                "download" -> requireManager().transferFile(targetId, "download", localPath, remotePath)
                else -> return mapOf("success" to false, "error" to "direction must be 'upload' or 'download'")
            }

            return if (result.isSuccess) {
                mapOf("success" to true, "message" to "File ${direction}ed successfully")
            } else {
                mapOf("success" to false, "error" to (result.exceptionOrNull()?.message ?: "Transfer failed"))
            }
        }
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
    )

    fun getTools(): List<Tool> = listOf(
        connectTool,
        disconnectTool,
        executeCommandTool,
        statusTool,
        transferFileTool,
    )
}
