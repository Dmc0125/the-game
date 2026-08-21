package shapes.game

import kotlin.math.PI
import kotlin.random.Random

const val FONT_MANROPE = "manrope"
const val FONT_DMMONO = "dmmono"

const val RADIUS = 8f
const val CELLS_COUNT = 15
const val CELL_PADDING_FRACTION = 0.075f
const val CELL_RADIUS_FRACTION = 0.25f
const val PLAYGROUND_PADDING_FRACTION = 0.02f
const val DRAG_SENSITIVITY = 1.75f
const val SHAPE_MOVEMENT_ANIMATION_DURATION = 0.065f
const val EXPLOSION_CHARGE_ANIMATION_DURATION = 0.22f
const val EXPLOSION_CELL_PULSE_COUNT = 4
const val EXPLOSION_CELL_SCALE_MAX = 1.35f
const val EXPLOSION_CELL_SCALE_MIN = 0.9f
const val EXPLOSION_PARTICLE_LIFESPAN = 0.6f
const val EXPLOSION_DELAY = 0.02f
const val PARTICLE_COUNT_PER_CELL = 10

object Color {
    val WHITE = Color.rgb(255, 255, 255)
    val RED = Color.rgb(197, 40, 61)
    val OVERLAPPING = Color.addAlpha(200, RED)
    val YELLOW = Color.rgb(226, 239, 112)
    val BLACK = Color.rgb(39, 43, 43)

    val BLUE = Color.rgb(112, 228, 239)
    val PURPLE = Color.rgb(203, 66, 159)
    val ORANGE = Color.rgb(217, 93, 57)
    val PINK = Color.rgb(227, 86, 124)

    fun rgb(r: Int, g: Int, b: Int): Int {
        return 0xff000000.toInt() or ((r and 0xff) shl 16) or
                ((g and 0xff) shl 8) or
                (b and 0xff)
    }

    fun argb(a: Int, r: Int, g: Int, b: Int): Int {
        return ((a and 0xff) shl 24) or
                ((r and 0xff) shl 16) or
                ((g and 0xff) shl 8) or
                (b and 0xff)
    }

    fun addAlpha(a: Int, rgb: Int): Int {
        return ((a and 0xff) shl 24) or (rgb and 0xffffff)
    }
}

val colors: Array<Int> = arrayOf(
    Color.BLUE,
    Color.PURPLE,
    Color.ORANGE,
    Color.PINK,
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

val shapesMap: Array<Array<Coords>> = arrayOf(
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

enum class GameState {
    Countdown,
    Placing,
    AnimatingCurrentShape,
    AnimatingCellsExplosion,
}

class CountdownText(val ctx: GameContext) {
    val textSizeStart = 30f * ctx.scaledDensity
    val textSizeEnd = 60f * ctx.scaledDensity
    val textSizeDiff = textSizeEnd - textSizeStart

    var text: String = ""
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

        val textWidth = ctx.renderer.measureText(text, textSize, FontWeight.Medium, FONT_DMMONO)
        textX = pgRect.x + (pgRect.width - textWidth) / 2
        textY = pgRect.y + textSize + (pgRect.height - textSize) / 2

        return false
    }

    fun render() {
        val color = Color.argb((opacity * 255).toInt(), 255, 255, 255)
        ctx.renderer.drawText(text, textX, textY, color, textSize, FontWeight.Medium, FONT_DMMONO)
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
            val cells = shapesMap[shapeIdx]
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
    val bagSize = shapesMap.size / 4
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
    val maxDistance = ctx.cellSize * 4
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

enum class CellAnimationState {
    None,
    Charging,
    Exploding,
}

data class Cell(
    val ctx: GameContext,
    val idx: Int,
    var color: Int = 0,
    var filled: Boolean = false,
) {
    var filledAt = 0f

    var animationState = CellAnimationState.None
    val chargeAlphaAnim = Animation(0, EXPLOSION_CHARGE_ANIMATION_DURATION, lerp = ::lerp)

    val pulsePhaseDuration = EXPLOSION_CHARGE_ANIMATION_DURATION / EXPLOSION_CELL_PULSE_COUNT / 2
    val pulseGrowTo = EXPLOSION_CELL_SCALE_MAX
    val pulseShrinkTo = EXPLOSION_CELL_SCALE_MIN
    val pulseGrowAnim = Animation(0f, pulsePhaseDuration, lerp = ::lerp)
    val pulseShrinkAnim = Animation(0f, pulsePhaseDuration, lerp = ::lerp)

    var particlesAnimating = false
    val particles = Array(PARTICLE_COUNT_PER_CELL) { Particle(ctx) }
}

fun Cell.beginExplosion(elapsedTime: Float, delay: Float) {
    animationState = CellAnimationState.Charging

    chargeAlphaAnim.delay = delay
    chargeAlphaAnim.current = 0
    chargeAlphaAnim.begin(elapsedTime, 255)

    pulseGrowAnim.delay = delay
    pulseGrowAnim.current = 1f
    pulseGrowAnim.begin(elapsedTime, pulseGrowTo)

    pulseShrinkAnim.delay = delay + pulsePhaseDuration
    pulseShrinkAnim.current = pulseGrowTo
    pulseShrinkAnim.begin(elapsedTime, pulseShrinkTo)
}

fun Cell.currentScale(): Float = if (pulseGrowAnim.animating) {
    pulseGrowAnim.current
} else {
    pulseShrinkAnim.current
}

fun Cell.updateExplosion(elapsedTime: Float) {
    assert(animationState != CellAnimationState.None) { "Explosion animation is not active" }

    when (animationState) {
        CellAnimationState.Charging -> {
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
            } else {
                animationState = CellAnimationState.Exploding
            }
        }

        CellAnimationState.Exploding -> {
            if (!particlesAnimating) {
                particlesAnimating = true
                for (particle in particles) {
                    particle.spawn()
                }
            } else {
                var allDone = true
                for (particle in particles) {
                    val done = particle.update()
                    if (!done) {
                        allDone = false
                    }
                }

                if (allDone) {
                    animationState = CellAnimationState.None
                    particlesAnimating = false
                }
            }
        }

        CellAnimationState.None -> {}
    }
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

class GameContext(
    val pixelDensity: Float,
    val scaledDensity: Float,
    val onScoreChange: ((Int) -> Unit)? = null,
    val onNextShape: ((Int) -> Unit)? = null,
) {
    var dt = 0f
    var elapsedTime = 0f
    var renderer: Renderer = Renderer.Default

    var pendingRotation = false
    var pendingPlacement = false

    var pgRect = Rect()
    var pgPadding = 0f
    var cellSize = 0f
    var cellPadding = 0f

    val cells = Array(CELLS_COUNT * CELLS_COUNT) { Cell(this, it) }
    var state: GameState = GameState.Countdown
    val shapesBag = ShapesBag()
    var currentShape = CurrentShape()
    val countdownText = CountdownText(this)
    val screenShake = ScreenShake(this)

    var score = 0
}

fun GameContext.addScore(amount: Int) {
    score += amount
    onScoreChange?.invoke(score)
}

fun GameContext.onSizeChanged(wf: Float, hf: Float) {
    pgPadding = wf * PLAYGROUND_PADDING_FRACTION
    pgRect = Rect(0f, 0f, wf, hf)

    cellSize = (wf - pgPadding * 2) / CELLS_COUNT
    cellPadding = cellSize * CELL_PADDING_FRACTION

    for (cell in cells) {
        for (particle in cell.particles) {
            particle.init(cell.idx)
        }
    }
}

fun coordsToIdx(col: Int, row: Int): Int {
    return col + row * CELLS_COUNT
}

fun placeShapeAndBeginExplosion(ctx: GameContext) {
    // place
    for (cellCoords in ctx.currentShape.cells()) {
        val idx = coordsToIdx(cellCoords.col, cellCoords.row)
        val cell = ctx.cells[idx]

        cell.filled = true
        cell.filledAt = ctx.elapsedTime
        cell.color = ctx.currentShape.color

        ctx.addScore(1)
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

    fun Result.beginExplosion(elapsedTime: Float): Float {
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
            maxDelay = kotlin.math.max(maxDelay, rowResult.beginExplosion(ctx.elapsedTime))
        }

        if (colResult.filled) {
            // begin
            animating = true
            maxDelay = kotlin.math.max(maxDelay, colResult.beginExplosion(ctx.elapsedTime))
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

fun GameContext.update(touch: Touch) {
    when (state) {
        GameState.Countdown -> {
            // if (shapes.BuildConfig.DEBUG) {
            //     state = GameState.Placing
            // } else {
            if (countdownText.update(elapsedTime, pgRect)) {
                state = GameState.Placing
            }
            // }
        }

        GameState.Placing -> {
            if (currentShape.shape == -1) { // spawn
                val shapeIdx = shapesBag.next()
                val nextShapeIdx = shapesBag.peek()

                currentShape = CurrentShape(shapeIdx)
                onNextShape?.invoke(nextShapeIdx)

                currentShape.checkOverlap(cells)

                pendingRotation = false
                pendingPlacement = false
            }

            if (pendingRotation) {
                val newRotation = currentShape.rotation + 1
                val newShapeIdx = shapeRotationIndex(currentShape.shape, newRotation)
                val newOffsets = shapesMap[newShapeIdx]
                val kicks = checkOverTheEdge(currentShape.coords, newOffsets)

                currentShape.rotation = newRotation

                if (kicks.col != 0 || kicks.row != 0) {
                    currentShape.coords += kicks
                    currentShape.beginMovementAnimation(elapsedTime)
                }

                currentShape.checkOverlap(cells)
                currentShape.beginRotationAnimation(elapsedTime)

                pendingRotation = false
            }

            if (pendingPlacement) {
                if (!currentShape.overlapping) {
                    if (!currentShape.movementAnimation.animating) {
                        placeShapeAndBeginExplosion(this)
                    } else {
                        state = GameState.AnimatingCurrentShape
                    }
                }
                pendingPlacement = false
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

                    val shapeIdx = shapeRotationIndex(currentShape.shape, currentShape.rotation)
                    val cellsOffsets = shapesMap[shapeIdx]
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
                placeShapeAndBeginExplosion(this)
                state = GameState.Placing
            }
        }

        GameState.AnimatingCellsExplosion -> {
            var allDone = true

            for (cellIdx in cells.indices) {
                val cell = cells[cellIdx]
                if (!cell.filled) {
                    continue
                }

                when (cell.animationState) {
                    CellAnimationState.Charging -> {
                        cell.updateExplosion(elapsedTime)
                        if (cell.animationState == CellAnimationState.Exploding) {
                            addScore(10)
                        }
                        allDone = false
                    }

                    CellAnimationState.Exploding -> {
                        cell.updateExplosion(elapsedTime)
                        if (cell.animationState == CellAnimationState.None) {
                            cell.filled = false
                        } else {
                            allDone = false
                        }
                    }

                    else -> Unit
                }
            }

            if (screenShake.animating) {
                screenShake.update()
                if (allDone) {
                    allDone = !screenShake.animating
                }
            }

            if (allDone) {
                state = GameState.Placing
            }
        }
    }
}

fun coordsToPos(ctx: GameContext, coords: Vec2): Vec2 {
    val x = coords.x * ctx.cellSize + ctx.pgPadding
    val y = coords.y * ctx.cellSize + ctx.pgPadding
    return Vec2(x, y)
}

fun renderCell(ctx: GameContext, renderer: Renderer, coords: Coords, color: Int) {
    renderCell(ctx, renderer, coords.toVec2(), color)
}

fun renderCell(ctx: GameContext, renderer: Renderer, coords: Vec2, color: Int) {
    val (cellx, celly) = coordsToPos(ctx, coords)
    val p = ctx.cellPadding
    val r = ctx.cellSize * CELL_RADIUS_FRACTION
    renderer.drawRoundRect(
        cellx + p, celly + p, ctx.cellSize - p, ctx.cellSize - p,
        r,
        color,
    )
}

fun GameContext.render() {
    if (screenShake.animating) {
        renderer.save()
        val (offsetx, offsety) = screenShake.offset
        renderer.translate(offsetx, offsety)
    }

    // playground
    renderer.drawRoundRect(pgRect, RADIUS * pixelDensity, Color.BLACK)

    run { // draw placed cells
        val scratchCoords = Coords(0, 0)
        for (idx in 0..<CELLS_COUNT * CELLS_COUNT) {
            val cell = cells[idx]

            if (!cell.filled) {
                continue
            }
            if (cell.particlesAnimating) {
                continue
            }

            scratchCoords.col = idx % CELLS_COUNT
            scratchCoords.row = idx / CELLS_COUNT

            renderCell(this, renderer, scratchCoords, cell.color)
        }
    }

    when (state) {
        GameState.Countdown -> {
            countdownText.render()
        }

        GameState.Placing, GameState.AnimatingCurrentShape -> {
            assert(currentShape.shape < shapesMap.size)

            // current shape
            if (currentShape.shape != -1) {
                if (currentShape.rotationAnimation.animating) {
                    // render rotation
                    val pivot = coordsToPos(this, currentShape.movementAnimation.current + Vec2(2f, 2f))
                    renderer.save()
                    renderer.rotate(
                        currentShape.rotationAnimation.current, pivot.x, pivot.y
                    )

                    val shapeIdx = shapeRotationIndex(currentShape.shape, 0)

                    for (cellOffset in shapesMap[shapeIdx]) {
                        val cellCoords = cellOffset.toVec2() + currentShape.movementAnimation.current
                        val color = if (currentShape.overlapping) {
                            Color.OVERLAPPING
                        } else {
                            currentShape.color
                        }
                        renderCell(this, renderer, cellCoords, color)
                    }

                    renderer.restore()
                } else {
                    val shapeIdx = shapeRotationIndex(currentShape.shape, currentShape.rotation)
                    for (cellOffset in shapesMap[shapeIdx]) {
                        val cellCoords = cellOffset.toVec2() + currentShape.movementAnimation.current
                        val color = if (currentShape.overlapping) {
                            Color.OVERLAPPING
                        } else {
                            currentShape.color
                        }
                        renderCell(this, renderer, cellCoords, color)
                    }
                }
            }
        }

        GameState.AnimatingCellsExplosion -> {
            // exploding cells
            val scratchCoords = Coords(0, 0)
            for (idx in 0..<CELLS_COUNT * CELLS_COUNT) {
                val cell = cells[idx]
                if (cell.chargeAlphaAnim.animating) {
                    scratchCoords.col = idx % CELLS_COUNT
                    scratchCoords.row = idx / CELLS_COUNT

                    // cell center
                    var cellCenter = coordsToPos(this, scratchCoords.toVec2())
                    cellCenter += cellSize / 2

                    // scaled
                    val scaledCellSize = cellSize * cell.currentScale()
                    val cellx = cellCenter.x - scaledCellSize / 2
                    val celly = cellCenter.y - scaledCellSize / 2

                    val p = cellPadding
                    val r = scaledCellSize * CELL_RADIUS_FRACTION
                    renderer.drawRoundRect(
                        cellx + p, celly + p, scaledCellSize - p, scaledCellSize - p,
                        r,
                        Color.addAlpha(cell.chargeAlphaAnim.current, Color.WHITE),
                    )
                }

                // particles
                if (cell.particlesAnimating) {
                    for (particle in cell.particles) {
                        if (!particle.dead()) {
                            val (cx, cy) = particle.currentPosition
                            val clr = Color.addAlpha(particle.alpha, Color.WHITE)
                            renderer.drawRect(cx, cy, 3f * pixelDensity, 3f * pixelDensity, clr)
                        }
                    }
                }
            }
        }
    }

    if (screenShake.animating) {
        renderer.restore()
    }
}
