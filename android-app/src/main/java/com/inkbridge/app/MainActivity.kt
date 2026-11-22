package com.inkbridge.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var drawingView: DrawingView
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var client: BluetoothSppClient
    private val permissionRequest = 4102
    private var fingerInputEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 23, 34)
        window.navigationBarColor = Color.rgb(16, 23, 34)
        window.decorView.systemUiVisibility = 0
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        client = BluetoothSppClient(
            onState = { connected, message ->
                runOnUiThread {
                    if (::statusText.isInitialized) statusText.text = message
                    if (::connectButton.isInitialized) {
                        connectButton.text = if (connected) "Disconnect" else "Connect laptop"
                        if (!message.startsWith("Connecting", ignoreCase = true)) connectButton.isEnabled = true
                    }
                }
            },
            onCommand = { frame ->
                runOnUiThread {
                    if (::drawingView.isInitialized) drawingView.applyRemoteCommand(frame)
                }
            }
        )

        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(Color.rgb(16, 23, 34))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "INKBRIDGE"
            textSize = 20f
            setTextColor(Color.WHITE)
            letterSpacing = 0.12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        statusText = TextView(this).apply {
            text = "Not connected · Bluetooth SPP"
            textSize = 11f
            setTextColor(Color.rgb(167, 181, 200))
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
        }
        val statusBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusText)
        }
        connectButton = actionButton("Connect laptop") { onConnectClicked() }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(statusBox, LinearLayout.LayoutParams(0, dp(48), 1.35f))
        header.addView(connectButton, LinearLayout.LayoutParams(dp(142), dp(42)))
        root.addView(header)

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tools.addView(actionButton("Pen") { drawingView.setTool(DrawingView.Tool.PEN) })
        tools.addView(actionButton("Marker") { drawingView.setTool(DrawingView.Tool.MARKER) })
        tools.addView(actionButton("Eraser") { drawingView.setTool(DrawingView.Tool.ERASER) })
        tools.addView(actionButton("Undo") { drawingView.undo() })
        tools.addView(actionButton("Redo") { drawingView.redo() })
        tools.addView(actionButton("Clear") { confirmClear() })
        tools.addView(actionButton("Save PNG") { saveCanvas() })
        tools.addView(actionButton("Finger: Off") {
            fingerInputEnabled = !fingerInputEnabled
            drawingView.setStylusOnly(!fingerInputEnabled)
            (it as Button).text = if (fingerInputEnabled) "Finger: On" else "Finger: Off"
        })
        root.addView(tools, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        val palette = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }
        val colors = listOf(
            "Black" to Color.rgb(25, 31, 40),
            "Blue" to Color.rgb(35, 111, 235),
            "Red" to Color.rgb(225, 65, 73),
            "Green" to Color.rgb(34, 153, 104),
            "Orange" to Color.rgb(245, 157, 48),
            "Purple" to Color.rgb(137, 91, 219)
        )
        colors.forEach { (name, color) ->
            val chip = Button(this).apply {
                text = name
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                setPadding(dp(8), 0, dp(8), 0)
                minWidth = 0
                minimumWidth = 0
                minimumHeight = dp(32)
                setOnClickListener { drawingView.setColor(color) }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32))
            lp.marginEnd = dp(7)
            palette.addView(chip, lp)
        }
        val sizeLabel = TextView(this).apply {
            text = "Size"
            textSize = 11f
            setTextColor(Color.rgb(167, 181, 200))
            setPadding(dp(8), 0, dp(4), 0)
        }
        palette.addView(sizeLabel)
        val size = SeekBar(this).apply {
            max = 28
            progress = 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    drawingView.setBrushSizeDp((progress + 2).toFloat())
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        palette.addView(size, LinearLayout.LayoutParams(dp(150), dp(36)))
        root.addView(palette)

        drawingView = DrawingView(this).apply {
            setFrameSink { frame -> client.send(frame) }
            setStylusOnly(true)
            setBrushSizeDp(7f)
        }
        val canvasLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        canvasLp.topMargin = dp(3)
        canvasLp.bottomMargin = dp(3)
        root.addView(drawingView, canvasLp)

        val footer = TextView(this).apply {
            text = "16:9 writing surface  ·  Stylus pressure supported when the tablet reports it  ·  Keep laptop overlay visible during class"
            textSize = 10f
            setTextColor(Color.rgb(132, 149, 172))
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
        }
        root.addView(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))
        setContentView(root)
    }

    private fun onConnectClicked() {
        if (clientConnectedLabel()) {
            client.disconnect()
            return
        }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), permissionRequest)
            return
        }
        showPairedDevices()
    }

    private fun clientConnectedLabel(): Boolean = connectButton.text.toString() == "Disconnect"

    private fun showPairedDevices() {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null) {
            toast("This tablet does not have Bluetooth")
            return
        }
        if (!adapter.isEnabled) {
            try { startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            catch (_: Exception) { toast("Enable Bluetooth in Android Settings") }
            return
        }
        try {
            val paired = adapter.bondedDevices.sortedBy { safeDeviceName(it).lowercase(Locale.getDefault()) }
            if (paired.isEmpty()) {
                toast("Pair your tablet with the Windows laptop in Android Bluetooth Settings first")
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                return
            }
            val labels = paired.map { "${safeDeviceName(it)}  ·  ${it.address}" }.toTypedArray()
            android.app.AlertDialog.Builder(this)
                .setTitle("Choose your paired Windows laptop")
                .setItems(labels) { _, which ->
                    connectButton.isEnabled = false
                    statusText.text = "Connecting…"
                    client.connect(paired[which])
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: SecurityException) {
            toast("Allow Nearby devices permission to connect")
        }
    }

    private fun safeDeviceName(device: BluetoothDevice): String = try {
        device.name ?: "Bluetooth device"
    } catch (_: SecurityException) { "Bluetooth device" }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) showPairedDevices()
            else toast("Bluetooth permission is required to connect to paired devices")
        }
    }

    private fun confirmClear() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Clear this page?")
            .setMessage("This removes the current ink from the tablet and connected laptop.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ -> drawingView.clear() }
            .show()
    }

    private fun saveCanvas() {
        val bitmap = drawingView.exportBitmap() ?: run { toast("Canvas is not ready yet"); return }
        val filename = "InkBridge_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/InkBridge")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create image")
                contentResolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IllegalStateException("PNG export failed")
                } ?: throw IllegalStateException("Could not open image destination")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                toast("Saved to Pictures/InkBridge")
            } else {
                val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "InkBridge")
                if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Could not create export folder")
                FileOutputStream(File(directory, filename)).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IllegalStateException("PNG export failed")
                }
                toast("Saved in this app's Pictures/InkBridge folder")
            }
        } catch (e: Exception) {
            toast("Save failed: ${e.message}")
        } finally {
            bitmap.recycle()
        }
    }

    private fun actionButton(label: String, action: (View) -> Unit): Button = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(39, 53, 72))
        minWidth = 0
        minimumWidth = 0
        minimumHeight = dp(38)
        setPadding(dp(7), 0, dp(7), 0)
        setOnClickListener(action)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
            marginEnd = dp(5)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        if (::client.isInitialized) client.shutdown()
        super.onDestroy()
    }
}
