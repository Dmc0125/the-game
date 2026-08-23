package shapes.game

import kotlin.math.PI
import kotlin.random.Random

const val FONT_MANROPE = "manrope"
const val FONT_DMMONO = "dmmono"

const val RADIUS = 8f
const val CELLS_COUNT = 12
const val SHAPE_CELLS_COUNT = 4
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

        val centerColOffset = (SHAPE_CELLS_COUNT - width) / 2
        val centerRowOffset = (SHAPE_CELLS_COUNT - height) / 2
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
            rotated[i].col = (SHAPE_CELLS_COUNT - 1) - row
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
    // 2x2 square
    *generateShapeOffsets(arrayOf(Coords(1, 1), Coords(2, 1), Coords(1, 2), Coords(2, 2))),
    // 1x3 horizontal bar
    *generateShapeOffsets(arrayOf(Coords(0, 1), Coords(1, 1), Coords(2, 1))),
    // 2x2 L triomino
    *generateShapeOffsets(arrayOf(Coords(1, 1), Coords(1, 2), Coords(2, 2))),
    // 3x2 T tetromino
    *generateShapeOffsets(arrayOf(Coords(0, 1), Coords(1, 1), Coords(2, 1), Coords(1, 2))),
    // 1x4 horizontal bar
    *generateShapeOffsets(arrayOf(Coords(0, 2), Coords(1, 2), Coords(2, 2), Coords(3, 2))),
    // 3x2 L tetromino
    *generateShapeOffsets(arrayOf(Coords(0, 0), Coords(0, 1), Coords(1, 1), Coords(2, 1))),
    // 3x2 S tetromino
    *generateShapeOffsets(arrayOf(Coords(1, 0), Coords(2, 0), Coords(0, 1), Coords(1, 1))),
    // 3x3 T pentomino
    *generateShapeOffsets(arrayOf(Coords(0, 0), Coords(1, 0), Coords(2, 0), Coords(1, 1), Coords(1, 2))),
    // 3x3 U pentomino
    *generateShapeOffsets(arrayOf(Coords(0, 0), Coords(2, 0), Coords(0, 1), Coords(1, 1), Coords(2, 1))),
    // 3x3 W pentomino
    *generateShapeOffsets(arrayOf(Coords(0, 0), Coords(0, 1), Coords(1, 1), Coords(1, 2), Coords(2, 2))),
    // 4x3 staircase hexomino
    *generateShapeOffsets(arrayOf(Coords(0, 0), Coords(1, 0), Coords(1, 1), Coords(2, 1), Coords(2, 2), Coords(3, 2))),
)

fun shapeRotationIndex(shapeIdx: Int, rotation: Int): Int {
    logd("shapeRotationIndex: shapeIdx=$shapeIdx, rotation=$rotation")
    return (shapeIdx * 4) + (rotation % 4)
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

class CurrentShape(
    val ctx: GameContext,
    var shape: Int = -1,
) {
    companion object {
        val DEFAULT_COORDS = Coords(CELLS_COUNT / 2 - SHAPE_CELLS_COUNT / 2, CELLS_COUNT / 2 - SHAPE_CELLS_COUNT / 2)
    }

    val createdAt: Float = ctx.elapsedTime
    val color: Int = colors[Random.nextInt(colors.size)]

    var rotation: Int = 0
    var dragging: Boolean = false

    var overlapping = false

    var projectionCoords = DEFAULT_COORDS.copy()
    var posCoordsPrev = DEFAULT_COORDS.toVec2()
    var posCoords = DEFAULT_COORDS.toVec2()

    val projectionAnim = Animation(DEFAULT_COORDS.toVec2(), SHAPE_MOVEMENT_ANIMATION_DURATION, lerp = ::lerp)
    val rotationAnim = Animation(0f, SHAPE_MOVEMENT_ANIMATION_DURATION, lerp = ::lerp)

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
                    return projectionCoords + offset
                }
            }
        }
    }
}

fun CurrentShape.move(newPos: Vec2) {
    posCoords = newPos
    projectionCoords.col = kotlin.math.round(newPos.x).toInt()
    projectionCoords.row = kotlin.math.round(newPos.y).toInt()
    projectionAnim.begin(ctx.elapsedTime, projectionCoords.toVec2())
}

fun CurrentShape.checkOverlap() {
    overlapping = false
    for (cellCoords in cells()) {
        val idx = coordsToIdx(cellCoords.col, cellCoords.row)
        assert(idx in ctx.cells.indices) { "idx=$idx, cells.size=${ctx.cells.size}" }
        if (ctx.cells[idx].filled) {
            overlapping = true
            break
        }
    }
}

fun CurrentShape.availablePlacementCoords(rot: Int = -1): Coords? {
    val offsets = arrayOf(
        Coords(0, -1), // up
        Coords(1, -1), // up right
        Coords(1, 0), // right
        Coords(1, 1), // down right
        Coords(0, 1), // down
        Coords(-1, 1), // down left
        Coords(-1, 0), // left
        Coords(-1, -1), // up left
    )

    val queue = ArrayDeque(listOf(projectionCoords))
    val visited = mutableSetOf<Coords>()
    val shapeIdx = shapeRotationIndex(shape, if (rot == -1) this.rotation else rot)

    search@ while (queue.size > 0) {
        val tryCoords = queue.removeFirst()
        if (visited.contains(tryCoords)) {
            continue
        }

        visited.add(tryCoords)

        // process

        var valid = true

        for (cellOffset in shapesMap[shapeIdx]) {
            val tryCellCoords = tryCoords + cellOffset

            if (
                tryCellCoords.col !in 0..<CELLS_COUNT ||
                tryCellCoords.row !in 0..<CELLS_COUNT
            ) {
                valid = false
                break
            }

            val idx = coordsToIdx(tryCellCoords.col, tryCellCoords.row)
            if (idx < 0 || idx > ctx.cells.size - 1) {
                continue@search
            }

            val cell = ctx.cells[idx]
            if (cell.filled) {
                valid = false
                break
            }
        }

        if (valid) {
            return tryCoords
        }

        // next

        for (offset in offsets) {
            val nextCoords = tryCoords + offset
            if (
                nextCoords !in visited &&
                nextCoords.col in 0..<CELLS_COUNT &&
                nextCoords.row in 0..<CELLS_COUNT
            ) {
                queue.add(nextCoords)
            }
        }
    }

    return null
}

fun CurrentShape.render() {
    fun renderShape(rot: Int = 0) {
        val shapeIdx = shapeRotationIndex(shape, rot)
        for (cellOffset in shapesMap[shapeIdx]) {
            val clr = if (overlapping) {
                Color.OVERLAPPING
            } else {
                color
            }

            val projectionCellCoords = cellOffset.toVec2() + projectionAnim.current
            renderCell(ctx, projectionCellCoords, Color.addAlpha(150, clr), true)

            val cellPosCoords = cellOffset.toVec2() + posCoords
            renderCell(ctx, cellPosCoords, clr)
        }
    }

    // rotating
    if (rotationAnim.animating) {
        // middle of the shape
        val pivotCoords = posCoords + Vec2(SHAPE_CELLS_COUNT / 2f, SHAPE_CELLS_COUNT / 2f)
        val pivotPos = coordsToPos(ctx, pivotCoords)

        ctx.renderer.save()
        ctx.renderer.rotate(
            rotationAnim.current,
            pivotPos.x, pivotPos.y,
        )

        renderShape()

        ctx.renderer.restore()
    } else {
        renderShape(rotation)
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

class ScoreAnnouncer(val ctx: GameContext) {
    var startPos = Vec2.DEFAULT
    var endOffset = 0f
    val duration = 0.5f

    var animating = false
    var text = ""
    var currentPos = Vec2.DEFAULT
    var startTime = 0f
    var alpha = 0
}

fun ScoreAnnouncer.init(coords: Coords) {
    val padding = ctx.cellSize * 0.3f

    startPos = coordsToPos(ctx, coords) // cell top left
    startPos.x += ctx.cellSize // cell top right
    startPos.x += padding

    endOffset = ctx.cellSize / 2

    alpha = 255
}

fun ScoreAnnouncer.begin(amount: Int) {
    text = "+$amount"
    currentPos = startPos.copy()
    startTime = ctx.elapsedTime
    animating = true
}

fun ScoreAnnouncer.update() {
    val age = ctx.elapsedTime - startTime
    if (age > duration) {
        animating = false
        return
    }

    val fraction = age / duration
    currentPos.y = startPos.y - lerp(0f, endOffset, fraction)
    alpha = lerp(255, 0, fraction)
}

fun ScoreAnnouncer.render() {
    ctx.renderer.drawText(
        text,
        currentPos.x,
        currentPos.y,
        Color.addAlpha(alpha, Color.WHITE),
        16f * ctx.scaledDensity,
        FontWeight.Medium,
        FONT_DMMONO
    )
    ctx.renderer.strokeText(
        text,
        currentPos.x,
        currentPos.y,
        0.75f * ctx.scaledDensity,
        Color.addAlpha(alpha, Color.BLACK),
        16f * ctx.scaledDensity,
        FontWeight.Medium,
        FONT_DMMONO
    )
}

data class Particle(val ctx: GameContext) {
    // config
    val startPosition = Vec2(0f, 0f)
    var distance = 0f

    // computed
    var alpha = 255
    var currentPosition = Vec2(0f, 0f)
    val endPosition = Vec2(0f, 0f)
    var spawnTime = 0f
}

fun Particle.init(coords: Coords) {
    // center of the cell

    val (x, y) = coordsToPos(ctx, coords)

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

    val scoreAnnouncer = ScoreAnnouncer(ctx)
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
                scoreAnnouncer.begin(10)
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

enum class GameState {
    Countdown,
    Placing,
    AnimatingCurrentShape,
    AnimatingCellsExplosion,
    GameOver,
}

class GameContext(
    val pixelDensity: Float,
    val scaledDensity: Float,
    val onScoreChange: ((Int) -> Unit)? = null,
    val onPlaceShape: (() -> Unit)? = null,
    val onNextShape: ((Int) -> Unit)? = null,
    val onGameOver: (() -> Unit)? = null,
) {
    var dt = 0f
    var elapsedTime = 0f
    var renderer: Renderer = Renderer.Default

    // outside effects

    var pendingRotation = false
    var pendingPlacement = false
    var changedWidth = -1f
    var changedHeight = -1f

    // layout

    var pgRect = Rect()
    var pgPadding = 0f
    var cellSize = 0f
    var cellPadding = 0f

    // state

    val cells = Array(CELLS_COUNT * CELLS_COUNT) { Cell(this, it) }
    var state: GameState = GameState.Countdown
    val shapesBag = ShapesBag()
    var currentShape = CurrentShape(this)
    val countdownText = CountdownText(this)
    val screenShake = ScreenShake(this)
    var shapesPlaced = 0
    var score = 0
}

fun GameContext.reset() {
    dt = 0f
    elapsedTime = 0f

    shapesPlaced = 0
    score = 0
    onScoreChange?.invoke(score)

    for (cell in cells) {
        cell.filled = false
    }

    state = GameState.Countdown
    shapesBag.current = -1
    currentShape.shape = -1
}

fun GameContext.addScore(amount: Int) {
    score += amount
    onScoreChange?.invoke(score)
}

fun cellIdxToCoords(idx: Int): Coords {
    val col = idx % CELLS_COUNT
    val row = idx / CELLS_COUNT
    return Coords(col, row)
}

fun coordsToPos(ctx: GameContext, coords: Coords): Vec2 {
    return coordsToPos(ctx, coords.toVec2())
}

fun coordsToPos(ctx: GameContext, coords: Vec2): Vec2 {
    val x = coords.x * ctx.cellSize + ctx.pgPadding
    val y = coords.y * ctx.cellSize + ctx.pgPadding
    return Vec2(x, y)
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
        cell.scoreAnnouncer.begin(1)

        ctx.addScore(1)
    }

    ctx.shapesPlaced += 1
    ctx.onPlaceShape?.invoke()
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

fun checkOverTheEdge(newPosCoords: Vec2, shapeIdx: Int): Vec2 {
    var minCol = Float.MAX_VALUE
    var maxCol = Float.MIN_VALUE
    var minRow = Float.MAX_VALUE
    var maxRow = Float.MIN_VALUE

    val shapeOffsets = shapesMap[shapeIdx]
    for (cellOffsets in shapeOffsets) {
        val cellCoords = newPosCoords + cellOffsets.toVec2()
        minCol = kotlin.math.min(minCol, cellCoords.x)
        maxCol = kotlin.math.max(maxCol, cellCoords.x)
        minRow = kotlin.math.min(minRow, cellCoords.y)
        maxRow = kotlin.math.max(maxRow, cellCoords.y)
    }

    val offsets = Vec2(0f, 0f)
    if (minCol < 0) offsets.x -= minCol
    if (maxCol >= CELLS_COUNT - 1) offsets.x -= (maxCol - CELLS_COUNT + 1)
    if (minRow < 0) offsets.y -= minRow
    if (maxRow >= CELLS_COUNT - 1) offsets.y -= (maxRow - CELLS_COUNT + 1)

    return offsets
}

fun GameContext.update(touch: Touch) {
    // size change

    if (changedWidth > -1f || changedHeight > -1) {
        val wf = changedWidth
        val hf = changedHeight

        pgPadding = wf * PLAYGROUND_PADDING_FRACTION
        pgRect = Rect(0f, 0f, wf, hf)

        cellSize = (wf - pgPadding * 2) / CELLS_COUNT
        cellPadding = cellSize * CELL_PADDING_FRACTION

        for (cell in cells) {
            val col = cell.idx % CELLS_COUNT
            val row = cell.idx / CELLS_COUNT
            val coords = Coords(col, row)

            for (particle in cell.particles) {
                particle.init(coords)
            }

            cell.scoreAnnouncer.init(coords)
        }

        changedWidth = -1f
        changedHeight = -1f
    }

    // score announcer

    for (cell in cells) {
        if (cell.scoreAnnouncer.animating) {
            cell.scoreAnnouncer.update()
        }
    }

    when (state) {
        GameState.Countdown -> {
            if (shapes.BuildConfig.DEBUG) {
                state = GameState.Placing
            } else {
                if (countdownText.update(elapsedTime, pgRect)) {
                    state = GameState.Placing
                }
            }
        }

        GameState.Placing -> {
            // spawn shape
            if (currentShape.shape == -1) {
                val shapeIdx = shapesBag.next()
                currentShape = CurrentShape(this, shapeIdx)

                var availableCoords = currentShape.availablePlacementCoords(0)
                if (availableCoords == null) availableCoords = currentShape.availablePlacementCoords(1)
                if (availableCoords == null) availableCoords = currentShape.availablePlacementCoords(2)
                if (availableCoords == null) availableCoords = currentShape.availablePlacementCoords(3)

                if (availableCoords == null) {
                    onGameOver?.invoke()
                    state = GameState.GameOver
                    return
                }

                val nextShapeIdx = shapesBag.peek()
                onNextShape?.invoke(nextShapeIdx)
                currentShape.checkOverlap()

                pendingRotation = false
                pendingPlacement = false
            } else {
                // check round timer

                if (currentShape.createdAt + 10f < elapsedTime) {
                    // force place

                    val availableCoords = currentShape.availablePlacementCoords()
                    if (availableCoords == null) {
                        onGameOver?.invoke()
                        state = GameState.GameOver
                        return
                    }

                    if (availableCoords != currentShape.projectionCoords) {
                        currentShape.projectionCoords = availableCoords
                        currentShape.projectionAnim.begin(elapsedTime, availableCoords.toVec2())
                    }

                    if (!currentShape.projectionAnim.animating) {
                        placeShapeAndBeginExplosion(this)
                    } else {
                        state = GameState.AnimatingCurrentShape
                    }
                } else {
                    // process inputs

                    // rotation

                    if (pendingRotation) {
                        val newRotation = currentShape.rotation + 1
                        val newShapeIdx = shapeRotationIndex(currentShape.shape, newRotation)
                        val kicks = checkOverTheEdge(currentShape.posCoords, newShapeIdx)

                        if (kicks.x != 0f || kicks.y != 0f) {
                            currentShape.move(currentShape.posCoords + kicks)
                        }

                        currentShape.rotation = newRotation
                        currentShape.rotationAnim.begin(elapsedTime, newRotation * 90f)
                        currentShape.checkOverlap()

                        pendingRotation = false
                    }

                    // placement

                    var placed = false

                    if (pendingPlacement) {
                        if (!currentShape.overlapping) {
                            if (!currentShape.projectionAnim.animating) {
                                placeShapeAndBeginExplosion(this)
                            } else {
                                state = GameState.AnimatingCurrentShape
                            }

                            placed = true
                        }
                        pendingPlacement = false
                    }

                    if (!placed) {
                        // shape movement

                        if (!currentShape.dragging && touch.isDown) {
                            currentShape.dragging = true
                        } else if (currentShape.dragging) {
                            if (!touch.isDown) {
                                currentShape.dragging = false
                                currentShape.posCoordsPrev = currentShape.posCoords.copy()
                            } else {
                                val diff = touch.position - touch.startPosition
                                val colsDiff = (diff / cellSize) * DRAG_SENSITIVITY
                                var newPosCoords = currentShape.posCoordsPrev + colsDiff

                                val shapeIdx = shapeRotationIndex(currentShape.shape, currentShape.rotation)
                                val kicks = checkOverTheEdge(newPosCoords, shapeIdx)
                                newPosCoords += kicks

                                currentShape.move(newPosCoords)
                                currentShape.checkOverlap()
                            }
                        }

                        currentShape.projectionAnim.update(elapsedTime)
                        currentShape.rotationAnim.update(elapsedTime)
                    }
                }
            }
        }

        GameState.AnimatingCurrentShape -> {
            // shape dragging

            currentShape.projectionAnim.update(elapsedTime)

            if (!currentShape.projectionAnim.animating) {
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

        GameState.GameOver -> Unit
    }
}

fun renderCell(ctx: GameContext, coords: Coords, color: Int, stroke: Boolean = false) {
    renderCell(ctx, coords.toVec2(), color, stroke)
}

fun renderCell(ctx: GameContext, coords: Vec2, color: Int, stroke: Boolean = false) {
    val (cellx, celly) = coordsToPos(ctx, coords)
    val p = ctx.cellPadding
    val r = ctx.cellSize * CELL_RADIUS_FRACTION
    if (stroke) {
        ctx.renderer.strokeRoundRect(
            cellx + p, celly + p, ctx.cellSize - p, ctx.cellSize - p,
            r,
            color,
            2f * ctx.pixelDensity,
        )
    } else {
        ctx.renderer.drawRoundRect(
            cellx + p, celly + p, ctx.cellSize - p, ctx.cellSize - p,
            r,
            color,
        )
    }
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
        for (idx in 0..<CELLS_COUNT * CELLS_COUNT) {
            val cell = cells[idx]

            if (cell.filled && !cell.particlesAnimating) {
                val coords = cellIdxToCoords(idx)
                renderCell(this, coords, cell.color)
            }

            if (cell.scoreAnnouncer.animating) {
                cell.scoreAnnouncer.render()
            }
        }
    }

    when (state) {
        GameState.Countdown -> {
            countdownText.render()
        }

        GameState.Placing, GameState.AnimatingCurrentShape -> {
            assert(currentShape.shape < shapesMap.size)

            if (currentShape.shape != -1) {
                currentShape.render()
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

        GameState.GameOver -> Unit
    }

    if (screenShake.animating) {
        renderer.restore()
    }
}
