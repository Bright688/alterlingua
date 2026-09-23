package com.alterlingua.app.keyboard

import android.content.Context
import android.content.res.Configuration
import androidx.core.graphics.toColorInt

/** The keyboard's colours. Light values come from the Stitch keyboard design; dark ones match the app's dark theme. */
class KeyboardColors(
    val board: Int,
    val letterKey: Int,
    val functionKey: Int,
    val pressed: Int,
    val actionKey: Int,
    val actionKeyPressed: Int,
    val keyShadow: Int,
    val text: Int,
    val functionText: Int,
    val onAction: Int,
    val outline: Int,
    val accent: Int,
) {
    companion object {
        fun forContext(context: Context): KeyboardColors {
            val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            return if (dark) Dark else Light
        }

        private val Light = KeyboardColors(
            board = "#EAEDFF".toColorInt(),
            letterKey = "#FFFFFF".toColorInt(),
            functionKey = "#E2E7FF".toColorInt(),
            pressed = "#C9D4F5".toColorInt(),
            actionKey = "#004AC6".toColorInt(),
            actionKeyPressed = "#2563EB".toColorInt(),
            keyShadow = "#26131B2E".toColorInt(),
            text = "#131B2E".toColorInt(),
            functionText = "#464555".toColorInt(),
            onAction = "#FFFFFF".toColorInt(),
            outline = "#C3C6D7".toColorInt(),
            accent = "#006A61".toColorInt(),
        )

        private val Dark = KeyboardColors(
            board = "#0F1522".toColorInt(),
            letterKey = "#1E293B".toColorInt(),
            functionKey = "#2C3850".toColorInt(),
            pressed = "#3A4A6B".toColorInt(),
            actionKey = "#B4C5FF".toColorInt(),
            actionKeyPressed = "#DBE1FF".toColorInt(),
            keyShadow = "#59000000".toColorInt(),
            text = "#F8FAFC".toColorInt(),
            functionText = "#C3C6D7".toColorInt(),
            onAction = "#00174B".toColorInt(),
            outline = "#334155".toColorInt(),
            accent = "#6BD8CB".toColorInt(),
        )
    }
}
