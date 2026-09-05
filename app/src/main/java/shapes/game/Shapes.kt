package shapes.game

import kotlin.random.Random

data class ShapeRotation(
    val offsets: Array<Coords> = arrayOf(),
    var minCol: Int = Int.MAX_VALUE,
    var maxCol: Int = Int.MIN_VALUE,
    var minRow: Int = Int.MAX_VALUE,
    var maxRow: Int = Int.MIN_VALUE,
    var cols: Int = 0,
    var rows: Int = 0,
)

fun newShape(base: Array<Coords>): Array<ShapeRotation> {
    fun rotate(s: ShapeRotation) {
        for (offset in s.offsets) {
            val temp = offset.col
            offset.col = -offset.row
            offset.row = temp
        }
    }

    fun center(s: ShapeRotation) {
        // normalize
        var minCol = Int.MAX_VALUE
        var minRow = Int.MAX_VALUE
        for (offset in s.offsets) {
            minCol = kotlin.math.min(minCol, offset.col)
            minRow = kotlin.math.min(minRow, offset.row)
        }

        var width = 0
        var height = 0

        for (offset in s.offsets) {
            offset.col -= minCol
            offset.row -= minRow

            width = kotlin.math.max(width, offset.col)
            height = kotlin.math.max(height, offset.row)
        }

        width += 1
        height += 1

        // center
        val centerColOffset = (SHAPE_CELLS_COUNT - width) / 2
        val centerRowOffset = (SHAPE_CELLS_COUNT - height) / 2

        for (offset in s.offsets) {
            offset.col += centerColOffset
            offset.row += centerRowOffset

            s.minCol = kotlin.math.min(s.minCol, offset.col)
            s.maxCol = kotlin.math.max(s.maxCol, offset.col)
            s.minRow = kotlin.math.min(s.minRow, offset.row)
            s.maxRow = kotlin.math.max(s.maxRow, offset.row)
        }

        s.cols = s.maxCol - s.minCol + 1
        s.rows = s.maxRow - s.minRow + 1
    }

    val r1 = ShapeRotation(base)
    center(r1)

    val r2 = ShapeRotation(r1.offsets.map { it.copy() }.toTypedArray())
    rotate(r2)
    center(r2)

    val r3 = ShapeRotation(r2.offsets.map { it.copy() }.toTypedArray())
    rotate(r3)
    center(r3)

    val r4 = ShapeRotation(r3.offsets.map { it.copy() }.toTypedArray())
    rotate(r4)
    center(r4)

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

data class ShapeReference(var role: ShapeRole = ShapeRole.Recovery, var idx: Int = -1)

fun getShapeRotation(shape: ShapeReference, rotation: Int): ShapeRotation {
    val shapes = SHAPES[shape.role]
    checkNotNull(shapes) { "role: ${shape.role} idx: ${shape.idx}" }
    val s = shapes[shape.idx]
    return s[rotation % 4]
}

class Bag<T>(val items: Array<T>) {
    var next = -1
}

fun <T> bagNext(bag: Bag<T>, generator: (items: Array<T>, start: Int, end: Int) -> Unit): T {
    val bagSize = bag.items.size / 2

    val isFirst = bag.next == -1
    if (isFirst) {
        generator(bag.items, 0, bagSize)
        bag.next = 0
    }

    val isLast = bag.next == bag.items.size - 1
    val isLastInFirstBag = !isLast && bag.next == bagSize - 1

    if (isLast) {
        generator(bag.items, 0, bagSize)
    } else if (isLastInFirstBag) {
        generator(bag.items, bagSize, bag.items.size)
    }

    val item = bag.items[bag.next]
    if (isLast) {
        bag.next = 0
    } else {
        bag.next += 1
    }
    return item
}

fun <T> bagPeek(bag: Bag<T>): T {
    assert(bag.next != -1)
    assert(bag.next < bag.items.size)
    return bag.items[bag.next]
}

class ShapesBag {
    val shapes = Bag(Array(20) { ShapeReference() })
    val colors = Bag(Array(8) { 0 })
}

fun shapesBagInitShapes(
    shapes: Array<ShapeReference>,
    start: Int,
    end: Int,
    phase: Int,
    random: Random = Random.Default,
) {
    // each bag consists of 10 shapes
    // - 4 recovery, 4 normal, 2 hard
    //
    // shapes are randomly selected from their corresponding roles
    //
    // rules:
    // 1. start with 1 recovery
    // 2. 2 hard shapes can not be adjacent
    var currentIdx = start + 0

    fun chooseShape(role: ShapeRole) {
        val shapesMap = SHAPES[role]!!
        shapes[currentIdx].role = role
        shapes[currentIdx].idx = Random.nextInt(shapesMap.size)
        currentIdx += 1
    }

    when {
        phase < 2 -> {
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

        phase < 3 -> {
            chooseShape(ShapeRole.Recovery)
            chooseShape(ShapeRole.Normal)
            chooseShape(ShapeRole.Normal)
            chooseShape(ShapeRole.Recovery)
            chooseShape(ShapeRole.Hard)
            chooseShape(ShapeRole.Normal)
            chooseShape(ShapeRole.Recovery)
            chooseShape(ShapeRole.Hard)
            chooseShape(ShapeRole.Normal)
            chooseShape(ShapeRole.Recovery)
        }

        phase < 4 -> {
            chooseShape(ShapeRole.Recovery)
            chooseShape(ShapeRole.Normal)
            chooseShape(ShapeRole.Hard)
            chooseShape(ShapeRole.Recovery)
            chooseShape(ShapeRole.Hard)
            chooseShape(ShapeRole.Normal)
            chooseShape(ShapeRole.Recovery)
            chooseShape(ShapeRole.Hard)
            chooseShape(ShapeRole.Normal)
            chooseShape(ShapeRole.Recovery)
        }

        else -> {
            chooseShape(ShapeRole.Recovery)
            chooseShape(ShapeRole.Hard)
            chooseShape(ShapeRole.Hard)
            chooseShape(ShapeRole.Recovery)
            chooseShape(ShapeRole.Hard)
            chooseShape(ShapeRole.Normal)
            chooseShape(ShapeRole.Recovery)
            chooseShape(ShapeRole.Hard)
            chooseShape(ShapeRole.Normal)
            chooseShape(ShapeRole.Recovery)
        }
    }
}

fun shapesBagInitColors(colors: Array<Int>, start: Int, end: Int) {
    colors[start] = Color.lime
    colors[start + 1] = Color.yellow
    colors[start + 2] = Color.purple
    colors[start + 3] = Color.blue

    for (i in end - 1 downTo start) {
        val j = Random.nextInt(start, i + 1)
        val temp = colors[i]
        colors[i] = colors[j]
        colors[j] = temp
    }
}

data class Shape(
    val reference: ShapeReference = ShapeReference(),
    val color: Int = 0,
)

fun shapesBagNext(bag: ShapesBag, phase: Int): Shape {
    val nextShape = bagNext(bag.shapes) { items, start, end -> shapesBagInitShapes(items, start, end, phase) }
    val nextColor = bagNext(bag.colors) { items, start, end -> shapesBagInitColors(items, start, end) }
    return Shape(nextShape, nextColor)
}

fun shapesBagPeek(bag: ShapesBag): Shape = Shape(
    bagPeek(bag.shapes),
    bagPeek(bag.colors),
)
