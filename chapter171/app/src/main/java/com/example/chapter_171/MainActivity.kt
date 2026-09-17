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

// ARCH-FIXME: chapter186 과 같은 구조적 문제를 공유한다(이쪽이 원형이다).
//   1) 화면(Composable)이 데이터 소스 `LocationUtils` 를 직접 생성한다 → View 가 data 계층을 안다
//   2) `LocationUtils.requestLocationUpdate(viewModel)` → 하위 계층이 ViewModel 을 참조(역방향 의존)
//   3) Repository 계층이 없다
//   4) 주소 변환(`reverseGeocodeLocation`)을 화면이 직접 호출한다 → 화면이 데이터 가공까지 한다
//  정상 방향:
//      MyApp(Composable) → LocationViewModel → LocationRepository → LocationDataSource / Geocoder
//  고치기: 화면은 `viewModel.uiState` 를 읽고 `viewModel.onAddressRequested()` 를 부르기만 한다.
//  위치 구독 시작/중지, 주소 변환, 에러 처리는 전부 ViewModel 아래로 내린다.
@Composable
fun MyApp(viewModel: LocationViewModel) {
    val context = LocalContext.current
    // FIXME: 재구성될 때마다 `LocationUtils` 인스턴스가 새로 만들어진다.
    //  내부에서 `FusedLocationProviderClient` 까지 매번 생성한다.
    //  고치기: `val locationUtils = remember(context) { LocationUtils(context) }`
    //  더 나은 구조는 ViewModel 이 주입받아 들고 있는 것이다(화면은 위치 API 를 몰라야 한다).
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
    // FIXME: 컴포저블 본문에서 동기 네트워크 호출(`reverseGeocodeLocation`)을 하고 있다.
    //  재구성될 때마다 실행되어 프레임을 멈추게 하고, 실패하면 화면이 통째로 죽는다.
    //  고치기: ViewModel 에서 좌표가 바뀔 때만 조회해 상태로 내려 준다.
    //      LaunchedEffect(location.value) { location.value?.let { viewModel.loadAddress(it) } }
    val address = location.value?.let {
        locationUtils.reverseGeocodeLocation(location = it)
    }
    // TODO: [todos/074-android-activity-result-api-and-launcher.md](../../../../../../../../todos/074-android-activity-result-api-and-launcher.md)
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        Log.i("testing", permissions.toString())
        // TODO: [todos/076-android-location-permission-coarse-vs-fine.md](../../../../../../../../todos/076-android-location-permission-coarse-vs-fine.md)
        // FIXME: `&&` 라서 사용자가 "대략적인 위치"만 허용한 경우를 완전 거부와 똑같이 취급한다.
        //  사용자는 권한을 줬는데 "권한이 필요합니다" 안내를 받게 된다.
        //  공식 권장은 세 갈래 분기다.
        //      when {
        //          permissions[ACCESS_FINE_LOCATION] == true -> { /* 정밀 */ }
        //          permissions[ACCESS_COARSE_LOCATION] == true -> { /* 대략 — 이것도 동작한다 */ }
        //          else -> { /* 거부 */ }
        //      }
        if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true && permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            // FIXME: 태그가 "testing", 내용이 "here can you see?" 인 확인용 로그다.
            //  이 파일의 `Log.i("testing", ...)` 3개 모두 삭제 대상이다.
            Log.i("testing", "here can you see?")
            locationUtils.requestLocationUpdate(viewModel = viewModel)
        } else {
            // TODO: [todos/077-android-should-show-request-permission-rationale.md](../../../../../../../../todos/077-android-should-show-request-permission-rationale.md)
            // FIXME: `context as MainActivity` 는 안전하지 않은 캐스팅이다.
            //  `LocalContext.current` 가 항상 MainActivity 라는 보장이 없어(미리보기, ContextWrapper 등)
            //  `ClassCastException` 이 날 수 있다.
            //  고치기: 안전하게 액티비티를 찾는다.
            //      fun Context.findActivity(): Activity? = when (this) {
            //          is Activity -> this
            //          is ContextWrapper -> baseContext.findActivity()
            //          else -> null
            //      }
            // FIXME: 권한 요청 "전"에 설명하는 것이 공식 권장 흐름인데, 여기서는 거부당한 "후"에
            //  Toast 로 알리기만 한다. Toast 는 사라지고 재요청 버튼도 없어 사용자가 할 수 있는 일이 없다.
            //  고치기: rationale 이 필요하면 다이얼로그로 설명 후 재요청, 영구 거부면 설정 화면으로 보낸다.
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