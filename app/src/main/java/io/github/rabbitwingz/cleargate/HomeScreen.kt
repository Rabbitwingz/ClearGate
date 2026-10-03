package io.github.rabbitwingz.cleargate

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One row of the service's activity log (see Store.addLog). */
data class LogEntry(val time: Long, val kind: Kind, val detail: String) {
    enum class Kind(val key: String) {
        Answered(Store.LOG_ANSWERED), Skipped(Store.LOG_SKIPPED), Fallback(Store.LOG_FALLBACK)
    }
}

fun readLog(context: android.content.Context): List<LogEntry> {
    val array = Store.log(context)
    return (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val kind = LogEntry.Kind.entries.firstOrNull { it.key == o.optString("kind") } ?: return@mapNotNull null
        LogEntry(o.optLong("t"), kind, o.optString("detail"))
    }
}

/** What the status card shows: running, paused for banking, or never switched on. */
private enum class Mode { On, Paused, NotSetUp }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    status: SetupStatus,
    prefsVersion: Int,
    onOpenSettings: () -> Unit,
    onOpenBankingSettings: () -> Unit,
) {
    val context = LocalContext.current
    // prefsVersion changes whenever the service writes, so these re-read live.
    val paused = remember(prefsVersion) { Store.isPaused(context) }
    val skipped = remember(prefsVersion) { Store.skippedCount(context) }
    val lastSkipped = remember(prefsVersion) { Store.lastSkippedAt(context) }
    val log = remember(prefsVersion) { readLog(context) }
    // The banking tip shows until it's tapped once, and never if banking apps are already set up.
    val showBankingTip = remember(prefsVersion) {
        !Store.isWidgetPromoDismissed(context) && Store.bankPackages(context).isEmpty()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val mode = when {
        status.serviceOn -> Mode.On
        paused -> Mode.Paused
        else -> Mode.NotSetUp
    }
    val headline = when (mode) {
        Mode.On -> "Skipping MyGate ads"
        Mode.Paused -> "Paused for banking"
        Mode.NotSetUp -> "Finish setup to start skipping ads"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("ClearGate") },
                subtitle = { Text(headline) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
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
            item(key = "hero") {
                StatusHero(
                    mode = mode,
                    onPause = { PauseControl.pause(context) },
                    onResume = { PauseControl.resume(context, returnToApp = true) },
                    onSetUp = { PauseControl.resume(context, returnToApp = true) },
                )
            }
            item(key = "stats") { StatsRow(skipped = skipped, lastSkipped = lastSkipped) }
            if (mode != Mode.NotSetUp && showBankingTip) {
                item(key = "bankingTip") {
                    BankingTipPill(onClick = {
                        Store.setWidgetPromoDismissed(context, true)
                        onOpenBankingSettings()
                    })
                }
            }

            if (mode == Mode.NotSetUp || !status.batteryUnrestricted) {
                item(key = "setupHeader") { SectionHeader("Finish setup") }
                item(key = "setup") { SetupSteps(status) }
                if (mode == Mode.NotSetUp && restrictedSettingsApply) {
                    item(key = "restricted") { RestrictedSettingsHint(onOpen = { SystemScreens.appInfo(context) }) }
                }
            }
            if (!status.myGateInstalled) {
                item(key = "noMyGate") { NoMyGateNote() }
            }

            item(key = "activityHeader") { SectionHeader("Recent activity") }
            if (log.isEmpty()) {
                item(key = "empty") { EmptyActivity() }
            } else {
                items(uniqueKeys(log), key = { it.first }) { (_, entry) ->
                    ActivityRow(entry = entry, modifier = Modifier.animateItem())
                }
            }
        }
    }

}

/**
 * One-time tip pointing to Settings › Banking apps, where the user adds banks for the widget and the long-press
 * shortcuts. Tapping it opens Settings with that card highlighted and hides the tip for good.
 */
@Composable
private fun BankingTipPill(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccountBalance, null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                "Banking app won't open? Set up quick access",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusHero(mode: Mode, onPause: () -> Unit, onResume: () -> Unit, onSetUp: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when (mode) {
            Mode.On -> colors.primaryContainer
            Mode.Paused -> colors.surfaceContainerHighest
            Mode.NotSetUp -> colors.errorContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "heroContainer",
    )
    val onContainer = when (mode) {
        Mode.On -> colors.onPrimaryContainer
        Mode.Paused -> colors.onSurface
        Mode.NotSetUp -> colors.onErrorContainer
    }

    Surface(shape = RoundedCornerShape(32.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            ShapeBadge(
                icon = when (mode) {
                    Mode.On -> Icons.Rounded.GppGood
                    Mode.Paused -> Icons.Rounded.PauseCircle
                    Mode.NotSetUp -> Icons.Rounded.GppMaybe
                },
                polygon = if (mode == Mode.On) MaterialShapes.Cookie9Sided else MaterialShapes.Cookie4Sided,
                container = when (mode) {
                    Mode.On -> colors.primary
                    Mode.Paused -> colors.outline
                    Mode.NotSetUp -> colors.error
                },
                content = when (mode) {
                    Mode.On -> colors.onPrimary
                    Mode.Paused -> colors.surface
                    Mode.NotSetUp -> colors.onError
                },
                size = 72.dp,
                spin = mode == Mode.On,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                when (mode) {
                    Mode.On -> "You're protected"
                    Mode.Paused -> "Paused for banking"
                    Mode.NotSetUp -> "Not set up yet"
                },
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = onContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (mode) {
                    Mode.On -> "After you approve or deny a visitor, the ad is skipped and MyGate goes away."
                    Mode.Paused -> "ClearGate's accessibility is off, so banking apps won't complain. " +
                        "MyGate's ads will show until you resume."
                    Mode.NotSetUp -> "Turn on ClearGate in Accessibility settings so it can skip the ad."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = onContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(16.dp))
            when (mode) {
                // Inverted colours (light on the teal card) so the button stands out from the card.
                Mode.On -> Button(
                    onClick = onPause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.onPrimaryContainer,
                        contentColor = colors.primaryContainer,
                    ),
                ) {
                    Icon(Icons.Rounded.PauseCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pause for banking")
                }
                Mode.Paused -> Button(onClick = onResume) {
                    Icon(Icons.Rounded.PlayCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Resume")
                }
                Mode.NotSetUp -> Button(onClick = onSetUp) { Text("Turn on") }
            }
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
private fun ActivityRow(entry: LogEntry, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (icon, title, subtitle) = describe(entry)
    val (badgeBg, badgeFg) = when (entry.kind) {
        LogEntry.Kind.Skipped -> colors.primary to colors.onPrimary
        LogEntry.Kind.Answered -> colors.tertiaryContainer to colors.onTertiaryContainer
        LogEntry.Kind.Fallback -> colors.secondaryContainer to colors.onSecondaryContainer
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(relativeTime(entry.time), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun describe(entry: LogEntry): Triple<ImageVector, String, String> = when (entry.kind) {
    LogEntry.Kind.Answered -> Triple(
        Icons.Rounded.TouchApp,
        "You answered a visitor",
        entry.detail.replaceFirstChar { it.uppercase() },
    )
    LogEntry.Kind.Skipped -> Triple(Icons.Rounded.VisibilityOff, "Ad skipped", "Pressed Home once MyGate confirmed")
    LogEntry.Kind.Fallback -> Triple(Icons.Rounded.Replay, "Pressed Back as a fallback", "MyGate was still showing the ad")
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
        val base = "${e.time}|${e.kind}|${e.detail}"
        val n = seen.merge(base, 1, Int::plus)!!
        (if (n == 1) base else "$base#$n") to e
    }
}

private fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE).toString()
