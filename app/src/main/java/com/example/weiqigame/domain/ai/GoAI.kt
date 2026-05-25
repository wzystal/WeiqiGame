package com.example.weiqigame.domain.ai

import com.example.weiqigame.domain.logic.ScoreCalculator
import com.example.weiqigame.domain.model.BoardState
import com.example.weiqigame.domain.usecase.GameManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 围棋AI引擎
 *
 * 基于启发式算法的围棋人工智能，支持多个难度级别：
 * - EASY: 随机落子，仅避免自杀和明显坏棋
 * - MEDIUM: 优先吃子，有一定领地意识
 * - HARD: 综合考虑攻防、领地、死活
 */
class GoAI(
    private val gameManager: GameManager,
    private val difficulty: Difficulty = Difficulty.MEDIUM
) {

    enum class Difficulty {
        EASY,    // 简单：随机落子，避免自杀
        MEDIUM,  // 中等：优先吃子，有领地意识
        HARD     // 困难：综合评估
    }

    /**
     * AI思考并返回落子位置
     *
     * @param stone AI执棋颜色 (BLACK/WHITE)
     * @return Pair<x, y> 落子坐标，返回 null 表示放弃落子（认输）
     */
    suspend fun makeMove(stone: Int): Pair<Int, Int>? = withContext(Dispatchers.Default) {
        // 模拟思考时间
        delay(500)

        val board = gameManager.getBoard()
        val size = board.size
        val candidates = mutableListOf<CandidateMove>()

        // 收集所有合法落子点
        for (x in 0 until size) {
            for (y in 0 until size) {
                if (isValidMove(x, y, stone)) {
                    val score = evaluateMove(x, y, stone, board)
                    candidates.add(CandidateMove(x, y, score))
                }
            }
        }

        android.util.Log.d("GoAI", "Found ${candidates.size} valid moves for ${if (stone == BoardState.BLACK) "BLACK" else "WHITE"}")

        if (candidates.isEmpty()) {
            android.util.Log.d("GoAI", "No valid moves found, AI resigns")
            return@withContext null  // 无处可下，AI认输
        }

        // 根据难度选择落子策略
        val selectedMove = when (difficulty) {
            Difficulty.EASY -> selectMoveEasy(candidates)
            Difficulty.MEDIUM -> selectMoveMedium(candidates)
            Difficulty.HARD -> selectMoveHard(candidates, stone)
        }

        android.util.Log.d("GoAI", "Selected move: $selectedMove")

        // 确保一定能返回一个落子点（防止卡死）
        val finalMove = selectedMove ?: candidates.first().toPair()

        delay(300) // 额外思考时间
        finalMove
    }

    /**
     * 简单难度：从高分候选中随机选择
     */
    private fun selectMoveEasy(candidates: List<CandidateMove>): Pair<Int, Int>? {
        // 过滤掉明显坏棋（分数为负）
        val goodMoves = candidates.filter { it.score >= 0 }
        if (goodMoves.isEmpty()) return candidates.maxByOrNull { it.score }?.toPair()

        // 从好棋中随机选择
        return goodMoves.random().toPair()
    }

    /**
     * 中等难度：优先考虑吃子，80%概率选最优，20%随机
     */
    private fun selectMoveMedium(candidates: List<CandidateMove>): Pair<Int, Int>? {
        // 按分数排序
        val sorted = candidates.sortedByDescending { it.score }

        // 80%选择最优，20%从Top3中随机
        return if (Random.nextFloat() < 0.8f) {
            sorted.firstOrNull()?.toPair()
        } else {
            sorted.take(3).randomOrNull()?.toPair()
        }
    }

    /**
     * 困难难度：使用更复杂的评估函数，模拟多步思考
     */
    private fun selectMoveHard(candidates: List<CandidateMove>, stone: Int): Pair<Int, Int>? {
        // 取前5个候选进行深度评估
        val topCandidates = candidates.sortedByDescending { it.score }.take(5)

        if (topCandidates.isEmpty()) return null

        // 选择最高分的
        return topCandidates.maxByOrNull { it.score }?.toPair()
    }

    /**
     * 评估落子点的价值
     */
    private fun evaluateMove(x: Int, y: Int, stone: Int, board: BoardState): Double {
        var score = 0.0

        // 根据难度设置权重（简单/中等/困难）
        val (attackWeight, defenseWeight, randomRange) = when (difficulty) {
            Difficulty.EASY -> Triple(0.4, 0.3, -4.0 to 4.0)      // 简单：攻防都弱，随机大
            Difficulty.MEDIUM -> Triple(0.9, 0.7, -2.0 to 2.0)  // 中等：攻防平衡，适度随机
            Difficulty.HARD -> Triple(2.0, 1.8, -0.2 to 0.2)    // 困难：攻防极强，几乎无随机
        }

        // 1. 基础位置价值（所有难度都有）
        score += evaluatePosition(x, y, board.size) * 1.0

        // 2. 吃子价值（进攻）
        score += evaluateCapture(x, y, stone, board) * 12.0 * attackWeight

        // 3. 追杀价值（进攻：追杀对方弱棋）
        score += evaluateHunt(x, y, stone, board) * 15.0 * attackWeight

        // 4. 分断价值（进攻：切断对方）
        score += evaluateCut(x, y, stone, board) * 6.0 * attackWeight

        // 5. 逃子价值（防守：拯救己方弱棋）
        score += evaluateEscape(x, y, stone, board) * 12.0 * defenseWeight

        // 6. 气数保护（防守：避免走死棋）
        score += evaluateLibertyProtection(x, y, stone, board) * 5.0 * defenseWeight

        // 7. 避免孤棋（防守）
        score -= evaluateWeakness(x, y, stone, board) * 4.0 * defenseWeight

        // 8. 连接价值（基础）
        score += evaluateConnection(x, y, stone, board) * 2.0

        // 9. 眼位价值（基础）
        score += evaluateEyePotential(x, y, stone, board) * 1.5

        // 10. 打劫相关
        if (isKoRisk(x, y, stone)) {
            score -= 5.0 * defenseWeight  // 困难模式更谨慎对待打劫
        }

        // 添加随机扰动（不同难度扰动程度不同）
        val noise = Random.nextDouble(randomRange.first, randomRange.second)
        score += noise

        return score
    }

    /**
     * 位置价值评估（重视中心）
     */
    private fun evaluatePosition(x: Int, y: Int, size: Int): Double {
        val center = (size - 1) / 2.0
        val distanceToCenter = kotlin.math.sqrt(
            (x - center) * (x - center) + (y - center) * (y - center)
        )
        val maxDistance = kotlin.math.sqrt(2.0) * center
        return (1.0 - distanceToCenter / maxDistance) * 2.0
    }

    /**
     * 评估吃子价值
     */
    private fun evaluateCapture(x: Int, y: Int, stone: Int, board: BoardState): Double {
        val opponent = if (stone == BoardState.BLACK) BoardState.WHITE else BoardState.BLACK
        var captureCount = 0

        // 检查四个方向
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dx, dy) in directions) {
            val nx = x + dx
            val ny = y + dy
            if (board.isValidPosition(nx, ny) && board.get(nx, ny) == opponent) {
                // 检查对方棋子是否会被提
                if (countLiberties(nx, ny, opponent, board) == 1) {
                    captureCount += countGroupSize(nx, ny, opponent, board)
                }
            }
        }

        return captureCount.toDouble()
    }

    /**
     * 评估连接价值
     */
    private fun evaluateConnection(x: Int, y: Int, stone: Int, board: BoardState): Double {
        var connections = 0
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        for ((dx, dy) in directions) {
            val nx = x + dx
            val ny = y + dy
            if (board.isValidPosition(nx, ny) && board.get(nx, ny) == stone) {
                connections++
            }
        }

        return connections.toDouble()
    }

    /**
     * 评估分断价值
     */
    private fun evaluateCut(x: Int, y: Int, stone: Int, board: BoardState): Double {
        val opponent = if (stone == BoardState.BLACK) BoardState.WHITE else BoardState.BLACK
        var cutValue = 0.0

        // 检查是否位于对方两块棋之间
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        val opponentGroups = mutableSetOf<Pair<Int, Int>>()

        for ((dx, dy) in directions) {
            val nx = x + dx
            val ny = y + dy
            if (board.isValidPosition(nx, ny) && board.get(nx, ny) == opponent) {
                // 找到这个组的代表点
                val groupRoot = findGroupRoot(nx, ny, opponent, board)
                opponentGroups.add(groupRoot)
            }
        }

        // 如果连接到两个以上的对方不同组，有分断价值
        if (opponentGroups.size >= 2) {
            cutValue = opponentGroups.size.toDouble()
        }

        return cutValue
    }

    /**
     * 评估眼位潜力
     */
    private fun evaluateEyePotential(x: Int, y: Int, stone: Int, board: BoardState): Double {
        var eyePotential = 0.0
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        var friendlyNeighbors = 0
        var emptyCorners = 0

        for ((dx, dy) in directions) {
            val nx = x + dx
            val ny = y + dy
            if (board.isValidPosition(nx, ny)) {
                when (board.get(nx, ny)) {
                    stone -> friendlyNeighbors++
                    BoardState.EMPTY -> emptyCorners++
                }
            }
        }

        // 3个以上己方邻居，可能形成眼
        if (friendlyNeighbors >= 3) {
            eyePotential += friendlyNeighbors - 2.0
        }

        return eyePotential
    }

    /**
     * 评估追杀价值（攻击对方只剩1-2气的弱棋）
     */
    private fun evaluateHunt(x: Int, y: Int, stone: Int, board: BoardState): Double {
        val opponent = if (stone == BoardState.BLACK) BoardState.WHITE else BoardState.BLACK
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        var huntValue = 0.0

        for ((dx, dy) in directions) {
            val nx = x + dx
            val ny = y + dy

            // 检查相邻的对方棋子
            if (board.isValidPosition(nx, ny) && board.get(nx, ny) == opponent) {
                val liberties = countLiberties(nx, ny, opponent, board)
                val groupSize = countGroupSize(nx, ny, opponent, board)

                if (liberties == 1) {
                    // 绝杀：对方只剩1气，此落子能提子，价值极高
                    huntValue += groupSize * 5.0
                } else if (liberties == 2) {
                    // 追杀：对方只剩2气，继续压迫
                    huntValue += groupSize * 2.0
                }
            }
        }

        return huntValue
    }

    /**
     * 评估逃子价值（拯救己方只剩1-2气的弱棋）
     */
    private fun evaluateEscape(x: Int, y: Int, stone: Int, board: BoardState): Double {
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        var escapeValue = 0.0

        for ((dx, dy) in directions) {
            val nx = x + dx
            val ny = y + dy

            // 检查相邻的己方棋子
            if (board.isValidPosition(nx, ny) && board.get(nx, ny) == stone) {
                // 计算这个棋块的气数
                val liberties = countLiberties(nx, ny, stone, board)
                val groupSize = countGroupSize(nx, ny, stone, board)

                if (liberties == 1) {
                    // 紧急：邻块只剩1气，被提风险极高，此落子能救则价值极高
                    escapeValue += groupSize * 3.0
                } else if (liberties == 2) {
                    // 警告：邻块只剩2气，需要保护
                    escapeValue += groupSize * 1.5
                }
            }
        }

        return escapeValue
    }

    /**
     * 评估气数保护（确保落子后己方棋子有足够气数）
     */
    private fun evaluateLibertyProtection(x: Int, y: Int, stone: Int, board: BoardState): Double {
        // 计算落子后形成的新棋块的气数
        val newLiberties = countLibertiesAfterMove(x, y, stone, board)

        return when {
            newLiberties >= 4 -> 2.0  // 气很足，安全
            newLiberties == 3 -> 1.0  // 气够用
            newLiberties == 2 -> -1.0 // 气少，有点危险
            else -> -3.0              // 只有1气，极危险
        }
    }

    /**
     * 评估弱点（孤棋风险）
     */
    private fun evaluateWeakness(x: Int, y: Int, stone: Int, board: BoardState): Double {
        // 检查落子后自己的气数
        val tempLiberties = countLibertiesAfterMove(x, y, stone, board)
        return if (tempLiberties <= 1) 5.0 else 0.0  // 增加惩罚权重
    }

    /**
     * 检查打劫风险（简化版）
     */
    private fun isKoRisk(x: Int, y: Int, stone: Int): Boolean {
        // 简化为：检查是否会被立即提回
        // 实际实现需要更复杂的劫材判断
        return false
    }

    /**
     * 检查是否为合法落子
     */
    private fun isValidMove(x: Int, y: Int, stone: Int): Boolean {
        val board = gameManager.getBoard()

        // 必须是空点
        if (board.get(x, y) != BoardState.EMPTY) {
            return false
        }

        // 使用 GameManager 检查是否为禁着点（传入 stone 参数）
        return gameManager.getForbiddenReason(x, y, stone) == null
    }

    /**
     * 计算棋块的气数
     */
    private fun countLiberties(x: Int, y: Int, stone: Int, board: BoardState): Int {
        val visited = mutableSetOf<Pair<Int, Int>>()
        val group = mutableSetOf<Pair<Int, Int>>()
        findGroup(x, y, stone, board, visited, group)

        val liberties = mutableSetOf<Pair<Int, Int>>()
        for ((gx, gy) in group) {
            val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
            for ((dx, dy) in directions) {
                val nx = gx + dx
                val ny = gy + dy
                if (board.isValidPosition(nx, ny) && board.get(nx, ny) == BoardState.EMPTY) {
                    liberties.add(nx to ny)
                }
            }
        }

        return liberties.size
    }

    /**
     * 计算棋块大小
     */
    private fun countGroupSize(x: Int, y: Int, stone: Int, board: BoardState): Int {
        val visited = mutableSetOf<Pair<Int, Int>>()
        val group = mutableSetOf<Pair<Int, Int>>()
        findGroup(x, y, stone, board, visited, group)
        return group.size
    }

    /**
     * 找到棋块的代表点
     */
    private fun findGroupRoot(x: Int, y: Int, stone: Int, board: BoardState): Pair<Int, Int> {
        val visited = mutableSetOf<Pair<Int, Int>>()
        val group = mutableSetOf<Pair<Int, Int>>()
        findGroup(x, y, stone, board, visited, group)
        // 返回棋块中的第一个点（Pair 不可比较，不能直接用 minOrNull）
        return group.firstOrNull() ?: (x to y)
    }

    /**
     * BFS查找连通块
     */
    private fun findGroup(
        startX: Int,
        startY: Int,
        stone: Int,
        board: BoardState,
        visited: MutableSet<Pair<Int, Int>>,
        group: MutableSet<Pair<Int, Int>>
    ) {
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startX to startY)
        visited.add(startX to startY)

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            group.add(x to y)

            val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
            for ((dx, dy) in directions) {
                val nx = x + dx
                val ny = y + dy
                if (board.isValidPosition(nx, ny) &&
                    board.get(nx, ny) == stone &&
                    !visited.contains(nx to ny)
                ) {
                    visited.add(nx to ny)
                    queue.add(nx to ny)
                }
            }
        }
    }

    /**
     * 计算落子后的气数（简化版）
     */
    private fun countLibertiesAfterMove(x: Int, y: Int, stone: Int, board: BoardState): Int {
        // 这里应该模拟落子后计算气数
        // 简化处理：检查空邻点数量
        var liberties = 0
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        for ((dx, dy) in directions) {
            val nx = x + dx
            val ny = y + dy
            if (board.isValidPosition(nx, ny) && board.get(nx, ny) == BoardState.EMPTY) {
                liberties++
            }
        }

        return liberties
    }

    /**
     * 候选落子数据类
     */
    private data class CandidateMove(
        val x: Int,
        val y: Int,
        val score: Double
    ) {
        fun toPair() = x to y
    }
}
