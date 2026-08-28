package shapes.game

import kotlin.random.Random

fun newShape(base: Array<Coords>): Array<Array<Coords>> {
    fun rotate(offsets: Array<Coords>): Array<Coords> {
        val rotated = offsets.map { it.copy() }.toTypedArray()
        for ((i, offset) in offsets.withIndex()) {
            val (col, row) = offset
            rotated[i].col = (SHAPE_CELLS_COUNT - 1) - row
            rotated[i].row = col
        }
        return rotated
    }

    // normalize
    val r1 = base.map { it.copy() }.toTypedArray()
    var minCol = Int.MAX_VALUE
    var minRow = Int.MAX_VALUE
    for (idx in r1.indices) {
        minCol = kotlin.math.min(minCol, r1[idx].col)
        minRow = kotlin.math.min(minRow, r1[idx].row)
    }

    // center
    var width = Int.MIN_VALUE
    var height = Int.MIN_VALUE
    for (idx in r1.indices) {
        r1[idx].col -= minCol
        r1[idx].row -= minRow

        width = kotlin.math.max(width, r1[idx].col)
        height = kotlin.math.max(height, r1[idx].row)
    }

    val centerColOffset = (SHAPE_CELLS_COUNT - width) / 2
    val centerRowOffset = (SHAPE_CELLS_COUNT - height) / 2
    for (idx in r1.indices) {
        r1[idx].col += centerColOffset
        r1[idx].row += centerRowOffset
    }

    val r2 = rotate(r1)
    val r3 = rotate(r2)
    val r4 = rotate(r3)
    return arrayOf(r1, r2, r3, r4)
}

enum class ShapeRole {
    Recovery,
    Normal,
    Hard,
}

val SHAPES = mapOf(
    ShapeRole.Recovery to arrayOf(
        newShape(arrayOf(Coords(1, 1))), // I1x1
        newShape(arrayOf(Coords(1, 1), Coords(1, 2))), // I1x2
        newShape(arrayOf(Coords(1, 1), Coords(2, 1), Coords(1, 2), Coords(2, 2))), // O2x2
        newShape(arrayOf(Coords(1, 1), Coords(1, 2), Coords(2, 2))), // L2x2
        newShape(arrayOf(Coords(1, 1), Coords(1, 2), Coords(1, 3))), // I1x3
        newShape(arrayOf(Coords(0, 0), Coords(1, 0), Coords(2, 0), Coords(1, 1))), // T3x2
    ),
    ShapeRole.Normal to arrayOf(
        newShape(arrayOf(Coords(1, 1), Coords(1, 2), Coords(1, 3), Coords(2, 3), Coords(3, 3))), // L3x3
        newShape(arrayOf(Coords(1, 1), Coords(1, 2), Coords(1, 3), Coords(1, 4))), // I1x4
        newShape(arrayOf(Coords(0, 0), Coords(1, 0), Coords(0, 1), Coords(1, 1), Coords(0, 2), Coords(1, 2))), // O2x3
        newShape(arrayOf(Coords(0, 0), Coords(0, 1), Coords(0, 2), Coords(1, 2))), // L2x3
        newShape(arrayOf(Coords(0, 0), Coords(0, 1), Coords(0, 2), Coords(0, 3), Coords(1, 3))), // L2x4
        newShape(arrayOf(Coords(0, 0), Coords(1, 0), Coords(2, 0), Coords(1, 1), Coords(1, 2))), // T3x3
    ),
    ShapeRole.Hard to arrayOf(
        newShape(
            arrayOf(
                Coords(0, 0), Coords(1, 0), Coords(2, 0),
                Coords(0, 1), Coords(1, 1), Coords(2, 1),
                Coords(0, 2), Coords(1, 2), Coords(2, 2)
            )
        ), // O3x3
        newShape(
            arrayOf(
                Coords(0, 0), Coords(1, 0),
                Coords(0, 1), Coords(1, 1),
                Coords(0, 2), Coords(1, 2),
                Coords(0, 3), Coords(1, 3)
            )
        ), // O2x4
    ),
)

data class Shape(var role: ShapeRole = ShapeRole.Recovery, var idx: Int = -1)

fun shapeOffsets(shape: Shape, rotation: Int): Array<Coords> {
    val shapes = SHAPES[shape.role]
    checkNotNull(shapes) { "role: ${shape.role} idx: ${shape.idx}" }
    val s = shapes[shape.idx]
    return s[rotation % 4]
}

class ShapesBag {
    val shapes = Array(20) { Shape() }
    var next = -1
}

fun shapesBagInit(bagShapes: Array<Shape>, random: Random = Random.Default) {
    // each bag consists of 10 shapes
    // - 4 recovery, 4 normal, 2 hard
    //
    // shapes are randomly selected from their corresponding roles
    //
    // rules:
    // 1. start with 1 recovery
    // 2. 2 hard shapes can not be adjacent
    var currentIdx = 0

    fun chooseShape(role: ShapeRole) {
        val shapes = SHAPES[role]!!
        bagShapes[currentIdx].role = role
        bagShapes[currentIdx].idx = Random.nextInt(shapes.size)
        currentIdx += 1
    }

    chooseShape(ShapeRole.Recovery)
    chooseShape(ShapeRole.Normal)
    chooseShape(ShapeRole.Recovery)
    chooseShape(ShapeRole.Hard)
    chooseShape(ShapeRole.Recovery)
    chooseShape(ShapeRole.Normal)
    chooseShape(ShapeRole.Recovery)
    chooseShape(ShapeRole.Hard)
    chooseShape(ShapeRole.Recovery)
    chooseShape(ShapeRole.Normal)
}

fun shapesBagNext(bag: ShapesBag): Shape {
    val isFirst = bag.next == -1
    if (isFirst) {
        val shapes = bag.shapes.sliceArray(0..<10)
        shapesBagInit(shapes)
        bag.next = 0
    }

    val isLast = bag.next == 19
    if (isLast) {
        val shapes = bag.shapes.sliceArray(0..<10)
        shapesBagInit(shapes)
    } else if (bag.next == 9) {
        val shapes = bag.shapes.sliceArray(10..<20)
        shapesBagInit(shapes)
    }

    val shape = bag.shapes[bag.next]
    if (isLast) {
        bag.next = 0
    } else {
        bag.next += 1
    }
    return shape
}

fun shapesBagPeek(bag: ShapesBag): Shape {
    assert(bag.next != -1)
    assert(bag.next < bag.shapes.size)
    return bag.shapes[bag.next]
}
