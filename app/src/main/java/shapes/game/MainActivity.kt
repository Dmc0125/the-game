package shapes.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (screen) {
            Screen.Start -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Green)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .clickable { screen = Screen.Game }
                ) {
                    BasicText(
                        text = "Start",
                        style = TextStyle(
                            color = Color.Black,
                            fontWeight = FontWeight.Medium,
                            fontSize = 24.sp,
                        )
                    )
                }
            }

            Screen.Game -> GameScreen()
        }
    }
}

@Composable
fun GameScreen() {
    Box(
        modifier = Modifier
            .padding(start = 20.dp, top = 30.dp, end = 20.dp)
            .fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
        }
    }
}
