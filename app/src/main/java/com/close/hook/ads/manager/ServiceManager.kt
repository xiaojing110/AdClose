package com.close.hook.ads.manager

import android.util.Log
import com.close.hook.ads.preference.HookPrefs
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object ServiceManager {

    private const val TAG = "ServiceManager"
    private const val RECONNECT_BASE_DELAY_MS = 2000L
    private const val RECONNECT_MAX_DELAY_MS = 30000L
    private const val MAX_RETRY_COUNT = 10

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val connectionState = _connectionState.asStateFlow()

    private val isInitialized = AtomicBoolean(false)
    private val retryCount = AtomicLong(0)
    private val lastBindTime = AtomicLong(0)

    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    @JvmStatic
    val service: XposedService?
        get() = (connectionState.value as? ConnectionState.Connected)?.service

    @JvmStatic
    val isModuleActivated: Boolean
        get() {
            val state = connectionState.value
            return state is ConnectionState.Connected || state is ConnectionState.ServiceDied
        }

    @JvmStatic
    val isServiceConnected: Boolean
        get() = connectionState.value is ConnectionState.Connected

    @JvmStatic
    val activationStatus: ActivationStatus
        get() = when (val state = connectionState.value) {
            is ConnectionState.Connected -> ActivationStatus.ACTIVE
            is ConnectionState.ServiceDied -> ActivationStatus.HOOKS_ACTIVE_RECONNECTING
            is ConnectionState.Disconnected -> {
                if (retryCount.get() > 0) ActivationStatus.RECONNECTING
                else ActivationStatus.DISCONNECTED
            }
            is ConnectionState.Connecting -> ActivationStatus.CONNECTING
        }

    fun init() {
        if (!isInitialized.compareAndSet(false, true)) {
            return
        }

        val listener = object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(boundService: XposedService) {
                var isFirstConnection = false
                _connectionState.update { currentState ->
                    if (currentState is ConnectionState.Connected) {
                        Log.w(TAG, "Already connected to ${currentState.service.frameworkName}. Ignoring ${boundService.frameworkName}.")
                        currentState
                    } else {
                        isFirstConnection = true
                        ConnectionState.Connected(boundService)
                    }
                }

                if (isFirstConnection) {
                    retryCount.set(0)
                    lastBindTime.set(System.currentTimeMillis())
                    HookPrefs.invalidateCaches()
                    Log.i(TAG, "LSPosed service connected: ${boundService.frameworkName} v${boundService.frameworkVersion}")
                }
            }

            override fun onServiceDied(deadService: XposedService) {
                val wasConnected: Boolean
                _connectionState.update { currentState ->
                    wasConnected = currentState is ConnectionState.Connected && currentState.service === deadService
                    if (wasConnected) {
                        Log.w(TAG, "LSPosed service (${deadService.frameworkName}) died. Hooks remain active.")
                        ConnectionState.ServiceDied(deadService.frameworkName)
                    } else {
                        currentState
                    }
                }
                scheduleReconnect()
            }
        }

        XposedServiceHelper.registerListener(listener)
        Log.i(TAG, "ServiceManager initialized and listener registered.")
    }

    private fun scheduleReconnect() {
        val currentRetry = retryCount.incrementAndGet()
        if (currentRetry > MAX_RETRY_COUNT) {
            Log.w(TAG, "Max retry count ($MAX_RETRY_COUNT) reached. Giving up reconnection.")
            _connectionState.update { ConnectionState.Disconnected }
            return
        }

        val delayMs = minOf(
            RECONNECT_BASE_DELAY_MS * (1 shl (currentRetry - 1).toInt()),
            RECONNECT_MAX_DELAY_MS
        )

        Log.i(TAG, "Scheduling reconnect attempt #$currentRetry in ${delayMs}ms")

        scope.launch {
            delay(delayMs)

            val currentState = connectionState.value
            if (currentState is ConnectionState.Connected) {
                retryCount.set(0)
                return@launch
            }

            Log.d(TAG, "Reconnect attempt #$currentRetry")
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(boundService: XposedService) {
                    _connectionState.update { ConnectionState.Connected(boundService) }
                    retryCount.set(0)
                    lastBindTime.set(System.currentTimeMillis())
                    HookPrefs.invalidateCaches()
                    Log.i(TAG, "Reconnected to LSPosed: ${boundService.frameworkName}")
                }

                override fun onServiceDied(deadService: XposedService) {
                    Log.w(TAG, "Reconnected service died again.")
                    _connectionState.update { ConnectionState.ServiceDied(deadService.frameworkName) }
                    scheduleReconnect()
                }
            })
        }
    }

    fun forceReconnect() {
        retryCount.set(0)
        scheduleReconnect()
    }
}

sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data class Connected(val service: XposedService) : ConnectionState
    data class ServiceDied(val frameworkName: String) : ConnectionState
    data object Disconnected : ConnectionState
}

enum class ActivationStatus {
    CONNECTING,
    ACTIVE,
    HOOKS_ACTIVE_RECONNECTING,
    RECONNECTING,
    DISCONNECTED
}
