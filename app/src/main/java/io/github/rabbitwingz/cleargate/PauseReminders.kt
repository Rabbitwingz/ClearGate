package io.github.rabbitwingz.cleargate

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock

/**
 * The "ClearGate is paused · Resume" notification, and a one-time nudge if the user is still paused after
 * NUDGE_AFTER_MS. Both go away as soon as ClearGate is back on (PauseControl.onResumed).
 */
object PauseReminders {
    private const val CHANNEL_PAUSED = "paused"
    private const val CHANNEL_NUDGE = "resume_reminder"
    private const val NOTIFICATION_ID = 1
    private const val NUDGE_AFTER_MS = 10 * 60_000L

    fun showPaused(context: Context) = post(context, nudge = false)

    fun scheduleNudge(context: Context) {
        // Inexact on purpose (no exact-alarm permission needed); Android may deliver it a few minutes late.
        context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + NUDGE_AFTER_MS,
            nudgeIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(nudgeIntent(context))
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** Called by the nudge alarm: reminds again, with sound, if ClearGate is still paused. */
    fun nudge(context: Context) {
        if (Store.isPaused(context) && !SetupStatus.read(context).serviceOn) post(context, nudge = true)
    }

    private fun post(context: Context, nudge: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(manager)

        val resume = PendingIntent.getActivity(
            context, 0, PauseControl.resumeActivityIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(context, R.drawable.ic_tile)
        val notification = Notification.Builder(context, if (nudge) CHANNEL_NUDGE else CHANNEL_PAUSED)
            .setSmallIcon(icon)
            .setContentTitle(if (nudge) "Done banking?" else "ClearGate is paused")
            .setContentText(
                if (nudge) "Resume ClearGate so MyGate's ads are skipped again."
                else "MyGate's ads will show until you resume."
            )
            .setContentIntent(resume)
            .addAction(Notification.Action.Builder(icon, "Resume", resume).build())
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannels(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PAUSED, "While paused", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows that ClearGate is paused, with a Resume button."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NUDGE, "Resume reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminds you once if ClearGate is still paused after 10 minutes."
            }
        )
    }

    private fun nudgeIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_NUDGE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Private (not exported) receiver for the nudge alarm and the widget's Pause button. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NUDGE -> PauseReminders.nudge(context)
            // The widget only sends this while ClearGate is on; otherwise its button opens ResumeActivity directly.
            ACTION_PAUSE -> PauseControl.pause(context)
        }
    }

    companion object {
        const val ACTION_NUDGE = "io.github.rabbitwingz.cleargate.NUDGE"
        const val ACTION_PAUSE = "io.github.rabbitwingz.cleargate.PAUSE"
    }
}
