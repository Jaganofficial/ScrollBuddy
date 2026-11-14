package com.scrollbuddy.history

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** One app's swipes and watching time on one day. */
data class DayUsage(
    val date: LocalDate,
    val packageName: String,
    val swipes: Int,
    val watchedMs: Long,
)

data class UsageTotals(val swipes: Int = 0, val watchedMs: Long = 0L) {
    operator fun plus(other: UsageTotals) = UsageTotals(swipes + other.swipes, watchedMs + other.watchedMs)
}

enum class HistoryRange {
    Today, Week, Month, Year, AllTime;

    /** Longer ranges are shown month by month, shorter ones day by day. */
    val isMonthly: Boolean get() = this == Year || this == AllTime

    /** The first day in the range, or null when it has no beginning. */
    fun firstDay(today: LocalDate): LocalDate? = when (this) {
        Today -> today
        Week -> today.minusDays(DAYS_IN_WEEK - 1)
        Month -> today.minusDays(DAYS_IN_MONTH - 1)
        Year -> today.withDayOfMonth(1).minusMonths(MONTHS_IN_YEAR - 1)
        AllTime -> null
    }

    private companion object {
        const val DAYS_IN_WEEK = 7L
        const val DAYS_IN_MONTH = 30L
        const val MONTHS_IN_YEAR = 12L
    }
}

/** A day, or a month when [HistoryRange.isMonthly]; [start] is its first day. */
data class HistoryBucket(val start: LocalDate, val totals: UsageTotals)

/** [buckets] run from oldest to newest with no gaps, so they can be drawn as a chart. */
data class HistorySummary(
    val total: UsageTotals,
    val byPackage: Map<String, UsageTotals>,
    val buckets: List<HistoryBucket>,
)

fun summarize(entries: List<DayUsage>, range: HistoryRange, today: LocalDate): HistorySummary =
    summarize(entries, range.firstDay(today), today, range.isMonthly)

/** Whether dates this far apart are better shown month by month than day by day. */
fun isMonthlySpan(firstDay: LocalDate, lastDay: LocalDate): Boolean =
    ChronoUnit.DAYS.between(firstDay, lastDay) >= MAX_DAILY_BUCKETS

/**
 * Sums the days from [firstDay] to [lastDay], both included. With no [firstDay] it starts at the
 * first day that has anything. [monthly] puts the days into buckets of a month.
 */
fun summarize(entries: List<DayUsage>, firstDay: LocalDate?, lastDay: LocalDate, monthly: Boolean): HistorySummary {
    val inRange = entries.filter { !it.date.isAfter(lastDay) && (firstDay == null || !it.date.isBefore(firstDay)) }
    fun bucketOf(date: LocalDate) = if (monthly) date.withDayOfMonth(1) else date

    val byBucket = HashMap<LocalDate, UsageTotals>()
    val byPackage = LinkedHashMap<String, UsageTotals>()
    var total = UsageTotals()
    for (entry in inRange) {
        val totals = UsageTotals(entry.swipes, entry.watchedMs)
        total += totals
        byBucket.merge(bucketOf(entry.date), totals, UsageTotals::plus)
        byPackage.merge(entry.packageName, totals, UsageTotals::plus)
    }

    // An empty history with no first day shows just the last one
    val start = bucketOf(firstDay ?: inRange.minOfOrNull { it.date } ?: lastDay)
    val buckets = generateSequence(start) { if (monthly) it.plusMonths(1) else it.plusDays(1) }
        .takeWhile { !it.isAfter(lastDay) }
        .map { HistoryBucket(it, byBucket[it] ?: UsageTotals()) }
        .toList()
    return HistorySummary(total, byPackage, buckets)
}

// More days than this in a chart are too thin to read
private const val MAX_DAILY_BUCKETS = 62L

private const val KEY_SEPARATOR = '|'
private const val VALUE_SEPARATOR = ':'

internal fun usageKey(date: LocalDate, packageName: String) = "$date$KEY_SEPARATOR$packageName"

internal fun encodeUsage(totals: UsageTotals) = "${totals.swipes}$VALUE_SEPARATOR${totals.watchedMs}"

/** Returns null for anything that isn't a stored entry, so a damaged one is skipped, not fatal. */
internal fun decodeUsage(key: String, value: String?): DayUsage? {
    val date = try {
        LocalDate.parse(key.substringBefore(KEY_SEPARATOR))
    } catch (e: DateTimeParseException) {
        return null
    }
    val packageName = key.substringAfter(KEY_SEPARATOR, missingDelimiterValue = "")
    val swipes = value?.substringBefore(VALUE_SEPARATOR)?.toIntOrNull()
    val watchedMs = value?.substringAfter(VALUE_SEPARATOR, missingDelimiterValue = "")?.toLongOrNull()
    if (packageName.isEmpty() || swipes == null || watchedMs == null) return null
    return DayUsage(date, packageName, swipes, watchedMs)
}
