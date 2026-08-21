package kz.maestrosultan.fitjournal.domain.quota

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The one place the native layers push free-quota configuration into shared
 * code. A global `object` + StateFlow, deliberately mirroring [UserSession]:
 * Swift reads and writes it synchronously, no DI, no suspend interface.
 *
 * The whole model is two facts: how many free workouts a never-subscriber gets,
 * and whether this user has ever subscribed. There is no cutoff instant and no
 * stored counter — see [WorkoutQuotaGate] for why that is enough.
 */
object FreeQuotaSettings {

    data class Config(
        val limit: Int,
        /**
         * Has this user EVER held the subscription entitlement — including a
         * trial, and including one that has since expired?
         *
         * `null` means NOT YET KNOWN (offline, or Qonversion hasn't answered),
         * which is treated as unmetered. Unknown must fail open: a device that
         * cannot reach Qonversion usually cannot reach Superwall either, so
         * metering it would block the user with no way to buy. It self-heals —
         * the count is derived from the records table, so the first definitive
         * answer applies retroactively.
         */
        val hasEverSubscribed: Boolean?,
        /**
         * When the last subscription ran out, ISO-8601, DISPLAY ONLY — it dates the
         * lapsed card's eyebrow and is never part of any gate decision. Null when we
         * never cached an expiry, which the card renders as an undated eyebrow.
         */
        val subscriptionEndedAtIso: String?,
    )

    private val _config = MutableStateFlow(Config(limit = 0, hasEverSubscribed = null, subscriptionEndedAtIso = null))
    val config: StateFlow<Config> = _config.asStateFlow()

    /** Remote Config → shared. Called once per launch, after fetchAndActivate. */
    fun setLimit(limit: Long) {
        _config.update { it.copy(limit = limit.toInt().coerceAtLeast(0)) }
    }

    /**
     * Subscription layer → shared. Pass null to mean "still unknown".
     *
     * Two monotonic rules, and only [reset] escapes them:
     *
     * 1. STICKY ONCE TRUE. Having ever subscribed is a fact about the past and
     *    cannot become false. Without this a later offline/failed probe would
     *    silently hand a former subscriber a fresh free allowance on the next
     *    launch — a reinstall-free way to reset the meter.
     *
     * 2. UNKNOWN NEVER DEMOTES A KNOWN ANSWER. `null` means "we did not find
     *    out", which is not news and must not erase what we already learned.
     *    The foreground retry made overlapping probes possible for the first
     *    time, and without this a slow probe that misses the network ceiling
     *    lands AFTER a fast one that resolved an authoritative `false` and puts
     *    the user back to unmetered until the next foreground re-reads disk.
     *    Fail-open and self-healing, but free to close.
     */
    fun setHasEverSubscribed(hasEverSubscribed: Boolean?) {
        _config.update { current ->
            when {
                current.hasEverSubscribed == true -> current
                hasEverSubscribed == null && current.hasEverSubscribed != null -> current
                else -> current.copy(hasEverSubscribed = hasEverSubscribed)
            }
        }
    }

    /**
     * Clear everything back to "nothing known yet".
     *
     * Required on LOGOUT, not just in tests: this is a process-wide singleton and
     * `hasEverSubscribed` is sticky, so without it the next account to sign in on
     * the same device inherits the previous one's answer — a never-subscriber
     * would be refused their free allowance because the previous user had
     * subscribed, or vice versa.
     */
    fun reset() {
        _config.value = Config(limit = 0, hasEverSubscribed = null, subscriptionEndedAtIso = null)
        _isEntitled.value = null
    }

    /** Subscription layer → shared. Display only; see [Config.subscriptionEndedAtIso]. */
    fun setSubscriptionEndedAt(iso: String?) {
        _config.update { it.copy(subscriptionEndedAtIso = iso) }
    }

    /**
     * Does this user hold a live entitlement RIGHT NOW? Tri-state for the same
     * reason [Config.hasEverSubscribed] is.
     *
     * `null` = not resolved yet. A plain Boolean starting `false` made "the
     * subscription layer has not reported yet" indistinguishable from
     * "authoritatively not entitled", and that is the one direction that fails
     * CLOSED: a PAYING subscriber whose sticky `hasEverSubscribed` is restored
     * from disk (no network needed) before Superwall reports would resolve to
     * [WorkoutQuota.Lapsed] and be refused a write. Every non-active path in the
     * subscription controller reaches `deactivateSubscription()`, so a genuine
     * lapse still produces an explicit `false` within the same launch.
     */
    private val _isEntitled = MutableStateFlow<Boolean?>(null)
    val isEntitled: StateFlow<Boolean?> = _isEntitled.asStateFlow()

    /**
     * Exactly three permitted call sites per platform:
     *  - SuperwallController.activateSubscription   → true
     *  - SuperwallController.deactivateSubscription → false
     *  - the monetization-disabled branch of ConfigurationViewModel → true
     *    (this is what keeps DEBUG builds, and therefore the demo screenshot
     *    harness, unmetered)
     */
    fun setEntitled(entitled: Boolean) {
        _isEntitled.value = entitled
    }

    /**
     * Is there still an entitlement-history answer worth going to the network
     * for? The platforms poll this on FOREGROUND and re-probe when it is true.
     *
     * [Config.hasEverSubscribed] is resolved once per account and then cached on
     * disk, so this is only ever true when the FIRST resolution missed — signed
     * in offline, or Qonversion didn't answer inside the launch timeout. Without
     * the retry that miss lasts until the next cold start, which users rarely
     * perform; with it, one app switch closes the unmetered window.
     *
     * Entitled short-circuits it. An entitled user is [WorkoutQuota.Unlimited]
     * whatever the history says, and on a monetization-disabled build (the
     * `setEntitled(true)` branch of ConfigurationViewModel) Qonversion was never
     * initialised — so the probe would be both pointless and unanswerable.
     */
    val needsEntitlementHistory: Boolean
        get() = _config.value.hasEverSubscribed == null && _isEntitled.value != true
}
