package com.example.csw.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Authentication payload sent from client to server during initial handshake.
 */
data class AuthPayload(
    val username: String,
    val password: String = "",
    val clientType: String = "Android",
    val deviceName: String = "Android Device"
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("Username", username)
            put("username", username)
            put("Password", password)
            put("password", password)
            put("ClientType", clientType)
            put("clientType", clientType)
            put("DeviceName", deviceName)
            put("deviceName", deviceName)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): AuthPayload {
            val obj = JSONObject(jsonStr)
            return AuthPayload(
                username = obj.optString("Username", obj.optString("username", "")),
                password = obj.optString("Password", obj.optString("password", "")),
                clientType = obj.optString("ClientType", obj.optString("clientType", "Android")),
                deviceName = obj.optString("DeviceName", obj.optString("deviceName", "Android Device"))
            )
        }
    }
}

/**
 * Chat message model sent and received across clients.
 */
data class SendMessagePayload(
    val messageId: String = UUID.randomUUID().toString(),
    val senderUsername: String,
    val content: String,
    val timestamp: String = "",
    val messageType: String = "text"
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("MessageId", messageId)
            put("messageId", messageId)
            put("SenderUsername", senderUsername)
            put("senderUsername", senderUsername)
            put("Content", content)
            put("content", content)
            put("Timestamp", timestamp)
            put("timestamp", timestamp)
            put("MessageType", messageType)
            put("messageType", messageType)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): SendMessagePayload {
            val obj = JSONObject(jsonStr)
            return SendMessagePayload(
                messageId = obj.optString("MessageId", obj.optString("messageId", UUID.randomUUID().toString())),
                senderUsername = obj.optString("SenderUsername", obj.optString("senderUsername", "Unknown")),
                content = obj.optString("Content", obj.optString("content", "")),
                timestamp = obj.optString("Timestamp", obj.optString("timestamp", "")),
                messageType = obj.optString("MessageType", obj.optString("messageType", "text"))
            )
        }
    }
}

/**
 * Chat message item presented in the UI.
 */
data class ChatMessageItem(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val content: String,
    val timestamp: String,
    val isFromMe: Boolean = false,
    val isFile: Boolean = false,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val isImage: Boolean = false,
    val localUri: String? = null
)

/**
 * Connected user / peer in the workspace.
 */
data class ConnectedUser(
    val username: String,
    val isOnline: Boolean = true,
    val status: String = "Active",
    val clientType: String = "Windows",
    val ipAddress: String = ""
) {
    companion object {
        fun fromJson(obj: JSONObject): ConnectedUser {
            return ConnectedUser(
                username = obj.optString("Username", obj.optString("username", "Unknown")),
                isOnline = obj.optBoolean("IsOnline", obj.optBoolean("isOnline", true)),
                status = obj.optString("Status", obj.optString("status", "Active")),
                clientType = obj.optString("ClientType", obj.optString("clientType", "Unknown")),
                ipAddress = obj.optString("IpAddress", obj.optString("ipAddress", ""))
            )
        }
    }
}

/**
 * Shared file or image item available on the server.
 */
data class FileItem(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileSize: Long,
    val sender: String,
    val timestamp: String = "",
    val isImage: Boolean = false,
    val localPath: String? = null
) {
    companion object {
        fun fromJson(obj: JSONObject): FileItem {
            val name = obj.optString("FileName", obj.optString("fileName", "file"))
            val lower = name.lowercase()
            val isImg = lower.endsWith(".png") || lower.endsWith(".jpg") ||
                    lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif")
            return FileItem(
                id = obj.optString("Id", obj.optString("id", UUID.randomUUID().toString())),
                fileName = name,
                fileSize = obj.optLong("FileSize", obj.optLong("fileSize", 0L)),
                sender = obj.optString("Sender", obj.optString("sender", "Unknown")),
                timestamp = obj.optString("Timestamp", obj.optString("timestamp", "")),
                isImage = obj.optBoolean("IsImage", obj.optBoolean("isImage", isImg)),
                localPath = obj.optString("LocalPath", null)
            )
        }
    }
}

/**
 * Initial workspace snapshot sent by ChatServer upon successful login.
 */
data class CollaborationSnapshot(
    val connectedUsers: List<ConnectedUser> = emptyList(),
    val recentMessages: List<ChatMessageItem> = emptyList(),
    val sharedFiles: List<FileItem> = emptyList(),
    val serverTime: String = ""
) {
    companion object {
        fun fromJson(jsonStr: String, currentUsername: String = ""): CollaborationSnapshot {
            val obj = JSONObject(jsonStr)
            val usersList = mutableListOf<ConnectedUser>()
            val usersArr = obj.optJSONArray("ConnectedUsers") ?: obj.optJSONArray("connectedUsers")
            if (usersArr != null) {
                for (i in 0 until usersArr.length()) {
                    val userObj = usersArr.optJSONObject(i)
                    if (userObj != null) {
                        usersList.add(ConnectedUser.fromJson(userObj))
                    }
                }
            }

            val messagesList = mutableListOf<ChatMessageItem>()
            val msgsArr = obj.optJSONArray("RecentMessages") ?: obj.optJSONArray("recentMessages")
            if (msgsArr != null) {
                for (i in 0 until msgsArr.length()) {
                    val msgObj = msgsArr.optJSONObject(i)
                    if (msgObj != null) {
                        val sender = msgObj.optString("SenderUsername", msgObj.optString("senderUsername", "Unknown"))
                        messagesList.add(
                            ChatMessageItem(
                                id = msgObj.optString("MessageId", msgObj.optString("messageId", UUID.randomUUID().toString())),
                                sender = sender,
                                content = msgObj.optString("Content", msgObj.optString("content", "")),
                                timestamp = msgObj.optString("Timestamp", msgObj.optString("timestamp", "")),
                                isFromMe = sender.equals(currentUsername, ignoreCase = true)
                            )
                        )
                    }
                }
            }

            val filesList = mutableListOf<FileItem>()
            val filesArr = obj.optJSONArray("SharedFiles") ?: obj.optJSONArray("sharedFiles")
            if (filesArr != null) {
                for (i in 0 until filesArr.length()) {
                    val fileObj = filesArr.optJSONObject(i)
                    if (fileObj != null) {
                        filesList.add(FileItem.fromJson(fileObj))
                    }
                }
            }

            val sTime = obj.optString("ServerTime", obj.optString("serverTime", ""))
            return CollaborationSnapshot(
                connectedUsers = usersList,
                recentMessages = messagesList,
                sharedFiles = filesList,
                serverTime = sTime
            )
        }
    }
}

/**
 * Command payload for Presence, Typing notifications, etc.
 */
data class CollaborationCommand(
    val commandType: String,
    val username: String,
    val extraData: String = ""
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("CommandType", commandType)
            put("commandType", commandType)
            put("Username", username)
            put("username", username)
            put("ExtraData", extraData)
            put("extraData", extraData)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): CollaborationCommand {
            val obj = JSONObject(jsonStr)
            return CollaborationCommand(
                commandType = obj.optString("CommandType", obj.optString("commandType", "")),
                username = obj.optString("Username", obj.optString("username", "")),
                extraData = obj.optString("ExtraData", obj.optString("extraData", ""))
            )
        }
    }
}

/**
 * File transfer metadata header.
 */
data class FileMetadataPayload(
    val fileId: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileSize: Long,
    val sender: String,
    val isImage: Boolean = false
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("FileId", fileId)
            put("fileId", fileId)
            put("FileName", fileName)
            put("fileName", fileName)
            put("FileSize", fileSize)
            put("fileSize", fileSize)
            put("Sender", sender)
            put("sender", sender)
            put("IsImage", isImage)
            put("isImage", isImage)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): FileMetadataPayload {
            val obj = JSONObject(jsonStr)
            val name = obj.optString("FileName", obj.optString("fileName", "unknown"))
            val lower = name.lowercase()
            val isImg = lower.endsWith(".png") || lower.endsWith(".jpg") ||
                    lower.endsWith(".jpeg") || lower.endsWith(".webp")
            return FileMetadataPayload(
                fileId = obj.optString("FileId", obj.optString("fileId", UUID.randomUUID().toString())),
                fileName = name,
                fileSize = obj.optLong("FileSize", obj.optLong("fileSize", 0L)),
                sender = obj.optString("Sender", obj.optString("sender", "Unknown")),
                isImage = obj.optBoolean("IsImage", obj.optBoolean("isImage", isImg))
            )
        }
    }
}
