package kz.maestrosultan.fitjournal.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kz.maestrosultan.fitjournal.data.db.FitJournalDatabase
import kz.maestrosultan.fitjournal.data.exercise.entity.DBCategoryObject
import kz.maestrosultan.fitjournal.data.exercise.entity.DBExerciseObject
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.ResultType

/**
 * Fresh in-memory SQLite for one test. FK constraints ON so the
 * workoutExercises/workoutSets ON DELETE CASCADE rules fire — without this
 * PRAGMA, SQLite silently ignores them. Each call is an isolated database.
 */
internal fun newTestDb(): FitJournalDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    FitJournalDatabase.Schema.create(driver)
    driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    return FitJournalDatabase(driver)
}

/**
 * DB-row → domain mapper for exercises. Production picks a localized name via
 * platform Locale; tests just use the English name. Categories are already
 * resolved on [DBExerciseObject] by the datasource's [ExerciseDBMapper].
 */
internal val testExerciseMapper: (DBExerciseObject) -> Exercise = { row ->
    fun toCategory(c: DBCategoryObject) =
        Category(c.uuid, c.remoteId, c.nameEn, CategoryType.create(c.type), c.details)
    Exercise(
        uuid = row.uuid,
        remoteId = row.remoteId,
        name = row.nameEn,
        details = row.details,
        primaryCategory = toCategory(row.primaryCategory),
        secondaryCategories = row.secondaryCategories?.map(::toCategory) ?: emptyList(),
        image1 = row.image1,
        image2 = row.image2,
        resultType = ResultType.create(row.resultType),
        isPersonal = !row.isGlobal,
    )
}
