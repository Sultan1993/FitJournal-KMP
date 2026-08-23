package kz.maestrosultan.fitjournal.kmp.design

/**
 * One color role, as 32-bit ARGB packed into a Long (0xAARRGGBB).
 *
 * Compose consumes the value directly (`Color(token.light)`); iOS wraps it
 * in a `UIColor(dynamicProvider:)` (see `UIColorExtensions.swift`).
 */
data class ColorToken(val light: Long, val dark: Long)

/**
 * Single source of truth for the foundational palette on BOTH apps.
 *
 * Tokens are named by role, not appearance. Platform accessor layers
 * (`FitJournalColors` on Android, `UIColor` extensions on iOS) map their
 * legacy names onto these tokens — new code should use the semantic names.
 *
 * Two places cannot read this object because they render before any code
 * runs, and must be kept in sync by hand with [brand] / [background]:
 *  - iOS launch screen + IB named colors: regenerate the asset catalog with
 *    `iOS/scripts/sync-color-assets.py` after editing tokens here.
 *  - Android `app/src/main/res/values` + `values-night` colors.xml (launcher/splash/auth).
 *
 * Design decisions baked in (FJ 2.0 palette consolidation):
 *  - Grays are lavender-tinted (the slate #4C5980 family was retired).
 *  - Alpha ramps are for lines only; text colors are solid.
 *  - [textSecondary] passes WCAG AA (>= 4.5:1) on [background] and [surface]
 *    in both modes — the old #9C9EB9 secondary text (~2.5:1) did not.
 *
 * Muscle-group chart colors intentionally live elsewhere: `CategoryType.colorHex`.
 */
object ColorTokens {

    // Brand
    val brand = ColorToken(0xFF7C72F2, 0xFF7C72F2)
    val brandSubtle = ColorToken(0xFFE5E1FC, 0xFF2B2650)
    val accent = ColorToken(0xFFFBEAB2, 0xFFFBEAB2)

    /**
     * Brand-coloured INK for content drawn on [brandSubtle], not on the page.
     * Deliberately not [brand]: #7C72F2 sits too close to the light brandSubtle
     * (#E5E1FC) and too dark on the dark one (#2B2650), so the design steps it
     * down in light and up in dark to hold contrast on both.
     */
    val brandInk = ColorToken(0xFF6F66DE, 0xFFA79EFF)

    /**
     * Muted ink on [brandSubtle] — the sub-lines under a brand-card headline.
     * Light is [textSecondary] exactly; dark is violet-tinted, because the
     * neutral #A6A9C0 reads dirty against #2B2650.
     */
    val brandInkSecondary = ColorToken(0xFF61647D, 0xFFC3C0E8)

    // Surfaces — dark-mode elevation order (lighter = higher):
    //   background < sheet < surface(card) < surfaceElevated.
    val background = ColorToken(0xFFFFFFFF, 0xFF000000)

    /**
     * Bottom sheets / modal popups. Light = same as [background] (the dimmed
     * screen behind separates them). Dark = lifted off pure black so the sheet
     * doesn't blend into the barely-visible dim.
     */
    val sheet = ColorToken(0xFFFFFFFF, 0xFF18181F)

    val card = ColorToken(0xFFF1F3F9, 0xFF18181F)

    /** Cards. Above both [background] and [sheet] in dark, so a card reads as raised on either. */
    val surface = ColorToken(0xFFF1F3F9, 0xFF26262E)

    /**
     * Nested/elevated fills (Focus pills, picker segments) + floating chrome
     * (alerts, action sheets) — one step above [surface] in dark.
     */
    val surfaceElevated = ColorToken(0xFFFFFFFF, 0xFF2E2E38)

    // Text — solid colors, three steps.
    val textPrimary = ColorToken(0xFF040415, 0xFFFFFFFF)

    /** Meaningful supporting text (subtitles, dates, counts). */
    val textSecondary = ColorToken(0xFF61647D, 0xFFA6A9C0)

    /** Hints only: placeholders, disabled states, decorative unit captions. */
    val textTertiary = ColorToken(0xFF9C9EB9, 0xFF70738C)

    // Lines — alpha over textPrimary's base so they sit on any surface.
    val border = ColorToken(0x33040415, 0x33FFFFFF) // 20%
    val divider = ColorToken(0x1A040415, 0x1AFFFFFF) // 10%

    // Feedback
    val positive = ColorToken(0xFF2D9E64, 0xFF2D9E64)
    val negative = ColorToken(0xFFEB6363, 0xFFEB6363)
}
