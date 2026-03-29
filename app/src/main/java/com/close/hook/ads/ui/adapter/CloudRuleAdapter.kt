package com.close.hook.ads.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.close.hook.ads.R
import com.close.hook.ads.data.model.RuleSubscription
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloudRuleAdapter(
    private val context: Context,
    private val onUpdate: (RuleSubscription) -> Unit,
    private val onToggle: (RuleSubscription) -> Unit,
    private val onDelete: (RuleSubscription) -> Unit,
    private val onIntervalChange: (RuleSubscription, Int) -> Unit
) : ListAdapter<RuleSubscription, CloudRuleAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RuleSubscription>() {
            override fun areItemsTheSame(old: RuleSubscription, new: RuleSubscription) = old.id == new.id
            override fun areContentsTheSame(old: RuleSubscription, new: RuleSubscription) = old == new
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.sub_name)
        val url: TextView = view.findViewById(R.id.sub_url)
        val status: TextView = view.findViewById(R.id.sub_status)
        val ruleCount: TextView = view.findViewById(R.id.sub_rule_count)
        val lastUpdate: TextView = view.findViewById(R.id.sub_last_update)
        val interval: TextView = view.findViewById(R.id.sub_interval)
        val enabledSwitch: Switch = view.findViewById(R.id.sub_enabled_switch)
        val btnUpdate: MaterialButton = view.findViewById(R.id.btn_update)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cloud_rule_subscription, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.name.text = item.name
        holder.url.text = item.url
        holder.ruleCount.text = context.getString(R.string.rule_count, item.ruleCount)

        holder.status.text = when (item.status) {
            RuleSubscription.STATUS_UPDATING -> context.getString(R.string.updating)
            RuleSubscription.STATUS_SUCCESS -> context.getString(R.string.sync_success)
            RuleSubscription.STATUS_FAILED -> context.getString(R.string.sync_failed)
            else -> context.getString(R.string.pending_update)
        }

        holder.lastUpdate.text = if (item.lastUpdate > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            context.getString(R.string.last_update_time, sdf.format(Date(item.lastUpdate)))
        } else {
            context.getString(R.string.never_updated)
        }

        holder.interval.text = context.getString(R.string.interval_hours, item.updateIntervalHours)

        holder.enabledSwitch.setOnCheckedChangeListener(null)
        holder.enabledSwitch.isChecked = item.enabled
        holder.enabledSwitch.setOnCheckedChangeListener { _, _ -> onToggle(item) }

        holder.btnUpdate.setOnClickListener { onUpdate(item) }

        holder.btnDelete.visibility = if (item.isBuiltin) View.GONE else View.VISIBLE
        holder.btnDelete.setOnClickListener { onDelete(item) }

        holder.interval.setOnClickListener {
            val intervals = arrayOf(6, 12, 24, 48, 72)
            val labels = intervals.map { "$it h" }.toTypedArray()
            val currentIndex = intervals.indexOf(item.updateIntervalHours).coerceAtLeast(0)

            android.app.AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.select_update_interval))
                .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                    onIntervalChange(item, intervals[which])
                    dialog.dismiss()
                }
                .show()
        }
    }
}
