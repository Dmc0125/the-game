package shapes.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import shapes.game.FONT_DMMONO
import shapes.game.FONT_MANROPE
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
fun GameScreen() {
    val gameView = remember { mutableStateOf<GameView?>(null) }

    Column(
        modifier = Modifier
            .padding(start = 20.dp, top = 48.dp, end = 20.dp)
            .fillMaxSize()
            .forwardUncomsumedTouches { gameView.value?.handleTouch(it) },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val nextShapeView = remember { mutableStateOf<NextShapeView?>(null) }
        val score = remember { mutableStateOf(0) }

        TopBar(score.value, onViewReady = { nextShapeView.value = it })

        GameBoard(
            onViewReady = { gameView.value = it },
            onScoreChange = { score.value = it },
            onNextShape = { nextShapeView.value?.onNextShape(it) },
        )

        GameControls(
            onRotate = { gameView.value?.handleShapeRotate() },
            onPlace = { gameView.value?.handleShapePlace() },
        )

        // debug controls

        if (shapes.BuildConfig.DEBUG) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    text = "1x1",
                    onClick = { gameView.value?.debugSpawnShape() },
                )
                Button(
                    modifier = Modifier.weight(1f),
                    text = "Fill row",
                    onClick = { gameView.value?.debugFillRow() },
                )
            }
        }
    }
}

@Composable
fun TopBar(
    score: Int,
    onViewReady: (NextShapeView) -> Unit,
) {
    val height = 90.dp
    val verticalPadding = 12.dp

    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .neobrutalistShadow()
            .background(Color.White, RoundedCornerShape(RADIUS.dp))
            .border(3.dp, Color.Black, RoundedCornerShape(RADIUS.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // score

        Column(
            modifier = Modifier
                .padding(start = 20.dp, top = verticalPadding, bottom = verticalPadding)
                .fillMaxHeight(),
        ) {
            BasicText(
                text = "SCORE",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = AppFont.family(FONT_MANROPE, FontWeight.Medium),
                    color = Color.Black,
                ),
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    text = "%06d".format(score),
                    style = TextStyle(
                        fontSize = 40.sp,
                        fontFamily = AppFont.family(FONT_DMMONO, FontWeight.Medium),
                        color = Color.Black,
                    ),
                )
            }
        }

        // next shape

        Column(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .background(
                    Color(shapes.game.Color.BLACK), RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = RADIUS.dp,
                        bottomStart = 0.dp,
                        bottomEnd = RADIUS.dp,
                    )
                )
                .drawBehind {
                    val width = 3.dp.toPx()
                    drawLine(
                        color = Color.Black,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = width,
                    )
                }
                .padding(verticalPadding),
        ) {
            BasicText(
                text = "NEXT",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = AppFont.family(FONT_MANROPE, FontWeight.Medium),
                    color = Color.White,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    factory = { context ->
                        NextShapeView(context).also {
                            onViewReady(it)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun GameBoard(
    onViewReady: (GameView) -> Unit,
    onScoreChange: (Int) -> Unit,
    onNextShape: (Int) -> Unit,
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .neobrutalistShadow()
            .graphicsLayer {
                clip = false
            },
        factory = { context ->
            GameView(context, onScoreChange, onNextShape).also {
                onViewReady(it)
                it.resume()
            }
        },
        onRelease = { gameView -> gameView.pause() },
    )
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
        Button(
            paddingHorizontal = 32.dp,
            paddingVertical = 32.dp,
            onClick = onRotate,
        ) {
            BasicText(
                text = "R",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = AppFont.family(FONT_MANROPE, FontWeight.Bold),
                    color = Color.Black,
                )
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            paddingHorizontal = 32.dp,
            paddingVertical = 32.dp,
            onClick = onPlace,
        ) {
            BasicText(
                text = "Place",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = AppFont.family(FONT_MANROPE, FontWeight.Bold),
                    color = Color.Black,
                )
            )
        }
    }
}
