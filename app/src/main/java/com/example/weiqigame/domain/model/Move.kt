package com.example.weiqigame.domain.model

/**
 * 落子数据类
 *
 * 记录一次落子操作的完整信息
 *
 * @param x 横坐标（0-18）
 * @param y 纵坐标（0-18）
 * @param stone 棋子颜色：BoardState.BLACK(1) 或 BoardState.WHITE(2)
 * @param sequence 落子序号（用于复盘）
 * @param captureCount 本次落子提掉的对方棋子数量
 */
data class Move(
    val x: Int,
    val y: Int,
    val stone: Int,
    val sequence: Int = 0,
    val captureCount: Int = 0
) {
    init {
        require(x in 0..18) { "x 坐标必须在 0-18 范围内" }
        require(y in 0..18) { "y 坐标必须在 0-18 范围内" }
        require(stone == BoardState.BLACK || stone == BoardState.WHITE) {
            "棋子必须是黑棋或白棋"
        }
    }

    /**
     * 获取当前落子方的对手颜色
     */
    fun getOpponent(): Int {
        return if (stone == BoardState.BLACK) BoardState.WHITE else BoardState.BLACK
    }

    /**
     * 格式化为标准围棋坐标（如 A1、T19 等）
     */
    fun toCoordinate(): String {
        val column = ('A' + x).toString()
        val row = (19 - y).toString()
        return "$column$row"
    }

    companion object {
        /**
         * 从标准围棋坐标解析（如 "A1"、"T19"）
         */
        fun fromCoordinate(coordinate: String, stone: Int): Move {
            require(coordinate.length >= 2) { "坐标格式错误" }
            val x = coordinate[0].uppercaseChar() - 'A'
            val y = 19 - coordinate.substring(1).toInt()
            return Move(x, y, stone)
        }
    }
}

/**
 * 游戏模式枚举
 */
enum class GameMode {
    LOCAL,      // 本地单机对战
    ONLINE_HOST, // 局域网创建房间（黑方）
    ONLINE_CLIENT // 局域网加入房间（白方）
}

/**
 * 游戏状态枚举
 */
enum class GameStatus {
    IDLE,       // 等待开始
    PREPARING,  // 准备中（网络对战握手阶段）
    PLAYING,    // 对局中
    PAUSED,     // 暂停
    FINISHED    // 已结束
}

/**
 * 游戏结果数据类
 */
data class GameResult(
    val winner: Int,      // BoardState.BLACK 或 BoardState.WHITE
    val blackScore: Float,
    val whiteScore: Float,
    val reason: String  // "RESIGN" | "TIMEOUT" | "NORMAL"
)

/**
 * 玩家信息
 */
data class Player(
    val name: String,
    val stone: Int,     // 该玩家执黑或执白
    val isLocal: Boolean // 是否为本地玩家
)
