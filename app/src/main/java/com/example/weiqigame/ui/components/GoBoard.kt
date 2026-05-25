package com.example.weiqigame.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.weiqigame.domain.model.BoardState

/**
 * 围棋棋盘组件
 *
 * 使用 Compose Canvas 绘制的 19x19 围棋棋盘
 *
 * @param board 当前棋盘状态
 * @param currentStone 当前要落子的颜色（用于预览）
 * @param previewPosition 预览位置（手指按下但未松开）
 * @param previewCaptures 预览提子效果（会被提掉的棋子）
 * @param isMyTurn 是否轮到当前玩家
 * @param onBoardClick 点击回调，返回落子坐标 (x, y)
 * @param onPreviewChange 预览位置变化回调
 */
@Composable
fun GoBoard(
    board: BoardState,
    currentStone: Int,
    previewPosition: Pair<Int, Int>?,
    previewCaptures: List<Pair<Int, Int>>,
    isMyTurn: Boolean,
    modifier: Modifier = Modifier,
    onBoardClick: (Int, Int) -> Unit,
    onPreviewChange: (Int, Int) -> Unit = { _, _ -> }
) {
    // 棋盘颜色
    val boardColor = Color(0xFFDCB35C)  // 木质棋盘色
    val lineColor = Color(0xFF000000)    // 黑线
    val starPointColor = Color(0xFF000000) // 星位颜色

    // 计算棋盘参数（增大边距，让格子间距更大）
    val density = LocalDensity.current
    val paddingPx = with(density) { 24.dp.toPx() }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(boardColor)
            .padding(4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isMyTurn) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (!isMyTurn) return@detectTapGestures

                            val (x, y) = offsetToBoardCoordinate(
                                offset,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                board.size,
                                paddingPx
                            )

                            if (board.isValidPosition(x, y)) {
                                onPreviewChange(x, y)
                                tryAwaitRelease()
                                onPreviewChange(-1, -1) // 清除预览
                            }
                        },
                        onTap = { offset ->
                            if (!isMyTurn) return@detectTapGestures

                            val (x, y) = offsetToBoardCoordinate(
                                offset,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                board.size,
                                paddingPx
                            )

                            if (board.isValidPosition(x, y)) {
                                onBoardClick(x, y)
                            }
                        }
                    )
                }
        ) {
            val boardSizePx = size.width.coerceAtMost(size.height)
            val gridSize = (boardSizePx - 2 * paddingPx) / (board.size - 1)

            // 绘制棋盘格线
            drawGridLines(board.size, paddingPx, gridSize, lineColor)

            // 绘制星位（天元和四四路点）
            drawStarPoints(board.size, paddingPx, gridSize, starPointColor)

            // 绘制所有已落棋子
            drawStones(board, paddingPx, gridSize)

            // 绘制预览棋子（手指按下位置）
            previewPosition?.let { (x, y) ->
                if (board.isValidPosition(x, y) && board.isEmpty(x, y)) {
                    drawPreviewStone(x, y, currentStone, paddingPx, gridSize)
                }
            }

            // 高亮预览提子
            previewCaptures.forEach { (x, y) ->
                drawCapturePreview(x, y, paddingPx, gridSize)
            }
        }
    }
}

/**
 * 将触摸坐标转换为棋盘坐标
 */
private fun offsetToBoardCoordinate(
    offset: Offset,
    width: Float,
    height: Float,
    boardSize: Int,
    paddingPx: Float
): Pair<Int, Int> {
    val boardSizePx = width.coerceAtMost(height)
    val gridSize = (boardSizePx - 2 * paddingPx) / (boardSize - 1)

    // 计算点击位置最近的交叉点（使用标准算法，确保精准）
    // 四舍五入到最近的交叉点
    val x = ((offset.x - paddingPx + gridSize / 2) / gridSize).toInt()
        .coerceIn(0 until boardSize)
    val y = ((offset.y - paddingPx + gridSize / 2) / gridSize).toInt()
        .coerceIn(0 until boardSize)

    return x to y
}

/**
 * 绘制棋盘格线
 */
private fun DrawScope.drawGridLines(
    boardSize: Int,
    padding: Float,
    gridSize: Float,
    lineColor: Color
) {
    val start = padding
    val end = padding + gridSize * (boardSize - 1)

    // 横线
    for (i in 0 until boardSize) {
        val y = padding + i * gridSize
        drawLine(
            color = lineColor,
            start = Offset(start, y),
            end = Offset(end, y),
            strokeWidth = 1.5f,
            cap = StrokeCap.Round
        )
    }

    // 竖线
    for (i in 0 until boardSize) {
        val x = padding + i * gridSize
        drawLine(
            color = lineColor,
            start = Offset(x, start),
            end = Offset(x, end),
            strokeWidth = 1.5f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 绘制星位点
 */
private fun DrawScope.drawStarPoints(
    boardSize: Int,
    padding: Float,
    gridSize: Float,
    starColor: Color
) {
    // 标准 19 路棋盘的星位
    val starPoints = if (boardSize == 19) {
        listOf(
            3 to 3, 3 to 9, 3 to 15,
            9 to 3, 9 to 9, 9 to 15,
            15 to 3, 15 to 9, 15 to 15
        )
    } else {
        // 其他路数简化处理，只在中心点绘制
        listOf(boardSize / 2 to boardSize / 2)
    }

    val starRadius = gridSize * 0.1f

    starPoints.forEach { (x, y) ->
        val centerX = padding + x * gridSize
        val centerY = padding + y * gridSize
        drawCircle(
            color = starColor,
            radius = starRadius,
            center = Offset(centerX, centerY)
        )
    }
}

/**
 * 绘制所有棋子
 */
private fun DrawScope.drawStones(
    board: BoardState,
    padding: Float,
    gridSize: Float
) {
    val stoneRadius = gridSize * 0.45f

    for (y in 0 until board.size) {
        for (x in 0 until board.size) {
            val stone = board.get(x, y)
            if (stone != BoardState.EMPTY) {
                val centerX = padding + x * gridSize
                val centerY = padding + y * gridSize

                val stoneColor = if (stone == BoardState.BLACK) {
                    Color(0xFF000000)
                } else {
                    Color(0xFFFFFFFF)
                }

                drawCircle(
                    color = stoneColor,
                    radius = stoneRadius,
                    center = Offset(centerX, centerY)
                )

                // 白棋添加边框
                if (stone == BoardState.WHITE) {
                    drawCircle(
                        color = Color(0xFF888888),
                        radius = stoneRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1f)
                    )
                }

                // 黑棋添加高光效果
                if (stone == BoardState.BLACK) {
                    drawCircle(
                        color = Color(0xFF333333),
                        radius = stoneRadius * 0.3f,
                        center = Offset(centerX - stoneRadius * 0.3f, centerY - stoneRadius * 0.3f)
                    )
                }
            }
        }
    }
}

/**
 * 绘制预览棋子（半透明）
 */
private fun DrawScope.drawPreviewStone(
    x: Int,
    y: Int,
    stone: Int,
    padding: Float,
    gridSize: Float
) {
    val stoneRadius = gridSize * 0.45f
    val centerX = padding + x * gridSize
    val centerY = padding + y * gridSize

    val stoneColor = if (stone == BoardState.BLACK) {
        Color(0xFF000000).copy(alpha = 0.5f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.7f)
    }

    drawCircle(
        color = stoneColor,
        radius = stoneRadius,
        center = Offset(centerX, centerY)
    )
}

/**
 * 绘制提子预览（红色标记）
 */
private fun DrawScope.drawCapturePreview(
    x: Int,
    y: Int,
    padding: Float,
    gridSize: Float
) {
    val centerX = padding + x * gridSize
    val centerY = padding + y * gridSize
    val radius = gridSize * 0.2f

    // 绘制红色叉号
    drawLine(
        color = Color.Red,
        start = Offset(centerX - radius, centerY - radius),
        end = Offset(centerX + radius, centerY + radius),
        strokeWidth = 3f
    )
    drawLine(
        color = Color.Red,
        start = Offset(centerX + radius, centerY - radius),
        end = Offset(centerX - radius, centerY + radius),
        strokeWidth = 3f
    )
}
