package com.example.chapter_171

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chapter_171.ui.theme.Chapter171Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: LocationViewModel = viewModel()
            Chapter171Theme {
                MyApp(viewModel)
            }
        }
    }
}

@Composable
fun MyApp(viewModel: LocationViewModel) {
    val context = LocalContext.current
    val locationUtils = LocationUtils(context)
    LocationDisplay(locationUtils, viewModel, context)
}

@Composable
fun LocationDisplay(
    locationUtils: LocationUtils,
    viewModel: LocationViewModel,
    context: Context
) {
    val location = viewModel.location
    val address = location.value?.let {
        locationUtils.reverseGeocodeLocation(location = it)
    }
    // TODO: [todos/android-activity-result-api-and-launcher.md](../../../../../../../../todos/android-activity-result-api-and-launcher.md)
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        Log.i("testing", permissions.toString())
        // TODO: [todos/android-location-permission-coarse-vs-fine.md](../../../../../../../../todos/android-location-permission-coarse-vs-fine.md)
        if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true && permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            Log.i("testing", "here can you see?")
            locationUtils.requestLocationUpdate(viewModel = viewModel)
        } else {
            // TODO: [todos/android-should-show-request-permission-rationale.md](../../../../../../../../todos/android-should-show-request-permission-rationale.md)
            val rationalRequired = ActivityCompat.shouldShowRequestPermissionRationale(
                context as MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
            ) || ActivityCompat.shouldShowRequestPermissionRationale(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            )
            Log.i("testing", "rationalRequired - $rationalRequired")
            if (rationalRequired) {
                Toast.makeText(
                    context,
                    "Location Permission is required for this feature to work",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Location Permission is required. Please enable it in the Android settings",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (location.value != null) {
            Text("Address: ${location.value!!.latitude} ${location.value!!.longitude}\n$address")
        } else {
            Text("Location not available")
        }
        Button(
            onClick = {
                if (locationUtils.hasLocationPermission(context)) {
                    locationUtils.requestLocationUpdate(viewModel)
                } else {
                    requestPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                    )
                }
            }) {
            Text("Get Location")
        }
    }
}