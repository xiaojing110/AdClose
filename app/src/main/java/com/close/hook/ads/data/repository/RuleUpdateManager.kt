package com.close.hook.ads.data.repository

import android.content.Context
import android.util.Log
import com.close.hook.ads.data.DataSource
import com.close.hook.ads.data.database.UrlDatabase
import com.close.hook.ads.data.model.RuleSubscription
import com.close.hook.ads.data.model.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RuleUpdateManager(context: Context) {

    companion object {
        private const val TAG = "RuleUpdateManager"

        @Volatile
        private var INSTANCE: RuleUpdateManager? = null

        fun getInstance(context: Context): RuleUpdateManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RuleUpdateManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val appContext = context.applicationContext
    private val urlDatabase = UrlDatabase.getDatabase(context)
    private val subscriptionDao = urlDatabase.ruleSubscriptionDao
    private val dataSource = DataSource.getDataSource(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _updateProgress = MutableStateFlow<UpdateProgress?>(null)
    val updateProgress: StateFlow<UpdateProgress?> = _updateProgress

    data class UpdateProgress(
        val subscriptionName: String,
        val current: Int,
        val total: Int,
        val message: String
    )

    data class UpdateResult(
        val success: Boolean,
        val rulesAdded: Int,
        val rulesSkipped: Int,
        val errorMessage: String? = null
    )

    /**
     * Initialize default subscriptions on first launch
     */
    suspend fun initDefaultSubscriptions() = withContext(Dispatchers.IO) {
        if (subscriptionDao.count() == 0) {
            RuleSubscription.defaultSubscriptions().forEach { sub ->
                subscriptionDao.insert(sub)
            }
            Log.d(TAG, "Default subscriptions initialized")
        }
    }

    /**
     * Fetch and parse rules from a subscription URL
     */
    suspend fun fetchRules(subscription: RuleSubscription): UpdateResult = withContext(Dispatchers.IO) {
        try {
            subscriptionDao.updateStatus(subscription.id, System.currentTimeMillis(), subscription.ruleCount, RuleSubscription.STATUS_UPDATING)

            val request = Request.Builder()
                .url(subscription.url)
                .header("User-Agent", "AdClose-Mod/4.2.5")
                .header("Cache-Control", "no-cache")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val error = "HTTP ${response.code}: ${response.message}"
                subscriptionDao.updateStatus(subscription.id, System.currentTimeMillis(), subscription.ruleCount, RuleSubscription.STATUS_FAILED)
                return@withContext UpdateResult(false, 0, 0, error)
            }

            val body = response.body?.string() ?: ""
            val parsedRules = parseRuleText(body)

            if (parsedRules.isEmpty()) {
                subscriptionDao.updateStatus(subscription.id, System.currentTimeMillis(), 0, RuleSubscription.STATUS_SUCCESS)
                return@withContext UpdateResult(true, 0, 0)
            }

            // Remove old rules from this subscription (identified by URL prefix in type)
            val oldRules = dataSource.getAllUrls()
            val subTag = "[sub:${subscription.id}]"
            val toRemove = oldRules.filter { it.url.startsWith(subTag) }
            if (toRemove.isNotEmpty()) {
                dataSource.removeList(toRemove)
            }

            // Add new rules with subscription tag
            val newUrls = parsedRules.map { rule ->
                Url(type = rule.first, url = "$subTag${rule.second}")
            }

            // Deduplicate against existing rules
            val existingSet = dataSource.getAllUrls().map { "${it.type}|${it.url}" }.toSet()
            val toAdd = newUrls.filter { "${it.type}|${it.url}" !in existingSet }

            if (toAdd.isNotEmpty()) {
                dataSource.addListUrl(toAdd)
            }

            subscriptionDao.updateStatus(subscription.id, System.currentTimeMillis(), toAdd.size, RuleSubscription.STATUS_SUCCESS)

            Log.d(TAG, "Updated subscription '${subscription.name}': ${toAdd.size} rules added")
            UpdateResult(true, toAdd.size, newUrls.size - toAdd.size)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch rules for '${subscription.name}'", e)
            subscriptionDao.updateStatus(subscription.id, System.currentTimeMillis(), subscription.ruleCount, RuleSubscription.STATUS_FAILED)
            UpdateResult(false, 0, 0, e.message ?: "Unknown error")
        }
    }

    /**
     * Parse rule text in AdClose format (type, url per line)
     */
    private fun parseRuleText(text: String): List<Pair<String, String>> {
        val rules = mutableListOf<Pair<String, String>>()
        val lines = text.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val parts = trimmed.split(",", limit = 2).map { it.trim() }
            if (parts.size == 2) {
                val type = parts[0]
                val value = parts[1]
                if (type.equals("Domain", ignoreCase = true) ||
                    type.equals("URL", ignoreCase = true) ||
                    type.equals("KeyWord", ignoreCase = true)) {
                    rules.add(Pair(type, value))
                }
            }
        }
        return rules
    }

    /**
     * Update all enabled subscriptions that need refreshing
     */
    suspend fun updateAllIfNeeded() = withContext(Dispatchers.IO) {
        val subscriptions = subscriptionDao.getEnabledSubscriptions()
        val now = System.currentTimeMillis()

        for (sub in subscriptions) {
            val intervalMs = sub.updateIntervalHours * 3600 * 1000L
            if (now - sub.lastUpdate >= intervalMs || sub.lastUpdate == 0L) {
                _updateProgress.value = UpdateProgress(sub.name, 0, 1, "正在更新 ${sub.name}...")
                fetchRules(sub)
                _updateProgress.value = null
            }
        }
    }

    /**
     * Force update a specific subscription
     */
    suspend fun forceUpdate(subscriptionId: Long): UpdateResult {
        val sub = subscriptionDao.getById(subscriptionId) ?: return UpdateResult(false, 0, 0, "订阅不存在")
        _updateProgress.value = UpdateProgress(sub.name, 0, 1, "正在更新 ${sub.name}...")
        val result = fetchRules(sub)
        _updateProgress.value = null
        return result
    }

    /**
     * Force update all subscriptions
     */
    suspend fun forceUpdateAll(): Map<Long, UpdateResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<Long, UpdateResult>()
        val subscriptions = subscriptionDao.getEnabledSubscriptions()

        for ((index, sub) in subscriptions.withIndex()) {
            _updateProgress.value = UpdateProgress(
                sub.name, index + 1, subscriptions.size,
                "正在更新 (${index + 1}/${subscriptions.size}): ${sub.name}..."
            )
            results[sub.id] = fetchRules(sub)
        }
        _updateProgress.value = null
        results
    }

    /**
     * Remove all rules belonging to a subscription
     */
    suspend fun removeRulesForSubscription(subscriptionId: Long) = withContext(Dispatchers.IO) {
        val allRules = dataSource.getAllUrls()
        val subTag = "[sub:$subscriptionId]"
        val toRemove = allRules.filter { it.url.startsWith(subTag) }
        if (toRemove.isNotEmpty()) {
            dataSource.removeList(toRemove)
        }
    }

    /**
     * Add a custom subscription
     */
    suspend fun addSubscription(name: String, url: String, intervalHours: Int = 12): Long = withContext(Dispatchers.IO) {
        val existing = subscriptionDao.getByUrl(url)
        if (existing != null) return@withContext -1L

        val sub = RuleSubscription(
            name = name,
            url = url,
            enabled = true,
            isBuiltin = false,
            updateIntervalHours = intervalHours
        )
        subscriptionDao.insert(sub)
    }

    /**
     * Delete a subscription and its rules
     */
    suspend fun deleteSubscription(subscription: RuleSubscription) = withContext(Dispatchers.IO) {
        removeRulesForSubscription(subscription.id)
        subscriptionDao.delete(subscription)
    }
}
