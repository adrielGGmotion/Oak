package com.oak.app.sandbox

object ProotConfig {
    /** Extra environment variables needed by some distributions. */
    fun envFor(env: SandboxEnvironment): Map<String, String> = when (env) {
        is SandboxEnvironment.ArchLinux -> mapOf(
            "PACMAN" to "/usr/bin/pacman",
        )
        else -> emptyMap()
    }
}
