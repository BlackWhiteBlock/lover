package com.lover.app.core.util

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

object DateUtils {
    fun lovingDays(togetherDate: LocalDate, today: LocalDate = LocalDate.now()): Long =
        if (togetherDate.isAfter(today)) 0 else ChronoUnit.DAYS.between(togetherDate, today) + 1

    fun yearlyCountdown(date: LocalDate, today: LocalDate = LocalDate.now()): Long {
        var next = MonthDay.from(date).atYear(today.year)
        if (next.isBefore(today)) next = MonthDay.from(date).atYear(today.year + 1)
        return ChronoUnit.DAYS.between(today, next)
    }

    fun milestoneCountdown(date: LocalDate, today: LocalDate = LocalDate.now()): Long? =
        if (date.isBefore(today)) null else ChronoUnit.DAYS.between(today, date)

}
