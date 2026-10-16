package kz.maestrosultan.fitjournal.analytics

import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn

enum class DayKind(val value: String) { TODAY("today"), PAST("past"), FUTURE("future") }

/** Where a workout's calendar day sits relative to today; [daysFromToday] is negative for the past. */
data class DayContext(val dayKind: DayKind, val daysFromToday: Int)

/** The device-local calendar day. */
fun systemToday(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** Pure: `daysFromToday = today.daysUntil(date)`. */
fun dayContextOf(date: LocalDate, today: LocalDate): DayContext {
    val days = today.daysUntil(date)
    val kind = when {
        days < 0 -> DayKind.PAST
        days > 0 -> DayKind.FUTURE
        else -> DayKind.TODAY
    }
    return DayContext(kind, days)
}

/** Swift: `DayContextKt.dayContextOf(date:)`. */
fun dayContextOf(date: LocalDate): DayContext = dayContextOf(date, systemToday())
