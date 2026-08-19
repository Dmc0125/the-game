package shapes.game

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import shapes.game.R

val COLOR_BLUE = Color(112, 228, 239)
val COLOR_YELLOW = Color(226, 239, 112)
val COLOR_BLACK = Color(39, 43, 43)
const val RADIUS = 8f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppFont.init(this)

        enableEdgeToEdge()
        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            App()
        }
    }
}

enum class Screen {
    Start,
    Game,
}

@Composable
fun App() {
    var screen by remember { mutableStateOf(Screen.Start) }
    if (BuildConfig.DEBUG) {
        screen = Screen.Game
    }

    Box(modifier = Modifier.fillMaxSize().background(COLOR_YELLOW), contentAlignment = Alignment.Center) {
        when (screen) {
            Screen.Start -> {
                Button(
                    backgroundColor = COLOR_BLUE,
                    paddingHorizontal = 32.dp,
                    paddingVertical = 20.dp,
                    onClick = { screen = Screen.Game },
                ) {
                    BasicText(
                        text = "Start",
                        style = TextStyle(
                            color = Color.Black,
                            fontSize = 24.sp,
                            fontFamily = AppFont.famBold
                        )
                    )
                }
            }

            Screen.Game -> GameScreen()
        }
    }
}

@Composable
fun Modifier.neobrutalistShadow(
    color: Color = Color.Black,
    offsetX: Dp = 6.dp,
    offsetY: Dp = 6.dp,
    radius: Dp = RADIUS.dp,
) = drawBehind {
    drawRoundRect(
        color = color,
        topLeft = Offset(offsetX.toPx(), offsetY.toPx()),
        cornerRadius = CornerRadius(radius.toPx()),
        size = size,
    )
}

@Composable
fun GameScreen() {
    val gameView = remember { mutableStateOf<GameView?>(null) }
    val nextShapeView = remember { mutableStateOf<NextShapeView?>(null) }
    val score = remember { mutableStateOf(0) }

    fun onScoreChange(newScore: Int) {
        score.value = newScore
    }

    fun onNextShape(shapeIdx: Int) {
        nextShapeView.value?.onNextShape(shapeIdx)
    }

    Column(
        modifier = Modifier
            .padding(start = 20.dp, top = 48.dp, end = 20.dp)
            .fillMaxSize()
            .pointerInput(Unit) {
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
                            gameView.value?.handleTouch(event)
                        }
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(20.dp),
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
            Column(
                modifier = Modifier
                    .padding(start = 20.dp, top = verticalPadding, bottom = verticalPadding)
                    .fillMaxHeight(),
            ) {
                BasicText(
                    text = "SCORE",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = AppFont.famMonoMedium,
                        color = Color.Black,
                    ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val score = "%06d".format(score.value)

                    BasicText(
                        text = score,
                        style = TextStyle(
                            fontSize = 40.sp,
                            fontFamily = AppFont.famMonoMedium,
                            color = Color.Black,
                        ),
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .background(
                        COLOR_BLACK, RoundedCornerShape(
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
                        fontFamily = AppFont.famMonoMedium,
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
                                nextShapeView.value = it
                            }
                        }
                    )
                }
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .neobrutalistShadow()
                .graphicsLayer {
                    clip = false
                },
            factory = { context ->
                GameView(context, ::onScoreChange, ::onNextShape).also {
                    gameView.value = it
                    it.resume()
                }
            },
            onRelease = { gameView -> gameView.pause() },
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Button(
                paddingHorizontal = 32.dp,
                paddingVertical = 32.dp,
                onClick = { gameView.value?.handleRotate() },
            ) {
                BasicText(
                    text = "R",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = AppFont.famBold,
                        color = Color.Black,
                    )
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                paddingHorizontal = 32.dp,
                paddingVertical = 32.dp,
                onClick = { gameView.value?.handlePlace() },
            ) {
                BasicText(
                    text = "Place",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = AppFont.famBold,
                        color = Color.Black,
                    )
                )
            }
        }

        if (BuildConfig.DEBUG) {
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
fun Button(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    paddingHorizontal: Dp = 20.dp,
    paddingVertical: Dp = 20.dp,
    onClick: () -> Unit,
    text: String? = null,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    var c = content
    if (c == null && text != null) {
        c = {
            BasicText(
                text = text,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = AppFont.famBold,
                    color = Color.Black,
                )
            )
        }
    }
    check(c != null)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val animTranslation by animateDpAsState(
        targetValue = if (pressed) 6.dp else 0.dp,
        label = "translation",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = animTranslation.toPx()
                translationY = animTranslation.toPx()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .neobrutalistShadow(
                offsetX = 6.dp - animTranslation,
                offsetY = 6.dp - animTranslation,
            )
            .background(backgroundColor, RoundedCornerShape(RADIUS.dp))
            .border(3.dp, Color.Black, RoundedCornerShape(RADIUS.dp))
            .padding(horizontal = paddingHorizontal, vertical = paddingVertical),
        contentAlignment = Alignment.Center,
    ) {
        c()
    }
}
