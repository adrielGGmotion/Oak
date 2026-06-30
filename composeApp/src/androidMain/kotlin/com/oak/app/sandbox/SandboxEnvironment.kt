package com.oak.app.sandbox

import java.io.File

enum class Compression {
    Gzip,
    Xz,
}

sealed class SandboxEnvironment(
    val id: String,
    val displayName: String,
    val packageManager: PackageManager,
    val defaultPackages: List<String>,
    val compression: Compression,
    val extraProotArgs: List<String> = emptyList(),
) {
    /** Generate download URLs for a given CPU arch (aarch64, armhf, x86_64, x86). */
    abstract fun getDownloadUrls(arch: String): List<String>

    /** Map the standard Linux arch name to this distro's naming convention. */
    open fun mapArch(arch: String): String = arch

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
        compression = Compression.Gzip,
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
        compression = Compression.Xz,
        extraProotArgs = listOf("--link2symlink"),
    ) {
        // Rootfs tarballs from termux/proot-distro
        private const val ROOTFS_TAG = "v4.29.0"
        private const val ROOTFS_RELEASE = "debian-trixie"

        override val firstBootCommands: List<String>
            get() = listOf("apt-get update")

        override val installedCheckPaths: List<String>
            get() = listOf("usr/bin/python3", "usr/bin/ssh")

        override fun mapArch(arch: String): String = when (arch) {
            "armhf" -> "arm"
            "x86" -> "i686"
            else -> arch
        }

        override fun getDownloadUrls(arch: String): List<String> = listOf(
            "https://github.com/termux/proot-distro/releases/download/$ROOTFS_TAG/${ROOTFS_RELEASE}-$arch-pd-$ROOTFS_TAG.tar.xz",
        )
    }

    data object Ubuntu : SandboxEnvironment(
        id = "ubuntu",
        displayName = "Ubuntu",
        packageManager = UbuntuPackageManager(),
        defaultPackages = listOf("bash", "curl", "wget", "git", "jq", "python3", "python3-pip",
            "nodejs", "openssh-client", "lftp", "rsync"),
        compression = Compression.Xz,
        extraProotArgs = listOf("--link2symlink"),
    ) {
        // Rootfs tarballs from termux/proot-distro
        private const val ROOTFS_TAG = "v4.29.0"
        private const val ROOTFS_RELEASE = "ubuntu-plucky"

        override val firstBootCommands: List<String>
            get() = listOf("apt-get update")

        override val installedCheckPaths: List<String>
            get() = listOf("usr/bin/python3", "usr/bin/ssh")

        override fun mapArch(arch: String): String = when (arch) {
            "armhf" -> "arm"
            "x86" -> "i686"
            else -> arch
        }

        override fun getDownloadUrls(arch: String): List<String> = listOf(
            "https://github.com/termux/proot-distro/releases/download/$ROOTFS_TAG/${ROOTFS_RELEASE}-$arch-pd-$ROOTFS_TAG.tar.xz",
        )
    }

    data object ArchLinux : SandboxEnvironment(
        id = "arch",
        displayName = "Arch Linux",
        packageManager = PacmanPackageManager(),
        defaultPackages = listOf("bash", "curl", "wget", "git", "jq", "python", "python-pip",
            "nodejs", "openssh", "lftp", "rsync"),
        compression = Compression.Xz,
        extraProotArgs = listOf("--link2symlink"),
    ) {
        // Rootfs tarballs from termux/proot-distro
        private const val ROOTFS_TAG = "v4.34.2"
        private const val ROOTFS_RELEASE = "archlinux"

        override val firstBootCommands: List<String>
            get() = listOf(
                "pacman-key --init 2>/dev/null; pacman-key --populate archlinux 2>/dev/null; pacman -Sy --noconfirm",
            )

        override val installedCheckPaths: List<String>
            get() = listOf("usr/bin/python3", "usr/bin/ssh")

        override fun mapArch(arch: String): String = when (arch) {
            "armhf" -> "arm"
            "x86" -> "i686"
            else -> arch
        }

        override fun getDownloadUrls(arch: String): List<String> = listOf(
            "https://github.com/termux/proot-distro/releases/download/$ROOTFS_TAG/${ROOTFS_RELEASE}-$arch-pd-$ROOTFS_TAG.tar.xz",
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
