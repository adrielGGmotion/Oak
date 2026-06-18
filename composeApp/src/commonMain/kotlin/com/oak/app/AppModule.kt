package com.oak.app

import com.oak.app.data.AppSettings
import com.oak.app.data.AskQuestionsManager
import com.oak.app.data.ConversationStorage
import com.oak.app.data.DataRepository
import com.oak.app.data.EmailStore
import com.oak.app.data.HeartbeatManager
import com.oak.app.data.MemoryStore
import com.oak.app.data.NotificationStore
import com.oak.app.data.RemoteDataRepository
import com.oak.app.data.SmsDraftStore
import com.oak.app.data.SmsStore
import com.oak.app.data.TaskScheduler
import com.oak.app.data.TaskStore
import com.oak.app.data.ToolExecutor
import com.oak.app.email.EmailPoller
import com.oak.app.inference.createLocalInferenceEngine
import com.oak.app.mcp.McpServerManager
import com.oak.app.network.Requests
import com.oak.app.notifications.NotificationReader
import com.oak.app.sms.SmsPoller
import com.oak.app.sms.SmsReader
import com.oak.app.sms.SmsSender
import com.oak.app.ssh.SshServerManager
import com.oak.app.tools.CalendarPermissionController
import com.oak.app.tools.NotificationListenerController
import com.oak.app.tools.NotificationPermissionController
import com.oak.app.tools.SmsPermissionController
import com.oak.app.tools.SmsSendPermissionController
import com.oak.app.tools.SshTools
import com.oak.app.tools.StoragePermissionController
import com.oak.app.ui.chat.ChatSessionManager
import com.oak.app.ui.chat.ChatViewModel
import com.oak.app.ui.sandbox.SandboxFileBrowserViewModel
import com.oak.app.ui.sandbox.SandboxPackagesViewModel
import com.oak.app.ui.sandbox.SandboxSessionViewModel
import com.oak.app.ui.settings.SandboxViewModel
import com.oak.app.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CalendarPermissionController> { CalendarPermissionController() }
    single<NotificationPermissionController> { NotificationPermissionController() }
    single<SmsPermissionController> { SmsPermissionController() }
    single<SmsSendPermissionController> { SmsSendPermissionController() }
    single<StoragePermissionController> { StoragePermissionController() }
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
    single<AskQuestionsManager> {
        AskQuestionsManager()
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
    single<HeartbeatManager> {
        HeartbeatManager(get(), get(), get(), get())
    }
    single<McpServerManager> {
        McpServerManager(get())
    }
    single<SshServerManager> {
        SshServerManager(get()) { createSshClient() }.also { SshTools.init(it) }
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
            sshServerManager = get(),
            sandboxController = get(),
            localInferenceEngine = createLocalInferenceEngine(),
        )
    }
    single<DataRepository> { get<RemoteDataRepository>() }
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
    single { ChatSessionManager(get<DataRepository>()) }
    viewModel { SettingsViewModel(get<DataRepository>(), get<DaemonController>(), get<NotificationPermissionController>(), get<StoragePermissionController>(), get<TaskScheduler>()) }
    viewModel { SandboxViewModel(get<DataRepository>(), get<SandboxController>()) }
    viewModel { SandboxFileBrowserViewModel(get<SandboxController>()) }
    viewModel { SandboxPackagesViewModel(get<SandboxController>()) }
    viewModel { SandboxSessionViewModel(get<SandboxController>(), get<DataRepository>()) }
    viewModel { ChatViewModel(get<DataRepository>(), get<TaskScheduler>(), get<ChatSessionManager>(), get<AskQuestionsManager>()) }
}
