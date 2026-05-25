package com.example.weiqigame.data.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NSD（网络服务发现）帮助类
 *
 * 【Android NSD 机制】
 * NSD 允许设备在局域网内发现彼此的服务，无需预先知道 IP 地址。
 * 工作流程：
 * 1. 主机注册服务（registerService）：声明"我提供围棋对战服务"
 * 2. 客户端发现服务（discoverServices）：搜索"谁提供围棋对战服务"
 * 3. 解析服务（resolveService）：获取主机的 IP 地址和端口
 *
 * 【服务命名规范】
 * - 服务名称：围棋-{设备名后4位}（如 "围棋-A3B2"）
 * - 服务类型：_go._tcp（围棋服务，TCP 协议）
 */
class NsdHelper(private val context: Context) {

    companion object {
        private const val TAG = "NsdHelper"

        // 服务类型，遵循 DNS-SD 规范：_<服务>._<协议>
        const val SERVICE_TYPE = "_go._tcp"

        // 服务基础名称前缀
        const val SERVICE_PREFIX = "围棋-"

        // 默认端口号
        const val DEFAULT_PORT = 8765
    }

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // 发现的设备列表
    private val _discoveredServices = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val discoveredServices: StateFlow<List<NsdServiceInfo>> = _discoveredServices.asStateFlow()

    // 当前注册的服务信息
    private var registeredService: NsdServiceInfo? = null

    // 注册监听器
    private var registrationListener: NsdManager.RegistrationListener? = null

    // 发现监听器
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * 注册服务（作为主机创建房间）
     *
     * @param deviceId 设备标识（用于生成服务名）
     * @param port 监听端口
     * @param onRegistered 注册成功回调
     * @param onError 注册失败回调
     */
    fun registerService(
        deviceId: String,
        port: Int = DEFAULT_PORT,
        onRegistered: (NsdServiceInfo) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // 停止之前的注册
        unregisterService()

        val serviceName = "$SERVICE_PREFIX${deviceId.takeLast(4)}"
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "服务注册成功：${serviceInfo.serviceName}")
                registeredService = serviceInfo
                onRegistered(serviceInfo)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                val error = "服务注册失败：错误码 $errorCode"
                Log.e(TAG, error)
                onError(error)
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "服务已注销：${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "服务注销失败：错误码 $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    /**
     * 注销服务
     */
    fun unregisterService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e(TAG, "注销服务失败", e)
            }
            registrationListener = null
        }
        registeredService = null
    }

    /**
     * 开始发现服务（作为客户端搜索房间）
     *
     * @param onServiceFound 发现新服务回调
     * @param onError 发现失败回调
     */
    fun startDiscovery(
        onServiceFound: (NsdServiceInfo) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // 停止之前的发现
        stopDiscovery()

        // 清空已发现的设备列表
        _discoveredServices.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "开始发现服务：$serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "发现服务：${serviceInfo.serviceName}")

                // 过滤自己的服务（如果同时作为主机和客户端）
                if (serviceInfo.serviceName == registeredService?.serviceName) {
                    return
                }

                // 只添加围棋服务
                if (serviceInfo.serviceName.startsWith(SERVICE_PREFIX)) {
                    val currentList = _discoveredServices.value.toMutableList()
                    if (currentList.none { it.serviceName == serviceInfo.serviceName }) {
                        currentList.add(serviceInfo)
                        _discoveredServices.value = currentList
                        onServiceFound(serviceInfo)
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "服务丢失：${serviceInfo.serviceName}")

                // 从列表中移除
                val currentList = _discoveredServices.value.toMutableList()
                currentList.removeAll { it.serviceName == serviceInfo.serviceName }
                _discoveredServices.value = currentList
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "停止发现服务：$serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                val error = "启动发现失败：错误码 $errorCode"
                Log.e(TAG, error)
                onError(error)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "停止发现失败：错误码 $errorCode")
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    /**
     * 停止发现服务
     */
    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "停止发现失败", e)
            }
            discoveryListener = null
        }
    }

    /**
     * 解析服务获取 IP 和端口
     *
     * @param serviceInfo 要解析的服务
     * @param onResolved 解析成功回调，返回包含 IP 和端口的服务信息
     * @param onError 解析失败回调
     */
    fun resolveService(
        serviceInfo: NsdServiceInfo,
        onResolved: (NsdServiceInfo) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                val error = "解析服务失败：错误码 $errorCode"
                Log.e(TAG, error)
                onError(error)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "服务解析成功：${serviceInfo.serviceName}, " +
                        "IP: ${serviceInfo.host}, 端口: ${serviceInfo.port}")
                onResolved(serviceInfo)
            }
        }

        nsdManager.resolveService(serviceInfo, resolveListener)
    }

    /**
     * 清理所有资源
     */
    fun tearDown() {
        stopDiscovery()
        unregisterService()
    }
}
