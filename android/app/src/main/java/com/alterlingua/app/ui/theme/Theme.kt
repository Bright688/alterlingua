package com.alterlingua.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Brand.Primary,
    onPrimary = Brand.OnPrimary,
    primaryContainer = Brand.PrimaryContainer,
    onPrimaryContainer = Brand.OnPrimaryContainer,
    secondary = Brand.Secondary,
    onSecondary = Brand.OnSecondary,
    secondaryContainer = Brand.SecondaryContainer,
    onSecondaryContainer = Brand.OnSecondaryContainer,
    tertiary = Brand.Tertiary,
    onTertiary = Brand.OnTertiary,
    tertiaryContainer = Brand.TertiaryContainer,
    onTertiaryContainer = Brand.OnTertiaryContainer,
    error = Brand.Error,
    onError = Brand.OnError,
    errorContainer = Brand.ErrorContainer,
    onErrorContainer = Brand.OnErrorContainer,
    background = Brand.Background,
    onBackground = Brand.OnBackground,
    surface = Brand.Surface,
    onSurface = Brand.OnSurface,
    surfaceVariant = Brand.SurfaceVariant,
    onSurfaceVariant = Brand.OnSurfaceVariant,
    outline = Brand.Outline,
    outlineVariant = Brand.OutlineVariant,
)

private val DarkColors = darkColorScheme(
    primary = Brand.DarkPrimary,
    onPrimary = Brand.DarkOnPrimary,
    primaryContainer = Brand.DarkPrimaryContainer,
    onPrimaryContainer = Brand.DarkOnPrimaryContainer,
    secondary = Brand.DarkSecondary,
    onSecondary = Brand.DarkOnSecondary,
    tertiary = Brand.DarkTertiary,
    onTertiary = Brand.DarkOnTertiary,
    background = Brand.DarkBackground,
    onBackground = Brand.DarkOnSurface,
    surface = Brand.DarkSurface,
    onSurface = Brand.DarkOnSurface,
    surfaceVariant = Brand.DarkSurfaceVariant,
    onSurfaceVariant = Brand.DarkOnSurfaceVariant,
    outline = Brand.DarkOutline,
)

// Design system "Shapes": 12dp fields, 16-24dp cards, pill (full) chips and buttons.
private val AlterLinguaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Accessor for the colours that are not part of the Material 3 colour scheme. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current

/**
 * The AlterLingua visual system. Uses the brand palette rather than Android dynamic
 * colour so the app matches the approved Stitch design.
 */
@Composable
fun AlterLinguaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AlterLinguaTypography,
            shapes = AlterLinguaShapes,
            content = content,
        )
    }
}
