package com.example.chapter_186

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

// ARCH-FIXME: 이 클래스는 이름과 실제 역할이 다르다.
//  "Utils" 라는 이름은 상태 없는 헬퍼 함수 모음을 뜻하지만, 실제로는
//  위치 API 구독·권한 확인·주소 변환을 담당하는 **데이터 소스**다.
//  이름이 역할을 숨기면 사람들이 아무 데서나 인스턴스를 만들어 쓰게 된다(실제로 그렇게 되고 있다).
//  고치기: `LocationDataSource` 또는 `LocationRepository` 로 이름을 바꾸고 data 패키지로 옮긴다.
//
// ARCH-FIXME: 의존 방향이 뒤집혀 있다. MVVM 의 정상 방향은 아래와 같다.
//      View → ViewModel → Repository/DataSource
//  그런데 지금은 이렇다.
//      View(Composable) 가 LocationUtils 를 만든다        ← View 가 데이터 계층을 직접 생성
//      LocationUtils.requestLocationUpdate(viewModel)      ← DataSource 가 ViewModel 을 참조 (역방향)
//  하위 계층이 상위 계층을 알게 되어, 이 클래스는 다른 화면·다른 ViewModel 에서 재사용할 수 없고
//  단위 테스트를 하려면 ViewModel 인스턴스를 만들어 넘겨야 한다.
//  고치기: 결과를 흘려보내고 구독은 ViewModel 이 한다.
//      // DataSource
//      fun locationUpdates(): Flow<LocationData> = callbackFlow { ... awaitClose { remove... } }
//      // ViewModel
//      init { viewModelScope.launch { dataSource.locationUpdates().collect { _uiState.update { ... } } } }
class LocationUtils(val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // 이렇게 린팅을 스킵하는 방법도 있음
    fun requestLocationUpdate(viewModel: LocationViewModel) {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let {
                    val location = LocationData(latitude = it.latitude, longitude = it.longitude)
                    viewModel.updateLocation(location)
                }
            }
        }
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100).build()
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context, permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun reverseGeocodeLocation(location: LocationData): String {
        val geocoder = Geocoder(context, Locale.getDefault())
        val coordinate = LatLng(location.latitude, location.longitude)
        val addresses: MutableList<Address>? =
            geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1)
        return if (addresses?.isNotEmpty() == true) {
            addresses[0].getAddressLine(0)
        } else {
            "Not Address Found"
        }
    }
}
