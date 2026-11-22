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

/** Full-size, page-aware ink surface. The displayed page ratio follows the document or template. */
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
    private var brushSizeDp = 3.5f
    private var stylusOnly = false
    private var frameSink: ((String) -> Unit)? = null
    private var content = RectF()
    private var pageAspectRatio = 0.75f
    private var pageBackground: Bitmap? = null

    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(220, 226, 234)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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
    fun setBrushSizeDp(size: Float) { brushSizeDp = size.coerceIn(1.5f, 28f) }
    fun setStylusOnly(value: Boolean) { stylusOnly = value }

    /** Replace the paper and reset only the current page's editing history. */
    fun setPaper(bitmap: Bitmap?, aspectRatio: Float) {
        finishCurrentStroke()
        emit(JSONObject().put("type", "clear"))
        pageBackground = bitmap
        pageAspectRatio = aspectRatio.coerceIn(0.3f, 3.2f)
        clearPage()
        requestLayout()
        invalidate()
    }

    fun clearPage() {
        strokes.clear()
        undoStack.clear()
        redoStack.clear()
        activeStroke = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        invalidate()
    }

    fun finishCurrentStroke() {
        if (activeStroke != null) finishStroke()
        activePointerId = MotionEvent.INVALID_POINTER_ID
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val viewRatio = w.toFloat() / h.toFloat()
        if (viewRatio > pageAspectRatio) {
            val cw = h * pageAspectRatio
            val left = (w - cw) / 2f
            content.set(left, 0f, left + cw, h.toFloat())
        } else {
            val ch = w / pageAspectRatio
            val top = (h - ch) / 2f
            content.set(0f, top, w.toFloat(), top + ch)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(29, 39, 53))
        if (pageBackground != null) {
            canvas.drawBitmap(pageBackground!!, null, content, backgroundPaint)
        } else {
            canvas.drawRect(content, paperPaint)
        }
        canvas.drawRect(content, borderPaint)
        strokes.forEach { drawStroke(canvas, it, content) }
        activeStroke?.let { drawStroke(canvas, it, content) }
    }

    private fun drawStroke(canvas: Canvas, stroke: InkStroke, target: RectF) {
        if (stroke.points.isEmpty() || stroke.mode == Tool.ERASER) return
        val base = stroke.widthRatio * min(target.width(), target.height())
        val pts = stroke.points
        val first = pts.first()
        if (pts.size == 1) {
            inkPaint.color = stroke.color
            inkPaint.strokeWidth = base * pressureScale(first.pressure)
            canvas.drawPoint(target.left + first.x * target.width(), target.top + first.y * target.height(), inkPaint)
            return
        }
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            inkPaint.color = stroke.color
            inkPaint.strokeWidth = base * pressureScale((a.pressure + b.pressure) / 2f)
            canvas.drawLine(
                target.left + a.x * target.width(), target.top + a.y * target.height(),
                target.left + b.x * target.width(), target.top + b.y * target.height(), inkPaint
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
                    appendPoint(event.getHistoricalX(index, h), event.getHistoricalY(index, h), safePressure(event.getHistoricalPressure(index, h)))
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
                finishCurrentStroke()
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
            Tool.ERASER -> maxOf(18f, brushSizeDp * 3.2f)
            Tool.MARKER -> brushSizeDp * 2.1f
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
        val point = normalize(x, y, pressure)
        val previous = stroke.points.lastOrNull()
        if (previous != null && abs(previous.x - point.x) < 0.00012f && abs(previous.y - point.y) < 0.00012f) return
        stroke.points.add(point)
        emit(JSONObject().put("type", "point").put("x", point.x).put("y", point.y).put("p", point.pressure))
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
        val minimum = min(content.width(), content.height()).coerceAtLeast(1f)
        val radius = maxOf(8f / minimum, eraser.widthRatio * 1.3f)
        fun metric(p: InkPoint) = (p.x * content.width() / minimum) to (p.y * content.height() / minimum)
        fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared <= 0.0000001f) return hypot(px - ax, py - ay)
            val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
            return hypot(px - (ax + t * dx), py - (ay + t * dy))
        }
        val erasePoints = eraser.points.map(::metric)
        val candidates = existing.points.map(::metric)
        for ((ex, ey) in erasePoints) for ((cx, cy) in candidates) {
            if (hypot(ex - cx, ey - cy) <= radius) return true
        }
        if (erasePoints.size > 1) for (i in 1 until erasePoints.size) {
            val (ax, ay) = erasePoints[i - 1]
            val (bx, by) = erasePoints[i]
            for ((cx, cy) in candidates) if (distanceToSegment(cx, cy, ax, ay, bx, by) <= radius) return true
        }
        return false
    }

    fun undo(notifyRemote: Boolean = true) {
        finishCurrentStroke()
        val action = undoStack.removeLastOrNull() ?: return
        strokes.removeAll(action.added.toSet())
        strokes.addAll(action.removed)
        redoStack.add(action)
        if (notifyRemote) emit(JSONObject().put("type", "undo"))
        invalidate()
    }

    fun redo(notifyRemote: Boolean = true) {
        finishCurrentStroke()
        val action = redoStack.removeLastOrNull() ?: return
        strokes.removeAll(action.removed.toSet())
        strokes.addAll(action.added)
        undoStack.add(action)
        if (notifyRemote) emit(JSONObject().put("type", "redo"))
        invalidate()
    }

    fun clear(notifyRemote: Boolean = true) {
        finishCurrentStroke()
        if (strokes.isNotEmpty()) {
            val removed = strokes.toList()
            strokes.clear()
            undoStack.add(EditAction(emptyList(), removed))
            redoStack.clear()
        }
        if (notifyRemote) emit(JSONObject().put("type", "clear"))
        invalidate()
    }

    fun applyRemoteCommand(frame: String) {
        try {
            when (JSONObject(frame).optString("type")) {
                "undo" -> undo(notifyRemote = false)
                "redo" -> redo(notifyRemote = false)
                "clear" -> clear(notifyRemote = false)
            }
        } catch (_: Exception) {
        }
    }

    /** A page-sized flattened bitmap used for annotated PDF export and page navigation. */
    fun renderPageBitmap(): Bitmap {
        finishCurrentStroke()
        val width = 1440
        val height = (width / pageAspectRatio).toInt().coerceIn(480, 3600)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val target = RectF(0f, 0f, width.toFloat(), height.toFloat())
        if (pageBackground != null) canvas.drawBitmap(pageBackground!!, null, target, backgroundPaint)
        else canvas.drawRect(target, paperPaint)
        strokes.forEach { drawStroke(canvas, it, target) }
        return bitmap
    }

    fun exportBitmap(): Bitmap? = if (width > 0 && height > 0) renderPageBitmap() else null

    private fun normalize(x: Float, y: Float, pressure: Float) = InkPoint(
        ((x - content.left) / content.width().coerceAtLeast(1f)).coerceIn(0f, 1f),
        ((y - content.top) / content.height().coerceAtLeast(1f)).coerceIn(0f, 1f),
        pressure.coerceIn(0.15f, 1f)
    )
    private fun safePressure(value: Float) = if (value.isNaN() || value <= 0f) 0.55f else value.coerceIn(0.15f, 1f)
    private fun pressureScale(value: Float) = 0.48f + value.coerceIn(0.15f, 1f) * 0.82f
    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun emit(json: JSONObject) { frameSink?.invoke(json.toString()) }
}
