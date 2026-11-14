package com.scrollbuddy.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.scrollbuddy.R
import com.scrollbuddy.ui.theme.ScrollBuddyTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The small bubble at the top of the screen that shows the day's count while the person swipes
 * through Reels or Shorts. Like the nudge it is a TYPE_ACCESSIBILITY_OVERLAY window, which only
 * the accessibility service can add as [context]. It can't be touched, so every touch goes to
 * the app underneath.
 *
 * The service only hears about swipes, not about the person leaving the feed, so the bubble
 * goes away by itself a few seconds after the last swipe.
 */
class CounterBubble(private val context: Context, private val owner: SavedStateRegistryOwner) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var content by mutableStateOf(CounterText("", warning = false))
    private var hiding: Job? = null

    /** Shows [text], or changes what an already showing bubble says. */
    internal fun show(text: CounterText) {
        content = text
        if (view == null) add()
        hiding?.cancel()
        hiding = owner.lifecycleScope.launch {
            delay(SHOWN_MS)
            hide()
        }
    }

    fun hide() {
        hiding?.cancel()
        hiding = null
        view?.let(windowManager::removeView)
        view = null
    }

    private fun add() {
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                ScrollBuddyTheme {
                    // Room below for the pill's shadow, which the window would cut off
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        CounterPill(content)
                    }
                }
            }
        }
        try {
            windowManager.addView(composeView, layoutParams())
            view = composeView
        } catch (e: WindowManager.BadTokenException) {
            // The service was switched off between the swipe and this call
            Log.w(TAG, "Could not show the count", e)
        }
    }

    // As wide as the screen, with the pill in the middle: a window that wraps the pill keeps
    // its first width and cuts a longer text off
    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Laid out inside the system bars, so the top is just under the status bar
        gravity = Gravity.TOP
    }

    private companion object {
        const val TAG = "CounterBubble"
        const val SHOWN_MS = 3_500L
    }
}

@Composable
private fun CounterPill(content: CounterText, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (content.warning) colors.primary else colors.inverseSurface)
    val foreground by animateColorAsState(if (content.warning) colors.onPrimary else colors.inverseOnSurface)
    Surface(
        modifier = modifier.animateContentSize(),
        shape = CircleShape,
        color = background,
        contentColor = foreground,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, top = 6.dp, end = 16.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_head),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.secondaryContainer),
            )
            Text(content.text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview
@Composable
private fun CounterPillPreview() {
    ScrollBuddyTheme { CounterPill(CounterText("Vaathi coming… 2 left", warning = true)) }
}
