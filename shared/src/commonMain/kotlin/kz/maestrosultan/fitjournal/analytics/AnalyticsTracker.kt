package kz.maestrosultan.fitjournal.analytics

/**
 * What shared code emits through. Non-suspend on purpose: callers fire and
 * forget from use cases, ViewModels and composables, and Swift can call it
 * synchronously. Production is [Analytics]; jvmTests inject a recording fake.
 */
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)

    /** Exactly `track(AnalyticsEvent.ScreenView(screen))` — one catalog case, one path. */
    fun screenView(screen: ScreenName)

    fun setUserProperty(property: UserProperty, value: String?)
}

/**
 * The platform analytics SDK, as the native layer hands it to [Analytics.install]
 * (Firebase on both platforms). Non-suspend so Swift implements the plain
 * requirement, not SKIE's `__`-prefixed async one.
 *
 * [logEvent] params are already wire-shaped: every value is a `Long`, `Double`
 * or `String`, which cross to Swift as `NSNumber` / `NSString` and go straight
 * into the SDK call.
 */
interface AnalyticsSink {
    fun logEvent(name: String, params: Map<String, Any>)
    fun setUserId(userId: String?)
    fun setUserProperty(name: String, value: String?)
}
