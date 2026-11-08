package io.github.pyrocklast.mot.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.github.pyrocklast.mot.databinding.SettingsAppBarBinding

/**
 * What every settings screen shares: drawn edge to edge, a title bar with Back under the status
 * bar, a list that scrolls under the navigation bar, and at most one dialog open at a time.
 *
 * The main settings and app picker use Android's own activity navigation and
 * follow the phone's dark mode, font and colours;
 * the launcher's own look applies to the home screen and drawer only.
 */
abstract class SettingsScreen : ComponentActivity() {

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    /** Call after setContentView. [scrolling] is the screen's list, which goes under the navigation bar. */
    protected fun setUpFrame(root: View, appBar: SettingsAppBarBinding, scrolling: View, title: CharSequence) {
        appBar.toolbar.title = title
        appBar.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            appBar.root.updatePadding(top = top)
            insets
        }
        scrolling.padForNavigationBar()
    }

    /** Shows [newDialog], closing any other first. */
    fun showDialog(newDialog: AlertDialog) {
        dialog?.dismiss()
        dialog = newDialog
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }
}
