package com.local.gateadskipper

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One row of the service's activity log (see Store.addLog). */
data class LogEntry(val time: Long, val cls: String, val summary: String, val note: String) {
    enum class Kind { Answered, Skipped, Fallback, Screen }

    val kind: Kind = when {
        summary.startsWith("tapped: ") -> Kind.Answered
        note.startsWith("AD:") -> Kind.Skipped
        note.startsWith("still showing") -> Kind.Fallback
        else -> Kind.Screen
    }
}

fun readLog(context: android.content.Context): List<LogEntry> {
    val array = Store.log(context)
    return (0 until array.length()).mapNotNull { i ->
        array.optJSONObject(i)?.let {
            LogEntry(it.optLong("t"), it.optString("cls"), it.optString("sum"), it.optString("note"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(status: SetupStatus, prefsVersion: Int, onReplayIntro: () -> Unit) {
    val context = LocalContext.current
    // prefsVersion changes whenever the service writes, so these re-read live.
    val enabled = remember(prefsVersion) { Store.isEnabled(context) }
    val skipped = remember(prefsVersion) { Store.skippedCount(context) }
    val lastSkipped = remember(prefsVersion) { Store.lastSkippedAt(context) }
    val log = remember(prefsVersion) { readLog(context) }
    val blocked = remember(prefsVersion) { Store.blockedClasses(context) }

    var showAllScreens by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val headline = when {
        !status.serviceOn -> "Finish setup to start skipping ads"
        !enabled -> "Paused"
        else -> "Skipping MyGate ads"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("ClearGate") },
                subtitle = { Text(headline) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("How it works") },
                            leadingIcon = { Icon(Icons.Rounded.Info, null) },
                            onClick = { menuOpen = false; onReplayIntro() },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear activity") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) },
                            onClick = { menuOpen = false; confirmClear = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Source code") },
                            leadingIcon = { Icon(Icons.Rounded.Code, null) },
                            onClick = { menuOpen = false; SystemScreens.web(context, REPO_URL) },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val visible = if (showAllScreens) log else log.filter { it.kind != LogEntry.Kind.Screen }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                StatusHero(status = status, enabled = enabled, onToggle = { Store.setEnabled(context, it) })
            }
            item(key = "stats") { StatsRow(skipped = skipped, lastSkipped = lastSkipped) }

            if (!status.serviceOn || !status.batteryUnrestricted) {
                item(key = "setupHeader") { SectionHeader("Finish setup") }
                item(key = "setup") { SetupSteps(status) }
                if (!status.serviceOn && restrictedSettingsApply) {
                    item(key = "restricted") { RestrictedSettingsHint(onOpen = { SystemScreens.appInfo(context) }) }
                }
            }
            if (!status.myGateInstalled) {
                item(key = "noMyGate") { NoMyGateNote() }
            }

            item(key = "activityHeader") {
                SectionHeader("Recent activity") {
                    FilterChip(
                        selected = showAllScreens,
                        onClick = { showAllScreens = !showAllScreens },
                        label = { Text("All screens") },
                    )
                }
            }
            if (visible.isEmpty()) {
                item(key = "empty") { EmptyActivity() }
            } else {
                items(uniqueKeys(visible), key = { it.first }) { (_, entry) ->
                    ActivityRow(
                        entry = entry,
                        blocked = entry.cls in blocked,
                        onToggleBlock = { Store.setBlocked(context, entry.cls, entry.cls !in blocked) },
                        modifier = Modifier.animateItem(),
                    )
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusHero(status: SetupStatus, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val active = status.serviceOn && enabled
    val container by animateColorAsState(
        when {
            !status.serviceOn -> colors.errorContainer
            enabled -> colors.primaryContainer
            else -> colors.surfaceContainerHighest
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "heroContainer",
    )
    val onContainer = when {
        !status.serviceOn -> colors.onErrorContainer
        enabled -> colors.onPrimaryContainer
        else -> colors.onSurface
    }

    Surface(shape = RoundedCornerShape(32.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(
                    icon = when {
                        !status.serviceOn -> Icons.Rounded.GppMaybe
                        enabled -> Icons.Rounded.GppGood
                        else -> Icons.Rounded.PauseCircle
                    },
                    polygon = if (active) MaterialShapes.Cookie9Sided else MaterialShapes.Cookie4Sided,
                    container = if (!status.serviceOn) colors.error else if (enabled) colors.primary else colors.outline,
                    content = if (!status.serviceOn) colors.onError else if (enabled) colors.onPrimary else colors.surface,
                    size = 72.dp,
                    spin = active,
                )
                Spacer(Modifier.weight(1f))
                if (status.serviceOn) {
                    Switch(checked = enabled, onCheckedChange = onToggle)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                when {
                    !status.serviceOn -> "Not set up yet"
                    enabled -> "You're protected"
                    else -> "Paused"
                },
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = onContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !status.serviceOn -> "Turn on ClearGate in Accessibility settings so it can skip the ad."
                    enabled -> "After you approve or deny a visitor, the ad is skipped and MyGate goes away."
                    else -> "MyGate's ads will show as usual until you turn this back on."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = onContainer.copy(alpha = 0.85f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatsRow(skipped: Int, lastSkipped: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            value = skipped.toString(),
            label = if (skipped == 1) "ad skipped" else "ads skipped",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = if (lastSkipped == 0L) "—" else relativeTime(lastSkipped),
            label = "last skipped",
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActivityRow(entry: LogEntry, blocked: Boolean, onToggleBlock: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable(entry.time, entry.note) { mutableStateOf(false) }

    val (icon, title, subtitle) = describe(entry)
    val (badgeBg, badgeFg) = when (entry.kind) {
        LogEntry.Kind.Skipped -> colors.primary to colors.onPrimary
        LogEntry.Kind.Answered -> colors.tertiaryContainer to colors.onTertiaryContainer
        LogEntry.Kind.Fallback -> colors.secondaryContainer to colors.onSecondaryContainer
        LogEntry.Kind.Screen -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = badgeBg) {
                    Icon(icon, contentDescription = null, tint = badgeFg, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(entry.time), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    if (entry.cls.isNotEmpty()) {
                        Text("Screen", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                        Text(entry.cls, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = colors.onSurface)
                    }
                    if (entry.summary.isNotEmpty() && entry.kind != LogEntry.Kind.Answered) {
                        Spacer(Modifier.height(8.dp))
                        Text("What was on screen", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                        Text(entry.summary, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
                    }
                    val canBlock = entry.kind == LogEntry.Kind.Screen && entry.cls.isNotEmpty() &&
                        entry.cls !in AdSkipService.KNOWN_AD_ACTIVITIES
                    if (canBlock) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onToggleBlock, modifier = Modifier.align(Alignment.End)) {
                            Icon(Icons.Rounded.Block, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (blocked) "Stop treating as an ad" else "Always skip this screen")
                        }
                    }
                }
            }
        }
    }
}

private fun describe(entry: LogEntry): Triple<ImageVector, String, String> = when (entry.kind) {
    LogEntry.Kind.Answered -> Triple(
        Icons.Rounded.TouchApp,
        "You answered a visitor",
        entry.summary.removePrefix("tapped: ").replace(" button", "").replaceFirstChar { it.uppercase() },
    )
    LogEntry.Kind.Skipped -> Triple(
        Icons.Rounded.VisibilityOff,
        "Ad skipped",
        entry.note.removePrefix("AD: ").substringBefore(" →").replaceFirstChar { it.uppercase() },
    )
    LogEntry.Kind.Fallback -> Triple(Icons.Rounded.Replay, "Pressed Back as a fallback", "MyGate was still showing the ad")
    LogEntry.Kind.Screen -> Triple(
        Icons.Rounded.PhoneAndroid,
        "MyGate screen",
        entry.cls.removePrefix(AdSkipService.TARGET_PACKAGE).substringAfterLast('.').ifEmpty { "Unknown" },
    )
}

@Composable
private fun EmptyActivity() {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(
            "Nothing yet. Next time a visitor arrives, you'll see what happened here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
    }
}

@Composable
private fun NoMyGateNote() {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            "MyGate isn't installed on this phone, so there's nothing to skip yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
    }
}

/** Stable list keys (so rows animate when new ones arrive), made unique if two entries are identical. */
private fun uniqueKeys(entries: List<LogEntry>): List<Pair<String, LogEntry>> {
    val seen = HashMap<String, Int>()
    return entries.map { e ->
        val base = "${e.time}|${e.note}|${e.summary.hashCode()}"
        val n = seen.merge(base, 1, Int::plus)!!
        (if (n == 1) base else "$base#$n") to e
    }
}

private fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE).toString()
