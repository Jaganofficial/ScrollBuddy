package com.scrollbuddy.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoomScrollDetectorTest {

    private val nudges = mutableListOf<Nudge>()
    private val watched = mutableListOf<Pair<String, Long>>()
    private val detector = DoomScrollDetector(
        config = DetectorConfig(
            trackedPackages = setOf(INSTAGRAM, YOUTUBE),
            mode = LimitMode.Swipes,
            nudgeAfterSwipes = 20,
            nudgeAfterMs = 10 * MINUTE,
            snoozeSwipes = 5,
            snoozeMs = 5 * MINUTE,
            idleResetMs = 3 * MINUTE,
        ),
        minSwipeDeltaPx = 240,
        onSwipe = { packageName, watchedMs -> watched += packageName to watchedMs },
        onNudge = { nudges += it },
    )
    private var reel = 0

    /**
     * Swipes through [count] reels, one every [stepMs]. Each swipe is two events, as on a real
     * phone: the pager starts to move on the old reel, then lands on the next 100 ms later.
     */
    private fun swipeReels(count: Int, fromMs: Long, stepMs: Long = 2 * SECOND) {
        repeat(count) { i ->
            val at = fromMs + i * stepMs
            detector.onScrolled(INSTAGRAM, REELS_PAGER, fromIndex = reel, deltaX = 0, deltaY = 200, nowMs = at)
            detector.onScrolled(INSTAGRAM, REELS_PAGER, fromIndex = ++reel, deltaX = 0, deltaY = 1500, nowMs = at + 100)
        }
    }

    /** Swipes through [count] Shorts: a move up and its echo 100 ms later. */
    private fun swipeShorts(count: Int, fromMs: Long, stepMs: Long = 2 * SECOND) {
        repeat(count) { i ->
            val at = fromMs + i * stepMs
            detector.onScrolled(YOUTUBE, SHORTS_PAGER, fromIndex = -1, deltaX = 0, deltaY = -900, nowMs = at)
            detector.onScrolled(YOUTUBE, SHORTS_PAGER, fromIndex = -1, deltaX = 0, deltaY = 900, nowMs = at + 100)
        }
    }

    @Test
    fun `nudges when the swipe limit is reached`() {
        swipeReels(count = 20, fromMs = 0)

        assertEquals(
            listOf(Nudge(INSTAGRAM, swipes = 20, elapsedMs = 38 * SECOND + 100, count = 1, mode = LimitMode.Swipes)),
            nudges,
        )
    }

    @Test
    fun `does not nudge below the swipe limit`() {
        swipeReels(count = 19, fromMs = 0)

        assertTrue(nudges.isEmpty())
    }

    @Test
    fun `the time limit is ignored while swipes are limited`() {
        // One reel a minute goes past ten minutes long before it reaches twenty swipes
        swipeReels(count = 15, fromMs = 0, stepMs = MINUTE)

        assertTrue(nudges.isEmpty())
    }

    @Test
    fun `nudges when the time limit is reached`() {
        detector.config = detector.config.copy(mode = LimitMode.Time)
        // One reel a minute: the eleventh swipe is the first one past ten minutes
        swipeReels(count = 15, fromMs = 0, stepMs = MINUTE)

        assertEquals(
            Nudge(INSTAGRAM, swipes = 11, elapsedMs = 10 * MINUTE + 100, count = 1, mode = LimitMode.Time),
            nudges.single(),
        )
    }

    @Test
    fun `the swipe limit is ignored while time is limited`() {
        detector.config = detector.config.copy(mode = LimitMode.Time)
        swipeReels(count = 60, fromMs = 0)

        assertTrue(nudges.isEmpty())
    }

    @Test
    fun `with no reels allowed the first swipe nudges, and so does every one after it`() {
        detector.config = detector.config.copy(mode = LimitMode.Zero)
        swipeReels(count = 2, fromMs = 0)

        assertEquals(listOf(1, 2), nudges.map { it.swipes })
        assertEquals(LimitMode.Zero, nudges.first().mode)
    }

    @Test
    fun `nudges again after a full limit when no more was asked for`() {
        swipeReels(count = 39, fromMs = 0)
        assertEquals(1, nudges.size)

        swipeReels(count = 1, fromMs = 78 * SECOND)

        assertEquals(listOf(20, 40), nudges.map { it.swipes })
        assertEquals(listOf(1, 2), nudges.map { it.count })
    }

    @Test
    fun `after a little more is asked for, nudges again five swipes later`() {
        swipeReels(count = 20, fromMs = 0)
        detector.snooze(nowMs = 45 * SECOND)

        swipeReels(count = 4, fromMs = 50 * SECOND)
        assertEquals(1, nudges.size)
        swipeReels(count = 1, fromMs = 58 * SECOND)

        assertEquals(listOf(20, 25), nudges.map { it.swipes })
    }

    @Test
    fun `the limit after that is the full one again`() {
        swipeReels(count = 20, fromMs = 0)
        detector.snooze(nowMs = 45 * SECOND)
        swipeReels(count = 5, fromMs = 50 * SECOND)

        swipeReels(count = 19, fromMs = 60 * SECOND)
        assertEquals(2, nudges.size)
        swipeReels(count = 1, fromMs = 98 * SECOND)

        assertEquals(listOf(20, 25, 45), nudges.map { it.swipes })
    }

    @Test
    fun `a little more time is five minutes from when it was asked for`() {
        detector.config = detector.config.copy(mode = LimitMode.Time)
        swipeReels(count = 11, fromMs = 0, stepMs = MINUTE)
        // Answered a minute after the nudge: the five minutes run from 11 min, not from 10
        detector.snooze(nowMs = 11 * MINUTE)

        swipeReels(count = 5, fromMs = 11 * MINUTE + SECOND, stepMs = MINUTE)
        assertEquals(1, nudges.size)
        swipeReels(count = 1, fromMs = 16 * MINUTE)

        assertEquals(2, nudges.size)
    }

    @Test
    fun `tells how many swipes are left`() {
        assertNull(detector.swipesLeft)

        swipeReels(count = 17, fromMs = 0)
        assertEquals(3, detector.swipesLeft)

        swipeReels(count = 3, fromMs = 34 * SECOND)
        detector.snooze(nowMs = 45 * SECOND)
        swipeReels(count = 1, fromMs = 50 * SECOND)
        assertEquals(4, detector.swipesLeft)
        assertNull(detector.timeLeftMs(nowMs = 51 * SECOND))
    }

    @Test
    fun `tells how much time is left`() {
        detector.config = detector.config.copy(mode = LimitMode.Time)
        swipeReels(count = 1, fromMs = 0)

        assertEquals(9 * MINUTE, detector.timeLeftMs(nowMs = MINUTE))
        assertNull(detector.swipesLeft)
    }

    @Test
    fun `counts Shorts swipes too`() {
        swipeShorts(count = 20, fromMs = 0)

        assertEquals(
            Nudge(YOUTUBE, swipes = 20, elapsedMs = 38 * SECOND, count = 1, mode = LimitMode.Swipes),
            nudges.single(),
        )
    }

    @Test
    fun `counts the recorded Shorts session swipe for swipe`() {
        SwipeCounterTest.RECORDED_SHORTS.forEach { (deltaY, atMs) ->
            detector.onScrolled(YOUTUBE, SHORTS_PAGER, fromIndex = -1, deltaX = 0, deltaY = deltaY, nowMs = atMs)
        }

        assertEquals(15, watched.size)
    }

    @Test
    fun `a long pause starts a new session`() {
        swipeReels(count = 15, fromMs = 0)
        swipeReels(count = 15, fromMs = 30 * SECOND + 4 * MINUTE)

        assertTrue(nudges.isEmpty())
    }

    @Test
    fun `coming back to the app starts a new session`() {
        detector.onWindowFocused(INSTAGRAM, SCREEN, fullScreen = true)
        swipeReels(count = 15, fromMs = 0)
        // Home and back: nothing is heard from the home screen, then the same window again
        detector.onWindowFocused(INSTAGRAM, SCREEN, fullScreen = true)
        swipeReels(count = 15, fromMs = 31 * SECOND)

        assertTrue(nudges.isEmpty())
    }

    @Test
    fun `a dialog inside the app does not start a new session`() {
        detector.onWindowFocused(INSTAGRAM, SCREEN, fullScreen = true)
        swipeReels(count = 15, fromMs = 0)
        detector.onWindowFocused(INSTAGRAM, DIALOG, fullScreen = false)
        detector.onWindowFocused(INSTAGRAM, SCREEN, fullScreen = true)
        swipeReels(count = 5, fromMs = 31 * SECOND)

        assertEquals(20, nudges.single().swipes)
    }

    @Test
    fun `another screen of the app does not start a new session`() {
        detector.onWindowFocused(INSTAGRAM, SCREEN, fullScreen = true)
        swipeReels(count = 15, fromMs = 0)
        detector.onWindowFocused(INSTAGRAM, OTHER_SCREEN, fullScreen = true)
        detector.onWindowFocused(INSTAGRAM, SCREEN, fullScreen = true)
        swipeReels(count = 5, fromMs = 31 * SECOND)

        assertEquals(20, nudges.single().swipes)
    }

    @Test
    fun `a message shown inside a screen is not the person coming back`() {
        detector.onWindowFocused(INSTAGRAM, SCREEN, fullScreen = true)
        swipeReels(count = 15, fromMs = 0)
        detector.onWindowFocused(INSTAGRAM, MESSAGE, fullScreen = false)
        swipeReels(count = 5, fromMs = 31 * SECOND)

        assertEquals(20, nudges.single().swipes)
    }

    @Test
    fun `opening the app again after such a message is still noticed`() {
        detector.onWindowFocused(INSTAGRAM, SCREEN, fullScreen = true)
        swipeReels(count = 15, fromMs = 0)
        detector.onWindowFocused(INSTAGRAM, MESSAGE, fullScreen = false)
        // An app brought back from the home screen reports its screen twice: shown, then focused
        detector.onWindowFocused(INSTAGRAM, SCREEN, fullScreen = true)
        detector.onWindowFocused(INSTAGRAM, SCREEN, fullScreen = true)
        swipeReels(count = 15, fromMs = 31 * SECOND)

        assertTrue(nudges.isEmpty())
    }

    @Test
    fun `the first window heard of does not end a session already going`() {
        swipeReels(count = 15, fromMs = 0)
        detector.onWindowFocused(INSTAGRAM, SCREEN, fullScreen = true)
        swipeReels(count = 5, fromMs = 31 * SECOND)

        assertEquals(20, nudges.single().swipes)
    }

    @Test
    fun `coming back to the other app leaves this one's session alone`() {
        detector.onWindowFocused(YOUTUBE, SCREEN, fullScreen = true)
        swipeReels(count = 15, fromMs = 0)
        detector.onWindowFocused(YOUTUBE, SCREEN, fullScreen = true)
        swipeReels(count = 5, fromMs = 31 * SECOND)

        assertEquals(20, nudges.single().swipes)
    }

    @Test
    fun `closing the app from the nudge starts a new session`() {
        swipeReels(count = 20, fromMs = 0)
        detector.endSession()
        swipeReels(count = 20, fromMs = 45 * SECOND)

        // A count that starts again at one, and a first nudge again
        assertEquals(listOf(20, 20), nudges.map { it.swipes })
        assertEquals(listOf(1, 1), nudges.map { it.count })
    }

    @Test
    fun `screen off starts a new session`() {
        swipeReels(count = 15, fromMs = 0)
        detector.onScreenOff()
        swipeReels(count = 15, fromMs = 31 * SECOND)

        assertTrue(nudges.isEmpty())
    }

    @Test
    fun `moving to the other app starts a new session`() {
        swipeReels(count = 15, fromMs = 0)
        swipeShorts(count = 15, fromMs = 31 * SECOND)

        assertTrue(nudges.isEmpty())
    }

    @Test
    fun `scrolling the feed, comments or a grid counts for nothing`() {
        repeat(60) { i ->
            detector.onScrolled(INSTAGRAM, LIST, fromIndex = i, deltaX = 0, deltaY = 900, nowMs = i * SECOND)
            detector.onScrolled(YOUTUBE, LEGACY_LIST, fromIndex = i, deltaX = 0, deltaY = 900, nowMs = i * SECOND)
        }

        assertTrue(watched.isEmpty())
        assertTrue(nudges.isEmpty())
    }

    @Test
    fun `swiping between Instagram's tabs counts for nothing`() {
        repeat(60) { i ->
            detector.onScrolled(INSTAGRAM, REELS_PAGER, fromIndex = i % 4, deltaX = -1060, deltaY = 0, nowMs = i * SECOND)
        }

        assertTrue(watched.isEmpty())
    }

    @Test
    fun `an app that is switched off in settings counts for nothing`() {
        detector.config = detector.config.copy(trackedPackages = setOf(YOUTUBE))
        swipeReels(count = 30, fromMs = 0)

        assertTrue(watched.isEmpty())
        assertTrue(nudges.isEmpty())
    }

    @Test
    fun `reports each swipe with the time spent on the video just left`() {
        swipeReels(count = 3, fromMs = 0, stepMs = 5 * SECOND)

        // The first reel is timed from the session's first event, 100 ms before its swipe
        assertEquals(listOf(INSTAGRAM to 100L, INSTAGRAM to 5 * SECOND, INSTAGRAM to 5 * SECOND), watched)
    }

    @Test
    fun `a lower swipe limit applies to the session in progress`() {
        swipeReels(count = 10, fromMs = 0)
        detector.config = detector.config.copy(nudgeAfterSwipes = 10)
        swipeReels(count = 1, fromMs = 20 * SECOND)

        assertEquals(11, nudges.single().swipes)
    }

    private companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60 * SECOND
        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"
        const val REELS_PAGER = "androidx.viewpager.widget.ViewPager"
        const val SCREEN = "com.instagram.mainactivity.InstagramMainActivity"
        const val OTHER_SCREEN = "com.instagram.modal.ModalActivity"
        const val DIALOG = "android.app.Dialog"
        const val MESSAGE = "com.google.android.material.snackbar.Snackbar"
        const val SHORTS_PAGER = "android.widget.FrameLayout"
        const val LIST = "androidx.recyclerview.widget.RecyclerView"
        const val LEGACY_LIST = "android.support.v7.widget.RecyclerView"
    }
}
