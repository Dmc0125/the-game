package shapes.game

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

val COLOR_GREEN = Color(112, 228, 239)
val COLOR_YELLOW = Color(226, 239, 112)
val RADIUS = 8f

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

    Box(modifier = Modifier.fillMaxSize().background(COLOR_YELLOW), contentAlignment = Alignment.Center) {
        when (screen) {
            Screen.Start -> {
                Box(
                    Modifier
                        .border(3.dp, Color.Black, RoundedCornerShape(RADIUS.dp))
                        .neobrutalistShadow()
                        .clip(RoundedCornerShape(RADIUS.dp))
                        .background(COLOR_GREEN)
                        .padding(horizontal = 40.dp, vertical = 16.dp)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                        ) { screen = Screen.Game }
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

    Box(
        modifier = Modifier
            .padding(start = 20.dp, top = 48.dp, end = 20.dp)
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (gameView.value != null) {
                            gameView.value!!.handleTouch(event)
                        }
                    }
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            factory = { context ->
                GameView(context).also {
                    gameView.value = it
                    it.resume()
                }
            },
            onRelease = { gameView -> gameView.pause() },
        )
    }
}
