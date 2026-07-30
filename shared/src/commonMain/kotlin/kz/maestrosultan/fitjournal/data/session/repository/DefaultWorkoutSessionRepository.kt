package kz.maestrosultan.fitjournal.data.session.repository

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.session.datasource.WorkoutSessionsDBDataSource
import kz.maestrosultan.fitjournal.data.session.entity.toDomain
import kz.maestrosultan.fitjournal.domain.identifier.randomUuid
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository

/**
 * 100% local implementation (offline-first contract): SQLite is the source of
 * truth, there is no AWS model for sessions in iteration 1, and nothing here
 * touches the network.
 *
 * Timestamps come from the injected [clock] rather than `Clock.System` inline so
 * the jvmTest suite can step "now" deterministically instead of racing the wall
 * clock. Identifiers come from the shared [randomUuid] so a row created on
 * either platform is byte-identical in shape.
 *
 * No ordinary path throws: an unbridged Kotlin exception is an uncatchable
 * SIGABRT on iOS (it is merely a catchable crash on Android), so a double-start
 * returns the existing session and ending with nothing running returns `null`.
 */
class DefaultWorkoutSessionRepository(
    private val sessionsDB: WorkoutSessionsDBDataSource,
    private val clock: Clock,
) : WorkoutSessionRepository {

    /**
     * Swift-facing entry point. Kotlin default arguments are not a reliable
     * contract across the Objective-C bridge — Kotlin/Native does not emit
     * per-default-value initializers, so a `clock: Clock = Clock.System`
     * default would force every Swift call site to construct a Kotlin clock.
     * iOS therefore gets an explicit one-arg constructor that pins the system
     * clock here, in common code.
     */
    constructor(sessionsDB: WorkoutSessionsDBDataSource) : this(sessionsDB, Clock.System)

    override suspend fun getSession(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): WorkoutSession? = sessionsDB.getSession(userId, journalId, date.toString())?.toDomain()

    override fun getSessionFlow(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): Flow<WorkoutSession?> =
        sessionsDB.getSessionFlow(userId, journalId, date.toString())
            .map { row -> row?.toDomain() }

    override suspend fun getRunningSession(userId: String): WorkoutSession? =
        sessionsDB.getRunningSession(userId)?.toDomain()

    override fun getRunningSessionFlow(userId: String): Flow<WorkoutSession?> =
        sessionsDB.getRunningSessionFlow(userId)
            .map { row -> row?.toDomain() }

    override suspend fun startSession(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): WorkoutSession = sessionsDB.startSession(
        uuid = randomUuid(),
        userId = userId,
        journalId = journalId,
        date = date.toString(),
        now = clock.now(),
    ).toDomain()

    override suspend fun endSession(userId: String): WorkoutSession? =
        sessionsDB.endRunningSession(userId, clock.now())?.toDomain()

    override suspend fun deleteUserSessions(userId: String) = sessionsDB.deleteByUserId(userId)
}
