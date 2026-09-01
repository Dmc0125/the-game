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
const val DRAG_SENSITIVITY = 2f
const val SHAPE_MOVEMENT_ANIMATION_DURATION = 0.065f

const val CELL_CLEAR_REWARD = 10

val colors: Array<Int> = arrayOf(
    Color.blue,
    Color.lime,
    Color.yellow,
    Color.purple,
)

fun measureText(text: String, textSize: Float): Float {
    return Platform.renderer.measureText(text, textSize, FontWeight.Regular, FONT_SUPPLY_CENTER)
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

class Countdown(val textSizeStart: Float, textSizeEnd: Float) {
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
    countdown.textX = (layout.pgRect.width - textWidth) / 2
    countdown.textY = countdown.textSize + (layout.pgRect.height - countdown.textSize) / 2

    return false
}

fun countdownRender(countdown: Countdown) {
    val color = Color.argb((countdown.opacity * 255).toInt(), 255, 255, 255)
    textRender(countdown.text, countdown.textX, countdown.textY, color, countdown.textSize)
}

class CurrentShape(var shape: Shape, var initialized: Boolean = false) {
    companion object {
        val DEFAULT_COORDS = Coords(
            CELLS_COUNT / 2 - SHAPE_CELLS_COUNT / 2,
            CELLS_COUNT / 2 - SHAPE_CELLS_COUNT / 2,
        )
    }

    val color: Int = colors[Random.nextInt(colors.size)]
    var rotation: Int = 0

    var inDock = true
    var dragging: Boolean = false
    var dragStartPosition = Vec2(0f, 0f)
    var dragPosition = Vec2(0f, 0f)
    var dragPositionPrev = Vec2(0f, 0f)

    var overlapping = false
    var project = false
    var projectionCoords = DEFAULT_COORDS.copy()

    val projectionAnim = Anim()
    var projectionCoordsCurrent = Vec2(Float.MIN_VALUE, Float.MIN_VALUE)
    var projectionCoordsFrom = Vec2(0f, 0f)
    var projectionCoordsTo = Vec2(0f, 0f)

    val rotationAnim = Anim()
    var rotationCurrent = 0f
    var rotationFrom = 0f
    var rotationTo = 0f

    val rotationCenterDiff = Vec2(0f, 0f)
}

fun currentShapeSpawn(
    newShape: Shape,
    board: Board,
    elapsedTime: Float,
): Pair<Boolean, CurrentShape> {
    var gameOver = false

    Platform.trace.beginSection("gameSpawnShape")

    val newShape = CurrentShape(newShape, true)

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

fun currentShapeRotate(shape: CurrentShape, elapsedTime: Float) {
    val prevShapeRotation = getShapeRotation(shape.shape, shape.rotation)

    shape.rotation += 1

    val newShapeRotation = getShapeRotation(shape.shape, shape.rotation)

    val prevCenterCol = prevShapeRotation.minCol + prevShapeRotation.cols / 2f
    val prevCenterRow = prevShapeRotation.minRow + prevShapeRotation.rows / 2f
    val newCenterCol = newShapeRotation.minCol + newShapeRotation.cols / 2f
    val newCenterRow = newShapeRotation.minRow + newShapeRotation.rows / 2f
    shape.rotationCenterDiff.x = prevCenterCol - newCenterCol
    shape.rotationCenterDiff.y = prevCenterRow - newCenterRow

    if (shape.rotationAnim.running) {
        shape.rotationFrom = shape.rotationCurrent - 90f
    } else {
        shape.rotationFrom = -90f
    }

    shape.rotationTo = 0f
    animBegin(shape.rotationAnim, SHAPE_MOVEMENT_ANIMATION_DURATION, elapsedTime)
}

fun currentShapeBeginProjectionAnim(shape: CurrentShape, target: Vec2, elapsedTime: Float) {
    if (target == shape.projectionCoordsTo) return

    val uninit = shape.projectionCoordsCurrent.x == Float.MIN_VALUE &&
            shape.projectionCoordsCurrent.y == Float.MIN_VALUE
    if (uninit) {
        shape.projectionCoordsCurrent = target
        return
    }

    if (shape.projectionAnim.running) {
        shape.projectionCoordsFrom = shape.projectionCoordsCurrent.copy()
    } else {
        shape.projectionCoordsFrom = shape.projectionCoords.toVec2()
    }
    shape.projectionCoordsTo = target

    animBegin(shape.projectionAnim, SHAPE_MOVEMENT_ANIMATION_DURATION, elapsedTime)
}

fun currentShapeProjectionCoordsCurrent(shape: CurrentShape): Vec2 {
    if (shape.projectionAnim.running) {
        shape.projectionCoordsCurrent = animCurrent(
            shape.projectionAnim,
            shape.projectionCoordsFrom,
            shape.projectionCoordsTo,
            ::lerp,
            AnimationEasing.EaseInSquared,
        )
        return shape.projectionCoordsCurrent
    } else {
        return shape.projectionCoords.toVec2()
    }
}

fun currentShapeCheckOverlap(currentShape: CurrentShape, cells: Array<Cell>) {
    currentShape.overlapping = false
    val sr = getShapeRotation(currentShape.shape, currentShape.rotation)
    for (offset in sr.offsets) {
        val cellCoords = currentShape.projectionCoords + offset
        val idx = coordsToIdx(cellCoords.col, cellCoords.row)
        if (cells[idx].filled) {
            currentShape.overlapping = true
            break
        }
    }
}

fun currentShapeUpdateProjection(
    currentShape: CurrentShape,
    board: Container,
    layout: Layout,
    cells: Array<Cell>,
    elapsedTime: Float,
) {
    val boardInnerX = board.posX + layout.pgPadding
    val boardInnerY = board.posY + layout.pgPadding
    val boardInnerWidth = board.width - layout.pgPadding * 2f
    val boardInnerHeight = board.height - layout.pgPadding * 2f

    val sr = getShapeRotation(currentShape.shape, currentShape.rotation)
    val dragPosition = currentShape.dragPosition.copy() // touch + offset * sens

    val gridSize = SHAPE_CELLS_COUNT * layout.cellSize
    dragPosition.x -= gridSize / 2f // move pivot by half grid size left
    dragPosition.y -= gridSize // move pivot to by grid size up

    // center within the grid

    dragPosition.x += (sr.minCol + sr.cols / 2f) * layout.cellSize
    dragPosition.y += (sr.minRow + sr.rows / 2f) * layout.cellSize

    // drag x => center of the shape
    // drag y => center of the shape

    val shapeLeft = dragPosition.x - sr.cols / 2f * layout.cellSize
    val shapeRight = dragPosition.x + sr.cols / 2f * layout.cellSize
    val shapeTop = dragPosition.y - sr.rows / 2f * layout.cellSize
    val shapeBottom = dragPosition.y + sr.rows / 2f * layout.cellSize

    val xInside = shapeLeft > boardInnerX - 100f &&
            shapeRight < boardInnerX + boardInnerWidth + 100f
    val yInside = shapeTop > boardInnerY - 100f &&
            shapeBottom < boardInnerY + boardInnerHeight + 100f

    if (xInside && yInside) {
        val boardX = dragPosition.x - boardInnerX
        val boardY = dragPosition.y - boardInnerY

        var snappedCol = kotlin.math.round(boardX / layout.cellSize).toInt()
        var snappedRow = kotlin.math.round(boardY / layout.cellSize).toInt()

        // col => center of the shape
        // row => center of the shape

        // correct inside the board
        var originCol = snappedCol - sr.cols / 2 - sr.minCol
        var originRow = snappedRow - sr.rows / 2 - sr.minRow

        originCol = originCol.coerceIn(-sr.minCol, CELLS_COUNT - 1 - sr.maxCol)
        originRow = originRow.coerceIn(-sr.minRow, CELLS_COUNT - 1 - sr.maxRow)

        // origin col => left of the grid
        // origin row => top of the grid

        currentShapeBeginProjectionAnim(
            currentShape,
            Vec2(originCol.toFloat(), originRow.toFloat()),
            elapsedTime,
        )

        currentShape.projectionCoords.col = originCol
        currentShape.projectionCoords.row = originRow

        currentShapeCheckOverlap(currentShape, cells)

        currentShape.project = true
    } else {
        // snap to dock

        currentShape.projectionCoordsCurrent = Vec2(Float.MIN_VALUE, Float.MIN_VALUE)
        currentShape.projectionCoords = CurrentShape.DEFAULT_COORDS.copy()

        currentShape.overlapping = false
        currentShape.project = false
    }
}

fun currentShapeAvailableCoords(currentShape: CurrentShape, cells: Array<Cell>, rot: Int = -1): Coords? {
    Platform.trace.beginSection("availableCoords")

    val shapeRotation = getShapeRotation(
        currentShape.shape,
        if (rot == -1) currentShape.rotation else rot,
    )

    // coords represent shape grid top left position, which can be negative, since
    // the cells inside the shape grid are offsets from top left
    //
    // therefore we need offset this position by shape size so the idx stays positive
    //
    // for each axis, each occupied cell is: 0 <= origin + shapeOffset < CELLS_COUNT
    // where origin is the top left position of the shape grid
    //
    // therefore -minShapeOffset <= origin <= CELLS_COUUNT - 1 - maxShapeOffset

    val minCandidateCol = -shapeRotation.minCol
    val maxCandidateCol = CELLS_COUNT - 1 - shapeRotation.maxCol
    val minCandidateRow = -shapeRotation.minRow
    val maxCandidateRow = CELLS_COUNT - 1 - shapeRotation.maxRow

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
        for (cellOffset in shapeRotation.offsets) {
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
        textRender(text, shadowx, shadowy, Color.addAlpha(alpha, Color.ink), textSize)

        textRender(text, pos.x, pos.y, Color.addAlpha(alpha, clr), textSize)
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

    val shapeRotation = getShapeRotation(currentShape.shape, currentShape.rotation)

    for (offset in shapeRotation.offsets) {
        val cellCoords = currentShape.projectionCoords + offset
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
    Platform.renderer.drawRoundRect(layout.pgRect, 24f, Color.ink)

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

class Layout {
    var pgRect = Rect()
    var pgPadding = 0f
    var cellSize = 0f
    var cellPadding = 0f
    var cellRadius = 0f
}

fun layoutUpdate(layout: Layout, boardSize: Float) {
    layout.pgRect = Rect(0f, 0f, boardSize, boardSize)
    layout.pgPadding = boardSize * PLAYGROUND_PADDING_FRACTION
    layout.cellSize = (boardSize - layout.pgPadding * 2) / CELLS_COUNT
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
    data object ClearingAnimation : GameState
    data object GameOver : GameState
}

enum class GameScreen {
    Start,
    Playing,
    GameOver,
}

class GameContext {
    var dt = 0f
    var elapsedTime = 0f

    // outside effects

    var changedWidth = -1f
    var changedHeight = -1f

    //

    var screen = GameScreen.Playing
    var scale = 0f
    val ui = UiContext()
    val rootModifiers = Modifiers()
    val layout = Layout()

    // start screen

    val startButton = Button(
        bgColor = Color.blue,
        textColor = Color.white,
    )
    val rotateButton = Button(
        bgColor = Color.white,
        textColor = Color.black,
    )
    val placeButton = Button(
        bgColor = Color.white,
        textColor = Color.black,
    )

    var state: GameState = GameState.Countdown
    val countdown = Countdown(30f, 60f)
    val board = Board()
    val shapesBag = ShapesBag()
    var currentShape = CurrentShape(Shape())
    var nextShape = Shape()

    var shapesPlaced = 0

    var roundDuration = 0f
    var roundStart = 0f
    var roundEnd = 0f

    var clearStreak = 0
    var noClearStreak = 0
    var scoreMultiplier = 1
    var score = 0
    var queuedScoreMultiplierUpdates = 0
    var queuedScoreUpdates = 0

    val scoreAnim = Anim()
    var scoreCurrent = 0f
    var scoreFrom = 0f
    var scoreTarget = 0f
}

fun gameAnimateScore(game: GameContext, scoreChange: Int) {
    animBegin(game.scoreAnim, 0.1f, game.elapsedTime)
    game.scoreFrom = game.scoreCurrent
    game.scoreTarget += scoreChange
}

fun gamePlaceShapeAndClear(game: GameContext, forced: Boolean): Int {
    var cellCount = boardPlaceShape(game.board, game.currentShape, game.elapsedTime)
    val scoreChange = if (forced) {
        cellCount * -10
    } else {
        cellCount * game.scoreMultiplier
    }

    game.score += scoreChange
    gameAnimateScore(game, scoreChange)

    game.shapesPlaced += 1
    game.currentShape.initialized = false
    game.roundEnd = game.elapsedTime

    return boardClearFilledCells(game.board, game.elapsedTime)
}

fun gameForcePlaceShape(game: GameContext): GameState {
    Platform.trace.beginSection("forcePlace")

    val availableCoords = currentShapeAvailableCoords(game.currentShape, game.board.cells)
    if (availableCoords == null) {
        Platform.trace.endSection()
        return GameState.GameOver
    }

    Platform.trace.endSection()

    var forced = false

    if (availableCoords != game.currentShape.projectionCoords) {
        game.currentShape.projectionCoords = availableCoords
        forced = true
    }

    // if (!game.currentShape.projectionAnim.animating) {
    val linesCleared = gamePlaceShapeAndClear(game, forced)
    return GameState.PlayerTurnEnd(linesCleared)
    // } else {
    //     return GameState.AnimatingCurrentShape(true)
    // }
}

fun coordsToIdx(col: Int, row: Int): Int {
    return col + row * CELLS_COUNT
}

fun currentRoundDuration(shapesPlaced: Int): Float {
    val startingSeconds = 10f
    val minimumSeconds = 3f
    val warmupShapes = 10
    val halfLifeShapes = 50f

    val shapesIntoDifficulty = kotlin.math.max(0, shapesPlaced - warmupShapes)

    return minimumSeconds +
            (startingSeconds - minimumSeconds) *
            0.5f.pow(shapesIntoDifficulty / halfLifeShapes)
}

fun gameQueueResize(game: GameContext, newWidth: Float, newHeight: Float) {
    game.changedWidth = newWidth
    game.changedHeight = newHeight
}

fun gameUpdate(game: GameContext, dt: Float, touch: Touch) {
    assert(Platform.renderer != Renderer.Default) { "Renderer not set" }

    game.dt = dt
    game.elapsedTime += dt

    // -----------------
    // process inputs

    // resize

    if (game.changedWidth != -1f || game.changedHeight != -1f) {
        val logicalWidth = 360f

        game.scale = game.changedWidth / logicalWidth
        val logicalHeight = game.changedHeight / game.scale

        game.ui.logicalWidth = logicalWidth
        game.ui.logicalHeight = logicalHeight

        val paddingHorizontal = 20f

        mPaddingVertical(game.rootModifiers, 40f)
        mPaddingHorizontal(game.rootModifiers, paddingHorizontal)

        val boardSize = game.ui.logicalWidth - paddingHorizontal * 2f
        layoutUpdate(game.layout, boardSize)

        game.changedWidth = -1f
        game.changedHeight = -1f
    }

    // touch

    if (game.ui.nodes.size > 0) {
        if (game.screen == GameScreen.Start) {
            // start button

            val startButton = uiGetNode(game.ui, "start_button")
            val justPressed = touch.action == TouchAction.Down &&
                    !game.startButton.pressed &&
                    uiPosInside(startButton, touch.position.x, touch.position.y)

            if (justPressed) {
                buttonPress(game.startButton, game.elapsedTime)
            } else if (touch.action == TouchAction.Up && game.startButton.pressed) {
                buttonRelease(game.startButton, game.elapsedTime)
                game.screen = GameScreen.Playing
            }
        } else if (game.screen == GameScreen.Playing) {
            val currentShape = game.currentShape
            if (currentShape.initialized) {
                // place
                var placed = false
                run {
                    val placeButtonUi = uiGetNode(game.ui, "button_place")
                    val placeButton = game.placeButton
                    val justPressed = touch.action == TouchAction.Down &&
                            !placeButton.pressed &&
                            uiPosInside(placeButtonUi, touch.position.x, touch.position.y)

                    if (justPressed) {
                        buttonPress(placeButton, game.elapsedTime)
                    } else if (touch.action == TouchAction.Up && placeButton.pressed) {
                        if (!currentShape.overlapping) {
                            val linesCleared = gamePlaceShapeAndClear(game, false)
                            game.state = GameState.PlayerTurnEnd(linesCleared)
                            placed = true
                        }
                        buttonRelease(placeButton, game.elapsedTime)
                    }
                }

                if (!placed) {
                    // rotate
                    run {
                        val rotateButtonUi = uiGetNode(game.ui, "button_rotate")
                        val rotateButton = game.rotateButton
                        val justPressed = touch.action == TouchAction.Down &&
                                !rotateButton.pressed &&
                                uiPosInside(rotateButtonUi, touch.position.x, touch.position.y)

                        if (justPressed) {
                            buttonPress(rotateButton, game.elapsedTime)
                        } else if (touch.action == TouchAction.Up && rotateButton.pressed) {
                            currentShapeRotate(currentShape, game.elapsedTime)

                            val board = uiGetNode(game.ui, "game_board")
                            currentShapeUpdateProjection(
                                currentShape,
                                board,
                                game.layout,
                                game.board.cells,
                                game.elapsedTime
                            )

                            buttonRelease(rotateButton, game.elapsedTime)
                        }
                    }

                    // drag
                    run {
                        val currentShapeContainer = uiGetNode(game.ui, "current_shape")
                        val justPressed = touch.action == TouchAction.Down &&
                                !currentShape.dragging &&
                                uiPosInside(currentShapeContainer, touch.position.x, touch.position.y)

                        if (justPressed) {
                            currentShape.dragging = true
                            currentShape.inDock = false
                            currentShape.dragStartPosition = touch.position.copy()
                            if (currentShape.dragPositionPrev.x == 0f && currentShape.dragPositionPrev.y == 0f) {
                                currentShape.dragPositionPrev = touch.position.copy()
                                currentShape.dragPosition = touch.position.copy()
                            }
                        } else if (touch.action == TouchAction.Move && currentShape.dragging) {
                            val delta = (touch.position - currentShape.dragStartPosition) * DRAG_SENSITIVITY
                            currentShape.dragPosition = currentShape.dragPositionPrev + delta

                            val board = uiGetNode(game.ui, "game_board")
                            currentShapeUpdateProjection(
                                currentShape,
                                board,
                                game.layout,
                                game.board.cells,
                                game.elapsedTime
                            )
                        } else if (touch.action == TouchAction.Up && currentShape.dragging) {
                            if (!currentShape.project) {
                                currentShape.inDock = true
                                currentShape.dragPositionPrev.x = 0f
                                currentShape.dragPositionPrev.y = 0f
                            } else {
                                currentShape.dragPositionPrev.x = currentShape.dragPosition.x
                                currentShape.dragPositionPrev.y = currentShape.dragPosition.y
                            }

                            currentShape.dragging = false
                        }
                    }
                }
            }
        }
    }

    // -----------------
    // update game

    if (game.screen == GameScreen.Playing) {
        // countdown - independent

        if (game.state == GameState.Countdown) {
            // if (shapes.BuildConfig.DEBUG) {
            //     game.state = GameState.Placing
            // } else {
            // if (countdownUpdate(game.countdown, game.layout, game.elapsedTime)) {
            game.state = GameState.Placing
            // }
            // }
        }

        // current shape

        when (val gs = game.state) {
            GameState.Placing -> {
                if (!game.currentShape.initialized) {
                    val shape = shapesBagNext(game.shapesBag)
                    val (gameOver, newShape) = currentShapeSpawn(shape, game.board, game.elapsedTime)
                    game.roundStart = game.elapsedTime

                    if (gameOver) {
                        game.state = GameState.GameOver
                        return
                    }

                    game.currentShape = newShape

                    val nextShape = shapesBagPeek(game.shapesBag)
                    game.roundDuration = currentRoundDuration(game.shapesPlaced)
                    game.nextShape = nextShape
                } else {
                    if (game.roundStart + game.roundDuration < game.elapsedTime) {
                        game.state = gameForcePlaceShape(game)
                    }

                    animUpdate(game.currentShape.projectionAnim, game.elapsedTime)
                    animUpdate(game.currentShape.rotationAnim, game.elapsedTime)
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
                game.queuedScoreMultiplierUpdates -= 1
            }

            if (result.cellsFadedOut > 0) {
                val updates = kotlin.math.min(result.cellsFadedOut, game.queuedScoreUpdates)
                val totalChange = updates * CELL_CLEAR_REWARD * game.scoreMultiplier
                game.queuedScoreUpdates -= updates

                gameAnimateScore(game, totalChange)
            }

            if (result.allDone) {
                // round end
                game.state = GameState.Placing
            }
        }
    }

    // -----------------
    // update layout

    val ui = game.ui
    uiRootInit(ui, game.rootModifiers)

    if (game.screen == GameScreen.Start) {
        uiColBegin(
            ui,
            Modifiers(width = Size.FillMax, height = Size.FillMax),
            verticalAlignment = Alignment.End,
        )

        // start button
        run {
            animUpdate(game.startButton.anim, game.elapsedTime)

            val m = Modifiers(width = Size.FillMax)
            mPaddingHorizontal(m, 24f)
            mPaddingVertical(m, 20f)
            uiRowBegin(ui, m, id = "start_button")

            uiText(
                ui,
                Modifiers(
                    text = "start",
                    textSize = 20f,
                ),
                id = "start_button_text",
            )

            uiRowEnd(ui)
        }

        uiColEnd(ui)
    } else if (game.screen == GameScreen.Playing) {
        fun verticalSpacer(size: Float = 30f) {
            uiRowBegin(ui, Modifiers(height = Size.Abs(size)))
            uiRowEnd(ui)
        }

        fun horizontalSpacer(size: Float = 30f) {
            uiRowBegin(ui, Modifiers(width = Size.Abs(size)))
            uiRowEnd(ui)
        }

        // --------------
        // top bar
        uiRowBegin(ui, Modifiers(width = Size.FillMax))

        run {
            // score

            val m = Modifiers(width = Size.FillMaxF(0.6f), paddingTop = 10f)
            mPaddingHorizontal(m, 20f)
            uiColBegin(ui, m, id = "score_board")

            uiText(ui, Modifiers(text = "score", textSize = 8f), id = "score_header_text")

            run {
                animUpdate(game.scoreAnim, game.elapsedTime)
                game.scoreCurrent = animCurrent(
                    game.scoreAnim,
                    game.scoreFrom,
                    game.scoreTarget,
                    ::lerp,
                    AnimationEasing.EaseOutSquared,
                )

                val m = Modifiers(
                    text = "%06d".format(kotlin.math.round(game.scoreCurrent).toInt()),
                    textSize = 24f,
                )
                mPaddingVertical(m, 14f)
                uiText(ui, m, id = "score_value_text")
            }

            uiColEnd(ui)
        }

        uiRowEnd(ui)

        verticalSpacer()

        // --------------
        // timer

        run {
            val timerHeight = 12f
            uiRowBegin(ui, Modifiers(width = Size.FillMax, height = Size.Abs(timerHeight)))

            run {
                // remainig time
                var remainingTime = 0f
                var progress = 0f

                if (game.state == GameState.Placing) {
                    val elapsed = game.elapsedTime - game.roundStart
                    remainingTime = game.roundDuration - elapsed
                    progress = remainingTime / game.roundDuration
                } else if (game.state == GameState.ClearingAnimation) {
                    val elapsed = game.roundEnd - game.roundStart
                    remainingTime = game.roundDuration - elapsed
                    progress = remainingTime / game.roundDuration
                }

                run {
                    uiColBegin(ui, Modifiers(height = Size.FillMax), verticalAlignment = Alignment.Center)
                    val m = Modifiers(
                        text = "%02.01fs".format(remainingTime),
                        textSize = 8f,
                        width = Size.Abs(40f)
                    )
                    uiText(ui, m, "timer_text")
                    uiColEnd(ui)
                }

                // bar

                uiColBegin(
                    ui,
                    Modifiers(width = Size.FillMax, height = Size.Abs(timerHeight)),
                    verticalAlignment = Alignment.Center
                )

                val m = Modifiers(width = Size.FillMax, height = Size.Abs(12f))
                mPaddingHorizontal(m, 3f)
                mPaddingVertical(m, 3f)
                uiRowBegin(ui, m, id = "timer_bar")

                run {
                    // inner bar
                    uiRowBegin(
                        ui,
                        Modifiers(width = Size.FillMaxF(progress), height = Size.FillMax),
                        id = "timer_bar_inner",
                    )
                    uiRowEnd(ui)
                }

                uiRowEnd(ui)
                uiColEnd(ui)
            }

            uiRowEnd(ui)
        }

        verticalSpacer()

        // --------------
        // game board

        uiRowBegin(
            ui,
            Modifiers(
                width = Size.Abs(game.layout.pgRect.width),
                height = Size.Abs(game.layout.pgRect.height),
            ),
            id = "game_board",
        )
        uiRowEnd(ui)

        // --------------
        // bottom bar

        uiColBegin(ui, Modifiers(Size.FillMax, Size.FillMax), verticalAlignment = Alignment.End)
        uiRowBegin(
            ui,
            Modifiers(width = Size.FillMax, height = Size.FillMaxF(0.7f)),
            horizontalAlignment = Alignment.Center,
        )

        run {
            val width = game.layout.pgRect.width
            val componentSize = width * 0.3f
            val spacing = (width - (componentSize * 3f)) / 2f

            // next shape
            uiRowBegin(
                ui,
                Modifiers(
                    width = Size.Abs(componentSize),
                    height = Size.Abs(componentSize),
                ),
                Alignment.Start,
            )

            run {
                val dockSize = 0.8f

                uiColBegin(
                    ui,
                    Modifiers(
                        width = Size.FillMaxF(dockSize),
                        height = Size.FillMax,
                    ),
                    Alignment.Center,
                )

                val m = Modifiers(Size.FillMax, Size.FillMaxF(dockSize))
                mPaddingHorizontal(m, 10f)
                mPaddingVertical(m, 10f)
                uiColBegin(ui, m, id = "next_shape_dock_ui")

                run {
                    uiText(ui, Modifiers(text = "Next", textSize = 8f), id = "next_shape_title")

                    verticalSpacer(8f)

                    uiColBegin(
                        ui,
                        Modifiers(Size.FillMax, Size.FillMax),
                        Alignment.Center,
                        id = "next_shape_dock",
                    )
                    uiColEnd(ui)
                }

                uiColEnd(ui)
                uiColEnd(ui)
            }

            uiRowEnd(ui)

            horizontalSpacer(spacing)

            // current shape
            val m = Modifiers(
                width = Size.Abs(componentSize),
                height = Size.Abs(componentSize),
            )
            uiRowBegin(ui, m, id = "current_shape")
            uiRowEnd(ui)

            horizontalSpacer(spacing)

            // controls
            uiRowBegin(
                ui,
                Modifiers(width = Size.Abs(componentSize), height = Size.Abs(componentSize)),
                horizontalAlignment = Alignment.End,
                id = "controls",
            )

            run {
                uiColBegin(ui, Modifiers(width = Size.FillMaxF(0.8f)))

                val buttonHeight = componentSize * 0.4f
                val spacing = (componentSize - buttonHeight * 2f)
                val textSize = 10f
                val paddingTop = (buttonHeight - textSize) / 2f

                animUpdate(game.rotateButton.anim, game.elapsedTime)
                uiRowBegin(
                    ui,
                    Modifiers(Size.FillMax, Size.Abs(buttonHeight), paddingTop = paddingTop),
                    Alignment.Center,
                    id = "button_rotate",
                )
                uiText(ui, Modifiers(text = "R", textSize = textSize), id = "button_rotate_text")
                uiRowEnd(ui)

                verticalSpacer(spacing)

                animUpdate(game.placeButton.anim, game.elapsedTime)
                uiRowBegin(
                    ui,
                    Modifiers(Size.FillMax, Size.Abs(buttonHeight), paddingTop = paddingTop),
                    Alignment.Center,
                    id = "button_place",
                )
                uiText(ui, Modifiers(text = "Place", textSize = textSize), id = "button_place_text")
                uiRowEnd(ui)

                uiColEnd(ui)
            }

            uiRowEnd(ui)
        }

        uiRowEnd(ui)
        uiColEnd(ui)
    }

    uiRootEnd(ui)
}

fun gameRender(game: GameContext) {
    val r = Platform.renderer

    r.save()
    r.scale(game.scale, game.scale)

    // ------------
    // HUD

    val root = uiGetNode(game.ui, "root")
    r.drawRect(0f, 0f, root.width, root.height, Color.vanilla)

    if (game.screen == GameScreen.Start) {
        val startButton = uiGetNode(game.ui, "start_button")
        val startButtonText = uiGetNode(game.ui, "start_button_text")
        buttonRender(game.startButton, startButton, startButtonText)
    } else if (game.screen == GameScreen.Playing) {
        run {
            // top bar
            val scoreBoard = uiGetNode(game.ui, "score_board")
            cardRender(scoreBoard, Color.blue)

            val scoreHeaderText = uiGetNode(game.ui, "score_header_text")
            textRender(scoreHeaderText, Color.white)

            val scoreValueText = uiGetNode(game.ui, "score_value_text")
            textRender(scoreValueText, Color.white)
        }

        run {
            // timer
            val timerText = uiGetNode(game.ui, "timer_text")
            textRender(timerText, Color.ink)

            val bar = uiGetNode(game.ui, "timer_bar")
            r.drawRoundRect(bar.posX, bar.posY, bar.width, bar.height, bar.height / 2f, Color.ink)

            var barInner = uiGetNode(game.ui, "timer_bar_inner")
            r.drawRoundRect(
                barInner.posX, barInner.posY, barInner.width, barInner.height,
                barInner.height / 2f, Color.blue,
            )
        }

        // next shape
        run {
            val nextShapeDock = uiGetNode(game.ui, "next_shape_dock_ui")
            cardRender(nextShapeDock, Color.ink, 20f)

            val nextShapeTitle = uiGetNode(game.ui, "next_shape_title")
            textRender(nextShapeTitle, Color.white)
        }

        // controls
        run {
            // rotate button
            val rotateButton = uiGetNode(game.ui, "button_rotate")
            val rotateButtonText = uiGetNode(game.ui, "button_rotate_text")
            buttonRender(game.rotateButton, rotateButton, rotateButtonText)
        }

        run {
            // place button
            val placeButton = uiGetNode(game.ui, "button_place")
            val placeButtonText = uiGetNode(game.ui, "button_place_text")
            buttonRender(game.placeButton, placeButton, placeButtonText)
        }
    }

    // --------------
    // Game

    if (game.screen == GameScreen.Playing) {
        run {
            // game
            val gameBoard = uiGetNode(game.ui, "game_board")
            r.drawRoundRect(
                gameBoard.posX, gameBoard.posY,
                gameBoard.width, gameBoard.height,
                UI_RADIUS, Color.ink,
            )

            r.save()
            r.translate(gameBoard.posX, gameBoard.posY)

            if (game.state == GameState.Countdown) {
                countdownRender(game.countdown)
            }

            // board cells
            for (row in 0 until CELLS_COUNT) {
                val layout = game.layout
                val y = layout.pgPadding + row * layout.cellSize
                for (col in 0 until CELLS_COUNT) {
                    val x = layout.pgPadding + col * layout.cellSize
                    val idx = coordsToIdx(col, row)
                    val cell = game.board.cells[idx]

                    if (cell.filled) {
                        r.drawRoundRect(
                            x + layout.cellPadding, y + layout.cellPadding,
                            layout.cellSize - layout.cellPadding * 2,
                            layout.cellSize - layout.cellPadding * 2,
                            layout.cellRadius,
                            cell.color,
                        )
                    }
                }
            }

            if (game.state == GameState.ClearingAnimation) {
                boardRenderClearingAnimation(game.board, game.layout)
            }

            if (game.state == GameState.Placing) {
                //  projection

                val shape = game.currentShape
                val layout = game.layout

                if (shape.project) {
                    val color = if (shape.overlapping) {
                        Color.addAlpha(200, Color.red)
                    } else {
                        shape.color
                    }

                    val currentProjectionCoords = currentShapeProjectionCoordsCurrent(shape)
                    val s = getShapeRotation(shape.shape, shape.rotation)

                    val leftCoords = currentProjectionCoords.x + s.minCol
                    val topCoords = currentProjectionCoords.y + s.minRow

                    val leftPos = layout.pgPadding + leftCoords * layout.cellSize
                    val topPos = layout.pgPadding + topCoords * layout.cellSize

                    val gridSize = SHAPE_CELLS_COUNT * layout.cellSize
                    val shapeCenterX = leftPos + s.cols * layout.cellSize / 2f
                    val shapeCenterY = topPos + s.rows * layout.cellSize / 2f

                    if (shape.rotationAnim.running) {
                        r.save()

                        val currentOffset = animCurrent(
                            shape.rotationAnim,
                            shape.rotationCenterDiff,
                            Vec2(0f, 0f),
                            ::lerp,
                            AnimationEasing.EaseOutSquared,
                        )

                        val currentOffsetX = currentOffset.x * layout.cellSize
                        val currentOffsetY = currentOffset.y * layout.cellSize
                        r.translate(currentOffsetX, currentOffsetY)

                        val currentRotation = animCurrent(
                            shape.rotationAnim,
                            shape.rotationFrom,
                            shape.rotationTo,
                            ::lerp,
                            AnimationEasing.EaseInSquared,
                        )
                        shape.rotationCurrent = currentRotation

                        r.rotate(currentRotation, shapeCenterX, shapeCenterY)
                    }

                    for (offset in s.offsets) {
                        val x = leftPos + (offset.col - s.minCol) * layout.cellSize
                        val y = topPos + (offset.row - s.minRow) * layout.cellSize

                        r.strokeRoundRect(
                            x + layout.cellPadding, y + layout.cellPadding,
                            layout.cellSize - layout.cellPadding * 2, layout.cellSize - layout.cellPadding * 2,
                            layout.cellRadius,
                            color,
                            1f,
                        )
                    }

                    if (shape.rotationAnim.running) {
                        r.restore()
                    }
                }
            }

            r.restore()
        }

        // next shape
        run {
            if (game.state == GameState.Placing) {
                val nextShapeDock = uiGetNode(game.ui, "next_shape_dock")

                val dockX = nextShapeDock.posX
                val dockY = nextShapeDock.posY
                val dockWidth = nextShapeDock.width
                val dockHeight = nextShapeDock.height

                val shapeRotation = getShapeRotation(game.nextShape, 0)

                val cellSize = minOf(
                    dockWidth / shapeRotation.cols,
                    dockHeight / shapeRotation.rows,
                    dockWidth / SHAPE_CELLS_COUNT,
                )
                val cellRadius = cellSize * CELL_RADIUS_FRACTION
                val cellPadding = cellSize * 0.05f

                val left = dockX + (dockWidth - shapeRotation.cols * cellSize) / 2f
                val top = dockY + (dockHeight - shapeRotation.rows * cellSize) / 2f

                for (offset in shapeRotation.offsets) {
                    val x = left + (offset.col - shapeRotation.minCol) * cellSize
                    val y = top + (offset.row - shapeRotation.minRow) * cellSize
                    r.drawRoundRect(
                        x + cellPadding, y + cellPadding,
                        cellSize - cellPadding * 2, cellSize - cellPadding * 2,
                        cellRadius,
                        Color.blue,
                    )
                }
            }
        }

        // current shape
        run {
            val shapeDock = uiGetNode(game.ui, "current_shape")
            cardRender(shapeDock, Color.ink)

            val dockX = shapeDock.posX
            val dockY = shapeDock.posY
            val dockWidth = shapeDock.width
            val dockHeight = shapeDock.height

            // current shape

            val shape = game.currentShape
            val layout = game.layout
            val maxCellSize = 20f
            val sr = getShapeRotation(shape.shape, shape.rotation)

            if (shape.initialized) {
                var left = 0f
                var top = 0f

                var totalShapeWidth = 0f
                var totalShapeHeight = 0f

                var cellSize = 0f
                var cellPadding = 0f
                var color = 0
                var rotationPivot = Vec2(0f, 0f)

                if (shape.inDock) {
                    val dockPaddingHorizontal = dockWidth * 0.1f
                    val dockPaddingVertical = dockHeight * 0.1f
                    cellSize = minOf(
                        (dockWidth - dockPaddingHorizontal * 2f) / sr.cols,
                        (dockHeight - dockPaddingVertical * 2f) / sr.rows,
                        maxCellSize,
                    )
                    cellPadding = cellSize * 0.05f
                    val totalShapeWidth = sr.cols * cellSize
                    val totalShapeHeight = sr.rows * cellSize

                    left = dockX + (dockWidth - totalShapeWidth) / 2f
                    top = dockY + (dockHeight - totalShapeHeight) / 2f

                    rotationPivot.x = left + totalShapeWidth / 2f
                    rotationPivot.y = top + totalShapeHeight / 2f

                    color = shape.color
                } else {
                    cellSize = layout.cellSize
                    cellPadding = layout.cellPadding

                    val gridSize = SHAPE_CELLS_COUNT * cellSize

                    // move x to center and y to bottom
                    left = shape.dragPosition.x - (gridSize / 2f)
                    top = shape.dragPosition.y - gridSize

                    val totalShapeWidth = sr.cols * cellSize
                    val totalShapeHeight = sr.rows * cellSize

                    // center the shape within the grid
                    left += (gridSize - totalShapeWidth) / 2f
                    top += (gridSize - totalShapeHeight) / 2f

                    rotationPivot.x = left + totalShapeWidth / 2f
                    rotationPivot.y = top + totalShapeHeight / 2f

                    color = if (shape.overlapping) {
                        Color.addAlpha(200, Color.red)
                    } else {
                        shape.color
                    }
                }

                if (shape.rotationAnim.running) {
                    val currentRotation = animCurrent(
                        shape.rotationAnim,
                        shape.rotationFrom,
                        shape.rotationTo,
                        ::lerp,
                        AnimationEasing.EaseInSquared,
                    )
                    shape.rotationCurrent = currentRotation

                    r.save()
                    r.rotate(currentRotation, rotationPivot.x, rotationPivot.y)
                }

                val cellRadius = cellSize * CELL_RADIUS_FRACTION

                for (offset in sr.offsets) {
                    val cellx = left + (offset.col - sr.minCol) * cellSize
                    val celly = top + (offset.row - sr.minRow) * cellSize

                    r.drawRoundRect(
                        cellx + cellPadding,
                        celly + cellPadding,
                        cellSize - cellPadding * 2,
                        cellSize - cellPadding * 2,
                        cellRadius,
                        color,
                    )
                }

                if (shape.rotationAnim.running) {
                    r.restore()
                }
            }
        }
    }

    r.restore()
}
