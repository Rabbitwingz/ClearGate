package io.github.rabbitwingz.cleargate

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ClearGateTheme { GateApp() } }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GateApp() {
    val context = LocalContext.current
    var onboarded by rememberSaveable { mutableStateOf(Store.isOnboarded(context)) }

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

    // Theme values must be read here; transitionSpec isn't a composable context.
    val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = onboarded,
        transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) },
        label = "onboarding",
    ) { done ->
        if (!done) {
            OnboardingScreen(status = status, onFinish = {
                Store.setOnboarded(context, true)
                onboarded = true
            })
        } else {
            HomeScreen(status = status, prefsVersion = prefsVersion, onReplayIntro = { onboarded = false })
        }
    }
}
