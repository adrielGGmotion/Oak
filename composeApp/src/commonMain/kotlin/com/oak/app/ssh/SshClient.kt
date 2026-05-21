package com.oak.app.ssh

data class SshCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

data class SshConnectionInfo(
    val host: String,
    val port: Int = 22,
    val username: String,
)

interface SshClient {
    val connectionInfo: SshConnectionInfo?
    val isConnected: Boolean
    suspend fun connect(config: SshServerConfig): Result<Unit>
    fun disconnect()
    suspend fun executeCommand(command: String, timeoutSeconds: Long = 30L): SshCommandResult
    suspend fun uploadFile(localPath: String, remotePath: String): Result<Unit>
    suspend fun downloadFile(remotePath: String, localPath: String): Result<Unit>
}
