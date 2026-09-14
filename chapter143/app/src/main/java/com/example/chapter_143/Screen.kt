package com.example.chapter_143

// TODO: [todos/kotlin-sealed-class-and-interface.md](../../../../../../../../todos/kotlin-sealed-class-and-interface.md)
sealed class Screen(val route: String) {

    object RecipeScreen : Screen("recipe-screen")
    object DetailScreen : Screen("detail-screen")
}