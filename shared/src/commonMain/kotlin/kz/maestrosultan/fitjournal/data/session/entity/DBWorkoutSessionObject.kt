package kz.maestrosultan.fitjournal.data.session.entity

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.db.WorkoutSessions
import kz.maestrosultan.fitjournal.data.time.parseStoredInstant
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession

/**
 * Typed mirror of a `workoutSessions` row. `date` is a calendar day
 * (`LocalDate.toString()` in SQLite), `startedAt`/`endedAt` are UTC moments
 * stored as ISO-8601 TEXT. `endedAt == null` means the session is running.
 */
data class DBWorkoutSessionObject(
    val uuid: String,
    val userId: String,
    val journalId: String,
    val date: LocalDate,
    val workoutNumber: Int,
    val startedAt: Instant,
    val endedAt: Instant?,
)

fun WorkoutSessions.map(): DBWorkoutSessionObject = DBWorkoutSessionObject(
    uuid = uuid,
    userId = userId,
    journalId = journalId,
    date = LocalDate.parse(date),
    workoutNumber = workoutNumber.toInt(),
    startedAt = parseStoredInstant(startedAt),
    endedAt = endedAt?.let(::parseStoredInstant),
)

fun DBWorkoutSessionObject.toDomain(): WorkoutSession = WorkoutSession(
    id = uuid,
    userId = userId,
    journalId = journalId,
    date = date,
    workoutNumber = workoutNumber,
    startedAt = startedAt,
    endedAt = endedAt,
)
