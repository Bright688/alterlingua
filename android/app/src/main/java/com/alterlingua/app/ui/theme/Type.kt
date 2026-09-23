package com.alterlingua.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alterlingua.app.R

// Variable fonts bundled in res/font (SIL Open Font License, see docs/licenses).
// minSdk 26 supports font variation settings.
private fun variableFont(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Headlines and titles (design system: Plus Jakarta Sans). */
val PlusJakartaSans = FontFamily(
    variableFont(R.font.plus_jakarta_sans, 400),
    variableFont(R.font.plus_jakarta_sans, 500),
    variableFont(R.font.plus_jakarta_sans, 600),
    variableFont(R.font.plus_jakarta_sans, 700),
)

/** Body text and controls (design system: Inter). */
val Inter = FontFamily(
    variableFont(R.font.inter, 400),
    variableFont(R.font.inter, 500),
    variableFont(R.font.inter, 600),
)

/**
 * Compact type scale, reduced twice at the owner's request (the Stitch values were larger). Sizes in sp:
 * display 26, headlines 22 / 18, titles 16 / 14, body 14 / 13 / 12, labels 13 / 12 / 11.
 */
val AlterLinguaTypography = Typography(
    displayLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)
