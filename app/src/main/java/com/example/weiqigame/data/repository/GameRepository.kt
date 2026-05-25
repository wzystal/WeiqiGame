package com.example.weiqigame.data.repository

import com.example.weiqigame.data.remote.ConnectionState
import com.example.weiqigame.data.remote.GoMessage
import com.example.weiqigame.data.remote.NsdHelper
import com.example.weiqigame.data.remote.TcpGameConnection
import com.example.weiqigame.domain.model.BoardState
import com.example.weiqigame.domain.model.GameMode
import com.example.weiqigame.domain.model.GameResult
import com.example.weiqigame.domain.model.GameStatus
import com.example.weiqigame.domain.model.Move
import com.example.weiqigame.domain.usecase.GameManager
import com.example.weiqigame.domain.usecase.MoveResult
import com.example.weiqigame.domain.logic.ScoreResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 游戏仓库
 *
 * 整合本地游戏逻辑和网络对战功能，作为数据层的统一出口。
 * 向上层提供游戏状态、棋盘数据、网络连接等数据的观察接口。
 */
class GameRepository(
    val gameManager: GameManager = GameManager(),
    private val tcpConnection: TcpGameConnection = TcpGameConnection(),
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    // ========== 游戏状态 ==========

    private val _gameStatus = MutableStateFlow(GameStatus.IDLE)
    val gameStatus: StateFlow<GameStatus> = _gameStatus.asStateFlow()

    private val _gameMode = MutableStateFlow(GameMode.LOCAL)
    val gameMode: StateFlow<GameMode> = _gameMode.asStateFlow()

    private val _currentTurn = MutableStateFlow(BoardState.BLACK)
    val currentTurn: StateFlow<Int> = _currentTurn.asStateFlow()

    private val _board = MutableStateFlow(BoardState.create())
    val board: StateFlow<BoardState> = _board.asStateFlow()

    // 提子数
    private val _blackCaptured = MutableStateFlow(0)
    val blackCaptured: StateFlow<Int> = _blackCaptured.asStateFlow()

    private val _whiteCaptured = MutableStateFlow(0)
    val whiteCaptured: StateFlow<Int> = _whiteCaptured.asStateFlow()

    // 本地玩家执黑还是执白（网络对战时有效）
    private val _myStone = MutableStateFlow<Int?>(null)
    val myStone: StateFlow<Int?> = _myStone.asStateFlow()

    // 最后错误信息
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // 实时结算结果（用于显示当前局势）
    private val _currentScore = MutableStateFlow<ScoreResult?>(null)
    val currentScore: StateFlow<ScoreResult?> = _currentScore.asStateFlow()

    // ========== 网络状态 ==========

    val connectionState: StateFlow<ConnectionState> = tcpConnection.connectionState

    init {
        // 监听网络消息
        externalScope.launch {
            tcpConnection.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }

        // 监听网络错误
        externalScope.launch {
            tcpConnection.errors.collect { error ->
                _lastError.value = when (error) {
                    is com.example.weiqigame.data.remote.NetworkError.ConnectionFailed ->
                        "连接失败: ${error.message}"
                    is com.example.weiqigame.data.remote.NetworkError.Disconnected ->
                        "连接断开: ${error.reason}"
                    is com.example.weiqigame.data.remote.NetworkError.ProtocolError ->
                        "通信错误: ${error.message}"
                }
            }
        }
    }

    // ========== 本地游戏操作 ==========

    /**
     * 开始本地单机游戏
     */
    fun startLocalGame(boardSize: Int = 19) {
        gameManager.startNewGame(GameMode.LOCAL, boardSize)
        updateGameState()
    }

    /**
     * 尝试落子
     */
    suspend fun makeMove(x: Int, y: Int): MoveResult {
        // 网络模式下检查是否轮到自己
        if (_gameMode.value != GameMode.LOCAL) {
            val myColor = _myStone.value
            if (myColor == null || gameManager.currentPlayerStone != myColor) {
                return MoveResult.Error("不是你的回合")
            }
        }

        val result = gameManager.makeMove(x, y)

        if (result is MoveResult.Success) {
            // 更新状态
            updateGameState()

            // 网络模式下发送给对方（在 IO 线程执行）
            if (_gameMode.value != GameMode.LOCAL) {
                val message = GoMessage.Move(
                    x = x,
                    y = y,
                    stone = result.move.stone,
                    sequence = result.move.sequence,
                    captureCount = result.capturedStones.size
                )
                withContext(Dispatchers.IO) {
                    tcpConnection.sendMessage(message)
                }
            }
        }

        return result
    }

    /**
     * 认输
     */
    suspend fun resign() {
        val mode = _gameMode.value
        val myColor = if (mode == GameMode.LOCAL) {
            // 单机模式：当前轮到谁，就是谁认输
            gameManager.currentPlayerStone
        } else {
            // 网络模式：使用分配的颜色
            _myStone.value ?: BoardState.BLACK
        }

        val result = gameManager.resign(myColor)

        // 网络模式下通知对方（在 IO 线程执行）
        if (mode != GameMode.LOCAL) {
            withContext(Dispatchers.IO) {
                tcpConnection.sendMessage(GoMessage.Resign())
            }
        }

        _gameStatus.value = GameStatus.FINISHED
        _lastError.value = "游戏结束: ${if (result.winner == BoardState.BLACK) "黑方" else "白方"}获胜"
    }

    /**
     * 提前结算
     *
     * 根据当前棋盘计算双方得分，判定胜负
     */
    fun calculateFinalScore(): ScoreResult {
        val result = gameManager.calculateScore()
        _currentScore.value = result
        _gameStatus.value = GameStatus.FINISHED
        _lastError.value = "结算完成: ${result.getWinDescription()}"
        return result
    }

    /**
     * 获取当前局势得分（实时）
     */
    fun getCurrentScore(): ScoreResult {
        return gameManager.calculateScore()
    }

    /**
     * 检查是否轮到自己
     */
    fun isMyTurn(): Boolean {
        if (_gameMode.value == GameMode.LOCAL) return true
        val myColor = _myStone.value ?: return false
        return gameManager.isMyTurn(myColor)
    }

    // ========== 网络对战操作 ==========

    /**
     * 作为主机创建房间
     */
    fun startAsHost(onReady: () -> Unit = {}) {
        _gameMode.value = GameMode.ONLINE_HOST
        _myStone.value = BoardState.BLACK  // 主机执黑

        // 启动 TCP 监听
        tcpConnection.startAsHost { onReady() }
    }

    /**
     * 作为客户端加入房间
     */
    fun connectAsClient(address: java.net.InetAddress, onConnected: () -> Unit = {}) {
        _gameMode.value = GameMode.ONLINE_CLIENT
        _myStone.value = BoardState.WHITE  // 客户端执白

        tcpConnection.connectAsClient(address) {
            onConnected()
        }
    }

    /**
     * 发送游戏准备消息
     */
    suspend fun sendReadyMessage(boardSize: Int = 19) {
        val opponentColor = if (_myStone.value == BoardState.BLACK) BoardState.WHITE else BoardState.BLACK
        withContext(Dispatchers.IO) {
            tcpConnection.sendMessage(GoMessage.Ready(opponentColor, boardSize))
        }
    }

    /**
     * 断开网络连接
     */
    fun disconnect() {
        tcpConnection.disconnect()
        if (_gameStatus.value == GameStatus.PLAYING) {
            _gameStatus.value = GameStatus.IDLE
        }
    }

    // ========== 内部处理 ==========

    /**
     * 处理收到的网络消息
     */
    private fun handleIncomingMessage(message: GoMessage) {
        when (message) {
            is GoMessage.Move -> {
                // 对方落子，同步到本地游戏状态
                handleOpponentMove(message)
            }

            is GoMessage.Ready -> {
                // 收到准备消息，初始化游戏
                gameManager.startNewGame(_gameMode.value, message.boardSize)
                _myStone.value = message.assignedColor
                updateGameState()
                _gameStatus.value = GameStatus.PLAYING
            }

            is GoMessage.Resign -> {
                // 对方认输
                _gameStatus.value = GameStatus.FINISHED
                val myColor = _myStone.value ?: BoardState.BLACK
                _lastError.value = "对方认输，${if (myColor == BoardState.BLACK) "黑方" else "白方"}获胜"
            }

            is GoMessage.GameOver -> {
                _gameStatus.value = GameStatus.FINISHED
                _lastError.value = "游戏结束: ${message.reason}"
            }

            is GoMessage.Heartbeat -> {
                // 心跳消息，无需处理
            }

            else -> {
                // 其他消息类型暂不处理
            }
        }
    }

    /**
     * 处理对方落子
     */
    private fun handleOpponentMove(message: GoMessage.Move) {
        // 构造 Move 对象，但不执行验证（因为是对方的落子）
        val move = Move(message.x, message.y, message.stone, message.sequence, message.captureCount)

        // 直接在棋盘执行落子
        val board = gameManager.getBoardRef()
        board.set(move.x, move.y, move.stone)

        // 如果有提子，移除被提棋子
        // 这里简化处理，实际应该复用游戏管理器的提子逻辑
        // 但为了确保规则一致性，应该重新设计

        // 更新状态
        updateGameState()
    }

    /**
     * 更新游戏状态流
     */
    private fun updateGameState() {
        _gameStatus.value = gameManager.gameStatus
        _currentTurn.value = gameManager.currentPlayerStone
        _board.value = gameManager.getBoard()
        // 标准围棋术语：黑提子 = 黑方吃掉的白子数，白提子 = 白方吃掉的黑子数
        _blackCaptured.value = gameManager.getCaptureCount(BoardState.WHITE)   // 黑方提子数
        _whiteCaptured.value = gameManager.getCaptureCount(BoardState.BLACK)   // 白方提子数

        // 实时更新当前局势
        _currentScore.value = gameManager.calculateScore()
    }

    /**
     * 预览落子提子效果
     */
    fun previewMove(x: Int, y: Int): List<Pair<Int, Int>> {
        return gameManager.previewMove(x, y)
    }

    /**
     * 获取禁着点提示
     */
    fun getForbiddenHint(x: Int, y: Int): String? {
        return gameManager.getForbiddenReason(x, y)
    }

    /**
     * 清理资源
     */
    fun tearDown() {
        tcpConnection.tearDown()
    }
}
