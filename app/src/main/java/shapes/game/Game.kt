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
const val CELL_PADDING_FRACTION = 0.1f
const val PLAYGROUND_PADDING_FRACTION = 0.02f
const val DRAG_SENSITIVITY = 1.75f
const val SHAPE_MOVEMENT_ANIMATION_DURATION = 0.065f
const val EXPLOSION_CHARGE_ANIMATION_DURATION = 0.5f
const val EXPLOSION_CELL_PULSE_COUNT = 5
const val EXPLOSION_CELL_SCALE_MAX = 1.1f

val GCOLOR_RED = Color.rgb(197, 40, 61)
val GCOLOR_OVERLAPPING = Color.argb(200, Color.red(GCOLOR_RED), Color.green(GCOLOR_RED), Color.blue(GCOLOR_RED))
val GCOLOR_YELLOW = Color.rgb(226, 239, 112)
val GCOLOR_BLACK = Color.rgb(29, 32, 32)

val GCOLOR_BLUE = Color.rgb(112, 228, 239)
val GCOLOR_PURPLE = Color.rgb(203, 66, 159)
val GCOLOR_ORANGE = Color.rgb(217, 93, 57)
val GCOLOR_PINK = Color.rgb(227, 86, 124)

val colors: Array<Int> = arrayOf(
    GCOLOR_BLUE,
    GCOLOR_PURPLE,
    GCOLOR_ORANGE,
    GCOLOR_PINK,
)

fun generateShapeOffsets(base: Array<Coords>): Array<Array<Coords>> {
    fun center(offsets: Array<Coords>): Array<Coords> {
        // normalize
        val normalized = offsets.map { it.copy() }.toTypedArray()
        var minCol = Int.MAX_VALUE
        var minRow = Int.MAX_VALUE
        for (idx in normalized.indices) {
            minCol = kotlin.math.min(minCol, normalized[idx].col)
            minRow = kotlin.math.min(minRow, normalized[idx].row)
        }

        // center
        var width = Int.MIN_VALUE
        var height = Int.MIN_VALUE
        for (idx in normalized.indices) {
            normalized[idx].col -= minCol
            normalized[idx].row -= minRow

            width = kotlin.math.max(width, normalized[idx].col)
            height = kotlin.math.max(height, normalized[idx].row)
        }

        val centerColOffset = (4 - width) / 2
        val centerRowOffset = (4 - height) / 2
        for (idx in normalized.indices) {
            normalized[idx].col += centerColOffset
            normalized[idx].row += centerRowOffset
        }

        return normalized
    }

    fun rotate(offsets: Array<Coords>): Array<Coords> {
        val rotated = offsets.map { it.copy() }.toTypedArray()
        for ((i, offset) in offsets.withIndex()) {
            val (col, row) = offset
            rotated[i].col = 3 - row
            rotated[i].row = col
        }
        return rotated
    }

    val r1 = center(base)
    val r2 = rotate(r1)
    val r3 = rotate(r2)
    val r4 = rotate(r3)
    return arrayOf(r1, r2, r3, r4)
}

val shapes: Array<Array<Coords>> = arrayOf(
    // 1x1
    *generateShapeOffsets(arrayOf(Coords(1, 1))),
    // 1x2 horizontal domino
    *generateShapeOffsets(arrayOf(Coords(1, 1), Coords(2, 1))),
    // 2x1 vertical domino
    *generateShapeOffsets(arrayOf(Coords(1, 1), Coords(1, 2))),
    // 2x2 square
    *generateShapeOffsets(arrayOf(Coords(1, 1), Coords(2, 1), Coords(1, 2), Coords(2, 2))),
    // 1x3 horizontal bar
    *generateShapeOffsets(arrayOf(Coords(0, 1), Coords(1, 1), Coords(2, 1))),
    // 3x1 vertical bar
    *generateShapeOffsets(arrayOf(Coords(1, 0), Coords(1, 1), Coords(1, 2))),
    // 2x2 L triomino
    *generateShapeOffsets(arrayOf(Coords(1, 1), Coords(1, 2), Coords(2, 2))),
    // 3x2 T tetromino
    *generateShapeOffsets(arrayOf(Coords(0, 1), Coords(1, 1), Coords(2, 1), Coords(1, 2))),
    // 1x4 horizontal bar
    *generateShapeOffsets(arrayOf(Coords(0, 2), Coords(1, 2), Coords(2, 2), Coords(3, 2))),
    // 4x1 vertical bar
    *generateShapeOffsets(arrayOf(Coords(2, 0), Coords(2, 1), Coords(2, 2), Coords(2, 3))),
)
val SHAPES_INDICES = shapes.size / 4

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
    AnimatingCurrentShape,
    AnimatingCellsExplosion,
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

fun lerp(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress
fun lerp(start: Vec2, end: Vec2, progress: Float): Vec2 = start + (end - start) * progress
fun lerp(start: Int, end: Int, progress: Float): Int = (start + (end - start) * progress).toInt()

data class Animation<T>(
    var current: T,
    val duration: Float,
    var delay: Float = 0f,
    val lerp: (start: T, end: T, progress: Float) -> T,
) {
    var animating = false
    var animationStartTime = 0f
    var animationStartValue: T = current
    var animationEndValue: T = current

    fun begin(elapsedTime: Float, endVal: T) {
        if (animating) {
            update(elapsedTime)
        }

        if (endVal == current) return

        animationStartValue = current
        animationEndValue = endVal
        animationStartTime = elapsedTime
        animating = true
    }

    fun update(elapsedTime: Float) {
        if (!animating) return

        val animatingTime = elapsedTime - animationStartTime
        if (animatingTime < delay) {
            return
        }

        val progress = (animatingTime - delay) / duration
        if (progress >= 1f) {
            current = animationEndValue
            animating = false
            return
        }

        current = lerp(animationStartValue, animationEndValue, progress)
    }
}

class CurrentShape(
    var shape: Int = -1,
) {
    var rotation: Int = 0
    var dragging: Boolean = false
    var coords: Coords = Coords(5, 5)

    val color: Int = colors[Random.nextInt(colors.size)]

    var coordsPrev = coords.copy()
    var overlapping = false

    val movementAnimation = Animation(coords.toVec2(), SHAPE_MOVEMENT_ANIMATION_DURATION, lerp = ::lerp)
    val rotationAnimation = Animation(rotation * 90f, SHAPE_MOVEMENT_ANIMATION_DURATION, lerp = ::lerp)

    fun beginMovementAnimation(elapsedTime: Float) {
        movementAnimation.begin(elapsedTime, coords.toVec2())
    }

    fun updateMovementAnimation(elapsedTime: Float) {
        movementAnimation.update(elapsedTime)
    }

    fun beginRotationAnimation(elapsedTime: Float) {
        rotationAnimation.begin(elapsedTime, rotation * 90f)
    }

    fun updateRotationAnimation(elapsedTime: Float) {
        rotationAnimation.update(elapsedTime)
    }

    fun shapeIdx(): Int {
        return (shape * 4) + (rotation % 4)
    }

    fun cells(): Iterable<Coords> {
        return Iterable {
            val shapeIdx = shapeIdx()
            val cells = shapes[shapeIdx]
            var idx = 0

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

    fun checkOverlap(cells: Array<Cell>) {
        overlapping = false
        for (cellCoords in cells()) {
            val idx = cellCoords.col + cellCoords.row * CELLS_COUNT
            if (cells[idx].filled) {
                overlapping = true
                break
            }
        }
    }
}

class ShapesBag {
    var current = -1
    val indexes = IntArray(SHAPES_INDICES) { it }

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

data class Cell(
    val idx: Int,
    var color: Int = 0,
    var filled: Boolean = false,
) {
    val chargeClrAnim: Animation<Int> = Animation(
        0,
        EXPLOSION_CHARGE_ANIMATION_DURATION,
        delay = idx * 0.05f,
        lerp = { start, end, progress ->
            val r = lerp(Color.red(start).toFloat(), Color.red(end).toFloat(), progress).toInt()
            val g = lerp(Color.green(start).toFloat(), Color.green(end).toFloat(), progress).toInt()
            val b = lerp(Color.blue(start).toFloat(), Color.blue(end).toFloat(), progress).toInt()
            Color.rgb(r, g, b)
        },
    )
    val chargeSizeAnim: Animation<Float> = Animation(
        0f,
        EXPLOSION_CHARGE_ANIMATION_DURATION / EXPLOSION_CELL_PULSE_COUNT,
        delay = idx * 0.05f,
        lerp = ::lerp,
    )

    fun beginExplosion(elapsedTime: Float) {
        chargeClrAnim.current = color
        chargeClrAnim.begin(elapsedTime, Color.WHITE)

        chargeSizeAnim.current = 1f
        chargeSizeAnim.begin(elapsedTime, EXPLOSION_CELL_SCALE_MAX)
    }

    fun updateExplosion(elapsedTime: Float): Boolean {
        chargeClrAnim.update(elapsedTime)

        chargeSizeAnim.update(elapsedTime)
        if (!chargeSizeAnim.animating) {
            if (chargeSizeAnim.animationEndValue == EXPLOSION_CELL_SCALE_MAX) {
                chargeSizeAnim.begin(elapsedTime, 1f)
            } else {
                chargeSizeAnim.begin(elapsedTime, EXPLOSION_CELL_SCALE_MAX)
            }
        }

        return !chargeClrAnim.animating
    }
}

class ExplosionAnimation {
    var darken = Animation(0, EXPLOSION_CHARGE_ANIMATION_DURATION, lerp = ::lerp)

    fun begin(elapsedTime: Float) {
        darken.current = 0
        darken.begin(elapsedTime, 240)
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

    val shapesBag = ShapesBag()
    var currentShape = CurrentShape(0)
    var cellSize = 0f
    var cellPadding = 0f
    val cells = Array(CELLS_COUNT * CELLS_COUNT) { Cell(it) }

    val explosionAnim = ExplosionAnimation()

    var pendingRotations = 0
    var pendingPlacements = 0

    var score = 0
        set(value) {
            field = value
            onScoreChange(value)
        }


    // debug

    fun debugFillRow() {
        for (col in 0..<CELLS_COUNT - 1) {
            val cell = cells[col]
            cell.filled = true
            cell.color = colors[0]
        }
    }

    fun debugSpawnShape() {
        currentShape = CurrentShape(0)
    }

    //

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val wf = w.toFloat()
        val hf = h.toFloat()

        pgPadding = wf * PLAYGROUND_PADDING_FRACTION
        pgRect = Rect(0f, 0f, wf, hf)

        cellSize = (wf - pgPadding * 2) / CELLS_COUNT
        cellPadding = cellSize * CELL_PADDING_FRACTION
    }

    fun placeShapeAndDeleteFilled() {
        // place
        for (cellCoords in currentShape.cells()) {
            val idx = cellCoords.col + cellCoords.row * CELLS_COUNT
            val cell = cells[idx]
            cell.filled = true
            cell.color = currentShape.color
        }

        currentShape.shape = -1

        // delete filled
        val filledRows = BooleanArray(CELLS_COUNT) { false }
        val filledCols = BooleanArray(CELLS_COUNT) { false }

        for (row in 0..<CELLS_COUNT) {
            var filled = true
            for (col in 0..<CELLS_COUNT) {
                val idx = col + row * CELLS_COUNT
                if (!cells[idx].filled) {
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
                if (!cells[idx].filled) {
                    filled = false
                    break
                }
            }
            if (filled) {
                filledCols[col] = true
            }
        }

        var animating = false

        for (row in 0..<CELLS_COUNT) {
            if (filledRows[row]) {
                for (col in 0..<CELLS_COUNT) {
                    val idx = col + row * CELLS_COUNT
                    cells[idx].beginExplosion(elapsedTime)
                    animating = true
                }
            }
        }

        for (col in 0..<CELLS_COUNT) {
            if (filledCols[col]) {
                for (row in 0..<CELLS_COUNT) {
                    val idx = col + row * CELLS_COUNT
                    if (cells[idx].filled) {
                        cells[idx].beginExplosion(elapsedTime)
                        animating = true
                    }
                }
            }
        }

        if (animating) {
            explosionAnim.begin(elapsedTime)
            state = GameState.AnimatingCellsExplosion
        }
    }

    fun checkOverTheEdge(newCoords: Coords, cellsOffsets: Array<Coords>): Coords {
        var minCol = Int.MAX_VALUE
        var maxCol = Int.MIN_VALUE
        var minRow = Int.MAX_VALUE
        var maxRow = Int.MIN_VALUE

        for (cellOffsets in cellsOffsets) {
            val cellCoords = newCoords + cellOffsets
            minCol = kotlin.math.min(minCol, cellCoords.col)
            maxCol = kotlin.math.max(maxCol, cellCoords.col)
            minRow = kotlin.math.min(minRow, cellCoords.row)
            maxRow = kotlin.math.max(maxRow, cellCoords.row)
        }

        val offsets = Coords(0, 0)
        if (minCol < 0) offsets.col -= minCol
        if (maxCol >= CELLS_COUNT) offsets.col -= (maxCol - CELLS_COUNT + 1)
        if (minRow < 0) offsets.row -= minRow
        if (maxRow >= CELLS_COUNT) offsets.row -= (maxRow - CELLS_COUNT + 1)

        return offsets
    }

    fun update(dt: Float) {
        when (state) {
            GameState.Countdown -> {
                if (BuildConfig.DEBUG) {
                    state = GameState.Placing
                } else {
                    if (countdownText.update(elapsedTime, pgRect)) {
                        state = GameState.Placing
                    }
                }
            }

            GameState.Placing -> {
                if (currentShape.shape == -1) {
                    currentShape = CurrentShape(shapesBag.next())
                    currentShape.checkOverlap(cells)
                    pendingRotations = 0
                    pendingPlacements = 0
                }

                if (pendingRotations > 0) {
                    val newRotation = currentShape.rotation + 1
                    val newOffsets = shapes[(currentShape.shape * 4) + (newRotation % 4)]
                    val kicks = checkOverTheEdge(currentShape.coords, newOffsets)

                    currentShape.rotation = newRotation

                    if (kicks.col != 0 || kicks.row != 0) {
                        currentShape.coords += kicks
                        currentShape.beginMovementAnimation(elapsedTime)
                    }

                    currentShape.checkOverlap(cells)
                    currentShape.beginRotationAnimation(elapsedTime)

                    pendingRotations = 0
                }

                if (pendingPlacements > 0) {
                    if (!currentShape.overlapping) {
                        if (!currentShape.movementAnimation.animating) {
                            placeShapeAndDeleteFilled()
                        } else {
                            state = GameState.AnimatingCurrentShape
                        }
                    }
                    pendingPlacements = 0
                }

                if (!currentShape.dragging && touch.isDown) {
                    currentShape.dragging = true
                } else if (currentShape.dragging) {
                    if (!touch.isDown) {
                        currentShape.dragging = false
                        currentShape.coordsPrev = currentShape.coords.copy()
                    } else {
                        val diff = touch.position - touch.startPosition
                        val colsDiff = (diff / cellSize) * DRAG_SENSITIVITY
                        var newCoords = currentShape.coordsPrev + colsDiff.toCoords()

                        val cellsOffsets = shapes[currentShape.shapeIdx()]
                        val kicks = checkOverTheEdge(newCoords, cellsOffsets)

                        currentShape.coords = newCoords + kicks
                        currentShape.checkOverlap(cells)
                        currentShape.beginMovementAnimation(elapsedTime)
                    }
                }

                currentShape.updateMovementAnimation(elapsedTime)
                currentShape.updateRotationAnimation(elapsedTime)
            }

            GameState.AnimatingCurrentShape -> {
                currentShape.updateMovementAnimation(elapsedTime)

                if (!currentShape.movementAnimation.animating) {
                    placeShapeAndDeleteFilled()
                    state = GameState.Placing
                }
            }

            GameState.AnimatingCellsExplosion -> {
                explosionAnim.darken.update(elapsedTime)

                var allDone = true

                for (cellIdx in cells.indices) {
                    val cell = cells[cellIdx]

                    if (cell.chargeClrAnim.animating) {
                        val done = cell.updateExplosion(elapsedTime)
                        if (done) {
                            cell.filled = false
                            score += 10
                        } else {
                            allDone = false
                        }
                    }
                }

                if (allDone) {
                    state = GameState.Placing
                }
            }
        }
    }

    fun coordsToPos(coords: Vec2): Vec2 {
        val x = coords.x * cellSize + pgPadding
        val y = coords.y * cellSize + pgPadding
        return Vec2(x, y)
    }

    fun renderCell(canvas: Canvas, coords: Coords) {
        renderCell(canvas, coords.toVec2())
    }

    fun renderCell(canvas: Canvas, coords: Vec2) {
        val (cellx, celly) = coordsToPos(coords)
        val p = cellPadding
        val r = dp(RADIUS) - p
        canvas.drawRoundRect(
            cellx + p, celly + p, cellx + cellSize - p, celly + cellSize - p,
            r, r,
            paint,
        )
    }

    fun render(canvas: Canvas) {
        // playground
        paint.reset()
        paint.color = GCOLOR_BLACK
        canvas.drawRoundRect(pgRect.rectf, dp(RADIUS), dp(RADIUS), paint)

        run { // draw placed cells
            paint.reset()
            val scratchCoords = Coords(0, 0)
            for (idx in 0..<CELLS_COUNT * CELLS_COUNT) {
                if (!cells[idx].filled) {
                    continue
                }
                if (cells[idx].chargeClrAnim.animating) {
                    continue
                }

                scratchCoords.col = idx % CELLS_COUNT
                scratchCoords.row = idx / CELLS_COUNT

                paint.color = cells[idx].color
                renderCell(canvas, scratchCoords)
            }
        }

        when (state) {
            GameState.Countdown -> {
                countdownText.render(canvas)
            }

            GameState.Placing, GameState.AnimatingCurrentShape -> {
                assert(currentShape.shape < shapes.size)

                // current shape
                if (currentShape.shape != -1) {
                    paint.reset()

                    if (currentShape.rotationAnimation.animating) {
                        // render rotation
                        val pivot = coordsToPos(currentShape.movementAnimation.current + Vec2(2f, 2f))
                        canvas.save()
                        canvas.rotate(currentShape.rotationAnimation.current, pivot.x, pivot.y)

                        for (cellOffset in shapes[currentShape.shape * 4]) {
                            val cellCoords = cellOffset.toVec2() + currentShape.movementAnimation.current
                            paint.color = currentShape.color
                            if (currentShape.overlapping) {
                                paint.color = GCOLOR_OVERLAPPING
                            }
                            renderCell(canvas, cellCoords)
                        }

                        canvas.restore()
                    } else {
                        for (cellOffset in shapes[currentShape.shapeIdx()]) {
                            val cellCoords = cellOffset.toVec2() + currentShape.movementAnimation.current
                            paint.color = currentShape.color
                            if (currentShape.overlapping) {
                                paint.color = GCOLOR_OVERLAPPING
                            }
                            renderCell(canvas, cellCoords)
                        }
                    }
                }
            }

            GameState.AnimatingCellsExplosion -> {
                // explosion
                paint.reset()
                paint.color = Color.rgb(0, 0, 0)
                paint.alpha = explosionAnim.darken.current
                canvas.drawRoundRect(pgRect.rectf, dp(RADIUS), dp(RADIUS), paint)

                // exploding cells
                paint.reset()
                val scratchCoords = Coords(0, 0)
                for (idx in 0..<CELLS_COUNT * CELLS_COUNT) {
                    val cell = cells[idx]
                    if (!cell.chargeClrAnim.animating) {
                        continue
                    }

                    scratchCoords.col = idx % CELLS_COUNT
                    scratchCoords.row = idx / CELLS_COUNT

                    // cell center
                    var cellCenter = coordsToPos(scratchCoords.toVec2())
                    cellCenter += cellSize / 2

                    // scaled
                    val scaledCellSize = cellSize * cell.chargeSizeAnim.current
                    val cellx = cellCenter.x - scaledCellSize / 2
                    val celly = cellCenter.y - scaledCellSize / 2

                    paint.color = cell.chargeClrAnim.current
                    val p = cellPadding
                    val r = dp(RADIUS) - p
                    canvas.drawRoundRect(
                        cellx + p, celly + p, cellx + scaledCellSize - p, celly + scaledCellSize - p,
                        r, r,
                        paint,
                    )
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

    fun handleRotate() {
        pendingRotations += 1
    }

    fun handlePlace() {
        pendingPlacements += 1
    }
}
