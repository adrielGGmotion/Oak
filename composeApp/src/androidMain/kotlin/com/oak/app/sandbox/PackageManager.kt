package com.oak.app.sandbox

interface PackageManager {
    val name: String
    fun install(pkg: String): String
    fun remove(pkg: String): String
    fun update(): String
    fun upgrade(): String
    fun search(query: String): String
    fun listInstalled(): String
}

class AlpinePackageManager : PackageManager {
    override val name = "apk"
    override fun install(pkg: String) = "apk add --no-cache ${pkg.shellQuote()}"
    override fun remove(pkg: String) = "apk del ${pkg.shellQuote()}"
    override fun update() = "apk update"
    override fun upgrade() = "apk upgrade"
    override fun search(query: String) = "apk search -v ${query.shellQuote()}"
    override fun listInstalled() = "apk info -v"
}

open class AptPackageManager : PackageManager {
    override val name = "apt"
    override fun install(pkg: String) = "DEBIAN_FRONTEND=noninteractive apt-get install -y ${pkg.shellQuote()}"
    override fun remove(pkg: String) = "apt-get remove -y ${pkg.shellQuote()}"
    override fun update() = "apt-get update"
    override fun upgrade() = "apt-get upgrade -y"
    override fun search(query: String) = "apt-cache search ${query.shellQuote()}"
    override fun listInstalled() = "dpkg-query -W"
}

class UbuntuPackageManager : AptPackageManager()

class PacmanPackageManager : PackageManager {
    override val name = "pacman"
    override fun install(pkg: String) = "pacman -S --noconfirm ${pkg.shellQuote()}"
    override fun remove(pkg: String) = "pacman -R --noconfirm ${pkg.shellQuote()}"
    override fun update() = "pacman -Sy"
    override fun upgrade() = "pacman -Syu --noconfirm"
    override fun search(query: String) = "pacman -Ss ${query.shellQuote()}"
    override fun listInstalled() = "pacman -Q"
}

private fun String.shellQuote(): String = "'" + this.replace("'", "'\\''") + "'"
