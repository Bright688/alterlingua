package com.alterlingua.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.alterlingua.app.learning.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the palette against accidental drift from the approved Stitch design system. */
class BrandColorsTest {

    @Test
    fun lightPalette_matchesStitchDesignSystem() {
        assertEquals(Color(0xFF004AC6), Brand.Primary)
        assertEquals(Color(0xFF2563EB), Brand.PrimaryContainer)
        assertEquals(Color(0xFF006A61), Brand.Secondary)
        assertEquals(Color(0xFF632ECD), Brand.Tertiary)
        assertEquals(Color(0xFFFAF8FF), Brand.Background)
    }

    @Test
    fun darkPalette_usesDocumentedDarkSurfaces() {
        assertEquals(Color(0xFF0B0F19), Brand.DarkBackground)
        assertEquals(Color(0xFF131B2E), Brand.DarkSurface)
        assertEquals(Color(0xFF1E293B), Brand.DarkSurfaceVariant)
        assertEquals(Color(0xFF334155), Brand.DarkOutline)
    }

    @Test
    fun masteryChips_matchStitchHierarchyChips() {
        val light = LightExtendedColors
        assertEquals(Color(0xFFF1F5F9), light.mastery(MasteryStatus.UNKNOWN).container)
        assertEquals(Color(0xFFFEF3C7), light.mastery(MasteryStatus.LEARNING).container)
        assertEquals(Color(0xFFB45309), light.mastery(MasteryStatus.LEARNING).content)
        assertEquals(Color(0xFFCCFBF1), light.mastery(MasteryStatus.FAMILIAR).container)
        assertEquals(Color(0xFFD1FAE5), light.mastery(MasteryStatus.MASTERED).container)
        assertEquals(Color(0xFF047857), light.mastery(MasteryStatus.MASTERED).content)
    }
}
