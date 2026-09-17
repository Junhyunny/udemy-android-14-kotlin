package com.example.chapter_186

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ARCH-FIXME: Repository 계층이 아예 없다. ViewModel 이 네트워크 세부사항을 직접 알고 있다.
//      LocationViewModel → RetrofitClient.create() → GeocodingApiService
//  ViewModel 이 "무엇을 보여 줄지"가 아니라 "어떻게 가져올지"까지 책임지고 있다.
//  같은 프로젝트의 chapter205 는 Repository 를 두었는데 여기서는 빠져 있어 일관성도 없다.
//  고치기:
//      class GeocodingRepository(private val api: GeocodingApiService) {
//          suspend fun addressOf(lat: Double, lng: Double): Result<String> = ...
//      }
//      class LocationViewModel(private val repo: GeocodingRepository) : ViewModel()
//  이러면 API 응답 구조(GeocodingResult.formatted_address)가 ViewModel 위로 올라가지 않는다.
//
// ARCH-FIXME: 의존성을 생성자로 받지 않고 함수 안에서 전역 object 를 호출한다(`RetrofitClient.create()`).
//  주입 지점이 없어 테스트에서 네트워크를 대체할 수 없다.
//  고치기: 생성자 주입 + ViewModelProvider.Factory. (chapter205 Graph.kt 의 [2단계] 주석 참고)
//
// ARCH-FIXME: 이 ViewModel 의 이름은 `LocationViewModel` 인데 실제로는 쇼핑 목록 화면의 ViewModel 이다.
//  정작 이 앱의 핵심 데이터인 "쇼핑 목록"은 ViewModel 이 아니라 ShoppingList.kt 의 `remember` 에 있다.
//  화면 회전 한 번으로 목록이 전부 사라진다.
//  고치기: 쇼핑 목록 상태를 ViewModel 로 올리고, 위치/주소는 별도 ViewModel 이나 UiState 필드로 둔다.
class LocationViewModel : ViewModel() {
    // TODO: [todos/android-viewmodel-state-exposure-patterns.md](../../../../../../../../todos/android-viewmodel-state-exposure-patterns.md)
    // FIXME: `_` 로 숨겼는데 공개 타입이 `MutableState` 라 캡슐화가 성립하지 않는다.
    //  화면에서 `viewModel.location.value = ...` 로 직접 대입할 수 있다.
    //  고치기: 읽기 전용 타입으로 내린다. (chapter171 의 LocationViewModel 이 올바른 예다)
    //      val location: State<LocationData?> = _location
    //      val address: State<List<GeocodingResult>> = _address
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
                // FIXME: Google Geocoding API 는 HTTP 200 을 주면서 본문 `status` 로 실패를 알린다.
                //  (예: REQUEST_DENIED — 잘못된 키, ZERO_RESULTS — 결과 없음)
                //  지금은 `status` 를 확인하지 않아 실패해도 빈 목록이 조용히 들어간다.
                //  try-catch 로도 잡히지 않는 실패라 "아무 일도 안 일어나는" 증상이 된다.
                //  고치기: when (result.status) { "OK" -> ...; else -> 에러 처리 }
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