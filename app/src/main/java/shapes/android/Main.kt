package shapes.android

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import shapes.game.FontWeight
import shapes.game.Platform
import shapes.game.string
import android.util.Log

object AppFont {
    data class FontKey(val name: String, val weight: FontWeight)

    val fonts: MutableMap<FontKey, Typeface> = mutableMapOf()

    fun initFont(context: Context, name: String) {
        val assets = context.assets

        fun loadFontWeight(name: String, weight: FontWeight) {
            try {
                val typeface = Typeface.createFromAsset(
                    assets,
                    "font/${name}_${weight.string().lowercase()}.ttf",
                )
                if (typeface != null) {
                    fonts[FontKey(name, weight)] = typeface
                }
            } catch (e: Exception) {
            }
        }

        loadFontWeight(name, FontWeight.ExtraLight)
        loadFontWeight(name, FontWeight.Light)
        loadFontWeight(name, FontWeight.Regular)
        loadFontWeight(name, FontWeight.Medium)
        loadFontWeight(name, FontWeight.SemiBold)
        loadFontWeight(name, FontWeight.Bold)
        loadFontWeight(name, FontWeight.ExtraBold)
    }
}

fun AppFont.typeface(
    name: String = shapes.game.FONT_SUPPLY_CENTER,
    weight: FontWeight = shapes.game.FontWeight.Regular,
): Typeface {
    val face = fonts[AppFont.FontKey(name, weight)]
    require(face != null) { "Font not found: $name $weight" }
    return face
}

class AndroidRenderer : shapes.game.Renderer {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var canvas: Canvas? = null

    override fun save() {
        canvas?.save()
    }

    override fun restore() {
        canvas?.restore()
    }

    override fun translate(x: Float, y: Float) {
        canvas?.translate(x, y)
    }

    override fun rotate(angle: Float, x: Float, y: Float) {
        canvas?.rotate(angle, x, y)
    }

    override fun scale(scaleX: Float, scaleY: Float) {
        canvas?.scale(scaleX, scaleY)
    }

    override fun scale(scaleX: Float, scaleY: Float, x: Float, y: Float) {
        canvas?.scale(scaleX, scaleY, x, y)
    }

    override fun drawRoundRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) {
        paint.reset()
        paint.color = color
        canvas?.drawRoundRect(x, y, x + width, y + height, radius, radius, paint)
    }

    override fun drawRoundRect(rect: shapes.game.Rect, radius: Float, color: Int) {
        drawRoundRect(rect.x, rect.y, rect.width, rect.height, radius, color)
    }

    override fun strokeRoundRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
        strokeWidth: Float
    ) {
        paint.reset()
        paint.style = Paint.Style.STROKE
        paint.color = color
        paint.strokeWidth = strokeWidth
        canvas?.drawRoundRect(x, y, x + width, y + height, radius, radius, paint)
    }

    override fun drawRect(x: Float, y: Float, width: Float, height: Float, color: Int) {
        paint.reset()
        paint.color = color
        canvas?.drawRect(x, y, x + width, y + height, paint)
    }

    override fun drawRect(rect: shapes.game.Rect, color: Int) {
        drawRect(rect.x, rect.y, rect.width, rect.height, color)
    }

    override fun measureText(text: String, textSize: Float, fontWeight: FontWeight, font: String): Float {
        paint.reset()
        paint.textSize = textSize
        paint.typeface = AppFont.typeface(font, fontWeight)
        return paint.measureText(text)
    }

    override fun drawText(
        text: String,
        x: Float,
        y: Float,
        color: Int,
        textSize: Float,
        fontWeight: FontWeight,
        font: String
    ) {
        paint.reset()
        paint.textSize = textSize
        paint.color = color
        paint.typeface = AppFont.typeface(font, fontWeight)
        canvas?.drawText(text, x, y, paint)
    }

    override fun strokeText(
        text: String,
        x: Float,
        y: Float,
        strokeWidth: Float,
        color: Int,
        textSize: Float,
        fontWeight: FontWeight,
        font: String
    ) {
        paint.reset()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.textSize = textSize
        paint.color = color
        paint.typeface = AppFont.typeface(font, fontWeight)
        canvas?.drawText(text, x, y, paint)
    }
}


class AndroidTrace : shapes.game.Trace {
    override fun beginSection(name: String) = android.os.Trace.beginSection(name)
    override fun endSection() = android.os.Trace.endSection()
}

class MainActivity : AppCompatActivity() {
    lateinit var appView: AppView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Platform.withTrace(AndroidTrace())

        AppFont.initFont(this, shapes.game.FONT_DMMONO)
        AppFont.initFont(this, shapes.game.FONT_MANROPE)
        AppFont.initFont(this, shapes.game.FONT_SUPPLY_CENTER)

        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        window.attributes.preferredRefreshRate = display.refreshRate
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        Storage.init(this)

        appView = AppView(this)
        setContentView(appView)
    }

    override fun onResume() {
        super.onResume()
        appView.resume()
    }

    override fun onPause() {
        appView.pause()
        super.onPause()
    }
}

class Metrics {
    var beginTime = 0L
    var totalUpdateTime = 0f
    var totalDrawTime = 0f
    var frameCount = 0

    fun log(time: Long) {
        val elapsed = (time - beginTime) / 1e9f
        if (elapsed >= 10f) {
            val avgUpdateTime = totalUpdateTime / frameCount
            val avgDrawTime = totalDrawTime / frameCount
            val avgFps = frameCount / elapsed
            Log.i(
                "metris",
                "avg update time: $avgUpdateTime ms, avg draw time: $avgDrawTime ms, avg fps: $avgFps",
            )
            beginTime = time
            totalUpdateTime = 0f
            totalDrawTime = 0f
            frameCount = 0
        }
    }
}

class AppView(context: Context) : View(context) {
    var lastFrameTime = 0L
    val game = shapes.game.GameContext()
    val renderer = AndroidRenderer()
    var updateRun = false
    val touch = shapes.game.Touch()
    var running = false
    val metrics = Metrics()


    fun resume() {
        lastFrameTime = System.nanoTime()
        running = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(currentTime: Long) {
            if (!running) return

            if (lastFrameTime == 0L) {
                lastFrameTime = currentTime
            }

            val frameBegin = System.nanoTime()

            val deltaTime = currentTime - lastFrameTime
            lastFrameTime = currentTime
            val deltaTimeSeconds = deltaTime / 1e9f

            touch.position.x /= game.scale
            touch.position.y /= game.scale
            touch.consumed = false

            shapes.game.Platform.withRenderer(renderer)
            shapes.game.gameUpdate(game, deltaTimeSeconds, touch)

            touch.action = shapes.game.TouchAction.None
            touch.position.x = 0f
            touch.position.y = 0f

            updateRun = true

            metrics.totalUpdateTime += (System.nanoTime() - frameBegin) / 1e6f

            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shapes.game.gameQueueResize(game, w.toFloat(), h.toFloat())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touch.action = shapes.game.TouchAction.Down
                touch.position.x = event.x
                touch.position.y = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                touch.action = shapes.game.TouchAction.Move
                touch.position.x = event.x
                touch.position.y = event.y
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touch.action = shapes.game.TouchAction.Up
                touch.position.x = event.x
                touch.position.y = event.y
            }
        }

        return true
    }


    override fun onDraw(canvas: Canvas) {
        if (!updateRun || !running) return

        val drawBegin = System.nanoTime()

        renderer.canvas = canvas
        shapes.game.Platform.withRenderer(renderer)
        shapes.game.gameRender(game)

        metrics.totalDrawTime += (System.nanoTime() - drawBegin) / 1e6f
        metrics.frameCount += 1
        metrics.log(System.nanoTime())
    }
}

// sealed interface DebugAction {
//     data object ExplodeCell : DebugAction
//     data object FillRow : DebugAction
//     data object SpawnSingleCellShape : DebugAction
//     data object PlaceShape : DebugAction
//     data object FillDouble : DebugAction
//     data object Announce : DebugAction
// }

// @Composable
// fun App() {
//     var screen by remember {
//         mutableStateOf(if (shapes.BuildConfig.DEBUG) Screen.Playing else Screen.Start)
//     }
//     var gameSession by remember { mutableIntStateOf(0) }

//     fun showScreen(newScreen: Screen) {
//         if (newScreen != screen && newScreen != Screen.Start) {
//             gameSession += 1
//         }
//         screen = newScreen
//     }

//     Box(
//         modifier = Modifier
//             .fillMaxSize()
//             .background(Color(shapes.game.Color.vanilla)),
//         contentAlignment = Alignment.Center,
//     ) {
//         when (screen) {
//             Screen.Start -> {
//                 Box(
//                     modifier = Modifier
//                         .fillMaxSize()
//                         .padding(horizontal = 16.dp, vertical = 48.dp),
//                     contentAlignment = Alignment.BottomCenter,
//                 ) {
//                     AppButton(
//                         modifier = Modifier.fillMaxWidth(),
//                         color = Color(shapes.game.Color.blue),
//                         onClick = { screen = Screen.Playing },
//                     ) {
//                         Text("START", 24)
//                     }
//                 }
//             }

//             Screen.Playing, Screen.GameOver -> key(gameSession) {
//                 GameScreen(
//                     gameOver = screen == Screen.GameOver,
//                     onGameOver = { screen = Screen.GameOver },
//                     onPlayAgain = { showScreen(Screen.Playing) },
//                 )
//             }
//         }

//         if (shapes.BuildConfig.DEBUG) {
//             DebugMenu(
//                 screen = screen,
//                 onScreenChange = ::showScreen,
//                 onAction = { action ->
//                     // Actions are delivered to the currently composed GameScreen.
//                     DebugActions.dispatch(action)
//                 },
//             )
//         }
//     }
// }

// object DebugActions {
//     private var handler: ((DebugAction) -> Unit)? = null

//     fun register(handler: (DebugAction) -> Unit) {
//         this.handler = handler
//     }

//     fun unregister(handler: (DebugAction) -> Unit) {
//         if (this.handler === handler) this.handler = null
//     }

//     fun dispatch(action: DebugAction) {
//         handler?.invoke(action)
//     }
// }

// @Composable
// fun DebugMenu(
//     screen: Screen,
//     onScreenChange: (Screen) -> Unit,
//     onAction: (DebugAction) -> Unit,
// ) {
//     var expanded by remember { mutableStateOf(false) }

//     Box(
//         modifier = Modifier
//             .fillMaxSize()
//             .padding(top = 24.dp, end = 16.dp),
//         contentAlignment = Alignment.TopEnd,
//     ) {
//         if (expanded) {
//             Column(
//                 modifier = Modifier
//                     .padding(top = 44.dp)
//                     .width(156.dp)
//                     .background(Color.White, RoundedCornerShape(RADIUS.dp))
//                     .border(3.dp, Color.Black, RoundedCornerShape(RADIUS.dp))
//                     .padding(8.dp),
//                 verticalArrangement = Arrangement.spacedBy(6.dp),
//             ) {
//                 DebugSection("SCREEN")
//                 Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
//                     Screen.entries.forEach { target ->
//                         DebugMenuButton(
//                             modifier = Modifier.weight(1f),
//                             text = target.debugShortLabel(),
//                             backgroundColor = if (target == screen) Color(shapes.game.Color.blue) else Color.White,
//                             onClick = { onScreenChange(target) },
//                         )
//                     }
//                 }

//                 DebugSection("ACTIONS")
//                 Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
//                     DebugMenuButton(
//                         modifier = Modifier.weight(1f),
//                         text = "Explode",
//                         onClick = { onAction(DebugAction.ExplodeCell) },
//                     )
//                     DebugMenuButton(
//                         modifier = Modifier.weight(1f),
//                         text = "Fill row",
//                         onClick = { onAction(DebugAction.FillRow) },
//                     )
//                 }
//                 Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
//                     DebugMenuButton(
//                         modifier = Modifier.weight(1f),
//                         text = "Spawn 1×1",
//                         onClick = { onAction(DebugAction.SpawnSingleCellShape) },
//                     )
//                     DebugMenuButton(
//                         modifier = Modifier.weight(1f),
//                         text = "Place",
//                         onClick = { onAction(DebugAction.PlaceShape) },
//                     )
//                 }
//                 Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
//                     DebugMenuButton(
//                         modifier = Modifier.weight(1f),
//                         text = "Fill double",
//                         onClick = { onAction(DebugAction.FillDouble) },
//                     )
//                     DebugMenuButton(
//                         modifier = Modifier.weight(1f),
//                         text = "Announce",
//                         onClick = { onAction(DebugAction.Announce) },
//                     )
//                 }
//             }
//         }

//         Button(
//             modifier = Modifier.wrapContentWidth(),
//             backgroundColor = Color(shapes.game.Color.red),
//             paddingHorizontal = 8.dp,
//             paddingVertical = 8.dp,
//             neobrutalistShadow = false,
//             text = "DBG",
//             onClick = { expanded = !expanded },
//         )
//     }
// }

// @Composable
// private fun DebugSection(text: String) {
//     BasicText(
//         text = text,
//         style = TextStyle(
//             fontSize = 10.sp,
//             color = Color.Black,
//             fontFamily = AppFont.family(shapes.game.FONT_DMMONO, FontWeight.Medium),
//         ),
//     )
// }

// @Composable
// private fun DebugMenuButton(
//     text: String,
//     onClick: () -> Unit,
//     modifier: Modifier = Modifier,
//     backgroundColor: Color = Color.White,
// ) {
//     Button(
//         modifier = modifier,
//         backgroundColor = backgroundColor,
//         paddingHorizontal = 4.dp,
//         paddingVertical = 6.dp,
//         onClick = onClick,
//         neobrutalistShadow = false,
//     ) {
//         BasicText(
//             text = text,
//             style = TextStyle(
//                 fontSize = 11.sp,
//                 color = Color.Black,
//                 fontFamily = AppFont.family(shapes.game.FONT_MANROPE, FontWeight.Bold),
//             ),
//         )
//     }
// }

// private fun Screen.debugShortLabel() = when (this) {
//     Screen.Start -> "S"
//     Screen.Playing -> "P"
//     Screen.GameOver -> "O"
// }
