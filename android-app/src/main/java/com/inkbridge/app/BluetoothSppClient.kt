package com.inkbridge.app

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Secure, bidirectional Bluetooth Classic RFCOMM/SPP connection. */
class BluetoothSppClient(
    private val onState: (connected: Boolean, message: String) -> Unit,
    private val onCommand: (frame: String) -> Unit
) {
    companion object {
        // Standard Serial Port Profile UUID. Windows must expose an incoming SPP COM port.
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val reader: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var connected = false

    fun connect(device: BluetoothDevice) {
        io.execute {
            closeSocket()
            onState(false, "Connecting to ${safeName(device)}…")
            var candidate: BluetoothSocket? = null
            try {
                val created = device.createRfcommSocketToServiceRecord(SPP_UUID)
                candidate = created
                created.connect()
                socket = created
                connected = true
                onState(true, "Connected to ${safeName(device)}")
                startReader(created)
            } catch (e: Exception) {
                connected = false
                try { candidate?.close() } catch (_: Exception) { }
                try { socket?.close() } catch (_: Exception) { }
                socket = null
                onState(false, "Connection failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun startReader(active: BluetoothSocket) {
        reader.execute {
            try {
                BufferedReader(InputStreamReader(active.inputStream, Charsets.UTF_8)).use { input ->
                    while (connected && socket === active) {
                        val line = input.readLine() ?: break
                        if (line.isNotBlank()) onCommand(line)
                    }
                }
                if (connected && socket === active) {
                    connected = false
                    try { active.close() } catch (_: Exception) { }
                    socket = null
                    onState(false, "Bluetooth connection closed by the laptop")
                }
            } catch (e: IOException) {
                if (connected && socket === active) {
                    connected = false
                    try { active.close() } catch (_: Exception) { }
                    socket = null
                    onState(false, "Bluetooth disconnected: ${e.message ?: "I/O error"}")
                }
            }
        }
    }

    fun send(frame: String) {
        if (!connected) return
        io.execute {
            val active = socket ?: return@execute
            try {
                active.outputStream.write((frame + "\n").toByteArray(Charsets.UTF_8))
                active.outputStream.flush()
            } catch (e: IOException) {
                connected = false
                closeSocket()
                onState(false, "Bluetooth disconnected: ${e.message ?: "I/O error"}")
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
    }

    private fun safeName(device: BluetoothDevice): String = try {
        device.name ?: device.address
    } catch (_: SecurityException) {
        "Bluetooth device"
    }

    fun shutdown() {
        io.execute { closeSocket() }
        io.shutdown()
        reader.shutdown()
    }
}
