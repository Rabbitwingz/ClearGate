package io.github.rabbitwingz.cleargate

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast

/**
 * Invisible step behind every Resume button (tile, notification, widget, app): remembers that the user asked to
 * resume, so that once they switch ClearGate on and tap Allow, the service can take them out of Settings, then opens
 * Accessibility settings.
 */
class ResumeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.setResumeRequestedAt(this, System.currentTimeMillis())
        if (!PauseControl.openAccessibilitySettings(this)) {
            Toast.makeText(this, "Open Settings › Accessibility to turn ClearGate on", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}

/**
 * Behind the "Pause & open <bank>" shortcuts: pauses ClearGate, waits until Android reports accessibility off (the
 * bank checks the moment it starts), then opens the bank app.
 */
class BankLaunchActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launch == null) {
            Toast.makeText(this, "That app isn't installed any more", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (SetupStatus.read(this).serviceOn) PauseControl.pause(this)
        waitThenLaunch(launch, SystemClock.uptimeMillis() + MAX_WAIT_MS)
    }

    private fun waitThenLaunch(launch: Intent, deadline: Long) {
        if (!SetupStatus.read(this).serviceOn || SystemClock.uptimeMillis() >= deadline) {
            startActivity(launch)
            finish()
        } else {
            handler.postDelayed({ waitThenLaunch(launch, deadline) }, POLL_MS)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        private const val POLL_MS = 100L
        private const val MAX_WAIT_MS = 2_000L
    }
}
