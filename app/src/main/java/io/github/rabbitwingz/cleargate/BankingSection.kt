package io.github.rabbitwingz.cleargate

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything that makes banking-app pauses quick: "Pause & open" bank shortcuts, plus one-tap setup for resume
 * reminders, the Quick Settings tile and the home-screen widget. Setup rows disappear once done.
 */
@Composable
fun BankingSection(status: SetupStatus, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    // Re-read on preference changes (tile prompt result) and on resume (permission or widget dialogs closing).
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val prefs = Store.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LifecycleResumeEffect(Unit) {
        version++
        onPauseOrDispose { }
    }
    val banks = remember(version) { BankShortcuts.banks(context) }
    val tileAdded = remember(version) { Store.isTileAdded(context) }
    val widgetAdded = remember(version) {
        AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, ClearGateWidget::class.java))
            ?.isNotEmpty() ?: true
    }
    var picking by remember { mutableStateOf(false) }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) SystemScreens.appNotifications(context)
    }

    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountBalance, null, tint = colors.primary)
                Spacer(Modifier.width(12.dp))
                Text("Banking apps", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Some banking apps won't open while ClearGate is on. Open them from here to pause ClearGate " +
                    "automatically, then tap Resume in the notification when you're done.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )

            if (banks.isNotEmpty()) Spacer(Modifier.height(8.dp))
            banks.forEach { app ->
                BankRow(
                    app = app,
                    onOpen = {
                        context.startActivity(
                            Intent(context, BankLaunchActivity::class.java)
                                .putExtra(BankLaunchActivity.EXTRA_PACKAGE, app.packageName)
                        )
                    },
                    onPin = {
                        if (!BankShortcuts.pinToHomeScreen(context, app)) {
                            Toast.makeText(context, "Your launcher doesn't support this", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemove = {
                        BankShortcuts.remove(context, app.packageName)
                        version++
                    },
                )
            }
            if (banks.size < BankShortcuts.MAX_BANKS) {
                TextButton(onClick = { picking = true }) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (banks.isEmpty()) "Add a banking app" else "Add another")
                }
            }

            val setupRows = listOfNotNull(
                if (!status.notificationsAllowed) SetupRow(
                    Icons.Rounded.NotificationsActive, "Resume reminders",
                    "A Resume button in your notifications while paused, and a nudge after 10 minutes.", "Allow",
                ) {
                    if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else SystemScreens.appNotifications(context)
                } else null,
                if (!tileAdded) SetupRow(
                    Icons.Rounded.ToggleOn, "Quick Settings tile",
                    if (PauseControl.canPromptToAddTile) "Pause and resume from the notification shade."
                    else "Pull down Quick Settings, tap the edit (pencil) button and add Pause ClearGate.",
                    if (PauseControl.canPromptToAddTile) "Add" else "Done",
                ) {
                    if (Build.VERSION.SDK_INT >= 33) PauseControl.promptToAddTile(context)
                    else Store.setTileAdded(context, true)
                } else null,
                if (!widgetAdded) SetupRow(
                    Icons.Rounded.Widgets, "Home-screen widget",
                    "See whether ClearGate is on, and pause or resume, at a glance.", "Add",
                ) {
                    val added = AppWidgetManager.getInstance(context).requestPinAppWidget(
                        ComponentName(context, ClearGateWidget::class.java), null, null,
                    )
                    if (!added) {
                        Toast.makeText(context, "Long-press your home screen › Widgets › ClearGate", Toast.LENGTH_LONG).show()
                    }
                } else null,
            )
            if (setupRows.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = colors.outlineVariant)
                Spacer(Modifier.height(4.dp))
                setupRows.forEach { HelperRow(it) }
            }
        }
    }

    if (picking) {
        BankPickerDialog(
            exclude = banks.map { it.packageName }.toSet(),
            onPick = {
                BankShortcuts.add(context, it)
                version++
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private class SetupRow(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val action: String,
    val onAction: () -> Unit,
)

@Composable
private fun HelperRow(row: SetupRow) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(row.icon, null, tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(row.body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = row.onAction) { Text(row.action) }
    }
}

@Composable
private fun BankRow(app: LaunchableApp, onOpen: () -> Unit, onPin: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            app.icon.asImageBitmap(), contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onOpen) { Text("Pause & open") }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More options for ${app.label}") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Add to home screen") }, onClick = { menuOpen = false; onPin() })
                DropdownMenuItem(text = { Text("Remove") }, onClick = { menuOpen = false; onRemove() })
            }
        }
    }
}

@Composable
private fun BankPickerDialog(exclude: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { BankShortcuts.launchableApps(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a banking app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val shown = list.filter { it.packageName !in exclude && it.label.contains(query.trim(), ignoreCase = true) }
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(shown, key = { it.packageName }) { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPick(app.packageName) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(app.icon.asImageBitmap(), null, Modifier.size(36.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
