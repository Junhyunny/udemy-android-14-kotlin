package com.example.chapter_171

// TODO: [todos/android-play-services-location-dependency.md](../../../../../../../../todos/android-play-services-location-dependency.md)
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

class LocationUtils(val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // TODO: [todos/android-requires-permission-annotation.md](../../../../../../../../todos/android-requires-permission-annotation.md)
//    @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_COARSE_LOCATION])
    // FIXME: 하위 유틸리티가 상위 계층인 `LocationViewModel` 을 파라미터로 받는다. 의존 방향이 거꾸로다.
    //  이 클래스는 다른 화면에서 재사용할 수도, 테스트할 수도 없다.
    //  고치기: 결과를 흘려보내고 구독은 위에서 하게 한다.
    //      fun locationUpdates(): Flow<LocationData> = callbackFlow { ... awaitClose { removeLocationUpdates(cb) } }
    @SuppressLint("MissingPermission") // 이렇게 린팅을 스킵하는 방법도 있음
    fun requestLocationUpdate(viewModel: LocationViewModel) {
        // FIXME: `locationCallback` 이 지역 변수라 이 함수를 벗어나면 참조가 사라진다.
        //  `removeLocationUpdates(callback)` 는 "등록할 때 쓴 그 객체"를 넘겨야 하므로
        //  구조적으로 해제가 불가능하다. 실제로 이 파일에는 해제 코드가 아예 없다.
        //  결과: 버튼을 누를 때마다 콜백이 새로 쌓이고, 화면을 떠나도 위치 수신이 멈추지 않는다(배터리 소모).
        //  고치기: 콜백을 프로퍼티로 올리고 `stopLocationUpdate()` 를 만들어 짝을 맞춘다.
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let {
                    val location = LocationData(latitude = it.latitude, longitude = it.longitude)
                    viewModel.updateLocation(location)
                }
            }
        }
        // FIXME: `PRIORITY_HIGH_ACCURACY`(GPS 사용) + 100ms 주기는 배터리를 매우 빠르게 소모하는 조합이다.
        //  화면에 좌표를 한 번 보여 주는 용도에는 과하다.
        //  고치기: 용도에 맞춰 낮춘다. 예) Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L
        //  일회성 조회면 `getCurrentLocation()` 이나 `lastLocation` 으로 충분하다.
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100).build()
        // TODO: [todos/android-fused-location-provider-and-looper.md](../../../../../../../../todos/android-fused-location-provider-and-looper.md)
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    // FIXME: 두 권한을 `&&` 로 묶어 "둘 다 있어야 true" 로 판정한다.
    //  Android 12+ 에서 사용자가 "대략적인 위치"를 선택하면 COARSE 만 허용되는데,
    //  이 경우 위치를 받을 수 있는데도 권한이 없는 것으로 취급한다.
    //  Play services 도 `@RequiresPermission(anyOf = [...])` 로 "둘 중 하나"를 요구한다.
    //  고치기: `&&` 를 `||` 로 바꾼다.
    // FIXME: 생성자에서 이미 `context` 를 받는데 이 함수만 또 파라미터로 받는다.
    //  호출자가 다른 context 를 넘길 수 있어 혼란스럽다. 고치기: 프로퍼티 `context` 를 쓰고 파라미터를 없앤다.
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context, permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // FIXME: `Geocoder.getFromLocation(lat, lon, max)` 동기 버전은 네트워크를 타는 블로킹 호출이고
    //  API 33 부터 deprecated 다(빌드 시 경고가 뜬다). 메인 스레드에서 부르면 ANR 위험이 있다.
    //  게다가 `MainActivity` 가 이 함수를 컴포저블 본문에서 직접 호출해 재구성마다 실행된다.
    //  고치기: suspend 함수로 바꾸고 API 33+ 는 콜백 버전을 쓴다.
    //      suspend fun reverseGeocodeLocation(...): String = withContext(Dispatchers.IO) { ... }
    //  호출은 컴포저블이 아니라 ViewModel 에서 한다.
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