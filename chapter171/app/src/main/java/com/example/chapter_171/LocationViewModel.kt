package com.example.chapter_171

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

// FIXME: (참고) 이 챕터는 `State` 로 잘 내려 놓았다. chapter186 의 같은 클래스는 `MutableState` 로
//  노출돼 있어 캡슐화가 깨져 있으니, 그쪽을 이 파일 기준으로 맞추는 것이 좋다.
// ARCH-FIXME: ViewModel 이 자기 데이터를 스스로 가져오지 못하고, 외부(화면)가 `updateLocation()` 을
//  불러 주기를 기다리는 수동적인 구조다. 데이터 흐름의 주도권이 화면에 있다.
//  MVVM 에서 ViewModel 은 "필요한 데이터를 스스로 구해 상태로 만드는" 능동적 주체여야 한다.
//  고치기: 데이터 소스를 주입받아 ViewModel 이 직접 구독한다.
//      class LocationViewModel(private val repo: LocationRepository) : ViewModel() {
//          fun startTracking() { viewModelScope.launch { repo.locationUpdates().collect { ... } } }
//      }
class LocationViewModel : ViewModel() {
    private val _location = mutableStateOf<LocationData?>(null)
    val location: State<LocationData?> = _location

    fun updateLocation(newLocation: LocationData) {
        _location.value = newLocation
    }
}