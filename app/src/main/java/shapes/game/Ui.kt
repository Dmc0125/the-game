package shapes.game

const val FONT_SUPPLY_CENTER = "supplycenter"

const val UI_RADIUS = 24f
const val STROKE_WIDTH = 3f
const val SHADOW_OFFSET = 4f

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

fun textRender(text: String, x: Float, y: Float, color: Int, textSize: Float) {
    Platform.renderer.drawText(text, x, y, color, textSize, FontWeight.Regular, FONT_SUPPLY_CENTER)
}

fun textMeasure(text: String, textSize: Float): Float {
    return Platform.renderer.measureText(text, textSize, FontWeight.Regular, FONT_SUPPLY_CENTER)
}

fun textStroke(text: String, x: Float, y: Float, strokeWidth: Float, color: Int, textSize: Float) {
    Platform.renderer.strokeText(text, x, y, strokeWidth, color, textSize, FontWeight.Regular, FONT_SUPPLY_CENTER)
}
