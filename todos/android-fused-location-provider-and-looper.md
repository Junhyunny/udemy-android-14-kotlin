# `Looper`와 `fusedLocationClient`는 무엇을 하는가

## 질문이 나온 코드

- [`chapter171/app/src/main/java/com/example/chapter_171/LocationUtils.kt`](../chapter171/app/src/main/java/com/example/chapter_171/LocationUtils.kt)
- 질문: `Looper`와 `fusedLocationClient` 기능에 대해서 설명해줘.

```kotlin
private val fusedLocationClient: FusedLocationProviderClient =
    LocationServices.getFusedLocationProviderClient(context)

val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100).build()
fusedLocationClient.requestLocationUpdates(
    locationRequest,
    locationCallback,
    Looper.getMainLooper()
)
```

## 질문 전제 점검

- **둘은 층위가 완전히 다른 개념이다.** 한 줄에 같이 나와서 짝처럼 보이지만 관계가 없다.

  | | 정체 |
  | --- | --- |
  | `FusedLocationProviderClient` | **Google Play services**의 위치 API 진입점 |
  | `Looper` | **안드로이드 프레임워크**의 스레드 메시지 루프 |

  `requestLocationUpdates`의 세 번째 인자가 `Looper`인 이유는 단 하나, **"콜백을 어느 스레드에서 받을지"**를 지정하기 위해서다.

  > "The `Looper` object specifies the thread for the callback"

- **`Looper.getMainLooper()`는 "메인 스레드에서 콜백을 받겠다"는 뜻일 뿐이다.** 위치를 메인 스레드에서 **계산한다**는 뜻이 아니다. 위치 측정은 시스템 프로세스에서 이뤄지고, 결과만 지정한 스레드로 배달된다.

- **`LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100)`의 `100`은 밀리초다.** 즉 **0.1초마다 갱신**을 요청하고 있다. 여기에 `PRIORITY_HIGH_ACCURACY`(GPS 사용)가 겹치면 **배터리를 매우 빠르게 소모하는 조합**이다. 강의 예제라 즉시 결과를 보려고 짧게 잡은 것으로 보이는데, 실제 앱에서 이대로 쓰면 안 된다.

- **그리고 이 코드에는 `removeLocationUpdates`가 없다.** 한 번 시작한 위치 업데이트가 **영원히 멈추지 않는다.** 버튼을 누를 때마다 새 콜백이 추가로 등록되기까지 한다. 공식 문서가 명시적으로 다루는 부분이다.

  > "Consider whether you want to stop the location updates when the activity is no longer in focus ... This can be handy to reduce power consumption"

## 공부할 내용

### `FusedLocationProviderClient` — 무엇을 해 주는가

FLP는 여러 위치 소스를 **융합(fuse)**한다.

```
GPS 위성     ─┐  정확하지만 느리고, 실내에서 안 잡히고, 배터리를 먹는다
Wi-Fi AP     ─┼→ Fused Location Provider → Location 하나
기지국        ─┤  덜 정확하지만 빠르고 배터리를 덜 쓴다
가속도/자이로  ─┘
```

**개발자는 "얼마나 정확하게, 얼마나 자주"만 말하고, 어느 소스를 쓸지는 FLP가 정한다.** 프레임워크의 `LocationManager`를 직접 쓰면 이 판단을 전부 손으로 해야 한다.

```kotlin
// 진입점 — Context 로부터 클라이언트를 얻는다
val client = LocationServices.getFusedLocationProviderClient(context)
```

주요 기능은 셋이다.

| 메서드 | 하는 일 | 쓰는 때 |
| --- | --- | --- |
| `lastLocation` | 시스템이 **이미 갖고 있는** 마지막 위치 | 가장 빠르고 싸다. 대략적 위치면 충분할 때 |
| `getCurrentLocation()` | 지금 한 번 측정 | 일회성 조회 |
| `requestLocationUpdates()` | 계속 갱신 | 내비게이션, 운동 기록, 실시간 추적 |
| `removeLocationUpdates()` | 갱신 중지 | **반드시 짝을 맞춘다** |

`lastLocation`은 **null일 수 있다.** 기기가 부팅 후 한 번도 위치를 잡지 않았거나, 위치 설정이 꺼져 있었다면 값이 없다.

### `LocationRequest` — 정확도와 주기의 거래

```kotlin
val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100)
    .setMinUpdateIntervalMillis(50)      // 이보다 빠르게는 받지 않는다
    .setMaxUpdateDelayMillis(1000)       // 묶어서 배달해도 되는 최대 지연
    .build()
```

`Priority`는 **"어느 정도의 배터리를 쓸 각오가 돼 있는가"**를 말한다.

| Priority | 사용 소스 | 정확도 | 배터리 |
| --- | --- | --- | --- |
| `PRIORITY_HIGH_ACCURACY` | GPS 포함 | ~수 m | 많이 쓴다 |
| `PRIORITY_BALANCED_POWER_ACCURACY` | Wi-Fi, 기지국 | ~100 m | 보통 |
| `PRIORITY_LOW_POWER` | 기지국 위주 | ~10 km | 적게 쓴다 |
| `PRIORITY_PASSIVE` | **다른 앱이 요청할 때 묻어간다** | 상황에 따라 | 거의 안 쓴다 |

두 번째 인자인 간격은 **"이 정도 주기로 받고 싶다"는 희망**이지 보장이 아니다. 시스템은 배터리 상태와 다른 앱의 요청을 고려해 조정한다. 반대로 **다른 앱이 더 자주 요청하고 있으면 더 자주 올 수도 있다.**

이 코드를 실제 앱 수준으로 고치면 이렇게 된다.

```kotlin
// 지도에 내 위치를 표시하는 정도라면
val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000)
    .setMinUpdateIntervalMillis(5_000)
    .build()
```

### `Looper` — 스레드에 붙은 메시지 루프

`Looper`는 위치와 아무 상관이 없는, **안드로이드 스레드 모델의 기본 부품**이다.

```
Thread + Looper + MessageQueue + Handler
         └── 무한 루프를 돌며 큐에서 메시지를 꺼내 처리한다
```

일반 자바 스레드는 `run()`이 끝나면 죽는다. `Looper`를 붙이면 **큐를 감시하며 계속 살아 있는 스레드**가 된다. 안드로이드의 **메인 스레드(UI 스레드)가 바로 이 구조**다. 터치 이벤트, 화면 그리기, 브로드캐스트가 전부 메인 `Looper`의 큐를 통해 처리된다.

```kotlin
Looper.getMainLooper()   // 메인 스레드의 Looper — 항상 존재한다
Looper.myLooper()        // 현재 스레드의 Looper — 없으면 null
```

**왜 `requestLocationUpdates`가 `Looper`를 받는가?** 콜백을 아무 스레드에서나 부르면 UI를 건드릴 수 없기 때문이다. 안드로이드는 **UI를 메인 스레드에서만 만질 수 있다.**

```kotlin
override fun onLocationResult(locationResult: LocationResult) {
    viewModel.updateLocation(location)   // 상태 변경 → UI 갱신
}
```

`Looper.getMainLooper()`를 넘겼으므로 이 콜백은 메인 스레드에서 실행되고, 그래서 UI 상태를 안전하게 바꿀 수 있다.

**백그라운드에서 받고 싶다면** 자체 `Looper`를 가진 스레드를 만든다.

```kotlin
val handlerThread = HandlerThread("location").apply { start() }
fusedLocationClient.requestLocationUpdates(request, callback, handlerThread.looper)
```

무거운 후처리(DB 저장, 네트워크 전송)가 있을 때 쓴다. 다만 요즘은 코루틴으로 처리하는 편이 낫다.

### 콜백 대신 `Flow`로 감싸기

콜백 API는 코틀린에서 다루기 불편하다. `callbackFlow`로 감싸면 **생명주기와 해제가 자동으로 맞아떨어진다.**

```kotlin
@RequiresPermission(anyOf = [permission.ACCESS_COARSE_LOCATION, permission.ACCESS_FINE_LOCATION])
fun locationUpdates(): Flow<LocationData> = callbackFlow {
    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                trySend(LocationData(it.latitude, it.longitude))
            }
        }
    }
    val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000).build()
    fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

    awaitClose {
        fusedLocationClient.removeLocationUpdates(callback)   // 구독이 끊기면 자동 해제
    }
}
```

`awaitClose`가 핵심이다. **수집을 멈추는 순간 위치 업데이트도 멈춘다.** 지금 코드의 "영원히 멈추지 않는" 문제가 구조적으로 해결된다.

→ [`kotlin-flow-concepts-and-suspend.md`](kotlin-flow-concepts-and-suspend.md), [`compose-collectasstate-flow-to-state.md`](compose-collectasstate-flow-to-state.md)

### 현재 구조의 문제 정리

```kotlin
fun requestLocationUpdate(viewModel: LocationViewModel) {   // ① 의존 방향
    val locationCallback = object : LocationCallback() {     // ② 참조를 안 갖고 있다
        ...
    }
    ...
    fusedLocationClient.requestLocationUpdates(...)          // ③ 해제가 없다
}
```

| 문제 | 결과 |
| --- | --- |
| ① `LocationUtils`가 `LocationViewModel`을 안다 | 하위 계층이 상위 계층에 의존한다. 테스트·재사용이 어렵다 |
| ② `locationCallback`이 지역 변수다 | **해제하려 해도 참조가 없다.** `removeLocationUpdates`를 부를 수 없다 |
| ③ 중지 로직이 없다 | 버튼을 누를 때마다 콜백이 쌓이고, 화면을 떠나도 계속 돈다 |

②가 특히 중요하다. `removeLocationUpdates(callback)`는 **등록할 때 쓴 바로 그 콜백 객체**를 넘겨야 한다. 지역 변수로 만들면 해제가 원천적으로 불가능하다.

## 관련 아키텍처와 베스트 프랙티스

### 시작과 중지는 반드시 짝을 맞춘다

```kotlin
override fun onResume() {
    super.onResume()
    if (requestingLocationUpdates) startLocationUpdates()
}

override fun onPause() {
    super.onPause()
    stopLocationUpdates()
}

private fun stopLocationUpdates() {
    fusedLocationClient.removeLocationUpdates(locationCallback)
}
```

Compose라면 `DisposableEffect`가 같은 역할을 한다.

```kotlin
DisposableEffect(Unit) {
    locationUtils.startUpdates()
    onDispose { locationUtils.stopUpdates() }
}
```

**"시작하는 코드를 썼으면 멈추는 코드를 같은 호흡에 쓴다."** 위치·센서·브로드캐스트 리시버·미디어 플레이어에 공통으로 적용되는 규칙이다.

### 정확도는 필요한 만큼만 요청한다

| 유즈 케이스 | Priority | 간격 |
| --- | --- | --- |
| 턴바이턴 내비게이션 | `HIGH_ACCURACY` | 1초 |
| 운동 경로 기록 | `HIGH_ACCURACY` | 5~10초 |
| 근처 매장 찾기 | `BALANCED_POWER` | 일회성 (`getCurrentLocation`) |
| 날씨, 지역 뉴스 | `LOW_POWER` 또는 `lastLocation` | 매우 느슨 |

**대부분의 앱은 `lastLocation` 한 번이면 충분하다.** `requestLocationUpdates`가 정말 필요한지 먼저 묻는 것이 배터리 관점에서 가장 큰 개선이다.

### 백그라운드 위치는 완전히 다른 문제다

앱이 화면에 없을 때도 위치가 필요하면 `ACCESS_BACKGROUND_LOCATION` 권한과 **포그라운드 서비스**가 추가로 필요하다. 심사도 까다롭다. 화면이 켜져 있을 때만 필요하다면 그 범위를 넘지 않는 것이 좋다.

### 메인 스레드에서 하면 안 되는 일

콜백이 메인 스레드에서 돈다는 것은 **거기서 무거운 일을 하면 화면이 멈춘다**는 뜻이기도 하다.

```kotlin
override fun onLocationResult(result: LocationResult) {
    // 나쁨: 메인 스레드에서 동기 네트워크 호출
    val address = geocoder.getFromLocation(lat, lon, 1)
}
```

이 프로젝트의 `reverseGeocodeLocation`이 정확히 이 형태다. → [`android-play-services-location-dependency.md`](android-play-services-location-dependency.md), [`kotlin-coroutine-dispatchers.md`](kotlin-coroutine-dispatchers.md)

## 체크리스트

- [ ] `FusedLocationProviderClient`와 `Looper`가 서로 다른 층위임을 안다.
- [ ] FLP가 여러 위치 소스를 융합한다는 것을 설명할 수 있다.
- [ ] `lastLocation`, `getCurrentLocation`, `requestLocationUpdates`를 구분할 수 있다.
- [ ] `lastLocation`이 null일 수 있는 이유를 안다.
- [ ] `Priority` 네 가지의 차이를 설명할 수 있다.
- [ ] 요청 간격이 보장이 아니라 희망임을 안다.
- [ ] `Looper`가 스레드 메시지 루프라는 것을 설명할 수 있다.
- [ ] `Looper.getMainLooper()`를 넘기는 이유를 설명할 수 있다.
- [ ] 콜백을 지역 변수로 만들면 해제할 수 없는 이유를 안다.
- [ ] `removeLocationUpdates`를 반드시 불러야 하는 이유를 안다.
- [ ] `callbackFlow` + `awaitClose`로 해제를 자동화할 수 있다.
- [ ] 유즈 케이스별로 적절한 `Priority`를 고를 수 있다.

## 공식 참고 자료

- [Android Developers: Request location updates](https://developer.android.com/develop/sensors-and-location/location/request-updates)
- [Android Developers: Get the last known location](https://developer.android.com/develop/sensors-and-location/location/retrieve-current)
- [Android Developers: Change location settings](https://developer.android.com/develop/sensors-and-location/location/change-location-settings)
- [Google for Developers: `FusedLocationProviderClient`](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)
- [Google for Developers: `LocationRequest`](https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest)
- [Google for Developers: `Priority`](https://developers.google.com/android/reference/com/google/android/gms/location/Priority)
- [Android Developers: `Looper`](https://developer.android.com/reference/android/os/Looper)
- [Android Developers: `Handler`](https://developer.android.com/reference/android/os/Handler)
- [Android Developers: Background work and app lifecycle](https://developer.android.com/guide/background)
- [Kotlin Docs: `callbackFlow` (asynchronous flow)](https://kotlinlang.org/docs/flow.html)
