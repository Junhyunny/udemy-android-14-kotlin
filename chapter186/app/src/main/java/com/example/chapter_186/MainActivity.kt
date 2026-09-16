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

@Composable
fun Navigation() {
    val navController: NavHostController = rememberNavController()
    val viewModel: LocationViewModel = viewModel()
    val context = LocalContext.current
    val locationUtils = LocationUtils(context)
    // TODO: [todos/android-navigation-package-compose-vs-runtime.md](../../../../../../../../todos/android-navigation-package-compose-vs-runtime.md)
    // TODO: [todos/android-navigation-navhost-composable-dialog.md](../../../../../../../../todos/android-navigation-navhost-composable-dialog.md)
    NavHost(
        navController = navController,
        startDestination = "shoppinglistscreen"
    ) {
        composable("shoppinglistscreen") {
            ShoppingListApp(
                locationUtils = locationUtils,
                viewModel = viewModel,
                navController = navController,
                context = context,
                address = viewModel.address.value.firstOrNull()?.formatted_address ?: "No Address"
            )
        }
        dialog("locationscreen") {
            Log.d("navigation", "hello")
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