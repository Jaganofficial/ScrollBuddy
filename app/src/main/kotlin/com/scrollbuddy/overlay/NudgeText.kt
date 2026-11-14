package com.scrollbuddy.overlay

import android.content.res.Resources
import com.scrollbuddy.R
import com.scrollbuddy.detector.DetectorConfig
import com.scrollbuddy.detector.LimitMode
import com.scrollbuddy.detector.Nudge
import com.scrollbuddy.detector.ShortVideoFeed
import kotlin.random.Random

private const val MS_PER_MINUTE = 60_000L

// The bubble warns once this few swipes, or this little time, is left before the nudge
private const val WARN_SWIPES_LEFT = 3
private const val WARN_TIME_LEFT_MS = MS_PER_MINUTE

private fun isShorts(packageName: String) = ShortVideoFeed.of(packageName) == ShortVideoFeed.YouTubeShorts

/** One of the character's lines for [mode], picked at random, addressing the person as [address]. */
internal fun nudgeLine(
    resources: Resources,
    address: String,
    mode: LimitMode,
    random: Random = Random.Default,
): String {
    val lines = resources.getStringArray(
        if (mode == LimitMode.Zero) R.array.nudge_lines_zero else R.array.nudge_lines,
    )
    return lines[random.nextInt(lines.size)].format(address)
}

/** How many videos have gone by in the session, which is since the app was opened. */
internal fun nudgeStats(resources: Resources, nudge: Nudge): String =
    resources.getQuantityString(R.plurals.nudge_stats, nudge.swipes, nudge.swipes)

/**
 * The words on the button that asks for a little more, which name what it buys, or null when
 * [nudge] came with no reels allowed and there is nothing more to ask for.
 */
internal fun nudgeMoreLabel(resources: Resources, nudge: Nudge): String? = when (nudge.mode) {
    LimitMode.Zero -> null
    LimitMode.Time -> resources.getString(
        R.string.nudge_more_minutes,
        (DetectorConfig.DEFAULT_SNOOZE_MS / MS_PER_MINUTE).toInt(),
    )
    LimitMode.Swipes -> resources.getString(
        if (isShorts(nudge.packageName)) R.string.nudge_more_shorts else R.string.nudge_more_reels,
        DetectorConfig.DEFAULT_SNOOZE_SWIPES,
    )
}

/** What the small bubble over Reels and Shorts says; [warning] when the nudge is close. */
internal data class CounterText(val text: String, val warning: Boolean)

/**
 * [today] is the day's swipes in [packageName] so far. [swipesLeft] and [timeLeftMs] are what is
 * left before the nudge, each null when it is not what is limited.
 */
internal fun counterText(
    resources: Resources,
    packageName: String,
    today: Int,
    swipesLeft: Int?,
    timeLeftMs: Long?,
): CounterText = when {
    swipesLeft != null && swipesLeft <= WARN_SWIPES_LEFT ->
        CounterText(resources.getString(R.string.counter_warning_swipes, swipesLeft), warning = true)
    timeLeftMs != null && timeLeftMs <= WARN_TIME_LEFT_MS ->
        CounterText(resources.getString(R.string.counter_warning_time), warning = true)
    else -> CounterText(
        resources.getQuantityString(
            if (isShorts(packageName)) R.plurals.counter_shorts else R.plurals.counter_reels,
            today,
            today,
        ),
        warning = false,
    )
}
