package shapes.game

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

fun textRender(uiText: Container, color: Int) {
    textRender(
        uiText.modifiers.text,
        uiText.posX + uiText.modifiers.paddingLeft,
        uiText.posY + uiText.height - uiText.modifiers.paddingTop,
        color,
        uiText.modifiers.textSize,
    )
}

data class Button(
    var bgColor: Int,
    var textColor: Int,
) {
    var pressed: Boolean = false
    var touchUp: Boolean = false
    val anim = Anim()

    var offsetFrom = 0f
    var offsetTo = 0f
    var currentOffset = 0f
}

fun buttonPress(button: Button, elapsedTime: Float) {
    if (button.anim.running) {
        button.offsetFrom = button.currentOffset
    } else {
        button.offsetFrom = 0f
    }
    button.offsetTo = SHADOW_OFFSET

    animBegin(button.anim, 0.2f, elapsedTime)
    button.pressed = true
    button.touchUp = false
}

fun buttonRelease(button: Button, elapsedTime: Float) {
    if (button.anim.running) {
        button.offsetFrom = button.currentOffset
    } else {
        button.offsetFrom = SHADOW_OFFSET
    }
    button.offsetTo = 0f

    animBegin(button.anim, 0.2f, elapsedTime)
    button.pressed = false
    button.touchUp = true
}

fun buttonRender(
    button: Button,
    uiButton: Container,
    uiText: Container,
) {
    val r = Platform.renderer

    button.currentOffset =
        animCurrent(button.anim, button.offsetFrom, button.offsetTo, ::lerp, AnimationEasing.EaseOutSquared)

    r.save()
    r.translate(button.currentOffset, button.currentOffset)

    // button

    run {
        val x = uiButton.posX
        val y = uiButton.posY
        val width = uiButton.width
        val height = uiButton.height

        // shadow

        r.save()
        r.translate(SHADOW_OFFSET - button.currentOffset, SHADOW_OFFSET - button.currentOffset)
        r.drawRoundRect(x, y, width, height, UI_RADIUS, Color.black)
        r.restore()

        //

        r.drawRoundRect(x, y, width, height, UI_RADIUS, button.bgColor)
        r.strokeRoundRect(x, y, width, height, UI_RADIUS, Color.black, STROKE_WIDTH)
    }

    // text

    val x = uiText.posX
    val y = uiText.posY
    textRender(
        uiText.modifiers.text,
        x,
        y + uiText.height,
        button.textColor,
        uiText.modifiers.textSize,
    )

    r.restore()
}

fun cardRender(card: Container, bgColor: Int, radius: Float = UI_RADIUS) {
    val r = Platform.renderer

    val x = card.posX
    val y = card.posY
    val width = card.width
    val height = card.height

    // shadow

    r.drawRoundRect(x + SHADOW_OFFSET, y + SHADOW_OFFSET, width, height, radius, Color.black)

    // card

    r.drawRoundRect(x, y, width, height, radius, bgColor)
    r.strokeRoundRect(x, y, width, height, radius, Color.black, STROKE_WIDTH)
}
