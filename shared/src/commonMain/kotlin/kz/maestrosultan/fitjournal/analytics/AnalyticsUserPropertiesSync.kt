package kz.maestrosultan.fitjournal.analytics

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kz.maestrosultan.fitjournal.domain.quota.FreeQuotaSettings
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate
import kz.maestrosultan.fitjournal.domain.user.UserSession

/**
 * Keeps the three ACCOUNT-scoped user properties — `sub_tier`, `quota_state`,
 * `workouts_used` — in step with the signed-in user's subscription and meter.
 *
 * Follows [UserSession]: for each signed-in user it combines the reactive quota
 * with the raw [FreeQuotaSettings] inputs and sets the properties on every
 * distinct change. Signed out, it clears them (see below); the next sign-in sets them afresh even when the values
 * equal the previous account's, because the de-duplication is per session.
 *
 * Signed out, it WRITES null to all three: logout order isn't ours to control
 * (an outgoing session's [FreeQuotaSettings] reset can land after
 * [Analytics.clearUser]), so the sync itself guarantees the final state is cleared.
 */
@OptIn(ExperimentalAtomicApi::class, ExperimentalCoroutinesApi::class)
object AnalyticsUserPropertiesSync {

    private val running = AtomicReference<Job?>(null)

    /** Production entry point (both platforms, once at boot). Idempotent. */
    fun start(quotaGate: WorkoutQuotaGate) {
        start(quotaGate, Analytics, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    /** Test seam. Idempotent while a previous start is still running; a cancelled [scope] frees it. */
    fun start(quotaGate: WorkoutQuotaGate, tracker: AnalyticsTracker, scope: CoroutineScope) {
        val current = running.load()
        if (current?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            properties(quotaGate)
                // Terminal backstop: a throw here would end syncing for the process.
                // Cancellation-transparent, so stopping the scope still stops this.
                .catch { }
                .collect { values ->
                    tracker.setUserProperty(UserProperty.SUB_TIER, values?.subTier)
                    tracker.setUserProperty(UserProperty.QUOTA_STATE, values?.quotaState)
                    tracker.setUserProperty(UserProperty.WORKOUTS_USED, values?.workoutsUsed)
                }
        }
        if (running.compareAndSet(current, job)) job.start() else job.cancel()
    }

    private fun properties(quotaGate: WorkoutQuotaGate): Flow<QuotaProperties?> =
        UserSession.state.flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                combine(
                    quotaGate.getQuotaFlow(session.userId),
                    FreeQuotaSettings.config,
                    FreeQuotaSettings.isEntitled,
                ) { quota, config, isEntitled ->
                    QuotaProperties.of(quota, config.hasEverSubscribed, isEntitled)
                }.distinctUntilChanged()
            }
            // null = signed out → cleared. Consecutive nulls (re-emitted logout) collapse.
        }.distinctUntilChanged()
}

/** The derived property values; `internal` so the harness test can assert the mapping directly. */
internal data class QuotaProperties(val subTier: String, val quotaState: String, val workoutsUsed: String?) {
    companion object {
        fun of(quota: WorkoutQuota, hasEverSubscribed: Boolean?, isEntitled: Boolean?) = QuotaProperties(
            subTier = when {
                isEntitled == true -> "entitled"
                hasEverSubscribed == true && isEntitled == false -> "lapsed"
                hasEverSubscribed == false -> "free"
                else -> "unknown"
            },
            quotaState = when (quota) {
                WorkoutQuota.Unlimited -> "unlimited"
                is WorkoutQuota.Metered -> if (quota.isExhausted) "exhausted" else "metered"
                is WorkoutQuota.Lapsed -> "lapsed"
            },
            workoutsUsed = when (quota) {
                WorkoutQuota.Unlimited -> null
                is WorkoutQuota.Metered -> quota.used.toString()
                is WorkoutQuota.Lapsed -> quota.totalWorkouts.toString()
            },
        )
    }
}
