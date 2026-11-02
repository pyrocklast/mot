package io.github.pyrocklast.mot.settings

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Lets a settings list scroll under the navigation bar, with its last row still reachable. */
fun View.padForNavigationBar(extraDp: Int = 16) {
    val extra = (extraDp * resources.displayMetrics.density).toInt()
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        view.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom + extra)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
