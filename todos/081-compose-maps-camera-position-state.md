# `rememberCameraPositionState`와 블록 안의 `position`

## 질문이 나온 코드

- [`chapter186/app/src/main/java/com/example/chapter_186/LocationSelectionScreen.kt`](../chapter186/app/src/main/java/com/example/chapter_186/LocationSelectionScreen.kt)
- 질문: `rememberCameraPositionState` 기능과 내부에 클로저에서 업데이트하는 `position` 값은 무엇이지? 어디서 등장한 데이터인지 확인 바람. 무엇을 위한 API인지 정리해줘.

```kotlin
val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(userLocation.value, 10f)
}

GoogleMap(
    cameraPositionState = cameraPositionState,
    onMapClick = { userLocation.value = it }
) { ... }
```

## 질문 전제 점검

- **"어디서 등장한 데이터인지"가 질문의 핵심이고, 답은 "새로 만들어진 객체의 프로퍼티"다.** 바깥에서 가져온 변수가 아니다.

  ```kotlin
  // 시그니처
  fun rememberCameraPositionState(
      key: String? = null,
      init: CameraPositionState.() -> Unit = {}
  ): CameraPositionState
  ```

  `init`이 **수신 객체가 있는 람다**다. 블록 안의 `position`은 **방금 만들어진 `CameraPositionState` 인스턴스의 프로퍼티**다. `this.position`과 같다.

  ```kotlin
  rememberCameraPositionState {
      position = ...        // this.position = ...  (this 는 CameraPositionState)
  }
  ```

  같은 문법이 `navigate { launchSingleTop = true }`, `LocationRequest.Builder().apply { }`에도 쓰인다.

  → [`072-android-navigation-navoptions-builder.md`](072-android-navigation-navoptions-builder.md), [`002-kotlin-trailing-lambda-and-content-slot.md`](002-kotlin-trailing-lambda-and-content-slot.md)

- **"클로저에서 업데이트한다"는 표현은 반만 맞다.** 이 블록은 **딱 한 번, 초기화 시점에만** 실행된다. `remember`가 걸려 있기 때문이다.

  ```kotlin
  val cameraPositionState = rememberCameraPositionState {
      position = CameraPosition.fromLatLngZoom(userLocation.value, 10f)
  }
  ```

  **`userLocation.value`가 나중에 바뀌어도 이 블록은 다시 실행되지 않는다.** 지도를 탭해서 마커를 옮겨도 카메라는 처음 위치에 그대로 있다. 이게 의도라면 맞고(사용자가 지도를 움직이는 걸 방해하지 않음), 카메라도 따라가길 원했다면 별도 처리가 필요하다.

- **`CameraPositionState`는 "설정값"이 아니라 "살아 있는 상태"다.** 이 구분이 API 이해의 열쇠다.

  ```
  내가 씀:   position = ...          → 카메라를 옮긴다
  지도가 씀:  사용자가 드래그/줌       → position 이 갱신된다
  ```

  **양방향이다.** 그래서 "상태(state)"라는 이름이 붙었고, `remember`로 유지해야 한다.

## 공부할 내용

### 무엇을 위한 API인가

지도에는 "카메라"라는 개념이 있다. **지도를 내려다보는 가상의 시점**이다.

```kotlin
CameraPosition(
    target = LatLng(37.5, 127.0),   // 어디를 보는가
    zoom = 10f,                      // 얼마나 가까이
    tilt = 0f,                       // 기울기 (0~90도)
    bearing = 0f                     // 회전 (북쪽 기준 각도)
)
```

`CameraPositionState`는 이 카메라를 **Compose 상태로 다루기 위한 홀더**다. 하는 일이 셋이다.

| 역할 | 예 |
| --- | --- |
| **카메라 제어** | 코드로 특정 위치·줌으로 이동 |
| **카메라 관찰** | 사용자가 움직인 뒤의 현재 위치 읽기 |
| **이동 중 여부** | 애니메이션 진행 중인지 |

### `CameraPosition.fromLatLngZoom`

전체 생성자를 다 쓰지 않고 **좌표와 줌만 주는 축약 팩토리**다.

```kotlin
CameraPosition.fromLatLngZoom(latLng, 10f)
// == CameraPosition(latLng, 10f, 0f, 0f)
```

줌 레벨의 대략적인 감각은 이렇다.

| zoom | 보이는 범위 |
| --- | --- |
| 1 | 세계 |
| 5 | 대륙 |
| 10 | 도시 |
| 15 | 거리 |
| 20 | 건물 |

지금 코드의 `10f`는 **도시 수준**이다. "내 위치 고르기" 화면이라면 조금 더 확대하는 편이 자연스럽다.

### 카메라를 움직이는 방법

`position`에 직접 대입할 수도 있지만, **애니메이션이 없어 뚝 끊긴다.**

```kotlin
// 즉시 이동 (애니메이션 없음)
cameraPositionState.position = CameraPosition.fromLatLngZoom(newLatLng, 15f)

// 부드럽게 이동 (suspend 함수)
scope.launch {
    cameraPositionState.animate(
        update = CameraUpdateFactory.newLatLngZoom(newLatLng, 15f),
        durationMs = 500
    )
}
```

`animate()`는 `suspend` 함수라 **코루틴 스코프가 필요하다.**

```kotlin
LaunchedEffect(userLocation.value) {
    cameraPositionState.animate(
        CameraUpdateFactory.newLatLng(userLocation.value)
    )
}
```

이렇게 하면 **마커가 옮겨질 때 카메라도 따라간다.** 지금 코드에 없는 동작이고, 필요한지는 UX 판단이다. 사용자가 지도를 스크롤해서 다른 곳을 보고 있는데 카메라가 멋대로 돌아오면 오히려 불편하다.

→ [`059-compose-launchedeffect-and-snapshotflow.md`](059-compose-launchedeffect-and-snapshotflow.md)

### 카메라를 관찰하는 방법

```kotlin
// 현재 카메라 위치
val center = cameraPositionState.position.target
val currentZoom = cameraPositionState.position.zoom

// 사용자가 움직이는 중인가
if (cameraPositionState.isMoving) { ... }

// 왜 움직이는가
when (cameraPositionState.cameraMoveStartedReason) {
    CameraMoveStartedReason.GESTURE -> { /* 사용자가 드래그 */ }
    CameraMoveStartedReason.DEVELOPER_ANIMATION -> { /* 코드가 animate */ }
    CameraMoveStartedReason.API_ANIMATION -> { /* SDK 내부 */ }
    else -> {}
}
```

**`cameraMoveStartedReason`이 실무에서 유용하다.** "사용자가 직접 움직였을 때만 자동 추적을 끈다" 같은 동작을 만들 수 있다.

카메라가 멈췄을 때 주변 장소를 검색하는 패턴도 흔하다.

```kotlin
LaunchedEffect(cameraPositionState.isMoving) {
    if (!cameraPositionState.isMoving) {
        viewModel.searchNearby(cameraPositionState.position.target)
    }
}
```

**`snapshotFlow` + `debounce`로 만들면 더 낫다.** 움직임이 멈춘 뒤 일정 시간이 지나야 검색한다.

### 이 화면의 다른 설계 — 중앙 고정 핀

지금 코드는 **탭한 곳에 마커를 찍는** 방식이다.

```kotlin
onMapClick = { userLocation.value = it }
```

지도 앱에서 흔한 다른 방식은 **화면 중앙에 핀을 고정하고 지도를 움직이게** 하는 것이다.

```kotlin
Box {
    GoogleMap(cameraPositionState = cameraPositionState) { /* 마커 없음 */ }
    Icon(                                        // 화면 중앙에 고정
        Icons.Default.LocationOn, null,
        modifier = Modifier.align(Alignment.Center)
    )
}

// 확정할 때
val selected = cameraPositionState.position.target
```

**한 손으로 조작하기 쉽고, 마커가 손가락에 가려지지 않는다.** `CameraPositionState`를 "관찰" 용도로 쓰는 대표 사례다.

### `key` 파라미터

```kotlin
rememberCameraPositionState(key = "picker") { ... }
```

`key`가 바뀌면 **상태를 새로 만든다.** 여러 지도를 번갈아 보여 주거나, 완전히 다른 맥락으로 재사용할 때 쓴다. 대부분은 생략한다.

## 관련 아키텍처와 베스트 프랙티스

### 초기화 블록과 지속적 동기화를 구분한다

이 문서에서 가장 중요한 구분이다.

```kotlin
// 초기값 — 한 번만
rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(initial, 10f)
}

// 지속 동기화 — 값이 바뀔 때마다
LaunchedEffect(target) {
    cameraPositionState.animate(CameraUpdateFactory.newLatLng(target))
}
```

**`remember` 블록 안에 "계속 반영되길 바라는 로직"을 넣는 실수가 흔하다.** `MarkerState`에서도 똑같은 문제가 나온다.

→ [`080-compose-maps-marker-state-remember.md`](080-compose-maps-marker-state-remember.md)

### 카메라 상태를 ViewModel에 두지 않는다

```kotlin
// 피한다
class MapViewModel : ViewModel() {
    val cameraPositionState = CameraPositionState()   // UI 상태가 ViewModel 로
}
```

`CameraPositionState`는 **지도 컴포저블의 생명주기에 묶인 UI 상태**다. ViewModel에 두면 화면이 사라진 뒤에도 남아 누수가 된다.

**ViewModel에는 "어디를 봐야 하는가"라는 의미 있는 데이터만 둔다.**

```kotlin
class MapViewModel : ViewModel() {
    val selectedLocation: StateFlow<LatLng?>     // 도메인 데이터
}
```

→ [`047-android-viewmodel-state-exposure-patterns.md`](047-android-viewmodel-state-exposure-patterns.md)

### 지도는 비싼 컴포넌트다

`GoogleMap`은 내부적으로 `MapView`를 띄우는 무거운 컴포넌트다.

- **여러 개를 동시에 띄우지 않는다.** 목록의 각 항목마다 지도를 넣으면 급격히 느려진다. 정적 지도 이미지(Maps Static API)를 쓴다.
- **불필요한 재구성을 줄인다.** 지도를 감싼 컴포저블이 자주 재구성되면 비용이 크다.
- **API 키 제한을 건다.** → [`063-android-api-key-secure-management.md`](063-android-api-key-secure-management.md)

### 권한과 "내 위치" 버튼

```kotlin
GoogleMap(
    properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
    uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission)
)
```

`isMyLocationEnabled = true`인데 권한이 없으면 **`SecurityException`이 난다.** 권한 상태와 연동해야 한다.

→ [`076-android-location-permission-coarse-vs-fine.md`](076-android-location-permission-coarse-vs-fine.md)

## 체크리스트

- [ ] 블록 안의 `position`이 어디서 온 것인지 설명할 수 있다.
- [ ] 수신 객체 람다 문법을 안다.
- [ ] 초기화 블록이 한 번만 실행된다는 것을 안다.
- [ ] `CameraPositionState`가 양방향 상태임을 설명할 수 있다.
- [ ] `CameraPosition`의 네 가지 요소를 말할 수 있다.
- [ ] `fromLatLngZoom`이 축약 팩토리임을 안다.
- [ ] 줌 레벨의 대략적인 감각이 있다.
- [ ] `position` 대입과 `animate()`의 차이를 안다.
- [ ] `animate()`가 `suspend` 함수인 이유를 안다.
- [ ] `isMoving`, `cameraMoveStartedReason`의 용도를 안다.
- [ ] 초기화와 지속 동기화를 구분할 수 있다.
- [ ] 카메라 상태를 ViewModel에 두면 안 되는 이유를 안다.
- [ ] 중앙 고정 핀 방식의 장점을 설명할 수 있다.

## 공식 참고 자료

- [Google for Developers: Maps Compose Library](https://developers.google.com/maps/documentation/android-sdk/maps-compose)
- [Google for Developers: Camera and view (Maps SDK for Android)](https://developers.google.com/maps/documentation/android-sdk/views)
- [Google for Developers: `CameraPosition`](https://developers.google.com/android/reference/com/google/android/gms/maps/model/CameraPosition)
- [Google for Developers: `CameraUpdateFactory`](https://developers.google.com/android/reference/com/google/android/gms/maps/CameraUpdateFactory)
- [GitHub: googlemaps/android-maps-compose](https://github.com/googlemaps/android-maps-compose)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects)
- [Kotlin Docs: Type-safe builders](https://kotlinlang.org/docs/type-safe-builders.html)
