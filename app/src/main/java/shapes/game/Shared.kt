package shapes.game

// TODO: maybe rewrite these into value class so they do not allocate

data class Vec2(var x: Float, var y: Float) {
    operator fun plus(other: Vec2): Vec2 {
        return Vec2(x + other.x, y + other.y)
    }

    operator fun minus(other: Vec2): Vec2 {
        return Vec2(x - other.x, y - other.y)
    }

    operator fun times(other: Vec2): Vec2 {
        return Vec2(x * other.x, y * other.y)
    }

    operator fun div(other: Vec2): Vec2 {
        return Vec2(x / other.x, y / other.y)
    }

    operator fun plus(other: Float): Vec2 {
        return Vec2(x + other, y + other)
    }

    operator fun minus(other: Float): Vec2 {
        return Vec2(x - other, y - other)
    }

    operator fun times(scale: Float): Vec2 {
        return Vec2(x * scale, y * scale)
    }

    operator fun div(scale: Float): Vec2 {
        return Vec2(x / scale, y / scale)
    }

    fun toCoords(): Coords {
        return Coords(x.toInt(), y.toInt())
    }
}

data class Coords(var col: Int, var row: Int) {
    operator fun plus(other: Coords): Coords {
        return Coords(col + other.col, row + other.row)
    }

    operator fun minus(other: Coords): Coords {
        return Coords(col - other.col, row - other.row)
    }

    operator fun times(other: Coords): Coords {
        return Coords(col * other.col, row * other.row)
    }

    operator fun div(other: Coords): Coords {
        return Coords(col / other.col, row / other.row)
    }

    operator fun plus(other: Int): Coords {
        return Coords(col + other, row + other)
    }

    operator fun minus(other: Int): Coords {
        return Coords(col - other, row - other)
    }

    operator fun times(other: Int): Coords {
        return Coords(col * other, row * other)
    }

    operator fun div(other: Int): Coords {
        return Coords(col / other, row / other)
    }

    fun toVec2(): Vec2 {
        return Vec2(col.toFloat(), row.toFloat())
    }
}

data class Touch(
    var isDown: Boolean = false,
    var position: Vec2 = Vec2(0f, 0f),
    var startPosition: Vec2 = Vec2(0f, 0f),
)
