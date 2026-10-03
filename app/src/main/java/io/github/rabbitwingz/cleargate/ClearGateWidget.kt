package io.github.rabbitwingz.cleargate

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: shows On / Paused, with one button to pause or resume. */
class ClearGateWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context, pausing = false))
    }

    companion object {
        /** Redraws all ClearGate widgets. {@code pausing}: show "Paused" before Settings catches up. */
        fun refresh(context: Context, pausing: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ClearGateWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, pausing))
        }

        private fun views(context: Context, pausing: Boolean): RemoteViews {
            val on = !pausing && SetupStatus.read(context).serviceOn
            val paused = pausing || Store.isPaused(context)
            val views = RemoteViews(context.packageName, R.layout.widget)

            views.setTextViewText(
                R.id.widget_status,
                when {
                    on -> "On · skipping MyGate ads"
                    paused -> "Paused for banking"
                    else -> "Off"
                },
            )
            views.setTextViewText(R.id.widget_button, if (on) "Pause" else if (paused) "Resume" else "Turn on")

            // Pausing works from a broadcast; resuming has to open Settings, so it goes straight to an activity.
            val button = if (on) {
                PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_PAUSE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                PendingIntent.getActivity(
                    context, 1, PauseControl.resumeActivityIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            views.setOnClickPendingIntent(R.id.widget_button, button)

            val openApp = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            return views
        }
    }
}
