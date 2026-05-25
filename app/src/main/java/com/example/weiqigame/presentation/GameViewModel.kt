package com.example.weiqigame.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.weiqigame.data.remote.ConnectionState
import com.example.weiqigame.data.repository.GameRepository
import com.example.weiqigame.domain.model.BoardState
import com.example.weiqigame.domain.model.GameMode
import com.example.weiqigame.domain.model.GameStatus
import com.example.weiqigame.domain.usecase.MoveResult
import com.example.weiqigame.domain.logic.ScoreResult
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

    /**
     * 最后错误/提示信息
     */
    val errorMessage: StateFlow<String?> = repository.lastError

    // 预览状态（长按或悬停时显示）
    private val _previewPosition = MutableStateFlow<Pair<Int, Int>?>(null)
    val previewPosition: StateFlow<Pair<Int, Int>?> = _previewPosition.asStateFlow()

    private val _previewCaptures = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val previewCaptures: StateFlow<List<Pair<Int, Int>>> = _previewCaptures.asStateFlow()

    // ========== 用户操作 ==========

    /**
     * 开始本地单机游戏
     */
    fun startLocalGame(boardSize: Int = 19) {
        repository.startLocalGame(boardSize)
    }

    /**
     * 处理棋盘点击
     */
    suspend fun onBoardClick(x: Int, y: Int): String? {
        // 检查是否在游戏状态
        if (gameStatus.value != GameStatus.PLAYING) {
            return "游戏未开始"
        }

        // 检查是否轮到本地玩家
        if (!isMyTurn.value) {
            return "等待对方落子"
        }

        // 尝试落子（在 IO 线程执行）
        val result = withContext(Dispatchers.IO) {
            repository.makeMove(x, y)
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
        repository.startAsHost {
            viewModelScope.launch(Dispatchers.IO) {
                // 等待连接后发送准备消息
                repository.sendReadyMessage()
                withContext(Dispatchers.Main) {
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
