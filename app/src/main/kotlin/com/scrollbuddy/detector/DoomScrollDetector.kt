package com.scrollbuddy.detector

/** Which limit is in force. Only one is at a time. */
enum class LimitMode {
    /** The nudge comes after a number of swipes. */
    Swipes,

    /** The nudge comes after a length of time. */
    Time,

    /** No reels at all: the nudge comes at the first swipe and offers no more. */
    Zero,
}

data class DetectorConfig(
    val trackedPackages: Set<String>,
    val mode: LimitMode,
    val nudgeAfterSwipes: Int,
    val nudgeAfterMs: Long,
    val snoozeSwipes: Int = DEFAULT_SNOOZE_SWIPES,
    val snoozeMs: Long = DEFAULT_SNOOZE_MS,
    val idleResetMs: Long = DEFAULT_IDLE_RESET_MS,
) {
    companion object {
        // What "a little more" buys: the nudge's second button names these
        const val DEFAULT_SNOOZE_SWIPES = 5
        const val DEFAULT_SNOOZE_MS = 5 * 60_000L

        // A safety net only: a session normally ends when the person leaves the app. Should
        // that ever go unseen, this long without a swipe ends it anyway
        const val DEFAULT_IDLE_RESET_MS = 10 * 60_000L
    }
}

/**
 * [swipes] and [elapsedMs] describe the session so far. [count] is 1 for the session's first
 * nudge and grows with each one after it. [mode] is the limit that brought it.
 */
data class Nudge(
    val packageName: String,
    val swipes: Int,
    val elapsedMs: Long,
    val count: Int,
    val mode: LimitMode,
)

/**
 * Decides when someone has been swiping through Reels or Shorts for too long.
 *
 * It is fed every scroll event from the tracked apps and keeps only those from a short-video
 * pager (see [ShortVideoFeed]). A session runs from the first swipe after the app is opened
 * until the person leaves the app: [onWindowFocused] sees them come back, and [endSession] and
 * [onScreenOff] are told when they go. The nudge fires when the session uses up its allowance:
 * the limit of [DetectorConfig.mode], counted from the session's start or from its last nudge.
 * After [snooze] the next allowance is the smaller one the person asked for.
 *
 * [onSwipe] is called for every swipe with the time since the session's previous swipe, which
 * is the time spent on the video that was just left.
 *
 * Not thread-safe; call it from one thread. Timestamps must come from a clock that keeps running
 * while the device sleeps.
 */
class DoomScrollDetector(
    var config: DetectorConfig,
    minSwipeDeltaPx: Int,
    private val onSwipe: (packageName: String, watchedMs: Long) -> Unit,
    private val onNudge: (Nudge) -> Unit,
) {
    private val counter = SwipeCounter(minSwipeDeltaPx)
    private var sessionPackage: String? = null
    private var sessionStartMs = 0L
    private var lastEventMs = 0L
    private var lastSwipeMs = 0L
    private var swipes = 0
    private var nudges = 0

    // The allowance in use is counted from here: the session's start, or its last nudge
    private var allowanceFromSwipes = 0
    private var allowanceFromMs = 0L
    private var snoozed = false

    private val appWindows = HashMap<String, AppWindows>()

    /** One app's last screen to take the focus, and whether anything of its own has since. */
    private class AppWindows {
        var screen: String? = null
        var inAppSince = false
    }

    private val swipeAllowance: Int get() = if (snoozed) config.snoozeSwipes else config.nudgeAfterSwipes
    private val timeAllowanceMs: Long get() = if (snoozed) config.snoozeMs else config.nudgeAfterMs

    /** Swipes left before the next nudge, or null when swipes are not what is limited. */
    val swipesLeft: Int?
        get() = if (config.mode == LimitMode.Swipes && sessionPackage != null) {
            (swipeAllowance - (swipes - allowanceFromSwipes)).coerceAtLeast(0)
        } else {
            null
        }

    /** Time left before the next nudge, or null when time is not what is limited. */
    fun timeLeftMs(nowMs: Long): Long? = if (config.mode == LimitMode.Time && sessionPackage != null) {
        (timeAllowanceMs - (nowMs - allowanceFromMs)).coerceAtLeast(0L)
    } else {
        null
    }

    fun onScrolled(packageName: String, className: String, fromIndex: Int, deltaX: Int, deltaY: Int, nowMs: Long) {
        if (packageName !in config.trackedPackages) return
        val feed = ShortVideoFeed.of(packageName) ?: return
        if (!feed.isPagerScroll(className, fromIndex, deltaX, deltaY)) return

        if (packageName != sessionPackage || nowMs - lastEventMs > config.idleResetMs) {
            startSession(packageName, nowMs)
        }
        lastEventMs = nowMs
        if (!counter.onPagerScroll(fromIndex, deltaY, nowMs)) return

        swipes++
        onSwipe(packageName, nowMs - lastSwipeMs)
        lastSwipeMs = nowMs
        val used = when (config.mode) {
            LimitMode.Swipes -> swipes - allowanceFromSwipes >= swipeAllowance
            LimitMode.Time -> nowMs - allowanceFromMs >= timeAllowanceMs
            LimitMode.Zero -> true
        }
        if (used) nudge(packageName, nowMs)
    }

    /**
     * The person answered the last nudge by asking for a little more: the next one comes after
     * [DetectorConfig.snoozeSwipes] or [DetectorConfig.snoozeMs], counted from now.
     */
    fun snooze(nowMs: Long) {
        snoozed = true
        allowanceFromMs = nowMs
    }

    /**
     * A window of [packageName] took the focus, or something was shown inside one: a screen
     * named by [className] when [fullScreen], otherwise a dialog, a menu or a passing message.
     *
     * Going from one of an app's screens to a dialog or another screen and back is heard from
     * at each step. The same screen taking the focus twice with nothing of the app's in
     * between means the focus went outside the app, to the home screen, another app or the
     * notification shade, and nothing is heard from those. So that is the person coming back,
     * and the swipes from here on are a new session.
     */
    fun onWindowFocused(packageName: String, className: String, fullScreen: Boolean) {
        if (packageName !in config.trackedPackages) return
        val windows = appWindows.getOrPut(packageName, ::AppWindows)
        if (!fullScreen) {
            windows.inAppSince = true
            return
        }
        val cameBack = className == windows.screen && !windows.inAppSince
        windows.screen = className
        windows.inAppSince = false
        if (cameBack && packageName == sessionPackage) endSession()
    }

    /** The person left the app, so the next swipe starts a new session. */
    fun endSession() {
        sessionPackage = null
    }

    fun onScreenOff() = endSession()

    private fun nudge(packageName: String, nowMs: Long) {
        nudges++
        // A full allowance again, unless the person asks for the small one
        allowanceFromSwipes = swipes
        allowanceFromMs = nowMs
        snoozed = false
        onNudge(Nudge(packageName, swipes, nowMs - sessionStartMs, nudges, config.mode))
    }

    private fun startSession(packageName: String, nowMs: Long) {
        sessionPackage = packageName
        sessionStartMs = nowMs
        lastSwipeMs = nowMs
        swipes = 0
        nudges = 0
        allowanceFromSwipes = 0
        allowanceFromMs = nowMs
        snoozed = false
        counter.reset()
    }
}
