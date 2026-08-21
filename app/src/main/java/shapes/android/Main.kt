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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val screen = remember {
        mutableStateOf(
            if (shapes.BuildConfig.DEBUG) Screen.Game else Screen.Start
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(shapes.game.Color.YELLOW)),
        contentAlignment = Alignment.Center,
    ) {
        when (screen.value) {
            Screen.Start -> {
                Button(
                    backgroundColor = Color(shapes.game.Color.BLUE),
                    paddingHorizontal = 32.dp,
                    paddingVertical = 20.dp,
                    onClick = { screen.value = Screen.Game },
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
