package com.inkbridge.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Low-latency 16:9 ink surface. Coordinates sent to the PC are normalized to this surface. */
class DrawingView(context: Context) : View(context) {
    enum class Tool { PEN, MARKER, ERASER }
    private data class InkPoint(val x: Float, val y: Float, val pressure: Float)
    private data class InkStroke(
        val color: Int,
        val widthRatio: Float,
        val mode: Tool,
        val points: MutableList<InkPoint> = mutableListOf()
    )
    private data class EditAction(val added: List<InkStroke>, val removed: List<InkStroke>)

    private val strokes = mutableListOf<InkStroke>()
    private val undoStack = mutableListOf<EditAction>()
    private val redoStack = mutableListOf<EditAction>()
    private var activeStroke: InkStroke? = null
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var tool = Tool.PEN
    private var color = Color.rgb(25, 31, 40)
    private var brushSizeDp = 7f
    private var stylusOnly = true
    private var frameSink: ((String) -> Unit)? = null
    private var content = RectF()

    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(209, 218, 230)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        isFocusable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setFrameSink(sink: (String) -> Unit) { frameSink = sink }
    fun setTool(next: Tool) { tool = next }
    fun setColor(next: Int) { color = next; if (tool == Tool.ERASER) tool = Tool.PEN }
    fun setBrushSizeDp(size: Float) { brushSizeDp = size.coerceIn(2f, 42f) }
    fun setStylusOnly(value: Boolean) { stylusOnly = value }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val ratio = 16f / 9f
        val viewRatio = w.toFloat() / h.coerceAtLeast(1)
        if (viewRatio > ratio) {
            val cw = h * ratio
            val left = (w - cw) / 2f
            content.set(left, 0f, left + cw, h.toFloat())
        } else {
            val ch = w / ratio
            val top = (h - ch) / 2f
            content.set(0f, top, w.toFloat(), top + ch)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(25, 34, 47))
        canvas.drawRoundRect(content, dp(10f), dp(10f), paperPaint)
        canvas.drawRoundRect(content, dp(10f), dp(10f), borderPaint)
        strokes.forEach { drawStroke(canvas, it) }
        activeStroke?.let { drawStroke(canvas, it) }
    }

    private fun drawStroke(canvas: Canvas, stroke: InkStroke) {
        // Eraser strokes are hit-test paths, not visible ink.
        if (stroke.points.isEmpty() || stroke.mode == Tool.ERASER) return
        val base = stroke.widthRatio * min(content.width(), content.height())
        val pts = stroke.points
        val first = pts.first()
        if (pts.size == 1) {
            inkPaint.color = stroke.color
            inkPaint.strokeWidth = base * pressureScale(first.pressure)
            canvas.drawPoint(content.left + first.x * content.width(), content.top + first.y * content.height(), inkPaint)
            return
        }
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            inkPaint.color = stroke.color
            inkPaint.strokeWidth = base * pressureScale((a.pressure + b.pressure) / 2f)
            canvas.drawLine(
                content.left + a.x * content.width(), content.top + a.y * content.height(),
                content.left + b.x * content.width(), content.top + b.y * content.height(), inkPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (activeStroke != null) return true
                val index = event.actionIndex
                val pointerTool = event.getToolType(index)
                val isPen = pointerTool == MotionEvent.TOOL_TYPE_STYLUS || pointerTool == MotionEvent.TOOL_TYPE_ERASER
                if (stylusOnly && !isPen) return true
                val x = event.getX(index)
                val y = event.getY(index)
                if (!content.contains(x, y)) return true
                activePointerId = event.getPointerId(index)
                val actualTool = if (pointerTool == MotionEvent.TOOL_TYPE_ERASER) Tool.ERASER else tool
                beginStroke(x, y, safePressure(event.getPressure(index)), actualTool)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (activeStroke == null || index < 0) return true
                for (h in 0 until event.historySize) {
                    appendPoint(
                        event.getHistoricalX(index, h),
                        event.getHistoricalY(index, h),
                        safePressure(event.getHistoricalPressure(index, h))
                    )
                }
                appendPoint(event.getX(index), event.getY(index), safePressure(event.getPressure(index)))
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) finishActiveStroke(event, event.actionIndex)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val index = if (activePointerId != MotionEvent.INVALID_POINTER_ID) event.findPointerIndex(activePointerId) else -1
                if (activeStroke != null) finishActiveStroke(event, index)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (activeStroke != null) {
                    finishStroke()
                    invalidate()
                }
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun finishActiveStroke(event: MotionEvent, pointerIndex: Int) {
        if (activeStroke != null) {
            if (pointerIndex in 0 until event.pointerCount) {
                appendPoint(event.getX(pointerIndex), event.getY(pointerIndex), safePressure(event.getPressure(pointerIndex)))
            }
            finishStroke()
            invalidate()
        }
        activePointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun beginStroke(x: Float, y: Float, pressure: Float, mode: Tool) {
        val width = min(content.width(), content.height()).coerceAtLeast(1f)
        val localSize = when (mode) {
            Tool.ERASER -> maxOf(18f, brushSizeDp * 2.2f)
            Tool.MARKER -> brushSizeDp * 1.65f
            Tool.PEN -> brushSizeDp
        }
        val widthRatio = dp(localSize) / width
        val stroke = InkStroke(
            color = if (mode == Tool.MARKER) (color and 0x00FFFFFF) or 0x66000000 else color,
            widthRatio = widthRatio,
            mode = mode
        )
        activeStroke = stroke
        val point = normalize(x, y, pressure)
        stroke.points.add(point)
        emit(JSONObject()
            .put("type", "begin")
            .put("mode", if (mode == Tool.ERASER) "erase" else "draw")
            .put("x", point.x).put("y", point.y).put("p", point.pressure)
            .put("color", String.format(Locale.US, "#%08X", stroke.color))
            .put("width", stroke.widthRatio))
        invalidate()
    }

    private fun appendPoint(x: Float, y: Float, pressure: Float) {
        val stroke = activeStroke ?: return
        val p = normalize(x, y, pressure)
        val previous = stroke.points.lastOrNull()
        if (previous != null && abs(previous.x - p.x) < 0.00015f && abs(previous.y - p.y) < 0.00015f) return
        stroke.points.add(p)
        emit(JSONObject().put("type", "point").put("x", p.x).put("y", p.y).put("p", p.pressure))
    }

    private fun finishStroke() {
        val stroke = activeStroke ?: return
        activeStroke = null
        if (stroke.mode == Tool.ERASER) {
            val removed = strokes.filter { existing -> intersects(stroke, existing) }
            if (removed.isNotEmpty()) {
                strokes.removeAll(removed.toSet())
                undoStack.add(EditAction(emptyList(), removed))
                redoStack.clear()
            }
        } else {
            strokes.add(stroke)
            undoStack.add(EditAction(listOf(stroke), emptyList()))
            redoStack.clear()
        }
        emit(JSONObject().put("type", "end"))
    }

    private fun intersects(eraser: InkStroke, existing: InkStroke): Boolean {
        val minDimension = min(content.width(), content.height()).coerceAtLeast(1f)
        val radius = maxOf(8f / minDimension, eraser.widthRatio * 1.25f)
        fun metric(point: InkPoint): Pair<Float, Float> =
            (point.x * content.width() / minDimension) to (point.y * content.height() / minDimension)
        fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared <= 0.0000001f) return hypot(px - ax, py - ay)
            val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
            return hypot(px - (ax + t * dx), py - (ay + t * dy))
        }

        val erasePoints = eraser.points.map(::metric)
        val candidatePoints = existing.points.map(::metric)
        for ((ex, ey) in erasePoints) {
            for ((cx, cy) in candidatePoints) {
                if (hypot(ex - cx, ey - cy) <= radius) return true
            }
        }
        if (erasePoints.size > 1) {
            for (i in 1 until erasePoints.size) {
                val (ax, ay) = erasePoints[i - 1]
                val (bx, by) = erasePoints[i]
                for ((cx, cy) in candidatePoints) {
                    if (distanceToSegment(cx, cy, ax, ay, bx, by) <= radius) return true
                }
            }
        }
        return false
    }

    fun undo(notifyRemote: Boolean = true) {
        if (activeStroke != null) finishStroke()
        val action = undoStack.removeLastOrNull() ?: return
        strokes.removeAll(action.added.toSet())
        strokes.addAll(action.removed)
        redoStack.add(action)
        if (notifyRemote) emit(JSONObject().put("type", "undo"))
        invalidate()
    }

    fun redo(notifyRemote: Boolean = true) {
        if (activeStroke != null) finishStroke()
        val action = redoStack.removeLastOrNull() ?: return
        strokes.removeAll(action.removed.toSet())
        strokes.addAll(action.added)
        undoStack.add(action)
        if (notifyRemote) emit(JSONObject().put("type", "redo"))
        invalidate()
    }

    fun clear(notifyRemote: Boolean = true) {
        if (activeStroke != null) finishStroke()
        if (strokes.isNotEmpty()) {
            val removed = strokes.toList()
            strokes.clear()
            undoStack.add(EditAction(emptyList(), removed))
            redoStack.clear()
        }
        if (notifyRemote) emit(JSONObject().put("type", "clear"))
        invalidate()
    }

    /** Apply desktop-originated commands without echoing them back over Bluetooth. */
    fun applyRemoteCommand(frame: String) {
        try {
            when (JSONObject(frame).optString("type")) {
                "undo" -> undo(notifyRemote = false)
                "redo" -> redo(notifyRemote = false)
                "clear" -> clear(notifyRemote = false)
            }
        } catch (_: Exception) {
            // Ignore malformed/unknown control frames; they must never interrupt pen input.
        }
    }

    fun exportBitmap(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    private fun normalize(x: Float, y: Float, pressure: Float): InkPoint = InkPoint(
        ((x - content.left) / content.width().coerceAtLeast(1f)).coerceIn(0f, 1f),
        ((y - content.top) / content.height().coerceAtLeast(1f)).coerceIn(0f, 1f),
        pressure.coerceIn(0.15f, 1f)
    )

    private fun safePressure(value: Float): Float = if (value.isNaN() || value <= 0f) 0.55f else value.coerceIn(0.15f, 1f)
    private fun pressureScale(value: Float): Float = 0.48f + value.coerceIn(0.15f, 1f) * 0.82f
    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun emit(json: JSONObject) { frameSink?.invoke(json.toString()) }
}
