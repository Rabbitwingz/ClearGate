package io.github.rabbitwingz.cleargate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Settings: banking apps, pausing helpers, setup, privacy and about. Opened from the gear on the home screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(status: SetupStatus, onBack: () -> Unit, onReplayIntro: () -> Unit) {
    val context = LocalContext.current
    val helpers = rememberPauseHelpers(status)
    val setReminders = rememberRemindersSwitch(status)
    var confirmClear by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "banking") { BankingSection() }

            item(key = "pausingHeader") { SectionHeader("Pausing") }
            item(key = "pausing") {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Resume reminders",
                        body = "While paused, show a Resume button in your notifications, and remind you after " +
                            "10 minutes.",
                    ) {
                        Switch(checked = helpers.remindersOn, onCheckedChange = setReminders)
                    }
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.ToggleOn,
                        title = "Quick Settings tile",
                        body = if (PauseControl.canPromptToAddTile || helpers.tileAdded) {
                            "Pause and resume from the notification shade."
                        } else {
                            TILE_HOW_TO
                        },
                    ) {
                        if (helpers.tileAdded) Added()
                        else FilledTonalButton(onClick = { addPauseTile(context) }) {
                            Text(if (PauseControl.canPromptToAddTile) "Add" else "Done")
                        }
                    }
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Widgets,
                        title = "Home-screen widget",
                        body = "See whether ClearGate is on, and pause or resume, at a glance.",
                    ) {
                        if (helpers.widgetAdded) Added()
                        else FilledTonalButton(onClick = { addWidget(context) }) { Text("Add") }
                    }
                }
            }

            item(key = "setupHeader") { SectionHeader("Setup") }
            item(key = "setup") { SetupSteps(status) }
            if (!status.serviceOn && !Store.isPaused(context) && restrictedSettingsApply) {
                item(key = "restricted") { RestrictedSettingsHint(onOpen = { SystemScreens.appInfo(context) }) }
            }

            item(key = "privacyHeader") { SectionHeader("Privacy") }
            item(key = "privacy") {
                SettingsCard {
                    SettingsRow(Icons.Rounded.Lock, "Only sees MyGate", "Every other app is invisible to ClearGate.")
                    RowDivider()
                    SettingsRow(
                        Icons.Rounded.WifiOff, "No internet access",
                        "ClearGate can't send anything anywhere. Its activity log stays on your phone.",
                    )
                    RowDivider()
                    SettingsRow(
                        Icons.Rounded.PauseCircle, "Off when you need it",
                        "Pausing switches ClearGate's accessibility fully off.",
                    )
                }
            }

            item(key = "aboutHeader") { SectionHeader("About") }
            item(key = "about") {
                SettingsCard {
                    SettingsRow(Icons.Rounded.School, "How it works", "Replay the intro.", onClick = onReplayIntro) {
                        Chevron()
                    }
                    RowDivider()
                    SettingsRow(
                        Icons.Rounded.DeleteSweep, "Clear activity", "Removes the activity list on the home screen.",
                        onClick = { confirmClear = true },
                    ) { Chevron() }
                    RowDivider()
                    SettingsRow(
                        Icons.Rounded.Code, "Source code", "ClearGate is open source on GitHub.",
                        onClick = { SystemScreens.web(context, REPO_URL) },
                    ) { Chevron() }
                    RowDivider()
                    SettingsRow(Icons.Rounded.Info, "Version", BuildConfig.VERSION_NAME)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("Clear activity?") },
            text = { Text("This removes the activity list. Your ads-skipped count stays.") },
            confirmButton = { TextButton(onClick = { Store.clearLog(context); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp, end = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun Added() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text("Added", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
