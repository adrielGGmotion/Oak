package com.beer.app

import com.beer.app.data.AppSettings
import com.beer.app.data.ConversationStorage
import com.beer.app.data.DataRepository
import com.beer.app.data.EmailStore
import com.beer.app.data.HeartbeatManager
import com.beer.app.data.MemoryStore
import com.beer.app.data.NotificationStore
import com.beer.app.data.RemoteDataRepository
import com.beer.app.data.SmsDraftStore
import com.beer.app.data.SmsStore
import com.beer.app.data.TaskScheduler
import com.beer.app.data.TaskStore
import com.beer.app.data.ToolExecutor
import com.beer.app.email.EmailPoller
import com.beer.app.inference.createLocalInferenceEngine
import com.beer.app.mcp.McpServerManager
import com.beer.app.network.Requests
import com.beer.app.notifications.NotificationReader
import com.beer.app.sms.SmsPoller
import com.beer.app.sms.SmsReader
import com.beer.app.sms.SmsSender
import com.beer.app.splinterlands.SplinterlandsApi
import com.beer.app.splinterlands.SplinterlandsBattleRunner
import com.beer.app.splinterlands.SplinterlandsStore
import com.beer.app.tools.CalendarPermissionController
import com.beer.app.tools.NotificationListenerController
import com.beer.app.tools.NotificationPermissionController
import com.beer.app.tools.SmsPermissionController
import com.beer.app.tools.SmsSendPermissionController
import com.beer.app.ui.chat.ChatViewModel
import com.beer.app.ui.sandbox.SandboxFileBrowserViewModel
import com.beer.app.ui.sandbox.SandboxPackagesViewModel
import com.beer.app.ui.sandbox.SandboxSessionViewModel
import com.beer.app.ui.settings.SandboxViewModel
import com.beer.app.ui.settings.SettingsViewModel
import com.beer.app.ui.settings.SplinterlandsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CalendarPermissionController> { CalendarPermissionController() }
    single<NotificationPermissionController> { NotificationPermissionController() }
    single<SmsPermissionController> { SmsPermissionController() }
    single<SmsSendPermissionController> { SmsSendPermissionController() }
    single<SmsReader> { SmsReader() }
    single<SmsSender> { SmsSender() }
    single<NotificationListenerController> { NotificationListenerController() }
    single<NotificationReader> { NotificationReader() }
    single<AppSettings> {
        AppSettings(createSecureSettings()).also {
            it.runMigrations(createLegacySettings())
        }
    }
    single<Requests> {
        Requests()
    }
    single<ConversationStorage> {
        ConversationStorage(get())
    }
    single<ToolExecutor> {
        ToolExecutor()
    }
    single<MemoryStore> {
        MemoryStore(get())
    }
    single<TaskStore> {
        TaskStore(get())
    }
    single<EmailStore> {
        EmailStore(get())
    }
    single<EmailPoller> {
        EmailPoller(get<EmailStore>())
    }
    single<SmsStore> {
        SmsStore(get())
    }
    single<SmsPoller> {
        SmsPoller(get<SmsStore>(), get<SmsReader>())
    }
    single<SmsDraftStore> {
        SmsDraftStore(get())
    }
    single<NotificationStore> {
        NotificationStore(get())
    }
    single<SplinterlandsStore> {
        SplinterlandsStore(get())
    }
    single<SplinterlandsApi> {
        SplinterlandsApi()
    }
    single<HeartbeatManager> {
        HeartbeatManager(get(), get(), get(), get())
    }
    single<McpServerManager> {
        McpServerManager(get())
    }
    single<RemoteDataRepository> {
        RemoteDataRepository(
            requests = get(),
            appSettings = get(),
            conversationStorage = get(),
            toolExecutor = get(),
            memoryStore = get(),
            taskStore = get(),
            heartbeatManager = get(),
            emailStore = get(),
            emailPoller = get(),
            smsStore = get(),
            smsPoller = get(),
            smsReader = get(),
            smsPermissionController = get(),
            smsSendPermissionController = get(),
            smsSender = get(),
            smsDraftStore = get(),
            notificationStore = get(),
            notificationListenerController = get(),
            mcpServerManager = get(),
            sandboxController = get(),
            localInferenceEngine = createLocalInferenceEngine(),
        )
    }
    single<DataRepository> { get<RemoteDataRepository>() }
    single<SplinterlandsBattleRunner> {
        SplinterlandsBattleRunner(get(), get(), get<DataRepository>(), get<DaemonController>())
    }
    single<TaskScheduler> {
        TaskScheduler(
            get<DataRepository>(),
            get(),
            get(),
            get(),
            get(),
            get<EmailPoller>(),
            get<SmsStore>(),
            get<SmsPoller>(),
            get<NotificationStore>(),
        )
    }
    single<DaemonController> { createDaemonController() }
    single<SandboxController> { createSandboxController() }
    viewModel { SettingsViewModel(get<DataRepository>(), get<DaemonController>(), get<NotificationPermissionController>(), get<TaskScheduler>()) }
    viewModel { SandboxViewModel(get<DataRepository>(), get<SandboxController>()) }
    viewModel { SandboxFileBrowserViewModel(get<SandboxController>()) }
    viewModel { SandboxPackagesViewModel(get<SandboxController>()) }
    viewModel { SandboxSessionViewModel(get<SandboxController>(), get<DataRepository>()) }
    viewModel { SplinterlandsViewModel(get<DataRepository>(), get(), get(), get<SplinterlandsApi>()) }
    viewModel { ChatViewModel(get<DataRepository>(), get<TaskScheduler>()) }
}
