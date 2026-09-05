package com.example.csw.model

/**
 * Message types corresponding to ChatProtocol codes used between ChatServer and clients.
 * Represented as 32-bit integers (4 bytes in Little-Endian) in the packet header.
 */
enum class MessageType(val code: Int) {
    UNKNOWN(0),
    AUTH_REQUEST(1),
    AUTH_SUCCESS(2),
    AUTH_FAILED(3),
    CHAT_MESSAGE(4),
    SEND_FILE(5),
    FILE_DATA_CHUNK(6),
    FILE_ACK(7),
    COLLABORATION_SNAPSHOT(8),
    COMMAND(9),
    PRESENCE_UPDATE(10),
    TYPING_INDICATOR(11),
    DISCONNECT(12),
    HEARTBEAT(13);

    companion object {
        fun fromCode(code: Int): MessageType {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}
