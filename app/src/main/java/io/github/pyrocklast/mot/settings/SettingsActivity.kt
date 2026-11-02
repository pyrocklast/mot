package io.github.pyrocklast.mot.settings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.databinding.ActivitySettingsBinding

/**
 * mot launcher's settings: an ordinary Android settings screen. It follows the phone's dark mode, font
 * and Material You colours; the launcher's own look applies to the home screen and drawer only.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The first-run tips on the home screen have done their job
        Prefs(this).settingsOpened = true

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            binding.appBar.updatePadding(top = top)
            insets
        }
        if (savedInstanceState == null) supportFragmentManager.commit { replace(R.id.content, SettingsFragment()) }
    }

    /** Each screen names itself in the title bar. */
    fun setScreenTitle(title: CharSequence) {
        binding.collapsingToolbar.title = title
        binding.appBar.setExpanded(true, false)
    }

    /** Opens another settings screen (choosing an app, hidden apps); Back returns here. */
    fun show(fragment: Fragment) {
        supportFragmentManager.commit {
            setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            replace(R.id.content, fragment)
            addToBackStack(null)
        }
    }
}
