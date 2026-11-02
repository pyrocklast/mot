package io.github.pyrocklast.mot.util

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.widget.TextView
import io.github.pyrocklast.mot.data.Prefs

/** The launcher's look: fonts, weights and the text colour, shared by the launcher and settings' previews. */
object Look {

    /** The weights offered, from thin to bold. */
    val WEIGHTS = intArrayOf(100, 300, 400, 500, 700)

    val FONTS = intArrayOf(Prefs.FONT_SANS, Prefs.FONT_SERIF, Prefs.FONT_MONO, Prefs.FONT_CONDENSED)

    /**
     * One of Android's own fonts at a weight. Android 12 and later draw any weight of its main
     * font (it's a variable font); other families use their nearest weight.
     */
    fun typeface(font: Int, weight: Int): Typeface {
        val family = when (font) {
            Prefs.FONT_SERIF -> Typeface.SERIF
            Prefs.FONT_MONO -> Typeface.MONOSPACE
            Prefs.FONT_CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            else -> Typeface.SANS_SERIF
        }
        return Typeface.create(family, weight, false)
    }

    /**
     * Light text for dark wallpapers, dark text for light ones: as the wallpaper suits (Android
     * works this out from its colours), as set, or following dark mode.
     */
    fun lightText(context: Context, prefs: Prefs): Boolean = when (prefs.textColour) {
        Prefs.TEXT_DARK -> false
        Prefs.TEXT_LIGHT -> true
        Prefs.TEXT_FOLLOW_DARK_MODE ->
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> !wallpaperSuitsDarkText(context)
    }

    private fun wallpaperSuitsDarkText(context: Context): Boolean {
        // Unknown (e.g. some live wallpapers): light text, with its shadow, reads on most wallpapers
        val colours = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return false
        return colours.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
    }

    /** Launcher text colours and shadow, for previews outside the launcher. */
    fun applyTextColour(view: TextView, light: Boolean) {
        view.setTextColor(if (light) Color.WHITE else Color.BLACK)
        view.setShadowLayer(4f, 0f, 2f, if (light) 0x80000000.toInt() else 0x80FFFFFF.toInt())
    }
}
