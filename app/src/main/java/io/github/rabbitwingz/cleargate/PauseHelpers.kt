package io.github.rabbitwingz.cleargate

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/** The pausing helpers' current state: resume reminders, Quick Settings tile, home-screen widget. */
data class PauseHelpers(val remindersOn: Boolean, val tileAdded: Boolean, val widgetAdded: Boolean)

/**
 * Bumps whenever a preference changes or the screen resumes (e.g. after a permission or "add widget" dialog), so
 * state that lives outside Compose gets re-read.
 */
@Composable
fun rememberRefreshKey(): Int {
    val context = LocalContext.current
    var key by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val prefs = Store.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> key++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LifecycleResumeEffect(Unit) {
        key++
        onPauseOrDispose { }
    }
    return key
}

@Composable
fun rememberPauseHelpers(status: SetupStatus): PauseHelpers {
    val context = LocalContext.current
    val key = rememberRefreshKey()
    return remember(key, status) {
        PauseHelpers(
            remindersOn = Store.remindersEnabled(context) && status.notificationsAllowed,
            tileAdded = Store.isTileAdded(context),
            widgetAdded = AppWidgetManager.getInstance(context)
                ?.getAppWidgetIds(ComponentName(context, ClearGateWidget::class.java))
                ?.isNotEmpty() ?: true,
        )
    }
}

/**
 * Returns a function that switches resume reminders on or off. Switching on asks for notification permission if
 * needed (or opens ClearGate's notification settings if Android won't ask again); switching off clears any
 * reminder that's showing.
 */
@Composable
fun rememberRemindersSwitch(status: SetupStatus): (Boolean) -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) SystemScreens.appNotifications(context)
    }
    return { on ->
        Store.setRemindersEnabled(context, on)
        if (!on) {
            PauseReminders.cancel(context)
        } else if (!status.notificationsAllowed) {
            if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            else SystemScreens.appNotifications(context)
        } else if (Store.isPaused(context)) {
            PauseReminders.showPaused(context)
        }
    }
}

/** Adds the Pause tile: Android 13+ shows a system prompt; older versions need the user to drag it in. */
fun addPauseTile(context: Context) {
    if (Build.VERSION.SDK_INT >= 33) PauseControl.promptToAddTile(context)
    else Store.setTileAdded(context, true) // the user confirms they've added it by hand
}

const val TILE_HOW_TO = "Pull down Quick Settings, tap the edit (pencil) button and add Pause ClearGate."

/** Asks the launcher to place the ClearGate widget; falls back to instructions if it can't. */
fun addWidget(context: Context) {
    val added = AppWidgetManager.getInstance(context)
        .requestPinAppWidget(ComponentName(context, ClearGateWidget::class.java), null, null)
    if (!added) {
        Toast.makeText(context, "Long-press your home screen › Widgets › ClearGate", Toast.LENGTH_LONG).show()
    }
}

/** The intro's setup rows for pausing: resume reminders and the Quick Settings tile. */
@Composable
fun PauseSetupSteps(status: SetupStatus) {
    val context = LocalContext.current
    val helpers = rememberPauseHelpers(status)
    val setReminders = rememberRemindersSwitch(status)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SetupStep(
            done = helpers.remindersOn,
            icon = Icons.Rounded.NotificationsActive,
            title = "Resume reminders",
            body = "If you pause ClearGate for a banking app, get a Resume button in your notifications.",
            actionLabel = "Allow",
            onAction = { setReminders(true) },
        )
        SetupStep(
            done = helpers.tileAdded,
            icon = Icons.Rounded.ToggleOn,
            title = "Pause tile",
            body = if (PauseControl.canPromptToAddTile) "Pause and resume from your Quick Settings." else TILE_HOW_TO,
            actionLabel = if (PauseControl.canPromptToAddTile) "Add" else "Done",
            onAction = { addPauseTile(context) },
        )
    }
}
