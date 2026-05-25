package com.example.weiqigame.domain.model

/**
 * 棋盘状态类
 *
 * 使用一维数组存储棋盘状态，相比二维数组具有以下优势：
 * 1. 缓存友好：内存连续，CPU 缓存命中率高
 * 2. 索引计算简单：index = y * size + x
 * 3. 拷贝高效：System.arraycopy 原生支持
 *
 * @param size 棋盘路数（标准19路，支持13/9路）
 * @param cells 一维数组存储的棋盘状态，0=空，1=黑，2=白
 */
data class BoardState(
    val size: Int = 19,
    private val cells: IntArray = IntArray(size * size) { EMPTY }
) {
    companion object {
        const val EMPTY = 0   // 空交叉点
        const val BLACK = 1   // 黑棋
        const val WHITE = 2   // 白棋

        /**
         * 创建新棋盘
         */
        fun create(size: Int = 19): BoardState {
            require(size in listOf(9, 13, 19)) { "棋盘路数必须是 9、13 或 19" }
            return BoardState(size)
        }
    }

    /**
     * 获取指定位置的棋子
     * @param x 横坐标（0-18）
     * @param y 纵坐标（0-18）
     * @return 该位置的棋子类型：EMPTY(0)、BLACK(1)、WHITE(2)
     */
    fun get(x: Int, y: Int): Int {
        require(isValidPosition(x, y)) { "坐标 ($x, $y) 超出棋盘范围" }
        return cells[y * size + x]
    }

    /**
     * 在指定位置放置棋子
     * @param x 横坐标
     * @param y 纵坐标
     * @param stone 棋子类型：BLACK(1) 或 WHITE(2)
     */
    fun set(x: Int, y: Int, stone: Int) {
        require(isValidPosition(x, y)) { "坐标 ($x, $y) 超出棋盘范围" }
        require(stone == BLACK || stone == WHITE) { "只能放置黑棋或白棋" }
        cells[y * size + x] = stone
    }

    /**
     * 移除指定位置的棋子
     */
    fun remove(x: Int, y: Int) {
        require(isValidPosition(x, y)) { "坐标 ($x, $y) 超出棋盘范围" }
        cells[y * size + x] = EMPTY
    }

    /**
     * 检查坐标是否在棋盘范围内
     */
    fun isValidPosition(x: Int, y: Int): Boolean {
        return x in 0 until size && y in 0 until size
    }

    /**
     * 检查指定位置是否为空
     */
    fun isEmpty(x: Int, y: Int): Boolean {
        return get(x, y) == EMPTY
    }

    /**
     * 获取对手棋子类型
     */
    fun getOpponent(stone: Int): Int {
        return when (stone) {
            BLACK -> WHITE
            WHITE -> BLACK
            else -> throw IllegalArgumentException("无效的棋子类型: $stone")
        }
    }

    /**
     * 创建深拷贝，用于打劫判定的历史状态比对
     */
    fun copy(): BoardState {
        return BoardState(size, cells.copyOf())
    }

    /**
     * 判断两个棋盘状态是否相同（用于打劫判定）
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BoardState) return false
        return size == other.size && cells.contentEquals(other.cells)
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + cells.contentHashCode()
        return result
    }

    /**
     * 获取当前棋盘的所有空点坐标
     */
    fun getEmptyPoints(): List<Pair<Int, Int>> {
        val emptyPoints = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (get(x, y) == EMPTY) {
                    emptyPoints.add(x to y)
                }
            }
        }
        return emptyPoints
    }

    /**
     * 获取当前棋盘上的所有棋子位置及类型
     */
    fun getAllStones(): Map<Pair<Int, Int>, Int> {
        val stones = mutableMapOf<Pair<Int, Int>, Int>()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val stone = get(x, y)
                if (stone != EMPTY) {
                    stones[x to y] = stone
                }
            }
        }
        return stones
    }

    /**
     * 统计指定颜色的棋子数量
     */
    fun countStones(stone: Int): Int {
        return cells.count { it == stone }
    }

    override fun toString(): String {
        return buildString {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    append(
                        when (get(x, y)) {
                            EMPTY -> "+"
                            BLACK -> "●"
                            WHITE -> "○"
                            else -> "?"
                        }
                    )
                    append(" ")
                }
                appendLine()
            }
        }
    }
}
