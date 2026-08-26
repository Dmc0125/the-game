package shapes.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.util.Log
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.text.font.FontFamily
import jdk.internal.icu.util.CodePointTrie
import shapes.game.*

object AppFont {
    data class FontKey(val name: String, val weight: FontWeight)
    data class Font(val typeface: Typeface, val family: FontFamily)

    val fonts: MutableMap<FontKey, Font> = mutableMapOf()

    fun initFont(context: Context, name: String) {
        val assets = context.assets

        fun loadFontWeight(name: String, weight: FontWeight) {
            try {
                val typeface = Typeface.createFromAsset(
                    assets,
                    "font/${name}_${weight.string().lowercase()}.ttf",
                )
                if (typeface != null) {
                    fonts[FontKey(name, weight)] = Font(typeface, FontFamily(typeface))
                }
            } catch (e: Exception) {
            }
        }

        loadFontWeight(name, FontWeight.ExtraLight)
        loadFontWeight(name, FontWeight.Light)
        loadFontWeight(name, FontWeight.Regular)
        loadFontWeight(name, FontWeight.Medium)
        loadFontWeight(name, FontWeight.SemiBold)
        loadFontWeight(name, FontWeight.Bold)
        loadFontWeight(name, FontWeight.ExtraBold)
    }
}

fun AppFont.typeface(name: String, weight: FontWeight): Typeface {
    val face = fonts[AppFont.FontKey(name, weight)]?.typeface
    require(face != null) { "Font not found: $name $weight" }
    return face
}

fun AppFont.family(name: String, weight: FontWeight): FontFamily {
    val family = fonts[AppFont.FontKey(name, weight)]?.family
    require(family != null) { "Font not found: $name $weight" }
    return family
}

class CanvasRenderer : Renderer {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var canvas: Canvas? = null

    override fun save() {
        canvas?.save()
    }

    override fun restore() {
        canvas?.restore()
    }

    override fun translate(x: Float, y: Float) {
        canvas?.translate(x, y)
    }

    override fun rotate(angle: Float, x: Float, y: Float) {
        canvas?.rotate(angle, x, y)
    }

    override fun scale(scaleX: Float, scaleY: Float) {
        canvas?.scale(scaleX, scaleY)
    }

    override fun scale(scaleX: Float, scaleY: Float, x: Float, y: Float) {
        canvas?.scale(scaleX, scaleY, x, y)
    }

    override fun drawRoundRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) {
        paint.reset()
        paint.color = color
        canvas?.drawRoundRect(x, y, x + width, y + height, radius, radius, paint)
    }

    override fun drawRoundRect(rect: Rect, radius: Float, color: Int) {
        drawRoundRect(rect.x, rect.y, rect.width, rect.height, radius, color)
    }

    override fun strokeRoundRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
        strokeWidth: Float
    ) {
        paint.reset()
        paint.style = Paint.Style.STROKE
        paint.color = color
        paint.strokeWidth = strokeWidth
        canvas?.drawRoundRect(x, y, x + width, y + height, radius, radius, paint)
    }

    override fun drawRect(x: Float, y: Float, width: Float, height: Float, color: Int) {
        paint.reset()
        paint.color = color
        canvas?.drawRect(x, y, x + width, y + height, paint)
    }

    override fun drawRect(rect: Rect, color: Int) {
        drawRect(rect.x, rect.y, rect.width, rect.height, color)
    }

    override fun measureText(text: String, textSize: Float, fontWeight: FontWeight, font: String): Float {
        paint.reset()
        paint.textSize = textSize
        paint.typeface = AppFont.typeface(font, fontWeight)
        return paint.measureText(text)
    }

    override fun drawText(
        text: String,
        x: Float,
        y: Float,
        color: Int,
        textSize: Float,
        fontWeight: FontWeight,
        font: String
    ) {
        paint.reset()
        paint.textSize = textSize
        paint.color = color
        paint.typeface = AppFont.typeface(font, fontWeight)
        canvas?.drawText(text, x, y, paint)
    }

    override fun strokeText(
        text: String,
        x: Float,
        y: Float,
        strokeWidth: Float,
        color: Int,
        textSize: Float,
        fontWeight: FontWeight,
        font: String
    ) {
        paint.reset()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.textSize = textSize
        paint.color = color
        paint.typeface = AppFont.typeface(font, fontWeight)
        canvas?.drawText(text, x, y, paint)
    }
}

class Metrics(val logIntervalSeconds: Float) {
    var measuredFrames = 0
    var startTimeNanos = 0L
    var totalDtNanos = 0L
    var totalUpdateNanos = 0L
    var totalRenderNanos = 0L
}

fun Metrics.begin() {
    startTimeNanos = System.nanoTime()
    measuredFrames = 0
    totalDtNanos = 0L
    totalUpdateNanos = 0L
    totalRenderNanos = 0L
}

fun Metrics.record(
    dtNanos: Long,
    updateNanos: Long,
    renderNanos: Long,
) {
    totalDtNanos += dtNanos
    totalUpdateNanos += updateNanos
    totalRenderNanos += renderNanos
    measuredFrames++

    if (updateNanos > 1e6f) {
        Log.i("Metrics", "slow update=$updateNanos")
    }
}

fun Metrics.log() {
    val now = System.nanoTime()
    val elapsed = (now - startTimeNanos) / 1e9f

    if (elapsed > logIntervalSeconds) {
        val fps = measuredFrames / elapsed
        val avgDtS = totalDtNanos / 1e9f / measuredFrames
        val avgUpdateS = totalUpdateNanos / 1e6f / measuredFrames
        val avgRenderS = totalRenderNanos / 1e6f / measuredFrames

        Log.i(
            "Metrics",
            "fps=$fps, dt=${avgDtS}s, update=${avgUpdateS}ms, render=${avgRenderS}ms",
        )

        begin()
    }
}

class GameView(
    context: Context,
    onScoreChange: onScoreChange? = null,
    onPlaceShape: onPlaceShape? = null,
    onRoundStart: onRoundStart? = null,
    onGameOver: onGameOver? = null,
) : View(context) {
    var running = false
    var lastFrameTime: Long = 0
    val game = GameContext(
        context.resources.displayMetrics.density,
        context.resources.displayMetrics.scaledDensity,
        onScoreChange,
        onPlaceShape,
        onRoundStart,
        onGameOver,
    )
    val touch = Touch()
    var renderer = CanvasRenderer()
    val metrics = Metrics(10f)

    // debug

    fun debugExplodeCell() {
        // NOTE: does not continue after the explosion because of the announcer

        val center = CELLS_COUNT / 2
        val cell = game.board.cells[center * CELLS_COUNT + center]

        cell.filled = true
        cell.color = colors[0]
        cell.filledAt = game.elapsedTime
        cellBeginClearingAnimation(cell, 0f, game.elapsedTime)

        game.state = GameState.ClearingAnimation
    }

    fun debugFillRow() {
        val row = CELLS_COUNT / 2
        val center = CELLS_COUNT / 2

        for (col in 0..<CELLS_COUNT) {
            val cell = game.board.cells[row * CELLS_COUNT + col]
            cell.filled = true
            cell.color = colors[0]
            cell.filledAt = game.elapsedTime
        }

        boardClearFilledCells(game.board, game.elapsedTime)
        game.state = GameState.ClearingAnimation
    }

    fun debugFillDouble() {
        val centerRow = CELLS_COUNT / 2

        for (row in arrayOf(centerRow, centerRow + 1)) {
            for (col in 0..<CELLS_COUNT) {
                val cell = game.board.cells[row * CELLS_COUNT + col]
                cell.filled = true
                cell.color = colors[0]
                cell.filledAt = game.elapsedTime
            }
        }

        boardClearFilledCells(game.board, game.elapsedTime)
        game.state = GameState.ClearingAnimation
    }

    fun debugSpawnShape() {
        game.currentShape = CurrentShape(0)
        game.state = GameState.Placing
    }

    fun debugAnnounce() {
        announcerAnnounce(game.announcer, AnnouncerType.Single, 0, 0, game.elapsedTime)
    }

    //

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        game.changedWidth = w.toFloat()
        game.changedHeight = h.toFloat()
    }

    fun resume() {
        running = true
        lastFrameTime = System.nanoTime()
    }

    fun pause() {
        running = false
    }

    fun handleShapeRotate() {
        game.pendingRotation = true
    }

    fun handleShapePlace() {
        game.pendingPlacement = true
    }

    fun handleTouch(event: PointerEvent) {
        val me = event.motionEvent ?: return

        when (me.action) {
            MotionEvent.ACTION_DOWN -> {
                touch.isDown = true
                touch.position.x = me.x
                touch.position.y = me.y
                touch.startPosition.x = me.x
                touch.startPosition.y = me.y
            }

            MotionEvent.ACTION_MOVE -> {
                touch.position.x = me.x
                touch.position.y = me.y
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touch.isDown = false
            }

            else -> return
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!running) return

        renderer.canvas = canvas
        Platform.withRenderer(renderer)

        val currentTime = System.nanoTime()
        val delaTimeNanos = (currentTime - lastFrameTime)
        val deltaTime = delaTimeNanos / 1e9f
        lastFrameTime = currentTime

        game.dt = deltaTime
        game.elapsedTime += deltaTime

        val updateStartNs = System.nanoTime()
        gameUpdate(game, touch)
        val updateElapsedSeconds = System.nanoTime() - updateStartNs

        val renderStartNs = System.nanoTime()
        gameRender(game)
        val renderElapsedSeconds = System.nanoTime() - renderStartNs

        metrics.record(
            delaTimeNanos,
            updateElapsedSeconds,
            renderElapsedSeconds,
        )
        metrics.log()

        postInvalidateOnAnimation()
    }
}

class NextShapeView(context: Context) : View(context) {
    val maxCellSize = 49f
    val rectPaddingFraction = 0.1f

    var rect = Rect()
    var innerRect = Rect()

    val paint = Paint()
    var cellSize = 0f
    var shape: Array<Coords>? = null

    var minCol = Int.MAX_VALUE
    var minRow = Int.MAX_VALUE
    var totalWidth = 0f
    var totalHeight = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val wf = w.toFloat()
        val hf = h.toFloat()

        rect = Rect(0f, 0f, wf, hf)
        val padding = rect.width * rectPaddingFraction
        innerRect = Rect(padding, padding, rect.width - padding, rect.height - padding)
    }

    fun onNextShape(shapeIdx: Int) {
        shape = shapesMap[shapeRotationIndex(shapeIdx, 0)]
    }

    fun update() {
        if (shape == null) return

        minCol = Int.MAX_VALUE
        var maxCol = Int.MIN_VALUE
        minRow = Int.MAX_VALUE
        var maxRow = Int.MIN_VALUE

        for (cell in shape) {
            minCol = kotlin.math.min(minCol, cell.col)
            maxCol = kotlin.math.max(maxCol, cell.col)
            minRow = kotlin.math.min(minRow, cell.row)
            maxRow = kotlin.math.max(maxRow, cell.row)
        }

        val cols = maxCol - minCol + 1
        val rows = maxRow - minRow + 1

        cellSize = minOf(
            innerRect.width / cols.toFloat(),
            innerRect.height / rows.toFloat(),
            maxCellSize,
        )

        totalWidth = cols * cellSize
        totalHeight = rows * cellSize
    }

    fun render(canvas: Canvas) {
        if (shape != null) {
            paint.reset()
            paint.color = colors[0]

            val left = innerRect.x + (innerRect.width - totalWidth) / 2f
            val top = innerRect.y + (innerRect.height - totalHeight) / 2f

            for (cell in shape) {
                val (col, row) = cell

                var x = left + (col - minCol) * cellSize
                var y = top + (row - minRow) * cellSize

                val padding = cellSize * 0.05f
                val radius = cellSize * CELL_RADIUS_FRACTION

                canvas.drawRoundRect(
                    x + padding,
                    y + padding,
                    x + cellSize - padding,
                    y + cellSize - padding,
                    radius, radius,
                    paint,
                )
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        update()
        render(canvas)
        postInvalidateOnAnimation()
    }
}
