package io.github.rabbitwingz.cleargate

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    /** Bumped each time something (the widget's Add slot) asks to open Settings › Banking apps. */
    private val openBankingRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { ClearGateTheme { GateApp(openBankingRequests.intValue) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_BANKING, false) == true) openBankingRequests.intValue++
    }

    companion object {
        const val EXTRA_OPEN_BANKING = "open_banking"
    }
}

private enum class Screen { Onboarding, Home, Settings }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GateApp(openBankingRequests: Int) {
    val context = LocalContext.current
    var onboarded by rememberSaveable { mutableStateOf(Store.isOnboarded(context)) }
    var inSettings by rememberSaveable { mutableStateOf(false) }
    // Settings › Banking apps gets a brief highlight when opened from the banking tip or the widget's Add slot.
    var highlightBanking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openBankingRequests) {
        if (openBankingRequests > 0 && onboarded) {
            highlightBanking = true
            inSettings = true
        }
    }

    // Re-read system state whenever we come back (e.g. from Accessibility settings).
    var status by remember { mutableStateOf(SetupStatus.read(context)) }
    LifecycleResumeEffect(Unit) {
        status = SetupStatus.read(context)
        onPauseOrDispose { }
    }

    // Bumped on every preference write, including the service's log entries, so the UI updates live.
    var prefsVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val prefs = Store.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> prefsVersion++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    // Pausing or resuming writes a preference, then the service switches off or on a moment later: re-read then.
    LaunchedEffect(prefsVersion) {
        delay(400)
        status = SetupStatus.read(context)
    }

    // Theme values must be read here; transitionSpec isn't a composable context.
    val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val screen = when {
        !onboarded -> Screen.Onboarding
        inSettings -> Screen.Settings
        else -> Screen.Home
    }
    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) },
        label = "screen",
    ) { target ->
        when (target) {
            Screen.Onboarding -> OnboardingScreen(status = status, onFinish = {
                Store.setOnboarded(context, true)
                onboarded = true
            })
            Screen.Home -> HomeScreen(
                status = status,
                prefsVersion = prefsVersion,
                onOpenSettings = { inSettings = true },
                onOpenBankingSettings = {
                    highlightBanking = true
                    inSettings = true
                },
            )
            Screen.Settings -> SettingsScreen(
                status = status,
                highlightBanking = highlightBanking,
                onBack = {
                    inSettings = false
                    highlightBanking = false
                },
                onReplayIntro = {
                    inSettings = false
                    highlightBanking = false
                    onboarded = false
                },
            )
        }
    }
}
