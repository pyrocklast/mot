package io.github.pyrocklast.mot.settings

import android.view.View
import android.widget.Switch
import androidx.annotation.StringRes
import io.github.pyrocklast.mot.R

/**
 * A row with its own switch, like Android's Wi-Fi row. Tapping the text runs [onClick] (here:
 * choose the app), while the switch turns the feature on or off and calls [onCheckedChange].
 */
class SplitSwitchRow(view: View) : Row(view) {
    private val target: View = view.findViewById(R.id.splitTarget)
    private val switch: Switch = view.findViewById(R.id.splitSwitch)

    var onClick: () -> Unit = {}
    var onCheckedChange: (Boolean) -> Unit = {}

    // Setting isChecked isn't a change of yours, so it doesn't call onCheckedChange
    private var settingChecked = false

    var isChecked: Boolean
        get() = switch.isChecked
        set(value) {
            settingChecked = true
            switch.isChecked = value
            settingChecked = false
        }

    init {
        target.setOnClickListener { onClick() }
        // A tap or a slide of the switch
        switch.setOnCheckedChangeListener { _, isOn -> if (!settingChecked) onCheckedChange(isOn) }
    }

    // TalkBack reads the row's name with the switch, e.g. "Swipe left, switch, on"
    override fun setTitle(@StringRes title: Int) {
        super.setTitle(title)
        switch.contentDescription = this.title
    }
}
