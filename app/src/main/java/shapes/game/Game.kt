package shapes.game

import kotlin.math.pow
import kotlin.random.Random

const val FONT_MANROPE = "manrope"
const val FONT_DMMONO = "dmmono"
const val FONT_SUPPLY_CENTER = "supplycenter"

const val RADIUS = 8f
const val CELLS_COUNT = 12
const val SHAPE_CELLS_COUNT = 4
const val CELL_PADDING_FRACTION = 0.075f
const val CELL_RADIUS_FRACTION = 0.25f
const val PLAYGROUND_PADDING_FRACTION = 0.04f
const val DRAG_SENSITIVITY = 1.75f
const val SHAPE_MOVEMENT_ANIMATION_DURATION = 0.065f

const val CELL_CLEAR_REWARD = 10

object Color {
    const val black = 0xff000000.toInt()
    const val white = 0xffffffff.toInt()

    const val ink = 0xff182622.toInt()
    const val vanilla = 0xfff4eddd.toInt()

    const val blue = 0xff65bed0.toInt()
    const val red = 0xfff25b43.toInt()
    const val lime = 0xffbadd67.toInt()
    const val yellow = 0xfff2c94c.toInt()
    const val purple = 0xff8069b2.toInt()

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

    fun a(argb: Int): Int = (argb shr 24) and 0xff
    fun r(argb: Int): Int = (argb shr 16) and 0xff
    fun g(argb: Int): Int = (argb shr 8) and 0xff
    fun b(argb: Int): Int = argb and 0xff
}

val colors: Array<Int> = arrayOf(
    Color.blue,
    Color.lime,
    Color.yellow,
    Color.purple,
)

fun measureText(text: String, textSize: Float): Float {
    return Platform.renderer.measureText(text, textSize, FontWeight.Regular, FONT_SUPPLY_CENTER)
}

fun drawText(text: String, x: Float, y: Float, color: Int, textSize: Float) {
    Platform.renderer.drawText(text, x, y, color, textSize, FontWeight.Regular, FONT_SUPPLY_CENTER)
}

fun strokeText(text: String, x: Float, y: Float, strokeWidth: Float, color: Int, textSize: Float) {
    Platform.renderer.strokeText(text, x, y, strokeWidth, color, textSize, FontWeight.Regular, FONT_SUPPLY_CENTER)
}

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

fun countdownUpdate(countdown: Countdown, layout: Layout, elapsedTime: Float): Boolean {
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

    val textWidth = measureText(countdown.text, countdown.textSize)
    countdown.textX = layout.pgRect.x + (layout.pgRect.width - textWidth) / 2
    countdown.textY = layout.pgRect.y + countdown.textSize + (layout.pgRect.height - countdown.textSize) / 2

    return false
}

fun countdownRender(countdown: Countdown) {
    val color = Color.argb((countdown.opacity * 255).toInt(), 255, 255, 255)
    drawText(
        countdown.text,
        countdown.textX,
        countdown.textY,
        color,
        countdown.textSize,
    )
}

class CurrentShape(var shape: Shape, val createdAt: Float = 0f) {
    companion object {
        val DEFAULT_COORDS = Coords(CELLS_COUNT / 2 - SHAPE_CELLS_COUNT / 2, CELLS_COUNT / 2 - SHAPE_CELLS_COUNT / 2)
    }

    var initialized = false
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
        val cells = shapeOffsets(currentShape.shape, currentShape.rotation)
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
    newShape: Shape,
    board: Board,
    elapsedTime: Float,
): Pair<Boolean, CurrentShape> {
    var gameOver = false

    Platform.trace.beginSection("gameSpawnShape")

    val newShape = CurrentShape(newShape, elapsedTime)
    newShape.initialized = true

    var availableCoords = currentShapeAvailableCoords(newShape, board.cells, 0)
    if (availableCoords == null) availableCoords =
        currentShapeAvailableCoords(newShape, board.cells, 1)
    if (availableCoords == null) availableCoords =
        currentShapeAvailableCoords(newShape, board.cells, 2)
    if (availableCoords == null) availableCoords =
        currentShapeAvailableCoords(newShape, board.cells, 3)

    Platform.trace.endSection()

    if (availableCoords == null) {
        gameOver = true
    } else {
        currentShapeCheckOverlap(newShape, board.cells)
    }

    return Pair(gameOver, newShape)
}

fun checkOverTheEdge(newPosCoords: Vec2, shapeOffsets: Array<Coords>): Vec2 {
    var minCol = Float.MAX_VALUE
    var maxCol = Float.MIN_VALUE
    var minRow = Float.MAX_VALUE
    var maxRow = Float.MIN_VALUE

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
    val newShapeOffsets = shapeOffsets(currentShape.shape, newRotation)
    val kicks = checkOverTheEdge(currentShape.posCoords, newShapeOffsets)

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

            val kicks = checkOverTheEdge(newPosCoords, shapeOffsets(currentShape.shape, currentShape.rotation))
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
    Platform.trace.beginSection("availableCoords")

    val shapeOffsets = shapeOffsets(currentShape.shape, if (rot == -1) currentShape.rotation else rot)

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
        for (cellOffset in shapeOffsets) {
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

    Platform.trace.endSection()
    return resultCoords
}

fun currentShapeRender(currentShape: CurrentShape, layout: Layout) {
    fun renderShape(rot: Int = 0) {
        val cells = shapeOffsets(currentShape.shape, rot)

        for (cellOffset in cells) {
            val clr = if (currentShape.overlapping) {
                Color.addAlpha(200, Color.red)
            } else {
                currentShape.color
            }

            val innerCellSize = layout.cellSize - layout.cellPadding * 2f

            val projectionCoords = cellOffset.toVec2() + currentShape.projectionAnim.current
            val projx = layout.pgPadding + projectionCoords.x * layout.cellSize
            val projy = layout.pgPadding + projectionCoords.y * layout.cellSize

            Platform.renderer.strokeRoundRect(
                projx + layout.cellPadding, projy + layout.cellPadding,
                innerCellSize, innerCellSize,
                layout.cellRadius,
                Color.addAlpha(150, clr),
                2f * layout.pixelDensity,
            )

            val cellCoords = cellOffset.toVec2() + currentShape.posCoords
            val cellx = layout.pgPadding + cellCoords.x * layout.cellSize
            val celly = layout.pgPadding + cellCoords.y * layout.cellSize

            Platform.renderer.drawRoundRect(
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

        Platform.renderer.save()
        Platform.renderer.rotate(
            currentShape.rotationAnim.current,
            pivotPos.x, pivotPos.y,
        )

        renderShape()

        Platform.renderer.restore()
    } else {
        renderShape(currentShape.rotation)
    }
}

class Announcer(
    val textSize: Float,
    val textStrokeWidth: Float,
    val scaledDensity: Float,
) {
    enum class State {
        None,
        Growing,
        Shrinking,
        Disappearing,
    }

    companion object {
        // total = 0.1 + 0.12 + 0.5 + 0.2 = 0.92
        const val GROWING_DURATION = 0.1f
        const val SHRINKING_DURATION = 0.12f
        const val STABLE_DURATION = 0.5f
        const val DISAPPEARING_DURATION = 0.1f
    }

    var col: Int = 0
    var row: Int = 0
    var containerCenter = Vec2.default()
    var rotation = 0f

    var text: String = ""
    val textPosition = Vec2.default()
    var textWidth = 0f

    val scale = Animation(0f, 0f, lerp = ::lerp)
    val alpha = Animation(0, 0f, lerp = ::lerp)
    val rot = Animation(0f, 0f, lerp = ::lerp)
    var state: State = State.None
    var stableStartTime: Float = 0f
}

fun announcerAnnounce(
    announcer: Announcer,
    text: String,
    col: Int,
    row: Int,
    elapsedTime: Float,
) {
    announcer.state = Announcer.State.Growing
    announcer.text = text

    announcer.alpha.delay = 0f
    announcer.scale.delay = 0f

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

fun announcerUpdate(announcer: Announcer, layout: Layout, elapsedTime: Float) {
    val height = announcer.textSize

    var quadrantCenterY = layout.pgRect.height / 4f  // upper half center
    var quadrantCenterX = 0f

    if (announcer.col <= CELLS_COUNT / 2) {
        quadrantCenterX = layout.pgRect.width / 4f * 3f // right half center
        announcer.rotation = 20f
    } else {
        quadrantCenterX = layout.pgRect.width / 4f // left half center
        announcer.rotation = -20f
    }

    val containerBottomY = quadrantCenterY + height / 2f
    val containerTopY = containerBottomY - height

    announcer.textWidth = measureText(announcer.text, announcer.textSize)
    announcer.textPosition.x = quadrantCenterX - announcer.textWidth / 2f
    announcer.textPosition.y = containerTopY + announcer.textSize

    announcer.containerCenter.x = quadrantCenterX
    announcer.containerCenter.y = quadrantCenterY - height / 2f

    // animations

    when (announcer.state) {
        Announcer.State.Growing -> {
            announcer.scale.update(elapsedTime)

            if (!announcer.scale.animating) {
                announcer.state = Announcer.State.Shrinking
                announcer.scale.duration = Announcer.SHRINKING_DURATION
                announcer.scale.easing = AnimationEasing.EaseInSquared
                announcer.scale.begin(elapsedTime, 1f)
            }
        }

        Announcer.State.Shrinking -> {
            announcer.scale.update(elapsedTime)

            if (!announcer.scale.animating) {
                announcer.state = Announcer.State.Disappearing

                announcer.scale.delay = Announcer.STABLE_DURATION
                announcer.scale.duration = Announcer.DISAPPEARING_DURATION
                announcer.scale.easing = AnimationEasing.EaseOutSquared
                announcer.scale.begin(elapsedTime, 3f)

                announcer.alpha.delay = Announcer.STABLE_DURATION
                announcer.alpha.current = 255
                announcer.alpha.duration = Announcer.DISAPPEARING_DURATION
                announcer.alpha.easing = AnimationEasing.EaseOutSquared
                announcer.alpha.begin(elapsedTime, 0)
            }
        }

        Announcer.State.Disappearing -> {
            announcer.alpha.update(elapsedTime)
            announcer.scale.update(elapsedTime)

            if (!announcer.alpha.animating && !announcer.scale.animating) {
                announcer.state = Announcer.State.None
            }
        }

        Announcer.State.None -> Unit
    }
}

fun announcerRender(announcer: Announcer) {
    fun renderTextWithStroke(
        text: String,
        pos: Vec2,
        textSize: Float,
        strokeWidth: Float,
        alpha: Int,
        clr: Int,
        strokeColor: Int,
    ) {
        val shadowx = pos.x + 4 * announcer.scaledDensity
        val shadowy = pos.y + 4 * announcer.scaledDensity
        drawText(text, shadowx, shadowy, Color.addAlpha(alpha, Color.ink), textSize)

        drawText(text, pos.x, pos.y, Color.addAlpha(alpha, clr), textSize)
        strokeText(text, pos.x, pos.y, strokeWidth, Color.addAlpha(alpha, strokeColor), textSize)
    }

    fun render(alpha: Int, clr: Int, strokeClr: Int) {
        Platform.renderer.save()
        Platform.renderer.scale(
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

        Platform.renderer.rotate(
            rot,
            announcer.containerCenter.x,
            announcer.containerCenter.y,
        )

        val pos = announcer.textPosition
        renderTextWithStroke(
            announcer.text,
            pos,
            announcer.textSize,
            announcer.textStrokeWidth,
            alpha,
            clr,
            strokeClr,
        )

        Platform.renderer.restore()
    }

    when (announcer.state) {
        Announcer.State.Growing,
        Announcer.State.Shrinking -> {
            render(255, Color.white, Color.black)
        }

        Announcer.State.Disappearing -> {
            render(announcer.alpha.current, Color.white, Color.black)
        }

        Announcer.State.None -> Unit
    }
}

enum class LineClearPhase {
    None,
    PopGrow,
    PopShrink,
    FadeOut,
}

class LineClear(var row: Boolean) {
    companion object {
        const val LINE_POP_STAGGER = 0.2f
        const val LINE_GROWING_DURATION = 0.1f
        const val LINE_SHRINKING_DURATION = 0.3f

        // max -> 12 cells, 11 * CELL_FADE_OUT_STAGGER = 0.55f
        const val CELL_FADE_OUT_STAGGER = 0.05f
        const val CELL_FADE_OUT_DURATION = 0.3f
    }

    class Cell {
        var progress = Anim()
        var endRotation = 0f
        var endOffset = 0f
    }

    var cellAnchor = -1
    var phase: LineClearPhase = LineClearPhase.None
    var progress = Anim()
    var popDelay = 0f
    var fadeOutDelay = 0f
    val cells = Array(CELLS_COUNT) { Cell() }
}

fun lineClearBegin(
    lineClear: LineClear,
    cellAnchor: Int,
    popDelay: Float,
    fadeOutDelay: Float,
    elapsedTime: Float,
) {
    lineClear.phase = LineClearPhase.PopGrow
    lineClear.popDelay = popDelay
    lineClear.fadeOutDelay = fadeOutDelay
    lineClear.cellAnchor = cellAnchor

    animBegin(lineClear.progress, LineClear.LINE_GROWING_DURATION, elapsedTime, lineClear.popDelay)
}

data class LineClearUpdateResult(
    var popped: Boolean,
    var cellsFadedOut: Int,
)

fun lineClearUpdate(lineClear: LineClear, elapsedTime: Float): LineClearUpdateResult {
    val result = LineClearUpdateResult(popped = false, cellsFadedOut = 0)

    when (lineClear.phase) {
        LineClearPhase.PopGrow -> {
            if (!animUpdate(lineClear.progress, elapsedTime)) {
                lineClear.phase = LineClearPhase.PopShrink
                animBegin(lineClear.progress, LineClear.LINE_SHRINKING_DURATION, elapsedTime, 0f)
                result.popped = true
            }
        }

        LineClearPhase.PopShrink -> {
            if (!animUpdate(lineClear.progress, elapsedTime)) {
                lineClear.phase = LineClearPhase.FadeOut

                assert(lineClear.cellAnchor != -1)

                fun cellBegin(cell: LineClear.Cell, endOffset: Float, endRotation: Float, delay: Float) {
                    cell.endOffset = endOffset
                    cell.endRotation = endRotation
                    animBegin(cell.progress, LineClear.CELL_FADE_OUT_DURATION, elapsedTime, delay)
                }

                cellBegin(lineClear.cells[lineClear.cellAnchor], 0f, 0f, lineClear.fadeOutDelay)
                result.cellsFadedOut += 1

                var offset = 1
                while (true) {
                    val prevCellPos = lineClear.cellAnchor - offset
                    val postCellPos = lineClear.cellAnchor + offset

                    if (prevCellPos < 0 && postCellPos >= CELLS_COUNT) {
                        break
                    }

                    val delay = offset.toFloat() * LineClear.CELL_FADE_OUT_STAGGER + lineClear.fadeOutDelay
                    val endOffset = offset * 10f
                    val endRotation = offset * 5f

                    if (prevCellPos >= 0) {
                        cellBegin(lineClear.cells[prevCellPos], endOffset * -1, endRotation * -1, delay)
                    }
                    if (postCellPos < CELLS_COUNT) {
                        cellBegin(lineClear.cells[postCellPos], endOffset, endRotation, delay)
                    }

                    offset += 1
                }
            }
        }

        LineClearPhase.FadeOut -> {
            var allDone = true

            for (cell in lineClear.cells) {
                if (!cell.progress.running) {
                    continue
                }

                val preInDelay = cell.progress.state == AnimState.Delay

                if (animUpdate(cell.progress, elapsedTime)) {
                    allDone = false
                    val postInDelay = cell.progress.state == AnimState.Delay
                    if (preInDelay && !postInDelay) {
                        result.cellsFadedOut += 1
                    }
                }
            }

            if (allDone) {
                lineClear.phase = LineClearPhase.None
            }
        }

        LineClearPhase.None -> Unit
    }

    return result
}

fun lineClearRender(lineClear: LineClear, layout: Layout, lineCoord: Int, board: Board) {
    fun cellIdx(cellCoord: Int, cellCoords: Coords): Int {
        return if (lineClear.row) {
            cellCoords.col = cellCoord
            cellCoords.row = lineCoord

            lineCoord * CELLS_COUNT + cellCoord
        } else {
            cellCoords.col = lineCoord
            cellCoords.row = cellCoord

            cellCoord * CELLS_COUNT + lineCoord
        }
    }

    fun renderCell(cellCoords: Coords, scale: Float, rotation: Float, offset: Vec2, color: Int) {
        val cellPos = coordsToPos(layout, cellCoords)
        val cellCenter = cellPos + layout.cellSize / 2f

        if (rotation != 0f) {
            Platform.renderer.save()
            Platform.renderer.translate(offset.x, offset.y)
            Platform.renderer.rotate(rotation, cellCenter.x, cellCenter.y)
        }

        Platform.renderer.save()
        Platform.renderer.scale(scale, scale, cellCenter.x, cellCenter.y)

        val innerCellSize = layout.cellSize - layout.cellPadding * 2
        Platform.renderer.drawRoundRect(
            cellCenter.x - innerCellSize / 2f,
            cellCenter.y - innerCellSize / 2f,
            innerCellSize,
            innerCellSize,
            layout.cellRadius,
            color
        )

        if (rotation != 0f) {
            Platform.renderer.restore()
        }

        Platform.renderer.restore()
    }

    if (lineClear.phase == LineClearPhase.PopGrow || lineClear.phase == LineClearPhase.PopShrink) {
        val reversed = lineClear.phase == LineClearPhase.PopShrink
        val scale = animCurrent(
            lineClear.progress,
            1f, 1.15f,
            ::lerp, AnimationEasing.EaseInSquared,
            reversed,
        )
        val scratchCoords = Coords(0, 0)

        for (cellCoord in 0..<CELLS_COUNT) {
            val idx = cellIdx(cellCoord, scratchCoords)
            val cell = board.cells[idx]
            val color = animCurrent(
                lineClear.progress,
                cell.color, Color.white,
                ::lerpColor, AnimationEasing.EaseInSquared,
                reversed,
            )
            renderCell(scratchCoords, scale, 0f, Vec2(0f, 0f), color)
        }
    }

    if (lineClear.phase == LineClearPhase.FadeOut) {
        val scratchCoords = Coords(0, 0)

        for ((cellCoord, cell) in lineClear.cells.withIndex()) {
            val easing = AnimationEasing.EaseOutSquared

            val scale = animCurrent(cell.progress, 1f, 0.5f, ::lerp, easing)
            val alpha = animCurrent(cell.progress, 255, 0, ::lerp, easing)
            val rotation = animCurrent(cell.progress, 0f, cell.endRotation, ::lerp, easing)

            val offsetScalar = animCurrent(cell.progress, 0f, cell.endOffset, ::lerp, easing)
            val offset = if (lineClear.row) {
                Vec2(offsetScalar, 0f)
            } else {
                Vec2(0f, offsetScalar)
            }

            val idx = cellIdx(cellCoord, scratchCoords)
            val cell = board.cells[idx]
            val color = Color.addAlpha(alpha, cell.color)

            renderCell(scratchCoords, scale, rotation, offset, color)
        }
    }
}

class Cell(val idx: Int) {
    var color: Int = 0
    var filled: Boolean = false
    var filledAt = 0f
}

class Board {
    val cells = Array(CELLS_COUNT * CELLS_COUNT) { Cell(it) }
    val rowsClears = Array(CELLS_COUNT) { LineClear(true) }
    val colsClears = Array(CELLS_COUNT) { LineClear(false) }
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

fun boardClearFilledCells(board: Board, elapsedTime: Float): Int {
    // detect filled

    val filledRows = IntArray(CELLS_COUNT) { -1 }
    val filledCols = IntArray(CELLS_COUNT) { -1 }
    var linesCount = 0

    for (row in 0..<CELLS_COUNT) {
        var anchor = -1
        var prevFilledAt = -1f

        for (col in 0..<CELLS_COUNT) {
            val idx = row * CELLS_COUNT + col
            val cell = board.cells[idx]
            if (!cell.filled) {
                anchor = -1
                break
            }

            if (cell.filledAt > prevFilledAt) {
                anchor = col
                prevFilledAt = cell.filledAt
            }
        }

        if (anchor > -1) {
            filledRows[row] = anchor
            linesCount += 1
        }
    }

    for (col in 0..<CELLS_COUNT) {
        var anchor = -1
        var prevFilledAt = -1f

        for (row in 0..<CELLS_COUNT) {
            val idx = row * CELLS_COUNT + col
            val cell = board.cells[idx]
            if (!cell.filled) {
                anchor = -1
                break
            }

            if (cell.filledAt > prevFilledAt) {
                anchor = row
                prevFilledAt = cell.filledAt
            }
        }

        if (anchor > -1) {
            filledCols[col] = anchor
            linesCount += 1
        }
    }

    // begin pop

    // time for line pop
    // DELAY (0.2) + GROW (0.1) + SHRINK (0.3)

    fun fadeOutDelay(idx: Int): Float {
        val delay = (linesCount - 1 - idx) * LineClear.LINE_POP_STAGGER
        val stagger = idx * LineClear.LINE_POP_STAGGER
        return delay + stagger
    }

    var animating = false
    var rowIdx = 0

    // rows
    //
    // Pop:
    // 1st row = grow -> shrink
    // 2nd row = wait for 1st row to finish -> grow -> shrink
    // 3rd row = wait for 2nd row to finish -> grow -> shrink
    //
    // Fade out:
    // 1st row = wait for 3rd row to finish shrink -> fade out
    // 2nd row = wait for 3rd row to finish shrink -> wait LINE_POP_STAGGER -> fade out
    // 3rd row = wait for 3rd row to finish shrink -> wait LINE_POP_STAGGER * 2 -> fade out

    for ((row, anchorCol) in filledRows.withIndex()) {
        if (anchorCol > -1) {
            animating = true

            val popDelay = rowIdx * LineClear.LINE_POP_STAGGER
            val fadeOutDelay = fadeOutDelay(rowIdx)
            lineClearBegin(board.rowsClears[row], anchorCol, popDelay, fadeOutDelay, elapsedTime)

            rowIdx += 1

            // clear cells
            for (col in 0..<CELLS_COUNT) {
                val idx = row * CELLS_COUNT + col
                board.cells[idx].filled = false
            }
        }
    }

    // cols
    var colIdx = rowIdx

    for ((col, anchorRow) in filledCols.withIndex()) {
        if (anchorRow > -1) {
            animating = true

            val popDelay = colIdx * LineClear.LINE_POP_STAGGER
            val fadeOutDelay = fadeOutDelay(colIdx)
            lineClearBegin(board.colsClears[col], anchorRow, popDelay, fadeOutDelay, elapsedTime)

            colIdx += 1

            // clear cells
            for (row in 0..<CELLS_COUNT) {
                val idx = row * CELLS_COUNT + col
                board.cells[idx].filled = false
            }
        }
    }

    return linesCount
}

data class BoardUpdateResult(
    var allDone: Boolean,
    var linePopped: Boolean,
    var cellsFadedOut: Int,
)

fun boardUpdateClearingCells(board: Board, elapsedTime: Float): BoardUpdateResult {
    val result = BoardUpdateResult(
        allDone = true,
        linePopped = false,
        cellsFadedOut = 0,
    )

    for (row in board.rowsClears) {
        if (row.phase != LineClearPhase.None) {
            val lineResult = lineClearUpdate(row, elapsedTime)
            if (!result.linePopped) {
                result.linePopped = lineResult.popped
            }
            if (lineResult.cellsFadedOut > 0) {
                result.cellsFadedOut += lineResult.cellsFadedOut
            }
            if (row.phase != LineClearPhase.None) {
                result.allDone = false
            }
        }
    }

    for (col in board.colsClears) {
        if (col.phase != LineClearPhase.None) {
            val lineResult = lineClearUpdate(col, elapsedTime)
            if (!result.linePopped) {
                result.linePopped = lineResult.popped
            }
            if (lineResult.cellsFadedOut > 0) {
                result.cellsFadedOut += lineResult.cellsFadedOut
            }
            if (col.phase != LineClearPhase.None) {
                result.allDone = false
            }
        }
    }

    return result
}

fun boardRender(board: Board, layout: Layout) {
    Platform.renderer.drawRoundRect(layout.pgRect, 24 * layout.pixelDensity, Color.ink)

    for ((cellIdx, cell) in board.cells.withIndex()) {
        if (cell.filled) {
            val col = (cellIdx % CELLS_COUNT).toFloat()
            val row = (cellIdx / CELLS_COUNT).toFloat()

            val x = layout.pgPadding + col * layout.cellSize
            val y = layout.pgPadding + row * layout.cellSize

            Platform.renderer.drawRoundRect(
                x + layout.cellPadding, y + layout.cellPadding,
                layout.cellSize - layout.cellPadding * 2,
                layout.cellSize - layout.cellPadding * 2,
                layout.cellRadius,
                cell.color,
            )
        }
    }
}

fun boardRenderClearingAnimation(board: Board, layout: Layout) {
    for (row in 0..<CELLS_COUNT) {
        val rowLine = board.rowsClears[row]
        if (rowLine.phase != LineClearPhase.None) {
            lineClearRender(rowLine, layout, row, board)
        }
    }

    for (col in 0..<CELLS_COUNT) {
        val colLine = board.colsClears[col]
        if (colLine.phase != LineClearPhase.None) {
            lineClearRender(colLine, layout, col, board)
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
    data class PlayerTurnEnd(val linesCleared: Int) : GameState
    data class AnimatingCurrentShape(val forced: Boolean) : GameState
    data object ClearingAnimation : GameState
    data object GameOver : GameState
}

typealias onMultiplierChange = (change: Int) -> Unit
typealias onScoreChange = (change: Int) -> Unit
typealias onPlaceShape = () -> Unit
typealias onRoundStart = (shape: Shape, roundDuration: Float) -> Unit
typealias onGameOver = () -> Unit

class GameContext(
    val pixelDensity: Float,
    val scaledDensity: Float,
    val onMultiplierChange: onMultiplierChange? = null,
    val onScoreChange: onScoreChange? = null,
    val onPlaceShape: onPlaceShape? = null,
    val onRoundStart: onRoundStart? = null,
    val onGameOver: onGameOver? = null,
) {
    var dt = 0f
    var elapsedTime = 0f

    // outside effects

    var pendingRotation = false
    var pendingPlacement = false
    var changedWidth = -1f
    var changedHeight = -1f

    val layout = Layout(pixelDensity, scaledDensity)

    var state: GameState = GameState.Countdown
    val board = Board()
    val shapesBag = ShapesBag()
    var currentShape = CurrentShape(Shape())
    val countdown = Countdown(30f * scaledDensity, 60f * scaledDensity)
    val announcer = Announcer(48f * scaledDensity, 1f * scaledDensity, scaledDensity)

    var shapesPlaced = 0
    var roundDuration = 5f

    var clearStreak = 0
    var noClearStreak = 0
    var scoreMultiplier = 1
    var score = 0
    var queuedScoreMultiplierUpdates = 0
    var queuedScoreUpdates = 0
}

fun gamePlaceShapeAndClear(game: GameContext, forced: Boolean): Int {
    var cellCount = boardPlaceShape(game.board, game.currentShape, game.elapsedTime)
    val scoreChange = if (forced) {
        cellCount * -10
    } else {
        cellCount * game.scoreMultiplier
    }
    game.score += scoreChange
    game.onScoreChange?.invoke(scoreChange)

    game.shapesPlaced += 1
    game.onPlaceShape?.invoke()
    game.currentShape.initialized = false

    return boardClearFilledCells(game.board, game.elapsedTime)
}

fun gameForcePlaceShape(game: GameContext): GameState {
    Platform.trace.beginSection("forcePlace")

    val availableCoords = currentShapeAvailableCoords(game.currentShape, game.board.cells)
    if (availableCoords == null) {
        // game.onGameOver?.invoke()
        // game.state = GameState.GameOver
        Platform.trace.endSection()
        return GameState.GameOver
    }

    Platform.trace.endSection()

    var forced = false

    if (availableCoords != game.currentShape.projectionCoords) {
        game.currentShape.projectionCoords = availableCoords
        game.currentShape.projectionAnim.begin(game.elapsedTime, availableCoords.toVec2())
        forced = true
    }

    if (!game.currentShape.projectionAnim.animating) {
        val linesCleared = gamePlaceShapeAndClear(game, forced)
        return GameState.PlayerTurnEnd(linesCleared)
    } else {
        return GameState.AnimatingCurrentShape(true)
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
    assert(Platform.renderer != Renderer.Default) { "Renderer not set" }

    // layout

    if (game.changedWidth > -1f || game.changedHeight > -1) {
        layoutUpdate(game.layout, game.changedWidth, game.changedHeight)
        game.changedWidth = -1f
        game.changedHeight = -1f
    }


    // countdown - independent

    if (game.state == GameState.Countdown) {
        if (shapes.BuildConfig.DEBUG) {
            game.state = GameState.Placing
        } else {
            if (countdownUpdate(game.countdown, game.layout, game.elapsedTime)) {
                game.state = GameState.Placing
            }
        }
    }

    // current shape

    when (val gs = game.state) {
        GameState.Placing -> {
            if (!game.currentShape.initialized) {
                val shape = shapesBagNext(game.shapesBag)
                val (gameOver, newShape) = currentShapeSpawn(shape, game.board, game.elapsedTime)

                if (gameOver) {
                    game.onGameOver?.invoke()
                    game.state = GameState.GameOver
                    return
                }

                game.currentShape = newShape

                val nextShape = shapesBagPeek(game.shapesBag)
                game.roundDuration = currentRoundDuration(game.shapesPlaced)
                game.onRoundStart?.invoke(nextShape, game.roundDuration)

                game.pendingRotation = false
                game.pendingPlacement = false
            } else {
                if (game.currentShape.createdAt + game.roundDuration < game.elapsedTime) {
                    val gs = gameForcePlaceShape(game)
                    if (gs == GameState.GameOver) {
                        game.onGameOver?.invoke()
                    }
                    game.state = gs
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
                                val linesCleared = gamePlaceShapeAndClear(game, false)
                                game.state = GameState.PlayerTurnEnd(linesCleared)
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
        }

        is GameState.AnimatingCurrentShape -> {
            // shape dragging
            game.currentShape.projectionAnim.update(game.elapsedTime)

            if (!game.currentShape.projectionAnim.animating) {
                val linesCleared = gamePlaceShapeAndClear(game, gs.forced)
                game.state = GameState.PlayerTurnEnd(linesCleared)
            }
        }

        else -> Unit
    }

    // turn end

    when (val gs = game.state) {
        is GameState.PlayerTurnEnd -> {
            if (gs.linesCleared > 0) {
                game.noClearStreak = 0

                if (gs.linesCleared > 1 || game.clearStreak > 0) {
                    game.scoreMultiplier += gs.linesCleared
                    game.queuedScoreMultiplierUpdates += gs.linesCleared
                }

                val scoreUpdates = gs.linesCleared * CELLS_COUNT
                val scoreChange = scoreUpdates * CELL_CLEAR_REWARD * game.scoreMultiplier
                game.queuedScoreUpdates += scoreUpdates
                game.score += scoreChange
                game.clearStreak += 1

                game.state = GameState.ClearingAnimation
            } else {
                if (game.noClearStreak >= 5 && game.scoreMultiplier > 1) {
                    game.scoreMultiplier -= 1
                    game.onMultiplierChange?.invoke(-1)
                }

                game.clearStreak = 0
                game.noClearStreak += 1
                game.state = GameState.Placing
            }
        }

        else -> Unit
    }

    // cells clearing animation and announcer

    if (game.state == GameState.ClearingAnimation) {
        val result = boardUpdateClearingCells(game.board, game.elapsedTime)

        if (result.linePopped && game.queuedScoreMultiplierUpdates > 0) {
            game.onMultiplierChange?.invoke(1)
            game.queuedScoreMultiplierUpdates -= 1
        }

        if (result.cellsFadedOut > 0) {
            val updates = kotlin.math.min(result.cellsFadedOut, game.queuedScoreUpdates)
            val totalChange = updates * CELL_CLEAR_REWARD * game.scoreMultiplier
            game.onScoreChange?.invoke(totalChange)
            game.queuedScoreUpdates -= updates
        }

        // announcerUpdate(game.announcer, game.layout, game.elapsedTime)

        if (result.allDone && game.announcer.state == Announcer.State.None) {
            // round end
            game.state = GameState.Placing
        }
    }
}

fun gameRender(game: GameContext) {
    boardRender(game.board, game.layout)

    if (game.state == GameState.Countdown) {
        countdownRender(game.countdown)
    }

    if (game.state == GameState.ClearingAnimation) {
        boardRenderClearingAnimation(game.board, game.layout)
    }

    if (game.state == GameState.Placing || game.state is GameState.AnimatingCurrentShape) {
        if (game.currentShape.initialized) {
            currentShapeRender(game.currentShape, game.layout)
        }
    }

    if (game.announcer.state != Announcer.State.None) {
        announcerRender(game.announcer)
    }
}
