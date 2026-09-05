package com.example.csw.protocol

import com.example.csw.model.MessageType
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Raw packet representation received from or sent across the TCP stream.
 */
data class RawPacket(
    val messageType: MessageType,
    val rawTypeCode: Int,
    val payloadLength: Int,
    val payloadBytes: ByteArray
) {
    fun payloadAsString(): String {
        return String(payloadBytes, StandardCharsets.UTF_8)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RawPacket
        return rawTypeCode == other.rawTypeCode && payloadLength == other.payloadLength
    }

    override fun hashCode(): Int {
        var result = rawTypeCode
        result = 31 * result + payloadLength
        return result
    }
}

/**
 * ChatProtocol implementation matching the .NET C# BinaryWriter/BinaryReader protocol.
 * - Header: 8 bytes (4 bytes messageType + 4 bytes payloadLength) in Little-Endian format.
 * - Payload: UTF-8 JSON or raw binary stream.
 * - Fragmentation-safe: strictly assembles full packets using readFully.
 */
object ChatProtocol {

    const val HEADER_SIZE = 8 // 4 bytes code + 4 bytes length
    private const val MAX_PAYLOAD_SIZE = 50 * 1024 * 1024 // 50 MB safety limit

    /**
     * Reads a complete packet from the TCP input stream.
     * Guaranteed to assemble fragmented TCP packets completely.
     */
    @Throws(Exception::class)
    fun readPacket(inputStream: InputStream): RawPacket {
        val headerBuffer = ByteArray(HEADER_SIZE)
        readFully(inputStream, headerBuffer, 0, HEADER_SIZE)

        val headerBb = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val typeCode = headerBb.int
        val payloadLength = headerBb.int

        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE) {
            throw IllegalArgumentException("Invalid payload length: $payloadLength bytes")
        }

        val payload = ByteArray(payloadLength)
        if (payloadLength > 0) {
            readFully(inputStream, payload, 0, payloadLength)
        }

        return RawPacket(
            messageType = MessageType.fromCode(typeCode),
            rawTypeCode = typeCode,
            payloadLength = payloadLength,
            payloadBytes = payload
        )
    }

    /**
     * Writes a packet to the TCP output stream using Little-Endian header.
     */
    @Throws(Exception::class)
    fun writePacket(outputStream: OutputStream, messageType: MessageType, payloadStr: String) {
        val payloadBytes = payloadStr.toByteArray(StandardCharsets.UTF_8)
        writePacket(outputStream, messageType.code, payloadBytes)
    }

    /**
     * Writes a raw byte packet to the TCP output stream.
     */
    @Throws(Exception::class)
    fun writePacket(outputStream: OutputStream, typeCode: Int, payloadBytes: ByteArray) {
        val headerBb = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        headerBb.putInt(typeCode)
        headerBb.putInt(payloadBytes.size)

        outputStream.write(headerBb.array())
        if (payloadBytes.isNotEmpty()) {
            outputStream.write(payloadBytes)
        }
        outputStream.flush()
    }

    /**
     * Reads exactly [length] bytes into [buffer] starting at [offset].
     * Loops until all bytes are collected, solving TCP fragmentation across Wi-Fi.
     */
    @Throws(Exception::class)
    fun readFully(inputStream: InputStream, buffer: ByteArray, offset: Int, length: Int) {
        var bytesReadTotal = 0
        while (bytesReadTotal < length) {
            val count = inputStream.read(buffer, offset + bytesReadTotal, length - bytesReadTotal)
            if (count < 0) {
                throw EOFException("End of stream reached after reading $bytesReadTotal of $length bytes")
            }
            bytesReadTotal += count
        }
    }
}
