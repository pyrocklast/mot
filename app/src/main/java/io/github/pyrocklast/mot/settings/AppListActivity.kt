package io.github.pyrocklast.mot.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppEntry
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.data.Profile
import io.github.pyrocklast.mot.databinding.ActivityAppListBinding
import io.github.pyrocklast.mot.databinding.ItemSettingsAppBinding
import io.github.pyrocklast.mot.launcher.Slot
import io.github.pyrocklast.mot.util.Apps
import io.github.pyrocklast.mot.util.Search
import kotlinx.coroutines.launch

/**
 * A list of apps in the settings' own style, for two jobs:
 * - choosing the app a swipe, the time or the date opens (hidden apps included), with Android's
 *   own app first, and a tick on the one in use, or
 * - hidden apps: tap one to open it, or turn its switch off to show it in the drawer again.
 */
class AppListActivity : SettingsScreen() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private val slot: Slot? by lazy { Slot.decode(intent.getStringExtra(EXTRA_SLOT)) }

    // The hidden apps list keeps the apps it opened with, so a switch turned off by mistake can
    // be turned back on
    private var listed: Set<String> = emptySet()

    /** Which apps are hidden now, read once rather than for every row drawn. */
    private var hidden: Set<String> = emptySet()

    /** What choosing no app means here, e.g. "Default clock app": null where there's no such choice. */
    @get:StringRes
    private val defaultLabel: Int?
        get() = when (slot) {
            Slot.Clock -> R.string.default_clock_app
            Slot.Calendar -> R.string.default_calendar_app
            Slot.SwipeLeft -> R.string.default_camera_app
            Slot.SwipeRight -> R.string.default_phone_app
            else -> null
        }

    /** The app in use now, which gets the tick; null for Android's own. */
    private var chosen: AppTarget? = null

    private val defaultRow = DefaultRow()
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepository.get(this)
        listed = prefs.hiddenApps
        hidden = listed

        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val title = getString(when (slot) {
            null -> R.string.hidden_apps
            Slot.Clock -> R.string.choose_clock_app
            Slot.Calendar -> R.string.choose_calendar_app
            Slot.SwipeLeft -> R.string.choose_left_swipe_app
            Slot.SwipeRight -> R.string.choose_right_swipe_app
            Slot.AddHome -> R.string.add_to_home
            is Slot.Home -> R.string.change_app
        })
        setUpFrame(binding.root, binding.appBar, binding.list, title)

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = ConcatAdapter(defaultRow, adapter)
        binding.search.isVisible = slot != null
        binding.search.doAfterTextChanged { update() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.apps.collect { update() }
            }
        }
    }

    private fun update() {
        hidden = prefs.hiddenApps
        chosen = when (slot) {
            Slot.Clock -> prefs.clockApp
            Slot.Calendar -> prefs.calendarApp
            Slot.SwipeLeft -> prefs.swipeLeftApp
            Slot.SwipeRight -> prefs.swipeRightApp
            else -> null
        }
        val loadedApps = repo.apps.value
        val apps = loadedApps.orEmpty()
        val query = if (slot != null) Search.normalize(binding.search.text ?: "") else ""
        val rows = if (slot != null) {
            // Best matches first, as in the drawer
            val matches = apps.filter { Search.matches(it.searchText, query) }
            if (query.isEmpty()) matches else matches.sortedBy { Search.rank(it.searchText, it.searchWords, query) }
        } else {
            apps.filter { it.appKey in listed }
        }
        // Android's own app comes first, but not among search results: it isn't an app's name
        defaultRow.isShown = defaultLabel != null && query.isEmpty()
        adapter.submitList(rows)
        binding.empty.isVisible = rows.isEmpty() && !defaultRow.isShown
        if (rows.isEmpty()) {
            binding.empty.setText(when {
                loadedApps == null -> R.string.loading_apps
                slot != null && query.isNotEmpty() -> R.string.no_search_results
                slot != null -> R.string.no_apps_available
                else -> R.string.no_hidden_apps
            })
        }
    }

    private fun onRowClick(entry: AppEntry, view: View) {
        val slot = slot
        if (slot != null) {
            slot.save(prefs, entry.target)
            finish()
        } else {
            Apps.open(this, entry.target, view)
        }
    }

    private fun setHidden(entry: AppEntry, hide: Boolean) {
        hidden = if (hide) hidden + entry.appKey else hidden - entry.appKey
        prefs.hiddenApps = hidden
    }

    private class Holder(val binding: ItemSettingsAppBinding) : RecyclerView.ViewHolder(binding.root)

    // A tick on the choice in use, which TalkBack says too
    private fun tick(row: ItemSettingsAppBinding, on: Boolean) {
        row.chosen.isVisible = on
        ViewCompat.setStateDescription(row.root, if (on) getString(R.string.selected) else null)
    }

    /** The first row when choosing an app: Android's own, e.g. "Default clock app". */
    private inner class DefaultRow : RecyclerView.Adapter<Holder>() {
        var isShown = false
            set(value) {
                if (value == field) return
                field = value
                if (value) notifyItemInserted(0) else notifyItemRemoved(0)
            }

        override fun getItemCount() = if (isShown) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = ItemSettingsAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            row.root.setOnClickListener {
                slot?.save(prefs, null)
                finish()
            }
            return Holder(row)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.binding.name.setText(defaultLabel ?: return)
            holder.binding.detail.isVisible = false
            holder.binding.hidden.isVisible = false
            tick(holder.binding, chosen == null)
        }
    }

    private inner class Adapter : ListAdapter<AppEntry, Holder>(Diff) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = ItemSettingsAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val holder = Holder(row)
            row.root.setOnClickListener { view ->
                holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { onRowClick(getItem(it), view) }
            }
            return holder
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = getItem(position)
            holder.binding.name.text = entry.label
            // Tells apart the same app in two profiles, and apps from shortcuts
            val detail = listOfNotNull(
                getString(R.string.shortcut).takeIf { entry.target.isShortcut },
                when (entry.profile) {
                    Profile.WORK -> getString(R.string.work_profile)
                    Profile.PRIVATE -> getString(R.string.private_space)
                    Profile.PERSONAL -> null
                },
            ).joinToString(" · ")
            holder.binding.detail.text = detail
            holder.binding.detail.isVisible = detail.isNotEmpty()
            val switch = holder.binding.hidden
            switch.isVisible = slot == null
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = entry.appKey in hidden
            switch.contentDescription = getString(R.string.hide_app, entry.label)
            switch.setOnCheckedChangeListener { _, checked -> setHidden(entry, checked) }
            tick(holder.binding, slot != null && entry.target == chosen)
        }
    }

    private object Diff : DiffUtil.ItemCallback<AppEntry>() {
        override fun areItemsTheSame(old: AppEntry, new: AppEntry) = old.key == new.key
        override fun areContentsTheSame(old: AppEntry, new: AppEntry) = old == new
    }

    companion object {
        private const val EXTRA_SLOT = "slot"

        fun chooseApp(context: Context, slot: Slot): Intent =
            Intent(context, AppListActivity::class.java).putExtra(EXTRA_SLOT, slot.encode())

        fun hiddenApps(context: Context) = Intent(context, AppListActivity::class.java)
    }
}
