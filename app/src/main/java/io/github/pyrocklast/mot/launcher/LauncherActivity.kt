package io.github.pyrocklast.mot.launcher

import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.databinding.ActivityLauncherBinding
import io.github.pyrocklast.mot.settings.SettingsActivity
import io.github.pyrocklast.mot.util.Apps
import io.github.pyrocklast.mot.util.Look
import kotlinx.coroutines.launch

/**
 * mot launcher's launcher: the home screen, with the app drawer as a panel over it. Settings is a
 * separate, ordinary activity, so it follows the phone's theme while this one uses yours.
 */
class LauncherActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var binding: ActivityLauncherBinding
    private lateinit var home: HomeScreen
    private lateinit var drawer: Drawer
    private lateinit var menu: AppMenu

    /** The look this screen was drawn with; when settings change it, the screen is redrawn. */
    private var look = ""

    // A new wallpaper can call for the other text colour (with "Match the wallpaper")
    private val wallpaperColours = WallpaperManager.OnColorsChangedListener { _, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0 && currentLook() != look) recreate()
    }

    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        home.render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        // Crashing over and over while drawing leaves a phone with no home screen, so after a few
        // the look goes back to its defaults, which is where such a crash comes from
        val lookWasReset = prefs.resetLookAfterCrashes()
        look = currentLook()
        // Your text colour and weight, over the launcher theme
        theme.applyStyle(if (lightText()) R.style.ThemeOverlay_Mot_LightText else R.style.ThemeOverlay_Mot_DarkText, true)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = AppRepository.get(this)

        menu = AppMenu(this, prefs, repo)
        drawer = Drawer(this, binding, prefs, repo, menu, onChosen = { home.render() }, openSettings = ::openSettings)
        home = HomeScreen(
            context = this,
            binding = binding,
            prefs = prefs,
            repo = repo,
            menu = menu,
            openDrawer = { slot -> drawer.open(slot) },
            openSettings = ::openSettings,
            requestHomeRole = ::requestHomeRole,
        )

        // The home screen draws edge to edge, and keeps the clock and the names clear of the bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.home) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            home.applyInsets(top, bottom)
            insets
        }

        // With "Match the wallpaper", a new wallpaper can call for the other text colour
        WallpaperManager.getInstance(this).addOnColorsChangedListener(wallpaperColours, Handler(Looper.getMainLooper()))

        // Back leaves Private Space, or closes the drawer, which follows the back gesture as you
        // swipe (Android 15's predictive back). On the home screen it does nothing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackProgressed(backEvent: BackEventCompat) = drawer.backProgress(backEvent.progress)
            override fun handleOnBackCancelled() = drawer.backCancelled()
            override fun handleOnBackPressed() = drawer.back()
        })

        if (lookWasReset) Apps.toast(this, R.string.look_reset)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repo.apps.collect {
                        home.render()
                        drawer.update()
                    }
                }
                launch { repo.privateSpace.collect { drawer.update() } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Checking the wallpaper's colours takes a moment: once per return to the home screen
        val light = lightText()
        if (currentLook(light) != look) {
            recreate()
            return
        }
        drawer.onLauncherResumed()
        applySystemBars(light)
        home.render()
        home.startTicking()
        // Drawn without crashing: whatever went before doesn't count towards a reset
        prefs.crashesSurvived()
    }

    override fun onPause() {
        home.stopTicking()
        super.onPause()
    }

    override fun onDestroy() {
        WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperColours)
        super.onDestroy()
    }

    override fun onStop() {
        // Coming back to mot launcher always starts on the home screen, except after unlocking Private Space
        if (!drawer.keepOpenWhileAway) {
            drawer.close(animate = false)
            menu.dismiss()
        }
        home.cancelDrag()
        super.onStop()
    }

    // The Home button, pressed while mot launcher is already showing: back to a bare home screen
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        menu.dismiss()
        home.cancelDrag()
        drawer.close()
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun requestHomeRole() {
        val request = Apps.homeRoleRequest(this)
        if (request != null) homeRoleRequest.launch(request) else Apps.openHomeSettings(this)
    }

    private fun lightText(): Boolean = Look.lightText(this, prefs)

    // The drawer's rows are made with these, so a change redraws the screen
    private fun currentLook(light: Boolean = lightText()): String =
        listOf(light, prefs.nameFont, prefs.nameWeight, prefs.appNameScale, prefs.drawerAlignment).joinToString()

    // Status bar shown or hidden as set, and status and navigation bar icons that match the text
    private fun applySystemBars(light: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (prefs.showStatusBar) show(WindowInsetsCompat.Type.statusBars())
            else hide(WindowInsetsCompat.Type.statusBars())
            isAppearanceLightStatusBars = !light
            isAppearanceLightNavigationBars = !light
        }
    }
}
