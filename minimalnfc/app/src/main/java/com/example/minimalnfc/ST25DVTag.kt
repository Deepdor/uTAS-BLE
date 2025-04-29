package com.example.minimalnfc

import android.nfc.Tag
import android.nfc.tech.NfcV
import java.io.IOException

/**
 * Class with utility functions to interact with an ST25DV Tag.
 */
class ST25DVTag(private val tag: NfcV) {

    @Throws(IOException::class)
    fun read(address: Short): ByteArray {
        val readCommand = byteArrayOf(
            ST25DV_REQUEST_HEADER,
            READ_COMMAND,
            address.lsb(),
            address.msb()
        )
        return safeTransceive(readCommand)
    }

    @Throws(IOException::class)
    fun write(address: Short, data: ByteArray) {
        if (data.size != 4) {
            throw ST25DVException("The memory is written in blocks of 4 bytes, impossible to write ${data.size} bytes")
        }
        val writeCommand = ByteArray(8)
        writeCommand[0] = ST25DV_REQUEST_HEADER
        writeCommand[1] = WRITE_COMMAND
        writeCommand[2] = address.lsb()
        writeCommand[3] = address.msb()
        writeCommand[4] = data[0]
        writeCommand[5] = data[1]
        writeCommand[6] = data[2]
        writeCommand[7] = data[3]
        safeTransceive(writeCommand)
    }

    private fun safeTransceive(command: ByteArray): ByteArray {
        var nTry = 0
        var errorCode = COMMAND_OK
        do {
            val response = tag.transceive(command)
            errorCode = response[0]
            if (errorCode == COMMAND_OK)
                return response.copyOfRange(1, response.size)
            else {
                nTry++
                Thread.sleep(COMMAND_DELAY)
            }
        } while (nTry < COMMAND_RETRY)
        throw ST25DVException("IOError: code $errorCode")
    }

    fun connect() {
        tag.connect()
    }

    fun close() {
        tag.close()
    }

    fun getNfcV(): NfcV {
        return tag
    }

    companion object {
        private const val COMMAND_RETRY = 5
        private const val COMMAND_DELAY = 5L
        private const val COMMAND_OK: Byte = 0x00
        private const val ST25DV_REQUEST_HEADER: Byte = 0x02
        private const val READ_COMMAND: Byte = 0x30
        private const val WRITE_COMMAND: Byte = 0x31

        fun get(tag: Tag): ST25DVTag? {
            val nfcV = NfcV.get(tag)
            return if (nfcV != null) ST25DVTag(nfcV) else null
        }
    }
}

class ST25DVException(message: String?) : IOException(message)

fun Short.lsb(): Byte = (this.toInt() and 0xFF).toByte()
fun Short.msb(): Byte = ((this.toInt() shr 8) and 0xFF).toByte()
