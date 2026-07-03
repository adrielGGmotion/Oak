package com.oak.app.sandbox

enum class Distro(val id: String) {
    ALPINE("alpine"),
    DEBIAN("debian"),
    UBUNTU("ubuntu"),
    ARCH("arch");

    companion object {
        fun fromId(id: String): Distro = entries.firstOrNull { it.id == id } ?: ALPINE
    }
}
