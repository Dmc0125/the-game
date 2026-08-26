package shapes.android

import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import shapes.game.FONT_MANROPE
import shapes.game.FONT_SUPPLY_CENTER
import shapes.game.FontWeight

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
        val score = remember { mutableStateOf(0) }

        Row(
            modifier = Modifier.fillMaxWidth().height(topBarHeight),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // score
            Column(
                Modifier
                    .fillMaxHeight()
                    .weight(2f)
                    .appShadow()
                    .background(Color(shapes.game.Color.blue), RoundedCornerShape(RADIUS.dp))
                    .border(BORDER_WIDTH.dp, Color.Black, RoundedCornerShape(RADIUS.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text("SCORE", fontSize = 10)
                Column(Modifier.fillMaxSize(), Arrangement.Center) {
                    Text("%06d".format(score.value), fontSize = 32)
                }
            }

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

        // timer

        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(20.dp)) {
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

            Box(Modifier.width(30.dp)) {
                val remaining = progress.value * duration.value
                Text("%.01fs".format(remaining), 10)
            }

            // bar

            Box(
                Modifier
                    .fillMaxWidth()
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
        }

        // game

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
                        onScoreChange = { score.value = it },
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
                var previousHighScore = Storage.highScore()
                if (score.value > previousHighScore) {
                    Storage.saveHighScore(score.value)
                    previousHighScore = score.value
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color(shapes.game.Color.addAlpha(200, shapes.game.Color.ink)),
                            RoundedCornerShape(RADIUS.dp)
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BasicText(
                        text = "Out of place",
                        style = TextStyle(
                            fontSize = 32.sp,
                            color = Color.White,
                            fontFamily = AppFont.family(
                                shapes.game.FONT_MANROPE, FontWeight.Bold
                            ),
                        )
                    )

                    BasicText(
                        text = "Score: ${score.value}",
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = Color.White,
                            fontFamily = AppFont.family(
                                shapes.game.FONT_MANROPE, FontWeight.Bold
                            ),
                        )
                    )
                    BasicText(
                        text = "High score: ${previousHighScore}",
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = Color.White,
                            fontFamily = AppFont.family(
                                shapes.game.FONT_MANROPE, FontWeight.Bold
                            ),
                        )
                    )

                    Button(
                        text = "Play again",
                        onClick = {
                            onPlayAgain()
                        },
                    )
                }
            }
        }

        GameControls(
            onRotate = { gameView.value?.handleShapeRotate() },
            onPlace = { gameView.value?.handleShapePlace() },
        )
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
