package com.scrollbuddy.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.scrollbuddy.BuildConfig
import com.scrollbuddy.detector.DoomScrollDetector
import com.scrollbuddy.detector.LimitMode
import com.scrollbuddy.detector.Nudge
import com.scrollbuddy.history.HistoryStore
import com.scrollbuddy.overlay.CounterBubble
import com.scrollbuddy.overlay.NudgeOverlay
import com.scrollbuddy.overlay.counterText
import com.scrollbuddy.settings.BuddySettings
import com.scrollbuddy.settings.TrackedApp
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Hears from Instagram and YouTube, and nothing else. Its configuration limits it to those two
 * apps and to two kinds of event, something scrolling and a window taking the focus, and gives
 * it no access to screen content.
 * [DoomScrollDetector] picks out the swipes through Reels and Shorts, [HistoryStore] keeps
 * count of them, [CounterBubble] shows the day's count as they happen, and [NudgeOverlay] plays
 * the nudge when the detector asks for one.
 */
class ScrollWatchService : AccessibilityService() {

    private val owner = ServiceLifecycleOwner()
    private lateinit var settings: BuddySettings
    private lateinit var history: HistoryStore
    private lateinit var overlay: NudgeOverlay
    private lateinit var counter: CounterBubble
    private lateinit var detector: DoomScrollDetector

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = detector.onScreenOff()
    }

    // Debug builds only. `adb shell am broadcast -a com.scrollbuddy.DEBUG_NUDGE` plays the nudge
    // in its real window with no swiping, so it can be tried on an emulator that has neither
    // app; DEBUG_COUNT shows the count bubble, with `--ei left 2` for its warning
    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prefs = settings.read()
            val packageName = TrackedApp.Instagram.packageName
            if (intent.action == DEBUG_COUNT_ACTION) {
                val left = intent.getIntExtra(DEBUG_LEFT_EXTRA, Int.MAX_VALUE)
                counter.show(counterText(resources, packageName, today = 12, swipesLeft = left, timeLeftMs = null))
            } else {
                val nudge = Nudge(packageName, swipes = 25, elapsedMs = 12 * 60_000L, count = 1, mode = prefs.limitMode)
                showNudge(nudge)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        owner.start()
        settings = BuddySettings(this)
        history = HistoryStore(this)
        overlay = NudgeOverlay(
            context = this,
            owner = owner,
            onTakeBreak = {
                detector.endSession()
                performGlobalAction(GLOBAL_ACTION_HOME)
            },
            onMore = { detector.snooze(SystemClock.elapsedRealtime()) },
        )
        counter = CounterBubble(this, owner)
        detector = DoomScrollDetector(
            config = settings.read().toDetectorConfig(),
            minSwipeDeltaPx = (resources.displayMetrics.heightPixels * MIN_SWIPE_SCREEN_FRACTION).toInt(),
            onSwipe = { packageName, watchedMs ->
                history.addSwipe(packageName, watchedMs)
                showCount(packageName)
            },
            onNudge = ::showNudge,
        )
        owner.lifecycleScope.launch {
            settings.values.collect { detector.config = it.toDetectorConfig() }
        }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (BuildConfig.DEBUG) {
            // Exported, because the broadcasts come from the adb shell
            val debugActions = IntentFilter(DEBUG_NUDGE_ACTION).apply { addAction(DEBUG_COUNT_ACTION) }
            ContextCompat.registerReceiver(this, debugReceiver, debugActions, ContextCompat.RECEIVER_EXPORTED)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // A window taking the focus carries no change type; the others of this kind are
            // panes appearing inside a window, which say nothing about the app being opened
            val focused = event.contentChangeTypes == 0
            if (BuildConfig.DEBUG && Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "window pkg=$packageName class=$className fullScreen=${event.isFullScreen} focused=$focused")
            }
            if (focused) detector.onWindowFocused(packageName, className, event.isFullScreen)
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (BuildConfig.DEBUG && Log.isLoggable(TAG, Log.DEBUG)) {
            // Debug builds only, after `adb shell setprop log.tag.ScrollWatch DEBUG`. Structure
            // only: the event's text, which is what is on screen, is never read
            Log.d(
                TAG,
                "scroll pkg=$packageName class=$className from=${event.fromIndex} " +
                    "dx=${event.scrollDeltaX} dy=${event.scrollDeltaY}",
            )
        }
        detector.onScrolled(
            packageName = packageName,
            className = className,
            fromIndex = event.fromIndex,
            deltaX = event.scrollDeltaX,
            deltaY = event.scrollDeltaY,
            // Not event.eventTime: that is uptime, which stops while the device sleeps
            nowMs = SystemClock.elapsedRealtime(),
        )
    }

    override fun onInterrupt() = Unit

    private fun showNudge(nudge: Nudge) {
        counter.hide()
        overlay.show(nudge, settings.read().address(resources))
    }

    /** Called for each swipe, before the detector decides whether this one brings the nudge. */
    private fun showCount(packageName: String) {
        val swipesLeft = detector.swipesLeft
        val timeLeftMs = detector.timeLeftMs(SystemClock.elapsedRealtime())
        // Nothing left means the nudge is about to cover the screen, and with no reels allowed
        // it always is
        if (detector.config.mode == LimitMode.Zero || swipesLeft == 0 || timeLeftMs == 0L) return
        val today = history.swipesOn(LocalDate.now(), packageName)
        counter.show(counterText(resources, packageName, today, swipesLeft, timeLeftMs))
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        if (BuildConfig.DEBUG) unregisterReceiver(debugReceiver)
        counter.hide()
        overlay.hide()
        owner.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScrollWatch"
        private const val DEBUG_NUDGE_ACTION = "com.scrollbuddy.DEBUG_NUDGE"
        private const val DEBUG_COUNT_ACTION = "com.scrollbuddy.DEBUG_COUNT"
        private const val DEBUG_LEFT_EXTRA = "left"

        // A real swipe moved the Shorts pager at least a fifth of the screen in the recordings;
        // the few pixels it sends while settling are far below this
        private const val MIN_SWIPE_SCREEN_FRACTION = 0.1f

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java)
            val self = ComponentName(context, ScrollWatchService::class.java)
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val service = info.resolveInfo.serviceInfo
                    ComponentName(service.packageName, service.name) == self
                }
        }
    }
}
