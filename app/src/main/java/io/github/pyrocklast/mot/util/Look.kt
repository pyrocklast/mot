package io.github.pyrocklast.mot.util

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.content.res.Configuration
import io.github.pyrocklast.mot.data.Prefs

/**
 * The launcher's text colour: light or dark, as you set it or as the wallpaper suits. The font is
 * fixed (themes.xml): Android's own sans serif, light for the time and app names, regular for the
 * date, at sizes that follow Android's font size.
 */
object Look {

    // The text takes the lightest and darkest of the colours Android works out from the wallpaper,
    // rather than plain white and black, so the words belong to the picture behind them. The
    // launcher's own text uses these through launcherTextColor (themes.xml).
    private const val LIGHT_TEXT = android.R.color.system_neutral1_50
    private const val DARK_TEXT = android.R.color.system_neutral1_900

    /**
     * The wallpaper's colours, or null when it has none to give. Asking takes Android a moment,
     * so the launcher asks once and keeps them.
     */
    fun wallpaperColours(context: Context): WallpaperColors? = runCatching {
        // Some phones' and live wallpapers' colours can't be read: carry on without them
        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
    }.getOrNull()

    /** Light text for dark wallpapers, dark text for light ones: as set, or as the wallpaper suits. */
    fun lightText(context: Context, prefs: Prefs, colours: WallpaperColors?): Boolean = when (prefs.textColour) {
        Prefs.TEXT_DARK -> false
        Prefs.TEXT_LIGHT -> true
        // With a wallpaper that says nothing about itself, the phone's dark mode is the next best
        // guess at the light in the room
        else -> colours?.let { suitsDarkText(context, it, prefs.tintText) }?.not() ?: darkMode(context)
    }

    private fun darkMode(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /**
     * Whether dark text reads on the wallpaper. Android hints at this for a picture, but errs
     * towards light text — a wallpaper with one dark corner gets no hint — and a live wallpaper
     * usually sends no hint either. So the hint is taken when it says dark text reads, and
     * otherwise the colours themselves decide.
     */
    private fun suitsDarkText(context: Context, colours: WallpaperColors, tinted: Boolean): Boolean {
        if (colours.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) return true
        // Whichever of the two stands out more against the wallpaper. Dark text wins sooner than
        // it looks like it should: against a mid orange, dark is three times the contrast of light.
        val wallpaper = brightness(colours)
        return contrast(wallpaper, textLuminance(context, tinted, light = false)) >
            contrast(wallpaper, textLuminance(context, tinted, light = true))
    }

    /** How light the text itself is: the wallpaper's lightest or darkest colour, or plain white or black. */
    private fun textLuminance(context: Context, tinted: Boolean, light: Boolean): Float = when {
        tinted -> Color.valueOf(context.getColor(if (light) LIGHT_TEXT else DARK_TEXT)).luminance()
        light -> 1f
        else -> 0f
    }

    /**
     * How far apart two brightnesses are to the eye, from 1 (the same) to 21 (black on white).
     * The 0.05 is the light a screen reflects even at its blackest, which is why dark text beats
     * light text on anything but a genuinely dark wallpaper.
     */
    internal fun contrast(one: Float, other: Float): Float =
        (maxOf(one, other) + 0.05f) / (minOf(one, other) + 0.05f)

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
}
