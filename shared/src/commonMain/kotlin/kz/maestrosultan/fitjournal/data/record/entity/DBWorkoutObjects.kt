package kz.maestrosultan.fitjournal.data.record.entity

import kotlin.time.Instant
import kz.maestrosultan.fitjournal.data.db.WorkoutExercises
import kz.maestrosultan.fitjournal.data.db.WorkoutRecords
import kz.maestrosultan.fitjournal.data.db.WorkoutSets
import kz.maestrosultan.fitjournal.data.time.parseStoredInstant

/**
 * The most recent prior occurrence of one catalog exercise, at the data layer:
 * its record date (`yyyy-MM-dd`, as stored) plus its position-ordered sets.
 * Maps to the domain `LastOccurrence`.
 */
data class DBLastOccurrence(
    val recordDate: String,
    val sets: List<DBWorkoutSetObject>,
)

/**
 * The SQL row of `workoutRecords`. Internal: just the parent row, no
 * children. Most callers want [DBWorkoutRecord] (the full domain
 * record). Use this only on internal hydration paths and for sync push
 * (which iterates pending uploads then expands per-uuid).
 *
 * `date` stays a `String` here because it's stored as a calendar day
 * (`yyyy-MM-dd`) — no zone, lex-sortable, and SQL filters compare it
 * lexicographically. Surfacing it as `Instant` would require choosing
 * a timezone, which is the wrong abstraction for "the day the user
 * worked out".
 */
data class DBWorkoutRecordRow(
    val uuid: String,
    val remoteId: String?,
    val userId: String,
    val journalId: String,
    val date: String,
    val position: Int,
    val comment: String?,
    val startedAt: Instant?,
    val durationSec: Int?,
    val deletedAt: Instant?,
    val pendingUpload: Boolean,
    val schemaVersion: Int,
    val createdDate: Instant,
    val updatedDate: Instant,
)

data class DBWorkoutExerciseObject(
    val uuid: String,
    val workoutRecordUuid: String,
    val exerciseUuid: String,
    val position: Int,
    val comment: String?,
)

data class DBWorkoutSetObject(
    val uuid: String,
    val workoutExerciseUuid: String,
    val position: Int,
    val weight: Double?,
    val reps: Int?,
    val distance: Double?,
    val duration: Int?,
    val difficultyType: Int,
    val completed: Boolean,
)

/** A workoutExercise with its child sets, ordered by position. */
data class DBWorkoutExerciseWithSets(
    val exercise: DBWorkoutExerciseObject,
    val sets: List<DBWorkoutSetObject>,
)


/**
 * A workout record — domain shape: an entry in the user's logbook. Has
 * its parent SQL row plus the child exercises (each with their child
 * sets). Hydrated from 3 SQL tables but presented as one domain object,
 * because that's what a "record" means in this app.
 */
data class DBWorkoutRecord(
    val row: DBWorkoutRecordRow,
    val exercises: List<DBWorkoutExerciseWithSets>,
)

fun WorkoutRecords.map(): DBWorkoutRecordRow = DBWorkoutRecordRow(
    uuid = uuid,
    remoteId = remoteId,
    userId = userId,
    journalId = journalId,
    date = date,
    position = position.toInt(),
    comment = comment,
    startedAt = startedAt?.let(::parseStoredInstant),
    durationSec = durationSec?.toInt(),
    deletedAt = deletedAt?.let(::parseStoredInstant),
    pendingUpload = pendingUpload,
    schemaVersion = schemaVersion.toInt(),
    createdDate = parseStoredInstant(createdDate),
    updatedDate = parseStoredInstant(updatedDate),
)

fun WorkoutExercises.map(): DBWorkoutExerciseObject = DBWorkoutExerciseObject(
    uuid = uuid,
    workoutRecordUuid = workoutRecordUuid,
    exerciseUuid = exerciseUuid,
    position = position.toInt(),
    comment = comment,
)

fun WorkoutSets.map(): DBWorkoutSetObject = DBWorkoutSetObject(
    uuid = uuid,
    workoutExerciseUuid = workoutExerciseUuid,
    position = position.toInt(),
    weight = weight,
    reps = reps?.toInt(),
    distance = distance,
    duration = duration?.toInt(),
    difficultyType = difficultyType.toInt(),
    completed = completed,
)
