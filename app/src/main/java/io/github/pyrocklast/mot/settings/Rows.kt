package io.github.pyrocklast.mot.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.Prefs

// Settings rows, laid out one under the other in a screen's column. No row saves itself: each
// shows a value from Prefs and writes changes back, so Prefs stays the one source of truth.

/** Adds rows to a settings screen's column, in order. */
class Rows(private val column: ViewGroup, private val prefs: Prefs) {

    private val context: Context get() = column.context

    private fun inflate(@LayoutRes layout: Int): View =
        LayoutInflater.from(context).inflate(layout, column, false).also { column.addView(it) }

    /**
     * A group: rows on their own rounded surface, added in [rows], under an optional heading
     * with an optional note. A titled group collapses when its heading is tapped, and stays as
     * the person left it.
     */
    fun group(@StringRes title: Int = 0, @StringRes summary: Int = 0, rows: Rows.() -> Unit) {
        val group = inflate(R.layout.settings_group)
        val heading = Row(group.findViewById(R.id.groupHeading))
        val groupRows: View = group.findViewById(R.id.groupRows)
        if (title != 0) heading.setTitle(title) else heading.isVisible = false
        // So TalkBack can move heading by heading through a long screen
        ViewCompat.setAccessibilityHeading(heading.view, title != 0)
        if (summary != 0) heading.summary = context.getString(summary)
        if (title != 0) collapsible(title, heading.view, groupRows)
        Rows(groupRows as ViewGroup, prefs).rows()
    }

    /**
     * Makes a heading collapse [rows]. The collapse hides only the rows, so the heading stays
     * to tap open again; the state is kept by the heading's name, which doesn't change between
     * builds the way a resource id can.
     */
    private fun collapsible(@StringRes title: Int, heading: View, rows: View) {
        val name = context.resources.getResourceEntryName(title)
        val toggle: TextView = heading.findViewById(R.id.groupToggle)
        var collapsed = name in prefs.collapsedGroups

        fun show() {
            rows.isVisible = !collapsed
            toggle.setText(if (collapsed) R.string.group_show else R.string.group_hide)
        }

        heading.isClickable = true
        heading.setOnClickListener {
            collapsed = !collapsed
            prefs.setGroupCollapsed(name, collapsed)
            show()
            // Tell TalkBack the section's rows have changed, so it reads the new state
            heading.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
        // TalkBack reads the heading as a button, and its state: "Expanded" or "Collapsed"
        heading.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isClickable = true
                info.stateDescription = context.getString(if (collapsed) R.string.group_collapsed else R.string.group_expanded)
            }
        }
        show()
    }

    fun item(@StringRes title: Int, onClick: () -> Unit): Row {
        val row = Row(inflate(R.layout.settings_row))
        row.setTitle(title)
        row.view.setOnClickListener { onClick() }
        return row
    }

    fun switch(@StringRes title: Int, checked: Boolean, onChange: (Boolean) -> Unit): SwitchRow {
        val row = SwitchRow(inflate(R.layout.settings_row_switch))
        row.setTitle(title)
        row.isChecked = checked
        row.onChange = onChange
        return row
    }

    fun slider(@StringRes title: Int, from: Int, to: Int, value: Int, onChange: (Int) -> Unit): SliderRow {
        val row = SliderRow(inflate(R.layout.settings_row_slider))
        row.setTitle(title)
        row.setRange(from, to)
        row.value = value
        row.onValueChange = onChange
        return row
    }

    fun splitSwitch(@StringRes title: Int, checked: Boolean, onChange: (Boolean) -> Unit, onClick: () -> Unit): SplitSwitchRow {
        val row = SplitSwitchRow(inflate(R.layout.settings_row_split_switch))
        row.setTitle(title)
        row.isChecked = checked
        row.onCheckedChange = onChange
        row.onClick = onClick
        return row
    }
}

/** A row's title and summary; the summary line shows only when there is one. */
open class Row(val view: View) {
    private val titleView: TextView = view.findViewById(android.R.id.title)
    private val summaryView: TextView? = view.findViewById(android.R.id.summary)

    val title: CharSequence get() = titleView.text

    open fun setTitle(@StringRes title: Int) = titleView.setText(title)

    open var summary: CharSequence?
        get() = summaryView?.text?.takeIf { summaryView.isVisible }
        set(value) {
            val summaryView = summaryView ?: return
            summaryView.text = value
            summaryView.isVisible = !value.isNullOrEmpty()
        }

    /** A group whose rows are all hidden is hidden too, surface and heading with it. */
    var isVisible: Boolean
        get() = view.isVisible
        set(value) {
            view.isVisible = value
            val groupRows = view.parent as? ViewGroup ?: return
            if (groupRows.id != R.id.groupRows) return
            val anyShown = (0 until groupRows.childCount).any { groupRows.getChildAt(it).isVisible }
            (groupRows.parent as? View)?.isVisible = anyShown
        }

    /** A disabled row is greyed out (by its text colours' disabled state) and takes no taps. */
    var isEnabled: Boolean
        get() = view.isEnabled
        set(value) = view.setEnabledWithChildren(value)

    private fun View.setEnabledWithChildren(enabled: Boolean) {
        isEnabled = enabled
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).setEnabledWithChildren(enabled)
    }
}

/**
 * A row with a switch: tapping anywhere on it flips the switch. [summaryOff] shows under the
 * title while it's off.
 */
class SwitchRow(view: View) : Row(view) {
    private val switch: Switch = view.findViewById(R.id.rowSwitch)

    var onChange: (Boolean) -> Unit = {}

    var summaryOff: CharSequence? = null
        set(value) {
            field = value
            showSummary()
        }

    // Setting isChecked isn't a change of yours, so it doesn't call onChange
    private var settingChecked = false

    var isChecked: Boolean
        get() = switch.isChecked
        set(value) {
            settingChecked = true
            switch.isChecked = value
            settingChecked = false
            showSummary()
        }

    init {
        view.setOnClickListener { switch.toggle() }
        // A tap on the row, or a slide of the switch itself
        switch.setOnCheckedChangeListener { _, isOn ->
            showSummary()
            if (!settingChecked) onChange(isOn)
        }
        // TalkBack reads the row as a switch, e.g. "Show keyboard, switch, on"
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Switch::class.java.name
                info.isCheckable = true
                info.isChecked = switch.isChecked
            }
        }
    }

    private fun showSummary() {
        summary = if (switch.isChecked) null else summaryOff
    }
}
