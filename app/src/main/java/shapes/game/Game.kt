package shapes.game

import android.os.Trace
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.pow
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

const val EXPLOSION_START_CLEAR_DELAY = 0.2f
const val EXPLOSION_CHARGE_ANIMATION_DURATION = 0.4f
const val EXPLOSION_CELL_PULSE_COUNT = 2
const val EXPLOSION_CELL_SCALE_MAX = 1.35f
const val EXPLOSION_CELL_SCALE_MIN = 0.9f
const val EXPLOSION_PARTICLE_LIFESPAN = 0.6f
const val EXPLOSION_DELAY = 0.02f
const val DEFAULT_CELL_CLEAR_REWARD = 10

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

fun easeInSquared(progress: Float): Float = progress * progress
fun easeOutSquared(progress: Float): Float = 1f - easeInSquared(1f - progress)

fun easeInCubic(progress: Float): Float = progress * progress * progress
fun easeOutCubic(progress: Float): Float = 1f - easeInCubic(1f - progress)

enum class AnimationEasing {
    Linear,
    EaseInSquared,
    EaseOutSquared,
    EaseInCubic,
    EaseOutCubic,
}

val easeFunctions = mapOf(
    AnimationEasing.Linear to { progress: Float -> progress },
    AnimationEasing.EaseInSquared to ::easeInSquared,
    AnimationEasing.EaseOutSquared to ::easeOutSquared,
    AnimationEasing.EaseInCubic to ::easeInCubic,
    AnimationEasing.EaseOutCubic to ::easeOutCubic,
)

data class Animation<T>(
    var current: T,
    var duration: Float,
    var delay: Float = 0f,
    val lerp: (start: T, end: T, progress: Float) -> T,
    var easing: AnimationEasing = AnimationEasing.Linear,
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

        val t = easeFunctions[easing]?.invoke(progress) ?: progress
        current = lerp(animationStartValue, animationEndValue, t)
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

    val projectionAnim = Animation(
        DEFAULT_COORDS.toVec2(),
        SHAPE_MOVEMENT_ANIMATION_DURATION,
        lerp = ::lerp,
        easing = AnimationEasing.EaseOutSquared
    )
    val rotationAnim =
        Animation(0f, SHAPE_MOVEMENT_ANIMATION_DURATION, lerp = ::lerp, easing = AnimationEasing.EaseOutSquared)

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

fun currentShapeAvailableCoords(currentShape: CurrentShape, cells: Array<Cell>, rot: Int = -1): Coords? {
    Trace.beginSection("availableCoords")

    val shapeIdx = shapeRotationIndex(currentShape.shape, if (rot == -1) currentShape.rotation else rot)
    val shapeOffsets = shapesMap[shapeIdx]

    // coords represent shape grid top left position, which can be negative, since
    // the cells inside the shape grid are offsets from top left
    //
    // therefore we need offset this position by shape size so the idx stays positive
    //
    // for each axis, each occupied cell is: 0 <= origin + shapeOffset < CELLS_COUNT
    // where origin is the top left position of the shape grid
    //
    // therefore -minShapeOffset <= origin <= CELLS_COUUNT - 1 - maxShapeOffset

    // find shape dimensions

    // determine min/max offset col/row

    var minOffsetCol = Int.MAX_VALUE
    var maxOffsetCol = Int.MIN_VALUE
    var minOffsetRow = Int.MAX_VALUE
    var maxOffsetRow = Int.MIN_VALUE

    for (cellOffset in shapeOffsets) {
        minOffsetCol = kotlin.math.min(minOffsetCol, cellOffset.col)
        maxOffsetCol = kotlin.math.max(maxOffsetCol, cellOffset.col)
        minOffsetRow = kotlin.math.min(minOffsetRow, cellOffset.row)
        maxOffsetRow = kotlin.math.max(maxOffsetRow, cellOffset.row)
    }

    val minCandidateCol = -minOffsetCol
    val maxCandidateCol = CELLS_COUNT - 1 - maxOffsetCol
    val minCandidateRow = -minOffsetRow
    val maxCandidateRow = CELLS_COUNT - 1 - maxOffsetRow

    // determine valid cols/rows count

    val validColsCount = maxCandidateCol - minCandidateCol + 1
    val validRowsCount = maxCandidateRow - minCandidateRow + 1

    val visited = BooleanArray(validColsCount * validRowsCount) // cell[cellIndex] = visited
    val queue = IntArray(validColsCount * validRowsCount) // cell indeces
    var queueStart = 0
    var queueEnd = 0

    fun index(col: Int, row: Int): Int {
        val offsetRow = row - minCandidateRow
        val offsetCol = col - minCandidateCol
        return offsetRow * validColsCount + offsetCol
    }

    fun enqueue(col: Int, row: Int) {
        if (col !in minCandidateCol..maxCandidateCol || row !in minCandidateRow..maxCandidateRow) {
            return
        }

        val idx = index(col, row)
        if (visited[idx]) {
            return
        }

        visited[idx] = true
        queue[queueEnd] = idx
        queueEnd += 1
    }

    enqueue(currentShape.projectionCoords.col, currentShape.projectionCoords.row)

    var resultCoords: Coords? = null
    search@ while (queueStart < queueEnd) {
        val tryIdx = queue[queueStart]
        queueStart += 1

        val tryCoords = Coords(
            tryIdx % validColsCount, // offsetCol
            tryIdx / validColsCount, // offsetRow
        )
        tryCoords.col += minCandidateCol
        tryCoords.row += minCandidateRow

        var valid = true
        for (cellOffset in shapesMap[shapeIdx]) {
            val tryCellCoords = tryCoords + cellOffset
            val idx = coordsToIdx(tryCellCoords.col, tryCellCoords.row)
            assert(idx >= 0 && idx < cells.size) // is guaranteed by enqueue

            val cell = cells[idx]
            if (cell.filled) {
                valid = false
                break
            }
        }

        if (valid) {
            resultCoords = tryCoords
            break
        }

        enqueue(tryCoords.col, tryCoords.row - 1) // up
        enqueue(tryCoords.col + 1, tryCoords.row - 1) // up right
        enqueue(tryCoords.col + 1, tryCoords.row) // right
        enqueue(tryCoords.col + 1, tryCoords.row + 1) // down right
        enqueue(tryCoords.col, tryCoords.row + 1) // down
        enqueue(tryCoords.col - 1, tryCoords.row + 1) // down left
        enqueue(tryCoords.col - 1, tryCoords.row) //  left
        enqueue(tryCoords.col - 1, tryCoords.row - 1) // up left
    }

    Trace.endSection()
    return resultCoords
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
    if (amount > 0) {
        text = "+$amount"
    } else {
        text = "$amount"
    }
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

enum class CellExplosionState {
    None,
    Growing,
    Shrinking,
}

data class Cell(
    val ctx: GameContext,
    val idx: Int,
    var color: Int = 0,
    var filled: Boolean = false,
) {
    var filledAt = 0f
    var explosionState = CellExplosionState.None

    private fun lerpColor(start: Int, end: Int, progress: Float): Int {
        val a = (start shr 24) and 0xFF
        val r = (start shr 16) and 0xFF
        val g = (start shr 8) and 0xFF
        val b = start and 0xFF

        val targetA = (end shr 24) and 0xFF
        val targetR = (end shr 16) and 0xFF
        val targetG = (end shr 8) and 0xFF
        val targetB = end and 0xFF

        return Color.argb(
            lerp(a, targetA, progress),
            lerp(r, targetR, progress),
            lerp(g, targetG, progress),
            lerp(b, targetB, progress),
        )
    }

    val growingDuration = 0.2f
    val shrinkingDuration = 0.3f

    val chargeColorAnim = Animation(0, growingDuration, lerp = ::lerpColor)
    val pulseAnim = Animation(0f, shrinkingDuration, lerp = ::lerp)
    val rotationAnim = Animation(0f, shrinkingDuration, lerp = ::lerp, easing = AnimationEasing.EaseOutSquared)

    var scoreReward = 10
    val scoreAnnouncer = ScoreAnnouncer(ctx)
}

fun Cell.beginExplosion(delay: Float) {
    explosionState = CellExplosionState.Growing

    chargeColorAnim.delay = delay
    chargeColorAnim.current = color
    chargeColorAnim.duration = growingDuration
    chargeColorAnim.easing = AnimationEasing.EaseInSquared
    chargeColorAnim.begin(ctx.elapsedTime, Color.WHITE)

    pulseAnim.current = 1f
    pulseAnim.delay = delay
    pulseAnim.animating = false
    pulseAnim.easing = AnimationEasing.EaseInSquared
    pulseAnim.duration = growingDuration
    pulseAnim.begin(ctx.elapsedTime, 1.2f)
}

fun cellUpdateExplosion(cell: Cell, elapsedTime: Float): Boolean {
    assert(cell.explosionState != CellExplosionState.None) { "Explosion animation is not active" }

    cell.chargeColorAnim.update(elapsedTime)
    cell.pulseAnim.update(elapsedTime)

    when (cell.explosionState) {
        CellExplosionState.Growing -> {
            if (!cell.pulseAnim.animating && !cell.chargeColorAnim.animating) {
                cell.explosionState = CellExplosionState.Shrinking

                cell.chargeColorAnim.delay = 0f
                cell.chargeColorAnim.duration = cell.shrinkingDuration
                cell.chargeColorAnim.easing = AnimationEasing.EaseOutSquared
                cell.chargeColorAnim.begin(elapsedTime, Color.addAlpha(0, cell.chargeColorAnim.current))

                cell.pulseAnim.delay = 0f
                cell.pulseAnim.duration = cell.shrinkingDuration
                cell.pulseAnim.easing = AnimationEasing.EaseOutSquared
                cell.pulseAnim.begin(elapsedTime, 0.5f)

                cell.rotationAnim.animating = false
                cell.rotationAnim.current = 0f
                cell.rotationAnim.begin(elapsedTime, 45f)

                return true
            }
        }

        CellExplosionState.Shrinking -> {
            cell.rotationAnim.update(elapsedTime)

            if (!cell.pulseAnim.animating && !cell.chargeColorAnim.animating && !cell.rotationAnim.animating) {
                cell.explosionState = CellExplosionState.None
            }
        }

        CellExplosionState.None -> Unit
    }

    return false
}

sealed interface GameState {
    data object Countdown : GameState
    data object Placing : GameState
    data class AnimatingCurrentShape(val forced: Boolean) : GameState
    data object AnimatingCellsExplosion : GameState
    data object GameOver : GameState
}

typealias onScoreChange = (Int) -> Unit
typealias onPlaceShape = () -> Unit
typealias onRoundStart = (shapeIdx: Int, roundDuration: Float) -> Unit
typealias onGameOver = () -> Unit

class GameContext(
    val pixelDensity: Float,
    val scaledDensity: Float,
    val onScoreChange: onScoreChange? = null,
    val onPlaceShape: onPlaceShape? = null,
    val onRoundStart: onRoundStart? = null,
    val onGameOver: onGameOver? = null,
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
    var shapesPlaced = 0
    var roundDuration = 5f
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

fun placeShape(ctx: GameContext, forced: Boolean) {
    for (cellCoords in ctx.currentShape.cells()) {
        val idx = coordsToIdx(cellCoords.col, cellCoords.row)
        val cell = ctx.cells[idx]

        cell.filled = true
        cell.filledAt = ctx.elapsedTime
        cell.color = ctx.currentShape.color

        val score = if (forced) -1 else 1
        cell.scoreAnnouncer.begin(score)
        ctx.addScore(score)
    }

    ctx.shapesPlaced += 1
    ctx.onPlaceShape?.invoke()
    ctx.currentShape.shape = -1
}

fun clearFilled(ctx: GameContext) {
    val filledRows = IntArray(CELLS_COUNT) { -1 }
    val filledCols = IntArray(CELLS_COUNT) { -1 }

    data class Result(
        val fixedCoord: Int,
        var filled: Boolean = true,
        var start: Int = -1,
        var prevFillTime: Float = 0f,
        val coordsToIdx: (fixedCoord: Int, movingCoord: Int) -> Int,
    )

    fun resultProcess(result: Result, movingCoord: Int) {
        val idx = result.coordsToIdx(result.fixedCoord, movingCoord)
        val cell = ctx.cells[idx]

        if (!cell.filled) {
            result.filled = false
        }

        if (result.prevFillTime < cell.filledAt) {
            result.prevFillTime = cell.filledAt
            result.start = movingCoord
        }
    }

    fun resultBeginExplosion(result: Result, elapsedTime: Float, delay: Float, scoreMultiplier: Int): Float {
        println(delay)

        // first
        run {
            val idx = result.coordsToIdx(result.fixedCoord, result.start)
            val cell = ctx.cells[idx]
            cell.beginExplosion(delay)
        }

        // delays

        var offset = 1
        while (true) {
            val preCoord = result.start - offset
            val postCoord = result.start + offset

            if (preCoord < 0 && postCoord >= CELLS_COUNT) {
                break
            }

            val cellDelay = offset * EXPLOSION_DELAY + delay

            if (preCoord >= 0) {
                val left = ctx.cells[result.coordsToIdx(result.fixedCoord, preCoord)]
                left.scoreReward = scoreMultiplier * DEFAULT_CELL_CLEAR_REWARD

                if (left.explosionState == CellExplosionState.None) {
                    left.beginExplosion(cellDelay)
                }
            }

            if (postCoord < CELLS_COUNT) {
                val right = ctx.cells[result.coordsToIdx(result.fixedCoord, postCoord)]
                right.scoreReward = scoreMultiplier * DEFAULT_CELL_CLEAR_REWARD

                if (right.explosionState == CellExplosionState.None) {
                    right.beginExplosion(cellDelay)
                }
            }

            offset += 1
        }

        val maxDelay = (offset - 1) * EXPLOSION_DELAY
        return maxDelay
    }

    var animating = false
    var scoreMultiplier = 1
    var rowDelay = 0f
    var colDelay = 0f

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

            if (rowResult.filled) {
                resultProcess(rowResult, j)
            }
            if (colResult.filled) {
                resultProcess(colResult, j)
            }
        }

        if (rowResult.filled) {
            animating = true
            resultBeginExplosion(rowResult, ctx.elapsedTime, rowDelay, scoreMultiplier)

            scoreMultiplier += 1
            rowDelay += EXPLOSION_START_CLEAR_DELAY

        }

        if (colResult.filled) {
            animating = true
            resultBeginExplosion(colResult, ctx.elapsedTime, colDelay, scoreMultiplier)

            scoreMultiplier += 1
            colDelay += EXPLOSION_START_CLEAR_DELAY
        }
    }

    if (animating) {
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

fun currentRoundDuration(shapesPlaced: Int): Float {
    val startingSeconds = 5f
    val minimumSeconds = 3f
    val warmupShapes = 10
    val halfLifeShapes = 50f

    val shapesIntoDifficulty = kotlin.math.max(0, shapesPlaced - warmupShapes)

    return minimumSeconds +
            (startingSeconds - minimumSeconds) *
            0.5f.pow(shapesIntoDifficulty / halfLifeShapes)
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

    val currentState = state
    when (currentState) {
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
                Trace.beginSection("spawnShape")
                val shapeIdx = shapesBag.next()
                currentShape = CurrentShape(this, shapeIdx)

                var availableCoords = currentShapeAvailableCoords(currentShape, cells, 0)
                if (availableCoords == null) availableCoords = currentShapeAvailableCoords(currentShape, cells, 1)
                if (availableCoords == null) availableCoords = currentShapeAvailableCoords(currentShape, cells, 2)
                if (availableCoords == null) availableCoords = currentShapeAvailableCoords(currentShape, cells, 3)

                if (availableCoords == null) {
                    onGameOver?.invoke()
                    state = GameState.GameOver
                    Trace.endSection()
                    return
                }

                val nextShapeIdx = shapesBag.peek()
                currentShape.checkOverlap()

                roundDuration = currentRoundDuration(shapesPlaced)
                onRoundStart?.invoke(nextShapeIdx, roundDuration)

                pendingRotation = false
                pendingPlacement = false
                Trace.endSection()
            } else {
                // check round timer

                if (currentShape.createdAt + roundDuration < elapsedTime) {
                    // force place
                    Trace.beginSection("forcePlace")

                    val availableCoords = currentShapeAvailableCoords(currentShape, cells)
                    if (availableCoords == null) {
                        onGameOver?.invoke()
                        state = GameState.GameOver
                        Trace.endSection()
                        return
                    }

                    Trace.endSection()

                    var forced = false

                    if (availableCoords != currentShape.projectionCoords) {
                        currentShape.projectionCoords = availableCoords
                        currentShape.projectionAnim.begin(elapsedTime, availableCoords.toVec2())
                        forced = true
                    }

                    if (!currentShape.projectionAnim.animating) {
                        placeShape(this, forced)
                        clearFilled(this)
                    } else {
                        state = GameState.AnimatingCurrentShape(true)
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
                                placeShape(this, false)
                                clearFilled(this)
                            } else {
                                state = GameState.AnimatingCurrentShape(false)
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

        is GameState.AnimatingCurrentShape -> {
            // shape dragging

            currentShape.projectionAnim.update(elapsedTime)

            if (!currentShape.projectionAnim.animating) {
                placeShape(this, currentState.forced)
                clearFilled(this)
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

                if (cell.explosionState != CellExplosionState.None) {
                    if (cellUpdateExplosion(cell, elapsedTime)) {
                        cell.scoreAnnouncer.begin(cell.scoreReward)
                        addScore(cell.scoreReward)
                    }

                    if (cell.explosionState == CellExplosionState.None) {
                        cell.filled = false
                        cell.scoreReward = DEFAULT_CELL_CLEAR_REWARD
                    } else {
                        allDone = false
                    }
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
    // playground
    renderer.drawRoundRect(pgRect, RADIUS * pixelDensity, Color.BLACK)

    run { // draw placed cells
        for (idx in 0..<CELLS_COUNT * CELLS_COUNT) {
            val cell = cells[idx]

            if (cell.filled && cell.explosionState == CellExplosionState.None) {
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

        GameState.Placing, is GameState.AnimatingCurrentShape -> {
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
                if (cell.explosionState != CellExplosionState.None) {
                    scratchCoords.col = idx % CELLS_COUNT
                    scratchCoords.row = idx / CELLS_COUNT

                    // cell center
                    var cellCenter = coordsToPos(this, scratchCoords.toVec2())
                    cellCenter += cellSize / 2


                    if (cell.rotationAnim.animating) {
                        val rotation = cell.rotationAnim.current
                        renderer.save()
                        renderer.rotate(rotation, cellCenter.x, cellCenter.y)
                    }

                    // scaled
                    if (cell.pulseAnim.current > 0f) {
                        val scaledCellSize = cellSize * cell.pulseAnim.current
                        val cellx = cellCenter.x - scaledCellSize / 2
                        val celly = cellCenter.y - scaledCellSize / 2

                        val p = cellPadding
                        val r = scaledCellSize * CELL_RADIUS_FRACTION
                        renderer.drawRoundRect(
                            cellx + p, celly + p, scaledCellSize - p, scaledCellSize - p,
                            r,
                            cell.chargeColorAnim.current,
                        )
                    }

                    if (cell.rotationAnim.animating) {
                        renderer.restore()
                    }
                }
            }
        }

        GameState.GameOver -> Unit
    }
}
