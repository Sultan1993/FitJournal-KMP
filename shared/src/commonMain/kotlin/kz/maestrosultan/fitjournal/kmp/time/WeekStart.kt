package kz.maestrosultan.fitjournal.kmp.time

import kotlinx.datetime.DayOfWeek

/**
 * The device locale's first day of the week (US -> SUNDAY, most of EU/RU ->
 * MONDAY). A platform/locale read, so it lives in this neutral kmp package
 * rather than the UI layer — domain week math (the post-workout "Nth workout
 * this week" ordinal) and any UI can both read it, and it matches the week
 * start the calendar already uses via Kizitonwose's `daysOfWeek()`.
 */
expect fun firstDayOfWeekFromLocale(): DayOfWeek
