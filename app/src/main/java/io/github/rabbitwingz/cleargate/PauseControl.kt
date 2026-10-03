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
 */
object PauseControl {
    /** Switches ClearGate's accessibility off. Returns false if the service isn't running (nothing to pause). */
    fun pause(context: Context): Boolean {
        val service = AdSkipService.running() ?: return false
        Store.setPaused(context, true)
        service.disableSelf()
        refreshTile(context)
        return true
    }

    /**
     * Opens Accessibility settings so the user can switch ClearGate back on. (The per-service details page needs a
     * system-only permission, so ordinary apps get a SecurityException there.) The fragment-args extras ask
     * Pixel/AOSP Settings to scroll to and highlight ClearGate's entry; other Settings apps ignore them.
     */
    fun resumeIntent(context: Context): Intent {
        val key = ComponentName(context, AdSkipService::class.java).flattenToString()
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, key) })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Opens the resume screen. Returns false (instead of crashing) if Settings refuses or is missing. */
    fun resume(context: Context): Boolean = try {
        context.startActivity(resumeIntent(context))
        true
    } catch (_: RuntimeException) { // ActivityNotFoundException, SecurityException
        false
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

    /** Asks the system to redraw the Quick Settings tile with the current state. */
    fun refreshTile(context: Context) {
        TileService.requestListeningState(context, ComponentName(context, PauseTileService::class.java))
    }
}
