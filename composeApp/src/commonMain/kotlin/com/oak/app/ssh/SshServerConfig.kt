package com.oak.app.ssh

import kotlinx.serialization.Serializable

@Serializable
enum class SshAuthType {
    Password,
    Key,
}

@Serializable
data class SshServerConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: SshAuthType = SshAuthType.Password,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val isEnabled: Boolean = true,
)

enum class SshConnectionStatus {
    Unknown,
    Connecting,
    Connected,
    Disconnected,
    Error,
}

data class SshServerState(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: SshAuthType,
    val isEnabled: Boolean,
    val connectionStatus: SshConnectionStatus,
    val errorMessage: String? = null,
)
