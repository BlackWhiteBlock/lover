package com.lover.app.core.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateUtilsTest {
    @Test
    fun `loving days includes first day`() {
        val start = LocalDate.of(2026, 7, 10)
        assertEquals(5, DateUtils.lovingDays(start, LocalDate.of(2026, 7, 14)))
        assertEquals(1, DateUtils.lovingDays(start, start))
    }

    @Test
    fun `future together date returns zero`() {
        assertEquals(
            0,
            DateUtils.lovingDays(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 14)),
        )
    }

    @Test
    fun `yearly countdown rolls to next year`() {
        val date = LocalDate.of(2020, 2, 14)
        assertEquals(1, DateUtils.yearlyCountdown(date, LocalDate.of(2026, 2, 13)))
        assertEquals(364, DateUtils.yearlyCountdown(date, LocalDate.of(2026, 2, 15)))
    }

    @Test
    fun `milestone reports reached state`() {
        assertNull(
            DateUtils.milestoneCountdown(
                LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 14),
            ),
        )
    }

}
