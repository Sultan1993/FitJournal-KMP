package kz.maestrosultan.fitjournal.analytics

import kotlin.concurrent.Volatile

/**
 * Where a quota paywall was asked for. [value] is the wire value of the `entry`
 * param; [isCard] splits the vocabulary into the two kinds the paywall funnel
 * reports on: a card the user tapped voluntarily vs a gate that stopped a write.
 */
enum class PaywallEntry(val value: String, val isCard: Boolean) {
    HOME_CARD("home_card", true),
    LIST_CARD("list_card", true),
    WORKOUT_SCREEN("workout_screen", false),
    IMPORT("import", false),
    REPEAT("repeat", false),
    ADD_EXERCISE("add_exercise", false),
    EXERCISE_CALENDAR("exercise_calendar", false),
}

/**
 * The entry of the paywall that is ON SCREEN right now — bound when Superwall
 * reports the paywall presented ([Analytics.paywallShown]) and cleared on
 * dismiss ([Analytics.paywallDismissed]) and logout ([Analytics.clearUser]).
 *
 * A request that never presented (skipped, errored) never binds it, so a later
 * purchase cannot be attributed to a paywall the user did not see. The purchase
 * path reads it synchronously before awaiting the store, because dismiss can
 * land while the purchase is still in flight.
 */
object PaywallAttribution {
    @Volatile
    var presented: PaywallEntry? = null
}
