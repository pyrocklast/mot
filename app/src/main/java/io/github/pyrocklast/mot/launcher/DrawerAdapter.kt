package io.github.pyrocklast.mot.launcher

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppEntry
import io.github.pyrocklast.mot.data.Profile
import io.github.pyrocklast.mot.databinding.ItemAppBinding

/** A row in the drawer: an app or shortcut, or the Private Space header. */
sealed interface DrawerRow {
    data class App(val entry: AppEntry) : DrawerRow
    data class PrivateSpace(val locked: Boolean) : DrawerRow
}

class DrawerAdapter(
    private val nameSizePx: Float,
    private val gravity: Int,
    private val onClick: (DrawerRow, View) -> Unit,
    private val onLongClick: (DrawerRow, View) -> Unit,
) : ListAdapter<DrawerRow, DrawerAdapter.Holder>(Diff) {

    class Holder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.name.setTextSize(TypedValue.COMPLEX_UNIT_PX, nameSizePx)
        binding.name.gravity = gravity
        val holder = Holder(binding)
        binding.name.setOnClickListener { view -> holder.row()?.let { onClick(it, view) } }
        binding.name.setOnLongClickListener { view ->
            holder.row()?.let { onLongClick(it, view) }
            true
        }
        return holder
    }

    private fun Holder.row(): DrawerRow? = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { getItem(it) }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val context = holder.itemView.context
        val name = holder.binding.name
        when (val row = getItem(position)) {
            is DrawerRow.App -> {
                val entry = row.entry
                name.text = if (entry.isNew) "${entry.label} ✦" else entry.label
                name.alpha = 1f
                val work = entry.profile == Profile.WORK
                holder.binding.profileDot.isVisible = work
                name.contentDescription = when {
                    work -> context.getString(R.string.work_app, entry.label)
                    entry.isNew -> context.getString(R.string.new_app, entry.label)
                    else -> null
                }
            }
            is DrawerRow.PrivateSpace -> {
                name.text = context.getString(R.string.private_space)
                name.alpha = if (row.locked) 0.6f else 0.8f
                holder.binding.profileDot.isVisible = false
                name.contentDescription = context.getString(
                    if (row.locked) R.string.private_space_locked else R.string.private_space_unlocked
                )
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DrawerRow>() {
        override fun areItemsTheSame(old: DrawerRow, new: DrawerRow): Boolean = when {
            old is DrawerRow.App && new is DrawerRow.App -> old.entry.key == new.entry.key
            else -> old is DrawerRow.PrivateSpace && new is DrawerRow.PrivateSpace
        }

        override fun areContentsTheSame(old: DrawerRow, new: DrawerRow): Boolean = old == new
    }
}
