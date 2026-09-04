package shapes.game

import kotlin.math.pow

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

    fun brighten(color: Int, amount: Int): Int = Color.argb(
        Color.a(color),
        (Color.r(color) + amount).coerceIn(0, 255),
        (Color.g(color) + amount).coerceIn(0, 255),
        (Color.b(color) + amount).coerceIn(0, 255),
    )

    fun darken(color: Int, amount: Int): Int = Color.argb(
        Color.a(color),
        (Color.r(color) - amount).coerceIn(0, 255),
        (Color.g(color) - amount).coerceIn(0, 255),
        (Color.b(color) - amount).coerceIn(0, 255),
    )
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

fun cellRender(
    layout: Layout,
    x: Float,
    y: Float,
    size: Float = layout.cellSize,
    padding: Float = layout.cellPadding,
    radius: Float = layout.cellRadius,
    color: Int,
) {
    val r = Platform.renderer

    val strokeColor = Color.brighten(color, 25)
    val lightColor = Color.brighten(color, 20)
    val darkColor = Color.darken(color, 40)

    val innerx = x + padding
    val innery = y + padding
    val innerWidth = size - padding * 2
    val innerHeight = size - padding * 2

    r.drawRoundRect(
        innerx, innery,
        innerWidth, innerHeight,
        radius,
        lightColor,
    )
    val offset = layout.cellSize * 0.1f
    r.drawRoundRect(
        innerx + offset, innery + offset,
        innerWidth - offset, innerHeight - offset,
        radius,
        darkColor,
    )
    r.drawRoundRect(
        innerx + offset, innery + offset,
        innerWidth - offset * 2, innerHeight - offset * 2,
        radius,
        color,
    )
    r.strokeRoundRect(
        innerx + offset, innery + offset,
        innerWidth - offset * 2, innerHeight - offset * 2,
        radius,
        strokeColor,
        1f,
    )
}

// UI

fun verticalSpacer(ui: UiContext, size: Float = 30f) {
    uiRowBegin(ui, Modifiers(height = Size.Abs(size)))
    uiRowEnd(ui)
}

fun horizontalSpacer(ui: UiContext, size: Float = 30f) {
    uiRowBegin(ui, Modifiers(width = Size.Abs(size)))
    uiRowEnd(ui)
}

fun buildTopBar(game: GameContext) {
    val ui = game.ui

    uiRowBegin(ui, Modifiers(width = Size.FillMax))

    run {
        val topBarWidth = game.layout.boardSize
        var topBarHeight = 0f
        val scoreBoardWidth = topBarWidth * 0.7f
        val spacing = topBarWidth * 0.05f

        run {
            // score

            val m = Modifiers(
                width = Size.Abs(scoreBoardWidth),
                paddingTop = 10f,
                ui = UiModifier.Card(Color.blue),
            )
            mPaddingHorizontal(m, 20f)
            uiColBegin(ui, m)
            topBarHeight += m.paddingTop

            run {
                val uim = UiModifier.Text(text = "score", textSize = 8f, textColor = Color.black)
                uiText(ui, Modifiers(ui = uim))
                topBarHeight += uim.textSize
            }

            run {
                animUpdate(game.scoreAnim, game.elapsedTime)
                game.scoreCurrent = animCurrent(
                    game.scoreAnim,
                    game.scoreFrom,
                    game.scoreTarget,
                    ::lerp,
                    AnimationEasing.EaseOutSquared,
                )

                val uim = UiModifier.Text(
                    text = "%06d".format(kotlin.math.round(game.scoreCurrent).toInt()),
                    textSize = 24f,
                    textColor = Color.black,
                )
                val m = Modifiers(ui = uim)
                mPaddingVertical(m, 14f)
                uiText(ui, m)

                topBarHeight += uim.textSize + m.paddingTop + m.paddingBottom
            }

            uiColEnd(ui)
        }

        horizontalSpacer(ui, spacing)

        // mutlipliers
        uiColBegin(ui, Modifiers(Size.FillMax, Size.Abs(topBarHeight)))

        run {
            val blockHeight = topBarHeight * 0.42f
            val spacing = topBarHeight - (blockHeight * 2f)
            val radius = 13f

            var clearStreakScale = 1f
            if (game.clearStreakMultiplierAnim.running) {
                animUpdate(game.clearStreakMultiplierAnim, game.elapsedTime)
                if (game.clearStreakMultiplierIncreased) {
                    clearStreakScale = keyframeCurrent(game.clearStreakMultiplierAnim, popKeyframe)
                } else {
                    clearStreakScale = keyframeCurrent(game.clearStreakMultiplierAnim, shrinkKeyframe)
                }
            }

            uiRowBegin(
                ui,
                Modifiers(
                    Size.FillMax,
                    Size.Abs(blockHeight),
                    scaleX = clearStreakScale,
                    scaleY = clearStreakScale,
                    ui = UiModifier.Card(Color.yellow, radius),
                ),
                horizontalAlignment = Alignment.Center,
            )
            uiColBegin(ui, Modifiers(height = Size.FillMax), verticalAlignment = Alignment.Center)
            uiText(
                ui, Modifiers(
                    ui = UiModifier.Text(
                        text = "${game.clearStreak}x streak",
                        textSize = 8f,
                        textColor = Color.black,
                    )
                )
            )
            uiColEnd(ui)
            uiRowEnd(ui)

            verticalSpacer(ui, spacing)

            var multiplierScale = 1f
            if (game.comboMultiplierAnim.running) {
                animUpdate(game.comboMultiplierAnim, game.elapsedTime)
                if (game.comboMultiplierIncreased) {
                    multiplierScale = keyframeCurrent(game.comboMultiplierAnim, popKeyframe)
                } else {
                    multiplierScale = keyframeCurrent(game.comboMultiplierAnim, shrinkKeyframe)
                }
            }

            uiRowBegin(
                ui,
                Modifiers(
                    Size.FillMax,
                    Size.Abs(blockHeight),
                    scaleX = multiplierScale,
                    scaleY = multiplierScale,
                    ui = UiModifier.Card(Color.red, radius),
                ),
                horizontalAlignment = Alignment.Center,
            )
            uiColBegin(ui, Modifiers(height = Size.FillMax), verticalAlignment = Alignment.Center)
            uiText(
                ui, Modifiers(
                    ui = UiModifier.Text(
                        text = "${game.comboMultiplierVisual}x combo",
                        textSize = 8f,
                        textColor = Color.black,
                    )
                )
            )
            uiColEnd(ui)
            uiRowEnd(ui)
        }

        uiColEnd(ui)
    }

    uiRowEnd(ui)
}

fun buildStatusBar(game: GameContext) {
    val ui = game.ui

    run {
        val statusBarWidth = game.layout.boardSize
        val statusBarHeight = 12f

        val remainingTimeWidth = 40f
        val phaseWidth = 60f
        val timerBarWidth = statusBarWidth - remainingTimeWidth - phaseWidth

        uiRowBegin(ui, Modifiers(width = Size.FillMax, height = Size.Abs(statusBarHeight)))

        run {
            // remainig time
            var remainingTime = 0f
            var progress = 0f

            if (game.state == GameState.Placing) {
                val elapsed = game.elapsedTime - game.roundStart
                remainingTime = game.roundDuration - elapsed
                progress = remainingTime / game.roundDuration
            } else if (game.state != GameState.Countdown) {
                val elapsed = game.roundEnd - game.roundStart
                remainingTime = game.roundDuration - elapsed
                progress = remainingTime / game.roundDuration
            }

            run {
                uiColBegin(ui, Modifiers(height = Size.FillMax), verticalAlignment = Alignment.Center)
                val m = Modifiers(
                    ui = UiModifier.Text(
                        text = "%02.01fs".format(remainingTime),
                        textSize = 8f,
                        textColor = Color.black,
                    ),
                    width = Size.Abs(remainingTimeWidth)
                )
                uiText(ui, m)
                uiColEnd(ui)
            }

            // bar

            run {
                uiColBegin(
                    ui,
                    Modifiers(Size.Abs(timerBarWidth), Size.Abs(statusBarHeight)),
                    verticalAlignment = Alignment.Center
                )

                val m = Modifiers(width = Size.FillMax, height = Size.Abs(12f))
                mPaddingHorizontal(m, 3f)
                mPaddingVertical(m, 3f)
                m.ui = UiModifier.Box(bgColor = Color.ink, radius = 6f)
                uiRowBegin(ui, m)

                run {
                    val t = run {
                        if (progress > 0.5f) {
                            return@run 0f
                        }

                        val p = progress / 0.5f

                        val waves = 7f
                        val acceleration = 5f
                        val phase = 2f * kotlin.math.PI * waves * (1f - p).pow(acceleration)
                        val o = 1f - kotlin.math.cos(phase).toFloat()
                        o / 2
                    }
                    val color = lerpColor(Color.lime, Color.red, t)

                    // inner bar
                    uiRowBegin(
                        ui,
                        Modifiers(
                            Size.FillMaxF(progress),
                            Size.FillMax,
                            ui = UiModifier.Box(bgColor = color, radius = 3f),
                        ),
                    )
                    uiRowEnd(ui)
                }

                uiRowEnd(ui)
                uiColEnd(ui)
            }

            // phase

            run {
                uiColBegin(
                    ui,
                    Modifiers(width = Size.Abs(phaseWidth), height = Size.Abs(statusBarHeight)),
                    verticalAlignment = Alignment.Center,
                )
                uiRowBegin(
                    ui,
                    Modifiers(Size.Abs(phaseWidth)),
                    horizontalAlignment = Alignment.End,
                )

                uiText(
                    ui,
                    Modifiers(
                        ui = UiModifier.Text(
                            text = "Phase ${game.phase}",
                            textSize = 8f,
                            textColor = Color.black,
                        )
                    ),
                )

                uiRowEnd(ui)
                uiColEnd(ui)
            }
        }

        uiRowEnd(ui)
    }
}

fun buildBottomBar(game: GameContext) {
    val ui = game.ui

    uiColBegin(ui, Modifiers(Size.FillMax, Size.FillMax), verticalAlignment = Alignment.End)
    uiRowBegin(
        ui,
        Modifiers(width = Size.FillMax, height = Size.FillMaxF(0.7f)),
        horizontalAlignment = Alignment.Center,
    )

    run {
        val width = game.layout.boardSize
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
            m.ui = UiModifier.Card(bgColor = Color.ink, radius = 20f)
            uiColBegin(ui, m)

            run {
                uiText(
                    ui,
                    Modifiers(
                        ui = UiModifier.Text(
                            text = "Next",
                            textSize = 8f,
                            textColor = Color.white,
                        )
                    ),
                )

                verticalSpacer(ui, 8f)

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

        horizontalSpacer(ui, spacing)

        // current shape
        val m = Modifiers(
            width = Size.Abs(componentSize),
            height = Size.Abs(componentSize),
            ui = UiModifier.Card(Color.ink),
        )
        uiRowBegin(ui, m, id = "current_shape")
        uiRowEnd(ui)

        horizontalSpacer(ui, spacing)

        // controls
        uiRowBegin(
            ui,
            Modifiers(width = Size.Abs(componentSize), height = Size.Abs(componentSize)),
            horizontalAlignment = Alignment.End,
        )

        run {
            uiColBegin(ui, Modifiers(width = Size.FillMaxF(0.8f)))

            val buttonHeight = componentSize * 0.4f
            val spacing = (componentSize - buttonHeight * 2f)
            val textSize = 10f
            val paddingTop = (buttonHeight - textSize) / 2f

            uiRowBegin(
                ui,
                Modifiers(
                    Size.FillMax,
                    Size.Abs(buttonHeight),
                    paddingTop = paddingTop,
                    ui = UiModifier.Button(bgColor = Color.white)
                ),
                Alignment.Center,
                id = "button_rotate",
            )
            uiText(
                ui,
                Modifiers(ui = UiModifier.Text(text = "R", textSize = textSize, textColor = Color.ink)),
            )
            uiRowEnd(ui)

            verticalSpacer(ui, spacing)

            uiRowBegin(
                ui,
                Modifiers(
                    Size.FillMax,
                    Size.Abs(buttonHeight),
                    paddingTop = paddingTop,
                    ui = UiModifier.Button(bgColor = Color.white)
                ),
                Alignment.Center,
                id = "button_place",
            )
            uiText(
                ui,
                Modifiers(ui = UiModifier.Text(text = "Place", textSize = textSize, textColor = Color.ink)),
                id = "button_place_text"
            )
            uiRowEnd(ui)

            uiColEnd(ui)
        }

        uiRowEnd(ui)
    }

    uiRowEnd(ui)
    uiColEnd(ui)
}

fun buildHUD(game: GameContext) {
    val ui = game.ui

    game.rootModifiers.ui = UiModifier.Box(Color.vanilla)
    uiRootInit(ui, game.elapsedTime, game.rootModifiers)

    if (game.screen == GameScreen.Start) {
        uiColBegin(
            ui,
            Modifiers(width = Size.FillMax, height = Size.FillMax),
            verticalAlignment = Alignment.End,
        )

        // start button
        run {
            val m = Modifiers(width = Size.FillMax)
            mPaddingHorizontal(m, 24f)
            mPaddingVertical(m, 20f)
            m.ui = UiModifier.Button(Color.blue)
            uiRowBegin(ui, m, id = "start_button")

            uiText(
                ui,
                Modifiers(
                    ui = UiModifier.Text(
                        text = "start",
                        textSize = 20f,
                        textColor = Color.white,
                    ),
                ),
            )

            uiRowEnd(ui)
        }

        uiColEnd(ui)
    } else if (game.screen == GameScreen.Playing) {
        buildTopBar(game)
        verticalSpacer(ui)
        buildStatusBar(game)
        verticalSpacer(ui)

        // --------------
        // game board
        uiRowBegin(
            ui,
            Modifiers(
                width = Size.Abs(game.layout.boardSize),
                height = Size.Abs(game.layout.boardSize),
            ),
            id = "game_board",
        )
        uiRowEnd(ui)

        buildBottomBar(game)
    }

    uiRootEnd(ui)
}
