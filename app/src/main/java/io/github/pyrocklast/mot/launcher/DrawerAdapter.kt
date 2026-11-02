package io.github.pyrocklast.mot.launcher

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppEntry
import io.github.pyrocklast.mot.data.Profile
import io.github.pyrocklast.mot.databinding.ItemAppBinding
import io.github.pyrocklast.mot.databinding.ItemDrawerActionBinding

/** A row in the drawer: an app or shortcut, or one of the small Private Space rows. */
sealed interface DrawerRow {
    data class App(val entry: AppEntry) : DrawerRow

    /** At the end of your apps: opens Private Space, unlocking it first if needed. */
    data class OpenPrivateSpace(val locked: Boolean) : DrawerRow

    /** At the end of Private Space's apps. */
    data object LockPrivateSpace : DrawerRow
}

class DrawerAdapter(
    private val nameSizePx: Float,
    private val nameTypeface: Typeface,
    private val gravity: Int,
    private val onClick: (DrawerRow, View) -> Unit,
    private val onLongClick: (DrawerRow, View) -> Unit,
) : ListAdapter<DrawerRow, RecyclerView.ViewHolder>(Diff) {

    private class AppHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)
    private class ActionHolder(val binding: ItemDrawerActionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) = if (getItem(position) is DrawerRow.App) TYPE_APP else TYPE_ACTION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val holder: RecyclerView.ViewHolder
        val clickable: TextView
        if (viewType == TYPE_APP) {
            val binding = ItemAppBinding.inflate(inflater, parent, false)
            binding.name.setTextSize(TypedValue.COMPLEX_UNIT_PX, nameSizePx)
            binding.name.typeface = nameTypeface
            binding.name.gravity = gravity
            holder = AppHolder(binding)
            clickable = binding.name
        } else {
            val binding = ItemDrawerActionBinding.inflate(inflater, parent, false)
            // The padlock sits next to the words, wherever the drawer is aligned
            (binding.action.layoutParams as FrameLayout.LayoutParams).gravity = gravity
            holder = ActionHolder(binding)
            clickable = binding.action
        }
        clickable.setOnClickListener { view -> holder.row()?.let { onClick(it, view) } }
        clickable.setOnLongClickListener { view ->
            holder.row()?.let { onLongClick(it, view) }
            true
        }
        return holder
    }

    private fun RecyclerView.ViewHolder.row(): DrawerRow? =
        bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { getItem(it) }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context
        when (val row = getItem(position)) {
            is DrawerRow.App -> {
                val binding = (holder as AppHolder).binding
                val entry = row.entry
                binding.name.text = if (entry.isNew) "${entry.label} ✦" else entry.label
                val work = entry.profile == Profile.WORK
                binding.profileDot.isVisible = work
                binding.name.contentDescription = when {
                    work -> context.getString(R.string.work_app, entry.label)
                    entry.isNew -> context.getString(R.string.new_app, entry.label)
                    else -> null
                }
            }
            is DrawerRow.OpenPrivateSpace -> {
                val action = (holder as ActionHolder).binding.action
                action.setText(R.string.private_space)
                action.contentDescription = context.getString(
                    if (row.locked) R.string.private_space_locked else R.string.private_space_unlocked
                )
            }
            DrawerRow.LockPrivateSpace -> {
                val action = (holder as ActionHolder).binding.action
                action.setText(R.string.lock)
                action.contentDescription = context.getString(R.string.lock_private_space)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DrawerRow>() {
        override fun areItemsTheSame(old: DrawerRow, new: DrawerRow): Boolean = when {
            old is DrawerRow.App && new is DrawerRow.App -> old.entry.key == new.entry.key
            else -> old::class == new::class
        }

        override fun areContentsTheSame(old: DrawerRow, new: DrawerRow): Boolean = old == new
    }

    private companion object {
        const val TYPE_APP = 0
        const val TYPE_ACTION = 1
    }
}
