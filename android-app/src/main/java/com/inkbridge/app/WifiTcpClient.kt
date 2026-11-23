package com.inkbridge.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Primary InkBridge transport: authenticated TCP over the same local Wi-Fi/LAN.
 * Bluetooth pairing is deliberately not required for this transport.
 */
class WifiTcpClient(
    private val onState: (connected: Boolean, message: String) -> Unit,
    private val onCommand: (frame: String) -> Unit
) {
    companion object {
        const val DEFAULT_PORT = 45832
        private const val CONNECT_TIMEOUT_MS = 7000
        private const val HANDSHAKE_TIMEOUT_MS = 7000
        private const val MAX_FRAME_CHARS = 8 * 1024 * 1024
    }

    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val readerExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var connected = false

    val isConnected: Boolean get() = connected

    fun connect(host: String, pairingCode: String) {
        io.execute {
            closeSocket()
            val target = host.trim()
            onState(false, "Connecting to $target over Wi-Fi…")
            var candidate: Socket? = null
            try {
                require(target.isNotBlank()) { "Enter the laptop's IP address." }
                require(pairingCode.length == 6 && pairingCode.all(Char::isDigit)) {
                    "The connection code must contain 6 digits."
                }

                val connectedSocket = Socket().apply {
                    tcpNoDelay = true
                    keepAlive = true
                    connect(InetSocketAddress(target, DEFAULT_PORT), CONNECT_TIMEOUT_MS)
                    soTimeout = HANDSHAKE_TIMEOUT_MS
                }
                candidate = connectedSocket

                val input = BufferedReader(InputStreamReader(connectedSocket.getInputStream(), Charsets.UTF_8))
                val output = BufferedWriter(OutputStreamWriter(connectedSocket.getOutputStream(), Charsets.UTF_8))

                output.write(JSONObject()
                    .put("type", "hello")
                    .put("token", pairingCode)
                    .put("protocol", 2)
                    .toString())
                output.newLine()
                output.flush()

                val acknowledgement = input.readLine()
                    ?: throw IOException("The laptop closed the connection before completing the handshake.")
                val response = JSONObject(acknowledgement)
                if (response.optString("type") != "welcome") {
                    throw IOException("Connection code was rejected. Check the 6-digit code shown on the laptop.")
                }

                connectedSocket.soTimeout = 0
                socket = connectedSocket
                writer = output
                connected = true
                onState(true, "Connected to laptop over Wi-Fi")
                startReader(connectedSocket, input)
            } catch (error: Exception) {
                connected = false
                try { candidate?.close() } catch (_: Exception) { }
                if (socket === candidate) socket = null
                writer = null
                val reason = when (error) {
                    is java.net.SocketTimeoutException -> "Connection timed out. Check the IP, Wi-Fi network and Windows Firewall."
                    is java.net.ConnectException -> "Laptop refused the connection. Make sure InkBridge is open on Windows."
                    else -> error.message ?: error.javaClass.simpleName
                }
                onState(false, "Wi-Fi connection failed: $reason")
            }
        }
    }

    private fun startReader(active: Socket, input: BufferedReader) {
        readerExecutor.execute {
            try {
                while (connected && socket === active) {
                    val line = input.readLine() ?: break
                    if (line.isNotBlank() && line.length <= MAX_FRAME_CHARS) onCommand(line)
                }
                if (connected && socket === active) {
                    connected = false
                    closeSocket()
                    onState(false, "Laptop disconnected. Tap Connect to try again.")
                }
            } catch (error: IOException) {
                if (connected && socket === active) {
                    connected = false
                    closeSocket()
                    onState(false, "Wi-Fi disconnected: ${error.message ?: "connection lost"}")
                }
            }
        }
    }

    fun send(frame: String) {
        if (!connected) return
        io.execute { writeFrame(frame) }
    }

    private fun writeFrame(frame: String) {
        val activeWriter = writer ?: return
        try {
            activeWriter.write(frame)
            activeWriter.newLine()
            activeWriter.flush()
        } catch (error: IOException) {
            connected = false
            closeSocket()
            onState(false, "Wi-Fi disconnected: ${error.message ?: "could not send data"}")
        }
    }

    fun sendFile(name: String, mime: String, bytes: ByteArray, onComplete: (Boolean, String) -> Unit) {
        if (!connected) { onComplete(false, "Connect to Windows first."); return }
        io.execute {
            try {
                val id = java.util.UUID.randomUUID().toString().replace("-", "")
                val chunkSize = 256 * 1024
                val total = (bytes.size + chunkSize - 1) / chunkSize
                writeFrame(JSONObject().put("type", "file_start").put("id", id).put("name", name)
                    .put("mime", mime).put("size", bytes.size).put("total", total).toString())
                var offset = 0
                var index = 0
                while (offset < bytes.size) {
                    val length = minOf(chunkSize, bytes.size - offset)
                    val chunk = android.util.Base64.encodeToString(bytes, offset, length, android.util.Base64.NO_WRAP)
                    writeFrame(JSONObject().put("type", "file_chunk").put("id", id)
                        .put("index", index++).put("data", chunk).toString())
                    offset += length
                }
                writeFrame(JSONObject().put("type", "file_end").put("id", id).toString())
                onComplete(true, "File sent to Windows: $name")
            } catch (error: Exception) {
                onComplete(false, "File transfer failed: ${error.message}")
            }
        }
    }

    fun disconnect() {
        io.execute {
            closeSocket()
            onState(false, "Disconnected")
        }
    }

    private fun closeSocket() {
        connected = false
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        writer = null
    }

    fun shutdown() {
        try { socket?.close() } catch (_: Exception) { }
        connected = false
        io.shutdownNow()
        readerExecutor.shutdownNow()
    }
}
