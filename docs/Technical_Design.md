# 技术方案文档 (Technical Design Document)

## 1. 架构设计

### 1.1 整体架构：Clean Architecture + MVVM

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer (Compose)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   MenuScreen   │  │  GameScreen  │  │  LobbyScreen   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│         │                   │                   │           │
│         └───────────────────┼───────────────────┘           │
│                             ▼                               │
│                    ┌─────────────────┐                      │
│                    │  ViewModel层    │                      │
│                    │  (StateFlow)    │                      │
│                    └─────────────────┘                      │
└─────────────────────────────┬───────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│                   Domain Layer (UseCase)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  GameLogic   │  │  RuleEngine  │  │  NetworkGame │      │
│  │   UseCase    │  │   UseCase    │  │   UseCase    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────┬───────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│                  Data Layer (Repository)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ GameStateRepo │  │ NetworkRepo  │  │  Settings    │      │
│  │  (In-Memory) │  │  (NSD+TCP)   │  │  (DataStore) │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 模块划分

| 模块 | 职责 | 技术实现 |
|------|------|----------|
| `domain` | 纯业务逻辑，与平台无关 | Kotlin 纯类，无 Android 依赖 |
| `data` | 数据存取、网络通信 | Repository 模式 |
| `presentation` | UI 状态管理 | ViewModel + StateFlow |
| `ui` | 界面绘制 | Jetpack Compose + Canvas |

### 1.3 依赖方向

```
UI → ViewModel → UseCase → Repository
严格禁止反向依赖，Domain 层不感知 Android 框架
```

---

## 2. 算法设计

### 2.1 棋盘状态存储

```kotlin
/**
 * 棋盘状态使用一维数组存储，提高缓存命中率
 * 索引计算：index = y * 19 + x
 */
class BoardState(
    val size: Int = 19,
    private val cells: IntArray = IntArray(size * size) { EMPTY }
) {
    companion object {
        const val EMPTY = 0   // 空点
        const val BLACK = 1   // 黑棋
        const val WHITE = 2   // 白棋
    }
    
    fun get(x: Int, y: Int): Int = cells[y * size + x]
    fun set(x: Int, y: Int, stone: Int) { cells[y * size + x] = stone }
    
    /**
     * 创建深拷贝，用于历史状态记录（打劫判定）
     */
    fun copy(): BoardState = BoardState(size, cells.copyOf())
}
```

### 2.2 气计算与提子算法（BFS）

```kotlin
/**
 * 伪代码：计算指定位置棋子群的气数
 */
fun calculateLiberties(board: BoardState, startX: Int, startY: Int): Int {
    val stone = board.get(startX, startY)
    if (stone == EMPTY) return 0
    
    val visited = BooleanArray(board.size * board.size)
    val queue = ArrayDeque<Pair<Int, Int>>()
    val liberties = mutableSetOf<Pair<Int, Int>>()
    
    queue.add(startX to startY)
    visited[startY * board.size + startX] = true
    
    while (queue.isNotEmpty()) {
        val (x, y) = queue.removeFirst()
        
        // 检查四邻
        for ((nx, ny) in getNeighbors(x, y)) {
            val neighbor = board.get(nx, ny)
            
            if (neighbor == EMPTY) {
                // 邻居是空点，计为气
                liberties.add(nx to ny)
            } else if (neighbor == stone && !visited[ny * board.size + nx]) {
                // 邻居是同色且未访问，加入队列
                visited[ny * board.size + nx] = true
                queue.add(nx to ny)
            }
        }
    }
    
    return liberties.size
}

/**
 * 伪代码：执行提子
 */
fun captureStones(board: BoardState, x: Int, y: Int): List<Pair<Int, Int>> {
    val captured = mutableListOf<Pair<Int, Int>>()
    val opponent = if (board.get(x, y) == BLACK) WHITE else BLACK
    
    // 检查四邻的对手棋子群
    for ((nx, ny) in getNeighbors(x, y)) {
        if (board.get(nx, ny) == opponent) {
            val liberties = calculateLiberties(board, nx, ny)
            if (liberties == 0) {
                // 该棋子群无气，提取所有棋子
                captured.addAll(removeStoneGroup(board, nx, ny))
            }
        }
    }
    
    return captured
}
```

### 2.3 打劫判定（历史状态比对）

```kotlin
/**
 * 伪代码：打劫判定
 */
fun isKo(board: BoardState, move: Move, history: List<BoardState>): Boolean {
    // 模拟落子
    val simulatedBoard = board.copy()
    simulatedBoard.set(move.x, move.y, move.stone)
    
    // 执行提子
    captureStones(simulatedBoard, move.x, move.y)
    
    // 检查是否与历史上任一状态重复（简化版仅检查上一步）
    return history.isNotEmpty() && simulatedBoard == history.last()
}
```

---

## 3. 网络 P2P 设计

### 3.1 局域网发现机制（Android NSD）

```
┌──────────────┐                      ┌──────────────┐
│   Host 设备   │                      │  Client 设备  │
│  (创建房间)   │                      │  (加入房间)   │
└──────┬───────┘                      └──────┬───────┘
       │                                       │
       ▼                                       ▼
┌─────────────────┐                  ┌─────────────────┐
│ registerService │                  │  discoverServices │
│  name="围棋-XXXX" │                  │   type="_go._tcp  │
│  type="_go._tcp"  │                  │   "               │
└────────┬────────┘                  └────────┬────────┘
         │                                    │
         │◄────────── 服务发现 ──────────────►│
         │                                    │
         ▼                                    ▼
┌─────────────────┐                  ┌─────────────────┐
│  解析后获得 IP    │◄─────────────────│  resolveService │
│    和端口       │    返回 IP + Port   │                 │
└─────────────────┘                  └─────────────────┘
```

**服务命名规范**：`围棋-{设备名后4位}`（如 `围棋-A3B2`）  
**服务类型**：`_go._tcp`  
**元数据**：`version=1.0`, `boardSize=19`

### 3.2 TCP Socket 连接生命周期

```
┌─────────┐     SYN      ┌─────────┐
│  Client │ ────────────► │  Host   │
│         │ ◄──────────── │         │
│         │    SYN+ACK    │         │
│         │ ────────────► │         │
│         │      ACK      │         │
└────┬────┘               └────┬────┘
     │                         │
     ▼                         ▼
┌─────────────────────────────────────┐
│           连接建立阶段               │
│  1. 握手消息交换（颜色分配）          │
│  2. 同步初始棋盘状态                 │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│           游戏进行阶段               │
│  双向持续监听，按协议收发消息          │
│  心跳保活：30秒间隔                   │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│           连接终止阶段               │
│  正常结束：发送 GAME_OVER 消息       │
│  异常断开：捕获 IOException，UI 提示   │
└─────────────────────────────────────┘
```

### 3.3 数据分包/粘包处理

**问题**：TCP 是流式协议，可能出现粘包或拆包  
**解决方案**：

```kotlin
/**
 * 协议格式：Length-Prefixed Message
 * 
 * [4字节消息长度][N字节JSON消息体]
 * 
 * 示例：{"type":"MOVE",...} 长度为 50 字节
 * 发送：0x00000032 + {JSON}
 */

class MessageFraming {
    fun encode(message: GoMessage): ByteArray {
        val json = gson.toJson(message)
        val bytes = json.toByteArray(Charsets.UTF_8)
        val lengthPrefix = ByteBuffer.allocate(4).putInt(bytes.size).array()
        return lengthPrefix + bytes
    }
    
    fun decode(stream: InputStream): GoMessage {
        // 1. 读取4字节长度
        val lengthBytes = stream.readNBytes(4)
        val length = ByteBuffer.wrap(lengthBytes).int
        
        // 2. 读取指定长度的消息体
        val bodyBytes = stream.readNBytes(length)
        val json = String(bodyBytes, Charsets.UTF_8)
        
        return gson.fromJson(json, GoMessage::class.java)
    }
}
```

### 3.4 GoMessage JSON 协议定义

```kotlin
/**
 * 所有消息的基类，使用 sealed class 保证类型安全
 */
sealed class GoMessage(
    val type: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 落子消息：由当前回合方发送
     */
    data class Move(
        val x: Int,           // 0-18
        val y: Int,           // 0-18
        val stone: Int,       // 1=黑, 2=白
        val captureCount: Int // 本次提子数量
    ) : GoMessage("MOVE")
    
    /**
     * 准备消息：连接建立后确认双方就绪
     */
    data class Ready(
        val assignedColor: Int,  // 对方分配给我的颜色
        val boardSize: Int       // 19/13/9
    ) : GoMessage("READY")
    
    /**
     * 认输消息
     */
    class Resign : GoMessage("RESIGN")
    
    /**
     * 心跳消息：维持连接活跃
     */
    class Heartbeat : GoMessage("HEARTBEAT")
    
    /**
     * 聊天消息（V1 预留）
     */
    data class Chat(
        val content: String
    ) : GoMessage("CHAT")
    
    /**
     * 游戏结束消息
     */
    data class GameOver(
        val reason: String,  // "RESIGN" | "TIMEOUT" | "DISCONNECT"
        val winner: Int      // 1=黑胜, 2=白胜, 0=和棋
    ) : GoMessage("GAME_OVER")
}
```

---

## 4. 代码规范声明

### 4.1 对 Kotlin 初学者友好的设计原则

| 原则 | 具体措施 |
|------|----------|
| **避免过度抽象** | 不滥用高阶函数、inline、reified 等高级特性 |
| **显式优于隐式** | 参数类型显式声明，减少类型推断依赖 |
| **命名自解释** | 使用完整英文单词，如 `calculateLiberties` 而非 `calcLib` |
| **单一职责** | 每个函数 ≤ 50 行，只做一件事 |
| **防御式编程** | 参数校验前置，使用 `require()` 抛出清晰异常 |

### 4.2 教学级中文注释位置

| 文件/类 | 注释内容 |
|---------|----------|
| `BoardState.kt` | 一维数组存储的索引计算原理 |
| `LibertyCalculator.kt` | BFS 遍历的队列操作图解 |
| `KoDetector.kt` | 打劫规则的历史状态比对逻辑 |
| `NsdHelper.kt` | NSD 服务注册的 Android 机制说明 |
| `TcpGameServer.kt` | Socket 的输入输出流处理注意事项 |
| `MessageFraming.kt` | Length-Prefixed 协议的粘包解决原理 |
| `GameViewModel.kt` | StateFlow 与 Compose 的订阅机制 |

### 4.3 目录结构

```
com.example.weiqigame/
├── data/
│   ├── local/          # 本地数据（设置、历史记录）
│   ├── remote/         # 网络通信（NSD、TCP）
│   │   ├── NsdHelper.kt
│   │   ├── TcpGameServer.kt
│   │   └── MessageFraming.kt
│   └── repository/       # Repository 实现
├── domain/
│   ├── model/          # 数据类（BoardState, Move, Stone）
│   ├── logic/          # 核心业务逻辑
│   │   ├── LibertyCalculator.kt   # 气计算
│   │   ├── CaptureDetector.kt     # 提子判定
│   │   ├── KoDetector.kt          # 打劫判定
│   │   └── GameRuleEngine.kt      # 规则引擎整合
│   └── usecase/        # UseCase 层
├── presentation/
│   ├── GameViewModel.kt
│   └── LobbyViewModel.kt
└── ui/
    ├── screens/
    │   ├── MenuScreen.kt
    │   ├── GameScreen.kt
    │   └── LobbyScreen.kt
    └── components/
        ├── GoBoard.kt      # 棋盘 Canvas 绘制
        ├── Stone.kt        # 棋子绘制
        └── Controls.kt     # 控制按钮
```
