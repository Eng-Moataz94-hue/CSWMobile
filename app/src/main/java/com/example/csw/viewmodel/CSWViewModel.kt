package com.example.csw.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.csw.model.AuthPayload
import com.example.csw.model.ChatMessageItem
import com.example.csw.model.CollaborationCommand
import com.example.csw.model.CollaborationSnapshot
import com.example.csw.model.ConnectedUser
import com.example.csw.model.FileItem
import com.example.csw.model.FileMetadataPayload
import com.example.csw.model.MessageType
import com.example.csw.model.SendMessagePayload
import com.example.csw.network.SocketConnectionState
import com.example.csw.network.TcpSocketManager
import com.example.csw.protocol.RawPacket
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class WorkspaceUiState(
    val serverIp: String = "192.168.1.100",
    val serverPort: String = "5000",
    val username: String = "Student_Android",
    val password: String = "",
    val isLoggedIn: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val activeTab: Int = 0, // 0: Chat, 1: Files, 2: Members, 3: Server Info
    val messages: List<ChatMessageItem> = emptyList(),
    val users: List<ConnectedUser> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val typingStatus: String? = null,
    val infoMessage: String? = null
)

class CSWViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "CSWViewModel"
    val socketManager = TcpSocketManager(viewModelScope)

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var typingTimerJob: Job? = null

    init {
        // Collect connection state
        viewModelScope.launch {
            socketManager.connectionState.collect { connState ->
                when (connState) {
                    is SocketConnectionState.Disconnected -> {
                        _uiState.update {
                            it.copy(
                                isConnecting = false,
                                isLoggedIn = false
                            )
                        }
                    }
                    is SocketConnectionState.Connecting -> {
                        _uiState.update {
                            it.copy(
                                isConnecting = true,
                                connectionError = null
                            )
                        }
                    }
                    is SocketConnectionState.Connected -> {
                        _uiState.update { it.copy(isConnecting = false) }
                        // Send Auth payload immediately upon TCP connection
                        performAuthHandshake()
                    }
                    is SocketConnectionState.Error -> {
                        _uiState.update {
                            it.copy(
                                isConnecting = false,
                                isLoggedIn = false,
                                connectionError = connState.message
                            )
                        }
                    }
                }
            }
        }

        // Collect incoming packets from ChatServer
        viewModelScope.launch {
            socketManager.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    fun updateServerIp(ip: String) {
        _uiState.update { it.copy(serverIp = ip) }
    }

    fun updateServerPort(port: String) {
        _uiState.update { it.copy(serverPort = port) }
    }

    fun updateUsername(name: String) {
        _uiState.update { it.copy(username = name) }
    }

    fun updatePassword(pwd: String) {
        _uiState.update { it.copy(password = pwd) }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(activeTab = tabIndex) }
    }

    fun clearError() {
        _uiState.update { it.copy(connectionError = null) }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    /**
     * Initiates connection to ChatServer.
     */
    fun connect() {
        val ip = _uiState.value.serverIp.trim()
        val portStr = _uiState.value.serverPort.trim()
        val user = _uiState.value.username.trim()

        if (ip.isEmpty()) {
            _uiState.update { it.copy(connectionError = "Please enter the server IP address.") }
            return
        }

        if (ip == "localhost" || ip == "127.0.0.1") {
            _uiState.update {
                it.copy(
                    connectionError = "Cannot use localhost / 127.0.0.1 on Android! Please enter the Windows PC's Wi-Fi IP address (e.g., 192.168.x.x)."
                )
            }
            return
        }

        val port = portStr.toIntOrNull() ?: 5000
        if (user.isEmpty()) {
            _uiState.update { it.copy(connectionError = "Please enter a username.") }
            return
        }

        viewModelScope.launch {
            socketManager.connect(ip, port)
        }
    }

    /**
     * Sends the AuthPayload across the TCP connection.
     */
    private fun performAuthHandshake() {
        viewModelScope.launch {
            val authPayload = AuthPayload(
                username = _uiState.value.username,
                password = _uiState.value.password,
                clientType = "Android",
                deviceName = android.os.Build.MODEL ?: "Android Phone"
            )
            Log.d(tag, "Sending AuthPayload: ${authPayload.username}")
            val res = socketManager.sendPacket(MessageType.AUTH_REQUEST, authPayload.toJson())
            if (res.isFailure) {
                _uiState.update { it.copy(connectionError = "Failed to send auth handshake: ${res.exceptionOrNull()?.message}") }
            }
        }
    }

    /**
     * Parses incoming packets according to ChatProtocol.
     */
    private fun handleIncomingPacket(packet: RawPacket) {
        val payloadStr = packet.payloadAsString()
        Log.d(tag, "Handling packet: ${packet.messageType} payload length=${packet.payloadLength}")

        when (packet.messageType) {
            MessageType.AUTH_SUCCESS -> {
                Log.d(tag, "Auth success received from server")
                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        connectionError = null,
                        infoMessage = "Connected to CSW Workspace as ${it.username}"
                    )
                }
            }

            MessageType.AUTH_FAILED -> {
                Log.w(tag, "Auth failed from server: $payloadStr")
                _uiState.update {
                    it.copy(
                        isLoggedIn = false,
                        connectionError = "Authentication failed: $payloadStr"
                    )
                }
                socketManager.disconnect()
            }

            MessageType.COLLABORATION_SNAPSHOT -> {
                try {
                    val snapshot = CollaborationSnapshot.fromJson(payloadStr, _uiState.value.username)
                    _uiState.update { current ->
                        current.copy(
                            isLoggedIn = true,
                            messages = snapshot.recentMessages,
                            users = snapshot.connectedUsers,
                            files = snapshot.sharedFiles
                        )
                    }
                    Log.d(tag, "Loaded snapshot: ${snapshot.recentMessages.size} msgs, ${snapshot.connectedUsers.size} users, ${snapshot.sharedFiles.size} files")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse snapshot JSON: ${e.message}", e)
                }
            }

            MessageType.CHAT_MESSAGE -> {
                try {
                    val msgPayload = SendMessagePayload.fromJson(payloadStr)
                    val isMine = msgPayload.senderUsername.equals(_uiState.value.username, ignoreCase = true)
                    val time = if (msgPayload.timestamp.isNotEmpty()) msgPayload.timestamp else getCurrentTimeString()
                    val chatItem = ChatMessageItem(
                        id = msgPayload.messageId,
                        sender = msgPayload.senderUsername,
                        content = msgPayload.content,
                        timestamp = time,
                        isFromMe = isMine
                    )
                    _uiState.update { it.copy(messages = it.messages + chatItem) }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse chat message: ${e.message}", e)
                }
            }

            MessageType.SEND_FILE, MessageType.FILE_ACK -> {
                try {
                    val fileMeta = FileMetadataPayload.fromJson(payloadStr)
                    val isMine = fileMeta.sender.equals(_uiState.value.username, ignoreCase = true)
                    val fileItem = FileItem(
                        id = fileMeta.fileId,
                        fileName = fileMeta.fileName,
                        fileSize = fileMeta.fileSize,
                        sender = fileMeta.sender,
                        timestamp = getCurrentTimeString(),
                        isImage = fileMeta.isImage
                    )
                    val chatMsg = ChatMessageItem(
                        id = fileMeta.fileId,
                        sender = fileMeta.sender,
                        content = if (fileMeta.isImage) "Shared an image: ${fileMeta.fileName}" else "Shared a file: ${fileMeta.fileName}",
                        timestamp = getCurrentTimeString(),
                        isFromMe = isMine,
                        isFile = true,
                        fileName = fileMeta.fileName,
                        fileSize = fileMeta.fileSize,
                        isImage = fileMeta.isImage
                    )
                    _uiState.update {
                        it.copy(
                            files = it.files + fileItem,
                            messages = it.messages + chatMsg
                        )
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse file metadata: ${e.message}", e)
                }
            }

            MessageType.PRESENCE_UPDATE -> {
                try {
                    val cmd = CollaborationCommand.fromJson(payloadStr)
                    val updatedUsers = _uiState.value.users.map { user ->
                        if (user.username.equals(cmd.username, ignoreCase = true)) {
                            user.copy(isOnline = cmd.commandType.contains("online", ignoreCase = true) || cmd.commandType.contains("join", ignoreCase = true))
                        } else user
                    }
                    _uiState.update { it.copy(users = updatedUsers) }
                } catch (e: Exception) {
                    Log.e(tag, "Presence update error: ${e.message}")
                }
            }

            MessageType.TYPING_INDICATOR -> {
                try {
                    val cmd = CollaborationCommand.fromJson(payloadStr)
                    if (!cmd.username.equals(_uiState.value.username, ignoreCase = true)) {
                        _uiState.update { it.copy(typingStatus = "${cmd.username} is typing...") }
                        typingTimerJob?.cancel()
                        typingTimerJob = viewModelScope.launch {
                            delay(3000)
                            _uiState.update { it.copy(typingStatus = null) }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Typing indicator parse error: ${e.message}")
                }
            }

            else -> {
                // If the server sent a generic JSON command or unrecognized packet
                Log.d(tag, "Unrecognized packet received: ${packet.rawTypeCode}, length=${packet.payloadLength}")
            }
        }
    }

    /**
     * Sends a text chat message to the server.
     */
    fun sendChatMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val sender = _uiState.value.username
        val time = getCurrentTimeString()
        val payload = SendMessagePayload(
            messageId = UUID.randomUUID().toString(),
            senderUsername = sender,
            content = trimmed,
            timestamp = time,
            messageType = "text"
        )

        // Optimistically add to local messages
        val localItem = ChatMessageItem(
            id = payload.messageId,
            sender = sender,
            content = trimmed,
            timestamp = time,
            isFromMe = true
        )
        _uiState.update { it.copy(messages = it.messages + localItem) }

        viewModelScope.launch {
            val res = socketManager.sendPacket(MessageType.CHAT_MESSAGE, payload.toJson())
            if (res.isFailure) {
                _uiState.update { it.copy(connectionError = "Failed to send message: ${res.exceptionOrNull()?.message}") }
            }
        }
    }

    /**
     * Broadcasts typing status to peers.
     */
    fun onUserTyping() {
        val sender = _uiState.value.username
        viewModelScope.launch {
            val cmd = CollaborationCommand(
                commandType = "Typing",
                username = sender
            )
            socketManager.sendPacket(MessageType.TYPING_INDICATOR, cmd.toJson())
        }
    }

    /**
     * Sends a file/image through TCP.
     */
    fun sendFile(context: Context, uri: Uri, isImage: Boolean) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                var fileName = "file_${System.currentTimeMillis()}"
                var fileSize = 0L

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                val sender = _uiState.value.username
                val metadata = FileMetadataPayload(
                    fileId = UUID.randomUUID().toString(),
                    fileName = fileName,
                    fileSize = fileSize,
                    sender = sender,
                    isImage = isImage
                )

                // 1. Send File Metadata Packet
                socketManager.sendPacket(MessageType.SEND_FILE, metadata.toJson())

                // 2. Send File Bytes
                contentResolver.openInputStream(uri)?.use { inStream ->
                    val bytes = inStream.readBytes()
                    socketManager.sendRawPacket(MessageType.FILE_DATA_CHUNK.code, bytes)
                }

                // Add to local UI
                val fileItem = FileItem(
                    id = metadata.fileId,
                    fileName = fileName,
                    fileSize = fileSize,
                    sender = sender,
                    timestamp = getCurrentTimeString(),
                    isImage = isImage
                )
                val chatMsg = ChatMessageItem(
                    id = metadata.fileId,
                    sender = sender,
                    content = if (isImage) "Shared an image: $fileName" else "Shared a file: $fileName",
                    timestamp = getCurrentTimeString(),
                    isFromMe = true,
                    isFile = true,
                    fileName = fileName,
                    fileSize = fileSize,
                    isImage = isImage
                )
                _uiState.update {
                    it.copy(
                        files = it.files + fileItem,
                        messages = it.messages + chatMsg,
                        infoMessage = "File $fileName uploaded successfully"
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to upload file: ${e.message}", e)
                _uiState.update { it.copy(connectionError = "File upload failed: ${e.message}") }
            }
        }
    }

    /**
     * Disconnects from the server.
     */
    fun disconnect() {
        socketManager.disconnect()
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                isConnecting = false,
                infoMessage = "Disconnected from server"
            )
        }
    }

    private fun getCurrentTimeString(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}
