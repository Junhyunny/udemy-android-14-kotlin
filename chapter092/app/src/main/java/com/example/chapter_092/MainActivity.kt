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

// FIXME: 컴포저블(`CaptainGame`, `DirectionButton`, `GamePreview`)이 Activity 클래스 "안에" 선언돼 있다.
//  - 액티비티 인스턴스에 묶여 재사용·테스트·미리보기가 어렵다
//  - 실수로 액티비티 필드를 참조하면 그대로 결합이 생긴다
//  고치기: 파일 최상위(top-level)로 꺼내거나 별도 파일로 분리한다. 다른 챕터(106, 157, 205)처럼.
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
        // TODO: [todos/036-compose-recomposition-timing-and-scope.md](../../../../../../../../todos/036-compose-recomposition-timing-and-scope.md)
        /*
         * 이번엔 by 키워드가 안 쓰였는데, by 키워드가 있을 때 없을 떄 동작이 다른가? 어떤 부분이 다른지 설명해줘.
         * by 키워드를 사용하면 값을 그대로 꺼내서 사용하는데, 해당 변수의 값을 직접 변경하거나 하면 상태에 반영되나? 특히 다른 함수로 전달하면 참조가 이어지나? 프록시 객체야? 어떤지 알려줘.
         * 내가 테스트 해봤을 떄는 증가하지 않네
         */
        // FIXME: 위 주석의 "증가하지 않네"의 원인은 두 가지가 겹친 것이다.
        //  (1) `DirectionButton` 에 `Int` 값을 넘긴다 → 값 복사라 원본과 연결이 끊긴다
        //  (2) 넘겨받은 쪽에서 `treasuresFoundState.inc()` 를 호출한다
        //      → `Int.inc()` 는 "1 큰 새 값을 반환"할 뿐 원본을 바꾸지 않는다. 반환값을 버리고 있으니 완전한 no-op.
        //  즉 `by` 위임과는 무관하고, 상태를 어떻게 전달했는지가 문제다.
        //  고치기: 값을 내리지 말고 "이벤트를 올린다"(상태 호이스팅).
        //      DirectionButton(dir, onSail = { treasuresFound++ ; ... })
        // val treasuresFound = remember { mutableIntStateOf(0) }
        var treasuresFound by remember { mutableIntStateOf(0) }
        // TODO: [todos/035-compose-state-storage-and-snapshot.md](../../../../../../../../todos/035-compose-state-storage-and-snapshot.md)
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

    // FIXME: 하위 컴포저블에 `MutableState` 를 그대로 내려보내 하위가 상위 상태를 직접 쓰고 있다.
    //  - 상태가 어디서 바뀌는지 추적이 어려워진다(단방향 데이터 흐름 위반)
    //  - `DirectionButton` 이 미리보기·테스트에서 독립적으로 못 쓰인다
    //  고치기: 상태 대신 값과 콜백을 받는다.
    //      fun DirectionButton(direction: String, onClick: () -> Unit)
    //  그리고 `treasuresFound`, `direction`, `stormOrTreasure` 는 한 화면의 상태이므로
    //  `data class GameUiState(...)` 하나로 묶는 편이 낫다.
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
                // FIXME: `inc()` 는 반환값을 쓰지 않으면 아무 효과가 없다. 보물 개수가 영원히 0 인 진짜 원인.
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