package com.example.weiqigame.domain.usecase

import com.example.weiqigame.domain.logic.GameRuleEngine
import com.example.weiqigame.domain.logic.MoveValidationResult
import com.example.weiqigame.domain.logic.ScoreCalculator
import com.example.weiqigame.domain.logic.ScoreResult
import com.example.weiqigame.domain.model.BoardState
import com.example.weiqigame.domain.model.GameMode
import com.example.weiqigame.domain.model.GameResult
import com.example.weiqigame.domain.model.GameStatus
import com.example.weiqigame.domain.model.Move
import com.example.weiqigame.domain.model.Player

/**
 * 游戏管理器
 *
 * 负责管理一局游戏的完整生命周期：
 * - 游戏初始化（设置模式、分配颜色）
 * - 落子处理（验证规则、切换回合）
 * - 历史记录维护（用于打劫判定）
 * - 游戏结束判定
 */
class GameManager(
    private val ruleEngine: GameRuleEngine = GameRuleEngine(),
    private val scoreCalculator: ScoreCalculator = ScoreCalculator()
) {

    // 当前棋盘状态
    private lateinit var board: BoardState

    // 游戏模式
    private var gameMode: GameMode = GameMode.LOCAL

    // 当前回合（当前轮到谁落子）
    private var currentTurn: Int = BoardState.BLACK

    // 历史状态记录（用于打劫判定）
    private val history = mutableListOf<BoardState>()

    // 落子历史
    private val moves = mutableListOf<Move>()

    // 提子计数
    private var blackCapturedCount = 0
    private var whiteCapturedCount = 0

    // 玩家信息
    private lateinit var blackPlayer: Player
    private lateinit var whitePlayer: Player

    // 游戏状态
    var gameStatus: GameStatus = GameStatus.IDLE
        private set

    // 当前执子玩家
    val currentPlayerStone: Int
        get() = currentTurn

    // 是否是黑方回合
    val isBlackTurn: Boolean
        get() = currentTurn == BoardState.BLACK

    /**
     * 初始化一局新游戏
     *
     * @param mode 游戏模式（本地/联机）
     * @param boardSize 棋盘路数（9/13/19）
     */
    fun startNewGame(mode: GameMode = GameMode.LOCAL, boardSize: Int = 19) {
        // 重置所有状态
        board = BoardState.create(boardSize)
        history.clear()
        moves.clear()
        blackCapturedCount = 0
        whiteCapturedCount = 0
        currentTurn = BoardState.BLACK  // 黑棋先行
        gameMode = mode
        gameStatus = GameStatus.PLAYING

        // 设置玩家信息
        blackPlayer = Player("黑方", BoardState.BLACK, mode == GameMode.LOCAL || mode == GameMode.ONLINE_HOST)
        whitePlayer = Player("白方", BoardState.WHITE, mode == GameMode.LOCAL || mode == GameMode.ONLINE_CLIENT)
    }

    /**
     * 尝试在当前位置落子
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 落子结果，包含是否成功、提子数等信息
     */
    fun makeMove(x: Int, y: Int): MoveResult {
        if (gameStatus != GameStatus.PLAYING) {
            return MoveResult.Error("游戏未在进行中")
        }

        val move = Move(
            x = x,
            y = y,
            stone = currentTurn,
            sequence = moves.size + 1
        )

        // 验证并执行落子
        val validationResult = ruleEngine.validateAndExecute(board, move, history)

        return when (validationResult) {
            is MoveValidationResult.Valid -> {
                // 落子成功
                val capturedCount = validationResult.capturedStones.size

                // 更新提子计数
                if (currentTurn == BoardState.BLACK) {
                    whiteCapturedCount += capturedCount
                } else {
                    blackCapturedCount += capturedCount
                }

                // 保存历史状态（深拷贝）
                history.add(board.copy())

                // 记录落子
                moves.add(move.copy(captureCount = capturedCount))

                // 切换回合
                currentTurn = board.getOpponent(currentTurn)

                MoveResult.Success(
                    move = move,
                    capturedStones = validationResult.capturedStones,
                    blackCapturedTotal = blackCapturedCount,
                    whiteCapturedTotal = whiteCapturedCount,
                    isGameOver = false
                )
            }

            is MoveValidationResult.Invalid -> {
                MoveResult.Error(validationResult.message)
            }
        }
    }

    /**
     * 处理认输
     */
    fun resign(stone: Int): GameResult {
        gameStatus = GameStatus.FINISHED
        val winner = board.getOpponent(stone)

        return GameResult(
            winner = winner,
            blackScore = if (winner == BoardState.BLACK) 1f else 0f,
            whiteScore = if (winner == BoardState.WHITE) 1f else 0f,
            reason = "RESIGN"
        )
    }

    /**
     * 检查是否轮到指定颜色的玩家
     */
    fun isMyTurn(stone: Int): Boolean {
        return currentTurn == stone && gameStatus == GameStatus.PLAYING
    }

    /**
     * 获取当前棋盘状态（只读访问）
     */
    fun getBoard(): BoardState = board.copy()

    /**
     * 获取当前棋盘引用（用于渲染，谨慎修改）
     */
    fun getBoardRef(): BoardState = board

    /**
     * 获取提子数
     */
    fun getCaptureCount(stone: Int): Int {
        return if (stone == BoardState.BLACK) blackCapturedCount else whiteCapturedCount
    }

    /**
     * 获取落子历史
     */
    fun getMoveHistory(): List<Move> = moves.toList()

    /**
     * 预览指定位置的落子效果
     */
    fun previewMove(x: Int, y: Int): List<Pair<Int, Int>> {
        if (!board.isValidPosition(x, y) || !board.isEmpty(x, y)) {
            return emptyList()
        }
        val move = Move(x, y, currentTurn)
        return ruleEngine.previewCaptures(board, move)
    }

    /**
     * 检查指定位置是否是禁着点
     */
    fun isForbiddenPoint(x: Int, y: Int): Boolean {
        return ruleEngine.isForbiddenPoint(board, x, y, currentTurn, history) != null
    }

    /**
     * 获取禁着点原因（检查当前回合）
     */
    fun getForbiddenReason(x: Int, y: Int): String? {
        return getForbiddenReason(x, y, currentTurn)
    }

    /**
     * 获取禁着点原因（检查指定颜色）
     */
    fun getForbiddenReason(x: Int, y: Int, stone: Int): String? {
        return ruleEngine.isForbiddenPoint(board, x, y, stone, history)?.let {
            when (it) {
                com.example.weiqigame.domain.logic.InvalidReason.SUICIDE -> "禁着点：自杀步"
                com.example.weiqigame.domain.logic.InvalidReason.KO -> "禁着点：打劫"
                else -> null
            }
        }
    }

    /**
     * 计算当前局势得分
     */
    fun calculateScore(): ScoreResult {
        // 标准术语：blackCaptured 是黑方吃掉的白子，whiteCaptured 是白方吃掉的黑子
        val blackCaptured = getCaptureCount(BoardState.WHITE)   // 黑方战绩
        val whiteCaptured = getCaptureCount(BoardState.BLACK)   // 白方战绩
        return scoreCalculator.calculateScore(board, blackCaptured, whiteCaptured)
    }
}

/**
 * 落子结果密封类
 */
sealed class MoveResult {
    data class Success(
        val move: Move,
        val capturedStones: List<Pair<Int, Int>>,
        val blackCapturedTotal: Int,
        val whiteCapturedTotal: Int,
        val isGameOver: Boolean
    ) : MoveResult()

    data class Error(
        val message: String
    ) : MoveResult()
}
