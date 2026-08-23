package kz.maestrosultan.fitjournal.domain.quota

/**
 * Free-workout allowance state. Sealed because Unlimited and Metered are
 * mutually exclusive: no caller should be able to read `remaining` off a
 * subscriber. SKIE bridges the cases as WorkoutQuotaUnlimited /
 * WorkoutQuotaMetered.
 */
sealed interface WorkoutQuota {

    /** Entitled, metering off / not started, or config unresolved. No meter, no gate. */
    data object Unlimited : WorkoutQuota

    /** [used] counts WORKOUTS — (journalId, date, workoutNumber) slots — not calendar days. */
    data class Metered(val used: Int, val limit: Int) : WorkoutQuota {
        val remaining: Int get() = (limit - used).coerceAtLeast(0)
        val isExhausted: Boolean get() = remaining == 0
    }

    /**
     * Has held the entitlement before — a paid period or a trial — and does not
     * now. No free allowance: the 10 free workouts ARE the trial, and they have
     * already had more than that.
     *
     * A case of its own rather than `Metered(limit, limit)` because the surface it
     * drives is genuinely different, not just differently worded: it speaks about
     * their whole library rather than a meter ("Your 47 workouts are safe"), it is
     * offers Renew AND Restore purchase, and it
     * is drawn on the neutral card rather than the brand one. None of that can be
     * expressed as a spent meter.
     *
     * [totalWorkouts] is every workout this account has ever logged, INCLUDING the
     * ones logged while subscribed — the point of the line is that nothing was
     * lost.
     */
    data class Lapsed(val totalWorkouts: Int) : WorkoutQuota
}
