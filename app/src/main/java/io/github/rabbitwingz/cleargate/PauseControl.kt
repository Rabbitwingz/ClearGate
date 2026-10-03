package io.github.rabbitwingz.cleargate

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Pausing for banking apps. Banks flag any enabled third-party accessibility service, so a pause has to switch
 * ClearGate's accessibility fully off. The service can do that itself (disableSelf), but Android only lets the user
 * switch it back on, so resuming opens Accessibility settings for them to flip ClearGate's switch.
 *
 * Every entry point (tile, app, widget, bank shortcuts, notification) goes through here, so they all share the
 * "paused" notification, the 10-minute nudge and the tile/widget state.
 */
object PauseControl {
    /** Switches ClearGate's accessibility off. Returns false if the service isn't running (nothing to pause). */
    fun pause(context: Context): Boolean {
        val service = AdSkipService.running() ?: return false
        Store.setPaused(context, true)
        service.disableSelf()
        PauseReminders.showPaused(context)
        PauseReminders.scheduleNudge(context)
        refreshAll(context, pausing = true)
        return true
    }

    /** Starts resuming: remembers the request (see onResumed), then opens Accessibility settings. */
    fun resume(context: Context) {
        context.startActivity(Intent(context, ResumeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Intent for tiles, widgets and notifications that start a resume. */
    fun resumeActivityIntent(context: Context): Intent =
        Intent(context, ResumeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Called by the service when accessibility is back on: clears the paused state and its reminders. */
    fun onResumed(context: Context) {
        Store.setPaused(context, false)
        PauseReminders.cancel(context)
        refreshAll(context)
    }

    /**
     * Opens Accessibility settings so the user can switch ClearGate back on. (The per-service details page needs a
     * system-only permission, so ordinary apps get a SecurityException there.) The fragment-args extras ask
     * Pixel/AOSP Settings to scroll to and highlight ClearGate's entry; other Settings apps ignore them.
     * Returns false (instead of crashing) if Settings refuses or is missing.
     */
    fun openAccessibilitySettings(context: Context): Boolean {
        val key = ComponentName(context, AdSkipService::class.java).flattenToString()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, key) })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: RuntimeException) { // ActivityNotFoundException, SecurityException
            false
        }
    }

    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"

    /** Android 13+ can add the tile to Quick Settings with a system prompt; older versions need the shade's edit mode. */
    val canPromptToAddTile: Boolean get() = Build.VERSION.SDK_INT >= 33

    /** Shows Android's "Add tile" prompt. Must be called while an activity is in front. */
    @RequiresApi(33)
    fun promptToAddTile(context: Context) {
        val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
        statusBar.requestAddTileService(
            ComponentName(context, PauseTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_tile),
            context.mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) Store.setTileAdded(context, true)
        }
    }

    /**
     * Redraws the Quick Settings tile and home-screen widgets. {@code pausing}: the service is switching off right
     * now, so show "Paused" without waiting for Settings to catch up.
     */
    fun refreshAll(context: Context, pausing: Boolean = false) {
        TileService.requestListeningState(context, ComponentName(context, PauseTileService::class.java))
        ClearGateWidget.refresh(context, pausing)
    }
}
