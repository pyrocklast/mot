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

/**
 * Functional time/date settings only: independent visibility, date format and tap action.
 * Font family, weight and scale are no longer customizable: the preview uses the same
 * fixed typography as the home. Legacy preferences are retained, not destructively reset.
 */
class StyleActivity : SettingsScreen() {
    // Preserve old ordinal values for activities restored by Android after an update.
    enum class Element { NAMES, TIME, DATE }

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var binding: ActivityStyleBinding
    private val element: Element by lazy {
        when (intent.getIntExtra(EXTRA_ELEMENT, Element.TIME.ordinal)) {
            Element.DATE.ordinal -> Element.DATE
            else -> Element.TIME
        }
    }

    private lateinit var shown: SwitchRow
    private var format: Row? = null
    private lateinit var opens: Row

    private val isShown: Boolean get() = when (element) {
        Element.DATE -> prefs.showDate
        else -> prefs.showTime
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepository.get(this)
        binding = ActivityStyleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpFrame(
            binding.root, binding.appBar, binding.scroll,
            getString(if (element == Element.DATE) R.string.date else R.string.time),
        )
        Rows(binding.rows).group {
            shown = if (element == Element.DATE) switch(R.string.show_date, prefs.showDate) {
                prefs.showDate = it
                update()
            } else switch(R.string.show_time, prefs.showTime) {
                prefs.showTime = it
                update()
            }
            if (element == Element.DATE) {
                format = item(R.string.format) { chooseFormat() }
            }
            opens = item(if (element == Element.DATE) R.string.tap_date else R.string.tap_time) {
                chooseApp()
            }
        }
        binding.namePreview.isVisible = false
        binding.clockPreview.isVisible = true
        binding.clockPreview.gravity = Gravity.START
        // The fixed home typography follows the system's font scale.
        binding.previewClock.typeface = Look.typeface(Prefs.FONT_SANS, 300)
        binding.previewClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f)
        binding.previewDate.typeface = Look.typeface(Prefs.FONT_SANS, 400)
        binding.previewDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    }

    override fun onResume() {
        super.onResume()
        update()
    }

    private fun update() {
        shown.isChecked = isShown
        format?.summary = DateText.date(this, prefs.dateStyle)
        format?.isEnabled = isShown
        opens.isEnabled = isShown
        opens.summary = if (element == Element.DATE) {
            appName(repo, prefs.calendarApp, R.string.default_calendar_app)
        } else {
            appName(repo, prefs.clockApp, R.string.default_clock_app)
        }

        val light = Look.lightText(this, prefs)
        listOf(binding.previewClock, binding.previewDate).forEach {
            Look.applyTextColour(it, light, prefs.tintText)
        }
        binding.previewClock.isVisible = prefs.showTime
        binding.previewDate.isVisible = prefs.showDate
        binding.previewDate.text = DateText.date(this, prefs.dateStyle)
    }

    private fun chooseFormat() {
        val labels = Array<CharSequence>(DATE_FORMATS.size) { DateText.date(this, DATE_FORMATS[it]) }
        choiceDialog(R.string.date_format, labels, DATE_FORMATS.indexOf(prefs.dateStyle)) { which ->
            prefs.dateStyle = DATE_FORMATS[which]
            update()
        }
    }

    private fun chooseApp() {
        val isDate = element == Element.DATE
        val slot = if (isDate) Slot.Calendar else Slot.Clock
        val current = if (isDate) prefs.calendarApp else prefs.clockApp
        appChoiceDialog(
            prefs,
            if (isDate) R.string.tap_date else R.string.tap_time,
            if (isDate) R.string.default_calendar_app else R.string.default_clock_app,
            current,
            slot,
        ) { update() }
    }

    companion object {
        private const val EXTRA_ELEMENT = "element"
        private val DATE_FORMATS = intArrayOf(Prefs.DATE_SHORT, Prefs.DATE_FULL, Prefs.DATE_NUMERIC)

        fun time(context: Context) = of(context, Element.TIME)
        fun date(context: Context) = of(context, Element.DATE)

        private fun of(context: Context, element: Element) =
            Intent(context, StyleActivity::class.java).putExtra(EXTRA_ELEMENT, element.ordinal)
    }
}
