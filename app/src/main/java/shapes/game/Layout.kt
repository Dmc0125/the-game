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

data class Modifiers(
    var width: Size = Size.Fit,
    var height: Size = Size.Fit,

    var paddingLeft: Float = 0f,
    var paddingTop: Float = 0f,
    var paddingRight: Float = 0f,
    var paddingBottom: Float = 0f,

    // text
    var text: String = "",
    var textSize: Float = 0f,
    var fontWeight: FontWeight = FontWeight.Regular,
    var font: String = FONT_SUPPLY_CENTER,
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

data class UiContext(
    var logicalWidth: Float = 0f,
    var logicalHeight: Float = 0f,

    var root: Container = Container(),
    var current: Container? = null,
    val nodes: MutableMap<String, Container> = mutableMapOf(),
)

fun uiGetNode(ctx: UiContext, id: String): Container {
    return ctx.nodes[id] ?: throw IllegalArgumentException("Node not found: $id")
}

fun uiRootInit(
    ctx: UiContext,
    modifiers: Modifiers = Modifiers(),
    verticalAlignment: Alignment = Alignment.Start,
) {
    ctx.nodes.clear()
    ctx.current = null

    modifiers.width = Size.Abs(ctx.logicalWidth)
    modifiers.height = Size.Abs(ctx.logicalHeight)

    val id = "root"
    val root = Container()
    containerInit(ctx, root, ContainerDirection.Col, id, modifiers)
    root.width = ctx.logicalWidth
    root.height = ctx.logicalHeight
    root.alignment = verticalAlignment

    ctx.root = root
}

fun uiArrange(container: Container) {
    when (container.direction) {
        ContainerDirection.Col -> {
            var cursorX = container.posX + container.modifiers.paddingLeft
            var cursorY = when (container.alignment) {
                Alignment.Start -> container.posY + container.modifiers.paddingTop
                Alignment.Center -> container.posY + (container.height - container.contentHeight) / 2 - container.modifiers.paddingTop
                Alignment.End -> container.posY + container.height - container.contentHeight - container.modifiers.paddingBottom
            }
            for (i in 0..<container.childrenCount) {
                val child = container.children[i] ?: break
                child.posX = cursorX
                child.posY = cursorY
                uiArrange(child)
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
                val child = container.children[i] ?: break
                child.posX = cursorX
                child.posY = cursorY
                uiArrange(child)
                cursorX += child.width
            }
        }

        ContainerDirection.None -> Unit
    }
}

fun uiRootEnd(ctx: UiContext) {
    uiColEnd(ctx)

    val root = ctx.root
    root.cursorX = root.posX + root.modifiers.paddingLeft
    root.cursorY = root.posY + root.modifiers.paddingTop
    uiArrange(root)
}

class Container(
    var posX: Float = 0f,
    var posY: Float = 0f,

    var modifiers: Modifiers = Modifiers(),
    var parent: Container? = null,
) {
    var id: String = ""

    // computed
    var cursorX: Float = 0f
    var cursorY: Float = 0f

    var width: Float = 0f
    var height: Float = 0f

    var contentWidth: Float = 0f
    var contentHeight: Float = 0f

    var direction: ContainerDirection = ContainerDirection.None
    var alignment: Alignment = Alignment.Start

    val children: Array<Container?> = Array(10) { null }
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

        parent.children[parent.childrenCount] = ui
        parent.childrenCount += 1
    } else {
        ui.width = 0f
        ui.height = 0f
    }

    if (id != "") {
        ctx.nodes[id] = ui
    }
    ctx.current = ui
}

fun uiRowBegin(
    ctx: UiContext,
    modifiers: Modifiers = Modifiers(),
    horizontalAlignment: Alignment = Alignment.Start,
    id: String = "",
) {
    val rowUi = Container()
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
    val colUi = Container()
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
    val textUi = Container()
    containerInit(ctx, textUi, ContainerDirection.None, id, modifiers)

    if (modifiers.width == Size.Fit) {
        textUi.width += modifiers.paddingLeft
        textUi.width += Platform.renderer.measureText(
            modifiers.text,
            modifiers.textSize,
            modifiers.fontWeight,
            modifiers.font,
        )
        textUi.width += modifiers.paddingRight
    }

    if (modifiers.height == Size.Fit) {
        textUi.height += modifiers.paddingTop
        textUi.height += modifiers.textSize
        textUi.height += modifiers.paddingBottom
    }

    uiAdvance(textUi)
    ctx.current = textUi.parent
}
