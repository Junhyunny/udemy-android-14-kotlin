package com.example.chapter_186

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
//import com.google.maps.android.compose.rememberUpdatedMarkerState

@Composable
fun LocationSelectionScreen(
    location: LocationData,
    onLocationSelected: (LocationData) -> Unit
) {
    Log.d("LocationSelectionScreen", "logging")
    var newLocation: LocationData
    val userLocation = remember {
        mutableStateOf(
            LatLng(
                location.latitude,
                location.longitude
            )
        )
    }
    // TODO: [todos/compose-maps-marker-state-remember.md](../../../../../../../../todos/compose-maps-marker-state-remember.md)
    // val markerState = rememberUpdatedMarkerState(
    //     position = userLocation.value
    // )
    // TODO: [todos/compose-maps-camera-position-state.md](../../../../../../../../todos/compose-maps-camera-position-state.md)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLocation.value, 10f)
    }
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        GoogleMap(
            modifier = Modifier
                .weight(1f)
                .padding(top = 16.dp),
            cameraPositionState = cameraPositionState,
            onMapClick = {
                userLocation.value = it
            }
        ) {
            Marker(state = MarkerState(position = userLocation.value))
        }
        Button(onClick = {
            val userLocationData = userLocation.value
            newLocation = LocationData(userLocationData.latitude, userLocationData.longitude)
            onLocationSelected(newLocation)
        }) {
            Text("Set Location")
        }
    }
}