package shapes.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.util.Log
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.core.content.res.ResourcesCompat
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.random.Random

const val CELLS_COUNT = 15
const val CELL_PADDING_FRACTION = 0.075f
const val PLAYGROUND_PADDING_FRACTION = 0.02f
const val DRAG_SENSITIVITY = 1.75f
const val SHAPE_MOVEMENT_ANIMATION_DURATION = 0.065f
const val EXPLOSION_CHARGE_ANIMATION_DURATION = 0.22f
const val EXPLOSION_CELL_PULSE_COUNT = 4
const val EXPLOSION_CELL_SCALE_MAX = 1.35f
const val EXPLOSION_CELL_SCALE_MIN = 0.9f
const val EXPLOSION_PARTICLE_LIFESPAN = 0.6f
const val EXPLOSION_DELAY = 0.01f
const val PARTICLE_COUNT_PER_CELL = 10

val GCOLOR_RED = Color.rgb(197, 40, 61)
val GCOLOR_OVERLAPPING = Color.argb(200, Color.red(GCOLOR_RED), Color.green(GCOLOR_RED), Color.blue(GCOLOR_RED))
val GCOLOR_YELLOW = Color.rgb(226, 239, 112)
val GCOLOR_BLACK = Color.rgb(39, 43, 43)

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

fun shapeRotationIndex(shapeIdx: Int, rotation: Int): Int {
    logd("shapeRotationIndex: shapeIdx=$shapeIdx, rotation=$rotation")
    return (shapeIdx * 4) + (rotation % 4)
}

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

    fun cells(): Iterable<Coords> {
        return Iterable {
            val shapeIdx = shapeRotationIndex(shape, rotation)
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
            assert(idx in cells.indices) { "idx=$idx, cells.size=${cells.size}" }
            if (cells[idx].filled) {
                overlapping = true
                break
            }
        }
    }
}

class ShapesBag {
    var current = -1
    val bagSize = shapes.size / 4
    val indexes = IntArray(bagSize * 2)

    init {
        for (bag in 0..<2) {
            for (i in 0..<bagSize) {
                indexes[bag * bagSize + i] = i
            }
        }
    }
}

fun IntArray.shuffleRange(from: Int, to: Int, random: Random = Random.Default) {
    require(from in indices) { "from index out of bounds; from=$from, size=$size" }
    require(to in from..size) { "to index out of bounds; to=$to, size=$size" }

    for (i in to - 1 downTo from + 1) {
        val j = random.nextInt(from, i + 1)
        val temp = this[i]
        this[i] = this[j]
        this[j] = temp
    }
}

fun ShapesBag.peek(): Int {
    assert(current != -1) { "peek called before next" }
    if (current == bagSize * 2) {
        return indexes[0]
    }
    return indexes[current]
}

fun ShapesBag.next(): Int {
    if (current == -1) {
        // init
        indexes.shuffleRange(0, bagSize)
        indexes.shuffleRange(bagSize, bagSize * 2)
        current = 0
    }

    if (current == bagSize) {
        // shuffle first bag when we enter the second bag
        indexes.shuffleRange(0, bagSize)
    }
    if (current == bagSize * 2) {
        // shuffle second bag when we enter the first bag
        indexes.shuffleRange(bagSize, bagSize * 2)
        current = 0
    }

    val c = indexes[current]
    current += 1
    return c
}


data class Particle(
    val ctx: GameContext,
) {
    // config
    val startPosition = Vec2(0f, 0f)
    var distance = 0f

    // computed
    var alpha = 255
    var currentPosition = Vec2(0f, 0f)
    val endPosition = Vec2(0f, 0f)
    var spawnTime = 0f
}

fun Particle.init(cellIdx: Int) {
    // center of the cell
    val col = cellIdx % CELLS_COUNT
    val row = cellIdx / CELLS_COUNT

    val x = ctx.pgPadding + col * ctx.cellSize
    val y = ctx.pgPadding + row * ctx.cellSize

    startPosition.x = x + ctx.cellSize / 2f
    startPosition.y = y + ctx.cellSize / 2f

    // TODO: make this bigger based on multi fill
    val maxDistance = ctx.cellSize * 7
    distance = (0.8f + Random.nextFloat() * 0.2f) * maxDistance
}

fun Particle.spawn() {
    spawnTime = ctx.elapsedTime

    alpha = 255
    currentPosition = startPosition.copy()

    val angle = Random.nextFloat() * (2f * PI.toFloat())
    endPosition.x = startPosition.x + distance * kotlin.math.cos(angle)
    endPosition.y = startPosition.y + distance * kotlin.math.sin(angle)
}

fun Particle.dead(): Boolean {
    return ctx.elapsedTime - spawnTime > EXPLOSION_PARTICLE_LIFESPAN
}

fun Particle.update(): Boolean {
    if (dead()) {
        return true
    }

    val age = ctx.elapsedTime - spawnTime
    val fraction = kotlin.math.min(1f, age / EXPLOSION_PARTICLE_LIFESPAN)

    if (fraction > 0.5f) {
        val alphaFraction = (fraction - 0.5f) / 0.5f
        alpha = ((1 - alphaFraction) * 255).toInt()
    }

    val p = 1 - fraction
    currentPosition = lerp(startPosition, endPosition, 1 - p * p)
    return false
}

data class Cell(
    val ctx: GameContext,
    val idx: Int,
    var color: Int = 0,
    var filled: Boolean = false,
) {
    var filledAt = 0f

    val chargeAlphaAnim = Animation(0, EXPLOSION_CHARGE_ANIMATION_DURATION, lerp = ::lerp)

    val pulsePhaseDuration = EXPLOSION_CHARGE_ANIMATION_DURATION / EXPLOSION_CELL_PULSE_COUNT / 2
    val pulseGrowTo = EXPLOSION_CELL_SCALE_MAX
    val pulseShrinkTo = EXPLOSION_CELL_SCALE_MIN
    val pulseGrowAnim = Animation(0f, pulsePhaseDuration, lerp = ::lerp)
    val pulseShrinkAnim = Animation(0f, pulsePhaseDuration, lerp = ::lerp)

    var particlesAnimating = false
    val particles = Array(PARTICLE_COUNT_PER_CELL) { Particle(ctx) }
    var explosionAnimating = false
}

fun Cell.beginExplosion(elapsedTime: Float, delay: Float) {
    chargeAlphaAnim.delay = delay
    chargeAlphaAnim.current = 0
    chargeAlphaAnim.begin(elapsedTime, 255)

    pulseGrowAnim.delay = delay
    pulseGrowAnim.current = 1f
    pulseGrowAnim.begin(elapsedTime, pulseGrowTo)

    pulseShrinkAnim.delay = delay + pulsePhaseDuration
    pulseShrinkAnim.current = pulseGrowTo
    pulseShrinkAnim.begin(elapsedTime, pulseShrinkTo)

    explosionAnimating = true
}

fun Cell.currentScale(): Float = if (pulseGrowAnim.animating) {
    pulseGrowAnim.current
} else {
    pulseShrinkAnim.current
}

fun Cell.updateExplosion(elapsedTime: Float): Boolean {
    chargeAlphaAnim.update(elapsedTime)

    if (chargeAlphaAnim.animating) {
        pulseGrowAnim.update(elapsedTime)
        pulseShrinkAnim.update(elapsedTime)

        if (!pulseShrinkAnim.animating) {
            pulseGrowAnim.delay = 0f
            pulseGrowAnim.current = pulseShrinkAnim.current
            pulseGrowAnim.begin(elapsedTime, pulseGrowTo)
            pulseShrinkAnim.delay = pulsePhaseDuration
            pulseShrinkAnim.current = pulseGrowTo
            pulseShrinkAnim.begin(elapsedTime, pulseShrinkTo)
        }
    }

    if (!chargeAlphaAnim.animating) {
        if (!particlesAnimating) {
            particlesAnimating = true
            for (particle in particles) {
                particle.spawn()
            }
        } else {
            particlesAnimating = true
            for (particle in particles) {
                val done = particle.update()
                if (done) {
                    particlesAnimating = false
                }
            }
        }
        explosionAnimating = particlesAnimating
    }

    return !explosionAnimating
}

class ScreenShake(val ctx: GameContext) {
    val delay = EXPLOSION_CHARGE_ANIMATION_DURATION
    var startTime = 0f
    var duration = 0f
    var magnitude = 0f
    val offset = Vec2(0f, 0f)
    var animating = false

    fun begin(magnitude: Float, duration: Float) {
        this.magnitude = magnitude
        this.duration = duration
        startTime = ctx.elapsedTime
        animating = true
    }

    fun update() {
        val ageWithDelay = ctx.elapsedTime - startTime
        val age = ageWithDelay - delay

        if (age > duration) {
            animating = false
            return
        }

        if (ageWithDelay < delay) {
            return
        }

        val strength = magnitude * (1f - age / duration)
        offset.x = kotlin.math.sin(age * 73f) * strength
        offset.y = kotlin.math.sin(age * 109f + 1.2f) * strength
    }
}

class GameContext {
    var dt = 0f
    var elapsedTime = 0f

    var pgRect = Rect()
    var pgPadding = 0f
    var cellSize = 0f
    var cellPadding = 0f

    val cells = Array(CELLS_COUNT * CELLS_COUNT) { Cell(this, it) }
    var state: GameState = GameState.Countdown
    val shapesBag = ShapesBag()
    var currentShape = CurrentShape()

    val screenShake = ScreenShake(this)
}

fun coordsToIdx(col: Int, row: Int): Int {
    return col + row * CELLS_COUNT
}

fun placeShapeAndBeginExplosion(ctx: GameContext) {
    // place
    for (cellCoords in ctx.currentShape.cells()) {
        val idx = cellCoords.col + cellCoords.row * CELLS_COUNT
        val cell = ctx.cells[idx]
        cell.filled = true
        cell.filledAt = ctx.elapsedTime
        cell.color = ctx.currentShape.color
    }

    ctx.currentShape.shape = -1

    // begin explosion

    val filledRows = IntArray(CELLS_COUNT) { -1 }
    val filledCols = IntArray(CELLS_COUNT) { -1 }

    data class Result(
        val fixedCoord: Int,
        var filled: Boolean = true,
        var start: Int = -1,
        var prevFillTime: Float = 0f,
        val coordsToIdx: (fixedCoord: Int, movingCoord: Int) -> Int,
    )

    fun Result.process(movingCoord: Int) {
        val idx = coordsToIdx(fixedCoord, movingCoord)
        val cell = ctx.cells[idx]
        if (!cell.filled) {
            filled = false
        }
        if (prevFillTime < cell.filledAt) {
            prevFillTime = cell.filledAt
            start = movingCoord
        }
    }

    fun Result.beingExplosion(elapsedTime: Float): Float {
        // first
        run {
            val idx = coordsToIdx(fixedCoord, start)
            val cell = ctx.cells[idx]
            cell.beginExplosion(elapsedTime, 0f)
        }

        // delays
        var offset = 1
        while (true) {
            val preCoord = start - offset
            val postCoord = start + offset

            if (preCoord < 0 && postCoord >= CELLS_COUNT) {
                break
            }

            val delay = offset * EXPLOSION_DELAY

            if (preCoord >= 0) {
                val idx = coordsToIdx(fixedCoord, preCoord)
                val left = ctx.cells[idx]
                left.beginExplosion(elapsedTime, delay)
            }

            if (postCoord < CELLS_COUNT) {
                val right = ctx.cells[coordsToIdx(fixedCoord, postCoord)]
                right.beginExplosion(elapsedTime, delay)
            }

            offset += 1
        }

        val maxDelay = (offset - 1) * EXPLOSION_DELAY
        return maxDelay
    }

    var animating = false
    var maxDelay = 0f

    for (i in 0..<CELLS_COUNT) {
        // row -> i = row, j = col
        val rowResult = Result(i) { row, col ->
            row * CELLS_COUNT + col
        }
        // col -> i = col, j = row
        val colResult = Result(i) { col, row ->
            row * CELLS_COUNT + col
        }

        for (j in 0..<CELLS_COUNT) {
            if (!rowResult.filled && !colResult.filled) {
                break
            }

            if (rowResult.filled) rowResult.process(j)
            if (colResult.filled) colResult.process(j)
        }


        if (rowResult.filled) {
            // begin
            animating = true
            maxDelay = kotlin.math.max(maxDelay, rowResult.beingExplosion(ctx.elapsedTime))
        }

        if (colResult.filled) {
            // begin
            animating = true
            maxDelay = kotlin.math.max(maxDelay, colResult.beingExplosion(ctx.elapsedTime))
        }
    }

    if (animating) {
        val screenShakeDuration = maxDelay + EXPLOSION_PARTICLE_LIFESPAN
        ctx.screenShake.begin(10f, screenShakeDuration)

        ctx.state = GameState.AnimatingCellsExplosion
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

class GameView(
    context: Context,
    val onScoreChange: (Int) -> Unit,
    val onNextShape: (Int) -> Unit,
) : View(context) {
    var running = false
    var lastFrameTime = 0L
    val paint = Paint()

    var touch = Touch()
    var pendingRotations = false
    var pendingPlacements = false
    var countdownText = CountdownText(paint, context)

    val ctx = GameContext()

    var score = 0
        set(value) {
            field = value
            onScoreChange(value)
        }

    // debug

    fun debugFillRow() {
        for (col in 0..<CELLS_COUNT - 1) {
            val cell = ctx.cells[col + CELLS_COUNT]
            cell.filled = true
            cell.color = colors[0]
        }
    }

    fun debugSpawnShape() {
        ctx.currentShape = CurrentShape(0)
    }

    //

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val wf = w.toFloat()
        val hf = h.toFloat()

        ctx.pgPadding = wf * PLAYGROUND_PADDING_FRACTION
        ctx.pgRect = Rect(0f, 0f, wf, hf)

        ctx.cellSize = (wf - ctx.pgPadding * 2) / CELLS_COUNT
        ctx.cellPadding = ctx.cellSize * CELL_PADDING_FRACTION

        for (cell in ctx.cells) {
            for (particle in cell.particles) {
                particle.init(cell.idx)
            }
        }
    }

    fun update() {
        when (ctx.state) {
            GameState.Countdown -> {
                if (BuildConfig.DEBUG) {
                    ctx.state = GameState.Placing
                } else {
                    if (countdownText.update(ctx.elapsedTime, ctx.pgRect)) {
                        ctx.state = GameState.Placing
                    }
                }
            }

            GameState.Placing -> {
                if (ctx.currentShape.shape == -1) {
                    val shapeIdx = ctx.shapesBag.next()
                    val nextShapeIdx = ctx.shapesBag.peek()

                    ctx.currentShape = CurrentShape(shapeIdx)
                    onNextShape(nextShapeIdx)

                    ctx.currentShape.checkOverlap(ctx.cells)

                    pendingRotations = false
                    pendingPlacements = false
                }

                if (pendingRotations) {
                    val newRotation = ctx.currentShape.rotation + 1
                    val newShapeIdx = shapeRotationIndex(ctx.currentShape.shape, newRotation)
                    val newOffsets = shapes[newShapeIdx]
                    val kicks = checkOverTheEdge(ctx.currentShape.coords, newOffsets)

                    ctx.currentShape.rotation = newRotation

                    if (kicks.col != 0 || kicks.row != 0) {
                        ctx.currentShape.coords += kicks
                        ctx.currentShape.beginMovementAnimation(ctx.elapsedTime)
                    }

                    ctx.currentShape.checkOverlap(ctx.cells)
                    ctx.currentShape.beginRotationAnimation(ctx.elapsedTime)

                    pendingRotations = false
                }

                if (pendingPlacements) {
                    if (!ctx.currentShape.overlapping) {
                        if (!ctx.currentShape.movementAnimation.animating) {
                            placeShapeAndBeginExplosion(ctx)
                        } else {
                            ctx.state = GameState.AnimatingCurrentShape
                        }
                    }
                    pendingPlacements = false
                }

                if (!ctx.currentShape.dragging && touch.isDown) {
                    ctx.currentShape.dragging = true
                } else if (ctx.currentShape.dragging) {
                    if (!touch.isDown) {
                        ctx.currentShape.dragging = false
                        ctx.currentShape.coordsPrev = ctx.currentShape.coords.copy()
                    } else {
                        val diff = touch.position - touch.startPosition
                        val colsDiff = (diff / ctx.cellSize) * DRAG_SENSITIVITY
                        var newCoords = ctx.currentShape.coordsPrev + colsDiff.toCoords()

                        val shapeIdx = shapeRotationIndex(ctx.currentShape.shape, ctx.currentShape.rotation)
                        val cellsOffsets = shapes[shapeIdx]
                        val kicks = checkOverTheEdge(newCoords, cellsOffsets)

                        ctx.currentShape.coords = newCoords + kicks
                        ctx.currentShape.checkOverlap(ctx.cells)
                        ctx.currentShape.beginMovementAnimation(ctx.elapsedTime)
                    }
                }

                ctx.currentShape.updateMovementAnimation(ctx.elapsedTime)
                ctx.currentShape.updateRotationAnimation(ctx.elapsedTime)
            }

            GameState.AnimatingCurrentShape -> {
                ctx.currentShape.updateMovementAnimation(ctx.elapsedTime)

                if (!ctx.currentShape.movementAnimation.animating) {
                    placeShapeAndBeginExplosion(ctx)
                    ctx.state = GameState.Placing
                }
            }

            GameState.AnimatingCellsExplosion -> {
                var allDone = true

                for (cellIdx in ctx.cells.indices) {
                    val cell = ctx.cells[cellIdx]

                    if (cell.explosionAnimating) {
                        val done = cell.updateExplosion(ctx.elapsedTime)
                        if (done) {
                            cell.filled = false
                            score += 10
                        } else {
                            allDone = false
                        }
                    }
                }

                if (ctx.screenShake.animating) {
                    ctx.screenShake.update()
                }

                if (allDone) {
                    ctx.state = GameState.Placing
                }
            }
        }
    }

    fun coordsToPos(coords: Vec2): Vec2 {
        val x = coords.x * ctx.cellSize + ctx.pgPadding
        val y = coords.y * ctx.cellSize + ctx.pgPadding
        return Vec2(x, y)
    }

    fun renderCell(canvas: Canvas, coords: Coords) {
        renderCell(canvas, coords.toVec2())
    }

    fun renderCell(canvas: Canvas, coords: Vec2) {
        val (cellx, celly) = coordsToPos(coords)
        val p = ctx.cellPadding
        val r = dp(RADIUS) - p
        canvas.drawRoundRect(
            cellx + p, celly + p, cellx + ctx.cellSize - p, celly + ctx.cellSize - p,
            r, r,
            paint,
        )
    }

    fun render(canvas: Canvas) {
        if (ctx.screenShake.animating) {
            canvas.save()
            val (offsetx, offsety) = ctx.screenShake.offset
            canvas.translate(offsetx, offsety)
        }

        // playground
        paint.reset()
        paint.color = GCOLOR_BLACK
        canvas.drawRoundRect(ctx.pgRect.rectf, dp(RADIUS), dp(RADIUS), paint)

        run { // draw placed cells
            paint.reset()
            val scratchCoords = Coords(0, 0)
            for (idx in 0..<CELLS_COUNT * CELLS_COUNT) {
                val cell = ctx.cells[idx]

                if (!cell.filled) {
                    continue
                }
                if (cell.particlesAnimating) {
                    continue
                }

                scratchCoords.col = idx % CELLS_COUNT
                scratchCoords.row = idx / CELLS_COUNT

                paint.color = cell.color
                renderCell(canvas, scratchCoords)
            }
        }

        when (ctx.state) {
            GameState.Countdown -> {
                countdownText.render(canvas)
            }

            GameState.Placing, GameState.AnimatingCurrentShape -> {
                assert(ctx.currentShape.shape < shapes.size)

                // current shape
                if (ctx.currentShape.shape != -1) {
                    paint.reset()

                    if (ctx.currentShape.rotationAnimation.animating) {
                        // render rotation
                        val pivot = coordsToPos(ctx.currentShape.movementAnimation.current + Vec2(2f, 2f))
                        canvas.save()
                        canvas.rotate(ctx.currentShape.rotationAnimation.current, pivot.x, pivot.y)

                        val shapeIdx = shapeRotationIndex(ctx.currentShape.shape, 0)

                        for (cellOffset in shapes[shapeIdx]) {
                            val cellCoords = cellOffset.toVec2() + ctx.currentShape.movementAnimation.current
                            paint.color = ctx.currentShape.color
                            if (ctx.currentShape.overlapping) {
                                paint.color = GCOLOR_OVERLAPPING
                            }
                            renderCell(canvas, cellCoords)
                        }

                        canvas.restore()
                    } else {
                        val shapeIdx = shapeRotationIndex(ctx.currentShape.shape, ctx.currentShape.rotation)
                        for (cellOffset in shapes[shapeIdx]) {
                            val cellCoords = cellOffset.toVec2() + ctx.currentShape.movementAnimation.current
                            paint.color = ctx.currentShape.color
                            if (ctx.currentShape.overlapping) {
                                paint.color = GCOLOR_OVERLAPPING
                            }
                            renderCell(canvas, cellCoords)
                        }
                    }
                }
            }

            GameState.AnimatingCellsExplosion -> {
                // exploding cells
                paint.reset()
                paint.color = Color.WHITE

                val scratchCoords = Coords(0, 0)
                for (idx in 0..<CELLS_COUNT * CELLS_COUNT) {
                    val cell = ctx.cells[idx]
                    if (cell.chargeAlphaAnim.animating) {
                        scratchCoords.col = idx % CELLS_COUNT
                        scratchCoords.row = idx / CELLS_COUNT

                        // cell center
                        var cellCenter = coordsToPos(scratchCoords.toVec2())
                        cellCenter += ctx.cellSize / 2

                        // scaled
                        val scaledCellSize = ctx.cellSize * cell.currentScale()
                        val cellx = cellCenter.x - scaledCellSize / 2
                        val celly = cellCenter.y - scaledCellSize / 2

                        paint.alpha = cell.chargeAlphaAnim.current
                        val p = ctx.cellPadding
                        val r = dp(RADIUS) - p
                        canvas.drawRoundRect(
                            cellx + p, celly + p, cellx + scaledCellSize - p, celly + scaledCellSize - p,
                            r, r,
                            paint,
                        )
                    }

                    // particles
                    if (cell.particlesAnimating) {
                        paint.reset()
                        paint.color = Color.WHITE
                        for (particle in cell.particles) {
                            if (!particle.dead()) {
                                val (cx, cy) = particle.currentPosition
                                paint.alpha = particle.alpha
                                canvas.drawRect(cx, cy, cx + dp(3f), cy + dp(3f), paint)
                            }
                        }
                    }
                }
            }
        }

        if (ctx.screenShake.animating) {
            canvas.restore()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!running) {
            println("start")
            return
        }

        val currentTime = System.nanoTime()
        ctx.dt = (currentTime - lastFrameTime).toFloat() / 1e9f
        lastFrameTime = currentTime
        ctx.elapsedTime += ctx.dt

        update()
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
        pendingRotations = true
    }

    fun handlePlace() {
        pendingPlacements = true
    }
}

class NextShapeView(ctx: Context) : View(ctx) {
    val radiusFraction = 0.15f
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
        shape = shapes[shapeRotationIndex(shapeIdx, 0)]
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
                val radius = cellSize * radiusFraction

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
