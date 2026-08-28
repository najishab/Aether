package com.najishab.aether.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.os.Process
import com.najishab.aether.R
import com.najishab.aether.core.AetherController
import com.najishab.aether.core.AnnouncementManager
import com.najishab.aether.core.IpEndpoint
import com.najishab.aether.model.ConnectionProfile
import com.najishab.aether.model.ConnectionState
import com.najishab.aether.model.isBusy
import com.najishab.aether.model.isConnected
import com.najishab.aether.ui.components.AmbientBackground
import com.najishab.aether.ui.components.AnnouncementBanner
import com.najishab.aether.ui.components.ButtonMode
import com.najishab.aether.ui.components.ConnectButton
import com.najishab.aether.ui.components.ConnectionCard
import com.najishab.aether.ui.components.DiagnosticsPanel
import com.najishab.aether.ui.theme.AetherBlue
import com.najishab.aether.ui.theme.AetherDanger
import com.najishab.aether.ui.theme.AetherSuccess
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.app.Activity
import android.content.Intent
import com.najishab.aether.ChangelogActivity
import com.najishab.aether.LiveGraphActivity
import com.najishab.aether.UsageCalendarActivity
import com.najishab.aether.data.ThemeMode
import androidx.compose.material.icons.rounded.History

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: ConnectionState,
    profile: ConnectionProfile,
    connectedSince: Long?,
    ipInfo: IpEndpoint?,
    ipLoading: Boolean,
    onProfileChange: (ConnectionProfile) -> Unit,
    onToggleConnection: () -> Unit,
    themeMode: ThemeMode = ThemeMode.DARK,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val mode = when {
        state.isConnected -> ButtonMode.CONNECTED
        state.isBusy -> ButtonMode.BUSY
        state is ConnectionState.Error -> ButtonMode.ERROR
        else -> ButtonMode.IDLE
    }

    val accent = when (mode) {
        // Same neon green the connection card and its animated edge use once
        // connected, so the whole screen reads as one palette.
        ButtonMode.CONNECTED -> AetherSuccess
        ButtonMode.ERROR -> AetherDanger
        else -> AetherBlue
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val exitScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = drawerState.isOpen) {
        drawerScope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen) {
        showExitConfirmDialog = true
    }
    // 1.2.2 UI-SPEED FIX: ModalNavigationDrawer composes its drawer content
    // even while the drawer is CLOSED, so the diagnostics, share, advanced and
    // about cards were live at all times — recomposing on every profile change
    // and on every log line, behind a panel nobody was looking at. They are now
    // only composed while the drawer is open or opening.
    val drawerVisible = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open

    // "More" panel (theme, live graph, usage calendar), reachable directly
    // from the home screen (top-right). Advanced settings moved into the
    // Drawer only - see drawerContent below.
    var showMoreSheet by remember { mutableStateOf(false) }
    val moreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsEnabled = state is ConnectionState.Idle || state is ConnectionState.Error

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(20.dp))

                    if (drawerVisible) {
                        DiagnosticsPanel()

                        Spacer(Modifier.height(16.dp))

                        SharePanel(
                            state = state,
                            profile = profile,
                            onProfileChange = onProfileChange,
                        )

                        Spacer(Modifier.height(16.dp))

                        AdvancedPanel(
                            profile = profile,
                            onProfileChange = onProfileChange,
                            enabled = settingsEnabled,
                        )

                        Spacer(Modifier.height(16.dp))

                        AboutPanel()

                        Spacer(Modifier.height(16.dp))

                        ChangelogRow()

                        Spacer(Modifier.height(16.dp))

                        LanguageSwitcher()
                    }
                }
            }
        },
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            AmbientBackground(accent = accent, active = state.isConnected)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                val currentAnnouncement by AnnouncementManager.current.collectAsState()
                currentAnnouncement?.let { announcement ->
                    AnnouncementBanner(
                        announcement = announcement,
                        onDismiss = { AnnouncementManager.dismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                }

                val appName = stringResource(R.string.app_name)

                Text(
                    text = buildAnnotatedString {
                        when (appName) {
                            "NajiAether" -> {
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                ) {
                                    append("Naji")
                                }

                                withStyle(
                                    SpanStyle(color = AetherBlue)
                                ) {
                                    append("Aether")
                                }
                            }

                            "ناجی ایثر" -> {
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                ) {
                                    append("ناجی")
                                }

                                append(" ")

                                withStyle(
                                    SpanStyle(color = AetherBlue)
                                ) {
                                    append("ایثر")
                                }
                            }

                            else -> {
                                append(appName)
                            }
                        }
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                ConnectButton(mode = mode, onClick = onToggleConnection)

                Spacer(Modifier.height(28.dp))

                // 1.2.6: status, timer, IP, speeds and the protocol row used to
                // be four separate floating surfaces here. They are one unified
                // glass card now - see ConnectionCard.
                ConnectionCard(
                    connected = state.isConnected,
                    statusTitle = stateTitle(state),
                    statusCaption = stateSubtitle(state),
                    connectedSince = connectedSince,
                    ipInfo = ipInfo,
                    ipLoading = ipLoading,
                    error = state is ConnectionState.Error,
                )

                Spacer(Modifier.height(16.dp))
            }

            IconButton(
                onClick = { drawerScope.launch { drawerState.open() } },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.menu_open),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }

            // "More" panel straight from the home screen (theme, live graph,
            // usage calendar). Advanced settings live in the Drawer only.
            IconButton(
                onClick = { showMoreSheet = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.more_open),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }

    if (showMoreSheet) {
        val moreContext = LocalContext.current
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = moreSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
            ) {
                MorePanel(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenLiveGraph = {
                        moreContext.startActivity(Intent(moreContext, LiveGraphActivity::class.java))
                    },
                    onOpenUsageCalendar = {
                        moreContext.startActivity(Intent(moreContext, UsageCalendarActivity::class.java))
                    },
                )
            }
        }
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text(text = stringResource(R.string.exit_confirm_title)) },
            text = { Text(text = stringResource(R.string.exit_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmDialog = false
                    exitScope.launch {
                        // If the VPN is connected (or mid-connect/disconnect),
                        // tear it down properly before the process dies —
                        // otherwise the native tunnel thread and TUN fd are
                        // left dangling instead of a clean disconnect.
                        if (state.isConnected || state.isBusy) {
                            AetherController.disconnect(context)
                            delay(700)
                        }
                        // Force-close, not background: finish the task AND
                        // kill the process, exactly like the system's
                        // "force stop" — otherwise the foreground service
                        // keeps the app alive after the activity closes.
                        activity?.finishAndRemoveTask()
                        Process.killProcess(Process.myPid())
                    }
                }) {
                    Text(text = stringResource(R.string.exit_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text(text = stringResource(R.string.exit_confirm_no))
                }
            },
        )
    }
}

@Composable
private fun ChangelogRow() {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(Intent(context, ChangelogActivity::class.java))
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.changelog_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LanguageSwitcher() {
    val context = LocalContext.current
    val currentLocale = AppCompatDelegate.getApplicationLocales()[0]?.language ?: "en"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val newLocale = if (currentLocale == "fa") "en" else "fa"
                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(newLocale)
                AppCompatDelegate.setApplicationLocales(appLocale)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = stringResource(R.string.language_setting),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.change_language_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.language_name),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun stateTitle(state: ConnectionState): String = when (state) {
    is ConnectionState.Idle -> stringResource(R.string.state_idle)
    is ConnectionState.Launching -> stringResource(R.string.state_launching)
    is ConnectionState.Connecting -> stringResource(R.string.state_connecting)
    is ConnectionState.Verifying -> stringResource(R.string.state_verifying)
    is ConnectionState.Connected -> stringResource(R.string.state_connected)
    is ConnectionState.Reconnecting -> stringResource(R.string.state_reconnecting)
    is ConnectionState.Disconnecting -> stringResource(R.string.state_disconnecting)
    is ConnectionState.Error -> stringResource(R.string.state_error)
}

@Composable
private fun stateSubtitle(state: ConnectionState): String = when (state) {
    is ConnectionState.Idle -> stringResource(R.string.tap_to_connect)
    // The exit IP + flag is shown inside the card, so keep the subtitle generic
    // instead of leaking the internal 127.0.0.1:port address.
    is ConnectionState.Connected -> stringResource(R.string.tap_to_disconnect)
    is ConnectionState.Reconnecting ->
        stringResource(R.string.reconnect_attempt, state.attempt, state.maxAttempts)
    is ConnectionState.Error -> state.message
    else -> stringResource(R.string.tap_to_disconnect)
}
