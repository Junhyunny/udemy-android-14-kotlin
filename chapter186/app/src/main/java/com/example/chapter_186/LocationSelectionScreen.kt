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
    // FIXME: 학습용 로그다. 삭제 대상.
    Log.d("LocationSelectionScreen", "logging")
    // FIXME: 람다 안에서만 쓰는 값을 컴포저블 본문의 지역 변수로 선언했다.
    //  컴포저블 본문은 재구성마다 다시 실행되므로 여기에 값을 담아 둘 수도 없다.
    //  고치기: 선언을 없애고 `onLocationSelected(LocationData(...))` 로 바로 넘긴다.
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
            // FIXME: 컴포저블 본문에서 `MarkerState` 를 새로 만들고 있다.
            //  재구성마다 새 인스턴스가 생겨 드래그 상태·정보창 상태가 초기화된다.
            //  고치기(현재 버전): val markerState = remember { MarkerState(userLocation.value) }
            //                    LaunchedEffect(userLocation.value) { markerState.position = userLocation.value }
            //  고치기(버전 올린 뒤): rememberUpdatedMarkerState(position = userLocation.value)
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