package com.example.weiqigame.domain.logic

import com.example.weiqigame.domain.model.BoardState

/**
 * 围棋结算计算器
 *
 * 使用中国围棋规则（数子法）计算胜负：
 * - 终局时，统计每方：实地 + 提子数 + 死子数
 * - 黑方贴目 3.75 子（即黑棋184.25子胜）
 * - 白方超过180.25子即胜
 */
class ScoreCalculator {

    companion object {
        // 中国规则贴目：黑方贴 3.75 子（即黑棋 184.25 子获胜）
        const val KOMI = 3.75
        const val BOARD_SIZE_19 = 19
        const val BOARD_SIZE_13 = 13
        const val BOARD_SIZE_9 = 9
    }

    /**
     * 计算双方得分
     *
     * @param board 当前棋盘状态
     * @param blackCaptured 黑方提子数（吃掉的白子）
     * @param whiteCaptured 白方提子数（吃掉的黑子）
     * @return ScoreResult 包含双方得分和胜负结果
     */
    fun calculateScore(
        board: BoardState,
        blackCaptured: Int,
        whiteCaptured: Int
    ): ScoreResult {
        val boardSize = board.size
        val totalPoints = boardSize * boardSize

        // 计算双方领地（使用 BFS 填充法计算）
        val (blackTerritory, whiteTerritory) = calculateTerritory(board)

        // 中国规则：
        // 黑方总子数 = 黑方实地 + 提子数
        // 白方总子数 = 白方实地 + 提子数 + 贴目
        val blackScore = (blackTerritory + blackCaptured).toDouble()
        val whiteScore = (whiteTerritory + whiteCaptured).toDouble() + KOMI

        // 判断胜负
        val winner = when {
            blackScore > whiteScore -> BoardState.BLACK
            whiteScore > blackScore -> BoardState.WHITE
            else -> BoardState.EMPTY // 和棋（极少见）
        }

        val margin = kotlin.math.abs(blackScore - whiteScore)

        return ScoreResult(
            blackScore = blackScore,
            whiteScore = whiteScore,
            blackTerritory = blackTerritory,
            whiteTerritory = whiteTerritory,
            blackCaptured = blackCaptured,
            whiteCaptured = whiteCaptured,
            komi = KOMI,
            winner = winner,
            margin = margin,
            boardSize = boardSize,
            totalPoints = totalPoints
        )
    }

    /**
     * 计算双方领地
     *
     * 使用简单的区域填充算法：
     * 1. 遍历所有空点
     * 2. 对每个未访问的空点进行 BFS，找到相邻的所有空点
     * 3. 判断该区域被哪方围住（看边界上的棋子颜色）
     * 4. 如果边界上只有一种颜色，该区域归该方所有
     * 5. 如果边界上有两种颜色或没有边界，该区域为中立（双活/未定）
     */
    private fun calculateTerritory(board: BoardState): Pair<Int, Int> {
        val size = board.size
        val visited = Array(size) { BooleanArray(size) { false } }
        var blackTerritory = 0
        var whiteTerritory = 0

        for (x in 0 until size) {
            for (y in 0 until size) {
                if (board.get(x, y) == BoardState.EMPTY && !visited[x][y]) {
                    val (territory, owner) = floodFillTerritory(board, x, y, visited)
                    when (owner) {
                        BoardState.BLACK -> blackTerritory += territory
                        BoardState.WHITE -> whiteTerritory += territory
                        // BoardState.EMPTY 表示中立区域（双活或无主），不计入任何一方
                    }
                }
            }
        }

        return Pair(blackTerritory, whiteTerritory)
    }

    /**
     * 区域填充算法
     *
     * @return Pair<区域大小, 归属方> (归属方: BLACK/WHITE/EMPTY)
     */
    private fun floodFillTerritory(
        board: BoardState,
        startX: Int,
        startY: Int,
        visited: Array<BooleanArray>
    ): Pair<Int, Int> {
        val size = board.size
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startX to startY)
        visited[startX][startY] = true

        val territory = mutableListOf<Pair<Int, Int>>()
        val boundaryStones = mutableSetOf<Int>()

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            territory.add(x to y)

            // 检查四个方向
            val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
            for ((dx, dy) in directions) {
                val nx = x + dx
                val ny = y + dy

                if (nx in 0 until size && ny in 0 until size) {
                    val stone = board.get(nx, ny)
                    if (stone == BoardState.EMPTY && !visited[nx][ny]) {
                        visited[nx][ny] = true
                        queue.add(nx to ny)
                    } else if (stone != BoardState.EMPTY) {
                        boundaryStones.add(stone)
                    }
                } else {
                    // 边界视为无归属（简化处理）
                    // 实际棋盘不会遇到这种情况，因为围棋棋盘有边界
                }
            }
        }

        // 判断区域归属
        val owner = when {
            boundaryStones.size == 1 && boundaryStones.contains(BoardState.BLACK) -> BoardState.BLACK
            boundaryStones.size == 1 && boundaryStones.contains(BoardState.WHITE) -> BoardState.WHITE
            else -> BoardState.EMPTY // 中立区域（双活、未定或无主）
        }

        return Pair(territory.size, owner)
    }
}

/**
 * 结算结果数据类
 */
data class ScoreResult(
    val blackScore: Double,      // 黑方总得分（含贴目计算）
    val whiteScore: Double,      // 白方总得分
    val blackTerritory: Int,     // 黑方实地（领地）
    val whiteTerritory: Int,     // 白方实地（领地）
    val blackCaptured: Int,      // 黑方提子数
    val whiteCaptured: Int,      // 白方提子数
    val komi: Double,            // 贴目数
    val winner: Int,             // 获胜方 (BLACK/WHITE/EMPTY)
    val margin: Double,          // 获胜 margin
    val boardSize: Int,          // 棋盘大小
    val totalPoints: Int         // 总点数（用于验证）
) {
    /**
     * 获取获胜方名称
     */
    fun getWinnerName(): String = when (winner) {
        BoardState.BLACK -> "黑方"
        BoardState.WHITE -> "白方"
        else -> "和棋"
    }

    /**
     * 格式化显示获胜信息
     */
    fun getWinDescription(): String {
        if (winner == BoardState.EMPTY) {
            return "和棋"
        }
        return "${getWinnerName()}获胜 (胜${margin.format(1)}子)"
    }

    /**
     * 获取实时胜率估算（简化版）
     * 返回黑方胜率百分比 (0-100)
     */
    fun getBlackWinRate(): Int {
        val total = blackScore + whiteScore
        if (total == 0.0) return 50
        val rate = (blackScore / total) * 100
        return rate.toInt().coerceIn(0, 100)
    }

    /**
     * 获取当前领先方和优势描述
     */
    fun getCurrentLeadDescription(): String {
        val diff = blackScore - whiteScore
        return when {
            diff > 0 -> "黑方领先 ${diff.format(1)} 子"
            diff < 0 -> "白方领先 ${(-diff).format(1)} 子"
            else -> "双方势均力敌"
        }
    }
}

/**
 * 格式化数字，保留指定小数位
 */
private fun Double.format(decimals: Int): String {
    return String.format("%.${decimals}f", this)
}
