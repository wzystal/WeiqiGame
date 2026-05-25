package com.example.weiqigame.domain.logic

import com.example.weiqigame.domain.model.BoardState
import com.example.weiqigame.domain.model.Move

/**
 * 落子验证结果
 */
sealed class MoveValidationResult {
    data class Valid(
        val move: Move,
        val capturedStones: List<Pair<Int, Int>>
    ) : MoveValidationResult()

    data class Invalid(
        val reason: InvalidReason,
        val message: String
    ) : MoveValidationResult()
}

/**
 * 落子无效的原因
 */
enum class InvalidReason {
    POSITION_OCCUPIED,   // 位置已有棋子
    OUT_OF_BOUNDS,       // 超出棋盘范围
    SUICIDE,             // 自杀步（禁着点）
    KO                   // 打劫（禁着点）
}

/**
 * 游戏规则引擎
 *
 * 整合所有围棋规则检测，提供统一的落子验证接口
 */
class GameRuleEngine {

    private val libertyCalculator = LibertyCalculator()
    private val captureDetector = CaptureDetector(libertyCalculator)
    private val koDetector = KoDetector(captureDetector)

    /**
     * 验证并执行落子
     *
     * 这是主要的落子接口，执行以下步骤：
     * 1. 基础验证（位置是否有效、是否已有棋子）
     * 2. 自杀判定
     * 3. 打劫判定
     * 4. 执行落子和提子
     * 5. 更新历史记录
     *
     * @param board 当前棋盘状态（会被修改）
     * @param move 待执行的落子
     * @param history 历史棋盘状态列表（用于打劫判定）
     * @return 验证结果，成功时返回 Valid 并执行落子，失败时返回 Invalid 且棋盘不变
     */
    fun validateAndExecute(
        board: BoardState,
        move: Move,
        history: List<BoardState>
    ): MoveValidationResult {
        // 1. 基础验证：坐标范围
        if (!board.isValidPosition(move.x, move.y)) {
            return MoveValidationResult.Invalid(
                InvalidReason.OUT_OF_BOUNDS,
                "坐标超出棋盘范围"
            )
        }

        // 2. 基础验证：位置是否已有棋子
        if (!board.isEmpty(move.x, move.y)) {
            return MoveValidationResult.Invalid(
                InvalidReason.POSITION_OCCUPIED,
                "该位置已有棋子"
            )
        }

        // 3. 自杀判定
        if (koDetector.isSuicide(board, move)) {
            return MoveValidationResult.Invalid(
                InvalidReason.SUICIDE,
                "禁着点：自杀步"
            )
        }

        // 4. 打劫判定
        if (koDetector.isKo(board, move, history)) {
            return MoveValidationResult.Invalid(
                InvalidReason.KO,
                "禁着点：打劫"
            )
        }

        // 5. 执行落子
        board.set(move.x, move.y, move.stone)

        // 6. 执行提子
        val capturedStones = captureDetector.detectAndCapture(board, move)

        return MoveValidationResult.Valid(move, capturedStones)
    }

    /**
     * 仅验证落子是否合法，不实际执行
     *
     * 用于 UI 层的落子预览、提示等功能
     */
    fun validateOnly(
        board: BoardState,
        move: Move,
        history: List<BoardState>
        
    ): MoveValidationResult {
        // 基础验证
        if (!board.isValidPosition(move.x, move.y)) {
            return MoveValidationResult.Invalid(
                InvalidReason.OUT_OF_BOUNDS,
                "坐标超出棋盘范围"
            )
        }

        if (!board.isEmpty(move.x, move.y)) {
            return MoveValidationResult.Invalid(
                InvalidReason.POSITION_OCCUPIED,
                "该位置已有棋子"
            )
        }

        // 自杀判定
        if (koDetector.isSuicide(board, move)) {
            return MoveValidationResult.Invalid(
                InvalidReason.SUICIDE,
                "禁着点：自杀步"
            )
        }

        // 打劫判定
        if (koDetector.isKo(board, move, history)) {
            return MoveValidationResult.Invalid(
                InvalidReason.KO,
                "禁着点：打劫"
            )
        }

        // 预览提子数
        val capturedStones = captureDetector.previewCaptures(board, move)
        return MoveValidationResult.Valid(move, capturedStones)
    }

    /**
     * 预览落子后会提哪些子
     */
    fun previewCaptures(board: BoardState, move: Move): List<Pair<Int, Int>> {
        return captureDetector.previewCaptures(board, move)
    }

    /**
     * 检查指定位置是否是禁着点
     */
    fun isForbiddenPoint(
        board: BoardState,
        x: Int,
        y: Int,
        stone: Int,
        history: List<BoardState>
    ): InvalidReason? {
        if (!board.isValidPosition(x, y) || !board.isEmpty(x, y)) {
            return InvalidReason.POSITION_OCCUPIED
        }

        val move = Move(x, y, stone)

        return when {
            koDetector.isSuicide(board, move) -> InvalidReason.SUICIDE
            koDetector.isKo(board, move, history) -> InvalidReason.KO
            else -> null
        }
    }
}
