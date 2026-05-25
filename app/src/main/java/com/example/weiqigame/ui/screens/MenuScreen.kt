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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                    // 单机对战
                    Button(
                        onClick = {
                            gameViewModel.startLocalGame()
                            onNavigateToGame()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "单机对战",
                            fontSize = 18.sp
                        )
                    }

                    // 局域网联机
                    Button(
                        onClick = onNavigateToLobby,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "局域网联机",
                            fontSize = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 规则说明（可扩展）
                    OutlinedButton(
                        onClick = { /* 打开规则说明 */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("围棋规则")
                    }
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
