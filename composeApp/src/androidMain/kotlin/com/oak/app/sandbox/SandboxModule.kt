package com.oak.app.sandbox

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val sandboxModule = module {
    single<ProotDistroManager> { ProotDistroManager(androidContext()) }
    single<LinuxSandboxManager> { LinuxSandboxManager(androidContext(), get(), get(), get()) }
}
