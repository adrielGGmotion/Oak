package com.oak.app

interface DaemonController {
    fun start()
    fun stop()
}

expect fun createDaemonController(): DaemonController
