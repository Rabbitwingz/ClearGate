package io.github.rabbitwingz.cleargate

import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings › Banking apps: the banking apps that open with ClearGate paused. They appear on the widget, on a
 * long-press of ClearGate's icon, and can be pinned to the home screen. Here the user only manages the list.
 */
@Composable
fun BankingSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val key = rememberRefreshKey()
    val banks = remember(key) { BankShortcuts.banks(context) }
    var picking by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountBalance, null, tint = colors.primary)
                Spacer(Modifier.width(12.dp))
                Text("Banking apps", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Some banking apps won't open while ClearGate is on. Add them here, then open them from the " +
                    "ClearGate widget or by long-pressing ClearGate's icon: ClearGate pauses first.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )

            if (banks.isNotEmpty()) Spacer(Modifier.height(8.dp))
            banks.forEach { app ->
                BankRow(
                    app = app,
                    onPin = {
                        if (!BankShortcuts.pinToHomeScreen(context, app)) {
                            Toast.makeText(context, "Your launcher doesn't support this", Toast.LENGTH_SHORT).show()
                        }
                    },
                    // Changes the bank list in preferences, which refreshes this card.
                    onRemove = { BankShortcuts.remove(context, app.packageName) },
                )
            }
            if (banks.size < BankShortcuts.MAX_BANKS) {
                TextButton(onClick = { picking = true }) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (banks.isEmpty()) "Add a banking app" else "Add another")
                }
            }
        }
    }

    if (picking) {
        BankPickerDialog(
            exclude = banks.map { it.packageName }.toSet(),
            onDone = { picked ->
                BankShortcuts.add(context, picked)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun BankRow(app: LaunchableApp, onPin: () -> Unit, onRemove: () -> Unit) {
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
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More options for ${app.label}") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Add to home screen") }, onClick = { menuOpen = false; onPin() })
                DropdownMenuItem(text = { Text("Remove") }, onClick = { menuOpen = false; onRemove() })
            }
        }
    }
}

/**
 * Picks banking apps to add (several at once, up to MAX_BANKS in total). {@code exclude}: banks already added.
 * Used by Settings › Banking apps and the home screen's widget card.
 */
@Composable
fun BankPickerDialog(exclude: Set<String>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptyList<String>()) }
    val roomLeft = BankShortcuts.MAX_BANKS - exclude.size
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { BankShortcuts.launchableApps(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose your banking apps") },
        text = {
            Column {
                Text(
                    if (roomLeft == 1) "You can add 1 more." else "You can add up to $roomLeft.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
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
                    LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(shown, key = { it.packageName }) { app ->
                            val checked = app.packageName in selected
                            val enabled = checked || selected.size < roomLeft
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = enabled) {
                                        selected = if (checked) selected - app.packageName else selected + app.packageName
                                    }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(app.icon.asImageBitmap(), null, Modifier.size(36.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    modifier = Modifier.weight(1f),
                                )
                                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(selected) }, enabled = selected.isNotEmpty()) {
                Text(if (selected.isEmpty()) "Add" else "Add ${selected.size}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
