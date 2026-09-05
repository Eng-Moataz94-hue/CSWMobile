package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.csw.ui.screens.ConnectionScreen
import com.example.csw.ui.screens.WorkspaceScreen
import com.example.csw.viewmodel.CSWViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: CSWViewModel = viewModel()
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                    Crossfade(
                        targetState = uiState.isLoggedIn,
                        label = "ScreenTransition"
                    ) { isLoggedIn ->
                        if (isLoggedIn) {
                            WorkspaceScreen(
                                uiState = uiState,
                                onTabSelected = viewModel::selectTab,
                                onSendMessage = viewModel::sendChatMessage,
                                onUserTyping = viewModel::onUserTyping,
                                onSendFile = { uri, isImage ->
                                    viewModel.sendFile(this@MainActivity, uri, isImage)
                                },
                                onDisconnect = viewModel::disconnect,
                                onClearInfo = viewModel::clearInfoMessage
                            )
                        } else {
                            ConnectionScreen(
                                uiState = uiState,
                                onIpChange = viewModel::updateServerIp,
                                onPortChange = viewModel::updateServerPort,
                                onUsernameChange = viewModel::updateUsername,
                                onPasswordChange = viewModel::updatePassword,
                                onConnectClick = viewModel::connect,
                                onClearError = viewModel::clearError
                            )
                        }
                    }
                }
            }
        }
    }
}

