package com.example.csw.network

import android.util.Log
import com.example.csw.model.MessageType
import com.example.csw.protocol.ChatProtocol
import com.example.csw.protocol.RawPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

sealed interface SocketConnectionState {
    data object Disconnected : SocketConnectionState
    data class Connecting(val ip: String, val port: Int) : SocketConnectionState
    data class Connected(val ip: String, val port: Int) : SocketConnectionState
    data class Error(val message: String, val canRetry: Boolean = true) : SocketConnectionState
}

/**
 * Native TCP Socket Engine for CSWMobile.
 * Runs strictly on Dispatchers.IO to prevent NetworkOnMainThreadException.
 */
class TcpSocketManager(
    private val coroutineScope: CoroutineScope
) {
    private val tag = "TcpSocketManager"

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var readerJob: Job? = null
    private val sendMutex = Mutex()

    private val _connectionState = MutableStateFlow<SocketConnectionState>(SocketConnectionState.Disconnected)
    val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<RawPacket>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<RawPacket> = _incomingPackets.asSharedFlow()

    /**
     * Connects to ChatServer using raw TCP socket on background dispatcher.
     */
    suspend fun connect(ip: String, port: Int, timeoutMs: Int = 6000): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Ensure prior socket is cleaned up
                disconnect()

                _connectionState.value = SocketConnectionState.Connecting(ip, port)
                Log.d(tag, "Attempting TCP connection to $ip:$port...")

                val newSocket = Socket()
                newSocket.connect(InetSocketAddress(ip, port), timeoutMs)
                newSocket.tcpNoDelay = true
                newSocket.keepAlive = true

                socket = newSocket
                inputStream = BufferedInputStream(newSocket.getInputStream())
                outputStream = BufferedOutputStream(newSocket.getOutputStream())

                _connectionState.value = SocketConnectionState.Connected(ip, port)
                Log.d(tag, "Successfully connected to $ip:$port")

                startReaderLoop()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(tag, "Connection failed to $ip:$port: ${e.message}", e)
                val errMsg = when {
                    e.message?.contains("ECONNREFUSED", ignoreCase = true) == true ->
                        "Connection refused at $ip:$port. Check if ChatServer is running and port is open."
                    e.message?.contains("ETIMEDOUT", ignoreCase = true) == true || e.message?.contains("timed out", ignoreCase = true) == true ->
                        "Connection timed out. Ensure device and PC are on the same Wi-Fi network and Windows Firewall allows port $port."
                    e.message?.contains("EHOSTUNREACH", ignoreCase = true) == true ->
                        "Host unreachable. Verify the Windows PC's Wi-Fi IP address."
                    else -> e.message ?: "Failed to connect to $ip:$port"
                }
                _connectionState.value = SocketConnectionState.Error(errMsg)
                disconnect()
                Result.failure(e)
            }
        }
    }

    /**
     * Starts continuous packet reader in the background.
     */
    private fun startReaderLoop() {
        readerJob?.cancel()
        readerJob = coroutineScope.launch(Dispatchers.IO) {
            val inStream = inputStream ?: return@launch
            try {
                while (isActive && socket?.isConnected == true && !socket!!.isClosed) {
                    val packet = ChatProtocol.readPacket(inStream)
                    Log.d(tag, "Received packet: Type=${packet.messageType} (${packet.rawTypeCode}), Length=${packet.payloadLength}")
                    _incomingPackets.emit(packet)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(tag, "Socket reader closed: ${e.message}")
                    _connectionState.value = SocketConnectionState.Error("Disconnected from server: ${e.localizedMessage}")
                }
            } finally {
                disconnect()
            }
        }
    }

    /**
     * Sends a packet with string payload safely using mutex synchronization.
     */
    suspend fun sendPacket(messageType: MessageType, payloadStr: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            sendMutex.withLock {
                try {
                    val out = outputStream ?: throw IllegalStateException("Socket is not connected")
                    ChatProtocol.writePacket(out, messageType, payloadStr)
                    Log.d(tag, "Sent packet: Type=$messageType, length=${payloadStr.length}")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to send packet: ${e.message}", e)
                    _connectionState.value = SocketConnectionState.Error("Send error: ${e.localizedMessage}")
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Sends raw bytes packet.
     */
    suspend fun sendRawPacket(typeCode: Int, payloadBytes: ByteArray): Result<Unit> {
        return withContext(Dispatchers.IO) {
            sendMutex.withLock {
                try {
                    val out = outputStream ?: throw IllegalStateException("Socket is not connected")
                    ChatProtocol.writePacket(out, typeCode, payloadBytes)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to send raw packet: ${e.message}", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Gracefully disconnects and releases socket resources.
     */
    fun disconnect() {
        try {
            readerJob?.cancel()
            readerJob = null
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.w(tag, "Error during socket close: ${e.message}")
        } finally {
            inputStream = null
            outputStream = null
            socket = null
            if (_connectionState.value is SocketConnectionState.Connected ||
                _connectionState.value is SocketConnectionState.Connecting) {
                _connectionState.value = SocketConnectionState.Disconnected
            }
        }
    }
}
