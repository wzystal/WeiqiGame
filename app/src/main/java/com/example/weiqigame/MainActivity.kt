package com.example.weiqigame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weiqigame.presentation.GameViewModel
import com.example.weiqigame.presentation.LobbyViewModel
import com.example.weiqigame.ui.screens.GameScreen
import com.example.weiqigame.ui.screens.LobbyScreen
import com.example.weiqigame.ui.screens.MenuScreen
import com.example.weiqigame.ui.theme.WeiqiGameTheme

/**
 * 应用主界面
 *
 * 使用简单的状态管理实现界面导航：
 * - MENU: 主菜单
 * - LOBBY: 联机大厅
 * - GAME: 游戏界面
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeiqiGameTheme {
                WeiqiApp(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * 导航状态
 */
private enum class Screen {
    MENU,   // 主菜单
    LOBBY,  // 联机大厅
    GAME    // 游戏界面
}

@Composable
fun WeiqiApp(modifier: Modifier = Modifier) {
    // 当前界面状态
    var currentScreen by remember { mutableStateOf(Screen.MENU) }

    // ViewModel
    val gameViewModel: GameViewModel = viewModel()
    val lobbyViewModel: LobbyViewModel = viewModel()

    // 根据当前状态显示对应界面
    when (currentScreen) {
        Screen.MENU -> {
            MenuScreen(
                gameViewModel = gameViewModel,
                onNavigateToGame = { currentScreen = Screen.GAME },
                onNavigateToLobby = { currentScreen = Screen.LOBBY },
                modifier = modifier
            )
        }

        Screen.LOBBY -> {
            LobbyScreen(
                lobbyViewModel = lobbyViewModel,
                gameViewModel = gameViewModel,
                onNavigateToGame = { currentScreen = Screen.GAME },
                onNavigateBack = { currentScreen = Screen.MENU }
            )
        }

        Screen.GAME -> {
            GameScreen(
                viewModel = gameViewModel,
                onBackToMenu = {
                    gameViewModel.disconnect()
                    currentScreen = Screen.MENU
                },
                modifier = modifier
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WeiqiAppPreview() {
    WeiqiGameTheme {
        WeiqiApp()
    }
}