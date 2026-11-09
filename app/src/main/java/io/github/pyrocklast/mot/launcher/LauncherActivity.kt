package io.github.pyrocklast.mot.launcher

import android.app.WallpaperColors
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
import androidx.core.view.doOnPreDraw
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

    /**
     * The wallpaper's colours, asked for once and then kept up to date by Android telling us when
     * they change, so coming back to the home screen doesn't have to ask again.
     */
    private var wallpaperColours: WallpaperColors? = null

    // A live wallpaper can report new colours all the time, and some phones report the same
    // ones twice: redraw only when they're really different
    private var wallpaperPalette: List<Int>? = null
    private var wallpaperId: Int = -1
    private fun systemWallpaperId(): Int = runCatching {
        WallpaperManager.getInstance(this).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    }.getOrDefault(-1)

    private fun palette(colours: WallpaperColors?): List<Int>? = colours?.let {
        listOf(
            it.primaryColor.toArgb(),
            it.secondaryColor?.toArgb() ?: -1,
            it.tertiaryColor?.toArgb() ?: -1,
            it.colorHints,
        )
    }

    private val wallpaperListener = WallpaperManager.OnColorsChangedListener { colours, which ->
        if (which and WallpaperManager.FLAG_SYSTEM == 0) return@OnColorsChangedListener
        wallpaperColours = colours
        if (prefs.textColour != Prefs.TEXT_MATCH_WALLPAPER && !prefs.tintText) return@OnColorsChangedListener
        val next = palette(colours)
        if (next == wallpaperPalette) return@OnColorsChangedListener
        wallpaperPalette = next
        // A new wallpaper gets a new ID, and the text colours that go with it. A live wallpaper's
        // colours can drift without one: only redraw for those if the text has to change.
        val nextId = systemWallpaperId()
        val wallpaperReplaced = nextId >= 0 && nextId != wallpaperId
        wallpaperId = nextId
        if (wallpaperReplaced || currentLook() != look) recreate()
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
        wallpaperColours = Look.wallpaperColours(this)
        wallpaperPalette = palette(wallpaperColours)
        wallpaperId = systemWallpaperId()
        look = currentLook()
        // Your text colour and weight, over the launcher theme
        val light = lightText()
        theme.applyStyle(if (light) R.style.ThemeOverlay_Mot_LightText else R.style.ThemeOverlay_Mot_DarkText, true)
        if (!prefs.tintText) {
            theme.applyStyle(if (light) R.style.ThemeOverlay_Mot_PlainLightText else R.style.ThemeOverlay_Mot_PlainDarkText, true)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = AppRepository.get(this)

        menu = AppMenu(this, prefs, repo)
        drawer = Drawer(this, binding, prefs, repo, menu, redrawHome = { home.render() }, openSettings = ::openSettings)
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

        // A new wallpaper can call for the other text colour
        WallpaperManager.getInstance(this).addOnColorsChangedListener(wallpaperListener, Handler(Looper.getMainLooper()))

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
        // Settings may have changed the look while you were away
        val light = lightText()
        if (currentLook(light) != look) {
            recreate()
            return
        }
        drawer.onLauncherResumed()
        applySystemBars(light)
        home.render()
        home.startTicking()
        // A crash while laying out or drawing comes after this, so the crashes so far only stop
        // counting once a whole frame has made it to the screen
        binding.root.doOnPreDraw { view ->
            view.viewTreeObserver.registerFrameCommitCallback { prefs.crashesSurvived() }
        }
    }

    override fun onPause() {
        home.stopTicking()
        super.onPause()
    }

    override fun onDestroy() {
        WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperListener)
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

    private fun lightText(): Boolean = Look.lightText(this, prefs, wallpaperColours)

    // The drawer's rows are made with these, so a change redraws the screen
    private fun currentLook(light: Boolean = lightText()): String =
        listOf(light, prefs.tintText, prefs.nameFont, prefs.nameWeight, prefs.appNameScale)
            .joinToString()

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
