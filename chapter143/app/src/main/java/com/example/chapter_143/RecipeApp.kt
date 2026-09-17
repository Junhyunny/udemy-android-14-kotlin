package com.example.chapter_143

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

// ARCH-FIXME: 패키지가 평평하다. 화면·ViewModel·네트워크·모델 8개 파일이 한 폴더에 있다.
//  고치기:
//      ui/      MainActivity.kt, RecipeApp.kt, RecipeScreen.kt, CategoryDetailScreen.kt, Screen.kt
//      ui/model MainViewModel.kt, RecipeUiState.kt
//      data/    ApiService.kt, RecipeRepository.kt
//      domain/  Category.kt
//
// ARCH-FIXME: `RecipeApp` 이 ViewModel 을 만들어 상태를 꺼낸 뒤 각 목적지에 값으로 내려보낸다.
//  그래서 상세 화면으로 갈 때 `savedStateHandle` 에 객체를 밀어 넣는 우회가 필요해졌다.
//  Navigation Compose 의 정석은 "목적지마다 필요한 ViewModel 을 그 안에서 얻는 것"이다.
//      composable(route = Screen.DetailScreen.route + "/{id}") { entry ->
//          val vm: DetailViewModel = viewModel()   // SavedStateHandle 로 id 를 자동 주입받는다
//          CategoryDetailScreen(uiState = vm.uiState.collectAsStateWithLifecycle().value)
//      }
//  이렇게 하면 화면 간에 객체를 들고 다닐 필요가 없고, 딥링크와 프로세스 종료 복귀에도 안전하다.
@Composable
fun RecipeApp(navController: NavHostController) {
    val recipeViewModel: MainViewModel = viewModel()
    val viewState by recipeViewModel.categoriesState
    NavHost(
        navController = navController,
        startDestination = Screen.RecipeScreen.route
    ) {
        composable(route = Screen.RecipeScreen.route) {
            RecipeScreen(viewState = viewState) {
                // TODO: [todos/android-navigation-backstackentry-savedstatehandle.md](../../../../../../../../todos/android-navigation-backstackentry-savedstatehandle.md)
                navController.currentBackStackEntry?.savedStateHandle?.set("cat", it)
                navController.navigate(Screen.DetailScreen.route)
            }
        }
        composable(route = Screen.DetailScreen.route) {
            // FIXME: 인자를 못 받았을 때 "빈 문자열로 채운 가짜 Category" 로 대체하고 있다.
            //  사용자에게는 아무 내용 없는 상세 화면이 그대로 보인다(실패를 숨긴 것).
            //  고치기: null 을 그대로 다뤄 분기한다.
            //      val category = ...get<Category>("cat")
            //      if (category == null) { ErrorScreen(); return@composable }
            //  더 나은 방법은 route 에 id 를 담아 상세 화면이 스스로 조회하는 것이다.
            //  `savedStateHandle` 방식은 프로세스 종료 후 복귀나 딥링크 진입에서 값이 없다.
            val value = navController.previousBackStackEntry?.savedStateHandle?.get<Category>("cat")
                ?: Category(
                    idCategory = "",
                    strCategory = "",
                    strCategoryThumb = "",
                    strCategoryDescription = ""
                )
            // FIXME: 학습용 로그가 남아 있다. 태그도 로그 태그가 아니라 문장이다. 삭제 대상.
            Log.i("before detail screen call", value.toString())
            CategoryDetailScreen(category = value)
            Log.i("after detail screen call", value.toString())
        }
    }
}