package shapes.android

import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import shapes.game.FontWeight
import shapes.game.onScoreChange

private fun Modifier.forwardUncomsumedTouches(onTouch: (PointerEvent) -> Unit): Modifier {
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)

                var skip = false
                for (change in event.changes) {
                    if (change.isConsumed) {
                        skip = true
                        break
                    }
                }

                if (!skip) {
                    onTouch(event)
                }
            }
        }
    }
}

sealed interface TimerCommand {
    data class Start(val commandCount: Int, val duration: Float) : TimerCommand
    data object Stop : TimerCommand
}

@Stable
class TimerController {
    var commandCount = 0
    var command by mutableStateOf<TimerCommand?>(null)

    fun stop() {
        command = TimerCommand.Stop
    }

    fun start(duration: Float) {
        command = TimerCommand.Start(commandCount, duration)
        commandCount += 1
    }
}

data class MultiplierChange(
    val commandCount: Int,
    val amount: Int,
)

data class ScoreChange(
    val commandCount: Int,
    val amount: Int,
)

@Composable
fun RollingScore(
    modifier: Modifier,
    scoreChange: ScoreChange,
) {
    val displayedScore = remember { Animatable(0f) }
    val endScore = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(scoreChange) {
        if (scoreChange.commandCount > 0) {
            val remaining = endScore.floatValue - displayedScore.value
            endScore.floatValue += scoreChange.amount

            displayedScore.animateTo(
                displayedScore.value + scoreChange.amount + remaining,
                tween(200, easing = FastOutSlowInEasing),
            )
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .appShadow()
            .background(Color(shapes.game.Color.blue), RoundedCornerShape(RADIUS.dp))
            .border(BORDER_WIDTH.dp, Color.Black, RoundedCornerShape(RADIUS.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text("SCORE", fontSize = 10)
        val score = displayedScore.value.toInt()
        Column(Modifier.fillMaxSize(), Arrangement.Center) {
            Text("%06d".format(score), fontSize = 32)
        }
    }
}

@Composable
fun GameScreen(
    gameOver: Boolean,
    onGameOver: () -> Unit,
    onPlayAgain: () -> Unit,
) {
    val gameView = remember { mutableStateOf<GameView?>(null) }

    if (shapes.BuildConfig.DEBUG) {
        DisposableEffect(Unit) {
            val handler: (DebugAction) -> Unit = { action ->
                when (action) {
                    DebugAction.ExplodeCell -> gameView.value?.debugExplodeCell()
                    DebugAction.FillRow -> gameView.value?.debugFillRow()
                    DebugAction.SpawnSingleCellShape -> gameView.value?.debugSpawnShape()
                    DebugAction.PlaceShape -> gameView.value?.handleShapePlace()
                    DebugAction.FillDouble -> gameView.value?.debugFillDouble()
                    DebugAction.Announce -> gameView.value?.debugAnnounce()
                }
            }
            DebugActions.register(handler)
            onDispose { DebugActions.unregister(handler) }
        }
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 48.dp)
            .fillMaxSize()
            .forwardUncomsumedTouches { gameView.value?.handleTouch(it) },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // top bar

        val topBarHeight = 90.dp
        val nextShapeView = remember { mutableStateOf<NextShapeView?>(null) }
        val scoreChange = remember { mutableStateOf<ScoreChange>(ScoreChange(0, 0)) }

        Row(
            modifier = Modifier.fillMaxWidth().height(topBarHeight),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            RollingScore(
                Modifier.fillMaxHeight().weight(2f),
                scoreChange.value,
            )

            // next shape
            Column(
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .appShadow()
                    .background(Color(shapes.game.Color.ink), RoundedCornerShape(RADIUS.dp))
                    .border(BORDER_WIDTH.dp, Color.Black, RoundedCornerShape(RADIUS.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text("NEXT", fontSize = 10, Color(shapes.game.Color.vanilla))
                Row(Modifier.fillMaxSize().padding(vertical = 4.dp), Arrangement.Center) {
                    AndroidView(
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                        factory = { context ->
                            NextShapeView(context).also {
                                nextShapeView.value = it
                            }
                        }
                    )
                }
            }
        }

        val timerController = remember { TimerController() }
        val multiplierChange = remember { mutableStateOf(MultiplierChange(0, 0)) }

        // timer + mult

        Row(
            Modifier.fillMaxWidth(),
            Arrangement.spacedBy(10.dp),
            Alignment.CenterVertically,
        ) {
            val progress = remember { Animatable(0f) }
            val duration = remember { mutableFloatStateOf(0f) }
            val initialColor = remember { Color(shapes.game.Color.lime) }
            val color = remember {
                Animatable(
                    initialColor,
                    typeConverter = Color.VectorConverter(initialColor.colorSpace),
                )
            }

            LaunchedEffect(timerController.command) {
                when (val cmd = timerController.command) {
                    is TimerCommand.Start -> {
                        duration.value = cmd.duration
                        coroutineScope {
                            launch {
                                progress.snapTo(1f)
                                progress.animateTo(
                                    0f, tween(
                                        (cmd.duration * 1000f).toInt(),
                                        easing = LinearEasing,
                                    )
                                )
                            }
                            launch {
                                color.snapTo(initialColor)
                                color.animateTo(
                                    Color(shapes.game.Color.red), tween(
                                        (cmd.duration * 1000f).toInt(),
                                        easing = LinearEasing,
                                    )
                                )
                            }
                        }
                    }

                    TimerCommand.Stop -> progress.stop()
                    null -> Unit
                }
            }

            // remaining seconds

            Box(Modifier.weight(0.75f)) {
                val remaining = progress.value * duration.value
                Text("%.01fs".format(remaining), 10)
            }

            // bar

            Box(
                Modifier
                    .weight(6f)
                    .height(12.dp)
                    .background(Color(shapes.game.Color.ink), RoundedCornerShape(6.dp))
                    .padding(3.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .background(color.value, RoundedCornerShape(3.dp)),
                )
            }

            // multiplier
            val multiplier = remember { mutableIntStateOf(1) }
            val multScale = remember { Animatable(1f) }

            LaunchedEffect(multiplierChange.value) {
                if (multiplierChange.value.commandCount == 0) return@LaunchedEffect

                val amount = multiplierChange.value.amount
                multiplier.intValue += amount

                var scaleTo = if (amount > 0) 1.3f else 0.8f
                multScale.animateTo(scaleTo, tween(durationMillis = 50))
                multScale.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }

            Box(
                Modifier
                    .weight(2f)
                    .height(32.dp)
                    .graphicsLayer {
                        scaleX = multScale.value
                        scaleY = multScale.value
                    }
                    .appShadow(cornerRadius = 10.dp)
                    .background(Color(shapes.game.Color.red), RoundedCornerShape(10.dp))
                    .border(3.dp, Color(shapes.game.Color.black), RoundedCornerShape(10.dp)),
                Alignment.Center,
            ) {
                Text("x${multiplier.intValue} MULT", 11)
            }
        }

        // game

        fun onMultiplierChange(amount: Int) {
            multiplierChange.value = MultiplierChange(
                commandCount = multiplierChange.value.commandCount + 1,
                amount = amount,
            )
        }

        fun onScoreChange(amount: Int) {
            scoreChange.value = ScoreChange(
                commandCount = scoreChange.value.commandCount + 1,
                amount = amount,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer { clip = false },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize().graphicsLayer { clip = false },
                factory = { context ->
                    GameView(
                        context,
                        onMultiplierChange = ::onMultiplierChange,
                        onScoreChange = ::onScoreChange,
                        onPlaceShape = { timerController.stop() },
                        onRoundStart = { shapeIdx, roundDuration ->
                            nextShapeView.value?.onNextShape(shapeIdx)
                            timerController.start(roundDuration)
                        },
                        onGameOver = {
                            onGameOver()
                            timerController.stop()
                        },
                    ).also {
                        gameView.value = it
                        it.resume()
                    }
                },
                onRelease = { gameView -> gameView.pause() },
            )

            if (gameOver) {
                // var previousHighScore = Storage.highScore()
                // if (score.intValue > previousHighScore) {
                //     Storage.saveHighScore(score.intValue)
                //     previousHighScore = score.intValue
                // }

                // Column(
                //     modifier = Modifier
                //         .fillMaxSize()
                //         .background(
                //             Color(shapes.game.Color.addAlpha(200, shapes.game.Color.ink)),
                //             RoundedCornerShape(RADIUS.dp)
                //         ),
                //     horizontalAlignment = Alignment.CenterHorizontally,
                //     verticalArrangement = Arrangement.Center,
                // ) {
                //     BasicText(
                //         text = "Out of place",
                //         style = TextStyle(
                //             fontSize = 32.sp,
                //             color = Color.White,
                //             fontFamily = AppFont.family(
                //                 shapes.game.FONT_MANROPE, FontWeight.Bold
                //             ),
                //         )
                //     )

                //     BasicText(
                //         text = "Score: ${score.intValue}",
                //         style = TextStyle(
                //             fontSize = 16.sp,
                //             color = Color.White,
                //             fontFamily = AppFont.family(
                //                 shapes.game.FONT_MANROPE, FontWeight.Bold
                //             ),
                //         )
                //     )
                //     BasicText(
                //         text = "High score: ${previousHighScore}",
                //         style = TextStyle(
                //             fontSize = 16.sp,
                //             color = Color.White,
                //             fontFamily = AppFont.family(
                //                 shapes.game.FONT_MANROPE, FontWeight.Bold
                //             ),
                //         )
                //     )

                //     Button(
                //         text = "Play again",
                //         onClick = {
                //             onPlayAgain()
                //         },
                //     )
                // }
            }
        }

        GameControls(
            onRotate = { gameView.value?.handleShapeRotate() },
            onPlace = { gameView.value?.handleShapePlace() },
        )
    }
}


@Composable
fun GameControls(
    onRotate: () -> Unit,
    onPlace: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AppButton(
            Modifier.weight(1f),
            onClick = onRotate,
            horizontalPadding = 0.dp,
            verticalPadding = 0.dp,
        ) {
            Box(Modifier.fillMaxWidth().height(90.dp), Alignment.Center) {
                Text("R", fontSize = 16, Color(shapes.game.Color.ink))
            }
        }

        AppButton(
            Modifier.weight(2f),
            onClick = onPlace,
            horizontalPadding = 0.dp,
            verticalPadding = 0.dp,
        ) {
            Box(Modifier.fillMaxWidth().height(90.dp), Alignment.Center) {
                Text("Place", fontSize = 16, Color(shapes.game.Color.ink))
            }
        }
    }
}
