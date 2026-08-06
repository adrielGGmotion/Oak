package com.inspiredandroid.oak

interface DaemonController {
    fun start()
    fun stop()
}

expect fun createDaemonController(): DaemonController
