package kz.maestrosultan.fitjournal.ui.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.identifier.randomUuid
import kz.maestrosultan.fitjournal.domain.journal.Journal
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/**
 * `buildHistoryFeed` — the pure aggregation the History screen renders. Clock is
 * fixed at Wednesday 2026-08-05; `firstDayOfWeek` is passed explicitly so the
 * week-bucket math is deterministic and not read from the host locale.
 *
 * Under MONDAY the current week starts 2026-08-03 and the 11 hero slots run
 * 2026-05-25 (oldest) -> 2026-08-03 (current).
 */
class HistoryFeedTest {

    // ── fixtures ─────────────────────────────────────────────────────────
    private val MON = DayOfWeek.MONDAY
    private val SUN = DayOfWeek.SUNDAY

    private fun journal(id: String, name: String) = Journal(id = id, name = name, comments = null, isPersonal = true)

    private fun catalogExercise(category: CategoryType) = Exercise(
        uuid = randomUuid(),
        remoteId = null,
        name = "Ex ${category.name}",
        details = null,
        primaryCategory = Category("c-${category.id}", "c-${category.id}", category.name, category, null),
        secondaryCategories = emptyList(),
        image1 = null,
        image2 = null,
        resultType = ResultType.WEIGHT_REPS,
        isPersonal = false,
    )

    private fun set(date: LocalDate, weight: Double? = 60.0, reps: Int? = 8) = WorkoutSet(
        id = randomUuid(),
        userId = USER,
        journalId = "j1",
        date = date,
        weight = weight,
        reps = reps,
        distance = null,
        duration = null,
        resultType = ResultType.WEIGHT_REPS,
    )

    private fun cardioSet(date: LocalDate, distance: Double? = 5.0, duration: Int? = 1800) = WorkoutSet(
        id = randomUuid(),
        userId = USER,
        journalId = "j1",
        date = date,
        weight = null,
        reps = null,
        distance = distance,
        duration = duration,
        resultType = ResultType.DISTANCE_DURATION,
    )

    private fun exercise(date: LocalDate, category: CategoryType, sets: List<WorkoutSet>) = WorkoutExercise(
        id = randomUuid(),
        userId = USER,
        journalId = "j1",
        date = date,
        exercise = catalogExercise(category),
        sets = sets,
        comment = null,
        lastOccurrence = null,
    )

    private fun record(date: LocalDate, workoutNumber: Int = 1, exercises: List<WorkoutExercise> = emptyList()) =
        WorkoutRecord(
            id = randomUuid(),
            userId = USER,
            journalId = "j1",
            position = 0,
            workoutNumber = workoutNumber,
            date = date,
            exercises = exercises,
            createdDate = FIXED_INSTANT,
            updatedDate = FIXED_INSTANT,
        )

    /** A single weighted set of `weight × reps` under `category` on `date`. */
    private fun rec(date: LocalDate, category: CategoryType, weight: Double, reps: Int, workoutNumber: Int = 1) =
        record(date, workoutNumber, listOf(exercise(date, category, listOf(set(date, weight, reps)))))

    private fun feed(
        records: List<WorkoutRecord>,
        journals: List<Journal> = listOf(journal("j1", "Main")),
        selectedJournalId: String = "j1",
        firstDayOfWeek: DayOfWeek = MON,
    ) = buildHistoryFeed(records, journals, selectedJournalId, TODAY, firstDayOfWeek)

    private fun loaded(
        records: List<WorkoutRecord>,
        journals: List<Journal> = listOf(journal("j1", "Main")),
        selectedJournalId: String = "j1",
        firstDayOfWeek: DayOfWeek = MON,
    ) = feed(records, journals, selectedJournalId, firstDayOfWeek) as HistoryContract.Content.Loaded

    // ── empty state ──────────────────────────────────────────────────────
    @Test
    fun emptyWithOneJournal_hasNoJournalRow() {
        val content = feed(emptyList())
        assertEquals(HistoryContract.Content.Empty(null), content)
    }

    @Test
    fun emptyWithTwoJournals_carriesSelectedJournalRow_fallingBackToFirst() {
        val journals = listOf(journal("j1", "Main"), journal("j2", "Cut"))
        val selected = feed(emptyList(), journals = journals, selectedJournalId = "j2") as HistoryContract.Content.Empty
        assertEquals("Cut", selected.journalRow?.name)
        val fallback = feed(emptyList(), journals = journals, selectedJournalId = "does-not-exist") as HistoryContract.Content.Empty
        assertEquals("Main", fallback.journalRow?.name, "unmatched id falls back to the first journal")
    }

    // ── hero slots ───────────────────────────────────────────────────────
    @Test
    fun heroHasExactlyElevenSlots_currentWeekLast_olderZeroFilled() {
        val hero = loaded(listOf(rec(TODAY, CategoryType.CHEST, 60.0, 8))).hero
        assertEquals(11, hero.slots.size)
        assertTrue(hero.slots.last().isCurrentWeek, "current week is the rightmost slot")
        assertEquals(1, hero.slots.count { it.isCurrentWeek }, "only the last slot is current")
        assertEquals(0.0, hero.slots.first().tonnage, "older empty weeks are zero-filled from the right")
    }

    // ── delta ────────────────────────────────────────────────────────────
    @Test
    fun deltaNull_forEarliestDataWeek_andHeroWhenThisWeekIsFirst() {
        val loaded = loaded(listOf(rec(TODAY, CategoryType.CHEST, 60.0, 8)))
        assertNull(loaded.hero.delta, "no earlier week has data")
        val thisWeek = loaded.weeks.single { it.kind == HistoryContract.WeekKind.ThisWeek }
        assertNull(thisWeek.delta)
    }

    @Test
    fun delta_isTonnageMinusPreviousCalendarWeek() {
        // current week 480 (60×8), last week 400 (50×8)
        val loaded = loaded(
            listOf(
                rec(TODAY, CategoryType.CHEST, 60.0, 8),
                rec(LocalDate(2026, 7, 28), CategoryType.CHEST, 50.0, 8),
            ),
        )
        assertEquals(80.0, loaded.hero.delta)
        assertEquals(80.0, loaded.weeks.single { it.kind == HistoryContract.WeekKind.ThisWeek }.delta)
        assertNull(
            loaded.weeks.single { it.kind == HistoryContract.WeekKind.LastWeek }.delta,
            "the earliest data week has no earlier week to compare",
        )
    }

    @Test
    fun restWeek_makesNextWeekDeltaItsFullTonnage() {
        // two weeks ago 400 (50×8), last week empty, current week 480 (60×8)
        val loaded = loaded(
            listOf(
                rec(LocalDate(2026, 7, 21), CategoryType.CHEST, 50.0, 8),
                rec(TODAY, CategoryType.CHEST, 60.0, 8),
            ),
        )
        assertEquals(480.0, loaded.hero.delta, "an empty previous calendar week contributes 0")
    }

    @Test
    fun equalWeeks_giveZeroDelta_notNull() {
        val loaded = loaded(
            listOf(
                rec(LocalDate(2026, 7, 28), CategoryType.CHEST, 60.0, 8),
                rec(TODAY, CategoryType.CHEST, 60.0, 8),
            ),
        )
        assertEquals(0.0, loaded.hero.delta)
    }

    // ── all data older than the 11-week window ───────────────────────────
    @Test
    fun allRecordsOlderThanElevenWeeks_loadsWithZeroSlots_andOldSections() {
        // 2026-04-27 is the Monday of a week before the oldest hero slot (2026-05-25).
        val oldWeekStart = LocalDate(2026, 4, 27)
        val loaded = loaded(listOf(rec(LocalDate(2026, 4, 29), CategoryType.CHEST, 60.0, 8)))
        assertTrue(loaded.hero.slots.all { it.tonnage == 0.0 }, "no division, no exception, all slots zero")
        assertEquals(0.0, loaded.hero.currentWeekTonnage)
        assertNotNull(
            loaded.weeks.singleOrNull { it.start == oldWeekStart },
            "the old week still gets a section",
        )
    }

    // ── month labels ─────────────────────────────────────────────────────
    @Test
    fun monthLabels_sumToEleven_andCollapseConsecutiveSameMonth() {
        val hero = loaded(listOf(rec(TODAY, CategoryType.CHEST, 60.0, 8))).hero
        assertEquals(11, hero.monthLabels.sumOf { it.slotCount })
        assertEquals(
            listOf(
                HistoryContract.MonthLabel(5, 1),
                HistoryContract.MonthLabel(6, 5),
                HistoryContract.MonthLabel(7, 4),
                HistoryContract.MonthLabel(8, 1),
            ),
            hero.monthLabels,
        )
    }

    // ── day rows ─────────────────────────────────────────────────────────
    @Test
    fun twoWorkoutsOneDay_countsWorkoutsExercisesAndFilledSetsOnly() {
        val filledPlusUnfilled = exercise(
            TODAY,
            CategoryType.CHEST,
            listOf(set(TODAY, 60.0, 8), set(TODAY, weight = null, reps = 8)), // 2nd set is unfilled
        )
        val second = exercise(TODAY, CategoryType.BACK, listOf(set(TODAY, 50.0, 8)))
        val loaded = loaded(
            listOf(
                record(TODAY, workoutNumber = 1, exercises = listOf(filledPlusUnfilled)),
                record(TODAY, workoutNumber = 2, exercises = listOf(second)),
            ),
        )
        val day = loaded.weeks.first().days.single { it.date == TODAY }
        assertEquals(2, day.workoutCount, "two distinct workoutNumbers")
        assertEquals(2, day.exerciseCount)
        assertEquals(2, day.setCount, "only the two filled sets count; the null-weight set does not")
        assertEquals(2, loaded.hero.workoutCount, "hero workoutCount is distinct (date, workoutNumber) this week")
    }

    @Test
    fun cardioOnlyDay_hasZeroTonnage() {
        val cardio = exercise(TODAY, CategoryType.CARDIO, listOf(cardioSet(TODAY)))
        val loaded = loaded(listOf(record(TODAY, exercises = listOf(cardio))))
        assertEquals(0.0, loaded.hero.currentWeekTonnage)
        val thisWeek = loaded.weeks.single { it.kind == HistoryContract.WeekKind.ThisWeek }
        assertEquals(0.0, thisWeek.tonnage)
        assertEquals(0.0, thisWeek.days.single().tonnage)
    }

    // ── week bucketing depends on firstDayOfWeek ─────────────────────────
    @Test
    fun sundayWorkout_bucketsIntoDifferentWeek_forMondayVsSundayStart() {
        val sunday = LocalDate(2026, 8, 2)
        val underMonday = loaded(listOf(rec(sunday, CategoryType.CHEST, 60.0, 8)), firstDayOfWeek = MON)
        // Monday-start: Sunday 08-02 belongs to the week starting 07-27 (last week).
        assertEquals(LocalDate(2026, 7, 27), underMonday.weeks.first().start)
        assertEquals(HistoryContract.WeekKind.LastWeek, underMonday.weeks.first().kind)

        val underSunday = loaded(listOf(rec(sunday, CategoryType.CHEST, 60.0, 8)), firstDayOfWeek = SUN)
        // Sunday-start: 08-02 IS a week start, and it is the current week.
        assertEquals(LocalDate(2026, 8, 2), underSunday.weeks.first().start)
        assertEquals(HistoryContract.WeekKind.ThisWeek, underSunday.weeks.first().kind)
    }

    // ── journal row on the Loaded case ───────────────────────────────────
    @Test
    fun loadedJournalRow_nullForOneJournal_selectedNameForMany() {
        val one = loaded(listOf(rec(TODAY, CategoryType.CHEST, 60.0, 8)))
        assertNull(one.journalRow)

        val journals = listOf(journal("j1", "Main"), journal("j2", "Cut"))
        val many = loaded(listOf(rec(TODAY, CategoryType.CHEST, 60.0, 8)), journals = journals, selectedJournalId = "j2")
        assertEquals("Cut", many.journalRow?.name)
    }

    // ── week kinds, ordering, topCategories, daysLeft ─────────────────────
    @Test
    fun weekKinds_orderingNewestFirst_daysNewestFirst_daysLeft() {
        val loaded = loaded(
            listOf(
                rec(TODAY, CategoryType.CHEST, 60.0, 8),                    // this week (08-03..)
                rec(LocalDate(2026, 8, 4), CategoryType.BACK, 60.0, 8),     // this week, later day
                rec(LocalDate(2026, 7, 28), CategoryType.CHEST, 60.0, 8),   // last week (07-27..)
                rec(LocalDate(2026, 7, 13), CategoryType.CHEST, 60.0, 8),   // older
            ),
        )
        assertEquals(
            listOf(HistoryContract.WeekKind.ThisWeek, HistoryContract.WeekKind.LastWeek, HistoryContract.WeekKind.Older),
            loaded.weeks.map { it.kind },
        )
        assertEquals(loaded.weeks.map { it.start }.sortedDescending(), loaded.weeks.map { it.start }, "weeks newest first")

        val thisWeekDays = loaded.weeks.first().days
        assertEquals(listOf(LocalDate(2026, 8, 5), LocalDate(2026, 8, 4)), thisWeekDays.map { it.date }, "days newest first")

        assertEquals(4, loaded.hero.daysLeft, "Wed under Monday-start: Thu Fri Sat Sun remain")
    }

    @Test
    fun topCategories_rankedBySetCountDesc_maxThree() {
        // One day, four categories with 4 / 3 / 2 / 1 filled sets respectively.
        val day = LocalDate(2026, 8, 4)
        fun ex(cat: CategoryType, n: Int) = exercise(day, cat, List(n) { set(day, 60.0, 8) })
        val loaded = loaded(
            listOf(
                record(
                    day,
                    exercises = listOf(
                        ex(CategoryType.BACK, 2),
                        ex(CategoryType.CHEST, 4),
                        ex(CategoryType.BICEPS, 1),
                        ex(CategoryType.SHOULDERS, 3),
                    ),
                ),
            ),
        )
        val top = loaded.weeks.first().days.single().topCategories
        assertEquals(listOf(CategoryType.CHEST, CategoryType.SHOULDERS, CategoryType.BACK), top)
        assertTrue(top.size <= 3)
    }

    // ── titleShowsYear ───────────────────────────────────────────────────
    @Test
    fun titleShowsYear_falseWithinTodaysYear_trueForAPreviousYear() {
        val loaded = loaded(
            listOf(
                rec(LocalDate(2026, 1, 15), CategoryType.CHEST, 60.0, 8), // this year -> false
                rec(LocalDate(2025, 12, 15), CategoryType.CHEST, 60.0, 8), // last year -> true
            ),
        )
        val thisYearWeek = loaded.weeks.single { it.endInclusive.year == 2026 && it.start.year == 2026 && it.kind == HistoryContract.WeekKind.Older }
        assertTrue(!thisYearWeek.titleShowsYear, "a week entirely inside 2026 hides the year")
        val lastYearWeek = loaded.weeks.single { it.start.year == 2025 }
        assertTrue(lastYearWeek.titleShowsYear, "a week ending in 2025 shows the year")
    }

    companion object {
        private val TODAY = LocalDate(2026, 8, 5)
        private val FIXED_INSTANT = Instant.parse("2026-08-05T10:00:00Z")
        private const val USER = "user-1"
    }
}
