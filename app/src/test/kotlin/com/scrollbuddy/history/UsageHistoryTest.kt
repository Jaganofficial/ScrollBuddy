package com.scrollbuddy.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UsageHistoryTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val entries = listOf(
        DayUsage(today, INSTAGRAM, swipes = 30, watchedMs = 600_000),
        DayUsage(today, YOUTUBE, swipes = 10, watchedMs = 120_000),
        DayUsage(today.minusDays(1), INSTAGRAM, swipes = 5, watchedMs = 60_000),
        DayUsage(today.minusDays(6), YOUTUBE, swipes = 7, watchedMs = 70_000),
        DayUsage(today.minusDays(7), YOUTUBE, swipes = 100, watchedMs = 1_000_000),
        DayUsage(LocalDate.of(2026, 3, 2), INSTAGRAM, swipes = 40, watchedMs = 400_000),
        DayUsage(LocalDate.of(2025, 11, 30), INSTAGRAM, swipes = 1_000, watchedMs = 9_000_000),
    )

    @Test
    fun `today covers only today, split by app`() {
        val summary = summarize(entries, HistoryRange.Today, today)

        assertEquals(UsageTotals(swipes = 40, watchedMs = 720_000), summary.total)
        assertEquals(UsageTotals(30, 600_000), summary.byPackage[INSTAGRAM])
        assertEquals(UsageTotals(10, 120_000), summary.byPackage[YOUTUBE])
        assertEquals(listOf(today), summary.buckets.map { it.start })
    }

    @Test
    fun `the week is the last seven days, one bucket a day with the empty days kept`() {
        val summary = summarize(entries, HistoryRange.Week, today)

        assertEquals(UsageTotals(swipes = 52, watchedMs = 850_000), summary.total)
        assertEquals(7, summary.buckets.size)
        assertEquals(today.minusDays(6), summary.buckets.first().start)
        assertEquals(listOf(7, 0, 0, 0, 0, 5, 40), summary.buckets.map { it.totals.swipes })
    }

    @Test
    fun `the month is the last thirty days`() {
        val summary = summarize(entries, HistoryRange.Month, today)

        assertEquals(152, summary.total.swipes)
        assertEquals(30, summary.buckets.size)
    }

    @Test
    fun `the year is this month and the eleven before it, one bucket a month`() {
        val summary = summarize(entries, HistoryRange.Year, today)

        // Leaves out 30 November 2025: the year starts on 1 December 2025
        assertEquals(192, summary.total.swipes)
        assertEquals(12, summary.buckets.size)
        assertEquals(LocalDate.of(2025, 12, 1), summary.buckets.first().start)
        assertEquals(40, summary.buckets.single { it.start == LocalDate.of(2026, 3, 1) }.totals.swipes)
        assertEquals(152, summary.buckets.last().totals.swipes)
    }

    @Test
    fun `all time starts at the first month with anything in it`() {
        val summary = summarize(entries, HistoryRange.AllTime, today)

        assertEquals(1_192, summary.total.swipes)
        assertEquals(LocalDate.of(2025, 11, 1), summary.buckets.first().start)
        assertEquals(13, summary.buckets.size)
    }

    @Test
    fun `an empty history still has a bucket for today`() {
        val summary = summarize(emptyList(), HistoryRange.AllTime, today)

        assertEquals(UsageTotals(), summary.total)
        assertEquals(listOf(HistoryBucket(today.withDayOfMonth(1), UsageTotals())), summary.buckets)
    }

    @Test
    fun `a day in the future, from a clock set wrong, is left out`() {
        val future = DayUsage(today.plusDays(2), INSTAGRAM, swipes = 9, watchedMs = 9)

        assertEquals(0, summarize(listOf(future), HistoryRange.AllTime, today).total.swipes)
    }

    @Test
    fun `chosen dates cover both ends and nothing outside them`() {
        val summary = summarize(entries, today.minusDays(7), today.minusDays(1), monthly = false)

        assertEquals(UsageTotals(swipes = 112, watchedMs = 1_130_000), summary.total)
        assertEquals(7, summary.buckets.size)
        assertEquals(today.minusDays(7), summary.buckets.first().start)
        assertEquals(today.minusDays(1), summary.buckets.last().start)
    }

    @Test
    fun `chosen dates far apart are shown month by month`() {
        val first = LocalDate.of(2026, 3, 1)

        assertEquals(true, isMonthlySpan(first, today))
        assertEquals(false, isMonthlySpan(today.minusDays(30), today))
        val summary = summarize(entries, first, today, monthly = true)
        assertEquals(9, summary.buckets.size)
        assertEquals(UsageTotals(swipes = 192, watchedMs = 2_250_000), summary.total)
    }

    @Test
    fun `a stored entry reads back as it was written`() {
        val key = usageKey(today, INSTAGRAM)
        val value = encodeUsage(UsageTotals(swipes = 42, watchedMs = 613_000))

        assertEquals(DayUsage(today, INSTAGRAM, 42, 613_000), decodeUsage(key, value))
    }

    @Test
    fun `a damaged entry is skipped`() {
        assertNull(decodeUsage("not-a-date|$INSTAGRAM", "1:2"))
        assertNull(decodeUsage("2026-11-13", "1:2"))
        assertNull(decodeUsage("2026-11-13|$INSTAGRAM", "many:2"))
        assertNull(decodeUsage("2026-11-13|$INSTAGRAM", "12"))
        assertNull(decodeUsage("2026-11-13|$INSTAGRAM", null))
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"
    }
}
