package com.inkbridge.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var drawingView: DrawingView
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var pageText: TextView
    private lateinit var client: WifiTcpClient
    private lateinit var fullScreenButton: Button
    private var isFullScreen = false

    private val openPdfRequest = 4103
    private val createPdfRequest = 4104
    private var touchInputEnabled = true
    private var renderer: PdfRenderer? = null
    private var currentPdfName = "InkBridge Notebook"
    private var currentPage = 0
    private var notebookPages = 10
    private var isPdfWorkspace = false
    private var templateStyle = "Ruled"
    private val pageSnapshots = mutableMapOf<Int, Bitmap>()
    private var lastSavedPdf: Uri? = null
    private var pendingUpdate: InkBridgeUpdate? = null

    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            checkForUpdates(showWhenNoUpdate = false)
            updateHandler.postDelayed(this, 30L * 60L * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 18, 32)
        window.navigationBarColor = Color.rgb(11, 18, 32)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        client = WifiTcpClient(
            onState = { connected, message ->
                runOnUiThread {
                    if (::statusText.isInitialized) {
                        statusText.text = when {
                            connected -> "● Wi-Fi connected"
                            message.startsWith("Connecting", true) -> "◌ Connecting…"
                            else -> "○ Wi-Fi not connected"
                        }
                    }
                    if (::connectButton.isInitialized) {
                        connectButton.text = if (connected) "Disconnect" else if (message.startsWith("Connecting", true)) "Connecting…" else "Connect Wi-Fi"
                        connectButton.isEnabled = !message.startsWith("Connecting", true)
                    }
                    if (connected && ::drawingView.isInitialized) drawingView.synchronizeToRemote()
                    if (!connected && !message.startsWith("Connecting", true) && message != "Disconnected") {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onCommand = { frame -> runOnUiThread { if (::drawingView.isInitialized) drawingView.applyRemoteCommand(frame) } }
        )

        buildUi()
        checkForUpdates(showWhenNoUpdate = false)
    }

    override fun onStart() {
        super.onStart()
        updateHandler.removeCallbacks(updateRunnable)
        updateHandler.postDelayed(updateRunnable, 30L * 60L * 1000L)
    }

    override fun onStop() {
        updateHandler.removeCallbacks(updateRunnable)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        UpdateManager.retryPendingInstall(this)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(11, 18, 32))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "INKBRIDGE"
            textSize = 18f
            letterSpacing = 0.12f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        statusText = TextView(this).apply {
            text = "○ Wi-Fi not connected"
            textSize = 11f
            maxLines = 1
            setTextColor(Color.rgb(167, 183, 204))
            gravity = Gravity.CENTER_VERTICAL
        }
        connectButton = actionButton("Connect Wi-Fi", Color.rgb(54, 117, 232), dp(112)) { onConnectClicked() }
        fullScreenButton = actionButton("Full screen", null, dp(96)) { toggleFullScreen() }
        connectButton.setOnLongClickListener { showWifiConnectDialog(); true }
        header.background = panelBackground(Color.rgb(18, 30, 45), 17)
        header.setPadding(dp(12), dp(5), dp(8), dp(5))
        header.addView(brand, LinearLayout.LayoutParams(0, dp(48), 1.0f))
        header.addView(statusText, LinearLayout.LayoutParams(0, dp(48), 1.1f))
        header.addView(connectButton)
        header.addView(fullScreenButton)
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
            bottomMargin = dp(8)
        })

        val documentTools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        documentTools.addView(actionButton("Open PDF", null, dp(94)) { openPdfPicker() })
        documentTools.addView(actionButton("New notebook", null, dp(112)) { createNotebookDialog() })
        documentTools.addView(actionButton("‹ Page", null, dp(74)) { changePage(-1) })
        pageText = TextView(this).apply {
            text = "Notebook · 1 / 10"
            textSize = 10f
            setTextColor(Color.rgb(187, 201, 220))
            gravity = Gravity.CENTER
            minWidth = dp(82)
        }
        documentTools.addView(pageText)
        documentTools.addView(actionButton("Page ›", null, dp(74)) { changePage(1) })
        documentTools.addView(actionButton("Save PDF", Color.rgb(42, 126, 225), dp(90)) { savePdfPicker() })
        documentTools.addView(actionButton("Share", null, dp(70)) { shareLastPdf() })
        documentTools.addView(actionButton("Updates", null, dp(78)) { checkForUpdates(showWhenNoUpdate = true) })

        val documentScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            background = panelBackground(Color.rgb(18, 30, 45), 15)
            setPadding(dp(7), dp(5), dp(7), dp(5))
            clipToPadding = false
            addView(documentTools)
        }
        root.addView(documentScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            bottomMargin = dp(7)
        })

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tools.addView(actionButton("Pen", Color.rgb(44, 59, 79), dp(55)) { drawingView.setTool(DrawingView.Tool.PEN) })
        tools.addView(actionButton("Marker", null, dp(68)) { drawingView.setTool(DrawingView.Tool.MARKER) })
        tools.addView(actionButton("Eraser", null, dp(66)) { drawingView.setTool(DrawingView.Tool.ERASER) })
        tools.addView(actionButton("Undo", null, dp(56)) { drawingView.undo() })
        tools.addView(actionButton("Redo", null, dp(56)) { drawingView.redo() })
        tools.addView(actionButton("Clear", null, dp(56)) { confirmClear() })
        tools.addView(actionButton("Color", null, dp(58)) { chooseColor() })
        tools.addView(actionButton("Size", null, dp(52)) { chooseBrushSize() })
        tools.addView(actionButton("Touch: On", null, dp(80)) {
            touchInputEnabled = !touchInputEnabled
            drawingView.setStylusOnly(!touchInputEnabled)
            (it as Button).text = if (touchInputEnabled) "Touch: On" else "Pen only"
        })
        val toolsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            background = panelBackground(Color.rgb(18, 30, 45), 15)
            setPadding(dp(7), dp(5), dp(7), dp(5))
            clipToPadding = false
            addView(tools)
        }
        root.addView(toolsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            bottomMargin = dp(7)
        })

        drawingView = DrawingView(this).apply {
            setFrameSink { frame -> client.send(frame) }
            setStylusOnly(false)
            setBrushSizeDp(3.5f)
        }
        drawingView.background = panelBackground(Color.rgb(24, 35, 51), 18)
        drawingView.clipToOutline = true
        val canvasParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        canvasParams.topMargin = dp(2)
        canvasParams.bottomMargin = dp(5)
        root.addView(drawingView, canvasParams)

        val hint = TextView(this).apply {
            text = "Same Wi-Fi · Connect · Write · Save as PDF"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(145, 164, 188))
        }
        root.addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)))
        setContentView(root)

        val initial = buildTemplate("Ruled")
        drawingView.setPaper(initial, initial.width.toFloat() / initial.height.toFloat())
        updatePageLabel()
    }

    private fun onConnectClicked() {
        if (connectButton.text.toString() == "Disconnect") {
            client.disconnect()
            return
        }
        showWifiConnectDialog()
    }

    private fun showWifiConnectDialog() {
        val prefs = getSharedPreferences("inkbridge", MODE_PRIVATE)
        val hostInput = EditText(this).apply {
            hint = "Laptop IP address (e.g. 192.168.1.20)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(prefs.getString("wifi_host", ""))
        }
        val codeInput = EditText(this).apply {
            hint = "6-digit code shown in InkBridge on the laptop"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            setText(prefs.getString("wifi_code", ""))
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
            addView(TextView(this@MainActivity).apply {
                text = "Connect both devices to the same Wi-Fi network or hotspot. Enter the IP and the 6-digit code shown in InkBridge on Windows."
                textSize = 13f
                setTextColor(Color.rgb(190, 202, 220))
                setPadding(0, 0, 0, dp(12))
            })
            addView(TextView(this@MainActivity).apply {
                text = "LAPTOP IP ADDRESS"
                textSize = 10f
                setTextColor(Color.rgb(130, 158, 195))
            })
            addView(hostInput)
            addView(TextView(this@MainActivity).apply {
                text = "CONNECTION CODE"
                textSize = 10f
                setTextColor(Color.rgb(130, 158, 195))
                setPadding(0, dp(10), 0, 0)
            })
            addView(codeInput)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Connect to your laptop")
            .setView(fields)
            .setPositiveButton("Connect") { _, _ ->
                val host = hostInput.text.toString().trim()
                val code = codeInput.text.toString().trim()
                if (host.isBlank() || code.length != 6 || !code.all(Char::isDigit)) {
                    Toast.makeText(this, "Enter the laptop IP and its 6-digit connection code.", Toast.LENGTH_LONG).show()
                    showWifiConnectDialog()
                } else {
                    prefs.edit().putString("wifi_host", host).putString("wifi_code", code).apply()
                    statusText.text = "◌ Connecting over Wi-Fi…"
                    connectButton.isEnabled = false
                    client.connect(host, code)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleFullScreen() {
        isFullScreen = !isFullScreen
        applyImmersiveMode()
        fullScreenButton.text = if (isFullScreen) "Exit full screen" else "Full screen"
    }

    private fun applyImmersiveMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isFullScreen) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isFullScreen) window.decorView.post { applyImmersiveMode() }
    }

    private fun chooseColor() {
        val names = arrayOf("Black", "Blue", "Red", "Green", "Purple", "Orange")
        val colors = intArrayOf(
            Color.rgb(24, 31, 42), Color.rgb(32, 105, 224), Color.rgb(220, 54, 66),
            Color.rgb(24, 142, 91), Color.rgb(133, 82, 204), Color.rgb(230, 133, 31)
        )
        android.app.AlertDialog.Builder(this).setTitle("Ink color")
            .setItems(names) { _, i -> drawingView.setColor(colors[i]) }.show()
    }

    private fun chooseBrushSize() {
        val slider = SeekBar(this).apply { max = 26; progress = 5 }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(TextView(this@MainActivity).apply { text = "Brush thickness"; setTextColor(Color.DKGRAY) })
            addView(slider)
        }
        android.app.AlertDialog.Builder(this).setTitle("Pen size").setView(box)
            .setPositiveButton("Apply") { _, _ -> drawingView.setBrushSizeDp((slider.progress + 2).toFloat()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmClear() {
        android.app.AlertDialog.Builder(this).setTitle("Clear this page?")
            .setMessage("This removes the current page ink from the tablet and connected laptop.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ -> drawingView.clear() }.show()
    }

    private fun openPdfPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, openPdfRequest)
    }

    private fun createNotebookDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("10")
            hint = "Number of pages"
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(TextView(this@MainActivity).apply {
                text = "Choose a notebook template and page count."
                setTextColor(Color.DKGRAY)
            })
            addView(input)
        }
        val templates = arrayOf("Blank", "Ruled", "Grid", "Dots", "Cornell notes")
        android.app.AlertDialog.Builder(this).setTitle("New notebook").setView(wrapper)
            .setPositiveButton("Choose template") { _, _ ->
                val count = input.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 10
                android.app.AlertDialog.Builder(this).setTitle("Page style")
                    .setItems(templates) { _, index -> startNotebook(templates[index], count) }
                    .setNegativeButton("Cancel", null).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun startNotebook(style: String, count: Int) {
        closePdf()
        currentPdfName = "InkBridge ${style} Notebook"
        templateStyle = style
        isPdfWorkspace = false
        notebookPages = count
        currentPage = 0
        pageSnapshots.clear()
        lastSavedPdf = null
        val bitmap = buildTemplate(style)
        drawingView.setPaper(bitmap, bitmap.width.toFloat() / bitmap.height.toFloat())
        updatePageLabel()
    }

    private fun openPdf(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) { }
            val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Could not open this PDF")
            val nextRenderer = PdfRenderer(descriptor)
            if (nextRenderer.pageCount <= 0) {
                nextRenderer.close()
                throw IllegalStateException("The PDF has no pages")
            }
            closePdf()
            renderer = nextRenderer
            isPdfWorkspace = true
            notebookPages = nextRenderer.pageCount
            currentPdfName = "Imported PDF"
            currentPage = 0
            pageSnapshots.clear()
            lastSavedPdf = null
            showWorkspacePage(0, saveCurrent = false)
            Toast.makeText(this, "PDF opened · ${notebookPages} pages", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun closePdf() {
        try { renderer?.close() } catch (_: Exception) { }
        renderer = null
    }

    private fun changePage(delta: Int) {
        val count = pageCount()
        val next = (currentPage + delta).coerceIn(0, count - 1)
        if (next == currentPage) return
        showWorkspacePage(next, saveCurrent = true)
    }

    private fun pageCount() = renderer?.pageCount ?: notebookPages

    private fun showWorkspacePage(index: Int, saveCurrent: Boolean) {
        if (saveCurrent && ::drawingView.isInitialized) {
            drawingView.finishCurrentStroke()
            pageSnapshots[currentPage] = drawingView.renderPageBitmap()
        }
        currentPage = index
        val snapshot = pageSnapshots[index]
        val bitmap = snapshot ?: if (isPdfWorkspace) renderPdfPage(index) else buildTemplate(templateStyle)
        drawingView.setPaper(bitmap, bitmap.width.toFloat() / bitmap.height.toFloat())
        updatePageLabel()
    }

    private fun renderPdfPage(index: Int): Bitmap {
        val activeRenderer = renderer ?: throw IllegalStateException("No PDF is open")
        val page = activeRenderer.openPage(index)
        try {
            val scale = minOf(1f, 1600f / maxOf(page.width, page.height).toFloat())
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    private fun buildTemplate(style: String): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(219, 229, 241)
            strokeWidth = 2f
        }
        val margin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(234, 151, 151)
            strokeWidth = 2.5f
        }
        when (style) {
            "Ruled" -> {
                var y = 90f
                while (y < height - 55) { canvas.drawLine(70f, y, width - 65f, y, line); y += 58f }
                canvas.drawLine(145f, 55f, 145f, height - 50f, margin)
            }
            "Grid" -> {
                var x = 60f
                while (x < width - 50) { canvas.drawLine(x, 55f, x, height - 55f, line); x += 48f }
                var y = 55f
                while (y < height - 45) { canvas.drawLine(55f, y, width - 55f, y, line); y += 48f }
            }
            "Dots" -> {
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(195, 207, 222) }
                var y = 58f
                while (y < height - 50) {
                    var x = 60f
                    while (x < width - 50) { canvas.drawCircle(x, y, 2.6f, dot); x += 42f }
                    y += 42f
                }
            }
            "Cornell notes" -> {
                var y = 100f
                while (y < height - 55) { canvas.drawLine(70f, y, width - 65f, y, line); y += 58f }
                canvas.drawLine(390f, 60f, 390f, height - 55f, margin)
                canvas.drawLine(70f, 250f, width - 65f, 250f, margin)
            }
        }
        return bitmap
    }

    private fun savePdfPicker() {
        drawingView.finishCurrentStroke()
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "${currentPdfName.replace(' ', '_')}_annotated.pdf")
        }
        startActivityForResult(intent, createPdfRequest)
    }

    private fun exportPdf(uri: Uri) {
        try {
            drawingView.finishCurrentStroke()
            pageSnapshots[currentPage] = drawingView.renderPageBitmap()
            val document = PdfDocument()
            try {
                val count = pageCount()
                for (index in 0 until count) {
                    val bitmap = pageSnapshots[index] ?: if (isPdfWorkspace) renderPdfPage(index) else buildTemplate(templateStyle)
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, null, Rect(0, 0, bitmap.width, bitmap.height), Paint(Paint.FILTER_BITMAP_FLAG))
                    document.finishPage(page)
                    if (pageSnapshots[index] == null) bitmap.recycle()
                }
                contentResolver.openOutputStream(uri)?.use { output -> document.writeTo(output) }
                    ?: throw IllegalStateException("Could not write to the selected file")
            } finally {
                document.close()
            }
            lastSavedPdf = uri
            Toast.makeText(this, "PDF saved successfully · ${pageCount()} pages", Toast.LENGTH_LONG).show()
            android.app.AlertDialog.Builder(this).setTitle("PDF saved")
                .setMessage("Your annotated notebook is ready to share.")
                .setPositiveButton("Share now") { _, _ -> sharePdf(uri) }
                .setNegativeButton("Done", null).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareLastPdf() {
        val uri = lastSavedPdf
        if (uri == null) {
            Toast.makeText(this, "Save the notebook as PDF first.", Toast.LENGTH_LONG).show()
            return
        }
        sharePdf(uri)
    }

    private fun sharePdf(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(contentResolver, "InkBridge PDF", uri)
        }
        startActivity(Intent.createChooser(send, "Share annotated PDF"))
    }

    private fun updatePageLabel() {
        if (::pageText.isInitialized) {
            val source = if (isPdfWorkspace) "PDF" else templateStyle
            pageText.text = "$source · ${currentPage + 1} / ${pageCount()}"
        }
    }

    private fun checkForUpdates(showWhenNoUpdate: Boolean) {
        UpdateManager.check(this) { update, error ->
            if (isFinishing || isDestroyed) return@check
            if (update != null) {
                if (pendingUpdate?.versionCode == update.versionCode) return@check
                pendingUpdate = update
                android.app.AlertDialog.Builder(this)
                    .setTitle("InkBridge update available")
                    .setMessage("Version ${update.tagName} is ready. Download the new APK now?\n\n${update.releaseNotes.take(500)}")
                    .setPositiveButton("Download & install") { _, _ ->
                        Toast.makeText(this, "Downloading update…", Toast.LENGTH_SHORT).show()
                        UpdateManager.downloadAndInstall(this, update) { status ->
                            Toast.makeText(this, status, Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Later", null)
                    .show()
            } else if (showWhenNoUpdate) {
                val message = error ?: "You're using the latest available version."
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun panelBackground(fill: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fill)
        setStroke(dp(1), Color.rgb(42, 58, 78))
    }

    private fun actionButton(label: String, buttonColor: Int?, width: Int = 0, action: (View) -> Unit): Button = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = null
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(buttonColor ?: Color.rgb(30, 43, 62))
            setStroke(dp(1), Color.rgb(48, 65, 88))
        }
        minWidth = 0
        minimumWidth = 0
        minimumHeight = dp(40)
        elevation = dp(1).toFloat()
        setPadding(dp(7), 0, dp(7), 0)
        setOnClickListener(action)
        layoutParams = LinearLayout.LayoutParams(if (width > 0) width else ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
            marginEnd = dp(5)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Activity result API retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        when (requestCode) {
            openPdfRequest -> openPdf(data.data!!)
            createPdfRequest -> exportPdf(data.data!!)
        }
    }

    override fun onDestroy() {
        updateHandler.removeCallbacksAndMessages(null)
        if (::client.isInitialized) client.shutdown()
        closePdf()
        super.onDestroy()
    }
}
