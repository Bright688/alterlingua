package com.alterlingua.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icons for the bottom navigation tabs that are not in material-icons-core.
 * Drawn from Material Symbols path data so the app does not need the large
 * material-icons-extended library. Home and Settings come from material-icons-core.
 */
object NavIcons {
    val Learn: ImageVector by lazy {
        icon("Learn", "M5,13.18v4L12,21l7,-3.82v-4L12,17l-7,-3.82zM12,3L1,9l11,6 9,-4.91V17h2V9L12,3z")
    }

    val Words: ImageVector by lazy {
        icon(
            "Words",
            "M18,2H6c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z" +
                "M6,4h5v8l-2.5,-1.5L6,12V4z",
            evenOdd = true,
        )
    }

    val Progress: ImageVector by lazy {
        icon("Progress", "M5,9.2h3V19H5zM10.6,5h2.8v14h-2.8zM16.2,13H19v6h-2.8z")
    }

    private fun icon(name: String, pathData: String, evenOdd: Boolean = false): ImageVector =
        pathIcon(name, pathData, evenOdd)
}

/** Builds a 24dp icon from Material Symbols path data. */
internal fun pathIcon(name: String, pathData: String, evenOdd: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black),
        pathFillType = if (evenOdd) {
            androidx.compose.ui.graphics.PathFillType.EvenOdd
        } else {
            androidx.compose.ui.graphics.PathFillType.NonZero
        },
    ).build()
