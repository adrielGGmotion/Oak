package com.inspiredandroid.oak

import com.inspiredandroid.oak.data.AppSettings
import com.inspiredandroid.oak.data.ConversationStorage
import com.inspiredandroid.oak.data.DataRepository
import com.inspiredandroid.oak.data.EmailStore
import com.inspiredandroid.oak.data.HeartbeatManager
import com.inspiredandroid.oak.data.MemoryStore
import com.inspiredandroid.oak.data.NotificationStore
import com.inspiredandroid.oak.data.RemoteDataRepository
import com.inspiredandroid.oak.data.SmsDraftStore
import com.inspiredandroid.oak.data.SmsStore
import com.inspiredandroid.oak.data.TaskScheduler
import com.inspiredandroid.oak.data.TaskStore
import com.inspiredandroid.oak.data.ToolExecutor
import com.inspiredandroid.oak.data.createConversationPersistence
import com.inspiredandroid.oak.data.runMigrations
import com.inspiredandroid.oak.email.EmailPoller
import com.inspiredandroid.oak.inference.createLocalInferenceEngine
import com.inspiredandroid.oak.mcp.McpServerManager
import com.inspiredandroid.oak.network.Requests
import com.inspiredandroid.oak.notifications.NotificationReader
import com.inspiredandroid.oak.skills.SkillManager
import com.inspiredandroid.oak.sms.SmsPoller
import com.inspiredandroid.oak.sms.SmsReader
import com.inspiredandroid.oak.sms.SmsSender
import com.inspiredandroid.oak.tools.AppPermission
import com.inspiredandroid.oak.tools.NotificationListenerController
import com.inspiredandroid.oak.tools.PermissionController
import com.inspiredandroid.oak.ui.build.OakBuildViewModel
import com.inspiredandroid.oak.ui.chat.ChatViewModel
import com.inspiredandroid.oak.ui.sandbox.SandboxFileBrowserViewModel
import com.inspiredandroid.oak.ui.sandbox.SandboxPackagesViewModel
import com.inspiredandroid.oak.ui.sandbox.SandboxSessionViewModel
import com.inspiredandroid.oak.ui.settings.SandboxViewModel
import com.inspiredandroid.oak.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Picks the file browser bound to Oak Build's Debian rather than the chat sandbox. */
val OAK_BUILD_FILES = named("oakBuildFiles")

/** Qualifier for the [PermissionController] singleton handling [permission]. */
fun permissionQualifier(permission: AppPermission) = named(permission.name)

val appModule = module {
    AppPermission.entries.forEach { permission ->
        single(permissionQualifier(permission)) { PermissionController(permission) }
    }
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
        ConversationStorage(get(), createConversationPersistence(get()))
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
    single<HeartbeatManager> {
        HeartbeatManager(get(), get(), get(), get())
    }
    single<McpServerManager> {
        McpServerManager(get())
    }
    single<SkillManager> {
        SkillManager(get<SandboxController>())
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
            smsPermissionController = get(permissionQualifier(AppPermission.READ_SMS)),
            smsSendPermissionController = get(permissionQualifier(AppPermission.SEND_SMS)),
            smsSender = get(),
            smsDraftStore = get(),
            notificationStore = get(),
            notificationListenerController = get(),
            mcpServerManager = get(),
            skillManager = get(),
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
    single<OakBuildController> { createOakBuildController() }
    viewModel { SettingsViewModel(get<DataRepository>(), get<DaemonController>(), get(permissionQualifier(AppPermission.POST_NOTIFICATIONS)), get<TaskScheduler>(), localNetworkPermissionController = get(permissionQualifier(AppPermission.LOCAL_NETWORK))) }
    viewModel { SandboxViewModel(get<DataRepository>(), get<SandboxController>()) }
    viewModel { SandboxFileBrowserViewModel(get<SandboxController>()) }
    viewModel { SandboxPackagesViewModel(get<SandboxController>()) }
    viewModel { SandboxSessionViewModel(get<SandboxController>(), get<DataRepository>()) }
    viewModel { OakBuildViewModel(get<OakBuildController>()) }
    // Same browser, second environment: Oak Build's Debian instead of the chat sandbox.
    viewModel(OAK_BUILD_FILES) { SandboxFileBrowserViewModel(get<OakBuildController>().files) }
    viewModel { ChatViewModel(get<DataRepository>(), get<TaskScheduler>(), localNetworkPermissionController = get(permissionQualifier(AppPermission.LOCAL_NETWORK))) }
}
