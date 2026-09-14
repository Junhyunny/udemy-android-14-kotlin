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

@Composable
fun MyApp() {
    // TODO: [todos/android-navigation-string-route-vs-type-safe.md](../../../../../../../../todos/android-navigation-string-route-vs-type-safe.md)
    val navController = rememberNavController()
    NavHost(
        navController = navController, startDestination = "firstScreen"
    ) {
        composable("firstScreen") {
            FirstScreen { name, age ->
                navController.navigate("secondScreen/${name}/${age}")
            }
        }
        // TODO: [todos/android-navigation-passing-many-arguments.md](../../../../../../../../todos/android-navigation-passing-many-arguments.md)
        composable(route = "secondScreen/{name}/{age}") {
            val name = it.arguments?.getString("name") ?: "no name"
            val ageString = it.arguments?.getString("age") ?: "0"
            SecondScreen(name, ageString.toInt()) {
                navController.navigate("firstScreen")
            }
        }
    }
}
