package com.example.weiqigame.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TCP 游戏连接管理
 *
 * 管理点对点 TCP 连接的生命周期：
 * - 作为主机：监听端口，等待客户端连接
 * - 作为客户端：主动连接主机
 * - 消息收发：通过 MessageFraming 处理粘包
 * - 心跳保活：30秒间隔
 * - 断线检测：自动关闭连接
 */
class TcpGameConnection {

    companion object {
        private const val TAG = "TcpGameConnection"
        private const val HEARTBEAT_INTERVAL = 30000L  // 30秒
        private const val CONNECTION_TIMEOUT = 10000   // 10秒
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val framing = MessageFraming()

    // Socket 连接
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    // 连接状态
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 接收到的消息流
    private val _incomingMessages = MutableSharedFlow<GoMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<GoMessage> = _incomingMessages.asSharedFlow()

    // 错误事件流
    private val _errors = MutableSharedFlow<NetworkError>(extraBufferCapacity = 16)
    val errors: SharedFlow<NetworkError> = _errors.asSharedFlow()

    // 心跳任务
    private var heartbeatJob: Job? = null

    // 接收任务
    private var receiveJob: Job? = null

    // 是否为主机
    private var isHost = false

    // 连接活跃标志
    private val isConnected = AtomicBoolean(false)

    /**
     * 作为主机启动监听
     *
     * @param port 监听端口
     * @param onClientConnected 客户端连接成功回调
     */
    fun startAsHost(port: Int = NsdHelper.DEFAULT_PORT, onClientConnected: () -> Unit = {}) {
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            return
        }

        isHost = true
        _connectionState.value = ConnectionState.CONNECTING

        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "主机启动，监听端口：$port")

                // 等待客户端连接（阻塞）
                socket = serverSocket!!.accept()
                Log.i(TAG, "客户端已连接：${socket!!.inetAddress}")

                // 设置超时
                socket!!.soTimeout = CONNECTION_TIMEOUT

                isConnected.set(true)
                _connectionState.value = ConnectionState.CONNECTED

                onClientConnected()

                // 启动接收循环和心跳
                startReceiving()
                startHeartbeat()

            } catch (e: IOException) {
                Log.e(TAG, "主机启动失败", e)
                _errors.emit(NetworkError.ConnectionFailed(e.message ?: "未知错误"))
                disconnect()
            }
        }
    }

    /**
     * 作为客户端连接主机
     *
     * @param hostAddress 主机地址
     * @param port 主机端口
     * @param onConnected 连接成功回调
     */
    fun connectAsClient(
        hostAddress: InetAddress,
        port: Int = NsdHelper.DEFAULT_PORT,
        onConnected: () -> Unit = {}
    ) {
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            return
        }

        isHost = false
        _connectionState.value = ConnectionState.CONNECTING

        scope.launch {
            try {
                socket = Socket(hostAddress, port)
                Log.i(TAG, "已连接到主机：${hostAddress.hostAddress}:$port")

                isConnected.set(true)
                _connectionState.value = ConnectionState.CONNECTED

                onConnected()

                // 启动接收循环和心跳
                startReceiving()
                startHeartbeat()

            } catch (e: IOException) {
                Log.e(TAG, "连接主机失败", e)
                _errors.emit(NetworkError.ConnectionFailed(e.message ?: "未知错误"))
                disconnect()
            }
        }
    }

    /**
     * 发送消息
     *
     * @param message 要发送的消息
     * @return 是否发送成功
     */
    fun sendMessage(message: GoMessage): Boolean {
        if (!isConnected.get() || socket?.isConnected != true) {
            return false
        }

        return try {
            framing.send(socket!!.getOutputStream(), message)
            true
        } catch (e: IOException) {
            Log.e(TAG, "发送消息失败", e)
            false
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        Log.i(TAG, "断开连接")

        isConnected.set(false)
        _connectionState.value = ConnectionState.DISCONNECTED

        // 停止任务
        heartbeatJob?.cancel()
        receiveJob?.cancel()

        // 关闭 Socket
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "关闭 Socket 失败", e)
        }

        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "关闭 ServerSocket 失败", e)
        }

        socket = null
        serverSocket = null
    }

    /**
     * 清理资源
     */
    fun tearDown() {
        disconnect()
        scope.cancel()
    }

    /**
     * 启动消息接收循环
     */
    private fun startReceiving() {
        receiveJob = scope.launch {
            val inputStream = socket!!.getInputStream()

            while (isActive && isConnected.get()) {
                try {
                    // 阻塞读取消息
                    val message = framing.decode(inputStream)
                    Log.d(TAG, "收到消息：${message.type}")

                    // 发送到消息流
                    _incomingMessages.emit(message)

                    // 如果是心跳，不需要额外处理（解码成功即证明连接正常）
                } catch (e: java.io.EOFException) {
                    Log.i(TAG, "连接已断开（EOF）")
                    _errors.emit(NetworkError.Disconnected("连接已断开"))
                    disconnect()
                    break
                } catch (e: IOException) {
                    Log.e(TAG, "接收消息失败", e)
                    _errors.emit(NetworkError.Disconnected(e.message ?: "连接异常"))
                    disconnect()
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "处理消息失败", e)
                    _errors.emit(NetworkError.ProtocolError(e.message ?: "消息格式错误"))
                }
            }
        }
    }

    /**
     * 启动心跳保活
     */
    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive && isConnected.get()) {
                delay(HEARTBEAT_INTERVAL)

                if (!isConnected.get()) break

                // 发送心跳
                val success = sendMessage(GoMessage.Heartbeat())
                if (!success) {
                    Log.e(TAG, "心跳发送失败，连接可能已断开")
                    _errors.emit(NetworkError.Disconnected("心跳超时"))
                    disconnect()
                    break
                }
            }
        }
    }
}
