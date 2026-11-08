package io.github.pyrocklast.mot.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppEntry
import io.github.pyrocklast.mot.data.AppRepository
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
 * - choosing the app a swipe, the time or the date opens (hidden apps included), or
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
        binding.list.adapter = adapter
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
        val loadedApps = repo.apps.value
        val apps = loadedApps.orEmpty()
        val query = if (slot != null) Search.normalize(binding.search.text ?: "") else ""
        val rows = if (slot != null) {
            // Best matches first, as in the drawer
            val alreadyAssigned = if (slot == Slot.AddHome) prefs.assignedHomeApps.toSet() else emptySet()
            val matches = apps.filter { it.target !in alreadyAssigned && Search.matches(it.searchText, query) }
            if (query.isEmpty()) matches else matches.sortedBy { Search.rank(it.searchText, it.searchWords, query) }
        } else {
            apps.filter { it.appKey in listed }
        }
        adapter.submitList(rows)
        binding.empty.isVisible = rows.isEmpty()
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
            if (!slot.save(prefs, entry.target)) {
                Apps.toast(this, if (prefs.homeCapacity < 0)
                    R.string.home_capacity_refresh else R.string.home_apps_full)
                return
            }
            finish()
        } else {
            Apps.open(this, entry.target, view)
        }
    }

    private fun setHidden(entry: AppEntry, hide: Boolean) {
        hidden = if (hide) hidden + entry.appKey else hidden - entry.appKey
        prefs.hiddenApps = hidden
    }

    private inner class Adapter : ListAdapter<AppEntry, Adapter.Holder>(Diff) {
        inner class Holder(val binding: ItemSettingsAppBinding) : RecyclerView.ViewHolder(binding.root)

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
