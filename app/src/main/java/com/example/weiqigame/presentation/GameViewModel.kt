package com.example.weiqigame.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.weiqigame.data.remote.ConnectionState
import com.example.weiqigame.data.repository.GameRepository
import com.example.weiqigame.domain.model.BoardState
import com.example.weiqigame.domain.model.GameMode
import com.example.weiqigame.domain.model.GameStatus
import com.example.weiqigame.domain.usecase.MoveResult
import com.example.weiqigame.domain.logic.ScoreResult
import com.example.weiqigame.domain.ai.GoAI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 游戏 ViewModel
 *
 * 作为 UI 层与数据层之间的桥梁，管理游戏界面的状态。
 * 遵循 MVVM 架构，所有 UI 状态都通过 StateFlow 暴露。
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository()

    // AI 相关
    private var ai: GoAI? = null
    private var aiStone: Int = BoardState.WHITE  // 默认 AI 执白
    private var isAIGame: Boolean = false

    // AI 思考状态
    private val _isAIThinking = MutableStateFlow(false)
    val isAIThinking: StateFlow<Boolean> = _isAIThinking.asStateFlow()

    // 是否是 AI 对战模式（用于 UI 显示）
    private val _isAIGameMode = MutableStateFlow(false)
    val isAIGameMode: StateFlow<Boolean> = _isAIGameMode.asStateFlow()

    // AI 难度等级（用于 UI 显示）
    private val _aiDifficulty = MutableStateFlow(GoAI.Difficulty.MEDIUM)
    val aiDifficulty: StateFlow<GoAI.Difficulty> = _aiDifficulty.asStateFlow()

    // ========== UI 状态 ==========

    /**
     * 棋盘状态
     */
    val board: StateFlow<BoardState> = repository.board

    /**
     * 当前回合
     */
    val currentTurn: StateFlow<Int> = repository.currentTurn

    /**
     * 游戏状态
     */
    val gameStatus: StateFlow<GameStatus> = repository.gameStatus

    /**
     * 游戏模式
     */
    val gameMode: StateFlow<GameMode> = repository.gameMode

    /**
     * 提子数
     */
    val blackCaptured: StateFlow<Int> = repository.blackCaptured
    val whiteCaptured: StateFlow<Int> = repository.whiteCaptured

    /**
     * 实时结算结果
     */
    val currentScore: StateFlow<ScoreResult?> = repository.currentScore

    /**
     * 网络连接状态
     */
    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    /**
     * 是否是本地玩家回合
     */
    val isMyTurn: StateFlow<Boolean> = combine(
        repository.currentTurn,
        repository.myStone,
        repository.gameMode
    ) { turn, myStone, mode ->
        when (mode) {
            GameMode.LOCAL -> true
            else -> myStone == turn
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * 玩家颜色提示
     */
    val playerHint: StateFlow<String> = combine(
        repository.myStone,
        repository.gameMode,
        repository.currentTurn
    ) { myStone, mode, turn ->
        when (mode) {
            GameMode.LOCAL -> if (turn == BoardState.BLACK) "黑方回合" else "白方回合"
            GameMode.ONLINE_HOST -> "你执黑${if (turn == BoardState.BLACK) "（你的回合）" else "（等待对方）"}"
            GameMode.ONLINE_CLIENT -> "你执白${if (turn == BoardState.WHITE) "（你的回合）" else "（等待对方）"}"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "黑方回合")

    // 本地错误/提示信息（用于 AI 对战等本地逻辑）
    private val _localError = MutableStateFlow<String?>(null)
    val localError: StateFlow<String?> = _localError.asStateFlow()

    /**
     * 最后错误/提示信息（优先使用 repository 的）
     */
    val errorMessage: StateFlow<String?> = repository.lastError

    // 预览状态（长按或悬停时显示）
    private val _previewPosition = MutableStateFlow<Pair<Int, Int>?>(null)
    val previewPosition: StateFlow<Pair<Int, Int>?> = _previewPosition.asStateFlow()

    private val _previewCaptures = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val previewCaptures: StateFlow<List<Pair<Int, Int>>> = _previewCaptures.asStateFlow()

    // ========== 用户操作 ==========

    /**
     * 开始本地单机游戏（双人对弈）
     */
    fun startLocalGame(boardSize: Int = 19) {
        isAIGame = false
        _isAIGameMode.value = false
        ai = null
        repository.startLocalGame(boardSize)
    }

    /**
     * 开始人机对战
     *
     * @param boardSize 棋盘大小
     * @param playerStone 玩家执棋颜色 (BLACK/WHITE)，默认玩家执黑
     * @param difficulty AI 难度 (EASY/MEDIUM/HARD)
     */
    fun startAIGame(
        boardSize: Int = 19,
        playerStone: Int = BoardState.BLACK,
        difficulty: GoAI.Difficulty = GoAI.Difficulty.MEDIUM
    ) {
        isAIGame = true
        _isAIGameMode.value = true
        _aiDifficulty.value = difficulty
        aiStone = if (playerStone == BoardState.BLACK) BoardState.WHITE else BoardState.BLACK

        repository.startLocalGame(boardSize)

        // 创建 AI 实例
        ai = GoAI(repository.gameManager, difficulty)

        // 如果 AI 执黑，AI 先手
        if (aiStone == BoardState.BLACK) {
            triggerAIMove()
        }
    }

    /**
     * 触发 AI 落子
     */
    private fun triggerAIMove() {
        if (!isAIGame || gameStatus.value != GameStatus.PLAYING) return

        val currentTurn = repository.currentTurn.value
        if (currentTurn != aiStone) return  // 不是 AI 回合

        viewModelScope.launch(Dispatchers.Default) {
            _isAIThinking.value = true

            try {
                val aiInstance = ai ?: return@launch
                val move = aiInstance.makeMove(aiStone)

                withContext(Dispatchers.Main) {
                    if (move != null) {
                        val (x, y) = move
                        // AI 落子
                        repository.makeMove(x, y)
                    } else {
                        // AI 无处可下，认输
                        _localError.value = "AI 认输，你获胜了！"
                        repository.calculateFinalScore()
                    }
                }
            } catch (e: Exception) {
                // AI 出错，不阻塞游戏
                e.printStackTrace()
            } finally {
                _isAIThinking.value = false
            }
        }
    }

    /**
     * 处理棋盘点击
     */
    suspend fun onBoardClick(x: Int, y: Int): String? {
        // 检查是否在游戏状态
        val currentStatus = gameStatus.value
        if (currentStatus != GameStatus.PLAYING) {
            return "游戏未开始"
        }

        // 检查是否轮到本地玩家
        val myStone = repository.myStone.value
        val gameManagerTurn = repository.gameManager.currentPlayerStone

        // 直接使用 gameManager.currentTurn 进行判断
        if (myStone != null && gameManagerTurn != myStone) {
            return "等待对方落子"
        }

        // 尝试落子（在 IO 线程执行）
        val result = withContext(Dispatchers.IO) {
            repository.makeMove(x, y)
        }

        // 如果是对战 AI 且落子成功，触发 AI 思考
        if (result is MoveResult.Success && isAIGame) {
            triggerAIMove()
        }

        return when (result) {
            is MoveResult.Success -> null  // 落子成功，无提示
            is MoveResult.Error -> result.message  // 返回错误提示
        }
    }

    /**
     * 处理长按/悬停预览
     */
    fun onPreviewPosition(x: Int, y: Int) {
        _previewPosition.value = x to y
        _previewCaptures.value = repository.previewMove(x, y)
    }

    fun clearPreview() {
        _previewPosition.value = null
        _previewCaptures.value = emptyList()
    }

    /**
     * 提前结算
     */
    fun calculateScore() {
        repository.calculateFinalScore()
    }

    /**
     * 获取当前局势描述
     */
    fun getCurrentScoreDescription(): String {
        val score = repository.getCurrentScore()
        return score.getCurrentLeadDescription()
    }

    /**
     * 重新开始游戏
     *
     * 本地模式：重新开始一局
     * 网络模式：向对手发送重新开始的请求（当前简化处理为本地重置）
     */
    fun restartGame() {
        val mode = gameMode.value
        val boardSize = board.value.size

        if (mode == GameMode.LOCAL) {
            // 本地模式直接重新开始
            repository.startLocalGame(boardSize)
        } else {
            // 网络模式：断开当前连接，回到准备状态等待重新匹配
            // 简化处理：断开连接，玩家需要重新进入大厅
            repository.disconnect()
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        // repository 中需要添加清除错误的方法，或这里直接不处理
    }

    // ========== 网络对战 ==========

    /**
     * 创建房间（作为主机）
     */
    fun createRoom(onReady: () -> Unit = {}) {
        Log.d("GameViewModel", "Host: Creating room...")
        repository.startAsHost {
            viewModelScope.launch(Dispatchers.IO) {
                Log.d("GameViewModel", "Host: Client connected, sending ready message...")
                // 等待连接后发送准备消息
                repository.sendReadyMessage()
                Log.d("GameViewModel", "Host: Ready message sent, gameStatus=${repository.gameStatus.value}")
                withContext(Dispatchers.Main) {
                    Log.d("GameViewModel", "Host: Navigating to game screen")
                    onReady()
                }
            }
        }
    }

    /**
     * 加入房间（作为客户端）
     */
    fun joinRoom(address: java.net.InetAddress, onConnected: () -> Unit = {}) {
        repository.connectAsClient(address) {
            viewModelScope.launch(Dispatchers.IO) {
                // 网络操作在 IO 线程执行
                withContext(Dispatchers.Main) {
                    onConnected()
                }
            }
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        repository.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        repository.tearDown()
    }
}
