package kz.maestrosultan.fitjournal.kmp.time

import kotlinx.datetime.DayOfWeek
import platform.Foundation.NSCalendar

actual fun firstDayOfWeekFromLocale(): DayOfWeek {
    // NSCalendar.firstWeekday: 1 = Sunday .. 7 = Saturday.
    val ns = NSCalendar.currentCalendar.firstWeekday.toInt()
    val iso = if (ns == 1) 7 else ns - 1 // -> ISO: MON=1 .. SUN=7
    return DayOfWeek.entries[iso - 1]
}
