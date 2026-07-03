package com.oak.app.ui.settings

/**
 * Temporary string constants for sandbox-related labels.
 *
 * These replace generated `Res.string.*` references that cannot be resolved by the Kotlin
 * K2 compiler in CI when defined via generated Compose Resources accessor files.
 * Once the underlying issue is resolved (CM plugin or Kotlin K2 fix), migrate back.
 */
object DistroLabels {
    const val SELECT_DISTRIBUTION = "Select distribution"
    const val STATUS_ACTIVE = "Active"
    const val STATUS_DOWNLOADED = "Downloaded"
    const val STATUS_NOT_DOWNLOADED = "Not downloaded"
    const val REMOVE = "Remove"
    const val ALPINE = "Alpine Linux"
    const val DEBIAN = "Debian"
    const val UBUNTU = "Ubuntu"
    const val ARCH = "Arch Linux"
    const val SANDBOX_TAB = "Linux Sandbox"
}
