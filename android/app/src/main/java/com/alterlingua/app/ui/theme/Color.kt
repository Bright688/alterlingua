package com.alterlingua.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.alterlingua.app.learning.MasteryStatus

/**
 * AlterLingua colour tokens.
 *
 * Source: Stitch "AlterLingua Design System" (light theme named colours).
 * The dark palette uses the documented dark surfaces (#0B0F19 / #131B2E / #1E293B / #334155)
 * plus the design system's "fixed-dim" tones; Stitch does not define a full dark scheme yet.
 */
internal object Brand {
    // Light
    val Primary = Color(0xFF004AC6)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFF2563EB)
    val OnPrimaryContainer = Color(0xFFEEEFFF)
    val Secondary = Color(0xFF006A61)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFF86F2E4)
    val OnSecondaryContainer = Color(0xFF006F66)
    val Tertiary = Color(0xFF632ECD)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFF7D4CE7)
    val OnTertiaryContainer = Color(0xFFF6EDFF)
    val Error = Color(0xFFBA1A1A)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF93000A)
    val Background = Color(0xFFFAF8FF)
    val OnBackground = Color(0xFF131B2E)
    val Surface = Color(0xFFFAF8FF)
    val OnSurface = Color(0xFF131B2E)
    val SurfaceVariant = Color(0xFFDAE2FD)
    val OnSurfaceVariant = Color(0xFF434655)
    val Outline = Color(0xFF737686)
    val OutlineVariant = Color(0xFFC3C6D7)

    // Dark
    val DarkPrimary = Color(0xFFB4C5FF)
    val DarkOnPrimary = Color(0xFF00174B)
    val DarkPrimaryContainer = Color(0xFF003EA8)
    val DarkOnPrimaryContainer = Color(0xFFDBE1FF)
    val DarkSecondary = Color(0xFF6BD8CB)
    val DarkOnSecondary = Color(0xFF00201D)
    val DarkTertiary = Color(0xFFD0BCFF)
    val DarkOnTertiary = Color(0xFF23005C)
    val DarkBackground = Color(0xFF0B0F19)
    val DarkSurface = Color(0xFF131B2E)
    val DarkSurfaceVariant = Color(0xFF1E293B)
    val DarkOnSurface = Color(0xFFF8FAFC)
    val DarkOnSurfaceVariant = Color(0xFFC3C6D7)
    val DarkOutline = Color(0xFF334155)

    // Cards and chips (design system "Elevation & Depth" and "Components")
    val LightCard = Color(0xFFFFFFFF)
    val LightCardBorder = Color(0xFFE2E8F0)
    val LightChipSurface = Color(0xFFF1F5F9)
    val DarkCard = Color(0xFF131B2E)
    val DarkCardBorder = Color(0xFF334155)
    val DarkChipSurface = Color(0xFF1E293B)

    // Bottom navigation indicator pill (primary-fixed light, primary tint dark)
    val LightNavIndicator = Color(0xFFDBE1FF)
    val DarkNavIndicator = Color(0xFF1E3A8A)
}

/** Colours for one Personal Language Map state: chip container, chip text, chip border and chart accent. */
@Immutable
data class MasteryPalette(
    val container: Color,
    val content: Color,
    val border: Color,
    val accent: Color,
)

/** Colours the Material 3 scheme does not cover. Provided by [AlterLinguaTheme]. */
@Immutable
data class ExtendedColors(
    val card: Color,
    val cardBorder: Color,
    val chipSurface: Color,
    val navIndicator: Color,
    private val newWord: MasteryPalette,
    private val learning: MasteryPalette,
    private val familiar: MasteryPalette,
    private val mastered: MasteryPalette,
) {
    fun mastery(status: MasteryStatus): MasteryPalette = when (status) {
        MasteryStatus.UNKNOWN -> newWord
        MasteryStatus.LEARNING -> learning
        MasteryStatus.FAMILIAR -> familiar
        MasteryStatus.MASTERED -> mastered
    }
}

// Light chips: exact values from the Stitch design system. Accents: its semantic tokens.
internal val LightExtendedColors = ExtendedColors(
    card = Brand.LightCard,
    cardBorder = Brand.LightCardBorder,
    chipSurface = Brand.LightChipSurface,
    navIndicator = Brand.LightNavIndicator,
    newWord = MasteryPalette(Color(0xFFF1F5F9), Color(0xFF475569), Color(0xFFCBD5E1), Color(0xFF94A3B8)),
    learning = MasteryPalette(Color(0xFFFEF3C7), Color(0xFFB45309), Color(0xFFFCD34D), Color(0xFFF59E0B)),
    familiar = MasteryPalette(Color(0xFFCCFBF1), Color(0xFF0F766E), Color(0xFF5EEAD4), Color(0xFF0D9488)),
    mastered = MasteryPalette(Color(0xFFD1FAE5), Color(0xFF047857), Color(0xFF6EE7B7), Color(0xFF10B981)),
)

// Dark chips are derived (dark container, light text) because Stitch does not define them yet.
internal val DarkExtendedColors = ExtendedColors(
    card = Brand.DarkCard,
    cardBorder = Brand.DarkCardBorder,
    chipSurface = Brand.DarkChipSurface,
    navIndicator = Brand.DarkNavIndicator,
    newWord = MasteryPalette(Color(0xFF1E293B), Color(0xFFCBD5E1), Color(0xFF475569), Color(0xFF94A3B8)),
    learning = MasteryPalette(Color(0xFF3A2A0A), Color(0xFFFCD34D), Color(0xFF92400E), Color(0xFFF59E0B)),
    familiar = MasteryPalette(Color(0xFF0B3B36), Color(0xFF5EEAD4), Color(0xFF0F766E), Color(0xFF2DD4BF)),
    mastered = MasteryPalette(Color(0xFF0B3A2C), Color(0xFF6EE7B7), Color(0xFF047857), Color(0xFF34D399)),
)
