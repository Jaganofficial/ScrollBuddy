package com.scrollbuddy.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.scrollbuddy.R

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60

/** "under 1 min", "12 min" or "1 h 5 min"; with [compact], for a narrow tile, "0 min" or "<1 min". */
@Composable
fun formatDuration(durationMs: Long, compact: Boolean = false): String {
    val minutes = (durationMs / MS_PER_MINUTE).toInt()
    return when {
        compact && durationMs == 0L -> stringResource(R.string.duration_minutes, 0)
        compact && minutes == 0 -> stringResource(R.string.duration_under_a_minute_compact)
        minutes == 0 -> stringResource(R.string.duration_under_a_minute)
        minutes < MINUTES_PER_HOUR -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(
            R.string.duration_hours_minutes,
            minutes / MINUTES_PER_HOUR,
            minutes % MINUTES_PER_HOUR,
        )
    }
}
