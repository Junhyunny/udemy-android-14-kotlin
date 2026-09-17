package com.example.chapter_186

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import com.example.chapter_186.ui.theme.Chapter186Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Chapter186Theme {
                Navigation()
            }
        }
    }
}

// ARCH-FIXME: `Navigation()` 이 조립(composition root) 역할까지 겸하고 있다.
//  내비게이션 그래프 정의 + ViewModel 생성 + 데이터 소스 생성 + 응답 매핑을 한 함수에서 한다.
//  특히 아래 `firstOrNull()?.formatted_address` 는 API 응답 구조를 내비게이션 코드가 아는 것이라
//  계층이 완전히 무너진 자리다.
//  고치기: 그래프 정의만 남기고, 의존성 생성은 DI 컨테이너로, 응답 매핑은 Repository/ViewModel 로 옮긴다.
//
// ARCH-FIXME: 두 화면이 같은 `viewModel` 인스턴스를 공유하도록 상위에서 만들어 내려보낸다.
//  의도 자체는 맞지만(위치 선택 결과를 목록 화면이 읽어야 한다), 이렇게 하면
//  ViewModel 의 생존 범위가 NavHost 전체가 되어 어느 화면을 벗어나도 상태가 남는다.
//  Navigation Compose 는 이런 경우를 위해 "그래프 스코프 ViewModel" 을 제공한다.
//      val parentEntry = remember(it) { navController.getBackStackEntry("graph_route") }
//      val viewModel: LocationViewModel = viewModel(parentEntry)
//  또는 화면 간 결과 전달은 `savedStateHandle` 로 하고 ViewModel 은 화면별로 두는 방법도 있다.
@Composable
fun Navigation() {
    val navController: NavHostController = rememberNavController()
    val viewModel: LocationViewModel = viewModel()
    val context = LocalContext.current
    val locationUtils = LocationUtils(context)
    // TODO: [todos/064-android-navigation-package-compose-vs-runtime.md](../../../../../../../../todos/064-android-navigation-package-compose-vs-runtime.md)
    // TODO: [todos/065-android-navigation-navhost-composable-dialog.md](../../../../../../../../todos/065-android-navigation-navhost-composable-dialog.md)
    NavHost(
        navController = navController,
        startDestination = "shoppinglistscreen"
    ) {
        composable("shoppinglistscreen") {
            // FIXME: 화면이 API 응답 구조(`GeocodingResult.formatted_address`)를 직접 알고 있다.
            //  서버 응답이 바뀌면 화면 코드까지 고쳐야 한다.
            //  고치기: ViewModel 이 `address: String` 형태로 가공해 노출한다.
            // FIXME: `context` 를 파라미터로 내려보내고 있다. 하위에서 `LocalContext.current` 로 얻으면 되고,
            //  `Context` 를 인자로 들고 다니면 미리보기·테스트가 어려워진다.
            ShoppingListApp(
                locationUtils = locationUtils,
                viewModel = viewModel,
                navController = navController,
                context = context,
                address = viewModel.address.value.firstOrNull()?.formatted_address ?: "No Address"
            )
        }
        dialog("locationscreen") {
            // FIXME: 학습용 로그("hello", "where am i?")가 남아 있다. 삭제 대상.
            Log.d("navigation", "hello")
            // FIXME: `location` 이 아직 null 이면 다이얼로그는 떠 있는데 안이 완전히 비어 있다.
            //  위치를 받아오는 데 시간이 걸리므로 실제로 발생한다. 사용자는 빈 창을 보게 된다.
            //  고치기: null 상태도 그린다.
            //      when (val loc = viewModel.location.value) {
            //          null -> LoadingDialog()
            //          else -> LocationSelectionScreen(location = loc) { ... }
            //      }
            viewModel.location.value?.let {
                Log.d("navigation", "where am i?")
                LocationSelectionScreen(location = it) { location ->
                    viewModel.fetchAddress("${location.latitude},${location.longitude}")
                    navController.popBackStack()
                }
            }
        }
    }
}