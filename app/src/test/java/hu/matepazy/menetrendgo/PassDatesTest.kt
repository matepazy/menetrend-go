package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class PassDatesTest {
    private val dates = PassDates(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 1))

    @Test fun changingStartPreservesThirtyInclusiveDaysAcrossMonthsAndLeapYears() {
        listOf("2026-01-31", "2028-02-01", "2026-12-20", "2026-03-15").forEach {
            val result = dates.changeStart(LocalDate.parse(it), true)
            assertEquals(30L, ChronoUnit.DAYS.between(result.starts, result.expires) + 1)
            assertEquals(LocalDate.parse(it), result.starts)
        }
    }

    @Test fun changingEndMovesStartBackExactlyTwentyNineDays() {
        val result = dates.changeEnd(LocalDate.of(2028, 3, 1), true)
        assertEquals(LocalDate.of(2028, 2, 1), result.starts)
        assertEquals(LocalDate.of(2028, 3, 1), result.expires)
    }

    @Test fun customDatesRemainIndependent() {
        assertEquals(dates.expires, dates.changeStart(LocalDate.of(2026, 2, 5), false).expires)
        assertEquals(dates.starts, dates.changeEnd(LocalDate.of(2026, 4, 5), false).starts)
    }
}
