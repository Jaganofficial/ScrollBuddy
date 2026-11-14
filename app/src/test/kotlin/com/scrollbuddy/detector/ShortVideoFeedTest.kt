package com.scrollbuddy.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The event shapes here are the ones recorded from Instagram and YouTube on a phone. */
class ShortVideoFeedTest {

    @Test
    fun `the Reels pager is a ViewPager that moves vertically and reports a position`() {
        assertTrue(ShortVideoFeed.InstagramReels.isPagerScroll(VIEW_PAGER, fromIndex = 2, deltaX = 0, deltaY = 1683))
        assertTrue(ShortVideoFeed.InstagramReels.isPagerScroll(VIEW_PAGER, fromIndex = 2, deltaX = 0, deltaY = -1909))
    }

    @Test
    fun `Instagram's sideways and motionless pagers are not Reels`() {
        // The profile tabs, then the main tab strip
        assertFalse(ShortVideoFeed.InstagramReels.isPagerScroll(VIEW_PAGER, fromIndex = 0, deltaX = -1060, deltaY = 0))
        assertFalse(ShortVideoFeed.InstagramReels.isPagerScroll(VIEW_PAGER, fromIndex = 0, deltaX = 0, deltaY = 0))
    }

    @Test
    fun `Instagram's feed, comments and grids are not Reels`() {
        assertFalse(ShortVideoFeed.InstagramReels.isPagerScroll(RECYCLER_VIEW, fromIndex = 4, deltaX = 0, deltaY = 900))
        assertFalse(ShortVideoFeed.InstagramReels.isPagerScroll("android.view.View", fromIndex = -1, deltaX = 0, deltaY = 40))
    }

    @Test
    fun `the Shorts pager is a FrameLayout that reports no position`() {
        assertTrue(ShortVideoFeed.YouTubeShorts.isPagerScroll(FRAME_LAYOUT, fromIndex = -1, deltaX = 0, deltaY = -1072))
    }

    @Test
    fun `YouTube's home feed, comments and watch page are not Shorts`() {
        assertFalse(ShortVideoFeed.YouTubeShorts.isPagerScroll(LEGACY_RECYCLER_VIEW, fromIndex = 3, deltaX = 0, deltaY = 800))
        assertFalse(ShortVideoFeed.YouTubeShorts.isPagerScroll(LEGACY_RECYCLER_VIEW, fromIndex = -1, deltaX = 0, deltaY = 7))
    }

    @Test
    fun `finds a feed by its app's package`() {
        assertEquals(ShortVideoFeed.YouTubeShorts, ShortVideoFeed.of("com.google.android.youtube"))
        assertNull(ShortVideoFeed.of("com.whatsapp"))
    }

    @Test
    fun `the accessibility service is limited to exactly the apps that have a feed`() {
        // Unit tests run with the module directory as the working directory
        val config = File("src/main/res/xml/scroll_watch_service_config.xml").readText()
        val listed = Regex("""android:packageNames="([^"]*)"""").find(config)?.groupValues?.get(1)

        assertEquals(ShortVideoFeed.entries.map { it.packageName }.toSet(), listed?.split(',')?.toSet())
    }

    private companion object {
        const val VIEW_PAGER = "androidx.viewpager.widget.ViewPager"
        const val FRAME_LAYOUT = "android.widget.FrameLayout"
        const val RECYCLER_VIEW = "androidx.recyclerview.widget.RecyclerView"
        const val LEGACY_RECYCLER_VIEW = "android.support.v7.widget.RecyclerView"
    }
}
