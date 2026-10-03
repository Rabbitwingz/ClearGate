package io.github.rabbitwingz.cleargate

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: tap to pause ClearGate before using a banking app, tap again to resume.
 * Pausing switches accessibility off directly; resuming opens ClearGate's switch (see PauseControl).
 */
class PauseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        if (SetupStatus.read(this).serviceOn && PauseControl.pause(this)) {
            updateTile(pausing = true)
        } else {
            openResumeScreen()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openResumeScreen() {
        val intent = PauseControl.resumeIntent(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /** {@code pausing}: the service is switching off right now, so show "Paused" without waiting for Settings. */
    private fun updateTile(pausing: Boolean = false) {
        val tile = qsTile ?: return
        val on = !pausing && SetupStatus.read(this).serviceOn
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when {
                on -> "On · tap to pause"
                Store.isPaused(this) -> "Paused · tap to resume"
                else -> "Off · tap to turn on"
            }
        }
        tile.updateTile()
    }
}
