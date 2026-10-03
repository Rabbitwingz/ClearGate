package io.github.rabbitwingz.cleargate

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews

/**
 * Home-screen widget: ClearGate's state with a Pause/Resume button, plus up to 4 banking apps that open with
 * ClearGate paused (see BankLaunchActivity) and an "Add bank" slot. 4x2 by default; on Android 12+ it switches to a
 * one-row layout when resized to 4x1.
 */
class ClearGateWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context, pausing = false))
    }

    companion object {
        private val BANK_ICONS = intArrayOf(R.id.bank_icon_0, R.id.bank_icon_1, R.id.bank_icon_2, R.id.bank_icon_3)
        private val BANK_LABELS = intArrayOf(R.id.bank_label_0, R.id.bank_label_1, R.id.bank_label_2, R.id.bank_label_3)
        private val BANK_SLOTS = intArrayOf(R.id.bank_slot_0, R.id.bank_slot_1, R.id.bank_slot_2, R.id.bank_slot_3)

        /** Redraws all ClearGate widgets. {@code pausing}: show "Paused" before Settings catches up. */
        fun refresh(context: Context, pausing: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ClearGateWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, pausing))
        }

        private fun views(context: Context, pausing: Boolean): RemoteViews {
            val state = State(
                on = !pausing && SetupStatus.read(context).serviceOn,
                paused = pausing || Store.isPaused(context),
                banks = BankShortcuts.banks(context).take(BankShortcuts.MAX_BANKS),
            )
            val full = build(context, R.layout.widget, state, compact = false)
            if (Build.VERSION.SDK_INT < 31) return full
            val compact = build(context, R.layout.widget_compact, state, compact = true)
            // The launcher picks the largest layout that fits the widget's current size.
            return RemoteViews(mapOf(SizeF(180f, 40f) to compact, SizeF(180f, 100f) to full))
        }

        private class State(val on: Boolean, val paused: Boolean, val banks: List<LaunchableApp>)

        private fun build(context: Context, layout: Int, state: State, compact: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, layout)

            if (compact) {
                views.setImageViewResource(R.id.widget_dot, if (state.on) R.drawable.widget_dot_on else R.drawable.widget_dot_off)
            } else {
                views.setTextViewText(
                    R.id.widget_status,
                    when {
                        state.on -> "Skipping MyGate ads"
                        state.paused -> "Paused for banking"
                        else -> "Off"
                    },
                )
                views.setOnClickPendingIntent(R.id.widget_root, openApp(context, banking = false))
            }

            views.setTextViewText(R.id.widget_button, if (state.on) "Pause" else if (state.paused) "Resume" else "Turn on")
            views.setOnClickPendingIntent(R.id.widget_button, buttonIntent(context, state.on))

            for (i in BANK_ICONS.indices) {
                val bank = state.banks.getOrNull(i)
                val target = if (compact) BANK_ICONS[i] else BANK_SLOTS[i]
                if (bank == null) {
                    views.setViewVisibility(target, View.GONE)
                    continue
                }
                views.setViewVisibility(target, View.VISIBLE)
                views.setImageViewBitmap(BANK_ICONS[i], bank.icon)
                views.setContentDescription(BANK_ICONS[i], "Open ${bank.label} with ClearGate paused")
                if (!compact) views.setTextViewText(BANK_LABELS[i], bank.label)
                views.setOnClickPendingIntent(
                    target,
                    PendingIntent.getActivity(
                        context, REQUEST_BANK_BASE + i, BankShortcuts.launchIntent(context, bank.packageName),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            views.setViewVisibility(R.id.bank_add, if (state.banks.size < BankShortcuts.MAX_BANKS) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(R.id.bank_add, openApp(context, banking = true))
            return views
        }

        /** Pausing works from a broadcast; resuming has to open Settings, so it goes straight to an activity. */
        private fun buttonIntent(context: Context, on: Boolean): PendingIntent = if (on) {
            PendingIntent.getBroadcast(
                context, REQUEST_PAUSE,
                Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_PAUSE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } else {
            PendingIntent.getActivity(
                context, REQUEST_RESUME, PauseControl.resumeActivityIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        /** Opens ClearGate; {@code banking} goes straight to Settings › Banking apps (the widget's Add slot). */
        private fun openApp(context: Context, banking: Boolean): PendingIntent = PendingIntent.getActivity(
            context, if (banking) REQUEST_ADD_BANK else REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_BANKING, banking)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private const val REQUEST_PAUSE = 0
        private const val REQUEST_RESUME = 1
        private const val REQUEST_OPEN = 2
        private const val REQUEST_ADD_BANK = 3
        private const val REQUEST_BANK_BASE = 10
    }
}
