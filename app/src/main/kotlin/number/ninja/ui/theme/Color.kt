package number.ninja.ui.theme

import androidx.compose.ui.graphics.Color

// Warm, playful but not garish Material 3 palette for a ~3rd-grade math trainer.
// Orange = primary ("let's go"), teal = secondary (calm/correct), grape = tertiary
// (facts/rewards). Named by role rather than a generated tonal ramp, since this is a
// small hand-picked set for MVP theming, not a full design-token system yet.

// Light scheme
val md_light_primary = Color(0xFFB4560A)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFFFFDCC2)
val md_light_onPrimaryContainer = Color(0xFF381E00)

val md_light_secondary = Color(0xFF3C6659)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFFBEECDB)
val md_light_onSecondaryContainer = Color(0xFF002015)

val md_light_tertiary = Color(0xFF8B5000)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = Color(0xFFFFDDB8)
val md_light_onTertiaryContainer = Color(0xFF2B1700)

val md_light_error = Color(0xFFBA1A1A)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFFDAD6)
val md_light_onErrorContainer = Color(0xFF410002)

val md_light_background = Color(0xFFFFFBFF)
val md_light_onBackground = Color(0xFF201A17)
val md_light_surface = Color(0xFFFFFBFF)
val md_light_onSurface = Color(0xFF201A17)
val md_light_surfaceVariant = Color(0xFFF5DFD1)
val md_light_onSurfaceVariant = Color(0xFF52443A)
val md_light_outline = Color(0xFF847469)

// Dark scheme
val md_dark_primary = Color(0xFFFFB787)
val md_dark_onPrimary = Color(0xFF4E2500)
val md_dark_primaryContainer = Color(0xFF703A00)
val md_dark_onPrimaryContainer = Color(0xFFFFDCC2)

val md_dark_secondary = Color(0xFFA2D0BF)
val md_dark_onSecondary = Color(0xFF07372A)
val md_dark_secondaryContainer = Color(0xFF224F40)
val md_dark_onSecondaryContainer = Color(0xFFBEECDB)

val md_dark_tertiary = Color(0xFFFFB868)
val md_dark_onTertiary = Color(0xFF462A00)
val md_dark_tertiaryContainer = Color(0xFF663D00)
val md_dark_onTertiaryContainer = Color(0xFFFFDDB8)

val md_dark_error = Color(0xFFFFB4AB)
val md_dark_onError = Color(0xFF690005)
val md_dark_errorContainer = Color(0xFF93000A)
val md_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_dark_background = Color(0xFF201A17)
val md_dark_onBackground = Color(0xFFEDE0DA)
val md_dark_surface = Color(0xFF201A17)
val md_dark_onSurface = Color(0xFFEDE0DA)
val md_dark_surfaceVariant = Color(0xFF52443A)
val md_dark_onSurfaceVariant = Color(0xFFD8C7BB)
val md_dark_outline = Color(0xFF9F8D81)

// Semantic "correct answer" green — not part of the M3 ColorScheme's default slots (tertiary is
// already spoken for as the grape "facts/rewards" role), added specifically for the multiple-
// choice answer highlight (spec: tap-to-answer, correct option highlights green). Kept separate
// from `error`/`errorContainer` (used for the wrong-option highlight) rather than introducing a
// second unrelated meaning for an existing role.
val md_light_success = Color(0xFF2E6B32)
val md_light_onSuccess = Color(0xFFFFFFFF)
val md_light_successContainer = Color(0xFFB6F1B0)
val md_light_onSuccessContainer = Color(0xFF002204)

val md_dark_success = Color(0xFF9BD497)
val md_dark_onSuccess = Color(0xFF003909)
val md_dark_successContainer = Color(0xFF15521C)
val md_dark_onSuccessContainer = Color(0xFFB6F1B0)
