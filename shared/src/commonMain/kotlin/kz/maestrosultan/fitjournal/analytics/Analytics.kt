package kz.maestrosultan.fitjournal.analytics

import kotlin.concurrent.Volatile

/**
 * The process-wide tracker — a global `object` like `UserSession` and
 * `FreeQuotaSettings`: no DI, Swift calls it synchronously.
 *
 * Every call is a no-op until the native layer [install]s a sink, and every sink
 * call runs inside `runCatching`. Analytics must never throw: an unhandled Kotlin
 * throw crossing into Swift is an uncatchable SIGABRT, and these calls sit on
 * taps and write paths.
 */
object Analytics : AnalyticsTracker {

    @Volatile
    private var sink: AnalyticsSink? = null

    fun install(sink: AnalyticsSink) {
        this.sink = sink
    }

    override fun track(event: AnalyticsEvent) = withSink { it.logEvent(event.spec.name, event.params) }

    override fun screenView(screen: ScreenName) = track(AnalyticsEvent.ScreenView(screen))

    override fun setUserProperty(property: UserProperty, value: String?) =
        withSink { it.setUserProperty(property.value, value) }

    fun setUserId(userId: String?) = withSink { it.setUserId(userId) }

    /**
     * Logout. Everything that describes the ACCOUNT goes — user id, every
     * [UserPropertyScope.ACCOUNT] property, the presented-paywall attribution —
     * so the next account on this device inherits none of it. PROCESS-scoped
     * properties (`app_build`) describe the install and stay.
     */
    fun clearUser() {
        PaywallAttribution.presented = null
        setUserId(null)
        UserProperty.entries
            .filter { it.scope == UserPropertyScope.ACCOUNT }
            .forEach { setUserProperty(it, null) }
    }

    /** A native entry point asked for a paywall. Attribution is untouched: nothing is on screen yet. */
    fun paywallRequested(entry: PaywallEntry) = track(AnalyticsEvent.PaywallRequested(entry))

    /** Superwall presented it: from now until dismiss, a purchase belongs to [entry]. */
    fun paywallShown(entry: PaywallEntry, placement: String) {
        PaywallAttribution.presented = entry
        track(AnalyticsEvent.PaywallShown(entry, placement))
    }

    /** Superwall decided not to show one. Nothing was presented, so attribution is untouched. */
    fun paywallSkipped(entry: PaywallEntry, reason: String) = track(AnalyticsEvent.PaywallSkipped(entry, reason))

    fun paywallDismissed() {
        PaywallAttribution.presented = null
    }

    /** Test seam: back to the never-installed state. jvmTest shares one JVM across suites. */
    internal fun reset() {
        sink = null
        PaywallAttribution.presented = null
    }

    private inline fun withSink(block: (AnalyticsSink) -> Unit) {
        val installed = sink ?: return
        runCatching { block(installed) }
    }
}
