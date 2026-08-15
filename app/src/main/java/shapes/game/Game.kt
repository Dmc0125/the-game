package shapes.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.core.content.res.ResourcesCompat
import kotlin.math.ceil
import kotlin.random.Random

const val CELLS_COUNT = 15
const val CELL_PADDING_FRACTION = 0.08f
const val PLAYGROUND_PADDING_FRACTION = 0.01f
const val DRAG_SENSITIVITY = 2f

val shapes: Array<Array<Coords>> = arrayOf(
    // 1x1
    arrayOf(Coords(1, 1)),
    // 1x2 horizontal domino
    arrayOf(Coords(1, 1), Coords(2, 1)),
    // 2x1 vertical domino
    arrayOf(Coords(1, 1), Coords(1, 2)),
    // 2x2 square
    arrayOf(
        Coords(1, 1), Coords(2, 1),
        Coords(1, 2), Coords(2, 2),
    ),
    // 1x3 horizontal bar
    arrayOf(Coords(0, 1), Coords(1, 1), Coords(2, 1)),
    // 3x1 vertical bar
    arrayOf(Coords(1, 0), Coords(1, 1), Coords(1, 2)),
    // 2x2 L triomino
    arrayOf(
        Coords(1, 1),
        Coords(1, 2), Coords(2, 2),
    ),
    // 3x2 T tetromino
    arrayOf(
        Coords(0, 1), Coords(1, 1), Coords(2, 1),
        Coords(1, 2),
    ),
    // 1x4 horizontal bar
    arrayOf(Coords(0, 2), Coords(1, 2), Coords(2, 2), Coords(3, 2)),
    // 4x1 vertical bar
    arrayOf(Coords(2, 0), Coords(2, 1), Coords(2, 2), Coords(2, 3)),
)

data class Rect(
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Float = 0f,
    var height: Float = 0f,
) {
    var rectf: RectF = RectF(x, y, x + width, y + height)

    fun update() {
        rectf.set(x, y, x + width, y + height)
    }
}

private fun View.dp(value: Float): Float = this.context.dp(value)

private fun View.sp(value: Float): Float = this.context.sp(value)

private fun Context.dp(value: Float): Float {
    return value * resources.displayMetrics.density
}

private fun Context.sp(value: Float): Float {
    return value * resources.displayMetrics.scaledDensity
}

enum class GameState {
    Countdown,
    Placing,
}

class CountdownText(val paint: Paint, context: Context) {
    val textSizeStart = context.sp(30f)
    val textSizeEnd = context.sp(60f)
    val textSizeDiff = textSizeEnd - textSizeStart

    var text: String = ""
    var prev: Int = -1
    var textSize: Float = 0f
    var start = 0f
    var textX: Float = 0f
    var textY: Float = 0f
    var opacity: Float = 1f

    fun update(elapsedTime: Float, pgRect: Rect): Boolean {
        var currentText = when {
            elapsedTime < 1f -> "3"
            elapsedTime < 2f -> "2"
            elapsedTime < 3f -> "1"
            elapsedTime < 4f -> "Go"
            else -> return true
        }

        if (currentText != text) {
            text = currentText
            textSize = textSizeStart
            start = elapsedTime
            opacity = 1f
        } else {
            val dt = elapsedTime - start
            textSize = textSizeStart + textSizeDiff * dt
            opacity = 1f - 1f * dt
        }

        paint.reset()
        paint.textSize = textSize
        paint.typeface = AppFont.bold
        val textWidth = paint.measureText(text)
        textX = pgRect.x + (pgRect.width - textWidth) / 2
        textY = pgRect.y + textSize + (pgRect.height - textSize) / 2

        return false
    }

    fun render(canvas: Canvas) {
        paint.reset()
        paint.color = Color.argb((opacity * 255).toInt(), 255, 255, 255)
        paint.textSize = textSize
        paint.typeface = AppFont.bold
        canvas.drawText(text, textX, textY, paint)
    }
}

class CurrentShape(
    var shape: Int,
    var dragging: Boolean = false,
    var coords: Coords = Coords(5, 5),
) {
    var coordsPrev = Coords(5, 5)
    var overlapping = false

    fun cells(): Iterable<Coords> {
        return Iterable {
            var idx = 0
            val cells = shapes[shape]

            object : Iterator<Coords> {
                override fun hasNext(): Boolean {
                    return idx < cells.size
                }

                override fun next(): Coords {
                    if (!hasNext()) throw NoSuchElementException()
                    val offset = cells[idx]
                    idx += 1
                    return coords + offset
                }
            }
        }
    }

    fun checkOverlap(cells: Array<Boolean>) {
        overlapping = false
        for (cellCoords in cells()) {
            val idx = cellCoords.col + cellCoords.row * CELLS_COUNT
            if (cells[idx]) {
                overlapping = true
                break
            }
        }
    }
}

class ShapesBag {
    var current = -1
    val indexes = IntArray(shapes.size) { it }

    fun next(): Int {
        if (current == indexes.size || current == -1) {
            indexes.shuffle()
            current = 0
        }

        val c = indexes[current]
        current += 1
        return c
    }
}

class GameView(context: Context, val onScoreChange: (Int) -> Unit) : View(context) {
    var running = false
    var lastFrameTime = 0L
    var elapsedTime = 0f
    val paint = Paint()
    var pgRect = Rect()
    var pgPadding = 0f
    var state = GameState.Countdown
    var touch = Touch()
    var countdownText = CountdownText(paint, context)
    var currentShape = CurrentShape(0)
    var cellSize = 0f
    var cellPadding = 0f
    val cells = Array(CELLS_COUNT * CELLS_COUNT) { false }
    val shapesBag = ShapesBag()
    var score = 0
        set(value) {
            field = value
            onScoreChange(value)
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val wf = w.toFloat()
        val hf = h.toFloat()

        pgPadding = wf * PLAYGROUND_PADDING_FRACTION
        pgRect = Rect(0f, 0f, wf, hf)

        cellSize = (wf - pgPadding * 2) / CELLS_COUNT
        cellPadding = cellSize * CELL_PADDING_FRACTION
    }

    fun update(dt: Float) {
        when (state) {
            GameState.Countdown -> {
                // if (countdownText.update(elapsedTime, pgRect)) {
                state = GameState.Placing
                currentShape = CurrentShape(shapesBag.next())
                currentShape.checkOverlap(cells)
                // }
            }

            GameState.Placing -> {
                if (!currentShape.dragging && touch.isDown) {
                    currentShape.dragging = true
                } else if (currentShape.dragging) {
                    if (!touch.isDown) {
                        if (!currentShape.overlapping) {
                            // place
                            for (cellCoords in currentShape.cells()) {
                                val idx = cellCoords.col + cellCoords.row * CELLS_COUNT
                                cells[idx] = true
                            }

                            // delete filled
                            val filledRows = BooleanArray(CELLS_COUNT) { false }
                            val filledCols = BooleanArray(CELLS_COUNT) { false }

                            for (row in 0..<CELLS_COUNT) {
                                var filled = true
                                for (col in 0..<CELLS_COUNT) {
                                    val idx = col + row * CELLS_COUNT
                                    if (!cells[idx]) {
                                        filled = false
                                        break
                                    }
                                }
                                if (filled) {
                                    filledRows[row] = true
                                }
                            }

                            for (col in 0..<CELLS_COUNT) {
                                var filled = true
                                for (row in 0..<CELLS_COUNT) {
                                    val idx = col + row * CELLS_COUNT
                                    if (!cells[idx]) {
                                        filled = false
                                        break
                                    }
                                }
                                if (filled) {
                                    filledCols[col] = true
                                }
                            }

                            for (row in 0..<CELLS_COUNT) {
                                if (filledRows[row]) {
                                    for (col in 0..<CELLS_COUNT) {
                                        val idx = col + row * CELLS_COUNT
                                        cells[idx] = false
                                        score += 10
                                    }
                                }
                            }

                            for (col in 0..<CELLS_COUNT) {
                                if (filledCols[col]) {
                                    for (row in 0..<CELLS_COUNT) {
                                        val idx = col + row * CELLS_COUNT
                                        if (cells[idx]) {
                                            cells[idx] = false
                                            score += 10
                                        }
                                    }
                                }

                            }

                            // new shape
                            currentShape = CurrentShape(shapesBag.next())
                            currentShape.checkOverlap(cells)
                        } else {
                            currentShape.coordsPrev = currentShape.coords.copy()
                        }
                    } else {
                        val diff = touch.position - touch.startPosition
                        val colsDiff = (diff / cellSize) * DRAG_SENSITIVITY
                        val newCoords = currentShape.coordsPrev + colsDiff.toCoords()

                        var minCol = Int.MAX_VALUE
                        var maxCol = Int.MIN_VALUE
                        var minRow = Int.MAX_VALUE
                        var maxRow = Int.MIN_VALUE

                        for (cellOffsets in shapes[currentShape.shape]) {
                            val cellCoords = newCoords + cellOffsets
                            minCol = kotlin.math.min(minCol, cellCoords.col)
                            maxCol = kotlin.math.max(maxCol, cellCoords.col)
                            minRow = kotlin.math.min(minRow, cellCoords.row)
                            maxRow = kotlin.math.max(maxRow, cellCoords.row)
                        }

                        if (minCol < 0) newCoords.col -= minCol
                        if (maxCol >= CELLS_COUNT) newCoords.col -= (maxCol - CELLS_COUNT + 1)
                        if (minRow < 0) newCoords.row -= minRow
                        if (maxRow >= CELLS_COUNT) newCoords.row -= (maxRow - CELLS_COUNT + 1)

                        currentShape.coords = newCoords
                        currentShape.checkOverlap(cells)
                    }
                }
            }
        }
    }


    fun render(canvas: Canvas) {
        fun coordsToPos(coords: Coords): Vec2 {
            val x = coords.col * cellSize + pgPadding
            val y = coords.row * cellSize + pgPadding
            return Vec2(x, y)
        }

        fun renderCell(coords: Coords) {
            val (cellx, celly) = coordsToPos(coords)
            val p = cellPadding
            val r = dp(RADIUS) - p
            canvas.drawRoundRect(
                cellx + p, celly + p, cellx + cellSize - p, celly + cellSize - p,
                r, r,
                paint,
            )
        }

        paint.color = Color.BLACK
        canvas.drawRoundRect(pgRect.rectf, dp(RADIUS), dp(RADIUS), paint)

        run { // draw placed cells
            paint.reset()
            paint.color = Color.LTGRAY
            val scratchCoords = Coords(0, 0)
            for (idx in 0..<CELLS_COUNT * CELLS_COUNT) {
                if (!cells[idx]) {
                    continue
                }

                scratchCoords.col = idx % CELLS_COUNT
                scratchCoords.row = idx / CELLS_COUNT
                renderCell(scratchCoords)
            }
        }

        when (state) {
            GameState.Countdown -> {
                countdownText.render(canvas)
            }

            GameState.Placing -> {
                assert(currentShape.shape < shapes.size)

                paint.reset()
                for (cellCoords in currentShape.cells()) {
                    paint.color = Color.GREEN
                    if (currentShape.overlapping) {
                        paint.color = Color.RED
                    }
                    renderCell(cellCoords)
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!running) {
            println("start")
            return
        }

        val currentTime = System.nanoTime()
        val dt = (currentTime - lastFrameTime).toFloat() / 1e9f
        lastFrameTime = currentTime
        elapsedTime += dt

        update(dt)
        render(canvas)

        postInvalidateOnAnimation()
    }

    fun resume() {
        running = true
        lastFrameTime = System.nanoTime()
    }

    fun pause() {
        running = false
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
}
