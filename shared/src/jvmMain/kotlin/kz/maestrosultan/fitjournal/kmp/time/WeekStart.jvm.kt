package kz.maestrosultan.fitjournal.kmp.time

import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.datetime.DayOfWeek

actual fun firstDayOfWeekFromLocale(): DayOfWeek =
    DayOfWeek.entries[WeekFields.of(Locale.getDefault()).firstDayOfWeek.value - 1]
