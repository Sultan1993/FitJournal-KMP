package kz.maestrosultan.fitjournal.domain.user

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The current signed-in identity — the ONE place shared (CMP) code reads
 * "who is this / which journal / which units", replacing the per-screen
 * [WorkoutUserContext] seam that each platform had to re-implement.
 *
 * Populated by the native layer at its existing identity choke points (iOS
 * `UserStorage` setters, Android `DefaultUserManager`) plus a bootstrap read on
 * cold launch, and [clear]ed on logout. Reading identity while logged out is a
 * programming error here — exactly how the platform stores already treat it
 * (Android throws "Fetching user info while logged out is forbidden") — so
 * [require] throws rather than inventing a logged-out sentinel.
 *
 * It is a [StateFlow] on purpose: Swift reads [current]/[state].value
 * synchronously (no conforming Swift to a *suspend* KMP interface — the reason
 * the iOS `createWorkoutViewModel` factory workaround existed), and future
 * screens can observe live journal/unit switches. Today's Workout VM still
 * snapshots identity once at construction, so behavior is unchanged.
 *
 * A global `object` (no DI) mirrors the app's existing single-user identity
 * singletons; ViewModels stay testable because they receive a plain
 * [UserSessionState] value, never this object.
 */
object UserSession {
    private val _state = MutableStateFlow<UserSessionState?>(null)
    val state: StateFlow<UserSessionState?> = _state.asStateFlow()

    /** Synchronous snapshot; null before sign-in / bootstrap. */
    val current: UserSessionState? get() = _state.value

    fun set(state: UserSessionState) {
        _state.value = state
    }

    fun clear() {
        _state.value = null
    }

    /** For call sites reachable only when logged in (every CMP screen). */
    fun require(): UserSessionState = checkNotNull(_state.value) {
        "UserSession read before sign-in / bootstrap"
    }
}

/**
 * Immutable identity snapshot: the canonical user id, the currently-selected
 * journal, and the two unit preferences — all cheaply resolved at each platform's
 * identity choke point, so adding a screen doesn't force a new native resolution
 * path. (`firebaseUserId` is excluded — auth-only, no screen reads it; per-journal
 * `workoutGoal` lives on [Journal][kz.maestrosultan.fitjournal.domain.journal.Journal],
 * not on session identity. Add either here when a screen actually needs it.)
 */
data class UserSessionState(
    val userId: String,
    val journalId: String,
    val measurementSystem: MeasurementSystem,
    val lengthMeasurementSystem: LengthMeasurementSystem,
)
