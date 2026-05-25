package com.example.weiqigame.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * 消息帧处理器
 *
 * 【TCP 粘包/拆包问题】
 * TCP 是流式协议，没有消息边界概念。发送方连续发送两条消息，
 * 接收方可能一次读到（粘包），也可能分多次读到（拆包）。
 *
 * 【解决方案：Length-Prefixed 协议】
 * 每条消息前附加 4 字节的长度前缀，格式如下：
 * [4字节消息长度][N字节 JSON 消息体]
 *
 * 示例：{"type":"MOVE",...} 长度为 50 字节
 * 实际发送：0x00 0x00 0x00 0x32 + {JSON 字符串}
 *
 * 接收方先读 4 字节获取长度，再读取指定长度的消息体。
 */
class MessageFraming(private val gson: Gson = Gson()) {

    companion object {
        // 长度前缀的字节数（4 字节 = 32 位整数）
        const val LENGTH_PREFIX_SIZE = 4

        // 最大消息长度（防止恶意大消息导致内存溢出）
        const val MAX_MESSAGE_SIZE = 1024 * 1024  // 1MB
    }

    /**
     * 将消息编码为字节数组
     *
     * @param message 要发送的消息
     * @return 编码后的字节数组（长度前缀 + JSON 体）
     */
    fun encode(message: GoMessage): ByteArray {
        // 将消息对象序列化为 JSON 字符串
        val json = gson.toJson(message)
        val bodyBytes = json.toByteArray(Charsets.UTF_8)
        val bodyLength = bodyBytes.size

        // 检查消息大小
        require(bodyLength <= MAX_MESSAGE_SIZE) {
            "消息体过大：$bodyLength 字节，最大允许 $MAX_MESSAGE_SIZE 字节"
        }

        // 创建 4 字节的长度前缀（大端序）
        val lengthPrefix = ByteBuffer.allocate(LENGTH_PREFIX_SIZE)
            .putInt(bodyLength)
            .array()

        // 合并长度前缀和消息体
        return lengthPrefix + bodyBytes
    }

    /**
     * 从输入流解码消息
     *
     * 阻塞方法，直到读取到完整消息
     *
     * @param inputStream 输入流
     * @return 解码后的消息对象
     * @throws java.io.EOFException 连接断开
     * @throws IllegalStateException 消息格式错误
     */
    fun decode(inputStream: InputStream): GoMessage {
        // 1. 读取 4 字节长度前缀
        val lengthBytes = readNBytes(inputStream, LENGTH_PREFIX_SIZE)
        val bodyLength = ByteBuffer.wrap(lengthBytes).int

        // 验证消息长度
        if (bodyLength <= 0 || bodyLength > MAX_MESSAGE_SIZE) {
            throw IllegalStateException("非法的消息长度：$bodyLength")
        }

        // 2. 读取指定长度的消息体
        val bodyBytes = readNBytes(inputStream, bodyLength)
        val json = String(bodyBytes, Charsets.UTF_8)

        // 3. 解析 JSON
        return parseMessage(json)
    }

    /**
     * 发送消息到输出流
     *
     * @param outputStream 输出流
     * @param message 要发送的消息
     */
    fun send(outputStream: OutputStream, message: GoMessage) {
        val encoded = encode(message)
        outputStream.write(encoded)
        outputStream.flush()
    }

    /**
     * 从输入流精确读取 N 字节数据
     *
     * 阻塞直到读取到指定字节数或流结束
     */
    private fun readNBytes(inputStream: InputStream, n: Int): ByteArray {
        val buffer = ByteArray(n)
        var totalRead = 0

        while (totalRead < n) {
            val read = inputStream.read(buffer, totalRead, n - totalRead)
            if (read == -1) {
                // 连接断开
                throw java.io.EOFException("连接已断开，读取 $n 字节时只读到 $totalRead 字节")
            }
            totalRead += read
        }

        return buffer
    }

    /**
     * 根据 type 字段解析具体的消息类型
     */
    private fun parseMessage(json: String): GoMessage {
        // 先解析为 JsonObject 获取 type 字段
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
        val type = jsonObject.get("type")?.asString
            ?: throw IllegalStateException("消息缺少 type 字段")

        // 根据 type 反序列化为具体类型
        return when (type) {
            "MOVE" -> gson.fromJson(json, GoMessage.Move::class.java)
            "READY" -> gson.fromJson(json, GoMessage.Ready::class.java)
            "RESIGN" -> GoMessage.Resign()
            "HEARTBEAT" -> GoMessage.Heartbeat()
            "CHAT" -> gson.fromJson(json, GoMessage.Chat::class.java)
            "GAME_OVER" -> gson.fromJson(json, GoMessage.GameOver::class.java)
            "ERROR" -> gson.fromJson(json, GoMessage.Error::class.java)
            else -> throw IllegalStateException("未知的消息类型：$type")
        }
    }
}
