package com.example.chapter_127

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chapter_127.ui.theme.Chapter127Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // TODO: [todos/046-compose-viewmodel-function-vs-manual.md](../../../../../../../../todos/046-compose-viewmodel-function-vs-manual.md)
            Log.i("custom", "re-composable")
            // FIXME: ViewModel 을 `setContent` 안에서 직접 생성하고 있다. 두 가지가 깨진다.
            //  (1) 재구성될 때마다 새 인스턴스가 만들어진다 → `remember` 조차 걸려 있지 않다
            //  (2) 화면 회전 시 카운트가 0 으로 초기화된다 → ViewModel 을 쓰는 이유 자체가 사라진다
            //  고치기: 생성자 인자가 있으므로 팩토리와 함께 `viewModel()` 로 얻는다.
            //      val viewModel: CounterViewModel = viewModel(factory = viewModelFactory {
            //          initializer { CounterViewModel(CounterRepository()) }
            //      })
            val viewModel = CounterViewModel(CounterRepository())
            Chapter127Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // TODO: [todos/036-compose-recomposition-timing-and-scope.md](../../../../../../../../todos/036-compose-recomposition-timing-and-scope.md)
                    Log.i("custom", "Surface re-composable")
                    CounterApp(viewModel = viewModel)
                }
            }
        }
    }
}

// FIXME: `Log.i("custom", ...)` 는 재구성 횟수를 눈으로 보려고 넣은 학습용 로그다.
//  실제 앱이라면 남기지 않는다. 남긴다면 최소한 릴리스 빌드에서 제거되도록 처리한다.
// FIXME: `modifier: Modifier = Modifier` 파라미터가 없다.
@Composable
fun CounterApp(viewModel: CounterViewModel) {
    Log.i("custom", "CounterApp re-composable")
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Log.i("custom", "CounterApp Column re-composable")
        Text(
            text = "Count: ${viewModel.count.value}",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(onClick = { viewModel.increment() }) {
                Text("Increment")
            }
            Spacer(modifier = Modifier.width(24.dp))
            Button(onClick = { viewModel.decrement() }) {
                Text("Decrement")
            }
        }
    }
}