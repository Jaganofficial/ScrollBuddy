package com.scrollbuddy.detector

/**
 * The short-video feeds that are watched, and how each one's full-screen pager shows up in
 * accessibility scroll events. Everything else in these apps (the home feed, comments, grids,
 * search) scrolls in list views of other classes, so it never matches.
 *
 * Measured on a phone in November 2026; a redesign of either app can change the class it uses.
 */
enum class ShortVideoFeed(val packageName: String, private val pagerClass: String) {
    /** A vertical ViewPager that reports the reel's position as the event's item index. */
    InstagramReels("com.instagram.android", "androidx.viewpager.widget.ViewPager"),

    /** A FrameLayout under the player that reports only distances, never an item index. */
    YouTubeShorts("com.google.android.youtube", "android.widget.FrameLayout");

    fun isPagerScroll(className: String, fromIndex: Int, deltaX: Int, deltaY: Int): Boolean {
        if (className != pagerClass || deltaX != 0) return false
        return when (this) {
            // Instagram's other pagers, the profile tabs and the main tab strip, move sideways
            // or report no movement at all
            InstagramReels -> fromIndex >= 0 && deltaY != 0
            YouTubeShorts -> fromIndex < 0
        }
    }

    companion object {
        fun of(packageName: String): ShortVideoFeed? = entries.firstOrNull { it.packageName == packageName }
    }
}
