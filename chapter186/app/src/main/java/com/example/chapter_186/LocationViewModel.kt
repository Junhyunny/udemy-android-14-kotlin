package com.example.chapter_186

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LocationViewModel : ViewModel() {
    // TODO: [todos/android-viewmodel-state-exposure-patterns.md](../../../../../../../../todos/android-viewmodel-state-exposure-patterns.md)
    private val _location = mutableStateOf<LocationData?>(null)
    val location: MutableState<LocationData?> = _location

    private val _address = mutableStateOf(listOf<GeocodingResult>())
    val address: MutableState<List<GeocodingResult>> = _address

    fun updateLocation(newLocation: LocationData) {
        _location.value = newLocation
    }

    // TODO: [todos/kotlin-coroutines-exception-handling-try-catch.md](../../../../../../../../todos/kotlin-coroutines-exception-handling-try-catch.md)
    fun fetchAddress(latLng: String) {
        viewModelScope.launch {
            try {
                val result = RetrofitClient.create().getAddressFromCoordinate(
                    latLng,
                    // TODO: [todos/android-api-key-secure-management.md](../../../../../../../../todos/android-api-key-secure-management.md)
                    BuildConfig.MAPS_API_KEY
                )
                Log.i("fetchAddress", result.results.toString())
                _address.value = result.results
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("fetchAddress", "주소 조회 실패: $latLng", e)
            }
        }
    }
}