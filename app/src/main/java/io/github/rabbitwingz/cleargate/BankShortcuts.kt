package io.github.rabbitwingz.cleargate

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
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

    /**
     * Renders an app icon into ClearGate's one icon shape (a rounded square, like ClearGate's own logo), so banks look
     * consistent whatever shape their app ships:
     * - adaptive icons: background and foreground layers drawn full-bleed, then masked;
     * - legacy icons that fill the square (e.g. a plain square logo): masked as they are;
     * - legacy icons with their own shape or transparent edges (a circle, a free-floating logo): shrunk onto a white
     *   tile, as launchers do.
     */
    private fun toBitmap(drawable: Drawable): Bitmap {
        val size = ICON_PX
        val content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        if (drawable is AdaptiveIconDrawable) {
            // Adaptive layers are 108dp with the visible area in the middle 72dp: overdraw by a quarter on each side.
            val bleed = size / 4
            for (layer in listOfNotNull(drawable.background, drawable.foreground)) {
                layer.setBounds(-bleed, -bleed, size + bleed, size + bleed)
                layer.draw(canvas)
            }
        } else {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            if (!fillsSquare(content)) {
                content.eraseColor(Color.WHITE)
                val inset = (size * 0.14f).toInt()
                drawable.setBounds(inset, inset, size - inset, size - inset)
                drawable.draw(canvas)
            }
        }
        return mask(content)
    }

    /** True if the legacy icon is opaque near all four corners, i.e. it's a full square rather than its own shape. */
    private fun fillsSquare(bitmap: Bitmap): Boolean {
        val edge = bitmap.width / 12
        val far = bitmap.width - 1 - edge
        return listOf(edge to edge, far to edge, edge to far, far to far)
            .all { (x, y) -> Color.alpha(bitmap.getPixel(x, y)) > 200 }
    }

    /** Clips to a rounded square with anti-aliased edges. */
    private fun mask(content: Bitmap): Bitmap {
        val size = content.width.toFloat()
        val out = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, size, size), size * CORNER, size * CORNER, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(content, 0f, 0f, paint)
        return out
    }

    /** Corner radius as a fraction of the icon size: matches the rounded squares on most launchers and ClearGate's logo. */
    private const val CORNER = 0.28f
}
