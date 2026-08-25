package shapes.game

import android.os.Trace
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
const val EXPLOSION_DELAY = 0.02f
const val DEFAULT_CELL_CLEAR_REWARD = 10

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

class Countdown(val textSizeStart: Float, val textSizeEnd: Float) {
    val textSizeDiff = textSizeEnd - textSizeStart

    var text: String = ""
    var textSize: Float = 0f
    var start = 0f
    var textX: Float = 0f
    var textY: Float = 0f
    var opacity: Float = 1f
}

fun countdownUpdate(countdown: Countdown, layout: Layout, renderer: Renderer, elapsedTime: Float): Boolean {
    var currentText = when {
        elapsedTime < 1f -> "3"
        elapsedTime < 2f -> "2"
        elapsedTime < 3f -> "1"
        elapsedTime < 4f -> "Go"
        else -> return true
    }

    if (currentText != countdown.text) {
        countdown.text = currentText
        countdown.textSize = countdown.textSizeStart
        countdown.start = elapsedTime
        countdown.opacity = 1f
    } else {
        val dt = elapsedTime - countdown.start
        countdown.textSize = countdown.textSizeStart + countdown.textSizeDiff * dt
        countdown.opacity = 1f - 1f * dt
    }

    val textWidth = renderer.measureText(countdown.text, countdown.textSize, FontWeight.Medium, FONT_DMMONO)
    countdown.textX = layout.pgRect.x + (layout.pgRect.width - textWidth) / 2
    countdown.textY = layout.pgRect.y + countdown.textSize + (layout.pgRect.height - countdown.textSize) / 2

    return false
}

fun countdownRender(countdown: Countdown, renderer: Renderer) {
    val color = Color.argb((countdown.opacity * 255).toInt(), 255, 255, 255)
    renderer.drawText(
        countdown.text,
        countdown.textX,
        countdown.textY,
        color,
        countdown.textSize,
        FontWeight.Medium,
        FONT_DMMONO
    )
}

class CurrentShape(var shape: Int = -1, val createdAt: Float = 0f) {
    companion object {
        val DEFAULT_COORDS = Coords(CELLS_COUNT / 2 - SHAPE_CELLS_COUNT / 2, CELLS_COUNT / 2 - SHAPE_CELLS_COUNT / 2)
    }

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
}

fun currentShapeCells(currentShape: CurrentShape): Iterable<Coords> {
    return Iterable {
        val shapeIdx = shapeRotationIndex(currentShape.shape, currentShape.rotation)
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
                return currentShape.projectionCoords + offset
            }
        }
    }
}

fun currentShapeSpawn(
    shapeIdx: Int,
    board: Board,
    elapsedTime: Float,
): Pair<Boolean, CurrentShape> {
    var gameOver = false

    Trace.beginSection("gameSpawnShape")

    val newShape = CurrentShape(shapeIdx, elapsedTime)

    var availableCoords = currentShapeAvailableCoords(newShape, board.cells, 0)
    if (availableCoords == null) availableCoords =
        currentShapeAvailableCoords(newShape, board.cells, 1)
    if (availableCoords == null) availableCoords =
        currentShapeAvailableCoords(newShape, board.cells, 2)
    if (availableCoords == null) availableCoords =
        currentShapeAvailableCoords(newShape, board.cells, 3)

    Trace.endSection()

    if (availableCoords == null) {
        gameOver = true
    } else {
        currentShapeCheckOverlap(newShape, board.cells)
    }

    return Pair(gameOver, newShape)
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


fun currentShapeMove(currentShape: CurrentShape, newPos: Vec2, elapsedTime: Float) {
    currentShape.posCoords = newPos
    currentShape.projectionCoords.col = kotlin.math.round(newPos.x).toInt()
    currentShape.projectionCoords.row = kotlin.math.round(newPos.y).toInt()
    currentShape.projectionAnim.begin(elapsedTime, currentShape.projectionCoords.toVec2())
}

fun currentShapeProcessRotation(currentShape: CurrentShape, elapsedTime: Float, cells: Array<Cell>) {
    val newRotation = currentShape.rotation + 1
    val newShapeIdx = shapeRotationIndex(currentShape.shape, newRotation)
    val kicks = checkOverTheEdge(currentShape.posCoords, newShapeIdx)

    if (kicks.x != 0f || kicks.y != 0f) {
        currentShapeMove(currentShape, currentShape.posCoords + kicks, elapsedTime)
    }

    currentShape.rotation = newRotation
    currentShape.rotationAnim.begin(elapsedTime, newRotation * 90f)
    currentShapeCheckOverlap(currentShape, cells)
}

fun currentShapeProcessMovement(
    currentShape: CurrentShape,
    layout: Layout,
    touch: Touch,
    elapsedTime: Float,
    cells: Array<Cell>
) {
    if (!currentShape.dragging && touch.isDown) {
        currentShape.dragging = true
    } else if (currentShape.dragging) {
        if (!touch.isDown) {
            currentShape.dragging = false
            currentShape.posCoordsPrev = currentShape.posCoords.copy()
        } else {
            val diff = touch.position - touch.startPosition
            val colsDiff = (diff / layout.cellSize) * DRAG_SENSITIVITY
            var newPosCoords = currentShape.posCoordsPrev + colsDiff

            val shapeIdx = shapeRotationIndex(currentShape.shape, currentShape.rotation)
            val kicks = checkOverTheEdge(newPosCoords, shapeIdx)
            newPosCoords += kicks

            currentShapeMove(currentShape, newPosCoords, elapsedTime)
            currentShapeCheckOverlap(currentShape, cells)
        }
    }

    currentShape.projectionAnim.update(elapsedTime)
    currentShape.rotationAnim.update(elapsedTime)
}

fun currentShapeCheckOverlap(currentShape: CurrentShape, cells: Array<Cell>) {
    currentShape.overlapping = false
    for (cellCoords in currentShapeCells(currentShape)) {
        val idx = coordsToIdx(cellCoords.col, cellCoords.row)
        assert(idx in cells.indices) { "idx=$idx, cells.size=${cells.size}" }
        if (cells[idx].filled) {
            currentShape.overlapping = true
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

fun currentShapeRender(currentShape: CurrentShape, layout: Layout, renderer: Renderer) {
    fun renderShape(rot: Int = 0) {
        val shapeIdx = shapeRotationIndex(currentShape.shape, rot)
        val cells = shapesMap[shapeIdx]

        for (cellOffset in cells) {
            val clr = if (currentShape.overlapping) {
                Color.OVERLAPPING
            } else {
                currentShape.color
            }

            val innerCellSize = layout.cellSize - layout.cellPadding * 2f

            val projectionCoords = cellOffset.toVec2() + currentShape.projectionAnim.current
            val projx = layout.pgPadding + projectionCoords.x * layout.cellSize
            val projy = layout.pgPadding + projectionCoords.y * layout.cellSize

            renderer.strokeRoundRect(
                projx + layout.cellPadding, projy + layout.cellPadding,
                innerCellSize, innerCellSize,
                layout.cellRadius,
                Color.addAlpha(150, clr),
                2f * layout.pixelDensity,
            )

            val cellCoords = cellOffset.toVec2() + currentShape.posCoords
            val cellx = layout.pgPadding + cellCoords.x * layout.cellSize
            val celly = layout.pgPadding + cellCoords.y * layout.cellSize

            renderer.drawRoundRect(
                cellx + layout.cellPadding, celly + layout.cellPadding,
                innerCellSize, innerCellSize,
                layout.cellRadius,
                clr,
            )
        }
    }

    // rotating
    if (currentShape.rotationAnim.animating) {
        // middle of the shape
        val pivotCoords = currentShape.posCoords + Vec2(SHAPE_CELLS_COUNT / 2f, SHAPE_CELLS_COUNT / 2f)
        val pivotPos = coordsToPos(layout, pivotCoords)

        renderer.save()
        renderer.rotate(
            currentShape.rotationAnim.current,
            pivotPos.x, pivotPos.y,
        )

        renderShape()

        renderer.restore()
    } else {
        renderShape(currentShape.rotation)
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

enum class CellExplosionState {
    None,
    Growing,
    Shrinking,
}

data class Cell(
    val idx: Int,
    var color: Int = 0,
    var filled: Boolean = false,
) {
    companion object {
        const val GROWING_DURATION = 0.2f
        const val SHRINKING_DURATION = 0.3f
    }

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

    val chargeColorAnim = Animation(0, GROWING_DURATION, lerp = ::lerpColor)
    val pulseAnim = Animation(0f, SHRINKING_DURATION, lerp = ::lerp)
    val rotationAnim = Animation(0f, SHRINKING_DURATION, lerp = ::lerp, easing = AnimationEasing.EaseOutSquared)

    var scoreReward = 10
}

fun cellBeginExplosion(cell: Cell, delay: Float, elapsedTime: Float) {
    cell.explosionState = CellExplosionState.Growing

    cell.chargeColorAnim.delay = delay
    cell.chargeColorAnim.current = cell.color
    cell.chargeColorAnim.duration = Cell.GROWING_DURATION
    cell.chargeColorAnim.easing = AnimationEasing.EaseInSquared
    cell.chargeColorAnim.begin(elapsedTime, Color.WHITE)

    cell.pulseAnim.current = 1f
    cell.pulseAnim.delay = delay
    cell.pulseAnim.animating = false
    cell.pulseAnim.easing = AnimationEasing.EaseInSquared
    cell.pulseAnim.duration = Cell.SHRINKING_DURATION
    cell.pulseAnim.begin(elapsedTime, 1.2f)
}

fun cellUpdateExplosion(cell: Cell, elapsedTime: Float): Boolean {
    assert(cell.explosionState != CellExplosionState.None) { "Explosion animation is not active" }

    cell.chargeColorAnim.update(elapsedTime)
    cell.pulseAnim.update(elapsedTime)

    when (cell.explosionState) {
        CellExplosionState.Growing -> {
            if (elapsedTime > cell.chargeColorAnim.animationStartTime + cell.chargeColorAnim.delay && cell.filled) {
                cell.filled = false
            }

            if (!cell.pulseAnim.animating && !cell.chargeColorAnim.animating) {
                cell.explosionState = CellExplosionState.Shrinking

                cell.chargeColorAnim.delay = 0f
                cell.chargeColorAnim.duration = Cell.SHRINKING_DURATION
                cell.chargeColorAnim.easing = AnimationEasing.EaseOutSquared
                cell.chargeColorAnim.begin(elapsedTime, Color.addAlpha(0, cell.chargeColorAnim.current))

                cell.pulseAnim.delay = 0f
                cell.pulseAnim.duration = Cell.SHRINKING_DURATION
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

sealed interface AnnouncerType {
    data object Single : AnnouncerType
    data object Double : AnnouncerType
    data object Triple : AnnouncerType
    data object Quadruple : AnnouncerType

    fun string(): String = when (this) {
        Single -> "Single"
        Double -> "Double"
        Triple -> "Triple"
        Quadruple -> "Quadruple"
    }
}

class Announcer(
    val multiplierTextSize: Float,
    val multiplierStrokeWidth: Float,
    val scoreTextSize: Float,
    val scoreTextStrokeWidth: Float,
) {
    sealed interface AnimState {
        data object None : AnimState
        data object Growing : AnimState
        data object Shrinking : AnimState
        data object Stable : AnimState
        data object Disappearing : AnimState
    }

    companion object {
        const val GROWING_DURATION = 0.1f
        const val SHRINKING_DURATION = 0.12f
        const val STABLE_DURATION = 0.5f
        const val DISAPPEARING_DURATION = 0.2f
    }

    var col: Int = 0
    var row: Int = 0
    var containerCenter = Vec2.default()
    var rotation = 0f

    var multiplierText: String = ""
    val multiplierTextPosition = Vec2.default()
    var multiplierTextWidth = 0f

    var scoreText: String = ""
    val scoreTextPosition = Vec2.default()
    var scoreTextWidth = 0f

    var score: Int = 0
    val scale = Animation(0f, 0f, lerp = ::lerp)
    val alpha = Animation(0, 0f, lerp = ::lerp)
    val rot = Animation(0f, 0f, lerp = ::lerp)
    var state: AnimState = AnimState.None
    var stableStartTime: Float = 0f
}

fun announcerAnnounce(
    announcer: Announcer,
    type: AnnouncerType,
    col: Int,
    row: Int,
    elapsedTime: Float,
) {
    announcer.state = Announcer.AnimState.Growing

    announcer.multiplierText = type.string()
    announcer.score = 100

    announcer.scale.current = 0.5f
    announcer.scale.duration = Announcer.GROWING_DURATION
    announcer.scale.easing = AnimationEasing.EaseOutSquared
    announcer.scale.begin(elapsedTime, 1.3f)

    announcer.rot.current = 0f
    announcer.rot.easing = AnimationEasing.EaseOutSquared
    announcer.rot.duration = Announcer.GROWING_DURATION

    announcer.col = col
    announcer.row = row
}

fun announcerAddScore(announcer: Announcer, score: Int, elapsedTime: Float) {
    if (announcer.state != Announcer.AnimState.Stable) {
        return
    }

    announcer.score += score
    announcer.stableStartTime = elapsedTime

    // grow by 2%
    announcer.scale.duration = Announcer.GROWING_DURATION
    announcer.scale.easing = AnimationEasing.EaseOutSquared
    announcer.scale.begin(elapsedTime, announcer.scale.current * 1.02f)

    // rotate
    var newRotation = Random.nextInt(0, 40)
    if (announcer.rotation < 0) {
        newRotation *= -1
    }

    if (announcer.rot.current == 0f) {
        announcer.rot.current = announcer.rotation
    }

    announcer.rot.begin(elapsedTime, newRotation.toFloat())
}

fun announcerUpdate(announcer: Announcer, layout: Layout, renderer: Renderer, elapsedTime: Float) {
    val spacing = 5 * layout.pixelDensity
    val height = announcer.multiplierTextSize + announcer.scoreTextSize + spacing
    var quadrantCenterY = 0f
    var quadrantCenterX = 0f

    if (announcer.row < CELLS_COUNT / 2 - 2) quadrantCenterY = layout.pgRect.height / 4f * 3f // lower half center
    else quadrantCenterY = layout.pgRect.height / 4f  // upper half center

    if (announcer.col <= CELLS_COUNT / 2) {
        quadrantCenterX = layout.pgRect.width / 4f * 3f // right half center
        announcer.rotation = 20f
    } else {
        quadrantCenterX = layout.pgRect.width / 4f // left half center
        announcer.rotation = -20f
    }

    val containerBottomY = quadrantCenterY + height / 2f
    val containerTopY = containerBottomY - height - spacing

    announcer.multiplierTextWidth =
        renderer.measureText(
            announcer.multiplierText,
            announcer.multiplierTextSize,
            FontWeight.Bold,
            FONT_MANROPE
        )
    announcer.multiplierTextPosition.x = quadrantCenterX - announcer.multiplierTextWidth / 2f
    announcer.multiplierTextPosition.y = containerTopY + announcer.multiplierTextSize

    announcer.scoreText = "%d".format(announcer.score)
    announcer.scoreTextWidth =
        renderer.measureText(announcer.scoreText, announcer.scoreTextSize, FontWeight.Bold, FONT_MANROPE)
    announcer.scoreTextPosition.x = quadrantCenterX - announcer.scoreTextWidth / 2f
    announcer.scoreTextPosition.y = containerBottomY

    announcer.containerCenter.x = quadrantCenterX
    announcer.containerCenter.y = quadrantCenterY - height / 2f

    // animations

    when (announcer.state) {
        Announcer.AnimState.Growing -> {
            announcer.scale.update(elapsedTime)

            if (!announcer.scale.animating) {
                announcer.state = Announcer.AnimState.Shrinking
                announcer.scale.duration = Announcer.SHRINKING_DURATION
                announcer.scale.easing = AnimationEasing.EaseInSquared
                announcer.scale.begin(elapsedTime, 1f)
            }
        }

        Announcer.AnimState.Shrinking -> {
            announcer.scale.update(elapsedTime)
            if (!announcer.scale.animating) {
                announcer.state = Announcer.AnimState.Stable
                announcer.stableStartTime = elapsedTime
            }
        }

        Announcer.AnimState.Stable -> {
            announcer.scale.update(elapsedTime)
            announcer.rot.update(elapsedTime)

            if (elapsedTime - announcer.stableStartTime >= Announcer.STABLE_DURATION) {
                announcer.state = Announcer.AnimState.Disappearing

                announcer.scale.duration = Announcer.DISAPPEARING_DURATION
                announcer.scale.easing = AnimationEasing.EaseOutSquared
                announcer.scale.begin(elapsedTime, 3f)

                announcer.alpha.current = 255
                announcer.alpha.duration = Announcer.DISAPPEARING_DURATION
                announcer.alpha.easing = AnimationEasing.EaseOutSquared
                announcer.alpha.begin(elapsedTime, 0)
            }
        }

        Announcer.AnimState.Disappearing -> {
            announcer.alpha.update(elapsedTime)
            announcer.scale.update(elapsedTime)

            if (!announcer.alpha.animating && !announcer.scale.animating) {
                announcer.state = Announcer.AnimState.None
            }
        }

        Announcer.AnimState.None -> Unit
    }
}

fun announcerRender(announcer: Announcer, renderer: Renderer) {
    fun renderTextWithStroke(
        renderer: Renderer,
        text: String,
        pos: Vec2,
        textSize: Float,
        strokeWidth: Float,
        fontWeight: FontWeight,
        font: String,
        clr: Int,
        strokeColor: Int,
    ) {
        renderer.drawText(text, pos.x, pos.y, clr, textSize, fontWeight, font)
        renderer.strokeText(text, pos.x, pos.y, strokeWidth, strokeColor, textSize, fontWeight, font)
    }

    fun render(clr: Int, strokeClr: Int) {
        renderer.save()
        renderer.scale(
            announcer.scale.current,
            announcer.scale.current,
            announcer.containerCenter.x,
            announcer.containerCenter.y,
        )

        val rot = if (announcer.rot.animating) {
            announcer.rot.current
        } else {
            announcer.rotation
        }

        renderer.rotate(
            rot,
            announcer.containerCenter.x,
            announcer.containerCenter.y,
        )

        val multPosition = announcer.multiplierTextPosition
        renderTextWithStroke(
            renderer,
            announcer.multiplierText,
            multPosition,
            announcer.multiplierTextSize,
            announcer.multiplierStrokeWidth,
            FontWeight.Bold,
            FONT_MANROPE,
            clr,
            strokeClr,
        )

        val scorePosition = announcer.scoreTextPosition
        renderTextWithStroke(
            renderer,
            announcer.scoreText,
            scorePosition,
            announcer.scoreTextSize,
            announcer.scoreTextStrokeWidth,
            FontWeight.Bold,
            FONT_MANROPE,
            clr,
            strokeClr,
        )

        renderer.restore()
    }

    when (announcer.state) {
        Announcer.AnimState.Growing,
        Announcer.AnimState.Shrinking,
        Announcer.AnimState.Stable -> {
            render(Color.WHITE, Color.BLACK)
        }

        Announcer.AnimState.Disappearing -> {
            render(
                Color.addAlpha(announcer.alpha.current, Color.WHITE),
                Color.addAlpha(announcer.alpha.current, Color.BLACK),
            )
        }

        Announcer.AnimState.None -> Unit
    }
}

class Board() {
    val cells = Array(CELLS_COUNT * CELLS_COUNT) { Cell(it) }
}

fun boardPlaceShape(board: Board, currentShape: CurrentShape, elapsedTime: Float): Int {
    var count = 0

    for (cellCoords in currentShapeCells(currentShape)) {
        val idx = coordsToIdx(cellCoords.col, cellCoords.row)
        val cell = board.cells[idx]

        cell.filled = true
        cell.filledAt = elapsedTime
        cell.color = currentShape.color

        count += 1
    }

    return count
}

data class ClearResult(
    val begin: Boolean,
    val count: Int,
    val firstCol: Int,
    val firstRow: Int
)

fun boardClearFilledCells(board: Board, elapsedTime: Float): ClearResult {
    data class Result(
        val fixedCoord: Int,
        var filled: Boolean = true,
        var start: Int = -1,
        var prevFillTime: Float = 0f,
        val coordsToIdx: (fixedCoord: Int, movingCoord: Int) -> Int,
    )

    fun resultProcess(result: Result, movingCoord: Int) {
        val idx = result.coordsToIdx(result.fixedCoord, movingCoord)
        val cell = board.cells[idx]

        if (!cell.filled) {
            result.filled = false
        }

        if (result.prevFillTime < cell.filledAt) {
            result.prevFillTime = cell.filledAt
            result.start = movingCoord
        }
    }

    fun resultBeginExplosion(result: Result, elapsedTime: Float, delay: Float, scoreMultiplier: Int) {
        // first
        run {
            val idx = result.coordsToIdx(result.fixedCoord, result.start)
            val cell = board.cells[idx]
            cell.filled = false
            cellBeginExplosion(cell, delay, elapsedTime)
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
                val left = board.cells[result.coordsToIdx(result.fixedCoord, preCoord)]
                left.scoreReward = scoreMultiplier * DEFAULT_CELL_CLEAR_REWARD

                if (left.explosionState == CellExplosionState.None) {
                    cellBeginExplosion(left, cellDelay, elapsedTime)
                }
            }

            if (postCoord < CELLS_COUNT) {
                val right = board.cells[result.coordsToIdx(result.fixedCoord, postCoord)]
                right.scoreReward = scoreMultiplier * DEFAULT_CELL_CLEAR_REWARD

                if (right.explosionState == CellExplosionState.None) {
                    cellBeginExplosion(right, cellDelay, elapsedTime)
                }
            }

            offset += 1
        }
    }

    var animating = false
    var scoreMultiplier = 1

    var count = 0
    var firstCol = -1
    var firstRow = -1
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

            if (rowResult.filled) resultProcess(rowResult, j)
            if (colResult.filled) resultProcess(colResult, j)
        }

        if (rowResult.filled) {
            if (firstRow == -1) {
                firstRow = i
                firstCol = rowResult.start
            }

            animating = true
            resultBeginExplosion(rowResult, elapsedTime, rowDelay, scoreMultiplier)

            count += 1
            scoreMultiplier += 1
            rowDelay += EXPLOSION_START_CLEAR_DELAY
        }

        if (colResult.filled) {
            if (firstCol == -1) {
                firstCol = i
                firstRow = colResult.start
            }

            animating = true
            resultBeginExplosion(colResult, elapsedTime, colDelay, scoreMultiplier)

            count += 1
            scoreMultiplier += 1
            colDelay += EXPLOSION_START_CLEAR_DELAY
        }
    }

    return ClearResult(
        animating,
        count,
        firstCol,
        firstRow
    )
}

fun boardUpdateClearingCells(board: Board, elapsedTime: Float): Pair<Int, Boolean> {
    var allDone = true
    var scoreReward = 0

    for (cellIdx in board.cells.indices) {
        val cell = board.cells[cellIdx]

        if (cell.explosionState != CellExplosionState.None) {
            if (cellUpdateExplosion(cell, elapsedTime)) {
                scoreReward += cell.scoreReward

            }

            if (cell.explosionState == CellExplosionState.None) {
                cell.scoreReward = DEFAULT_CELL_CLEAR_REWARD
            } else {
                allDone = false
            }
        }
    }

    return Pair(scoreReward, allDone)
}

fun boardRender(board: Board, layout: Layout, renderer: Renderer) {
    renderer.drawRoundRect(layout.pgRect, RADIUS * layout.pixelDensity, Color.BLACK)

    for ((cellIdx, cell) in board.cells.withIndex()) {
        if (cell.filled) {
            val col = (cellIdx % CELLS_COUNT).toFloat()
            val row = (cellIdx / CELLS_COUNT).toFloat()

            val x = layout.pgPadding + col * layout.cellSize
            val y = layout.pgPadding + row * layout.cellSize

            renderer.drawRoundRect(
                x + layout.cellPadding, y + layout.cellPadding,
                layout.cellSize - layout.cellPadding * 2,
                layout.cellSize - layout.cellPadding * 2,
                layout.cellRadius,
                cell.color,
            )
        }
    }
}

fun boardRenderDisappearingCells(board: Board, layout: Layout, renderer: Renderer) {
    val scratchCoords = Coords(0, 0)

    for (idx in 0..<CELLS_COUNT * CELLS_COUNT) {
        val cell = board.cells[idx]

        if (cell.explosionState != CellExplosionState.None) {
            scratchCoords.col = idx % CELLS_COUNT
            scratchCoords.row = idx / CELLS_COUNT

            // cell center
            var cellCenter = coordsToPos(layout, scratchCoords.toVec2())
            cellCenter += layout.cellSize / 2

            if (cell.rotationAnim.animating) {
                val rotation = cell.rotationAnim.current
                renderer.save()
                renderer.rotate(rotation, cellCenter.x, cellCenter.y)
            }

            // scaled
            if (cell.pulseAnim.current > 0f) {
                val scaledCellSize = (layout.cellSize - layout.cellPadding * 2) * cell.pulseAnim.current
                val cellx = cellCenter.x - scaledCellSize / 2
                val celly = cellCenter.y - scaledCellSize / 2
                renderer.drawRoundRect(
                    cellx, celly, scaledCellSize, scaledCellSize,
                    layout.cellRadius,
                    cell.chargeColorAnim.current,
                )
            }

            if (cell.rotationAnim.animating) {
                renderer.restore()
            }
        }
    }
}

data class Layout(
    val pixelDensity: Float,
    val scaledDensity: Float,
) {
    var pgRect = Rect()
    var pgPadding = 0f
    var cellSize = 0f
    var cellPadding = 0f
    var cellRadius = 0f
}

fun layoutUpdate(layout: Layout, containerWidth: Float, containerHeight: Float) {
    layout.pgRect = Rect(0f, 0f, containerWidth, containerHeight)
    layout.pgPadding = containerWidth * PLAYGROUND_PADDING_FRACTION
    layout.cellSize = (containerWidth - layout.pgPadding * 2) / CELLS_COUNT
    layout.cellPadding = layout.cellSize * CELL_PADDING_FRACTION
    layout.cellRadius = layout.cellSize * CELL_RADIUS_FRACTION
}

fun coordsToPos(layout: Layout, coords: Coords): Vec2 {
    return coordsToPos(layout, coords.toVec2())
}

fun coordsToPos(layout: Layout, coords: Vec2): Vec2 {
    val x = coords.x * layout.cellSize + layout.pgPadding
    val y = coords.y * layout.cellSize + layout.pgPadding
    return Vec2(x, y)
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

    val layout = Layout(pixelDensity, scaledDensity)

    var state: GameState = GameState.Countdown
    val board = Board()
    val shapesBag = ShapesBag()
    var currentShape = CurrentShape()
    val countdown = Countdown(30f * scaledDensity, 60f * scaledDensity)
    var shapesPlaced = 0
    var roundDuration = 5f
    var score = 0
    val announcer = Announcer(48f * scaledDensity, 1f * scaledDensity, 32f * scaledDensity, 1f * scaledDensity)
}

fun gameIncreaseScore(game: GameContext, amount: Int) {
    game.score += amount
    game.onScoreChange?.invoke(game.score)
}

fun gamePlaceShapeAndClear(game: GameContext, forced: Boolean): Boolean {
    var cellCount = boardPlaceShape(game.board, game.currentShape, game.elapsedTime)

    if (forced) {
        cellCount *= -1
    }

    gameIncreaseScore(game, cellCount)

    game.shapesPlaced += 1
    game.onPlaceShape?.invoke()
    game.currentShape.shape = -1

    val result = boardClearFilledCells(game.board, game.elapsedTime)

    if (result.begin) {
        game.state = GameState.AnimatingCellsExplosion
        val type = when (result.count) {
            1 -> AnnouncerType.Single
            2 -> AnnouncerType.Double
            3 -> AnnouncerType.Triple
            else -> AnnouncerType.Quadruple
        }
        announcerAnnounce(
            game.announcer,
            type,
            result.firstCol,
            result.firstRow,
            game.elapsedTime
        )
    }

    return result.begin
}

fun gameForcePlaceShape(game: GameContext) {
    Trace.beginSection("forcePlace")

    val availableCoords = currentShapeAvailableCoords(game.currentShape, game.board.cells)
    if (availableCoords == null) {
        game.onGameOver?.invoke()
        game.state = GameState.GameOver
        Trace.endSection()
        return
    }

    Trace.endSection()

    var forced = false

    if (availableCoords != game.currentShape.projectionCoords) {
        game.currentShape.projectionCoords = availableCoords
        game.currentShape.projectionAnim.begin(game.elapsedTime, availableCoords.toVec2())
        forced = true
    }

    if (!game.currentShape.projectionAnim.animating) {
        gamePlaceShapeAndClear(game, forced)
    } else {
        game.state = GameState.AnimatingCurrentShape(true)
    }
}

fun coordsToIdx(col: Int, row: Int): Int {
    return col + row * CELLS_COUNT
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

fun gameUpdate(game: GameContext, touch: Touch) {
    // layout

    if (game.changedWidth > -1f || game.changedHeight > -1) {
        layoutUpdate(game.layout, game.changedWidth, game.changedHeight)
        game.changedWidth = -1f
        game.changedHeight = -1f
    }


    val gameState = game.state

    // countdown - independent

    if (gameState == GameState.Countdown) {
        if (shapes.BuildConfig.DEBUG) {
            game.state = GameState.Placing
        } else {
            if (countdownUpdate(game.countdown, game.layout, game.renderer, game.elapsedTime)) {
                game.state = GameState.Placing
            }
        }
    }

    // current shape

    if (gameState == GameState.Placing) {
        if (game.currentShape.shape == -1) {
            val shapeIdx = game.shapesBag.next()
            val (gameOver, newShape) = currentShapeSpawn(shapeIdx, game.board, game.elapsedTime)

            if (gameOver) {
                game.onGameOver?.invoke()
                game.state = GameState.GameOver
                return
            }

            game.currentShape = newShape

            val nextShapeIdx = game.shapesBag.peek()
            game.roundDuration = currentRoundDuration(game.shapesPlaced)
            game.onRoundStart?.invoke(nextShapeIdx, game.roundDuration)

            game.pendingRotation = false
            game.pendingPlacement = false
        } else {
            if (game.currentShape.createdAt + game.roundDuration < game.elapsedTime) {
                gameForcePlaceShape(game)
            } else {
                // process inputs

                if (game.pendingRotation) {
                    currentShapeProcessRotation(game.currentShape, game.elapsedTime, game.board.cells)
                    game.pendingRotation = false
                }

                var placed = false
                if (game.pendingPlacement) {
                    if (!game.currentShape.overlapping) {
                        if (!game.currentShape.projectionAnim.animating) {
                            gamePlaceShapeAndClear(game, false)
                        } else {
                            game.state = GameState.AnimatingCurrentShape(false)
                        }

                        placed = true
                    }
                    game.pendingPlacement = false
                }

                if (!placed) {
                    currentShapeProcessMovement(
                        game.currentShape,
                        game.layout,
                        touch,
                        game.elapsedTime,
                        game.board.cells,
                    )
                }
            }
        }
    } else if (gameState is GameState.AnimatingCurrentShape) {
        // shape dragging
        game.currentShape.projectionAnim.update(game.elapsedTime)

        if (!game.currentShape.projectionAnim.animating) {
            if (!gamePlaceShapeAndClear(game, gameState.forced)) {
                game.state = GameState.Placing
            }
        }
    }

    // cells clearing animation

    if (gameState == GameState.AnimatingCellsExplosion) {
        val (scoreReward, allDone) = boardUpdateClearingCells(game.board, game.elapsedTime)

        if (scoreReward > 0) {
            announcerAddScore(game.announcer, scoreReward, game.elapsedTime)
            gameIncreaseScore(game, scoreReward)
        }
    }

    // announcer

    if (game.announcer.state != Announcer.AnimState.None) {
        announcerUpdate(game.announcer, game.layout, game.renderer, game.elapsedTime)
        if (game.announcer.state == Announcer.AnimState.None) {
            game.state = GameState.Placing
        }
    }
}

fun gameRender(game: GameContext) {
    boardRender(game.board, game.layout, game.renderer)

    if (game.state == GameState.Countdown) {
        countdownRender(game.countdown, game.renderer)
    }

    if (game.state == GameState.AnimatingCellsExplosion) {
        boardRenderDisappearingCells(game.board, game.layout, game.renderer)
    }

    if (game.state == GameState.Placing || game.state is GameState.AnimatingCurrentShape) {
        assert(game.currentShape.shape < shapesMap.size)
        if (game.currentShape.shape != -1) {
            currentShapeRender(game.currentShape, game.layout, game.renderer)
        }
    }

    if (game.announcer.state != Announcer.AnimState.None) {
        announcerRender(game.announcer, game.renderer)
    }
}
