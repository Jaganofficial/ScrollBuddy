package com.scrollbuddy.detector

import kotlin.math.abs

/**
 * Turns the scroll events of one short-video pager into swipes, each a move to another video.
 * One swipe produces several events, so the events themselves can't be counted.
 *
 * Not thread-safe; call it from one thread.
 */
class SwipeCounter(private val minDeltaPx: Int) {
    private var lastIndex = NO_INDEX
    private var swipeSign = 0
    private var lastDeltaMs = 0L

    /** Returns true when this event is the start of a new swipe. */
    fun onPagerScroll(fromIndex: Int, deltaY: Int, nowMs: Long): Boolean {
        if (fromIndex >= 0) {
            // The pager says which video it is on, so a swipe is simply a change of position
            val previous = lastIndex
            lastIndex = fromIndex
            return previous != NO_INDEX && previous != fromIndex
        }
        // A pager that reports only distances sends each swipe as a move one way followed within
        // a fraction of a second by an equal move back, then sometimes a few leftover pixels
        if (abs(deltaY) < minDeltaPx) return false
        val sign = if (deltaY > 0) 1 else -1
        val isEcho = swipeSign != 0 && sign != swipeSign && nowMs - lastDeltaMs <= ECHO_WINDOW_MS
        lastDeltaMs = nowMs
        if (isEcho) return false
        swipeSign = sign
        return true
    }

    fun reset() {
        lastIndex = NO_INDEX
        swipeSign = 0
        lastDeltaMs = 0L
    }

    private companion object {
        const val NO_INDEX = -1

        // The move back arrived at most 154 ms after the move out in the recordings, and the
        // fastest flicking left 246 ms between one swipe's last event and the next swipe
        const val ECHO_WINDOW_MS = 300L
    }
}
