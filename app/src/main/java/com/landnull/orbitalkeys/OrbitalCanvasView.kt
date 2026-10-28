package com.landnull.orbitalkeys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.sqrt

enum class KeyboardLayoutType {
    ORBITAL_HUB,
    QWERTY,
    DVORAK,
    COLEMAK
}

data class KeyNode(
    val label: String,
    val bounds: RectF,
    val rawChar: Char? = null,
    val rawActionCode: Int? = null,
    var isSticky: Boolean = false
)

data class PointTrace(val x: Float, val y: Float, val timeMs: Long)

@SuppressLint("ViewConstructor")
class OrbitalCanvasView(
    context: Context,
    private val inputDispatcher: OrbitalKeysService
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val keyNodes = mutableListOf<KeyNode>()
    private var currentLayoutType = KeyboardLayoutType.ORBITAL_HUB

    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(18f)
        textAlign = Paint.Align.CENTER
    }
    private val paintKeyBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1e293b")
        style = Paint.Style.FILL
    }
    private val paintKeyBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38bdf8")
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val paintTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38bdf8")
        strokeWidth = dp(6f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val paintGutterBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0f172a")
        style = Paint.Style.FILL
    }
    private val paintGutterText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38bdf8")
        textSize = dp(16f)
        typeface = Typeface.DEFAULT_BOLD
    }

    private var draggedNode: KeyNode? = null
    private var isDragging = false
    private val swipePoints = mutableListOf<PointTrace>()
    private var swipeStartTime: Long = 0

    private var longPressThresholdMs: Long = 400
    private var isEditModeActive = false
    private var isTrainingModeActive = false
    private var isMenuShowing = false

    private var trainingHint: String = "Long-press any key for menu"
    private var suggestionCandidates = listOf("orbit", "orbital", "test")

    private val modeTimeoutMs = 12000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val exitModeRunnable = Runnable {
        if (isEditModeActive || isTrainingModeActive) {
            isEditModeActive = false
            isTrainingModeActive = false
            isDragging = false
            draggedNode = null
            invalidate()
            Toast.makeText(context, "Mode timed out", Toast.LENGTH_SHORT).show()
        }
    }

    private var countdownText: String = ""
    private val paintCountdownText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#f59e0b")
        textSize = dp(48f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    init {
        loadLayout(KeyboardLayoutType.ORBITAL_HUB)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = dp(320f).toInt()
        setMeasuredDimension(width, desiredHeight)
    }

    private fun resetModeTimeout() {
        mainHandler.removeCallbacks(exitModeRunnable)
        if (isEditModeActive || isTrainingModeActive) {
            mainHandler.postDelayed(exitModeRunnable, modeTimeoutMs)
        }
    }

    fun loadLayout(type: KeyboardLayoutType) {
        currentLayoutType = type
        keyNodes.clear()
        when (type) {
            KeyboardLayoutType.ORBITAL_HUB -> setupOrbitalScaffold()
            KeyboardLayoutType.QWERTY -> setupStandardGrid(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"))
            KeyboardLayoutType.DVORAK -> setupStandardGrid(listOf("pyfgcrl", "aoeuidhtns", "qjkxbmwvz"))
            KeyboardLayoutType.COLEMAK -> setupStandardGrid(listOf("qwfpgjluy", "arstdhneio", "zxcvbkm"))
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Rebuild layout once we know real dimensions
        loadLayout(currentLayoutType)
    }

    private fun setupOrbitalScaffold() {
        val gutter = dp(48f)
        val keySize = dp(64f)
        val gap = dp(8f)

        // Top row modifiers
        keyNodes.add(KeyNode("CTRL", RectF(dp(8f), gutter + dp(8f), dp(8f) + keySize, gutter + dp(8f) + keySize)))
        keyNodes.add(KeyNode("ALT", RectF(dp(8f) + keySize + gap, gutter + dp(8f), dp(8f) + keySize * 2 + gap, gutter + dp(8f) + keySize)))
        keyNodes.add(KeyNode("TAB", RectF(width - dp(8f) - keySize * 3 - gap * 2, gutter + dp(8f), width - dp(8f) - keySize * 2 - gap, gutter + dp(8f) + keySize), rawActionCode = KeyEvent.KEYCODE_TAB))
        keyNodes.add(KeyNode("MIC", RectF(width - dp(8f) - keySize * 2 - gap, gutter + dp(8f), width - dp(8f) - keySize - gap, gutter + dp(8f) + keySize)))
        keyNodes.add(KeyNode("ENT", RectF(width - dp(8f) - keySize, gutter + dp(8f), width - dp(8f), gutter + dp(8f) + keySize), rawActionCode = KeyEvent.KEYCODE_ENTER))

        // Center typing area (sample letters)
        val centerY = gutter + dp(100f)
        val letters = listOf('a', 'r', 'o', 'b', 'i', 't')
        val startX = (width - (letters.size * keySize + (letters.size - 1) * gap)) / 2f
        letters.forEachIndexed { i, c ->
            val left = startX + i * (keySize + gap)
            keyNodes.add(KeyNode(c.uppercaseChar().toString(), RectF(left, centerY, left + keySize, centerY + keySize), rawChar = c))
        }

        // Bottom controls
        keyNodes.add(KeyNode("DEL", RectF(width - dp(8f) - keySize, height - dp(8f) - keySize, width - dp(8f), height - dp(8f)), rawActionCode = KeyEvent.KEYCODE_DEL))
        keyNodes.add(KeyNode("SPC", RectF(dp(80f), height - dp(8f) - keySize, width - dp(100f), height - dp(8f)), rawChar = ' '))
    }

    private fun setupStandardGrid(rows: List<String>) {
        val gutter = dp(48f)
        val keyWidth = dp(36f)
        val keyHeight = dp(48f)
        val spacing = dp(4f)
        val startY = gutter + dp(16f)

        rows.forEachIndexed { rowIndex, rowChars ->
            val rowWidth = rowChars.length * (keyWidth + spacing) - spacing
            val startX = (width - rowWidth) / 2f
            rowChars.forEachIndexed { colIndex, char ->
                val left = startX + colIndex * (keyWidth + spacing)
                val top = startY + rowIndex * (keyHeight + spacing)
                keyNodes.add(KeyNode(char.uppercaseChar().toString(), RectF(left, top, left + keyWidth, top + keyHeight), rawChar = char))
            }
        }

        // Add space and delete
        val bottomY = startY + rows.size * (keyHeight + spacing) + dp(8f)
        keyNodes.add(KeyNode("SPC", RectF(dp(60f), bottomY, width - dp(80f), bottomY + keyHeight), rawChar = ' '))
        keyNodes.add(KeyNode("DEL", RectF(width - dp(70f), bottomY, width - dp(8f), bottomY + keyHeight), rawActionCode = KeyEvent.KEYCODE_DEL))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isEditModeActive || isTrainingModeActive) resetModeTimeout()

        val x = event.x
        val y = event.y
        val currentTime = System.currentTimeMillis()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipePoints.clear()
                swipeStartTime = currentTime
                swipePoints.add(PointTrace(x, y, 0))
                draggedNode = keyNodes.find { it.bounds.contains(x, y) }
            }
            MotionEvent.ACTION_MOVE -> {
                val elapsed = currentTime - swipeStartTime
                swipePoints.add(PointTrace(x, y, elapsed))
                invalidate()

                if (draggedNode != null && elapsed > longPressThresholdMs) {
                    if (!isEditModeActive && !isTrainingModeActive && !isMenuShowing && countdownText.isEmpty()) {
                        isMenuShowing = true
                        showMasterContextMenu()
                    } else if (isEditModeActive) {
                        isDragging = true
                        draggedNode!!.bounds.offsetTo(x - draggedNode!!.bounds.width() / 2, y - draggedNode!!.bounds.height() / 2)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging && draggedNode != null) {
                    val targetNode = keyNodes.find { it != draggedNode && it.bounds.intersect(draggedNode!!.bounds) }
                    if (targetNode != null) {
                        val tempBounds = RectF(targetNode.bounds)
                        targetNode.bounds.set(draggedNode!!.bounds)
                        draggedNode!!.bounds.set(tempBounds)
                        OrbitalExporter.exportLayout(context, keyNodes)
                        Toast.makeText(context, "Layout saved to Downloads", Toast.LENGTH_SHORT).show()
                    }
                    isDragging = false
                    draggedNode = null
                } else {
                    processCompletedStroke()
                }
                swipePoints.clear()
                invalidate()
            }
        }
        return true
    }

    private fun processCompletedStroke() {
        if (swipePoints.size < 3 && draggedNode != null) {
            if (draggedNode!!.label == "MIC") {
                inputDispatcher.startVoiceRecognition()
            } else {
                draggedNode!!.rawChar?.let { inputDispatcher.sendRawChar(it) }
                draggedNode!!.rawActionCode?.let { inputDispatcher.sendRawAction(it) }
            }
        } else if (swipePoints.size >= 3) {
            var totalLength = 0f
            for (i in 1 until swipePoints.size) {
                val dx = swipePoints[i].x - swipePoints[i - 1].x
                val dy = swipePoints[i].y - swipePoints[i - 1].y
                totalLength += sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            }
            val duration = swipePoints.last().timeMs
            OrbitalExporter.exportSwipeCorpus(context, swipePoints, duration, totalLength)
        }
    }

    private fun showMasterContextMenu() {
        val options = arrayOf(
            "Enter Edit Mode (Drag & Drop)",
            "Enter Training Mode",
            "Switch Layout (Current: $currentLayoutType)"
        )

        android.app.AlertDialog.Builder(context)
            .setTitle("OrbitalKeys Control")
            .setItems(options) { _, which ->
                isMenuShowing = false
                when (which) {
                    0 -> startLiftoffSequence()
                    1 -> enterTrainingMode()
                    2 -> showLayoutPicker()
                }
            }
            .setOnCancelListener { isMenuShowing = false }
            .create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                show()
            }
    }

    private fun showLayoutPicker() {
        val layouts = arrayOf("Orbital Hub", "QWERTY", "Dvorak", "Colemak")
        val types = arrayOf(
            KeyboardLayoutType.ORBITAL_HUB,
            KeyboardLayoutType.QWERTY,
            KeyboardLayoutType.DVORAK,
            KeyboardLayoutType.COLEMAK
        )

        android.app.AlertDialog.Builder(context)
            .setTitle("Select Keyboard Layout")
            .setItems(layouts) { _, which ->
                loadLayout(types[which])
                Toast.makeText(context, "Loaded: ${layouts[which]}", Toast.LENGTH_SHORT).show()
            }
            .create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                show()
            }
    }

    private fun enterTrainingMode() {
        isTrainingModeActive = true
        resetModeTimeout()
        Toast.makeText(context, "Training Mode — swipe trails will be recorded", Toast.LENGTH_LONG).show()
        invalidate()
    }

    private fun startLiftoffSequence() {
        var count = 3
        val runnable = object : Runnable {
            override fun run() {
                if (count > 0) {
                    countdownText = "$count..."
                    invalidate()
                    count--
                    mainHandler.postDelayed(this, 800)
                } else if (count == 0) {
                    countdownText = "EDIT MODE"
                    isEditModeActive = true
                    resetModeTimeout()
                    invalidate()
                    count--
                    mainHandler.postDelayed(this, 1200)
                } else {
                    countdownText = ""
                    invalidate()
                }
            }
        }
        mainHandler.post(runnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Gutter
        canvas.drawRect(0f, 0f, width.toFloat(), dp(40f), paintGutterBg)
        val status = when {
            isEditModeActive -> "EDIT MODE — Drag keys, release on another to swap"
            isTrainingModeActive -> "TRAINING — $trainingHint"
            else -> "Long-press a key for menu | Layout: $currentLayoutType"
        }
        canvas.drawText(status, dp(12f), dp(26f), paintGutterText)

        if (isTrainingModeActive) {
            var chipX = dp(12f)
            suggestionCandidates.forEach { candidate ->
                val chipWidth = dp(80f)
                val chipRect = RectF(chipX, dp(48f), chipX + chipWidth, dp(72f))
                canvas.drawRoundRect(chipRect, dp(8f), dp(8f), paintKeyBg)
                canvas.drawText(candidate, chipRect.centerX(), chipRect.centerY() + dp(6f), paintGutterText)
                chipX += chipWidth + dp(8f)
            }
        }

        for (node in keyNodes) {
            canvas.drawRoundRect(node.bounds, dp(12f), dp(12f), paintKeyBg)
            paintKeyBorder.color = if (node.isSticky) Color.parseColor("#f59e0b") else Color.parseColor("#38bdf8")
            canvas.drawRoundRect(node.bounds, dp(12f), dp(12f), paintKeyBorder)

            val textY = node.bounds.centerY() - (paintText.descent() + paintText.ascent()) / 2
            canvas.drawText(node.label, node.bounds.centerX(), textY, paintText)
        }

        if (swipePoints.size > 1) {
            val path = Path().apply {
                moveTo(swipePoints[0].x, swipePoints[0].y)
                for (i in 1 until swipePoints.size) {
                    lineTo(swipePoints[i].x, swipePoints[i].y)
                }
            }
            canvas.drawPath(path, paintTrail)
        }

        if (countdownText.isNotEmpty()) {
            val textY = height / 2f
            canvas.drawText(countdownText, width / 2f, textY, paintCountdownText)
        }
    }
}
