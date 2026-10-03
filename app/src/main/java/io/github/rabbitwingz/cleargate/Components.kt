package io.github.rabbitwingz.cleargate

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/** An icon on one of Material's expressive shapes (cookie, burst, clover...), optionally turning slowly. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeBadge(
    icon: ImageVector,
    polygon: RoundedPolygon,
    container: Color,
    content: Color,
    size: Dp,
    iconSize: Dp = size * 0.45f,
    spin: Boolean = false,
    /** False for multi-colour icons (like clearGateMark) that carry their own colours. */
    tintIcon: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = polygon.toShape()
    val turning = rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 30_000, easing = LinearEasing)),
        label = "rotation",
    )
    val rotation = if (spin) turning.value else 0f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation }
                .clip(shape)
                .background(container)
        )
        Icon(
            icon,
            contentDescription = null,
            tint = if (tintIcon) content else Color.Unspecified,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * ClearGate's mark (the launcher icon's gate arch and chevrons) as a two-tone vector, so it can follow the theme.
 * Same paths as res/drawable/ic_tile.xml.
 */
@Composable
fun clearGateMark(arch: Color, chevrons: Color): ImageVector = remember(arch, chevrons) {
    ImageVector.Builder(
        name = "ClearGateMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 58f,
        viewportHeight = 58f,
    ).apply {
        addGroup(translationX = -25f, translationY = -26f)
        addPath(
            pathData = addPathNodes("M35,77 L35,52 A19,19 0 0 1 73,52 L73,77"),
            stroke = SolidColor(arch),
            strokeLineWidth = 7f,
            strokeLineCap = StrokeCap.Round,
        )
        addPath(
            pathData = addPathNodes("M45,54 L53,62 L45,70 M55,54 L63,62 L55,70"),
            stroke = SolidColor(chevrons),
            strokeLineWidth = 6.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        clearGroup()
    }.build()
}

/** One step of the setup checklist: what it is, whether it's done, and a button to fix it. */
@Composable
fun SetupStep(
    done: Boolean,
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = if (done) colors.surfaceContainerHigh else colors.surfaceContainerHighest,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (done) colors.primary else colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (done) Icons.Rounded.CheckCircle else icon,
                    contentDescription = if (done) "Done" else null,
                    tint = if (done) colors.onPrimary else colors.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (!done) {
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
