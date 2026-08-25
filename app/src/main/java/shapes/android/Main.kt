package shapes.android

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
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import shapes.game.FontWeight

const val RADIUS = 8f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppFont.initFont(this, shapes.game.FONT_DMMONO)
        AppFont.initFont(this, shapes.game.FONT_MANROPE)

        enableEdgeToEdge()
        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        Storage.init(this)

        setContent {
            App()
        }
    }
}

enum class Screen {
    Start,
    Playing,
    GameOver,
}

sealed interface DebugAction {
    data object ExplodeCell : DebugAction
    data object FillRow : DebugAction
    data object SpawnSingleCellShape : DebugAction
    data object PlaceShape : DebugAction
    data object FillDouble : DebugAction
    data object Announce : DebugAction
}

@Composable
fun App() {
    var screen by remember {
        mutableStateOf(if (shapes.BuildConfig.DEBUG) Screen.Playing else Screen.Start)
    }
    var gameSession by remember { mutableIntStateOf(0) }

    fun showScreen(newScreen: Screen) {
        if (newScreen != screen && newScreen != Screen.Start) {
            gameSession += 1
        }
        screen = newScreen
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(shapes.game.Color.YELLOW)),
        contentAlignment = Alignment.Center,
    ) {
        when (screen) {
            Screen.Start -> {
                Button(
                    backgroundColor = Color(shapes.game.Color.BLUE),
                    paddingHorizontal = 32.dp,
                    paddingVertical = 20.dp,
                    onClick = { showScreen(Screen.Playing) }
                ) {
                    BasicText(
                        text = "Start",
                        style = TextStyle(
                            color = Color.Black,
                            fontSize = 24.sp,
                            fontFamily = AppFont.family(shapes.game.FONT_MANROPE, FontWeight.Bold)
                        )
                    )
                }
            }

            Screen.Playing, Screen.GameOver -> key(gameSession) {
                GameScreen(
                    gameOver = screen == Screen.GameOver,
                    onGameOver = { screen = Screen.GameOver },
                    onPlayAgain = { showScreen(Screen.Playing) },
                )
            }
        }

        if (shapes.BuildConfig.DEBUG) {
            DebugMenu(
                screen = screen,
                onScreenChange = ::showScreen,
                onAction = { action ->
                    // Actions are delivered to the currently composed GameScreen.
                    DebugActions.dispatch(action)
                },
            )
        }
    }
}

object DebugActions {
    private var handler: ((DebugAction) -> Unit)? = null

    fun register(handler: (DebugAction) -> Unit) {
        this.handler = handler
    }

    fun unregister(handler: (DebugAction) -> Unit) {
        if (this.handler === handler) this.handler = null
    }

    fun dispatch(action: DebugAction) {
        handler?.invoke(action)
    }
}

@Composable
fun DebugMenu(
    screen: Screen,
    onScreenChange: (Screen) -> Unit,
    onAction: (DebugAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, end = 16.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        if (expanded) {
            Column(
                modifier = Modifier
                    .padding(top = 44.dp)
                    .width(156.dp)
                    .background(Color.White, RoundedCornerShape(RADIUS.dp))
                    .border(3.dp, Color.Black, RoundedCornerShape(RADIUS.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DebugSection("SCREEN")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Screen.entries.forEach { target ->
                        DebugMenuButton(
                            modifier = Modifier.weight(1f),
                            text = target.debugShortLabel(),
                            backgroundColor = if (target == screen) Color(shapes.game.Color.BLUE) else Color.White,
                            onClick = { onScreenChange(target) },
                        )
                    }
                }

                DebugSection("ACTIONS")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DebugMenuButton(
                        modifier = Modifier.weight(1f),
                        text = "Explode",
                        onClick = { onAction(DebugAction.ExplodeCell) },
                    )
                    DebugMenuButton(
                        modifier = Modifier.weight(1f),
                        text = "Fill row",
                        onClick = { onAction(DebugAction.FillRow) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DebugMenuButton(
                        modifier = Modifier.weight(1f),
                        text = "Spawn 1×1",
                        onClick = { onAction(DebugAction.SpawnSingleCellShape) },
                    )
                    DebugMenuButton(
                        modifier = Modifier.weight(1f),
                        text = "Place",
                        onClick = { onAction(DebugAction.PlaceShape) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DebugMenuButton(
                        modifier = Modifier.weight(1f),
                        text = "Fill double",
                        onClick = { onAction(DebugAction.FillDouble) },
                    )
                    DebugMenuButton(
                        modifier = Modifier.weight(1f),
                        text = "Announce",
                        onClick = { onAction(DebugAction.Announce) },
                    )
                }
            }
        }

        Button(
            modifier = Modifier.wrapContentWidth(),
            backgroundColor = Color(shapes.game.Color.PINK),
            paddingHorizontal = 8.dp,
            paddingVertical = 8.dp,
            neobrutalistShadow = false,
            text = "DBG",
            onClick = { expanded = !expanded },
        )
    }
}

@Composable
private fun DebugSection(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            fontSize = 10.sp,
            color = Color.Black,
            fontFamily = AppFont.family(shapes.game.FONT_DMMONO, FontWeight.Medium),
        ),
    )
}

@Composable
private fun DebugMenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
) {
    Button(
        modifier = modifier,
        backgroundColor = backgroundColor,
        paddingHorizontal = 4.dp,
        paddingVertical = 6.dp,
        onClick = onClick,
        neobrutalistShadow = false,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                fontSize = 11.sp,
                color = Color.Black,
                fontFamily = AppFont.family(shapes.game.FONT_MANROPE, FontWeight.Bold),
            ),
        )
    }
}

private fun Screen.debugShortLabel() = when (this) {
    Screen.Start -> "S"
    Screen.Playing -> "P"
    Screen.GameOver -> "O"
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
fun Button(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    paddingHorizontal: Dp = 20.dp,
    paddingVertical: Dp = 20.dp,
    neobrutalistShadow: Boolean = true,
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
                    // fontFamily = AppFont.famBold,
                    fontFamily = AppFont.family(shapes.game.FONT_MANROPE, FontWeight.Bold),
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

    var buttonModifier =
        modifier
            .graphicsLayer {
                translationX = animTranslation.toPx()
                translationY = animTranslation.toPx()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )

    if (neobrutalistShadow) {
        buttonModifier = buttonModifier.neobrutalistShadow(
            offsetX = 6.dp - animTranslation,
            offsetY = 6.dp - animTranslation,
        )
    }

    buttonModifier = buttonModifier.background(backgroundColor, RoundedCornerShape(RADIUS.dp))
        .border(3.dp, Color.Black, RoundedCornerShape(RADIUS.dp))
        .padding(horizontal = paddingHorizontal, vertical = paddingVertical)

    Box(
        modifier = buttonModifier,
        contentAlignment = Alignment.Center,
    ) {
        c()
    }
}
