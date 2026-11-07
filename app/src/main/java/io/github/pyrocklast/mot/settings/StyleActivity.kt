package io.github.pyrocklast.mot.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import androidx.core.view.isVisible
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.databinding.ActivityStyleBinding
import io.github.pyrocklast.mot.launcher.Slot
import io.github.pyrocklast.mot.util.DateText
import io.github.pyrocklast.mot.util.Look
import io.github.pyrocklast.mot.util.HomeFit
import kotlin.math.roundToInt

/**
 * Everything about one thing on the home screen, with a live preview on your wallpaper at the top:
 *
 * - App names: font, weight and size (in the drawer too).
 * - Time: shown or not, font, weight, size, and the app tapping it opens.
 * - Date: shown or not, format, font, weight, size, and the app tapping it opens.
 *
 * The time and date screens preview both, so you can see how they go together. Everything applies
 * as you change it; the home screen and drawer use it when you go back.
 */
class StyleActivity : SettingsScreen() {

    enum class Element { NAMES, TIME, DATE }

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var binding: ActivityStyleBinding
    private val element: Element by lazy { Element.entries[intent.getIntExtra(EXTRA_ELEMENT, 0)] }

    private var format: Row? = null
    private lateinit var font: Row
    private var opens: Row? = null

    /** Every row but the switch: greyed out while the time or date is hidden. */
    private val styleRows = ArrayList<Row>()

    // The three styles are the same three settings, saved in different places

    private var fontValue: Int
        get() = when (element) {
            Element.NAMES -> prefs.nameFont
            Element.TIME -> prefs.timeFont
            Element.DATE -> prefs.dateFont
        }
        set(value) {
            when (element) {
                Element.NAMES -> prefs.nameFont = value
                Element.TIME -> prefs.timeFont = value
                Element.DATE -> prefs.dateFont = value
            }
        }

    private var weightValue: Int
        get() = when (element) {
            Element.NAMES -> prefs.nameWeight
            Element.TIME -> prefs.timeWeight
            Element.DATE -> prefs.dateWeight
        }
        set(value) {
            when (element) {
                Element.NAMES -> prefs.nameWeight = value
                Element.TIME -> prefs.timeWeight = value
                Element.DATE -> prefs.dateWeight = value
            }
        }

    private var scaleValue: Float
        get() = when (element) {
            Element.NAMES -> prefs.appNameScale
            Element.TIME -> prefs.timeScale
            Element.DATE -> prefs.dateScale
        }
        set(value) {
            when (element) {
                Element.NAMES -> prefs.appNameScale = value
                Element.TIME -> prefs.timeScale = value
                Element.DATE -> prefs.dateScale = value
            }
        }

    // App names always show; the time and date can be switched off
    private val isShown: Boolean
        get() = when (element) {
            Element.NAMES -> true
            Element.TIME -> prefs.showTime
            Element.DATE -> prefs.showDate
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepository.get(this)
        binding = ActivityStyleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val title = when (element) {
            Element.NAMES -> R.string.app_names
            Element.TIME -> R.string.time
            Element.DATE -> R.string.date
        }
        setUpFrame(binding.root, binding.appBar, binding.scroll, getString(title))
        Rows(binding.rows).group { addRows(this) }
        setUpPreview()
    }

    private fun addRows(screen: Rows) {
        when (element) {
            Element.NAMES -> Unit
            Element.TIME -> screen.switch(R.string.show_time, prefs.showTime) {
                prefs.showTime = it
                update()
            }
            Element.DATE -> screen.switch(R.string.show_date, prefs.showDate) {
                prefs.showDate = it
                update()
            }
        }

        if (element == Element.DATE) {
            format = screen.item(R.string.format) { chooseFormat() }.also { styleRows += it }
        }

        font = screen.item(R.string.font) { chooseFont() }
        styleRows += font

        // Thin to bold, in five steps
        styleRows += screen.slider(R.string.weight, 0, Look.WEIGHTS.lastIndex, Look.WEIGHTS.indexOfNearest(weightValue)) {
            weightValue = Look.WEIGHTS[it]
            showPreview()
        }.apply { formatValue = { getString(weightLabel(Look.WEIGHTS[it])) } }

        // 50 to 150 % of the usual size, which itself follows Android's font size
        val sizeStep = ((scaleValue - 0.5f) * 10).roundToInt().coerceIn(0, 10)
        styleRows += screen.slider(R.string.size, 0, 10, sizeStep) {
            scaleValue = 0.5f + it / 10f
            showPreview()
        }.apply { formatValue = { percent(0.5f + it / 10f) } }

        opens = when (element) {
            Element.NAMES -> null
            Element.TIME -> screen.item(R.string.tap_time) {
                chooseApp(R.string.tap_time, R.string.default_clock_app, Slot.Clock)
            }
            Element.DATE -> screen.item(R.string.tap_date) {
                chooseApp(R.string.tap_date, R.string.default_calendar_app, Slot.Calendar)
            }
        }
        opens?.let { styleRows += it }
    }

    private fun setUpPreview() {
        val names = element == Element.NAMES
        binding.namePreview.isVisible = names
        binding.clockPreview.isVisible = !names
        if (names) {
            binding.namePreview.text = previewNames()
            binding.namePreview.gravity = Gravity.START
        }
        binding.clockPreview.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        val light = Look.lightText(this, prefs)
        listOf(binding.namePreview, binding.previewClock, binding.previewDate)
            .forEach { Look.applyTextColour(it, light, prefs.tintText) }
    }

    override fun onResume() {
        super.onResume()
        update()
    }

    /** Shows the current value of every row, and the preview. */
    private fun update() {
        val on = isShown
        styleRows.forEach { it.isEnabled = on }
        format?.summary = DateText.date(this, prefs.dateStyle)
        font.summary = getString(fontLabel(fontValue))
        opens?.summary = when (element) {
            Element.TIME -> appName(repo, prefs.clockApp, R.string.default_clock_app)
            else -> appName(repo, prefs.calendarApp, R.string.default_calendar_app)
        }
        showPreview()
    }

    // The time and date each in their own style, showing exactly what the home screen shows:
    // either of them switched off is gone from the preview too.
    private fun showPreview() {
        if (element == Element.NAMES) {
            binding.namePreview.typeface = Look.typeface(prefs.nameFont, prefs.nameWeight)
            binding.namePreview.setTextSize(TypedValue.COMPLEX_UNIT_PX, HomeFit.appNameSizePx(this, prefs))
            return
        }
        binding.previewClock.typeface = Look.typeface(prefs.timeFont, prefs.timeWeight)
        binding.previewClock.setTextSize(TypedValue.COMPLEX_UNIT_PX, HomeFit.timeSizePx(this, prefs))
        binding.previewDate.typeface = Look.typeface(prefs.dateFont, prefs.dateWeight)
        binding.previewDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, HomeFit.dateSizePx(this, prefs))
        binding.previewDate.text = DateText.date(this, prefs.dateStyle)

        binding.previewClock.isVisible = prefs.showTime
        binding.previewDate.isVisible = prefs.showDate
    }

    // Dialogs

    private fun chooseFont() {
        val labels = Array<CharSequence>(Look.FONTS.size) { getString(fontLabel(Look.FONTS[it])) }
        choiceDialog(R.string.font, labels, Look.FONTS.indexOf(fontValue)) { which ->
            fontValue = Look.FONTS[which]
            update()
        }
    }

    // Each format shown as today's date
    private fun chooseFormat() {
        val labels = Array<CharSequence>(DATE_FORMATS.size) { DateText.date(this, DATE_FORMATS[it]) }
        choiceDialog(R.string.date_format, labels, DATE_FORMATS.indexOf(prefs.dateStyle)) { which ->
            prefs.dateStyle = DATE_FORMATS[which]
            update()
        }
    }

    private fun chooseApp(title: Int, defaultLabel: Int, slot: Slot) {
        val current = if (slot == Slot.Clock) prefs.clockApp else prefs.calendarApp
        appChoiceDialog(prefs, title, defaultLabel, current, slot) { update() }
    }

    // Up to three of your home apps, or mot launcher's name when none is set
    private fun previewNames(): String {
        val names = prefs.assignedHomeApps.mapNotNull { repo.nameFor(it) }.take(3)
        return names.ifEmpty { listOf(getString(R.string.app_name)) }.joinToString("\n")
    }

    private fun IntArray.indexOfNearest(value: Int): Int = indices.minBy { kotlin.math.abs(this[it] - value) }

    companion object {
        private const val EXTRA_ELEMENT = "element"
        private val DATE_FORMATS = intArrayOf(Prefs.DATE_SHORT, Prefs.DATE_FULL, Prefs.DATE_NUMERIC)

        fun appNames(context: Context) = of(context, Element.NAMES)

        fun time(context: Context) = of(context, Element.TIME)

        fun date(context: Context) = of(context, Element.DATE)

        private fun of(context: Context, element: Element) =
            Intent(context, StyleActivity::class.java).putExtra(EXTRA_ELEMENT, element.ordinal)
    }
}
