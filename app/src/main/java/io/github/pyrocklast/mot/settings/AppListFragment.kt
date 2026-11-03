package io.github.pyrocklast.mot.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
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
import io.github.pyrocklast.mot.databinding.FragmentAppListBinding
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
class AppListFragment : Fragment() {

    private var binding: FragmentAppListBinding? = null
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private val slot: Slot? by lazy { Slot.decode(arguments?.getString(ARG_SLOT)) }

    // The hidden apps list keeps the apps it opened with, so a switch turned off by mistake can
    // be turned back on
    private var listed: Set<String> = emptySet()

    /** Which apps are hidden now, read once rather than for every row drawn. */
    private var hidden: Set<String> = emptySet()

    private val adapter = Adapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        prefs = Prefs(requireContext())
        repo = AppRepository.get(requireContext())
        listed = prefs.hiddenApps
        hidden = listed
        return FragmentAppListBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = binding ?: return
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.list.padForNavigationBar()
        binding.searchLayout.isVisible = slot != null
        binding.search.doAfterTextChanged { update() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.apps.collect { update() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as SettingsActivity).setScreenTitle(
            getString(if (slot != null) R.string.choose_an_app else R.string.hidden_apps)
        )
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun update() {
        val binding = binding ?: return
        hidden = prefs.hiddenApps
        val apps = repo.apps.value.orEmpty()
        val rows = if (slot != null) {
            // Best matches first, as in the drawer
            val query = Search.normalize(binding.search.text ?: "")
            val matches = apps.filter { Search.matches(it.searchText, query) }
            if (query.isEmpty()) matches else matches.sortedBy { Search.rank(it.searchText, it.searchWords, query) }
        } else {
            apps.filter { it.appKey in listed }
        }
        adapter.submitList(rows)
        binding.empty.isVisible = slot == null && rows.isEmpty() && repo.apps.value != null
    }

    private fun onRowClick(entry: AppEntry, view: View) {
        val slot = slot
        if (slot != null) {
            slot.save(prefs, entry.target)
            parentFragmentManager.popBackStack()
        } else {
            Apps.open(requireContext(), entry.target, view)
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
        private const val ARG_SLOT = "slot"

        fun chooseApp(slot: Slot) = AppListFragment().apply { arguments = bundleOf(ARG_SLOT to slot.encode()) }

        fun hiddenApps() = AppListFragment()
    }
}
