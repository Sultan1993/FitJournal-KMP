package kz.maestrosultan.fitjournal.kmp.time

import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.datetime.DayOfWeek

// Maps java.time.DayOfWeek (MON=1..SUN=7) onto kotlinx-datetime's DayOfWeek (MONDAY..SUNDAY).
actual fun firstDayOfWeekFromLocale(): DayOfWeek =
    DayOfWeek.entries[WeekFields.of(Locale.getDefault()).firstDayOfWeek.value - 1]
