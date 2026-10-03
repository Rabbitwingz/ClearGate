package io.github.rabbitwingz.cleargate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.DoorFront
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(status: SetupStatus, onFinish: () -> Unit) {
    val pager = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGE_COUNT - 1

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                // Keep the layout stable on the last page, where "Skip" makes no sense.
                TextButton(onClick = onFinish, enabled = !last) { Text(if (last) "" else "Skip") }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> IntroPage(
                        icon = Icons.Rounded.Shield,
                        shape = MaterialShapes.Cookie9Sided,
                        title = "Answer the gate.\nSkip the ad.",
                        body = "Every time you approve or deny a visitor, MyGate shows a full-screen ad. " +
                            "ClearGate gets it out of your way automatically.",
                    )
                    1 -> HowItWorksPage()
                    2 -> PrivacyPage()
                    else -> SetupPage(status)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageDots(count = PAGE_COUNT, current = pager.currentPage, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) {
                    Text(
                        when {
                            !last -> "Next"
                            status.serviceOn -> "Get started"
                            else -> "Finish later"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!last) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IntroPage(
    icon: ImageVector,
    shape: androidx.graphics.shapes.RoundedPolygon,
    title: String,
    body: String,
    extra: @Composable (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(16.dp))
        ShapeBadge(
            icon = icon,
            polygon = shape,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 200.dp,
            iconSize = 84.dp,
            spin = true,
        )
        Spacer(Modifier.height(40.dp))
        Text(
            title,
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (extra != null) {
            Spacer(Modifier.height(28.dp))
            extra()
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HowItWorksPage() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "How it works",
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 24.dp),
        )
        Step(1, Icons.Rounded.DoorFront, MaterialShapes.Cookie6Sided, "A visitor arrives",
            "MyGate pops up the entry request, just like always.")
        Step(2, Icons.Rounded.TouchApp, MaterialShapes.Clover4Leaf, "You tap Approve or Deny",
            "Your answer goes to the gate as usual. Nothing about that changes.")
        Step(3, Icons.Rounded.Home, MaterialShapes.Sunny, "The ad is skipped",
            "As soon as MyGate confirms your answer, ClearGate presses Home for you. " +
                "No ad, no MyGate home screen left open.")
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Step(
    number: Int,
    icon: ImageVector,
    shape: androidx.graphics.shapes.RoundedPolygon,
    title: String,
    body: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        ShapeBadge(
            icon = icon,
            polygon = shape,
            container = when (number) {
                1 -> MaterialTheme.colorScheme.secondaryContainer
                2 -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            content = when (number) {
                1 -> MaterialTheme.colorScheme.onSecondaryContainer
                2 -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
            size = 64.dp,
        )
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(
                "STEP $number",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrivacyPage() {
    IntroPage(
        icon = Icons.Rounded.PrivacyTip,
        shape = MaterialShapes.Clover8Leaf,
        title = "Private by design",
        body = "ClearGate uses Android's accessibility feature to see when MyGate shows the ad and to " +
            "press Home. That's all it does with it.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact(Icons.Rounded.Lock, "Only sees the MyGate app. Every other app is invisible to it.")
            Fact(Icons.Rounded.WifiOff, "No internet permission. Nothing ever leaves your phone.")
            Fact(Icons.Rounded.Tune, "Pause it any time with one switch.")
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupPage(status: SetupStatus) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Two quick settings",
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Turn these on and you're done. You can come back to them any time.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 24.dp),
        )
        SetupSteps(status)
        if (!status.serviceOn && restrictedSettingsApply) {
            Spacer(Modifier.height(16.dp))
            RestrictedSettingsHint(onOpen = { SystemScreens.appInfo(context) })
        }
        Spacer(Modifier.height(16.dp))
        BankingSection(status)
        Spacer(Modifier.height(16.dp))
    }
}

/** The checklist shared by onboarding and the home screen. */
@Composable
fun SetupSteps(status: SetupStatus) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SetupStep(
            done = status.serviceOn,
            icon = Icons.Rounded.Accessibility,
            title = "Turn on ClearGate",
            body = "Accessibility › ClearGate › On",
            actionLabel = "Open",
            onAction = { SystemScreens.accessibility(context) },
        )
        SetupStep(
            done = status.batteryUnrestricted,
            icon = Icons.Rounded.BatterySaver,
            title = "Keep it running",
            body = "Let it run in the background so your phone doesn't stop it.",
            actionLabel = "Allow",
            onAction = { SystemScreens.batteryUnrestricted(context) },
        )
    }
}

@Composable
fun RestrictedSettingsHint(onOpen: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Switch greyed out?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Android blocks this for apps installed outside the Play Store. Open App info, tap ⋮ " +
                    "(top right) › Allow restricted settings, then try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) { Text("Open App info") }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 28.dp else 10.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(Modifier.size(width = width, height = 10.dp).clip(CircleShape).background(color))
        }
    }
}
