package com.example.chapter_205

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun Navigation(
    navController: NavHostController = rememberNavController(),
    viewModel: WishViewModel = viewModel(),
) {
    NavHost(
        navController = navController,
        startDestination = Screen.HomeScreen.route
    ) {
        composable(route = Screen.HomeScreen.route) {
            HomeView(navController, viewModel)
        }
        composable(
            route = Screen.AddScreen.route + "/{id}",
            // TODO: [todos/android-navigation-navargument-setup.md](../../../../../../../../todos/android-navigation-navargument-setup.md)
            arguments = listOf(
                navArgument("id") {
                    type = NavType.LongType
                    defaultValue = 0L
                    nullable = false
                }
            )) { entry ->
            // FIXME: `if (entry.arguments != null)` 검사가 불필요하다.
            //  뒤의 `?.` 와 `?: 0L` 이 이미 null 을 처리하므로 조건 전체가 중복이다.
            //  고치기: `val id = entry.arguments?.getLong("id") ?: 0L`
            val id = if (entry.arguments != null) entry.arguments?.getLong("id") ?: 0L else 0L
            AddEditDetailView(id, navController, viewModel)
        }
    }
}