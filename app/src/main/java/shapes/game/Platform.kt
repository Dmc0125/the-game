package shapes.game

data class Touch(
    var isDown: Boolean = false,
    var position: Vec2 = Vec2(0f, 0f),
    var startPosition: Vec2 = Vec2(0f, 0f),
)

data class Rect(
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Float = 0f,
    var height: Float = 0f,
)

interface FontWeight {
    object ExtraLight : FontWeight
    object Light : FontWeight
    object Regular : FontWeight
    object Medium : FontWeight
    object SemiBold : FontWeight
    object Bold : FontWeight
    object ExtraBold : FontWeight
}

fun FontWeight.string(): String = when (this) {
    FontWeight.ExtraLight -> "ExtraLight"
    FontWeight.Light -> "Light"
    FontWeight.Regular -> "Regular"
    FontWeight.Medium -> "Medium"
    FontWeight.SemiBold -> "SemiBold"
    FontWeight.Bold -> "Bold"
    FontWeight.ExtraBold -> "ExtraBold"
    else -> throw IllegalArgumentException("Unknown FontWeight: $this")
}

interface Renderer {
    fun save()
    fun restore()
    fun translate(x: Float, y: Float)
    fun rotate(angle: Float, x: Float, y: Float)

    fun drawRoundRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int)
    fun drawRoundRect(rect: Rect, radius: Float, color: Int)

    fun strokeRoundRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int, strokeWidth: Float)

    fun drawRect(x: Float, y: Float, width: Float, height: Float, color: Int)
    fun drawRect(rect: Rect, color: Int)

    fun measureText(text: String, textSize: Float, fontWeight: FontWeight, font: String): Float
    fun drawText(text: String, x: Float, y: Float, color: Int, textSize: Float, fontWeight: FontWeight, font: String)
    fun strokeText(
        text: String,
        x: Float,
        y: Float,
        strokeWidth: Float,
        color: Int,
        textSize: Float,
        fontWeight: FontWeight,
        font: String
    )

    object Default : Renderer {
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(x: Float, y: Float) = Unit
        override fun rotate(angle: Float, x: Float, y: Float) = Unit

        override fun drawRoundRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) = Unit
        override fun drawRoundRect(rect: Rect, radius: Float, color: Int) = Unit

        override fun strokeRoundRect(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            radius: Float,
            color: Int,
            strokeWidth: Float
        ) = Unit

        override fun drawRect(x: Float, y: Float, width: Float, height: Float, color: Int) = Unit
        override fun drawRect(rect: Rect, color: Int) = Unit

        override fun measureText(text: String, textSize: Float, fontWeight: FontWeight, font: String): Float = 0f
        override fun drawText(
            text: String,
            x: Float,
            y: Float,
            color: Int,
            textSize: Float,
            fontWeight: FontWeight,
            font: String,
        ) =
            Unit

        override fun strokeText(
            text: String,
            x: Float,
            y: Float,
            strokeWidth: Float,
            color: Int,
            textSize: Float,
            fontWeight: FontWeight,
            font: String
        ) = Unit
    }
}
