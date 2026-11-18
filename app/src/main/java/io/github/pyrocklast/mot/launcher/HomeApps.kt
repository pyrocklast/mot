package io.github.pyrocklast.mot.launcher

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import io.github.pyrocklast.mot.util.HomeFit

/**
 * The home apps' names, in the columns [HomeFit.names] worked out: one under the time on a tall
 * screen, or several beside it on a screen turned on its side.
 *
 * The children stay in the order the names are in, whatever shape they end up in, so a keyboard,
 * TalkBack and the drag that reorders them all see the same list as the home screen does. The
 * last of them is "All apps", when there are more names than the screen can hold.
 */
class HomeApps @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    private var plan: HomeFit.Names? = null
    private var row = 0

    /** Lays the names out as [plan] says, each row [row] tall. */
    fun place(plan: HomeFit.Names, row: Int) {
        if (this.plan == plan && this.row == row) return
        this.plan = plan
        this.row = row
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        // A name has its column's width to live in, and is cut short if it needs more
        val childWidth = MeasureSpec.makeMeasureSpec(plan?.columnWidth ?: width, MeasureSpec.AT_MOST)
        val childHeight = MeasureSpec.makeMeasureSpec(row.coerceAtLeast(0), MeasureSpec.EXACTLY)
        forEachShown { it.measure(childWidth, childHeight) }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val plan = plan ?: return
        val waiting = ArrayList<View>(childCount).also { list -> forEachShown { list += it } }
        plan.counts.forEachIndexed { column, count ->
            val rows = count + if (plan.overflow && column == plan.counts.lastIndex) 1 else 0
            val x = columnLeft(plan, column)
            repeat(rows) { place(waiting.removeFirstOrNull() ?: return, x, plan.top + it * row) }
        }
        // A name with no slot: nowhere rather than wherever it was last time. The home screen
        // hides what the plan has no room for, so this is only ever belt and braces
        waiting.forEach { it.layout(0, 0, 0, 0) }
    }

    private inline fun forEachShown(action: (View) -> Unit) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.isVisible) action(child)
        }
    }

    private fun place(view: View, x: Int, y: Int) {
        view.layout(x, y, x + view.measuredWidth, y + view.measuredHeight)
    }

    // Columns run from the side the names start on, so they run right to left in a right-to-left
    // language, as the names themselves do
    private fun columnLeft(plan: HomeFit.Names, column: Int): Int =
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            width - plan.left - (column + 1) * plan.columnWidth
        } else {
            plan.left + column * plan.columnWidth
        }
}
