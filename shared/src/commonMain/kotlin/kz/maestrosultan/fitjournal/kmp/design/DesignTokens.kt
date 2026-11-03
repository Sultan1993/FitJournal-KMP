package kz.maestrosultan.fitjournal.kmp.design

/*
 * Non-colour design tokens — the single source for spacing, corner radii and type sizes on
 * BOTH apps (CMP, native Android, native iOS) and for the Claude Design project.
 *
 * Values are points (iOS) / dp (Android, CMP) / sp for type. Names are the values on purpose,
 * so "t16" or "s24" means the same thing in a design comment, a CSS class and a call site.
 *
 * Generated from this file (never edit the outputs by hand):
 *  - Claude Design `app/tokens.css` and the token doc — `scripts/design_tokens.py generate`
 * Enforced by `scripts/design_tokens.py check` (raw literals that bypass these objects fail).
 *
 * Fixed sizes (icon 24, avatar 40, button height 54, …) are geometry, not tokens — they stay
 * literal. Tokens cover gaps/padding, corner radii and font sizes only.
 */

/** Spacing scale: padding, gaps, spacers. [s2] is for hairline gaps only. */
object SpaceTokens {
    const val s2 = 2
    const val s4 = 4
    const val s8 = 8
    const val s12 = 12
    const val s16 = 16
    const val s24 = 24
    const val s32 = 32
}

/** Corner radii by role. Pills and circles use a full (50%) radius and are not tokens. */
object RadiusTokens {
    /** Progress bars, indicators, meter segments. */
    const val bar = 4

    /** Keys, thumbnails, chips, rows inside a sheet. */
    const val chip = 12

    /** Buttons and inputs. */
    const val control = 16

    /** Cards and cells. */
    const val card = 20

    /** Bottom sheets (top corners). */
    const val sheet = 28
}

/** Type scale (sp / pt). Family is always Rubik. [display36]/[display48] are numeric readouts and the login headline only. */
object TypeTokens {
    const val t10 = 10
    const val t12 = 12
    const val t14 = 14
    const val t16 = 16
    const val t20 = 20
    const val t24 = 24
    const val t34 = 34
    const val display36 = 36
    const val display48 = 48
}
