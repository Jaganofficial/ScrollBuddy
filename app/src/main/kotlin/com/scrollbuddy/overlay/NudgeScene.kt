package com.scrollbuddy.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scrollbuddy.R
import com.scrollbuddy.ui.CLOSE_SHOT_PIVOT_Y
import com.scrollbuddy.ui.CLOSE_SHOT_SCALE
import com.scrollbuddy.ui.Vaathi
import com.scrollbuddy.ui.VaathiClip
import com.scrollbuddy.ui.theme.ScrollBuddyTheme
import kotlinx.coroutines.launch
import kotlin.math.hypot

private val MAX_CHARACTER_HEIGHT = 440.dp

// The most of the screen's height the character takes, so the bubble has room above him
private const val CHARACTER_HEIGHT_SHARE = 0.5f
private val SCRIM = Color(0xFF0B1220)
private const val SCRIM_ALPHA = 0.94f
private const val CURTAIN_MS = 900

// He is in place by the time his waving hand is up
private const val ENTER_MS = 700
private const val CAMERA_MS = 280
private const val EXIT_MS = 220

// The bubble pops up from the tip of its tail
private const val BUBBLE_START_SCALE = 0.6f
private val BUBBLE_ORIGIN = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)

/** What the character is doing; each act plays one clip. */
private enum class Act(val clip: VaathiClip) {
    Greeting(VaathiClip.Wave),
    Stopping(VaathiClip.Stop),
    Holding(VaathiClip.Hold),
    Leaving(VaathiClip.Sweep),
}

/**
 * The whole nudge: a dark curtain sweeps across the screen from the left and the character
 * slides in behind it, waving. The view then moves in close, he holds up a stop sign and
 * [line] appears above him, with [stats] as the smaller line under it. Agreeing to stop moves
 * the view back out for a sweep of his hand before [onTakeBreak]. [moreLabel] is the button
 * that asks for a little more and calls [onMore]; with none, agreeing is the only way out.
 * [onMore] and [onTakeBreak] run after the scene has faded out, so the caller can remove its
 * window straight away.
 */
@Composable
fun NudgeScene(
    line: String,
    stats: String,
    moreLabel: String?,
    onMore: () -> Unit,
    onTakeBreak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val curtain = remember { Animatable(0f) }
    val enter = remember { Animatable(0f) }
    // 0 is the full shot and 1 the close one. A full-shot clip is enlarged on the way between
    // them, so the change of clip at either end shows as the view moving, not as a jump
    val camera = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var act by remember { mutableStateOf(Act.Greeting) }
    var gone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { curtain.animateTo(1f, tween(CURTAIN_MS, easing = FastOutSlowInEasing)) }
        enter.animateTo(1f, tween(ENTER_MS, easing = LinearOutSlowInEasing))
    }

    fun leaveThen(action: () -> Unit) {
        if (gone) return
        gone = true
        scope.launch {
            fade.animateTo(0f, tween(EXIT_MS))
            action()
        }
    }

    fun clipFinished() {
        when (act) {
            Act.Greeting -> scope.launch {
                camera.animateTo(1f, tween(CAMERA_MS, easing = FastOutSlowInEasing))
                act = Act.Stopping
            }
            Act.Stopping -> act = Act.Holding
            Act.Holding -> Unit
            Act.Leaving -> leaveThen(onTakeBreak)
        }
    }

    fun takeBreak() {
        if (act == Act.Leaving) return
        act = Act.Leaving
        scope.launch { camera.animateTo(0f, tween(CAMERA_MS, easing = FastOutSlowInEasing)) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            // No touch handling here. The window this plays in covers the screen, which already
            // keeps touches from the app underneath, and consuming them here as well cancels
            // the buttons' own taps: a finger that moves at all between down and up sends a
            // move event, and a button gives up its tap when an ancestor consumes one
            .drawBehind {
                drawCircle(
                    color = SCRIM,
                    radius = hypot(size.width, size.height) * curtain.value,
                    center = Offset(0f, size.height / 2),
                    alpha = SCRIM_ALPHA,
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // As tall as leaves the widest clip, the sweep, room across the screen
        val characterHeight = minOf(
            maxWidth / VaathiClip.Sweep.aspect,
            maxHeight * CHARACTER_HEIGHT_SHARE,
            MAX_CHARACTER_HEIGHT,
        )
        val density = LocalDensity.current
        val startOffsetPx = with(density) { -(maxWidth + characterHeight * VaathiClip.Wave.aspect).toPx() / 2 }
        val navigationBarPx = WindowInsets.navigationBars.getBottom(density)
        // A close shot is cut off at the thighs, so the character's frame sits on the bottom
        // edge of the screen, over the navigation bar, as if the rest of him were below it.
        // Only the bubble keeps clear of the system bars
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The bubble is in the tree only while it shows, and grows from BUBBLE_START_SCALE,
            // never from nothing. Hidden at a scale of zero it could still be touched, and
            // Compose 1.11 cannot map a touch into a layer scaled to zero: it remembers that
            // failure after the layer is back at full size, and from then on no touch reaches
            // the buttons
            AnimatedVisibility(
                visible = act == Act.Stopping || act == Act.Holding,
                enter = scaleIn(
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                    initialScale = BUBBLE_START_SCALE,
                    transformOrigin = BUBBLE_ORIGIN,
                ) + fadeIn(),
                exit = scaleOut(tween(EXIT_MS), BUBBLE_START_SCALE, BUBBLE_ORIGIN) + fadeOut(tween(EXIT_MS)),
            ) {
                SpeechBubble(
                    line = line,
                    stats = stats,
                    moreLabel = moreLabel,
                    onMore = { leaveThen(onMore) },
                    onTakeBreak = ::takeBreak,
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        )
                        .padding(horizontal = 24.dp),
                )
            }
            val clip = act.clip
            Vaathi(
                clip = clip,
                loop = act == Act.Holding,
                onFinished = ::clipFinished,
                modifier = Modifier
                    .height(characterHeight)
                    .graphicsLayer {
                        translationX = startOffsetPx * (1f - enter.value)
                        if (!clip.closeShot) {
                            val closeness = camera.value
                            val zoom = 1f + (CLOSE_SHOT_SCALE - 1f) * closeness
                            scaleX = zoom
                            scaleY = zoom
                            transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = CLOSE_SHOT_PIVOT_Y)
                            // In a full shot his feet show, and they stand above the navigation bar
                            translationY = -navigationBarPx * (1f - closeness)
                        }
                    },
            )
        }
    }
}

@Composable
private fun SpeechBubble(
    line: String,
    stats: String,
    moreLabel: String?,
    onMore: () -> Unit,
    onTakeBreak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Not the plain surface colour: at night that is the navy of the curtain behind the bubble
    val bubbleColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(24.dp),
            color = bubbleColor,
            shadowElevation = 8.dp,
        ) {
            Column(
                // A text button brings its own room below it
                modifier = Modifier.padding(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = if (moreLabel == null) 20.dp else 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.nudge_title),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = line,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stats,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onTakeBreak, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nudge_take_break))
                }
                if (moreLabel != null) {
                    TextButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) { Text(moreLabel) }
                }
            }
        }
        // The tail, pointing down at the character
        Canvas(Modifier.size(width = 28.dp, height = 14.dp)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(tail, bubbleColor)
        }
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun NudgeScenePreview() {
    ScrollBuddyTheme {
        NudgeScene(
            line = "I am waiting, Nanba… andha phone keezha pora varaikkum.",
            stats = "That's 25 reels in 12 minutes.",
            moreLabel = "Inu 5 reels sir",
            onMore = {},
            onTakeBreak = {},
        )
    }
}
