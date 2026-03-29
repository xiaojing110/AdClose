package com.close.hook.ads.data.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "rule_subscription")
data class RuleSubscription(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,

    @ColumnInfo(name = "name")
    var name: String,

    @ColumnInfo(name = "url")
    var url: String,

    @ColumnInfo(name = "enabled")
    var enabled: Boolean = true,

    @ColumnInfo(name = "is_builtin")
    var isBuiltin: Boolean = false,

    @ColumnInfo(name = "last_update")
    var lastUpdate: Long = 0L,

    @ColumnInfo(name = "update_interval_hours")
    var updateIntervalHours: Int = 12,

    @ColumnInfo(name = "rule_count")
    var ruleCount: Int = 0,

    @ColumnInfo(name = "status")
    var status: String = STATUS_IDLE
) : Parcelable {

    companion object {
        const val STATUS_IDLE = "idle"
        const val STATUS_UPDATING = "updating"
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"

        // Built-in default subscriptions
        fun defaultSubscriptions(): List<RuleSubscription> = listOf(
            RuleSubscription(
                name = "秋风广告规则",
                url = "https://raw.githubusercontent.com/TG-Twilight/AWAvenue-Ads-Rule/main/Filters/AWAvenue-Ads-Rule-AdClose.rule",
                enabled = true,
                isBuiltin = true,
                updateIntervalHours = 12
            )
        )
    }
}
