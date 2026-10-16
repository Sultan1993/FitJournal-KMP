package kz.maestrosultan.fitjournal.analytics

import java.util.Collections

/**
 * Test fake for [AnalyticsTracker]: records what shared code emitted, in order.
 * Synchronized collections because flows may emit from `Dispatchers.Default`
 * while the test thread reads.
 */
class RecordingTracker : AnalyticsTracker {
    val events: MutableList<AnalyticsEvent> = Collections.synchronizedList(mutableListOf())
    val screens: MutableList<ScreenName> = Collections.synchronizedList(mutableListOf())
    val properties: MutableMap<UserProperty, String?> = Collections.synchronizedMap(mutableMapOf())

    override fun track(event: AnalyticsEvent) {
        events += event
    }

    /** Mirrors [Analytics.screenView]: a screen view IS a `ScreenView` event. */
    override fun screenView(screen: ScreenName) {
        screens += screen
        track(AnalyticsEvent.ScreenView(screen))
    }

    override fun setUserProperty(property: UserProperty, value: String?) {
        properties[property] = value
    }
}
