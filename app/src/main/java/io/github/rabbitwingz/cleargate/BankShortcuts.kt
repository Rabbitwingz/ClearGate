package io.github.rabbitwingz.cleargate

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri

/** An app on the phone that can be launched (used for the bank picker and shortcuts). */
data class LaunchableApp(val packageName: String, val label: String, val icon: Bitmap)

/**
 * "Pause & open <bank>" shortcuts: long-press ClearGate's icon, or pin them to the home screen. Each one pauses
 * ClearGate and opens the bank in one tap (see BankLaunchActivity).
 */
object BankShortcuts {
    /** Launchers show about four shortcuts on a long-press, so that's the limit. */
    const val MAX_BANKS = 4
    private const val ICON_PX = 192

    fun banks(context: Context): List<LaunchableApp> =
        Store.bankPackages(context).mapNotNull { app(context, it) }.sortedBy { it.label.lowercase() }

    fun add(context: Context, packageNames: Collection<String>) {
        Store.setBankPackages(context, Store.bankPackages(context) + packageNames)
        publish(context)
    }

    fun remove(context: Context, packageName: String) {
        Store.setBankPackages(context, Store.bankPackages(context) - packageName)
        publish(context)
        context.getSystemService(ShortcutManager::class.java)?.disableShortcuts(listOf(shortcutId(packageName)))
    }

    /** Replaces the long-press shortcuts with the current bank list, and redraws the widget's bank row. */
    fun publish(context: Context) {
        context.getSystemService(ShortcutManager::class.java)?.let { manager ->
            manager.dynamicShortcuts = banks(context).take(MAX_BANKS).map { shortcut(context, it) }
        }
        ClearGateWidget.refresh(context)
    }

    /** Opens the bank via BankLaunchActivity (pause first, then launch). Distinct per bank for PendingIntents. */
    fun launchIntent(context: Context, packageName: String): Intent =
        Intent(context, BankLaunchActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.fromParts("bank", packageName, null))
            .putExtra(BankLaunchActivity.EXTRA_PACKAGE, packageName)

    /** Asks the launcher to put this bank's shortcut on the home screen. Returns false if it can't. */
    fun pinToHomeScreen(context: Context, app: LaunchableApp): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        return manager.requestPinShortcut(shortcut(context, app), null)
    }

    /** Every app with a launcher icon, except ClearGate and MyGate, sorted by name. */
    fun launchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName && it != AdSkipService.TARGET_PACKAGE }
            .mapNotNull { app(context, it) }
            .sortedBy { it.label.lowercase() }
    }

    private fun app(context: Context, packageName: String): LaunchableApp? = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        LaunchableApp(packageName, pm.getApplicationLabel(info).toString(), toBitmap(pm.getApplicationIcon(info)))
    } catch (_: Exception) {
        null // uninstalled
    }

    private fun shortcut(context: Context, app: LaunchableApp): ShortcutInfo =
        ShortcutInfo.Builder(context, shortcutId(app.packageName))
            .setShortLabel(app.label)
            .setLongLabel("Pause & open ${app.label}")
            .setIcon(Icon.createWithBitmap(app.icon))
            .setIntent(launchIntent(context, app.packageName))
            .build()

    private fun shortcutId(packageName: String) = "bank:$packageName"

    private fun toBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, ICON_PX, ICON_PX)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
}
