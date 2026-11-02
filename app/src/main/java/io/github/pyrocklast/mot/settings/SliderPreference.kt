package io.github.pyrocklast.mot.settings

import android.content.Context
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import io.github.pyrocklast.mot.R
import kotlin.math.roundToInt

/**
 * A settings row with a slider that applies as you move it, for whole numbers like the number of
 * home apps. Not persisted: [onValueChange] saves the value, and [value] is set from there.
 */
class SliderPreference(context: Context) : Preference(context) {

    var valueFrom = 0
    var valueTo = 10
    var formatValue: (Int) -> String = { it.toString() }
    var onValueChange: (Int) -> Unit = {}

    private var current = 0

    /** Setting this redraws the row; dragging the slider updates the row directly instead. */
    var value: Int
        get() = current
        set(newValue) {
            if (newValue == current) return
            current = newValue
            notifyChanged()
        }

    init {
        layoutResource = R.layout.preference_slider
        isPersistent = false
        // The row itself doesn't react to taps; the slider does
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val slider = holder.findViewById(R.id.slider) as Slider
        val valueText = holder.findViewById(R.id.sliderValue) as TextView

        slider.clearOnChangeListeners()
        slider.valueFrom = valueFrom.toFloat()
        slider.valueTo = valueTo.toFloat()
        slider.stepSize = 1f
        slider.value = current.coerceIn(valueFrom, valueTo).toFloat()
        slider.contentDescription = title
        slider.setLabelFormatter { formatValue(it.roundToInt()) }
        valueText.text = formatValue(current)

        slider.addOnChangeListener { _, sliderValue, fromUser ->
            val newValue = sliderValue.roundToInt()
            if (!fromUser || newValue == current) return@addOnChangeListener
            // Update the row in place: redrawing it mid-drag would interrupt the drag
            current = newValue
            valueText.text = formatValue(newValue)
            onValueChange(newValue)
        }
    }
}
