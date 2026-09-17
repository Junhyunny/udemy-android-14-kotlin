package com.example.chapter_157

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// FIXME: `modifier: Modifier = Modifier` 파라미터가 없다(SecondScreen 도 같다).
@Composable
fun FirstScreen(navigationToSecondScreen: (String, Int) -> Unit) {
    // FIXME: 입력값이 `remember` 에만 있어 화면 회전 시 사라진다.
    //  사용자가 입력 중이던 내용이 날아가는 것은 눈에 띄는 문제다.
    //  고치기: `rememberSaveable` 을 쓴다.
    val name = remember { mutableStateOf("") }
    // FIXME: 나이를 `Int` 상태로 두어 입력 도중의 문자열을 표현하지 못한다.
    //  비어 있는 칸이 항상 "0" 으로 보이고, 지울 수도 없다(`toIntOrNull() ?: 0` 때문).
    //  고치기: 입력 중에는 `String` 으로 두고 확정 시점에만 숫자로 바꾼다.
    val age = remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("This is the first screen", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name.value,
            onValueChange = { name.value = it }
        )
        Spacer(modifier = Modifier.height(16.dp))
        // FIXME: 같은 상태를 `age.value` 와 `age.intValue`(아래 Button) 로 섞어 읽는다.
        //  `mutableIntStateOf` 를 쓸 때는 박싱이 없는 `intValue` 로 통일한다.
        // FIXME: 숫자 입력인데 keyboardOptions 가 없어 문자 키보드가 뜬다.
        //  고치기: keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        // FIXME: 두 OutlinedTextField 모두 `label` 이 없어 어느 칸이 이름/나이인지 알 수 없다.
        OutlinedTextField(
            value = age.value.toString(),
            onValueChange = { age.value = it.toIntOrNull() ?: 0 }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navigationToSecondScreen(name.value, age.intValue) }) {
            Text("Go to the second screen")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FirstScreenPreview() {
    // FIXME: 불필요하고 위험한 캐스팅이다. `{ }` 는 `() -> Unit` 인데 이를 억지로 내려 캐스팅하고 있어,
    //  미리보기를 실행하면 `ClassCastException` 이 난다.
    //  고치기: 파라미터 개수를 맞춘 람다를 그대로 넘긴다. → FirstScreen { _, _ -> }
    FirstScreen({ } as (String, Int) -> Unit)
}