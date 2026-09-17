package com.example.chapter_246

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

// ARCH-FIXME: 이 ViewModel 이 들고 있는 `currentScreen` 은 NavController 가 이미 관리하는 정보와 중복이다.
//  같은 파일(MainView.kt)에서 두 가지를 동시에 쓰고 있다.
//      val navBackStackEntry by controller.currentBackStackEntryAsState()   ← 진짜 출처
//      val currentRoute = navBackStackEntry?.destination?.route
//      viewModel.currentScreen.value = item                                 ← 복사본
//  "지금 어느 화면인가"에 대한 진실 공급원이 둘이라 둘이 어긋날 수 있고, 실제로 어긋난다
//  (뒤로 가기로 이동하면 NavController 만 바뀌고 ViewModel 은 그대로다).
//  고치기: NavController 를 단일 진실 공급원으로 삼고 이 ViewModel 을 없앤다.
//      val currentScreen = screensInBottom.find { it.bRoute == currentRoute }
//  ViewModel 은 "내비게이션 상태"가 아니라 "화면에 필요한 데이터"를 담을 때 가치가 있다.
//
// ARCH-FIXME: 이 챕터에는 데이터 계층이 전혀 없다. `Dummy.kt` 의 전역 `libraries` 리스트를
//  화면들이 직접 참조한다. 학습 단계라 괜찮지만, 실제 데이터로 바꿀 때
//  화면 코드를 전부 고쳐야 하는 구조라는 점은 알아 두는 게 좋다.
class MainViewModel : ViewModel() {
    // FIXME: `MutableState` 로 노출해 화면에서 상태를 직접 바꿀 수 있다.
    //  실제로 MainView 에 `viewModel.currentScreen.value = item` 이라는 코드가 있어,
    //  `setCurrentScreen()` 함수를 만들어 둔 의미가 사라졌다.
    //  고치기: `val currentScreen: State<Screen> get() = _currentScreen` 으로 내리고
    //         변경은 `setCurrentScreen()` 으로만 하게 한다.
    private val _currentScreen: MutableState<Screen> =
        mutableStateOf(Screen.DrawerScreen.Account)
    val currentScreen: MutableState<Screen> get() = _currentScreen

    fun setCurrentScreen(screen: Screen) {
        _currentScreen.value = screen
    }
}