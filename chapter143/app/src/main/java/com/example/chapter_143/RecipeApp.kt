package com.example.chapter_143

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

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
            val value = navController.previousBackStackEntry?.savedStateHandle?.get<Category>("cat")
                ?: Category(
                    idCategory = "",
                    strCategory = "",
                    strCategoryThumb = "",
                    strCategoryDescription = ""
                )
            Log.i("before detail screen call", value.toString())
            CategoryDetailScreen(category = value)
            Log.i("after detail screen call", value.toString())
        }
    }
}