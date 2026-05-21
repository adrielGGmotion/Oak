package com.oak.app.ssh

import org.apache.sshd.client.SshClient as MinaSshClient
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.SftpClientFactory
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class SshClientImpl : SshClient {

    private var client: MinaSshClient? = null
    private var session: ClientSession? = null

    override var connectionInfo: SshConnectionInfo? = null
        private set

    override val isConnected: Boolean
        get() = session?.isOpen == true

    override suspend fun connect(config: SshServerConfig): Result<Unit> = runCatching {
        disconnect()

        val sshClient = MinaSshClient.setUpDefaultClient()

        if (config.authType == SshAuthType.Key && config.privateKey.isNotBlank()) {
            val passwordProvider = if (config.passphrase.isNotBlank()) {
                FilePasswordProvider.of(config.passphrase)
            } else null
            MinaSshClient.setKeyPairProvider(
                sshClient,
                Paths.get(config.privateKey),
                false, true, passwordProvider,
            )
        }

        sshClient.start()

        val sshSession = sshClient
            .connect(config.username, config.host, config.port)
            .verify(15, TimeUnit.SECONDS)
            .session

        if (config.authType == SshAuthType.Password && config.password.isNotBlank()) {
            sshSession.addPasswordIdentity(config.password)
        }

        sshSession.auth().verify(15, TimeUnit.SECONDS)

        this.client = sshClient
        this.session = sshSession
        connectionInfo = SshConnectionInfo(
            host = config.host,
            port = config.port,
            username = config.username,
        )
    }

    override fun disconnect() {
        try {
            session?.close(true)
        } catch (_: Exception) {
        }
        try {
            client?.stop()
        } catch (_: Exception) {
        }
        session = null
        client = null
        connectionInfo = null
    }

    override suspend fun executeCommand(command: String, timeoutSeconds: Long): SshCommandResult {
        val s = session ?: return SshCommandResult(-1, "", "Not connected")
        val channel = s.createExecChannel(command)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        channel.setOut(stdout)
        channel.setErr(stderr)
        try {
            channel.open().verify(timeoutSeconds, TimeUnit.SECONDS)
            val events = channel.waitFor(
                setOf(ClientChannelEvent.CLOSED),
                TimeUnit.SECONDS.toMillis(timeoutSeconds),
            )
            if (events.contains(ClientChannelEvent.TIMEOUT)) {
                channel.close(true)
                return SshCommandResult(-1, stdout.toString(), "Command timed out after ${timeoutSeconds}s")
            }
        } catch (e: Exception) {
            channel.close(true)
            return SshCommandResult(-1, stdout.toString(), stderr.toString() + "\n" + (e.message ?: "Command failed"))
        }
        val exitCode = channel.exitStatus ?: -1
        channel.close(true)
        return SshCommandResult(
            exitCode = exitCode,
            stdout = stdout.toString(),
            stderr = stderr.toString(),
        )
    }

    override suspend fun uploadFile(localPath: String, remotePath: String): Result<Unit> = runCatching {
        val s = session ?: throw IllegalStateException("Not connected")
        val sftp: SftpClient = SftpClientFactory.instance().createSftpClient(s)
        try {
            sftp.write(remotePath).use { os ->
                Files.newInputStream(Paths.get(localPath)).use { ins ->
                    ins.transferTo(os)
                }
            }
        } finally {
            sftp.close()
        }
    }

    override suspend fun downloadFile(remotePath: String, localPath: String): Result<Unit> = runCatching {
        val s = session ?: throw IllegalStateException("Not connected")
        val sftp: SftpClient = SftpClientFactory.instance().createSftpClient(s)
        try {
            sftp.read(remotePath).use { ins ->
                Files.newOutputStream(Paths.get(localPath)).use { os ->
                    ins.transferTo(os)
                }
            }
        } finally {
            sftp.close()
        }
    }
}
