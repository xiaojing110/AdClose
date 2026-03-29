package com.close.hook.ads.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.close.hook.ads.data.database.UrlDatabase
import com.close.hook.ads.data.model.RuleSubscription
import com.close.hook.ads.data.repository.RuleUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CloudRuleViewModel(application: Application) : AndroidViewModel(application) {

    private val updateManager = RuleUpdateManager.getInstance(application)
    private val subscriptionDao = UrlDatabase.getDatabase(application).ruleSubscriptionDao

    val subscriptions: StateFlow<List<RuleSubscription>> = subscriptionDao.getAllSubscriptions()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val updateProgress = updateManager.updateProgress

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun initDefaults() {
        viewModelScope.launch(Dispatchers.IO) {
            updateManager.initDefaultSubscriptions()
        }
    }

    fun forceUpdate(subscriptionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = updateManager.forceUpdate(subscriptionId)
            _toastMessage.value = if (result.success) {
                "更新成功: 新增 ${result.rulesAdded} 条规则"
            } else {
                "更新失败: ${result.errorMessage}"
            }
        }
    }

    fun forceUpdateAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val results = updateManager.forceUpdateAll()
            val totalAdded = results.values.sumOf { it.rulesAdded }
            val failed = results.values.count { !it.success }
            _toastMessage.value = if (failed == 0) {
                "全部更新完成: 共新增 $totalAdded 条规则"
            } else {
                "更新完成: 新增 $totalAdded 条, $failed 个失败"
            }
        }
    }

    fun toggleEnabled(subscription: RuleSubscription) {
        viewModelScope.launch(Dispatchers.IO) {
            subscriptionDao.setEnabled(subscription.id, !subscription.enabled)
        }
    }

    fun addSubscription(name: String, url: String, intervalHours: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = updateManager.addSubscription(name, url, intervalHours)
            if (id == -1L) {
                _toastMessage.value = "该订阅链接已存在"
            } else {
                _toastMessage.value = "订阅添加成功，正在更新规则..."
                updateManager.forceUpdate(id)
            }
        }
    }

    fun deleteSubscription(subscription: RuleSubscription) {
        viewModelScope.launch(Dispatchers.IO) {
            updateManager.deleteSubscription(subscription)
            _toastMessage.value = "已删除订阅: ${subscription.name}"
        }
    }

    fun updateInterval(subscription: RuleSubscription, intervalHours: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = subscription.copy(updateIntervalHours = intervalHours)
            subscriptionDao.update(updated)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
