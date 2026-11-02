package io.github.pyrocklast.mot.settings

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.pyrocklast.mot.R

/**
 * A settings row with its own switch, like Android's Wi-Fi row. Tapping the text runs the click
 * listener (here: choose the app), while the switch turns the feature on or off. Not persisted:
 * [onCheckedChange] saves the state.
 */
class SplitSwitchPreference(context: Context) : Preference(context) {

    var onCheckedChange: (Boolean) -> Unit = {}

    private var checked = false

    /** Setting this redraws the row; flipping the switch updates it directly instead. */
    var isChecked: Boolean
        get() = checked
        set(value) {
            if (value == checked) return
            checked = value
            notifyChanged()
        }

    init {
        layoutResource = R.layout.preference_split_switch
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // Only the text area and the switch react, each with its own ripple
        holder.itemView.setOnClickListener(null)
        holder.itemView.isClickable = false
        holder.itemView.isFocusable = false

        val target = holder.findViewById(R.id.splitTarget)
        target.setOnClickListener { onPreferenceClickListener?.onPreferenceClick(this) }
        target.isEnabled = isEnabled

        val switch = holder.findViewById(R.id.splitSwitch) as MaterialSwitch
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = checked
        switch.isEnabled = isEnabled
        // TalkBack reads the row's name with the switch, e.g. "Swipe left, switch, on"
        switch.contentDescription = title
        switch.setOnCheckedChangeListener { _, isOn ->
            if (isOn == checked) return@setOnCheckedChangeListener
            // Not through isChecked: redrawing the row would cut the switch's animation short
            checked = isOn
            onCheckedChange(isOn)
        }
    }
}
