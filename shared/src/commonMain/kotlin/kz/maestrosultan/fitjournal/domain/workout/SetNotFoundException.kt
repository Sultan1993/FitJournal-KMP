package kz.maestrosultan.fitjournal.domain.workout

/**
 * The set was gone by the time the write ran (e.g. a concurrent sync pull
 * deleted it) — no write happened.
 *
 * Carries no payload and exists only so a caller can tell "the row vanished"
 * from any other write failure: on this error the UI must drop edit mode and
 * recover to DB truth BEFORE alerting, because staying in an editor over a
 * ghost set is the bug. Every other failure leaves edit mode intact for retry.
 * Both platforms branched on this before the merge (Android
 * `WorkoutError.SetNotFound`, iOS `.SetNotFound`); it is deliberately NOT a
 * sealed hierarchy — add a sibling type when a second case actually earns one.
 */
class SetNotFoundException : IllegalStateException("Set was gone by write time")
