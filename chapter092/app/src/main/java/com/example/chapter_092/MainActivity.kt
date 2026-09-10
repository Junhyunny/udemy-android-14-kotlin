package com.example.chapter_092

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.chapter_092.ui.theme.Chapter092Theme
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Chapter092Theme {
                CaptainGame()
            }
        }
    }

    @Composable
    fun CaptainGame() {
        // TODO: [todos/compose-recomposition-timing-and-scope.md](../../../../../../../../todos/compose-recomposition-timing-and-scope.md)
        /*
         * 이번엔 by 키워드가 안 쓰였는데, by 키워드가 있을 때 없을 떄 동작이 다른가? 어떤 부분이 다른지 설명해줘.
         * by 키워드를 사용하면 값을 그대로 꺼내서 사용하는데, 해당 변수의 값을 직접 변경하거나 하면 상태에 반영되나? 특히 다른 함수로 전달하면 참조가 이어지나? 프록시 객체야? 어떤지 알려줘.
         * 내가 테스트 해봤을 떄는 증가하지 않네
         */
        // val treasuresFound = remember { mutableIntStateOf(0) }
        var treasuresFound by remember { mutableIntStateOf(0) }
        // TODO: [todos/compose-state-storage-and-snapshot.md](../../../../../../../../todos/compose-state-storage-and-snapshot.md)
        val direction = remember { mutableStateOf("North") }
        val stormOrTreasure = remember { mutableStateOf("") }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Treasures Found: $treasuresFound")
            Text(text = "Current Direction: ${direction.value}")
            Text(text = stormOrTreasure.value)
            DirectionButton("East", direction, stormOrTreasure, treasuresFound)
            DirectionButton("West", direction, stormOrTreasure, treasuresFound)
            DirectionButton("North", direction, stormOrTreasure, treasuresFound)
            DirectionButton("South", direction, stormOrTreasure, treasuresFound)
        }
    }

    @Composable
    fun DirectionButton(
        direction: String,
        directionState: MutableState<String>,
        stormOrTreasure: MutableState<String>,
        treasuresFoundState: Int,
    ) {
        Button(onClick = {
            directionState.value = direction
            if (Random.nextBoolean()) {
                treasuresFoundState.inc()
                stormOrTreasure.value = "Found a Treasure!"
            } else {
                stormOrTreasure.value = "Storm Ahead!"
            }
        }) {
            Text("Sail $direction")
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun GamePreview() {
        Chapter092Theme {
            CaptainGame()
        }
    }
}