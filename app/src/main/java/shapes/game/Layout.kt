package shapes.game

fun uiPosInside(ui: Container, x: Float, y: Float): Boolean {
    val xInside = x >= ui.posX && x <= ui.posX + ui.width
    val yInside = y >= ui.posY && y <= ui.posY + ui.height
    return xInside && yInside
}

sealed interface Size {
    data object Fit : Size
    data object FillMax : Size
    data class FillMaxF(val factor: Float) : Size
    data class Abs(val value: Float) : Size
}

enum class ContainerDirection {
    None,
    Row,
    Col,
}

enum class Alignment {
    Start,
    Center,
    End,
}

sealed interface UiModifier {
    data object None : UiModifier
    data class Box(
        val bgColor: Int,
        val radius: Float = 0f,
    ) : UiModifier

    data class Text(
        val text: String,
        val textSize: Float = 0f,
        val fontWeight: FontWeight = FontWeight.Regular,
        val font: String = FONT_SUPPLY_CENTER,
        val textColor: Int,
    ) : UiModifier

    data class Card(val bgColor: Int, val radius: Float = UI_RADIUS) : UiModifier

    data class Button(
        val bgColor: Int,
        val radius: Float = UI_RADIUS,
    ) : UiModifier
}

enum class ScaleOrigin {
    Center,
}

data class Modifiers(
    var width: Size = Size.Fit,
    var height: Size = Size.Fit,

    var paddingLeft: Float = 0f,
    var paddingTop: Float = 0f,
    var paddingRight: Float = 0f,
    var paddingBottom: Float = 0f,

    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var scaleOriginX: ScaleOrigin = ScaleOrigin.Center,
    var scaleOriginY: ScaleOrigin = ScaleOrigin.Center,

    // ui
    var ui: UiModifier = UiModifier.None,
)

fun mPaddingVertical(m: Modifiers, padding: Float): Modifiers {
    m.paddingTop = padding
    m.paddingBottom = padding
    return m
}

fun mPaddingHorizontal(m: Modifiers, padding: Float): Modifiers {
    m.paddingLeft = padding
    m.paddingRight = padding
    return m
}

fun uiAdvance(ui: Container) {
    val parent = ui.parent ?: return

    if (parent.direction == ContainerDirection.Row) {
        parent.cursorX += ui.width

        parent.contentWidth += ui.width
        parent.contentHeight = kotlin.math.max(parent.contentHeight, ui.height)
    } else if (parent.direction == ContainerDirection.Col) {
        parent.cursorY += ui.height

        parent.contentWidth = kotlin.math.max(parent.contentWidth, ui.width)
        parent.contentHeight += ui.height
    }
}

fun uiAvailableWidth(ui: Container, requested: Size): Float {
    return when (requested) {
        Size.Fit -> 0f
        Size.FillMax -> ui.width - ui.cursorX - ui.modifiers.paddingRight
        is Size.FillMaxF -> {
            val available = ui.width - ui.cursorX - ui.modifiers.paddingRight
            requested.factor * available
        }

        is Size.Abs -> requested.value
    }
}

fun uiAvailableHeight(ui: Container, requested: Size): Float {
    return when (requested) {
        Size.Fit -> 0f
        Size.FillMax -> ui.height - ui.cursorY - ui.modifiers.paddingBottom
        is Size.FillMaxF -> {
            val available = ui.height - ui.cursorY - ui.modifiers.paddingBottom
            requested.factor * available
        }

        is Size.Abs -> requested.value
    }
}

fun innerWidth(ui: Container): Float {
    return ui.modifiers.paddingLeft + ui.contentWidth + ui.modifiers.paddingRight
}

fun innerHeight(ui: Container): Float {
    return ui.modifiers.paddingTop + ui.contentHeight + ui.modifiers.paddingBottom
}

class UiButtonState {
    val anim = Anim()
    var pressed = false
    var clicked = false
    var offsetFrom = 0f
    var offsetTo = 0f
    var offsetCurrent = 0f
}

data class UiContext(
    var logicalWidth: Float = 0f,
    var logicalHeight: Float = 0f,

    var root: Container = Container(),
    var current: Container? = null,
) {
    val buttons = mutableMapOf<String, UiButtonState>()
    val nodesArena = Array(256) { Container() }
    var nodesArenaCount: Int = 0
}

fun uiAllocNode(ctx: UiContext): Container {
    val n = ctx.nodesArena[ctx.nodesArenaCount]
    ctx.nodesArenaCount += 1
    return n
}

fun uiGetNode(ctx: UiContext, id: String): Container {
    for (i in 0..<ctx.nodesArenaCount) {
        val n = ctx.nodesArena[i]
        if (n.id == id) {
            return n
        }
    }
    throw IllegalArgumentException("Node not found: $id")
}

fun uiArrange(ctx: UiContext, container: Container) {
    when (container.direction) {
        ContainerDirection.Col -> {
            var cursorX = container.posX + container.modifiers.paddingLeft
            var cursorY = when (container.alignment) {
                Alignment.Start -> container.posY + container.modifiers.paddingTop
                Alignment.Center -> container.posY + (container.height - container.contentHeight) / 2 - container.modifiers.paddingTop
                Alignment.End -> container.posY + container.height - container.contentHeight - container.modifiers.paddingBottom
            }
            for (i in 0..<container.childrenCount) {
                val childNodeIdx = container.children[i]
                val child = ctx.nodesArena[childNodeIdx]
                child.posX = cursorX
                child.posY = cursorY
                uiArrange(ctx, child)
                cursorY += child.height
            }
        }

        ContainerDirection.Row -> {
            var cursorY = container.posY + container.modifiers.paddingTop
            var cursorX = when (container.alignment) {
                Alignment.Start -> container.posX + container.modifiers.paddingLeft
                Alignment.Center -> container.posX + (container.width - container.contentWidth) / 2 - container.modifiers.paddingLeft
                Alignment.End -> container.posX + container.width - container.contentWidth - container.modifiers.paddingLeft
            }
            for (i in 0..<container.childrenCount) {
                val childNodeIdx = container.children[i]
                val child = ctx.nodesArena[childNodeIdx]
                child.posX = cursorX
                child.posY = cursorY
                uiArrange(ctx, child)
                cursorX += child.width
            }
        }

        ContainerDirection.None -> Unit
    }
}

fun uiRender(ctx: UiContext, node: Container) {
    fun childrenRender(node: Container) {
        for (i in 0..<node.childrenCount) {
            val child = ctx.nodesArena[node.children[i]]
            uiRender(ctx, child)
        }
    }

    val r = Platform.renderer

    val doScale = node.modifiers.scaleX != 1f || node.modifiers.scaleY != 1f
    if (doScale) {
        r.save()

        val scaleOriginX = when (node.modifiers.scaleOriginX) {
            ScaleOrigin.Center -> node.posX + node.width / 2f
        }
        val scaleOriginY = when (node.modifiers.scaleOriginY) {
            ScaleOrigin.Center -> node.posY + node.height / 2f
        }

        r.scale(
            node.modifiers.scaleX, node.modifiers.scaleY,
            scaleOriginX, scaleOriginY,
        )
    }

    when (val ui = node.modifiers.ui) {
        UiModifier.None -> childrenRender(node)

        is UiModifier.Box -> {
            val x = node.posX
            val y = node.posY
            val width = node.width
            val height = node.height

            r.drawRoundRect(x, y, width, height, ui.radius, ui.bgColor)

            childrenRender(node)
        }

        is UiModifier.Text -> {
            val x = node.posX
            val y = node.posY + node.height - node.modifiers.paddingTop
            Platform.renderer.drawText(
                ui.text,
                x, y,
                ui.textColor,
                ui.textSize,
                ui.fontWeight,
                ui.font,
            )
        }

        is UiModifier.Card -> {
            val x = node.posX
            val y = node.posY
            val width = node.width
            val height = node.height

            // shadow
            r.drawRoundRect(x + SHADOW_OFFSET, y + SHADOW_OFFSET, width, height, ui.radius, Color.black)
            // card
            r.drawRoundRect(x, y, width, height, ui.radius, ui.bgColor)
            r.strokeRoundRect(x, y, width, height, ui.radius, Color.black, STROKE_WIDTH)

            childrenRender(node)
        }

        is UiModifier.Button -> {
            val x = node.posX
            val y = node.posY
            val width = node.width
            val height = node.height

            val buttonState = ctx.buttons[node.id]
            check(buttonState != null)

            buttonState.offsetCurrent = animCurrent(
                buttonState.anim,
                buttonState.offsetFrom,
                buttonState.offsetTo,
                ::lerp,
                AnimationEasing.EaseOutSquared,
            )

            r.save()
            r.translate(buttonState.offsetCurrent, buttonState.offsetCurrent)

            // shadow
            r.save()
            r.translate(SHADOW_OFFSET - buttonState.offsetCurrent, SHADOW_OFFSET - buttonState.offsetCurrent)
            r.drawRoundRect(x, y, width, height, ui.radius, Color.black)
            r.restore()

            // button
            r.drawRoundRect(x, y, width, height, ui.radius, ui.bgColor)
            r.strokeRoundRect(x, y, width, height, ui.radius, Color.black, STROKE_WIDTH)

            // children
            childrenRender(node)

            r.restore()
        }
    }

    if (doScale) {
        r.restore()
    }
}

fun uiProcessInput(ctx: UiContext, touch: Touch, elapsedTime: Float) {
    if (touch.consumed) return

    nodes@ for (idx in ctx.nodesArenaCount - 1 downTo 0) {
        val node = ctx.nodesArena[idx]

        when (val ui = node.modifiers.ui) {
            is UiModifier.Button -> {
                val buttonState = ctx.buttons[node.id]
                check(buttonState != null) { "Button state not found for node ${node.id}" }
                buttonState.clicked = false

                val justPressed = touch.action == TouchAction.Down &&
                        !buttonState.pressed &&
                        uiPosInside(node, touch.position.x, touch.position.y)

                if (justPressed) {
                    buttonState.pressed = true

                    if (buttonState.anim.running) {
                        buttonState.offsetFrom = buttonState.offsetCurrent
                    } else {
                        buttonState.offsetFrom = 0f
                    }
                    buttonState.offsetTo = SHADOW_OFFSET
                    animBegin(buttonState.anim, 0.2f, elapsedTime)

                    touch.consumed = true
                    break@nodes
                } else if (touch.action == TouchAction.Up && buttonState.pressed) {
                    buttonState.pressed = false
                    buttonState.clicked = true

                    if (buttonState.anim.running) {
                        buttonState.offsetFrom = buttonState.offsetCurrent
                    } else {
                        buttonState.offsetFrom = SHADOW_OFFSET
                    }
                    buttonState.offsetTo = 0f
                    animBegin(buttonState.anim, 0.2f, elapsedTime)

                    touch.consumed = true
                    break@nodes
                }
            }

            else -> Unit
        }
    }
}

fun uiButtonReleased(ui: UiContext, id: String): Boolean {
    val buttonState = ui.buttons[id]
    check(buttonState != null) { "Button not found: $id" }

    val clicked = buttonState.clicked
    buttonState.clicked = false
    return clicked
}

fun uiRootInit(
    ctx: UiContext,
    elapsedTime: Float,
    modifiers: Modifiers = Modifiers(),
    verticalAlignment: Alignment = Alignment.Start,
) {
    for (bs in ctx.buttons.values) {
        animUpdate(bs.anim, elapsedTime)
    }

    ctx.nodesArenaCount = 0
    ctx.current = null

    modifiers.width = Size.Abs(ctx.logicalWidth)
    modifiers.height = Size.Abs(ctx.logicalHeight)

    val id = "root"
    val root = uiAllocNode(ctx)
    containerInit(ctx, root, ContainerDirection.Col, id, modifiers)
    root.width = ctx.logicalWidth
    root.height = ctx.logicalHeight
    root.alignment = verticalAlignment

    ctx.root = root
}

fun uiRootEnd(ctx: UiContext) {
    uiColEnd(ctx)

    val root = ctx.root
    root.cursorX = root.posX + root.modifiers.paddingLeft
    root.cursorY = root.posY + root.modifiers.paddingTop
    uiArrange(ctx, root)
}

class Container {
    var posX: Float = 0f
    var posY: Float = 0f
    var modifiers: Modifiers = Modifiers()
    var id: String = ""
    var parent: Container? = null

    // computed
    var cursorX: Float = 0f
    var cursorY: Float = 0f

    var width: Float = 0f
    var height: Float = 0f

    var contentWidth: Float = 0f
    var contentHeight: Float = 0f

    var direction: ContainerDirection = ContainerDirection.None
    var alignment: Alignment = Alignment.Start

    val children = Array(10) { 0 }
    var childrenCount: Int = 0
}

fun containerInit(
    ctx: UiContext,
    ui: Container,
    direction: ContainerDirection,
    id: String = "",
    modifiers: Modifiers = Modifiers(),
) {
    val parent = ctx.current

    ui.id = id
    ui.direction = direction
    ui.modifiers = modifiers

    ui.cursorX = modifiers.paddingLeft
    ui.cursorY = modifiers.paddingTop

    ui.parent = parent

    if (parent != null) {
        ui.width = when (val requested = modifiers.width) {
            Size.Fit -> 0f
            Size.FillMax -> parent.width - parent.cursorX - parent.modifiers.paddingRight
            is Size.FillMaxF -> {
                val available = parent.width - parent.cursorX - parent.modifiers.paddingRight
                requested.factor * available
            }

            is Size.Abs -> requested.value
        }
        ui.height = when (val requested = modifiers.height) {
            Size.Fit -> 0f
            Size.FillMax -> parent.height - parent.cursorY - parent.modifiers.paddingBottom
            is Size.FillMaxF -> {
                val available = parent.height - parent.cursorY - parent.modifiers.paddingBottom
                requested.factor * available
            }

            is Size.Abs -> requested.value
        }

        // current idx
        parent.children[parent.childrenCount] = ctx.nodesArenaCount - 1
        parent.childrenCount += 1
    } else {
        ui.width = 0f
        ui.height = 0f
    }

    ui.contentWidth = 0f
    ui.contentHeight = 0f
    ui.childrenCount = 0

    if (modifiers.ui is UiModifier.Button) {
        check(id != "") { "button id must be set" }
        if (ctx.buttons[id] == null) {
            ctx.buttons[id] = UiButtonState()
        }
    }

    ctx.current = ui
}

fun uiRowBegin(
    ctx: UiContext,
    modifiers: Modifiers = Modifiers(),
    horizontalAlignment: Alignment = Alignment.Start,
    id: String = "",
) {
    val rowUi = uiAllocNode(ctx)
    containerInit(ctx, rowUi, ContainerDirection.Row, id, modifiers)
    rowUi.alignment = horizontalAlignment
}

fun uiRowEnd(ctx: UiContext) {
    val ui = ctx.current
    check(ui != null) { "current ui is null" }
    check(ui.direction == ContainerDirection.Row) { "current ui is not a row" }

    if (ui.modifiers.width == Size.Fit) {
        ui.width = innerWidth(ui)
    }

    if (ui.modifiers.height == Size.Fit) {
        ui.height = innerHeight(ui)
    }

    uiAdvance(ui)
    ctx.current = ui.parent
}

fun uiColBegin(
    ctx: UiContext,
    modifiers: Modifiers = Modifiers(),
    verticalAlignment: Alignment = Alignment.Start,
    id: String = "",
) {
    val colUi = uiAllocNode(ctx)
    containerInit(ctx, colUi, ContainerDirection.Col, id, modifiers)
    colUi.alignment = verticalAlignment
}

fun uiColEnd(ctx: UiContext) {
    val ui = ctx.current
    check(ui != null) { "current ui is null" }
    check(ui.direction == ContainerDirection.Col) { "current ui is not a col" }

    if (ui.modifiers.width == Size.Fit) {
        ui.width = innerWidth(ui)
    }

    if (ui.modifiers.height == Size.Fit) {
        ui.height = innerHeight(ui)
    }

    uiAdvance(ui)
    ctx.current = ui.parent
}

fun uiText(
    ctx: UiContext,
    modifiers: Modifiers,
    id: String = "",
) {
    val textUi = uiAllocNode(ctx)
    containerInit(ctx, textUi, ContainerDirection.None, id, modifiers)

    check(modifiers.ui is UiModifier.Text)
    val mui = modifiers.ui as UiModifier.Text

    if (modifiers.width == Size.Fit) {
        textUi.width += modifiers.paddingLeft
        textUi.width += Platform.renderer.measureText(
            mui.text,
            mui.textSize,
            mui.fontWeight,
            mui.font,
        )
        textUi.width += modifiers.paddingRight
    }

    if (modifiers.height == Size.Fit) {
        textUi.height += modifiers.paddingTop
        textUi.height += mui.textSize
        textUi.height += modifiers.paddingBottom
    }

    uiAdvance(textUi)
    ctx.current = textUi.parent
}
