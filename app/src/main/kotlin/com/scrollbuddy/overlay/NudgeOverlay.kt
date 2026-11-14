package com.scrollbuddy.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.scrollbuddy.detector.Nudge
import com.scrollbuddy.ui.theme.ScrollBuddyTheme

/**
 * The full-screen window the nudge plays in, over whichever app is in front. It is a
 * TYPE_ACCESSIBILITY_OVERLAY window, which needs no permission but can only be added with the
 * accessibility service as [context]. [onTakeBreak] should send the person to the home screen;
 * [onMore] is told when they ask for a little more instead.
 */
class NudgeOverlay(
    private val context: Context,
    private val owner: SavedStateRegistryOwner,
    private val onTakeBreak: () -> Unit,
    private val onMore: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    /** [address] is what the character calls the person. Does nothing while a nudge is showing. */
    fun show(nudge: Nudge, address: String) {
        if (view != null) return
        val resources = context.resources
        val line = nudgeLine(resources, address, nudge.mode)
        val stats = nudgeStats(resources, nudge)
        val moreLabel = nudgeMoreLabel(resources, nudge)
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                ScrollBuddyTheme {
                    NudgeScene(
                        line = line,
                        stats = stats,
                        moreLabel = moreLabel,
                        onMore = {
                            onMore()
                            hide()
                        },
                        onTakeBreak = {
                            onTakeBreak()
                            hide()
                        },
                    )
                }
            }
        }
        try {
            windowManager.addView(composeView, layoutParams())
            view = composeView
        } catch (e: WindowManager.BadTokenException) {
            // The service was switched off between the swipe and this call
            Log.w(TAG, "Could not show the nudge", e)
        }
    }

    fun hide() {
        // removeView rather than removeViewImmediate: this is called from the view's own click
        // handlers, and the window must outlive the touch event that is still being dispatched
        view?.let(windowManager::removeView)
        view = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Not focusable, so the back button and keyboard still belong to the app underneath;
        // touches are taken because the window covers the whole screen
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Under the status bar, the navigation bar and any cutout, so nothing peeks out
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
    }

    private companion object {
        const val TAG = "NudgeOverlay"
    }
}
