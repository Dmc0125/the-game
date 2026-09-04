package shapes.game

import kotlin.math.pow
import kotlin.random.Random

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

    val textWidth = textMeasure(countdown.text, countdown.textSize)
    countdown.textX = (layout.boardSize - textWidth) / 2
    countdown.textY = countdown.textSize + (layout.boardSize - countdown.textSize) / 2

    return false
}

fun countdownRender(countdown: Countdown, gameBoardUi: Container) {
    val r = Platform.renderer
    r.save()
    r.translate(gameBoardUi.posX, gameBoardUi.posY)

    val color = Color.argb((countdown.opacity * 255).toInt(), 255, 255, 255)
    textRender(countdown.text, countdown.textX, countdown.textY, color, countdown.textSize)

    r.restore()
}

class CurrentShape(s: Shape, var initialized: Boolean = false) {
    companion object {
        val DEFAULT_COORDS = Coords(
            CELLS_COUNT / 2 - SHAPE_CELLS_COUNT / 2,
            CELLS_COUNT / 2 - SHAPE_CELLS_COUNT / 2,
        )
    }

    var shape = s.reference
    var color = s.color

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

    val newCurrentShape = CurrentShape(newShape, true)

    val s = newCurrentShape.shape
    val oc = newCurrentShape.projectionCoords
    val canPlace = currentShapeAvailableCoords(s, 0, oc, board.cells) != null ||
            currentShapeAvailableCoords(s, 1, oc, board.cells) != null ||
            currentShapeAvailableCoords(s, 2, oc, board.cells) != null ||
            currentShapeAvailableCoords(s, 3, oc, board.cells) != null

    Platform.trace.endSection()

    if (!canPlace) {
        gameOver = true
    } else {
        newCurrentShape.overlapping = currentShapeCheckOverlap(
            oc,
            s,
            newCurrentShape.rotation,
            board.cells,
        )
    }

    return Pair(gameOver, newCurrentShape)
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

fun currentShapeCheckOverlap(
    coords: Coords,
    shape: ShapeReference,
    rotation: Int,
    cells: Array<Cell>,
): Boolean {
    var overlapping = false
    val sr = getShapeRotation(shape, rotation)
    for (offset in sr.offsets) {
        val cellCoords = coords + offset
        val idx = coordsToIdx(cellCoords.col, cellCoords.row)
        if (cells[idx].filled) {
            overlapping = true
            break
        }
    }
    return overlapping
}

fun currentShapeUpdateProjection(
    currentShape: CurrentShape,
    board: Container,
    layout: Layout,
    cells: Array<Cell>,
    elapsedTime: Float,
    forceUpdate: Boolean = false,
) {
    val boardInnerX = board.posX + layout.boardPadding
    val boardInnerY = board.posY + layout.boardPadding
    val boardInnerWidth = board.width - layout.boardPadding * 2f
    val boardInnerHeight = board.height - layout.boardPadding * 2f

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
        val originCoords = Coords(
            snappedCol - sr.cols / 2 - sr.minCol,
            snappedRow - sr.rows / 2 - sr.minRow,
        )

        originCoords.col = originCoords.col.coerceIn(-sr.minCol, CELLS_COUNT - 1 - sr.maxCol)
        originCoords.row = originCoords.row.coerceIn(-sr.minRow, CELLS_COUNT - 1 - sr.maxRow)

        val update = originCoords.col != currentShape.projectionCoords.col ||
                originCoords.row != currentShape.projectionCoords.row ||
                forceUpdate

        if (update) {
            var overlapping = currentShapeCheckOverlap(
                originCoords,
                currentShape.shape,
                currentShape.rotation,
                cells,
            )
            if (overlapping) {
                val availableCoords = currentShapeAvailableCoords(
                    currentShape.shape,
                    currentShape.rotation,
                    originCoords,
                    cells,
                )
                if (availableCoords != null) {
                    originCoords.col = availableCoords.col
                    originCoords.row = availableCoords.row
                    overlapping = false
                }
            }

            // origin col => left of the grid
            // origin row => top of the grid

            currentShapeBeginProjectionAnim(currentShape, originCoords.toVec2(), elapsedTime)

            currentShape.projectionCoords.col = originCoords.col
            currentShape.projectionCoords.row = originCoords.row
            currentShape.overlapping = overlapping
            currentShape.project = true
        }
    } else {
        // snap to dock

        currentShape.projectionCoordsCurrent = Vec2(Float.MIN_VALUE, Float.MIN_VALUE)
        currentShape.projectionCoords = CurrentShape.DEFAULT_COORDS.copy()

        currentShape.overlapping = false
        currentShape.project = false
    }
}

fun currentShapeAvailableCoords(
    shapeReference: ShapeReference,
    rotation: Int,
    originCoords: Coords,
    cells: Array<Cell>,
): Coords? {
    Platform.trace.beginSection("availableCoords")

    val shapeRotation = getShapeRotation(shapeReference, rotation)

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

    enqueue(originCoords.col, originCoords.row)

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

fun currentShapeRender(
    currentShape: CurrentShape,
    shapeDockUi: Container,
    gameBoardUi: Container,
    layout: Layout,
    gameState: GameState,
) {
    val r = Platform.renderer
    val shape = currentShape
    val layout = layout

    if (gameState == GameState.Placing) {
        // projection
        if (shape.project && shape.initialized) {
            r.save()
            r.translate(gameBoardUi.posX, gameBoardUi.posY)

            val color = if (shape.overlapping) {
                Color.addAlpha(200, Color.red)
            } else {
                shape.color
            }

            val currentProjectionCoords = currentShapeProjectionCoordsCurrent(shape)
            val s = getShapeRotation(shape.shape, shape.rotation)

            val leftCoords = currentProjectionCoords.x + s.minCol
            val topCoords = currentProjectionCoords.y + s.minRow

            val leftPos = layout.boardPadding + leftCoords * layout.cellSize
            val topPos = layout.boardPadding + topCoords * layout.cellSize

            val gridSize = SHAPE_CELLS_COUNT * layout.cellSize
            val shapeCenterX = leftPos + s.cols * layout.cellSize / 2f
            val shapeCenterY = topPos + s.rows * layout.cellSize / 2f

            if (shape.rotationAnim.running) {
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
                var x = leftPos + (offset.col - s.minCol) * layout.cellSize
                var y = topPos + (offset.row - s.minRow) * layout.cellSize

                x += layout.cellPadding
                y += layout.cellPadding
                var innerSize = layout.cellSize - layout.cellPadding * 2

                // val projectionStrokeColor = Color.addAlpha(200, color)
                val projectionBgColor = Color.addAlpha(200, color)

                r.drawRoundRect(x, y, innerSize, innerSize, layout.cellRadius, projectionBgColor)

                // val strokeWidth = 2f
                // x += strokeWidth
                // y += strokeWidth
                // innerSize -= strokeWidth * 2

                // r.strokeRoundRect(x, y, innerSize, innerSize, layout.cellRadius, projectionStrokeColor, strokeWidth)
            }

            r.restore()
        }
    }

    // dragging and dock

    val dockX = shapeDockUi.posX
    val dockY = shapeDockUi.posY
    val dockWidth = shapeDockUi.width
    val dockHeight = shapeDockUi.height

    val maxCellSize = 20f
    val sr = getShapeRotation(shape.shape, shape.rotation)

    if (shape.initialized) {
        var left = 0f
        var top = 0f

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

            cellRender(
                layout,
                cellx,
                celly,
                cellSize,
                cellPadding,
                cellRadius,
                color,
            )
        }

        if (shape.rotationAnim.running) {
            r.restore()
        }
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

    var scheduled = false
    var cellAnchor = -1
    var phase: LineClearPhase = LineClearPhase.None
    var progress = Anim()
    var popDelay = 0f
    var fadeOutDelay = 0f
    val cells = Array(CELLS_COUNT) { Cell() }
}

fun lineClearSchedule(
    lineClear: LineClear,
    cellAnchor: Int,
    popDelay: Float,
    fadeOutDelay: Float,
) {
    lineClear.scheduled = true
    lineClear.phase = LineClearPhase.PopGrow
    lineClear.popDelay = popDelay
    lineClear.fadeOutDelay = fadeOutDelay
    lineClear.cellAnchor = cellAnchor
}

fun lineClearBegin(lineClear: LineClear, elapsedTime: Float) {
    lineClear.scheduled = false
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
            // Platform.renderer.save()
            // Platform.renderer.translate(offset.x, offset.y)
            // Platform.renderer.rotate(rotation, cellCenter.x, cellCenter.y)
        }

        Platform.renderer.save()
        Platform.renderer.scale(scale, scale, cellCenter.x, cellCenter.y)

        val innerCellSize = layout.cellSize - layout.cellPadding * 2
        cellRender(
            layout,
            cellPos.x,
            cellPos.y,
            color = color,
        )

        if (rotation != 0f) {
            // Platform.renderer.restore()
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
    enum class State {
        None,
        Placing,
        Clearing,
    }

    var state = State.None
    val cells = Array(CELLS_COUNT * CELLS_COUNT) { Cell(it) }
    val rowsClears = Array(CELLS_COUNT) { LineClear(true) }
    val colsClears = Array(CELLS_COUNT) { LineClear(false) }
    val placingAnim = Anim()
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

    animBegin(board.placingAnim, 0.2f, elapsedTime)
    board.state = Board.State.Placing

    return count
}

fun boardFindFilledLines(board: Board): Int {
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
            val popDelay = rowIdx * LineClear.LINE_POP_STAGGER
            val fadeOutDelay = fadeOutDelay(rowIdx)
            lineClearSchedule(board.rowsClears[row], anchorCol, popDelay, fadeOutDelay)
            rowIdx += 1
        }
    }

    // cols
    var colIdx = rowIdx

    for ((col, anchorRow) in filledCols.withIndex()) {
        if (anchorRow > -1) {
            val popDelay = colIdx * LineClear.LINE_POP_STAGGER
            val fadeOutDelay = fadeOutDelay(colIdx)
            lineClearSchedule(board.colsClears[col], anchorRow, popDelay, fadeOutDelay)
            colIdx += 1
        }
    }

    return linesCount
}

fun boardClearLines(board: Board, elapsedTime: Float) {
    board.state = Board.State.Clearing

    for (rowIndex in board.rowsClears.indices) {
        val row = board.rowsClears[rowIndex]
        if (row.scheduled) {
            lineClearBegin(row, elapsedTime)
            for (colIndex in 0..<CELLS_COUNT) {
                val idx = coordsToIdx(colIndex, rowIndex)
                board.cells[idx].filled = false
            }
        }
    }
    for (colIndex in board.colsClears.indices) {
        val col = board.colsClears[colIndex]
        if (col.scheduled) {
            lineClearBegin(col, elapsedTime)
            for (rowIndex in 0..<CELLS_COUNT) {
                val idx = coordsToIdx(colIndex, rowIndex)
                board.cells[idx].filled = false
            }
        }
    }
}

sealed interface BoardUpdateResult {
    data object None : BoardUpdateResult
    data class Placing(val linesFilled: Int) : BoardUpdateResult
    data class Clearing(
        var allDone: Boolean,
        var linePopped: Boolean,
        var cellsFadedOut: Int,
    ) : BoardUpdateResult
}

fun boardUpdate(board: Board, elapsedTime: Float): BoardUpdateResult {
    return when (board.state) {
        Board.State.Placing -> {
            animUpdate(board.placingAnim, elapsedTime)

            val placingAnimDone = !board.placingAnim.running
            if (placingAnimDone) {
                board.state = Board.State.None
                val linesCleared = boardFindFilledLines(board)
                BoardUpdateResult.Placing(linesCleared)
            } else {
                BoardUpdateResult.None
            }
        }

        Board.State.Clearing -> {
            val result = BoardUpdateResult.Clearing(
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

            if (result.allDone) {
                board.state = Board.State.None
            }

            result
        }

        Board.State.None -> BoardUpdateResult.None
    }
}

fun boardRender(board: Board, gameBoardUi: Container, layout: Layout) {
    val r = Platform.renderer
    r.save()

    val boardCenterX = gameBoardUi.posX + gameBoardUi.width / 2f
    val boardCenterY = gameBoardUi.posY + gameBoardUi.height / 2f

    val placingAnimRunning = board.placingAnim.running

    if (placingAnimRunning) {
        val scale = keyframeCurrent(board.placingAnim, shrinkKeyframe)
        r.scale(scale, scale, boardCenterX, boardCenterY)
    }

    r.drawRoundRect(
        gameBoardUi.posX, gameBoardUi.posY,
        gameBoardUi.width, gameBoardUi.height,
        UI_RADIUS, Color.ink,
    )

    r.translate(gameBoardUi.posX, gameBoardUi.posY)

    // board cells
    for (row in 0 until CELLS_COUNT) {
        val layout = layout
        val rowy = layout.boardPadding + row * layout.cellSize
        for (col in 0 until CELLS_COUNT) {
            val x = layout.boardPadding + col * layout.cellSize
            var y = rowy

            val idx = coordsToIdx(col, row)
            val cell = board.cells[idx]

            if (cell.filled) {
                cellRender(layout, x, y, color = cell.color)
            }
        }
    }

    if (board.state == Board.State.Clearing) {
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

    r.restore()
}

class Announcer {
    companion object {
        const val TEXT_SIZE = 48f
        const val BANNER_HEIGHT = TEXT_SIZE + 20f + 20f
        const val DURATION = 0.7f

        val scaleFrames = arrayOf(
            Keyframe(0.2f, 0.5f, 1.2f, AnimationEasing.EaseOutSquared),
            Keyframe(0.4f, 1.2f, 1f, AnimationEasing.EaseOutSquared),
            Keyframe(0.9f, 1f, 1f, AnimationEasing.Linear),
            Keyframe(1f, 1f, 2f, AnimationEasing.EaseOutSquared),
        )
        val alphaFrames = arrayOf(
            Keyframe(0.2f, 0f, 1f, AnimationEasing.EaseOutSquared),
            Keyframe(0.9f, 1f, 1f, AnimationEasing.Linear),
            Keyframe(1f, 1f, 0f, AnimationEasing.EaseOutSquared),
        )
    }

    var text = ""
    var bannerY = 0f
    var bannerWidth = 0f
    val pos = Vec2(0f, 0f)
    val center = Vec2(0f, 0f)
    val anim = Anim()
}

fun announcerAnnounce(announcer: Announcer, clearCount: Int, elapsedTime: Float) {
    announcer.text = when (clearCount) {
        2 -> "Double!"
        3 -> "Triple!"
        4 -> "Quadruple!"
        5 -> "Quintuple!"
        6 -> "Sextuple!"
        7 -> "Septuple!"
        8 -> "Octuple!"
        else -> return
    }
    animBegin(announcer.anim, Announcer.DURATION, elapsedTime)
}

fun announcerUpdate(
    announcer: Announcer,
    screenWidth: Float,
    boardPos: Vec2,
    boardSize: Float,
    elapsedTime: Float,
): Boolean {
    animUpdate(announcer.anim, elapsedTime)

    if (!announcer.anim.running) {
        return true
    }

    val boardCenter = boardPos + boardSize / 2
    val textWidth = textMeasure(announcer.text, Announcer.TEXT_SIZE)

    announcer.pos.x = boardCenter.x - textWidth / 2
    announcer.pos.y = boardCenter.y + Announcer.TEXT_SIZE / 2

    announcer.center.x = boardCenter.x
    announcer.center.y = boardCenter.y

    announcer.bannerWidth = screenWidth
    announcer.bannerY = announcer.center.y - Announcer.BANNER_HEIGHT / 2

    return false
}

fun announcerRender(announcer: Announcer) {
    val r = Platform.renderer

    r.save()
    r.rotate(3f, announcer.center.x, announcer.center.y)

    val scale = keyframeCurrent(announcer.anim, Announcer.scaleFrames)
    if (scale != 1f) {
        r.scale(scale, scale, announcer.center.x, announcer.center.y)
    }

    val alpha = run {
        val a = keyframeCurrent(announcer.anim, Announcer.alphaFrames)
        (a * 255).toInt()
    }
    val shadowColor = Color.addAlpha(alpha, Color.black)

    // background
    run {
        val x = -300f
        val y = announcer.bannerY
        val width = announcer.bannerWidth + 600f
        val height = Announcer.BANNER_HEIGHT

        val clr = Color.addAlpha(alpha, Color.purple)
        r.drawRect(x, y, width, height, clr)
        r.strokeRoundRect(x, y, width, height, 0f, shadowColor, STROKE_WIDTH)
    }

    // text
    run {
        val text = announcer.text
        val pos = announcer.pos
        textRender(text, pos.x + SHADOW_OFFSET, pos.y + SHADOW_OFFSET, shadowColor, Announcer.TEXT_SIZE)
        textRender(text, pos.x, pos.y, Color.addAlpha(alpha, Color.yellow), Announcer.TEXT_SIZE)
        textStroke(text, pos.x, pos.y, STROKE_WIDTH, shadowColor, Announcer.TEXT_SIZE)
    }

    r.restore()
}

class Layout {
    var boardSize = 0f
    var boardPadding = 0f
    var cellSize = 0f
    var cellPadding = 0f
    var cellRadius = 0f
}

fun layoutUpdate(layout: Layout, boardSize: Float) {
    layout.boardSize = boardSize
    layout.boardPadding = boardSize * PLAYGROUND_PADDING_FRACTION
    layout.cellSize = (boardSize - layout.boardPadding * 2) / CELLS_COUNT
    layout.cellPadding = layout.cellSize * CELL_PADDING_FRACTION
    layout.cellRadius = layout.cellSize * CELL_RADIUS_FRACTION
}

fun coordsToPos(layout: Layout, coords: Coords): Vec2 {
    return coordsToPos(layout, coords.toVec2())
}

fun coordsToPos(layout: Layout, coords: Vec2): Vec2 {
    val x = coords.x * layout.cellSize + layout.boardPadding
    val y = coords.y * layout.cellSize + layout.boardPadding
    return Vec2(x, y)
}

sealed interface GameState {
    data object Countdown : GameState
    data object Placing : GameState
    data object Board : GameState
    data object Announcer : GameState
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

    var state: GameState = GameState.Countdown
    val countdown = Countdown(30f, 60f)
    val board = Board()
    val shapesBag = ShapesBag()
    var currentShape = CurrentShape(Shape())
    var nextShape: Shape? = null
    val announcer = Announcer()

    var shapesPlaced = 0
    var phase = 0

    var roundDuration = 0f
    var roundStart = 0f
    var roundEnd = 0f

    var clearStreak = 0
    var noClearStreak = 0
    var comboMultiplier = 1
    var score = 0
    var queuedComboMultiplierUpdates = 0
    var queuedClearStreakMultiplierUpdates = 0

    // anims

    val screenShakeAnim = Anim()

    val scoreAnim = Anim()
    var scoreCurrent = 0f
    var scoreFrom = 0f
    var scoreTarget = 0f

    var comboMultiplierVisual = 1
    var comboMultiplierIncreased = false
    val comboMultiplierAnim = Anim()
    var clearStreakMultiplierIncreased = false
    val clearStreakMultiplierAnim = Anim()
}

fun gameAnimateScore(game: GameContext, scoreChange: Int) {
    animBegin(game.scoreAnim, 0.1f, game.elapsedTime)
    game.scoreFrom = game.scoreCurrent
    game.scoreTarget += scoreChange
}

fun gamePlaceShape(game: GameContext, forced: Boolean) {
    var forcedPlacement = false

    if (forced) {
        val availableCoords = currentShapeAvailableCoords(
            game.currentShape.shape,
            game.currentShape.rotation,
            game.currentShape.projectionCoords,
            game.board.cells,
        )
        if (availableCoords == null) {
            game.screen = GameScreen.GameOver
            return
        }

        if (availableCoords != game.currentShape.projectionCoords) {
            game.currentShape.projectionCoords = availableCoords
            forcedPlacement = true
        }
    }

    var cellCount = boardPlaceShape(game.board, game.currentShape, game.elapsedTime)
    val scoreChange = if (forcedPlacement) {
        cellCount * -10
    } else {
        val streakMult = if (game.clearStreak > 0) game.clearStreak else 1
        cellCount * game.comboMultiplier * streakMult
    }

    game.score += scoreChange
    gameAnimateScore(game, scoreChange)

    game.currentShape.initialized = false

    game.shapesPlaced += 1
    game.phase = game.shapesPlaced / 10
    game.roundEnd = game.elapsedTime
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

    if (game.ui.nodesArenaCount > 0) {
        uiProcessInput(game.ui, touch, game.elapsedTime)

        if (game.screen == GameScreen.Start) {
            // start button
            if (uiButtonReleased(game.ui, "start_button")) {
                game.screen = GameScreen.Playing
            }
        } else if (game.screen == GameScreen.Playing) {
            val currentShape = game.currentShape
            if (game.state == GameState.Placing && currentShape.initialized) {
                // place
                var placed = false
                if (uiButtonReleased(game.ui, "button_place")) {
                    if (!currentShape.overlapping) {
                        gamePlaceShape(game, false)
                        game.state = GameState.Board
                        placed = true
                    }
                }

                if (!placed) {
                    // rotate

                    if (uiButtonReleased(game.ui, "button_rotate")) {
                        currentShapeRotate(currentShape, game.elapsedTime)

                        val board = uiGetNode(game.ui, "game_board")
                        currentShapeUpdateProjection(
                            currentShape,
                            board,
                            game.layout,
                            game.board.cells,
                            game.elapsedTime,
                            true,
                        )
                    }

                    // drag
                    if (!touch.consumed) {
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

        if (game.state == GameState.Placing) {
            if (!game.currentShape.initialized) {
                val (gameOver, newShape) = currentShapeSpawn(
                    shapesBagNext(game.shapesBag, game.shapesPlaced),
                    game.board,
                    game.elapsedTime
                )
                game.roundStart = game.elapsedTime

                if (gameOver) {
                    game.screen = GameScreen.GameOver
                    return
                }

                game.currentShape = newShape

                game.roundDuration = currentRoundDuration(game.shapesPlaced)
                game.nextShape = shapesBagPeek(game.shapesBag)
            } else {
                if (game.roundStart + game.roundDuration < game.elapsedTime) {
                    gamePlaceShape(game, true)
                }

                animUpdate(game.currentShape.projectionAnim, game.elapsedTime)
                animUpdate(game.currentShape.rotationAnim, game.elapsedTime)
            }
        }

        // ----------
        // board upate

        when (val r = boardUpdate(game.board, game.elapsedTime)) {
            is BoardUpdateResult.None -> Unit

            is BoardUpdateResult.Placing -> {
                val linesCleared = r.linesFilled

                if (linesCleared > 0) {
                    game.noClearStreak = 0

                    if (linesCleared > 1) {
                        game.comboMultiplier += linesCleared
                        game.queuedComboMultiplierUpdates += linesCleared
                    }

                    game.clearStreak += 1
                    game.clearStreakMultiplierIncreased = true

                    if (linesCleared == 1 && game.clearStreak > 1) {
                        game.queuedClearStreakMultiplierUpdates += 1
                    }

                    var totalMultiplier = game.comboMultiplier
                    if (game.clearStreak > 0) {
                        totalMultiplier *= game.clearStreak
                    }

                    val scoreUpdates = linesCleared * CELLS_COUNT
                    val scoreChange = scoreUpdates * CELL_CLEAR_REWARD * totalMultiplier
                    game.score += scoreChange

                    if (linesCleared > 1) {
                        game.state = GameState.Announcer
                        announcerAnnounce(game.announcer, linesCleared, game.elapsedTime)
                    } else {
                        boardClearLines(game.board, game.elapsedTime)
                    }
                } else {
                    if (game.noClearStreak >= 5 && game.comboMultiplier > 1) {
                        game.comboMultiplier -= 1
                        game.comboMultiplierVisual -= 1

                        game.comboMultiplierIncreased = false
                        animBegin(game.comboMultiplierAnim, 0.2f, game.elapsedTime)
                    }

                    if (game.noClearStreak >= 3 && game.clearStreak > 0) {
                        game.clearStreak = 0
                        game.clearStreakMultiplierIncreased = false
                        animBegin(game.clearStreakMultiplierAnim, 0.2f, game.elapsedTime)
                    }

                    game.noClearStreak += 1
                    game.state = GameState.Placing
                }
            }

            is BoardUpdateResult.Clearing -> {
                if (r.linePopped && game.queuedComboMultiplierUpdates > 0) {
                    game.queuedComboMultiplierUpdates -= 1
                    game.comboMultiplierVisual += 1
                    game.comboMultiplierIncreased = true
                    animBegin(game.comboMultiplierAnim, 0.2f, game.elapsedTime)
                    animBegin(game.screenShakeAnim, 0.1f, game.elapsedTime)
                }

                if (r.linePopped && game.queuedClearStreakMultiplierUpdates > 0) {
                    game.queuedClearStreakMultiplierUpdates -= 1
                    animBegin(game.clearStreakMultiplierAnim, 0.2f, game.elapsedTime)
                    if (!game.screenShakeAnim.running) {
                        animBegin(game.screenShakeAnim, 0.1f, game.elapsedTime)
                    }
                }

                if (r.cellsFadedOut > 0) {
                    var totalMultiplier = game.comboMultiplier
                    if (game.clearStreak > 0) {
                        totalMultiplier *= game.clearStreak
                    }

                    val totalChange = r.cellsFadedOut * CELL_CLEAR_REWARD * totalMultiplier
                    gameAnimateScore(game, totalChange)
                }

                if (r.allDone) {
                    game.state = GameState.Placing
                }
            }
        }

        // announcer
        if (game.state == GameState.Announcer) {
            val boardNode = uiGetNode(game.ui, "game_board")
            val boardPos = Vec2(boardNode.posX, boardNode.posY)

            val done = announcerUpdate(
                game.announcer,
                game.ui.logicalWidth,
                boardPos,
                boardNode.width,
                game.elapsedTime,
            )
            if (done) {
                boardClearLines(game.board, game.elapsedTime)
                game.state = GameState.Board
            }
        }

        // screen shake

        if (game.screenShakeAnim.running) {
            animUpdate(game.screenShakeAnim, game.elapsedTime)
        }
    }

    buildHUD(game)
}

fun gameRender(game: GameContext) {
    val r = Platform.renderer

    r.save()
    r.scale(game.scale, game.scale)

    if (game.screenShakeAnim.running) {
        val gameBoard = uiGetNode(game.ui, "game_board")
        val boardCenterX = gameBoard.posX + gameBoard.width / 2f
        val boardCenterY = gameBoard.posY + gameBoard.height / 2f
        val keyframes = arrayOf(
            Keyframe(0.25f, 0f, 1f, AnimationEasing.Linear),
            Keyframe(0.5f, 1f, -1f, AnimationEasing.Linear),
            Keyframe(0.75f, -1f, 0.5f, AnimationEasing.Linear),
            Keyframe(1f, 0.5f, 0f, AnimationEasing.Linear),
        )
        val angle = keyframeCurrent(game.screenShakeAnim, keyframes)
        r.rotate(angle, boardCenterX, boardCenterY)
    }

    // ------------
    // HUD

    uiRender(game.ui, game.ui.root)

    // --------------
    // Game

    if (game.screen == GameScreen.Playing) {
        val gameBoardUi = uiGetNode(game.ui, "game_board")
        boardRender(game.board, gameBoardUi, game.layout)

        if (game.state == GameState.Countdown) {
            countdownRender(game.countdown, gameBoardUi)
        } else {
            // next shape
            game.nextShape?.let { nextShape ->
                val nextShapeDock = uiGetNode(game.ui, "next_shape_dock")

                val dockX = nextShapeDock.posX
                val dockY = nextShapeDock.posY
                val dockWidth = nextShapeDock.width
                val dockHeight = nextShapeDock.height

                val shapeRotation = getShapeRotation(nextShape.reference, 0)

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
                    cellRender(game.layout, x, y, cellSize, cellPadding, cellRadius, nextShape.color)
                }
            }

            // current shape
            val shapeDockUi = uiGetNode(game.ui, "current_shape")
            currentShapeRender(game.currentShape, shapeDockUi, gameBoardUi, game.layout, game.state)
        }


        if (game.state == GameState.Announcer) {
            announcerRender(game.announcer)
        }
    }

    r.restore()
}
