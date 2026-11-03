package io.github.pyrocklast.mot.settings

import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.StringRes
import io.github.pyrocklast.mot.R

/**
 * A row with a slider that applies as you move it, for whole numbers like the number of home
 * apps. [onValueChange] saves the value; setting [value] only moves the slider.
 */
class SliderRow(view: View) : Row(view) {
    private val slider: SeekBar = view.findViewById(R.id.slider)
    private val valueText: TextView = view.findViewById(R.id.sliderValue)

    var formatValue: (Int) -> String = { it.toString() }
        set(value) {
            field = value
            showValue()
        }

    var onValueChange: (Int) -> Unit = {}

    var value: Int
        get() = slider.progress
        set(newValue) {
            slider.progress = newValue.coerceIn(slider.min, slider.max)
            showValue()
        }

    init {
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                showValue()
                if (fromUser) onValueChange(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    override fun setTitle(@StringRes title: Int) {
        super.setTitle(title)
        slider.contentDescription = this.title
    }

    fun setRange(from: Int, to: Int) {
        slider.min = from
        slider.max = to
    }

    // The value beside the title, and for TalkBack, e.g. "Weight, Light" rather than a percentage
    private fun showValue() {
        val text = formatValue(slider.progress)
        valueText.text = text
        slider.stateDescription = text
    }
}
