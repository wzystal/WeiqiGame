package com.example.weiqigame.presentation

import android.app.Application
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.AndroidViewModel
import com.example.weiqigame.data.remote.NsdHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 大厅 ViewModel
 *
 * 管理局域网房间发现和连接的状态
 */
class LobbyViewModel(application: Application) : AndroidViewModel(application) {

    private val nsdHelper = NsdHelper(application.applicationContext)

    // 发现的设备列表
    val discoveredServices: StateFlow<List<NsdServiceInfo>> = nsdHelper.discoveredServices

    // 当前状态
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 设备 ID（用于生成服务名）
    private val deviceId: String
        get() = android.provider.Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "Unknown"

    /**
     * 创建房间（注册 NSD 服务）
     */
    fun createRoom(onRegistered: () -> Unit = {}) {
        nsdHelper.registerService(
            deviceId = deviceId,
            onRegistered = { serviceInfo ->
                _isHosting.value = true
                onRegistered()
            },
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }

    /**
     * 搜索房间（发现 NSD 服务）
     */
    fun searchRooms() {
        _isDiscovering.value = true
        nsdHelper.startDiscovery(
            onServiceFound = { /* 通过 StateFlow 自动更新 */ },
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }

    /**
     * 停止搜索
     */
    fun stopSearching() {
        _isDiscovering.value = false
        nsdHelper.stopDiscovery()
    }

    /**
     * 取消创建房间
     */
    fun cancelHosting() {
        _isHosting.value = false
        nsdHelper.unregisterService()
    }

    /**
     * 解析服务获取 IP 和端口
     */
    fun resolveService(
        serviceInfo: NsdServiceInfo,
        onResolved: (NsdServiceInfo) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        nsdHelper.resolveService(
            serviceInfo = serviceInfo,
            onResolved = onResolved,
            onError = onError
        )
    }

    override fun onCleared() {
        super.onCleared()
        nsdHelper.tearDown()
    }
}
