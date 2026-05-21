package com.oak.app.ssh

import com.oak.app.data.AppSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private val serverIdRegex = Regex("[^a-z0-9]")

class SshServerManager(
    private val appSettings: AppSettings,
    private val clientFactory: () -> SshClient,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val mutex = Mutex()
    private val clients = mutableMapOf<String, SshClient>()

    private var cachedConfigsJson: String? = null
    private var cachedConfigs: List<SshServerConfig> = emptyList()

    fun getServers(): List<SshServerConfig> {
        val jsonStr = appSettings.getSshServersJson()
        if (jsonStr.isBlank()) return emptyList()
        if (jsonStr == cachedConfigsJson) return cachedConfigs
        return try {
            json.decodeFromString<List<SshServerConfig>>(jsonStr).also {
                cachedConfigsJson = jsonStr
                cachedConfigs = it
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveServers(servers: List<SshServerConfig>) {
        val jsonStr = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(SshServerConfig.serializer()),
            servers,
        )
        appSettings.setSshServersJson(jsonStr)
        cachedConfigsJson = jsonStr
        cachedConfigs = servers
    }

    fun addServer(
        name: String,
        host: String,
        port: Int,
        username: String,
        authType: SshAuthType,
        password: String,
        privateKey: String,
        passphrase: String,
    ): SshServerConfig {
        val servers = getServers().toMutableList()
        val id = generateServerId(name, servers)
        val config = SshServerConfig(
            id = id,
            name = name,
            host = host,
            port = port,
            username = username,
            authType = authType,
            password = password,
            privateKey = privateKey,
            passphrase = passphrase,
        )
        servers.add(config)
        saveServers(servers)
        return config
    }

    fun removeServer(serverId: String) {
        val servers = getServers().toMutableList()
        servers.removeAll { it.id == serverId }
        saveServers(servers)
        disconnectClient(serverId)
    }

    fun setServerEnabled(serverId: String, enabled: Boolean) {
        val servers = getServers().toMutableList()
        val index = servers.indexOfFirst { it.id == serverId }
        if (index >= 0) {
            servers[index] = servers[index].copy(isEnabled = enabled)
            saveServers(servers)
        }
        if (!enabled) {
            disconnectClient(serverId)
        }
    }

    suspend fun connectServer(serverId: String): Result<Unit> = mutex.withLock {
        val server = getServers().find { it.id == serverId }
            ?: return@withLock Result.failure(Exception("Server not found: $serverId"))

        clients.remove(serverId)?.disconnect()

        val client = clientFactory()
        return@withLock try {
            client.connect(server)
            clients[serverId] = client
            Result.success(Unit)
        } catch (e: Exception) {
            client.disconnect()
            Result.failure(e)
        }
    }

    fun registerAdhocClient(id: String, client: SshClient) = runBlocking {
        mutex.withLock {
            clients.remove(id)?.disconnect()
            clients[id] = client
        }
    }

    fun isConnected(serverId: String): Boolean = runBlocking {
        mutex.withLock { clients[serverId]?.isConnected == true }
    }

    fun disconnectClient(serverId: String) {
        val client = runBlocking {
            mutex.withLock { clients.remove(serverId) }
        }
        client?.disconnect()
    }

    suspend fun executeCommand(
        serverId: String,
        command: String,
        timeoutSeconds: Long = 30L,
    ): SshCommandResult {
        val client = mutex.withLock { clients[serverId] }
            ?: return SshCommandResult(-1, "", "Not connected to server")
        return client.executeCommand(command, timeoutSeconds)
    }

    suspend fun transferFile(
        serverId: String,
        direction: String,
        localPath: String,
        remotePath: String,
    ): Result<Unit> {
        val client = mutex.withLock { clients[serverId] }
            ?: return Result.failure(Exception("Not connected to server"))
        return if (direction == "upload") {
            client.uploadFile(localPath, remotePath)
        } else {
            client.downloadFile(remotePath, localPath)
        }
    }

    fun getActiveServerIds(): List<String> = runBlocking {
        mutex.withLock { clients.keys.toList() }
    }

    fun getConnectionInfo(serverId: String): SshConnectionInfo? = runBlocking {
        mutex.withLock { clients[serverId]?.connectionInfo }
    }

    suspend fun connectEnabledServers() {
        val enabledServers = getServers().filter { it.isEnabled }
        val alreadyConnected = mutex.withLock { clients.keys.toSet() }
        coroutineScope {
            enabledServers
                .filter { it.id !in alreadyConnected }
                .map { server ->
                    async {
                        try {
                            connectServer(server.id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                    }
                }
                .awaitAll()
        }
    }

    private fun generateServerId(name: String, existing: List<SshServerConfig>): String {
        val base = name.lowercase().replace(serverIdRegex, "_").take(30)
        val existingIds = existing.map { it.id }.toSet()
        if (base !in existingIds) return base
        var counter = 2
        while ("${base}_$counter" in existingIds) counter++
        return "${base}_$counter"
    }
}
