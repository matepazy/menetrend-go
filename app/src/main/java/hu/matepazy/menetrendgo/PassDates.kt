package hu.matepazy.menetrendgo

import java.time.LocalDate

/** Both displayed dates are inclusive: the start is day one of a 30-day pass. */
data class PassDates(val starts: LocalDate, val expires: LocalDate) {
    fun changeStart(day: LocalDate, thirtyDays: Boolean) =
        copy(starts = day, expires = if (thirtyDays) day.plusDays(29) else expires)

    fun changeEnd(day: LocalDate, thirtyDays: Boolean) =
        copy(starts = if (thirtyDays) day.minusDays(29) else starts, expires = day)
}
