@file:OptIn(ExperimentalMaterial3Api::class)

package com.oak.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.oak.app.data.AppSettings
import com.oak.app.data.ThemeMode
import com.oak.app.ssh.SshServerManager
import com.oak.app.tools.CalendarPermissionController
import com.oak.app.tools.NotificationPermissionController
import com.oak.app.tools.SetupCalendarPermissionHandler
import com.oak.app.tools.SetupNotificationPermissionHandler
import com.oak.app.tools.SetupSmsPermissionHandler
import com.oak.app.tools.SetupStoragePermissionHandler
import com.oak.app.tools.SetupSmsSendPermissionHandler
import com.oak.app.tools.SmsPermissionController
import com.oak.app.tools.SmsSendPermissionController
import com.oak.app.tools.StoragePermissionController
import com.oak.app.ui.OakTheme
import com.oak.app.ui.chat.ChatScreen
import com.oak.app.ui.chat.ChatViewModel
import com.oak.app.ui.chat.ConversationSummary
import com.oak.app.ui.components.FullScreenImageHost
import com.oak.app.ui.components.LogoAnimation
import com.oak.app.ui.handCursor
import com.oak.app.ui.settings.SettingsScreen
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents.Companion.Format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.experimental.ExperimentalVoiceApi
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.drawer_artifacts
import oak.composeapp.generated.resources.drawer_cancel
import oak.composeapp.generated.resources.drawer_chats
import oak.composeapp.generated.resources.drawer_delete
import oak.composeapp.generated.resources.drawer_delete_conversation_text
import oak.composeapp.generated.resources.drawer_delete_conversation_title
import oak.composeapp.generated.resources.drawer_free_plan
import oak.composeapp.generated.resources.drawer_more_options
import oak.composeapp.generated.resources.drawer_new_chat
import oak.composeapp.generated.resources.drawer_no_conversations
import oak.composeapp.generated.resources.drawer_projects
import oak.composeapp.generated.resources.drawer_settings
import oak.composeapp.generated.resources.drawer_user
import oak.composeapp.generated.resources.tab_chat
import oak.composeapp.generated.resources.tab_settings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration

@Serializable
@SerialName("home")
object Home

@Serializable
@SerialName("settings")
object Settings

@Composable
fun App(
    navController: NavHostController,
    textToSpeech: TextToSpeechInstance? = null,
    isKoinStarted: Boolean = false,
) {
    setSingletonImageLoaderFactory { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .build()
    }

    // Reuse global Koin if already started (Android Application class),
    // otherwise create a new instance (iOS, Desktop, Wasm).
    if (isKoinStarted) {
        AppContent(navController, textToSpeech)
    } else {
        KoinApplication(
            configuration = koinConfiguration {
                modules(appModule)
            },
        ) {
            AppContent(navController, textToSpeech)
        }
    }
}

@Composable
private fun AppContent(
    navController: NavHostController,
    textToSpeech: TextToSpeechInstance?,
) {
    val appSettings = koinInject<AppSettings>()

    // Set up permission handlers
    val calendarPermissionController = koinInject<CalendarPermissionController>()
    SetupCalendarPermissionHandler(calendarPermissionController)

    val notificationPermissionController = koinInject<NotificationPermissionController>()
    SetupNotificationPermissionHandler(notificationPermissionController)

    val smsPermissionController = koinInject<SmsPermissionController>()
    SetupSmsPermissionHandler(smsPermissionController)

    val smsSendPermissionController = koinInject<SmsSendPermissionController>()
    SetupSmsSendPermissionHandler(smsSendPermissionController)

    val storagePermissionController = koinInject<StoragePermissionController>()
    SetupStoragePermissionHandler(storagePermissionController)

    // Set TTS voice to match system language
    @OptIn(ExperimentalVoiceApi::class)
    LaunchedEffect(textToSpeech) {
        val tts = textToSpeech ?: return@LaunchedEffect
        val systemLanguage = Locale.current.language
        val matchingVoice = tts.voices
            .firstOrNull { it.languageTag.startsWith(systemLanguage) }
        if (matchingVoice != null) {
            tts.currentVoice = matchingVoice
        }
    }

    // Auto-connect enabled SSH servers on startup, clean up stale ad-hoc clients
    val sshManager = koinInject<SshServerManager>()
    LaunchedEffect(Unit) {
        sshManager.connectEnabledServers()
        sshManager.cleanupStaleAdhocClients()
    }

    val uiScale by appSettings.uiScaleFlow.collectAsStateWithLifecycle()
    val defaultDensity = LocalDensity.current
    val scaledDensity = remember(defaultDensity, uiScale) {
        Density(defaultDensity.density * uiScale, defaultDensity.fontScale)
    }

    val themeMode by appSettings.themeModeFlow.collectAsStateWithLifecycle()
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.System -> systemInDark
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.OledBlack -> true
    }
    val useDynamicColors by appSettings.useDynamicColorsFlow.collectAsStateWithLifecycle()
    val fontFamily by appSettings.fontFamilyFlow.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        OakTheme(
            useDynamicColors = useDynamicColors,
            darkTheme = isDark,
            isOledBlack = themeMode == ThemeMode.OledBlack,
            fontFamily = fontFamily,
        ) {
            FullScreenImageHost {
                val chatViewModel: ChatViewModel = koinViewModel()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val chatState by chatViewModel.state.collectAsStateWithLifecycle()
                val filteredConversations = remember(chatState.savedConversations, chatState.pendingConversationDeletion) {
                    val pendingId = chatState.pendingConversationDeletion
                    if (pendingId != null) chatState.savedConversations.filter { it.id != pendingId }.toImmutableList() else chatState.savedConversations
                }
                val keyboardController = LocalSoftwareKeyboardController.current
                LaunchedEffect(drawerState.targetValue) {
                    if (drawerState.targetValue == DrawerValue.Open) {
                        keyboardController?.hide()
                    }
                }
                val showTabBar = currentPlatform !is Platform.Mobile
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val isHome = currentBackStackEntry?.destination?.route == "home"

                val navigationTabBar: @Composable () -> Unit = {
                    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    val count = 2
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = isHome,
                            onClick = {
                                navController.navigate(Home) {
                                    popUpTo(Home) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = if (isRtl) count - 1 else 0, count = count),
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(stringResource(Res.string.tab_chat))
                        }
                        SegmentedButton(
                            selected = !isHome,
                            onClick = {
                                navController.navigate(Settings) {
                                    popUpTo(Home)
                                    launchSingleTop = true
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = if (isRtl) 0 else count - 1, count = count),
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(stringResource(Res.string.tab_settings))
                        }
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = isHome,
                    drawerContent = {
                        ModalDrawerSheet {
                            var deleteTarget by remember { mutableStateOf<ConversationSummary?>(null) }

                            Column(Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LogoAnimation(size = 36.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Oak",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }

                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    label = { Text(stringResource(Res.string.drawer_new_chat)) },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        chatState.actions.startNewChat()
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        unselectedContainerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                )

                                Spacer(Modifier.height(4.dp))

                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            Icons.Outlined.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    label = { Text(stringResource(Res.string.drawer_projects)) },
                                    selected = false,
                                    onClick = {},
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        unselectedContainerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                )

                                Spacer(Modifier.height(4.dp))

                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    label = { Text(stringResource(Res.string.drawer_artifacts)) },
                                    selected = false,
                                    onClick = {},
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        unselectedContainerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                )

                                Spacer(Modifier.height(8.dp))

                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    item {
                                        Text(
                                            text = stringResource(Res.string.drawer_chats),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        )
                                    }

                                    if (filteredConversations.isEmpty()) {
                                        item {
                                            Text(
                                                text = stringResource(Res.string.drawer_no_conversations),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                                            )
                                        }
                                    } else {
                                        items(filteredConversations, key = { it.id }) { conversation ->
                                            val isActive = conversation.id == chatState.currentConversationId
                                            val isGenerating = conversation.id in chatState.generatingSessionIds
                                            Column {
                                                NavigationDrawerItem(
                                                    icon = if (conversation.isHeartbeat) {
                                                        {
                                                            Icon(
                                                                Icons.Outlined.History,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(18.dp),
                                                                tint = MaterialTheme.colorScheme.tertiary,
                                                            )
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                    label = {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                    val fallbackTitle = stringResource(Res.string.drawer_new_chat)
                                                    Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = conversation.title.ifEmpty { fallbackTitle },
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                                    color = if (isActive) {
                                                                        MaterialTheme.colorScheme.primary
                                                                    } else {
                                                                        MaterialTheme.colorScheme.onSurface
                                                                    },
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                                Text(
                                                                    text = formatDate(conversation.updatedAt),
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                )
                                                            }
                                                            var showMenu by remember { mutableStateOf(false) }
                                                            Box {
                                                                IconButton(
                                                                    onClick = { showMenu = true },
                                                                    modifier = Modifier.size(36.dp),
                                                                ) {
                                                                    Icon(
                                                                        Icons.Outlined.MoreVert,
                                                                        contentDescription = stringResource(Res.string.drawer_more_options),
                                                                        modifier = Modifier.size(18.dp),
                                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    )
                                                                }
                                                                DropdownMenu(
                                                                    expanded = showMenu,
                                                                    onDismissRequest = { showMenu = false },
                                                                ) {
                                                                    DropdownMenuItem(
                                                                text = { Text(stringResource(Res.string.drawer_delete)) },
                                                                    onClick = {
                                                                        showMenu = false
                                                                        deleteTarget = conversation
                                                                    },
                                                                        leadingIcon = {
                                                                            Icon(
                                                                                Icons.Outlined.Delete,
                                                                                contentDescription = null,
                                                                                modifier = Modifier.size(18.dp),
                                                                            )
                                                                        },
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    },
                                                    selected = isActive,
                                                    onClick = {
                                                        scope.launch { drawerState.close() }
                                                        chatState.actions.loadConversation(conversation.id)
                                                    },
                                                )
                                                if (isGenerating) {
                                                    LinearProgressIndicator(
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(Res.string.drawer_user),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(Res.string.drawer_free_plan),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(
                                        modifier = Modifier.handCursor(),
                                        onClick = {
                                            scope.launch { drawerState.close() }
                                            navController.navigate(Settings)
                                        },
                                    ) {
                                        Icon(
                                            Icons.Outlined.Settings,
                                            contentDescription = stringResource(Res.string.drawer_settings),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            deleteTarget?.let { target ->
                                AlertDialog(
                                    onDismissRequest = { deleteTarget = null },
                                    title = { Text(stringResource(Res.string.drawer_delete_conversation_title)) },
                                    text = { Text(stringResource(Res.string.drawer_delete_conversation_text)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            chatState.actions.deleteConversation(target.id)
                                            deleteTarget = null
                                        }) {
                                            Text(stringResource(Res.string.drawer_delete))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { deleteTarget = null }) {
                                            Text("Cancel")
                                        }
                                    },
                                )
                            }
                        }
                    },
                ) {
                    NavHost(
                        navController,
                        startDestination = Home,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    ) {
                        composable<Home> {
                            ChatScreen(
                                viewModel = chatViewModel,
                                textToSpeech = textToSpeech,
                                onNavigateToSettings = {
                                    navController.navigate(Settings)
                                },
                                isSandboxAvailable = currentPlatform is Platform.Mobile.Android,
                                navigationTabBar = if (showTabBar) navigationTabBar else null,
                                onToggleDrawer = {
                                    keyboardController?.hide()
                                    scope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                },
                            )
                        }
                        composable<Settings> {
                            if (showTabBar) {
                                DisposableEffect(Unit) {
                                    onDispose {
                                        chatViewModel.refreshSettings()
                                    }
                                }
                            }
                            SettingsScreen(
                                onNavigateBack = {
                                    chatViewModel.refreshSettings()
                                    navController.navigateUp()
                                },
                                navigationTabBar = if (showTabBar) navigationTabBar else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String = try {
    kotlin.time.Instant.fromEpochMilliseconds(epochMillis).format(
        Format {
            day()
            char(' ')
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            char(' ')
            year()
        },
    )
} catch (_: Exception) {
    ""
}
