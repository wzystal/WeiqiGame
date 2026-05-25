package com.example.weiqigame.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.weiqigame.presentation.GameViewModel
import com.example.weiqigame.presentation.LobbyViewModel

/**
 * 联机大厅界面
 *
 * 管理局域网对战房间：创建房间、搜索房间、加入房间
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    lobbyViewModel: LobbyViewModel,
    gameViewModel: GameViewModel,
    onNavigateToGame: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    // 收集状态
    val discoveredServices by lobbyViewModel.discoveredServices.collectAsState()
    val isDiscovering by lobbyViewModel.isDiscovering.collectAsState()
    val isHosting by lobbyViewModel.isHosting.collectAsState()
    val errorMessage by lobbyViewModel.errorMessage.collectAsState()

    // 显示错误
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("联机对战") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 创建房间按钮
            if (isHosting) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "房间已创建",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "等待对手连接...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { lobbyViewModel.cancelHosting() },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("取消")
                        }
                    }
                }

                // 主机等待连接成功后自动进入游戏
                LaunchedEffect(Unit) {
                    gameViewModel.createRoom {
                        onNavigateToGame()
                    }
                }
            } else {
                Button(
                    onClick = { lobbyViewModel.createRoom() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("创建房间（执黑先行）")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 搜索房间区域
            Text(
                text = "加入房间",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 搜索按钮
            Button(
                onClick = {
                    if (isDiscovering) {
                        lobbyViewModel.stopSearching()
                    } else {
                        lobbyViewModel.searchRooms()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isHosting
            ) {
                Text(if (isDiscovering) "停止搜索" else "搜索房间")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 发现的房间列表
            if (discoveredServices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDiscovering) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("正在搜索...", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text(
                            "未发现房间",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(discoveredServices) { service ->
                        RoomItem(
                            serviceName = service.serviceName,
                            onClick = {
                                // 解析服务并连接
                                lobbyViewModel.resolveService(
                                    serviceInfo = service,
                                    onResolved = { resolvedService ->
                                        resolvedService.host?.let { address ->
                                            gameViewModel.joinRoom(address) {
                                                onNavigateToGame()
                                            }
                                        }
                                    },
                                    onError = { error ->
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个房间项
 */
@Composable
private fun RoomItem(
    serviceName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 房间图标
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                modifier = Modifier.width(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = serviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "点击加入房间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
