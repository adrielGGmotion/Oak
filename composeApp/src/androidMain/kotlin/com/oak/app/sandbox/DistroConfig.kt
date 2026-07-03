package com.oak.app.sandbox

import android.os.Build
import java.io.File

enum class Compression {
    GZIP,
    XZ,
}

/**
 * Distro-specific configuration: download URLs, arch mapping, compression,
 * package manager commands, default packages, and rootfs layout details.
 */
data class DistroConfig(
    val distro: Distro,
    /** Download sources tried in order. The first URL that responds is used. */
    val downloadSources: List<String>,
    /** Compression format of the rootfs archive. */
    val compression: Compression,
    /** Package manager commands keyed by operation. */
    val packageManager: PackageManagerDef,
    /** Packages to install on first boot (SETUP = required for shell, BASIC = common tools). */
    val setupPackages: List<String>,
    val basicPackages: List<String>,
    /** Files to check inside rootfs to verify the distro is fully downloaded. */
    val verificationPaths: List<String>,
    /** Arch name for 32-bit ARM (termux uses "arm", Alpine uses "armhf"). */
    private val archArm: String = "armhf",
    /** Arch name for 32-bit x86 (termux uses "i686", Alpine uses "x86"). */
    private val archX86: String = "x86",
) {
    /** Architecture names used by this distro's download URLs. */
    fun mapArch(androidAbi: String): String = when {
        androidAbi.startsWith("arm64") -> "aarch64"
        androidAbi.startsWith("armeabi") -> archArm
        androidAbi.startsWith("x86_64") -> "x86_64"
        androidAbi.startsWith("x86") -> archX86
        else -> "aarch64"
    }
}

/** Package manager commands for a single distro family. */
data class PackageManagerDef(
    val update: String,
    val install: String,
    val uninstall: String,
    val search: String,
    val listInstalled: String,
    val upgrade: String,
    /** Regex to extract name+version from list output lines. */
    val parseInfoLine: (String) -> Pair<String, String>?,
    /** Regex to extract name+version+description from search output lines. */
    val parseSearchLine: (String) -> Triple<String, String, String?>?,
)

/** All distro configurations. */
object DistroConfigs {

    /** Map Android ABI to Alpine arch name. */
    private fun alpineArch(abi: String): String = when {
        abi.startsWith("arm64") -> "aarch64"
        abi.startsWith("armeabi") -> "armhf"
        abi.startsWith("x86_64") -> "x86_64"
        abi.startsWith("x86") -> "x86"
        else -> "aarch64"
    }

    private val ALPINE_VERSION = "3.21.3"
    private val ALPINE_BRANCH = "v3.21"

    private val ALPINE_MIRRORS = listOf(
        "https://dl-cdn.alpinelinux.org/alpine",
        "https://mirrors.edge.kernel.org/alpine",
        "https://ftp.halifax.rwth-aachen.de/alpine",
        "https://alpine.ethz.ch/alpine",
        "https://mirror.csclub.uwaterloo.ca/alpine",
        "https://mirrors.tuna.tsinghua.edu.cn/alpine",
    )

    // Alpine revision suffix: -r<NUMBER>$
    private val ALPINE_REVISION = Regex("-r\\d+\$")

    fun forDistro(distro: Distro): DistroConfig = when (distro) {
        Distro.ALPINE -> DistroConfig(
            distro = distro,
            downloadSources = ALPINE_MIRRORS.map { base ->
                "$base/$ALPINE_BRANCH/releases/{arch}/alpine-minirootfs-$ALPINE_VERSION-{arch}.tar.gz"
            },
            compression = Compression.GZIP,
            packageManager = PackageManagerDef(
                update = "apk update",
                install = "apk add --no-cache",
                uninstall = "apk del",
                search = "apk search -v",
                listInstalled = "apk info -v | sort",
                upgrade = "apk upgrade",
                parseInfoLine = { line ->
                    val s = line.trim()
                    if (s.isEmpty() || s.startsWith("WARNING:") || s.startsWith("ERROR:")) return@PackageManagerDef null
                    val revision = ALPINE_REVISION.find(s)?.value.orEmpty()
                    val withoutRev = if (revision.isNotEmpty()) s.dropLast(revision.length) else s
                    var splitAt = -1
                    for (i in withoutRev.length - 1 downTo 1) {
                        if (withoutRev[i - 1] == '-' && withoutRev[i].isDigit()) {
                            splitAt = i - 1
                            break
                        }
                    }
                    if (splitAt < 0) {
                        return@PackageManagerDef if (revision.isNotEmpty()) withoutRev to revision.trimStart('-') else s to ""
                    }
                    val name = withoutRev.substring(0, splitAt)
                    val version = withoutRev.substring(splitAt + 1) + revision
                    if (name.isEmpty()) null else name to version
                },
                parseSearchLine = { line ->
                    val s = line.trim()
                    if (s.isEmpty() || s.startsWith("WARNING:") || s.startsWith("ERROR:")) return@PackageManagerDef null
                    val sepIdx = s.indexOf(" - ")
                    val nameVer = if (sepIdx >= 0) s.substring(0, sepIdx) else s
                    val description = if (sepIdx >= 0) s.substring(sepIdx + 3).trim() else null
                    val revision = ALPINE_REVISION.find(nameVer)?.value.orEmpty()
                    val withoutRev = if (revision.isNotEmpty()) nameVer.dropLast(revision.length) else nameVer
                    var splitAt = -1
                    for (i in withoutRev.length - 1 downTo 1) {
                        if (withoutRev[i - 1] == '-' && withoutRev[i].isDigit()) {
                            splitAt = i - 1
                            break
                        }
                    }
                    if (splitAt < 0) {
                        val n = if (revision.isNotEmpty()) withoutRev else nameVer
                        return@PackageManagerDef Triple(n, revision.trimStart('-'), description?.takeIf { it.isNotEmpty() })
                    }
                    val name = withoutRev.substring(0, splitAt)
                    val version = withoutRev.substring(splitAt + 1) + revision
                    if (name.isEmpty()) null else Triple(name, version, description?.takeIf { it.isNotEmpty() })
                },
            ),
            setupPackages = listOf("bash", "python3"),
            basicPackages = listOf(
                "bash", "curl", "wget", "git", "jq", "python3", "py3-pip", "nodejs",
                "openssh-client", "lftp", "rsync",
            ),
            verificationPaths = listOf("bin/sh", "etc/os-release"),
            archArm = "armhf",
            archX86 = "x86",
        )

        Distro.DEBIAN -> DistroConfig(
            distro = distro,
            downloadSources = listOf(
                "https://github.com/termux/proot-distro/releases/download/v4.29.0/debian-trixie-{arch}-pd-v4.29.0.tar.xz",
            ),
            compression = Compression.XZ,
            packageManager = PackageManagerDef(
                update = "apt-get update",
                install = "apt-get install -y --no-install-recommends",
                uninstall = "apt-get remove -y",
                search = "apt-cache search --names-only",
                listInstalled = "dpkg-query -W -f=\'\${package} \${version}\\n\' 2>/dev/null | sort",
                upgrade = "apt-get upgrade -y",
                parseInfoLine = { line ->
                    val s = line.trim()
                    if (s.isEmpty()) return@PackageManagerDef null
                    val parts = s.split("\\s+".toRegex(), limit = 2)
                    if (parts.size < 2) return@PackageManagerDef null
                    parts[0] to parts[1]
                },
                parseSearchLine = { line ->
                    val s = line.trim()
                    if (s.isEmpty()) return@PackageManagerDef null
                    val sepIdx = s.indexOf(" - ")
                    val nameVer = if (sepIdx >= 0) s.substring(0, sepIdx) else s
                    val description = if (sepIdx >= 0) s.substring(sepIdx + 3).trim() else null
                    // apt-cache search outputs "<pkgname> - <description>" (no version)
                    Triple(nameVer, "", description?.takeIf { it.isNotEmpty() })
                },
            ),
            setupPackages = listOf("bash", "python3"),
            basicPackages = listOf(
                "bash", "curl", "wget", "git", "jq", "python3", "python3-pip", "nodejs",
                "openssh-client", "lftp", "rsync",
            ),
            verificationPaths = listOf("bin/sh", "bin/bash", "etc/debian_version", "usr/bin/apt-get"),
            archArm = "arm",
            archX86 = "i686",
        )

        Distro.UBUNTU -> DistroConfig(
            distro = distro,
            downloadSources = listOf(
                "https://github.com/termux/proot-distro/releases/download/v4.29.0/ubuntu-plucky-{arch}-pd-v4.29.0.tar.xz",
            ),
            compression = Compression.XZ,
            packageManager = PackageManagerDef(
                update = "apt-get update",
                install = "apt-get install -y --no-install-recommends",
                uninstall = "apt-get remove -y",
                search = "apt-cache search --names-only",
                listInstalled = "dpkg-query -W -f=\'\${package} \${version}\\n\' 2>/dev/null | sort",
                upgrade = "apt-get upgrade -y",
                parseInfoLine = { line ->
                    val s = line.trim()
                    if (s.isEmpty()) return@PackageManagerDef null
                    val parts = s.split("\\s+".toRegex(), limit = 2)
                    if (parts.size < 2) return@PackageManagerDef null
                    parts[0] to parts[1]
                },
                parseSearchLine = { line ->
                    val s = line.trim()
                    if (s.isEmpty()) return@PackageManagerDef null
                    val sepIdx = s.indexOf(" - ")
                    val nameVer = if (sepIdx >= 0) s.substring(0, sepIdx) else s
                    val description = if (sepIdx >= 0) s.substring(sepIdx + 3).trim() else null
                    // apt-cache search outputs "<pkgname> - <description>" (no version)
                    Triple(nameVer, "", description?.takeIf { it.isNotEmpty() })
                },
            ),
            setupPackages = listOf("bash", "python3"),
            basicPackages = listOf(
                "bash", "curl", "wget", "git", "jq", "python3", "python3-pip", "nodejs",
                "openssh-client", "lftp", "rsync",
            ),
            verificationPaths = listOf("bin/sh", "bin/bash", "etc/lsb-release", "usr/bin/apt-get"),
            archArm = "arm",
            archX86 = "i686",
        )

        Distro.ARCH -> DistroConfig(
            distro = distro,
            downloadSources = listOf(
                "https://github.com/termux/proot-distro/releases/download/v4.34.2/archlinux-{arch}-pd-v4.34.2.tar.xz",
            ),
            compression = Compression.XZ,
            packageManager = PackageManagerDef(
                update = "pacman -Sy",
                install = "pacman -S --noconfirm",
                uninstall = "pacman -R --noconfirm",
                search = "pacman -Ss",
                listInstalled = "pacman -Q",
                upgrade = "pacman -Su --noconfirm",
                parseInfoLine = { line ->
                    val s = line.trim()
                    if (s.isEmpty()) return@PackageManagerDef null
                    // pacman -Q outputs: "<name> <version>"
                    val parts = s.split("\\s+".toRegex(), limit = 2)
                    if (parts.size < 2) return@PackageManagerDef null
                    parts[0] to parts[1]
                },
                parseSearchLine = { line ->
                    val s = line.trim()
                    if (s.isEmpty()) return@PackageManagerDef null
                    // pacman -Ss outputs: "core/<name> <version>  [installed]\n    <description>"
                    // Remove "core/" prefix and "[installed]" suffix
                    val cleaned = s.replaceFirst("^[^/]+/".toRegex(), "")
                        .replace("\\s*\\[installed\\]\$".toRegex(), "")
                        .trim()
                    val sepIdx = cleaned.indexOf(" ")
                    if (sepIdx < 0) return@PackageManagerDef Triple(cleaned, "", null)
                    val name = cleaned.substring(0, sepIdx)
                    val rest = cleaned.substring(sepIdx + 1).trim()
                    val version = rest.takeWhile { !it.isWhitespace() }
                    Triple(name, version, null)
                },
            ),
            setupPackages = listOf("bash", "python"),
            basicPackages = listOf(
                "bash", "curl", "wget", "git", "jq", "python", "python-pip", "nodejs",
                "openssh", "lftp", "rsync",
            ),
            verificationPaths = listOf("bin/sh", "bin/bash", "etc/arch-release", "usr/bin/pacman"),
            archArm = "arm",
            archX86 = "i686",
        )
    }

    /** Resolve download URLs by replacing {arch} placeholder. */
    fun resolveUrls(config: DistroConfig, androidAbi: String): List<String> {
        val arch = config.mapArch(androidAbi)
        return config.downloadSources.map { it.replace("{arch}", arch) }
    }

    /** Get the Android ABI string for the current device. */
    fun getAndroidArch(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
}
