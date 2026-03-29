package com.close.hook.ads.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.close.hook.ads.data.model.RuleSubscription
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleSubscriptionDao {

    @Query("SELECT * FROM rule_subscription ORDER BY is_builtin DESC, name ASC")
    fun getAllSubscriptions(): Flow<List<RuleSubscription>>

    @Query("SELECT * FROM rule_subscription WHERE enabled = 1 ORDER BY is_builtin DESC")
    suspend fun getEnabledSubscriptions(): List<RuleSubscription>

    @Query("SELECT * FROM rule_subscription WHERE id = :id")
    suspend fun getById(id: Long): RuleSubscription?

    @Query("SELECT * FROM rule_subscription WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): RuleSubscription?

    @Insert
    suspend fun insert(subscription: RuleSubscription): Long

    @Update
    suspend fun update(subscription: RuleSubscription)

    @Delete
    suspend fun delete(subscription: RuleSubscription)

    @Query("DELETE FROM rule_subscription WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE rule_subscription SET last_update = :timestamp, rule_count = :count, status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, timestamp: Long, count: Int, status: String)

    @Query("UPDATE rule_subscription SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT COUNT(*) FROM rule_subscription")
    suspend fun count(): Int
}
