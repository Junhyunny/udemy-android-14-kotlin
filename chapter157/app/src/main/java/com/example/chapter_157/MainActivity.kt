package com.example.chapter_157

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chapter_157.ui.theme.Chapter157Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Chapter157Theme {
                MyApp()
            }
        }
    }
}

// ARCH-FIXME: 화면 간 데이터를 route 문자열에 실어 나르고 있다(`secondScreen/{name}/{age}`).
//  내비게이션 인자는 "무엇을 보여 줄지 식별하는 값"(id 등)에 쓰는 것이고,
//  화면이 표시할 데이터 자체를 실어 나르는 용도가 아니다.
//  인자가 늘어날수록 route 가 길어지고, 인코딩·타입 변환 문제가 함께 늘어난다(아래 FIXME 들 참고).
//  고치기(선택지):
//   (1) 공유 ViewModel — 두 화면이 같은 그래프 스코프의 ViewModel 을 본다
//   (2) id 만 넘기고 두 번째 화면이 스스로 조회한다  ← 실제 앱에서 가장 흔한 방식
//   (3) 타입 안전 route — @Serializable 목적지로 인자를 타입으로 표현한다
//
// ARCH-FIXME: 이 챕터에는 ViewModel 이 없어 입력값이 화면의 `remember` 에만 존재한다.
//  내비게이션 학습이 목적이라 의도된 구성이지만, 회전 시 입력이 사라지는 한계가 있다.
@Composable
fun MyApp() {
    // TODO: [todos/android-navigation-string-route-vs-type-safe.md](../../../../../../../../todos/android-navigation-string-route-vs-type-safe.md)
    val navController = rememberNavController()
    NavHost(
        navController = navController, startDestination = "firstScreen"
    ) {
        composable("firstScreen") {
            FirstScreen { name, age ->
                // FIXME: 사용자 입력을 route 문자열에 그대로 끼워 넣는다.
                //  이름에 `/` 나 공백, 한글이 들어가면 경로가 깨지거나 인자가 잘린다.
                //  고치기: 최소한 인코딩한다. → navigate("secondScreen/${Uri.encode(name)}/$age")
                //  근본 해결은 타입 안전 route(@Serializable 목적지)로 옮기는 것이다.
                navController.navigate("secondScreen/${name}/${age}")
            }
        }
        // TODO: [todos/android-navigation-passing-many-arguments.md](../../../../../../../../todos/android-navigation-passing-many-arguments.md)
        composable(route = "secondScreen/{name}/{age}") {
            val name = it.arguments?.getString("name") ?: "no name"
            val ageString = it.arguments?.getString("age") ?: "0"
            // FIXME: `toInt()` 는 숫자가 아닌 값에서 예외로 앱을 종료시킨다.
            //  고치기: `ageString.toIntOrNull() ?: 0`
            //  또는 인자 타입을 선언해 Navigation 이 변환하게 한다.
            //      arguments = listOf(navArgument("age") { type = NavType.IntType })
            //      val age = it.arguments?.getInt("age") ?: 0
            SecondScreen(name, ageString.toInt()) {
                // FIXME: 뒤로 가는 동작인데 `navigate` 를 쓰고 있다.
                //  첫 화면으로 "새로" 이동하므로 백스택이 계속 쌓인다.
                //  first → second → first → second ... 가 되어 뒤로 가기를 눌러도 앱을 빠져나올 수 없다.
                //  고치기: `navController.popBackStack()` (또는 `navigateUp()`)
                navController.navigate("firstScreen")
            }
        }
    }
}
