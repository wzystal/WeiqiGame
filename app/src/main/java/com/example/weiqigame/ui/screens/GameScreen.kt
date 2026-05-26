package com.example.weiqigame.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.weiqigame.data.remote.ConnectionState
import com.example.weiqigame.domain.model.GameMode
import com.example.weiqigame.domain.model.GameStatus
import com.example.weiqigame.presentation.GameViewModel
import com.example.weiqigame.ui.components.GameControls
import com.example.weiqigame.ui.components.GoBoard

/**
 * 游戏界面
 *
 * 主游戏界面，包含棋盘、控制面板和状态显示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onBackToMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 收集状态
    val board by viewModel.board.collectAsState()
    val currentTurn by viewModel.currentTurn.collectAsState()
    val gameStatus by viewModel.gameStatus.collectAsState()
    val gameMode by viewModel.gameMode.collectAsState()
    val blackCaptured by viewModel.blackCaptured.collectAsState()
    val whiteCaptured by viewModel.whiteCaptured.collectAsState()
    val isMyTurn by viewModel.isMyTurn.collectAsState()
    val playerHint by viewModel.playerHint.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val localError by viewModel.localError.collectAsState()
    val previewPosition by viewModel.previewPosition.collectAsState()
    val previewCaptures by viewModel.previewCaptures.collectAsState()
    val isAIGameMode by viewModel.isAIGameMode.collectAsState()
    val aiDifficulty by viewModel.aiDifficulty.collectAsState()
    val scope = rememberCoroutineScope()

    // 点击错误提示（用于显示非回合落子等错误）
    var clickError by remember { mutableStateOf<String?>(null) }

    // 显示错误提示
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    // 显示本地错误提示
    LaunchedEffect(localError) {
        localError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // 显示点击错误提示
    LaunchedEffect(clickError) {
        clickError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            clickError = null
        }
    }

    // 网络连接断开时返回菜单（仅被动断开时触发）
    LaunchedEffect(connectionState, gameMode) {
        if (gameMode != GameMode.LOCAL && connectionState == ConnectionState.DISCONNECTED) {
            if (gameStatus == GameStatus.PLAYING) {
                Toast.makeText(context, "连接已断开", Toast.LENGTH_LONG).show()
                onBackToMenu()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (gameMode) {
                        GameMode.LOCAL -> if (isAIGameMode) {
                            val difficultyText = when (aiDifficulty) {
                                com.example.weiqigame.domain.ai.GoAI.Difficulty.EASY -> "简单"
                                com.example.weiqigame.domain.ai.GoAI.Difficulty.MEDIUM -> "中等"
                                com.example.weiqigame.domain.ai.GoAI.Difficulty.HARD -> "困难"
                            }
                            "人机对战（$difficultyText）"
                        } else {
                            "双人对战"
                        }
                        GameMode.ONLINE_HOST -> "局域网对战（主机）"
                        GameMode.ONLINE_CLIENT -> "局域网对战（客户端）"
                    }
                    Text(text = titleText)
                },
                navigationIcon = {
                    // 可以添加返回按钮
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 32.dp)  // 增大顶部间距，确保棋盘不被标题栏遮挡
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 网络连接状态指示（仅网络模式）
                if (gameMode != GameMode.LOCAL) {
                    ConnectionStatus(connectionState)
                }

                // 等待中提示
                if (gameStatus == GameStatus.PREPARING) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                text = "等待对手连接...",
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                } else {
                    // 棋盘（填满可用空间，设置最小高度确保显示完整）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 8.dp)
                    ) {
                        GoBoard(
                            board = board,
                            currentStone = currentTurn,
                            previewPosition = previewPosition,
                            previewCaptures = previewCaptures,
                            isMyTurn = isMyTurn,
                            modifier = Modifier.fillMaxSize(),
                            onBoardClick = { x, y ->
                                scope.launch {
                                    val error = viewModel.onBoardClick(x, y)
                                    error?.let {
                                        // 同时设置 Toast 和状态变量
                                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                        clickError = it
                                    }
                                }
                            },
                            onPreviewChange = { x, y ->
                                viewModel.onPreviewPosition(x, y)
                            }
                        )

                        // 显示点击错误提示（在棋盘上方）
                        clickError?.let { error ->
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(8.dp)
                                    .fillMaxWidth(0.7f),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }

                // 控制面板（紧凑布局）
                val currentScore by viewModel.currentScore.collectAsState()

                GameControls(
                    currentTurn = currentTurn,
                    blackCaptured = blackCaptured,
                    whiteCaptured = whiteCaptured,
                    gameStatus = gameStatus,
                    playerHint = playerHint,
                    currentScore = currentScore,
                    onCalculateScore = {
                        viewModel.calculateScore()
                    },
                    onRestart = {
                        viewModel.restartGame()
                    },
                    onBackToMenu = onBackToMenu
                )
            }
        }
    }
}

/**
 * 网络连接状态指示
 */
@Composable
private fun ConnectionStatus(connectionState: ConnectionState) {
    val (text, color) = when (connectionState) {
        ConnectionState.CONNECTED -> "已连接" to MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING -> "连接中..." to MaterialTheme.colorScheme.secondary
        ConnectionState.RECONNECTING -> "重连中..." to MaterialTheme.colorScheme.tertiary
        ConnectionState.DISCONNECTED -> "未连接" to MaterialTheme.colorScheme.error
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
