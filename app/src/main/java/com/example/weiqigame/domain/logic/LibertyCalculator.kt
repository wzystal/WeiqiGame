package com.example.weiqigame.domain.logic

import com.example.weiqigame.domain.model.BoardState

/**
 * 气计算器
 *
 * 使用 BFS（广度优先搜索）算法计算棋子群的气数
 *
 * 【围棋规则回顾】
 * "气"是指与棋子直线相连的空交叉点。同色棋子若相连形成一个整体（称为"块"或"群"），
 * 则共享所有的气。当一块棋的所有气都被对方占据时，该块棋被提走。
 *
 * 【BFS 算法思路】
 * 想象你在一个迷宫中，从起点出发，每次可以向上/下/左/右走一步。
 * 你标记所有走过的同色格子，同时记录下相邻的空格子——这些空格子就是"气"。
 */
class LibertyCalculator {

    /**
     * 计算指定位置棋子群的气数
     *
     * @param board 当前棋盘状态
     * @param x 起始位置横坐标
     * @param y 起始位置纵坐标
     * @return 该棋子群的气数（>= 0）
     * @throws IllegalArgumentException 如果起始位置为空或超出范围
     */
    fun calculateLiberties(board: BoardState, x: Int, y: Int): Int {
        // 验证起始位置有效
        require(board.isValidPosition(x, y)) { "坐标超出棋盘范围" }
        val stone = board.get(x, y)
        require(stone != BoardState.EMPTY) { "不能计算空点的气" }

        // visited 数组记录已访问的位置，防止重复计算
        // 使用一维数组索引：index = y * size + x
        val visited = BooleanArray(board.size * board.size)

        // BFS 队列，存储待检查的坐标
        val queue = ArrayDeque<Pair<Int, Int>>()

        // liberties 集合存储找到的气（空点），使用 Set 去重
        val liberties = mutableSetOf<Pair<Int, Int>>()

        // 从起始位置开始搜索
        queue.add(x to y)
        visited[y * board.size + x] = true

        // BFS 主循环
        while (queue.isNotEmpty()) {
            // 取出队列中的下一个位置
            val (currentX, currentY) = queue.removeFirst()

            // 检查四个方向的邻居（上、下、左、右）
            for ((neighborX, neighborY) in getNeighbors(currentX, currentY, board.size)) {
                val neighborStone = board.get(neighborX, neighborY)

                when (neighborStone) {
                    BoardState.EMPTY -> {
                        // 邻居是空点，计为一个"气"
                        // 使用 Pair 存储坐标，Set 会自动去重
                        liberties.add(neighborX to neighborY)
                    }

                    stone -> {
                        // 邻居是同色棋子，属于同一个块
                        // 如果未访问过，加入队列继续搜索
                        val index = neighborY * board.size + neighborX
                        if (!visited[index]) {
                            visited[index] = true
                            queue.add(neighborX to neighborY)
                        }
                    }

                    else -> {
                        // 邻居是对方棋子，既不是气也不加入搜索
                        // 什么都不做
                    }
                }
            }
        }

        return liberties.size
    }

    /**
     * 获取一个棋子群的所有棋子位置
     *
     * 使用与 calculateLiberties 相同的 BFS 逻辑，但返回棋子而非气
     */
    fun getStoneGroup(board: BoardState, x: Int, y: Int): List<Pair<Int, Int>> {
        require(board.isValidPosition(x, y)) { "坐标超出棋盘范围" }
        val stone = board.get(x, y)
        require(stone != BoardState.EMPTY) { "空点没有棋子群" }

        val visited = BooleanArray(board.size * board.size)
        val queue = ArrayDeque<Pair<Int, Int>>()
        val group = mutableListOf<Pair<Int, Int>>()

        queue.add(x to y)
        visited[y * board.size + x] = true
        group.add(x to y)

        while (queue.isNotEmpty()) {
            val (currentX, currentY) = queue.removeFirst()

            for ((neighborX, neighborY) in getNeighbors(currentX, currentY, board.size)) {
                val index = neighborY * board.size + neighborX
                if (!visited[index] && board.get(neighborX, neighborY) == stone) {
                    visited[index] = true
                    queue.add(neighborX to neighborY)
                    group.add(neighborX to neighborY)
                }
            }
        }

        return group
    }

    /**
     * 获取指定位置的四个邻居坐标
     *
     * @return 有效的邻居坐标列表（已过滤越界）
     */
    private fun getNeighbors(x: Int, y: Int, boardSize: Int): List<Pair<Int, Int>> {
        val neighbors = mutableListOf<Pair<Int, Int>>()

        // 上 (x, y-1)
        if (y > 0) neighbors.add(x to (y - 1))
        // 下 (x, y+1)
        if (y < boardSize - 1) neighbors.add(x to (y + 1))
        // 左 (x-1, y)
        if (x > 0) neighbors.add((x - 1) to y)
        // 右 (x+1, y)
        if (x < boardSize - 1) neighbors.add((x + 1) to y)

        return neighbors
    }

    /**
     * 检查指定位置的棋子是否处于"被提"状态（气数为0）
     */
    fun isCaptured(board: BoardState, x: Int, y: Int): Boolean {
        return calculateLiberties(board, x, y) == 0
    }
}
