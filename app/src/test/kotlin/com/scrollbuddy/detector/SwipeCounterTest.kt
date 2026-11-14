package com.scrollbuddy.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeCounterTest {

    private val counter = SwipeCounter(minDeltaPx = 240)

    @Test
    fun `counts each change of position in a pager that reports positions`() {
        val swipes = RECORDED_REELS.count { (index, deltaY, atMs) -> counter.onPagerScroll(index, deltaY, atMs) }

        // Forward to reel 3, back to 2, then forward to 5
        assertEquals(7, swipes)
    }

    @Test
    fun `the first position seen is a starting point, not a swipe`() {
        assertFalse(counter.onPagerScroll(fromIndex = 6, deltaY = 300, nowMs = 0))
        assertTrue(counter.onPagerScroll(fromIndex = 7, deltaY = 1500, nowMs = 100))
    }

    @Test
    fun `counts one swipe for each move and its echo in a pager that reports only distances`() {
        val swipes = RECORDED_SHORTS.count { (deltaY, atMs) -> counter.onPagerScroll(NO_INDEX, deltaY, atMs) }

        assertEquals(15, swipes)
    }

    @Test
    fun `a move the other way after a pause is a swipe back, not an echo`() {
        assertTrue(counter.onPagerScroll(NO_INDEX, deltaY = -700, nowMs = 0))
        assertFalse(counter.onPagerScroll(NO_INDEX, deltaY = 700, nowMs = 120))

        assertTrue(counter.onPagerScroll(NO_INDEX, deltaY = 700, nowMs = 5_000))
        assertFalse(counter.onPagerScroll(NO_INDEX, deltaY = -700, nowMs = 5_120))
    }

    @Test
    fun `small movements are not swipes`() {
        assertFalse(counter.onPagerScroll(NO_INDEX, deltaY = 0, nowMs = 0))
        assertFalse(counter.onPagerScroll(NO_INDEX, deltaY = -150, nowMs = 1_000))
        assertFalse(counter.onPagerScroll(NO_INDEX, deltaY = 150, nowMs = 1_100))
    }

    @Test
    fun `reset forgets the last position`() {
        counter.onPagerScroll(fromIndex = 3, deltaY = 300, nowMs = 0)
        counter.reset()

        assertFalse(counter.onPagerScroll(fromIndex = 9, deltaY = 300, nowMs = 1_000))
    }

    companion object {
        const val NO_INDEX = -1

        /** Instagram's Reels pager on a phone: position, vertical distance, milliseconds. */
        val RECORDED_REELS = listOf(
            Triple(0, 214, 0L), Triple(1, 1545, 115L), Triple(1, 217, 138L), Triple(1, 4, 225L),
            Triple(1, 274, 4_132L), Triple(2, 1683, 4_327L), Triple(2, 21, 4_336L), Triple(2, 2, 4_456L),
            Triple(2, 72, 8_287L), Triple(3, 169, 8_367L), Triple(3, 3, 8_476L),
            Triple(2, -1909, 9_479L), Triple(2, -71, 9_565L),
            Triple(2, 135, 10_917L), Triple(2, 3, 11_011L), Triple(2, 9, 11_458L),
            Triple(3, 336, 11_565L), Triple(3, 10, 11_661L),
            Triple(4, 1890, 13_283L), Triple(4, 90, 13_400L),
            Triple(5, 1006, 21_906L), Triple(5, 962, 22_043L), Triple(5, 12, 22_051L),
        )

        /** YouTube's Shorts pager on the same phone: vertical distance, milliseconds. */
        val RECORDED_SHORTS = listOf(
            0 to 0L, -520 to 2_414L,
            -1072 to 6_515L, 1038 to 6_663L, 34 to 6_730L,
            -1021 to 10_984L, 1003 to 11_138L, 18 to 11_200L,
            -739 to 17_452L, 739 to 17_520L,
            -820 to 21_628L, 808 to 21_763L, 12 to 21_852L,
            -631 to 26_014L, 631 to 26_142L,
            -543 to 29_877L, 543 to 29_999L,
            -601 to 35_999L, 601 to 36_120L,
            -690 to 41_289L,
            -556 to 45_505L, 556 to 45_555L,
            -601 to 48_198L, 601 to 48_289L,
            -1928 to 50_084L, 1928 to 50_198L,
            // Flicking as fast as possible
            -543 to 62_145L, 543 to 62_237L,
            -647 to 62_483L, 647 to 62_595L,
            -660 to 63_013L, 660 to 63_042L,
        )
    }
}
