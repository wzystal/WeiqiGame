package com.example.weiqigame.domain.logic

import com.example.weiqigame.domain.model.BoardState
import com.example.weiqigame.domain.model.Move

/**
 * 打劫检测器
 *
 * 【围棋打劫规则】
 * 打劫（Ko）是围棋中一种特殊的禁着规则。
 * 当一方提子后，对方不能立即在提子处反提，必须间隔一手（在别处下一手）后才能提回。
 *
 * 【判定逻辑】
 * 本实现采用简化版打劫规则：
 * 如果当前落子后，棋盘状态与上一步的状态完全相同（同形），则判为打劫禁着点。
 *
 * 注意：完整的围棋规则中，打劫可能需要更复杂的历史状态比对（如"全同形规则"），
 * 但考虑到初学者友好和实现复杂度，本版本采用简化规则。
 */
class KoDetector(private val captureDetector: CaptureDetector) {

    /**
     * 检查落子是否构成打劫
     *
     * @param board 当前棋盘（未落子状态）
     * @param move 待检测的落子
     * @param history 历史棋盘状态列表，按时间顺序排列，最后一个元素是上一步的状态
     * @return 如果构成打劫返回 true，否则返回 false
     */
    fun isKo(
        board: BoardState,
        move: Move,
        history: List<BoardState>
    ): Boolean {
        // 打劫只可能在提子后发生
        // 先预览本次落子会提多少子
        val previewCaptures = captureDetector.previewCaptures(board, move)
        if (previewCaptures.isEmpty()) {
            // 本次落子不提子，不可能构成打劫
            return false
        }

        // 模拟本次落子后的棋盘状态
        val simulatedBoard = board.copy()
        simulatedBoard.set(move.x, move.y, move.stone)

        // 执行提子（模拟）
        captureDetector.detectAndCapture(simulatedBoard, move)

        // 简化版打劫判定：只检查上一步的状态
        // 如果当前棋盘 + 本次落子后的状态 == 上一步的状态，则构成打劫
        if (history.isNotEmpty()) {
            val lastState = history.last()
            if (simulatedBoard == lastState) {
                return true
            }
        }

        return false
    }

    /**
     * 检查落子是否会导致自杀（自杀步/禁着点）
     *
     * 【自杀规则】
     * 一般情况下，禁止落子后自己的棋子群立即无气（自杀）。
     * 但有一种例外：如果落子同时提掉了对方的棋子，使得己方有气，则允许。
     *
     * @param board 当前棋盘（未落子状态）
     * @param move 待检测的落子
     * @return 如果是自杀步返回 true，否则返回 false
     */
    fun isSuicide(board: BoardState, move: Move): Boolean {
        // 空点才能落子
        if (!board.isEmpty(move.x, move.y)) {
            return false
        }

        // 先预览本次落子会提多少对方子
        val captures = captureDetector.previewCaptures(board, move)

        // 如果提子数 > 0，说明落子后己方必然有气，不是自杀
        if (captures.isNotEmpty()) {
            return false
        }

        // 模拟落子
        val simulatedBoard = board.copy()
        simulatedBoard.set(move.x, move.y, move.stone)

        // 计算己方落子后的气数
        val liberties = LibertyCalculator().calculateLiberties(simulatedBoard, move.x, move.y)

        // 如果气数为 0，则是自杀步
        return liberties == 0
    }
}
