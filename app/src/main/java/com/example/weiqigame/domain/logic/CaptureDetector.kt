package com.example.weiqigame.domain.logic

import com.example.weiqigame.domain.model.BoardState
import com.example.weiqigame.domain.model.Move

/**
 * 提子检测器
 *
 * 检测并执行落子后的提子操作
 */
class CaptureDetector(private val libertyCalculator: LibertyCalculator) {

    /**
     * 检测并执行提子
     *
     * 当一颗棋子落下后，检查其四个方向的相邻对方棋子群，
     * 若某对方棋子群气数变为 0，则提取该群所有棋子
     *
     * @param board 棋盘状态（会被修改，移除被提棋子）
     * @param move 刚落下的棋子
     * @return 被提子的坐标列表
     */
    fun detectAndCapture(board: BoardState, move: Move): List<Pair<Int, Int>> {
        val capturedStones = mutableListOf<Pair<Int, Int>>()
        val opponent = board.getOpponent(move.stone)

        // 检查四个方向的邻居
        val neighbors = getNeighbors(move.x, move.y, board.size)

        for ((nx, ny) in neighbors) {
            // 只检查对手棋子
            if (board.get(nx, ny) == opponent) {
                // 计算该棋子群的气数
                val liberties = libertyCalculator.calculateLiberties(board, nx, ny)

                if (liberties == 0) {
                    // 该棋子群无气，执行提子
                    val stonesToCapture = libertyCalculator.getStoneGroup(board, nx, ny)
                    capturedStones.addAll(stonesToCapture)

                    // 从棋盘上移除这些棋子
                    for ((cx, cy) in stonesToCapture) {
                        board.remove(cx, cy)
                    }
                }
            }
        }

        return capturedStones
    }

    /**
     * 仅检测会被提子的位置，不实际执行提子
     *
     * 用于落子前的预判（如显示提子提示）
     */
    fun previewCaptures(board: BoardState, move: Move): List<Pair<Int, Int>> {
        val capturedStones = mutableListOf<Pair<Int, Int>>()
        val opponent = board.getOpponent(move.stone)

        val neighbors = getNeighbors(move.x, move.y, board.size)

        for ((nx, ny) in neighbors) {
            if (board.get(nx, ny) == opponent) {
                val liberties = libertyCalculator.calculateLiberties(board, nx, ny)
                if (liberties == 0) {
                    capturedStones.addAll(
                        libertyCalculator.getStoneGroup(board, nx, ny)
                    )
                }
            }
        }

        return capturedStones
    }

    /**
     * 获取四个邻居坐标（上、下、左、右）
     */
    private fun getNeighbors(x: Int, y: Int, boardSize: Int): List<Pair<Int, Int>> {
        val neighbors = mutableListOf<Pair<Int, Int>>()

        if (y > 0) neighbors.add(x to (y - 1))           // 上
        if (y < boardSize - 1) neighbors.add(x to (y + 1))  // 下
        if (x > 0) neighbors.add((x - 1) to y)         // 左
        if (x < boardSize - 1) neighbors.add((x + 1) to y)  // 右

        return neighbors
    }
}
