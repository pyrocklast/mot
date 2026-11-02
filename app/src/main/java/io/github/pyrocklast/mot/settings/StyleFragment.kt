package io.github.pyrocklast.mot.settings

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.databinding.FragmentStyleBinding
import io.github.pyrocklast.mot.util.DateText
import io.github.pyrocklast.mot.util.Look
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Font, weight and size, for app names or for the clock and date, with a live preview on your
 * wallpaper at the top. Everything applies as you change it; the home screen and drawer use it
 * when you go back.
 */
class StyleFragment : PreferenceFragmentCompat() {

    private lateinit var prefs: Prefs
    private var preview: FragmentStyleBinding? = null
    private var dialog: AlertDialog? = null
    private val forClock: Boolean by lazy { arguments?.getBoolean(ARG_CLOCK) == true }

    private lateinit var font: Preference

    // The two styles are the same three settings, saved in different places
    private var fontValue: Int
        get() = if (forClock) prefs.clockFont else prefs.nameFont
        set(value) {
            if (forClock) prefs.clockFont = value else prefs.nameFont = value
        }

    private var weightValue: Int
        get() = if (forClock) prefs.clockWeight else prefs.nameWeight
        set(value) {
            if (forClock) prefs.clockWeight = value else prefs.nameWeight = value
        }

    private var scaleValue: Float
        get() = if (forClock) prefs.clockScale else prefs.appNameScale
        set(value) {
            if (forClock) prefs.clockScale = value else prefs.appNameScale = value
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        prefs = Prefs(requireContext())
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        font = Preference(context).apply {
            setTitle(R.string.font)
            isIconSpaceReserved = false
            isPersistent = false
            setOnPreferenceClickListener {
                chooseFont()
                true
            }
        }
        screen.addPreference(font)

        // Thin to bold, in five steps
        screen.addPreference(SliderPreference(context).apply {
            setTitle(R.string.weight)
            isIconSpaceReserved = false
            valueFrom = 0
            valueTo = Look.WEIGHTS.lastIndex
            value = Look.WEIGHTS.indexOfNearest(weightValue)
            formatValue = { getString(weightLabel(Look.WEIGHTS[it])) }
            onValueChange = {
                weightValue = Look.WEIGHTS[it]
                showPreview()
            }
        })

        // 50 to 150 % of the usual size, which itself follows Android's font size
        screen.addPreference(SliderPreference(context).apply {
            setTitle(R.string.size)
            isIconSpaceReserved = false
            valueFrom = 0
            valueTo = 10
            value = ((scaleValue - 0.5f) * 10).roundToInt().coerceIn(0, 10)
            formatValue = { percent(0.5f + it / 10f) }
            onValueChange = {
                scaleValue = 0.5f + it / 10f
                showPreview()
            }
        })

        preferenceScreen = screen
    }

    // The settings list goes under the preview
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val list = super.onCreateView(inflater, container, savedInstanceState)
        val binding = FragmentStyleBinding.inflate(inflater, container, false)
        binding.listContainer.addView(
            list,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        preview = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.clipToPadding = false
        listView.padForNavigationBar()
        val binding = preview ?: return
        binding.namePreview.isVisible = !forClock
        binding.clockPreview.isVisible = forClock
        if (forClock) {
            binding.previewClock.isVisible = prefs.showTime
            binding.previewDate.isVisible = prefs.showDate
            binding.previewDate.text = DateText.date(requireContext(), prefs.dateStyle)
        } else {
            binding.namePreview.text = previewNames()
            binding.namePreview.gravity = prefs.homeAlignment
        }
        binding.clockPreview.gravity = prefs.homeAlignment or Gravity.CENTER_VERTICAL
        val light = Look.lightText(requireContext(), prefs)
        listOf(binding.namePreview, binding.previewClock, binding.previewDate).forEach { Look.applyTextColour(it, light) }
        showPreview()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as SettingsActivity).setScreenTitle(
            getString(if (forClock) R.string.clock_and_date else R.string.app_names)
        )
        font.summary = getString(fontLabel(fontValue))
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        preview = null
        super.onDestroyView()
    }

    private fun showPreview() {
        val binding = preview ?: return
        val typeface = Look.typeface(fontValue, weightValue)
        val scale = scaleValue
        val res = resources
        binding.namePreview.typeface = typeface
        binding.namePreview.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.app_name_size) * scale)
        binding.previewClock.typeface = typeface
        binding.previewClock.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.clock_size) * scale)
        binding.previewDate.typeface = typeface
        binding.previewDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.date_size) * scale)
    }

    private fun chooseFont() {
        val labels = Array<CharSequence>(Look.FONTS.size) { getString(fontLabel(Look.FONTS[it])) }
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.font)
            .setSingleChoiceItems(labels, Look.FONTS.indexOf(fontValue)) { choice, which ->
                choice.dismiss()
                fontValue = Look.FONTS[which]
                font.summary = labels[which]
                showPreview()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // Up to three of your home apps, or mot.'s name when none is set
    private fun previewNames(): String {
        val repo = AppRepository.get(requireContext())
        val names = prefs.homeApps.take(prefs.homeAppCount).filterNotNull().mapNotNull { repo.nameFor(it) }.take(3)
        return names.ifEmpty { listOf(getString(R.string.app_name)) }.joinToString("\n")
    }

    private fun IntArray.indexOfNearest(value: Int): Int = indices.minBy { kotlin.math.abs(this[it] - value) }

    private fun percent(scale: Float): String = NumberFormat.getPercentInstance().format(scale.toDouble())

    companion object {
        private const val ARG_CLOCK = "clock"

        fun appNames() = StyleFragment()

        fun clockAndDate() = StyleFragment().apply { arguments = bundleOf(ARG_CLOCK to true) }

        @StringRes
        fun fontLabel(font: Int) = when (font) {
            Prefs.FONT_SERIF -> R.string.font_serif
            Prefs.FONT_MONO -> R.string.font_mono
            Prefs.FONT_CONDENSED -> R.string.font_condensed
            else -> R.string.font_sans
        }

        @StringRes
        fun weightLabel(weight: Int) = when {
            weight < 200 -> R.string.weight_thin
            weight < 350 -> R.string.weight_light
            weight < 450 -> R.string.weight_regular
            weight < 600 -> R.string.weight_medium
            else -> R.string.weight_bold
        }
    }
}
