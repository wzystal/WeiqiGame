package com.example.weiqigame.data.remote

import com.example.weiqigame.domain.model.BoardState
import com.google.gson.annotations.SerializedName

/**
 * 网络消息基类
 *
 * 所有 P2P 通信消息都继承此类，使用 JSON 序列化传输
 */
sealed class GoMessage(
    @SerializedName("type")
    val type: String,
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 落子消息
     * 由当前回合方发送给对手
     */
    data class Move(
        @SerializedName("x")
        val x: Int,
        @SerializedName("y")
        val y: Int,
        @SerializedName("stone")
        val stone: Int,
        @SerializedName("sequence")
        val sequence: Int,
        @SerializedName("captureCount")
        val captureCount: Int
    ) : GoMessage("MOVE")

    /**
     * 游戏准备消息
     * 连接建立后，主机发送给客户端，分配颜色
     */
    data class Ready(
        @SerializedName("assignedColor")
        val assignedColor: Int,  // 分配给对方的颜色（客户端收到的是自己的颜色）
        @SerializedName("boardSize")
        val boardSize: Int
    ) : GoMessage("READY")

    /**
     * 认输消息
     */
    class Resign : GoMessage("RESIGN")

    /**
     * 心跳消息
     * 维持连接活跃，30秒间隔
     */
    class Heartbeat : GoMessage("HEARTBEAT")

    /**
     * 聊天消息（预留）
     */
    data class Chat(
        @SerializedName("content")
        val content: String
    ) : GoMessage("CHAT")

    /**
     * 游戏结束消息
     */
    data class GameOver(
        @SerializedName("reason")
        val reason: String,  // "RESIGN" | "TIMEOUT" | "DISCONNECT"
        @SerializedName("winner")
        val winner: Int
    ) : GoMessage("GAME_OVER")

    /**
     * 错误消息
     */
    data class Error(
        @SerializedName("code")
        val code: Int,
        @SerializedName("message")
        val message: String
    ) : GoMessage("ERROR")
}

/**
 * 网络连接状态
 */
enum class ConnectionState {
    DISCONNECTED,   // 未连接
    CONNECTING,     // 连接中
    CONNECTED,      // 已连接
    RECONNECTING    // 重连中
}

/**
 * 网络错误类型
 */
sealed class NetworkError {
    data class ConnectionFailed(val message: String) : NetworkError()
    data class Disconnected(val reason: String) : NetworkError()
    data class ProtocolError(val message: String) : NetworkError()
}
