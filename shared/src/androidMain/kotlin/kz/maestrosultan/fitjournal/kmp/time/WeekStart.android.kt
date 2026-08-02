package kz.maestrosultan.fitjournal.kmp.time

import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.datetime.DayOfWeek

// WeekFields.firstDayOfWeek is a java.time.DayOfWeek (value MON=1..SUN=7);
// map it onto kotlinx-datetime's DayOfWeek (entries MONDAY..SUNDAY).
actual fun firstDayOfWeekFromLocale(): DayOfWeek =
    DayOfWeek.entries[WeekFields.of(Locale.getDefault()).firstDayOfWeek.value - 1]
