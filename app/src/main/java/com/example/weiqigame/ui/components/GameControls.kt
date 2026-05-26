package com.example.weiqigame.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.weiqigame.domain.model.BoardState
import com.example.weiqigame.domain.model.GameStatus
import com.example.weiqigame.domain.logic.ScoreResult

/**
 * 游戏控制面板
 *
 * 显示当前回合、提子数、实时局势、结算按钮等信息
 */
@Composable
fun GameControls(
    currentTurn: Int,
    blackCaptured: Int,
    whiteCaptured: Int,
    gameStatus: GameStatus,
    playerHint: String,
    currentScore: ScoreResult?,
    onCalculateScore: () -> Unit,
    onRestart: () -> Unit = {},
    onBackToMenu: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 回合指示和提示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 当前回合指示
            TurnIndicator(currentTurn = currentTurn)

            // 玩家提示
            Text(
                text = playerHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 提子统计
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CaptureCounter(
                stone = BoardState.BLACK,
                count = blackCaptured,
                label = "黑"
            )
            CaptureCounter(
                stone = BoardState.WHITE,
                count = whiteCaptured,
                label = "白"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 实时局势显示
        if (currentScore != null && gameStatus == GameStatus.PLAYING) {
            LiveScoreCard(score = currentScore)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 结算结果显示
        if (gameStatus == GameStatus.FINISHED && currentScore != null) {
            FinalScoreCard(score = currentScore)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 按钮区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 提前结算按钮（只在游戏进行中显示）
            if (gameStatus == GameStatus.PLAYING) {
                Button(
                    onClick = onCalculateScore,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("提前结算", fontSize = 12.sp)
                }
            }

            // 重新开始按钮
            if (gameStatus == GameStatus.FINISHED) {
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("重新开始", fontSize = 12.sp)
                }
            }

            // 返回菜单按钮
            OutlinedButton(
                onClick = onBackToMenu,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text("返回菜单", fontSize = 12.sp)
            }
        }
    }
}

/**
 * 回合指示器
 */
@Composable
private fun TurnIndicator(currentTurn: Int) {
    val isBlack = currentTurn == BoardState.BLACK
    val color = if (isBlack) Color.Black else Color.White
    val textColor = if (isBlack) Color.White else Color.Black
    val label = if (isBlack) "黑" else "白"

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 棋子图标
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "$label",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 提子计数器
 */
@Composable
private fun CaptureCounter(
    stone: Int,
    count: Int,
    label: String
) {
    val color = if (stone == BoardState.BLACK) Color.Black else Color.White
    val textColor = if (stone == BoardState.BLACK) Color.White else Color.Black

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // 棋子图标
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "$count",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 实时局势卡片
 */
@Composable
private fun LiveScoreCard(score: ScoreResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = score.getCurrentLeadDescription(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "⚫${score.blackScore.format(1)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "⚪${score.whiteScore.format(1)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * 结算结果卡片
 */
@Composable
private fun FinalScoreCard(score: ScoreResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (score.winner) {
                BoardState.BLACK -> MaterialTheme.colorScheme.inversePrimary
                BoardState.WHITE -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 获胜方
            Text(
                text = score.getWinDescription(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 详细得分
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 黑方
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚫",
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${score.blackScore.format(1)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // VS
                Text(
                    text = "VS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )

                // 白方
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚪",
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${score.whiteScore.format(1)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 格式化数字，保留指定小数位
 */
private fun Double.format(decimals: Int): String {
    return String.format("%.${decimals}f", this)
}
