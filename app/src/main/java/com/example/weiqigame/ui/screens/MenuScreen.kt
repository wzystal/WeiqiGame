package com.example.weiqigame.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import com.example.weiqigame.domain.ai.GoAI
import com.example.weiqigame.domain.model.BoardState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weiqigame.presentation.GameViewModel

/**
 * 主菜单界面
 *
 * 应用入口，提供模式选择和导航
 */
@Composable
fun MenuScreen(
    gameViewModel: GameViewModel,
    onNavigateToGame: () -> Unit,
    onNavigateToLobby: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 控制是否显示难度选择
    var showDifficultySelector by remember { mutableStateOf(false) }

    Scaffold { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 标题
                Text(
                    text = "围棋对弈",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "围棋 Go / Baduk / Weiqi",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(48.dp))

                // 模式选择按钮
                Column(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 人机对战主按钮
                    Button(
                        onClick = { showDifficultySelector = !showDifficultySelector },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = if (showDifficultySelector) "人机对战 ▲" else "人机对战 ▼",
                            fontSize = 18.sp
                        )
                    }

                    // 难度选择器（展开状态）
                    if (showDifficultySelector) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "选择难度",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                // 简单
                                OutlinedButton(
                                    onClick = {
                                        gameViewModel.startAIGame(
                                            boardSize = 19,
                                            playerStone = BoardState.BLACK,
                                            difficulty = GoAI.Difficulty.EASY
                                        )
                                        onNavigateToGame()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Text("🌱 简单模式")
                                }

                                // 中等
                                Button(
                                    onClick = {
                                        gameViewModel.startAIGame(
                                            boardSize = 19,
                                            playerStone = BoardState.BLACK,
                                            difficulty = GoAI.Difficulty.MEDIUM
                                        )
                                        onNavigateToGame()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("⚔️ 中等模式")
                                }

                                // 困难
                                Button(
                                    onClick = {
                                        gameViewModel.startAIGame(
                                            boardSize = 19,
                                            playerStone = BoardState.BLACK,
                                            difficulty = GoAI.Difficulty.HARD
                                        )
                                        onNavigateToGame()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("👑 困难模式")
                                }
                            }
                        }
                    }

                    // 双人对战（本地）
                    OutlinedButton(
                        onClick = {
                            gameViewModel.startLocalGame()
                            onNavigateToGame()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "双人对战",
                            fontSize = 16.sp
                        )
                    }

                    // 局域网联机
                    OutlinedButton(
                        onClick = onNavigateToLobby,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "局域网联机",
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 规则说明
                    Text(
                        text = "游戏规则",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { /* 打开规则说明 */ }
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // 版本信息
                Text(
                    text = "v1.0 - 19路标准棋盘",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "支持单机双人 / 局域网 P2P 对战\n支持提子、自杀判定、打劫规则",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
