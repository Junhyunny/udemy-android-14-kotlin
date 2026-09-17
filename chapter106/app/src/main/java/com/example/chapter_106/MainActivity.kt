package com.example.chapter_106

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.chapter_106.ui.theme.Chapter106Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Chapter106Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShoppingListApp()
                }
            }
        }
    }
}


// FIXME: 내용이 비어 있는 미리보기가 남아 있다. 아무것도 그리지 않으므로 존재 이유가 없다.
//  고치기: 삭제하거나, 실제로 검증하고 싶은 화면을 넣는다.
//      @Preview(showBackground = true)
//      @Composable
//      fun ShoppingListAppPreview() { Chapter106Theme { ShoppingListApp() } }
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
}