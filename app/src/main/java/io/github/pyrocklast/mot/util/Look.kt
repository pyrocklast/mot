package io.github.pyrocklast.mot.util

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.widget.TextView
import io.github.pyrocklast.mot.data.Prefs

/** The launcher's look: fonts, weights and the text colour, shared by the launcher and settings' previews. */
object Look {

    /**
     * Light enough for dark text. Android's own hint can say so too, but it is cautious and some
     * wallpapers send none at all, so how light the wallpaper's colours are decides as well.
     */
    private const val LIGHT_ENOUGH_FOR_DARK_TEXT = 0.4f

    // The text takes the lightest and darkest of the colours Android works out from the wallpaper,
    // rather than plain white and black, so the words belong to the picture behind them. The
    // launcher's own text uses these through launcherTextColor (themes.xml).
    private const val LIGHT_TEXT = android.R.color.system_neutral1_50
    private const val DARK_TEXT = android.R.color.system_neutral1_900

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

    /** Light text for dark wallpapers, dark text for light ones: as set, or as the wallpaper suits. */
    fun lightText(context: Context, prefs: Prefs): Boolean = when (prefs.textColour) {
        Prefs.TEXT_DARK -> false
        Prefs.TEXT_LIGHT -> true
        // With a wallpaper that says nothing about itself, the phone's dark mode is the next best
        // guess at the light in the room
        else -> wallpaperSuitsDarkText(context)?.not() ?: darkMode(context)
    }

    private fun darkMode(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /**
     * Whether dark text reads on the wallpaper, or null when the wallpaper has no colours at all.
     * Android hints at this for a picture, but errs towards light text — a wallpaper with one
     * dark corner gets no hint — and a live wallpaper usually sends no hint either. So the hint
     * is taken when it says dark text reads, and otherwise the colours themselves decide.
     */
    private fun wallpaperSuitsDarkText(context: Context): Boolean? {
        val colours = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
        if (colours.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) return true
        return brightness(colours) > LIGHT_ENOUGH_FOR_DARK_TEXT
    }

    /**
     * How light a wallpaper's colours are, from 0 to 1. Brightness here is what the eye makes of
     * a colour, so yellow counts as lighter than blue.
     */
    private fun brightness(colours: WallpaperColors): Float = brightness(
        primary = colours.primaryColor.luminance(),
        secondary = colours.secondaryColor?.luminance(),
        tertiary = colours.tertiaryColor?.luminance(),
    )

    /**
     * A wallpaper's colours as one brightness, the most common counting for most: a wallpaper is
     * mostly its primary colour, with the others as trim.
     */
    internal fun brightness(primary: Float, secondary: Float?, tertiary: Float?): Float {
        var light = primary * 3f
        var weight = 3f
        secondary?.let {
            light += it * 2f
            weight += 2f
        }
        tertiary?.let {
            light += it
            weight += 1f
        }
        return light / weight
    }

    /** Launcher text colours and shadow, for previews outside the launcher. */
    fun applyTextColour(view: TextView, light: Boolean, tinted: Boolean) {
        val colour = when {
            tinted -> view.context.getColor(if (light) LIGHT_TEXT else DARK_TEXT)
            light -> android.graphics.Color.WHITE
            else -> android.graphics.Color.BLACK
        }
        view.setTextColor(colour)
        view.setShadowLayer(4f, 0f, 2f, if (light) 0x80000000.toInt() else 0x80FFFFFF.toInt())
    }
}
