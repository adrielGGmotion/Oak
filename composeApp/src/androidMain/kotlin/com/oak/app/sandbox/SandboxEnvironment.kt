package com.oak.app.sandbox

import java.io.File

sealed class SandboxEnvironment(
    val id: String,
    val displayName: String,
    val packageManager: PackageManager,
    val defaultPackages: List<String>,
    val extraProotArgs: List<String> = emptyList(),
) {
    /** Generate download URLs for a given CPU arch (aarch64, armhf, x86_64). */
    abstract fun getDownloadUrls(arch: String): List<String>

    /** First-boot commands to configure the package manager. */
    abstract val firstBootCommands: List<String>

    /** Paths to check for arePackagesInstalled() */
    abstract val installedCheckPaths: List<String>

    data object Alpine : SandboxEnvironment(
        id = "alpine",
        displayName = "Alpine Linux",
        packageManager = AlpinePackageManager(),
        defaultPackages = listOf("bash", "curl", "wget", "git", "jq", "python3", "py3-pip", "nodejs",
            "openssh-client", "lftp", "rsync"),
    ) {
        private const val VERSION = "3.21.3"
        private const val BRANCH = "v3.21"

        override val firstBootCommands: List<String> get() {
            val base = ALPINE_MIRRORS.first()
            return listOf(
                "mkdir -p /etc/apk",
                "printf '$base/$BRANCH/main\\n$base/$BRANCH/community\\n' > /etc/apk/repositories",
                "apk update",
            )
        }

        override val installedCheckPaths: List<String>
            get() = listOf("usr/bin/python3", "usr/bin/ssh")

        private val ALPINE_MIRRORS = listOf(
            "https://dl-cdn.alpinelinux.org/alpine",
            "https://mirrors.edge.kernel.org/alpine",
            "https://ftp.halifax.rwth-aachen.de/alpine",
            "https://alpine.ethz.ch/alpine",
            "https://mirror.csclub.uwaterloo.ca/alpine",
            "https://mirrors.tuna.tsinghua.edu.cn/alpine",
        )

        override fun getDownloadUrls(arch: String): List<String> = ALPINE_MIRRORS.map { base ->
            "$base/$BRANCH/releases/$arch/alpine-minirootfs-$VERSION-$arch.tar.gz"
        }
    }

    data object Debian : SandboxEnvironment(
        id = "debian",
        displayName = "Debian",
        packageManager = AptPackageManager(),
        defaultPackages = listOf("bash", "curl", "wget", "git", "jq", "python3", "python3-pip",
            "nodejs", "openssh-client", "lftp", "rsync"),
        extraProotArgs = listOf("--link2symlink"),
    ) {
        // See https://github.com/termux-user-repository/proot-distro-rootfs
        private const val ROOTFS_RELEASE = "v1.0"

        override val firstBootCommands: List<String>
            get() = listOf("apt-get update")

        override val installedCheckPaths: List<String>
            get() = listOf("usr/bin/python3", "usr/bin/ssh")

        override fun getDownloadUrls(arch: String): List<String> = listOf(
            "https://github.com/termux-user-repository/proot-distro-rootfs/releases/download/$ROOTFS_RELEASE/debian-rootfs-$arch.tar.gz",
        )
    }

    data object Ubuntu : SandboxEnvironment(
        id = "ubuntu",
        displayName = "Ubuntu",
        packageManager = UbuntuPackageManager(),
        defaultPackages = listOf("bash", "curl", "wget", "git", "jq", "python3", "python3-pip",
            "nodejs", "openssh-client", "lftp", "rsync"),
        extraProotArgs = listOf("--link2symlink"),
    ) {
        private const val ROOTFS_RELEASE = "v1.0"

        override val firstBootCommands: List<String>
            get() = listOf("apt-get update")

        override val installedCheckPaths: List<String>
            get() = listOf("usr/bin/python3", "usr/bin/ssh")

        override fun getDownloadUrls(arch: String): List<String> = listOf(
            "https://github.com/termux-user-repository/proot-distro-rootfs/releases/download/$ROOTFS_RELEASE/ubuntu-rootfs-$arch.tar.gz",
        )
    }

    data object ArchLinux : SandboxEnvironment(
        id = "arch",
        displayName = "Arch Linux",
        packageManager = PacmanPackageManager(),
        defaultPackages = listOf("bash", "curl", "wget", "git", "jq", "python", "python-pip",
            "nodejs", "openssh", "lftp", "rsync"),
        extraProotArgs = listOf("--link2symlink"),
    ) {
        private const val ROOTFS_RELEASE = "v1.0"

        override val firstBootCommands: List<String>
            get() = listOf(
                "pacman-key --init 2>/dev/null; pacman-key --populate archlinux 2>/dev/null; pacman -Sy --noconfirm",
            )

        override val installedCheckPaths: List<String>
            get() = listOf("usr/bin/python3", "usr/bin/ssh")

        override fun getDownloadUrls(arch: String): List<String> = listOf(
            "https://github.com/termux-user-repository/proot-distro-rootfs/releases/download/$ROOTFS_RELEASE/archlinux-rootfs-$arch.tar.gz",
        )
    }

    companion object {
        val ALL: List<SandboxEnvironment> = listOf(Alpine, Debian, Ubuntu, ArchLinux)
        val DEFAULT: SandboxEnvironment = Alpine

        fun fromId(id: String): SandboxEnvironment =
            ALL.firstOrNull { it.id == id } ?: DEFAULT

        /** Path under app internal storage for this distro's sandbox files. */
        fun storageDirName(id: String): String = "sandboxes/$id"
    }
}
