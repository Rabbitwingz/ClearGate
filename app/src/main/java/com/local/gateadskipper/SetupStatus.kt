package com.local.gateadskipper

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** What the app needs from the system, read fresh whenever the UI resumes. */
data class SetupStatus(
    val serviceOn: Boolean,
    val batteryUnrestricted: Boolean,
    val myGateInstalled: Boolean,
) {
    val ready: Boolean get() = serviceOn

    companion object {
        fun read(context: Context) = SetupStatus(
            serviceOn = isServiceEnabled(context),
            batteryUnrestricted = isBatteryUnrestricted(context),
            myGateInstalled = isMyGateInstalled(context),
        )

        private fun isServiceEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(context, AdSkipService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        private fun isBatteryUnrestricted(context: Context): Boolean {
            val pm = context.getSystemService(PowerManager::class.java) ?: return true
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        private fun isMyGateInstalled(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(AdSkipService.TARGET_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/** Android 13+ blocks accessibility for sideloaded apps until "Allow restricted settings" is used. */
val restrictedSettingsApply: Boolean get() = Build.VERSION.SDK_INT >= 33

const val REPO_URL = "https://github.com/Rabbitwingz/ClearGate"

object SystemScreens {
    fun accessibility(context: Context) = start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun appInfo(context: Context) = start(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    @SuppressLint("BatteryLife") // Sideloaded utility that must stay alive to work; not on Play.
    fun batteryUnrestricted(context: Context) {
        val ask = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        if (!start(context, ask)) start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun web(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
