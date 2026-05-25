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
import android.util.Log

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
        // 断开网络连接（如果之前有联机连接）
        tcpConnection.disconnect()
        _myStone.value = null
        _gameMode.value = GameMode.LOCAL
        gameManager.startNewGame(GameMode.LOCAL, boardSize)
        _gameStatus.value = GameStatus.PLAYING
        updateGameState()
    }

    /**
     * 尝试落子
     */
    suspend fun makeMove(x: Int, y: Int): MoveResult {
        // 网络模式下检查是否轮到自己
        if (_gameMode.value != GameMode.LOCAL) {
            val myColor = _myStone.value
            // 使用 gameManager.currentPlayerStone 作为唯一真实来源
            val currentTurn = gameManager.currentPlayerStone
            if (myColor == null || currentTurn != myColor) {
                Log.d("GameRepository", "makeMove被拒绝: myColor=$myColor, currentTurn=$currentTurn")
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

        // 主机也要初始化自己的游戏状态（因为主机不会收到自己发送的消息）
        gameManager.startNewGame(_gameMode.value, boardSize)
        _gameStatus.value = GameStatus.PLAYING
        updateGameState()
    }

    /**
     * 断开网络连接
     */
    fun disconnect() {
        tcpConnection.disconnect()
        _myStone.value = null
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
                _gameStatus.value = GameStatus.PLAYING
                updateGameState()
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
        Log.d("GameRepository", "收到对方落子: (${message.x}, ${message.y}), stone=${message.stone}, seq=${message.sequence}")

        // 验证落子颜色是否与当前回合匹配
        val currentTurn = gameManager.currentPlayerStone
        if (message.stone != currentTurn) {
            Log.e("GameRepository", "回合验证失败：消息stone=${message.stone}, 当前回合=$currentTurn")
            _lastError.value = "回合同步错误：对方在错误的回合落子"
            return
        }

        // 使用 gameManager.makeMove 进行完整的规则验证和落子
        val result = gameManager.makeMove(message.x, message.y)

        when (result) {
            is MoveResult.Success -> {
                Log.d("GameRepository", "对方落子成功，提子数: ${result.capturedStones.size}")
                // makeMove 已经自动切换了回合，updateGameState 会同步到 repository
                updateGameState()
            }
            is MoveResult.Error -> {
                Log.e("GameRepository", "对方落子验证失败: ${result.message}")
                _lastError.value = "对方落子无效: ${result.message}"
            }
        }
    }

    /**
     * 更新游戏状态流（同步GameManager状态到Repository）
     */
    private fun updateGameState() {
        // 始终从 GameManager 同步 currentTurn，确保状态一致
        _currentTurn.value = gameManager.currentPlayerStone

        _board.value = gameManager.getBoard()

        // 只在非PLAYING状态下同步gameStatus，避免覆盖联机模式的状态设置
        if (_gameStatus.value != GameStatus.PLAYING) {
            _gameStatus.value = gameManager.gameStatus
        }

        // 同步提子数（getCaptureCount返回的是该颜色吃掉的对方棋子数）
        // blackCaptured = 黑方吃掉的白子数 = getCaptureCount(WHITE)
        // whiteCaptured = 白方吃掉的黑子数 = getCaptureCount(BLACK)
        _blackCaptured.value = gameManager.getCaptureCount(BoardState.WHITE)   // 黑提子数
        _whiteCaptured.value = gameManager.getCaptureCount(BoardState.BLACK)   // 白提子数

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
