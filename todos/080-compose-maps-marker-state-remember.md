# `MarkerState`는 왜 `remember` 해서 써야 하나

## 질문이 나온 코드

- [`chapter186/app/src/main/java/com/example/chapter_186/LocationSelectionScreen.kt`](../chapter186/app/src/main/java/com/example/chapter_186/LocationSelectionScreen.kt)
- 질문: 예전에는 `MarkerState`를 직접 만들었는데 이제는 `remember` 해서 사용해야 되나 봐. 언제 이렇게 사용하도록 업데이트 되었는지 확인해주고, `rememberUpdatedMarkerState` 내부에선 어떤 동작이 일어나는지 알려줘.

```kotlin
// val markerState = rememberUpdatedMarkerState(position = userLocation.value)

GoogleMap(...) {
    Marker(state = MarkerState(position = userLocation.value))   // 현재 코드
}
```

## 질문 전제 점검

- **"예전에는 직접 만들었다"는 인식이 정확하지 않다.** `MarkerState`는 **처음부터 `remember` 해서 쓰는 것이 맞았다.** 바뀐 것은 "직접 생성 → remember"가 아니라 **`rememberMarkerState` → `rememberUpdatedMarkerState`**다.

  ```
  ① MarkerState(position = ...)          ← 항상 잘못된 사용법이었다
  ② rememberMarkerState(position = ...)  ← 예전 권장. 지금은 deprecated
  ③ rememberUpdatedMarkerState(position = ...)  ← 현재 권장
  ```

  지금 코드는 **①을 쓰고 주석에 ③이 적혀 있는** 상태다. 주석을 살리는 것이 맞다.

- **①이 왜 문제인가** — 컴포저블 본문에서 객체를 새로 만들면 **재구성될 때마다 새 객체가 생긴다.**

  ```kotlin
  Marker(state = MarkerState(position = userLocation.value))
  //             ^^^^^^^^^^^ 재구성마다 새 인스턴스
  ```

  `MarkerState`는 단순한 데이터 홀더가 아니라 **드래그 위치, 정보창 열림 여부 같은 상태를 들고 있다.** 새로 만들어지면 그 상태가 전부 초기화된다. 지도를 탭할 때마다 `userLocation`이 바뀌고, 재구성이 일어나고, 마커 상태가 리셋된다.

  → [`034-compose-remember-mutablestate-and-by.md`](034-compose-remember-mutablestate-and-by.md)

- **②가 deprecated된 이유는 "이름이 오해를 준다"는 것이다.** 라이브러리의 deprecation 메시지가 직설적이다.

  > "Use `rememberUpdatedMarkerState` instead - It may be confusing to think that the state is automatically updated as the position changes."

  `rememberMarkerState(position = x)`라고 쓰면 **`x`가 바뀔 때 마커도 따라 움직일 것 같다.** 그런데 `remember`의 의미상 **처음 값만 기억하고 이후 변경은 무시한다.** 이름이 동작을 배신했다.

- **언제 바뀌었나** — `rememberSaveable`의 커스텀 `key` 파라미터가 Compose 1.9에서 deprecated되면서, 그것에 의존하던 `rememberMarkerState`도 함께 정리됐다. 즉 **maps-compose 자체의 판단이라기보다 Compose 런타임 변화에 맞춘 대응**이다.

  이 프로젝트는 `maps-compose:2.15.0`을 쓰고 있어서 **`rememberUpdatedMarkerState`가 없을 수 있다.** 주석이 풀려 있지 않은 이유가 이것일 가능성이 높다. 버전을 올려야 쓸 수 있다.

## 공부할 내용

### `rememberUpdatedMarkerState`가 하는 일

이름이 `rememberUpdatedState`에서 왔다. 두 가지를 합친 것이다.

```kotlin
// 개념적으로 이런 동작이다
@Composable
fun rememberUpdatedMarkerState(position: LatLng): MarkerState {
    val state = remember { MarkerState(position) }   // ① 인스턴스는 한 번만 만든다
    state.position = position                        // ② 위치는 매 재구성마다 갱신한다
    return state
}
```

| | 효과 |
| --- | --- |
| ① `remember` | 재구성돼도 **같은 객체**를 쓴다. 드래그 상태, 정보창 상태가 유지된다 |
| ② 위치 갱신 | 인자가 바뀌면 **마커가 실제로 움직인다** |

`rememberMarkerState`에는 ②가 없었다. 그래서 위치를 바꾸려면 직접 `markerState.position = ...`을 해야 했고, 그걸 모르면 "마커가 안 움직인다"는 문제를 겪었다.

**`rememberUpdatedState`와 같은 관용구다.**

```kotlin
// Compose 표준 API
val currentOnClick by rememberUpdatedState(onClick)
```

"객체의 정체성은 유지하되 내용은 최신으로 유지한다"는 패턴이다.

→ [`059-compose-launchedeffect-and-snapshotflow.md`](059-compose-launchedeffect-and-snapshotflow.md)

### 이 코드를 고친다면

**버전을 올릴 수 있다면:**

```kotlin
val markerState = rememberUpdatedMarkerState(position = userLocation.value)

GoogleMap(
    cameraPositionState = cameraPositionState,
    onMapClick = { userLocation.value = it }
) {
    Marker(state = markerState)
}
```

**현재 버전(2.15.0)을 유지한다면:**

```kotlin
val markerState = remember { MarkerState(position = userLocation.value) }
LaunchedEffect(userLocation.value) {
    markerState.position = userLocation.value     // 위치가 바뀌면 갱신
}

GoogleMap(...) {
    Marker(state = markerState)
}
```

**둘 다 "인스턴스는 유지, 위치는 갱신"이라는 같은 일을 한다.** 후자가 `rememberUpdatedMarkerState` 내부에서 벌어지는 일을 그대로 풀어 쓴 형태다.

### `MarkerState`가 들고 있는 것

단순한 좌표 저장소가 아니다.

```kotlin
markerState.position           // 좌표
markerState.dragState          // 드래그 중인가 (START / DRAG / END)
markerState.showInfoWindow()   // 정보창 열기
markerState.hideInfoWindow()
```

**드래그 가능한 마커에서 차이가 극명해진다.** 매 재구성마다 새로 만들면 사용자가 마커를 끄는 도중에 상태가 초기화된다.

```kotlin
Marker(
    state = markerState,
    draggable = true,
    onClick = { markerState.showInfoWindow(); true }
)
```

### `userLocation`을 `remember`로 감싼 것은 맞다

지금 코드의 이 부분은 올바르다.

```kotlin
val userLocation = remember {
    mutableStateOf(LatLng(location.latitude, location.longitude))
}
```

`onMapClick`에서 값을 바꾸므로 **재구성을 넘어 유지돼야 한다.** `remember`가 없으면 탭할 때마다 초기값으로 되돌아간다.

다만 두 가지 개선점이 있다.

```kotlin
// ① by 위임으로 .value 를 줄인다
var userLocation by remember { mutableStateOf(LatLng(location.latitude, location.longitude)) }

// ② 화면 회전에도 살아남으려면
var userLocation by rememberSaveable(stateSaver = ...) { ... }
```

②는 `LatLng`이 `Parcelable`이라 `rememberSaveable`로 저장할 수 있다. 사용자가 위치를 고르다 화면을 돌리면 선택이 날아가는 것을 막는다.

### `newLocation` 변수는 불필요하다

```kotlin
var newLocation: LocationData          // 초기화 없는 지역 변수
...
Button(onClick = {
    val userLocationData = userLocation.value
    newLocation = LocationData(userLocationData.latitude, userLocationData.longitude)
    onLocationSelected(newLocation)
})
```

`newLocation`은 **람다 안에서만 쓰이는데 바깥에 선언돼 있다.** 컴포저블 본문의 지역 변수는 재구성마다 새로 만들어지므로 값을 담아 둘 수도 없다. 람다 안으로 넣는 것이 맞다.

```kotlin
Button(onClick = {
    onLocationSelected(LocationData(userLocation.value.latitude, userLocation.value.longitude))
})
```

## 관련 아키텍처와 베스트 프랙티스

### 컴포저블 본문에서 객체를 새로 만들지 않는다

이 문서의 핵심 원칙이다.

```kotlin
// 나쁨: 재구성마다 새 인스턴스
Marker(state = MarkerState(position = x))
val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
val client = OkHttpClient()

// 좋음
val markerState = rememberUpdatedMarkerState(position = x)
val formatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA) }
```

**판단 기준: "이 객체가 상태를 들고 있거나, 만드는 비용이 있는가?"** 둘 중 하나라도 해당하면 `remember`로 감싼다.

`Modifier`나 `LatLng` 같은 **가벼운 불변 값**은 감쌀 필요가 없다.

### `remember`, `rememberUpdated*`, `rememberSaveable` 구분

| API | 유지 범위 | 인자 변경 시 |
| --- | --- | --- |
| `remember { }` | 재구성 | **무시한다** |
| `remember(key) { }` | 재구성 (key 같을 때) | key가 바뀌면 **다시 만든다** |
| `rememberUpdated*` | 재구성 | **같은 객체를 갱신한다** |
| `rememberSaveable { }` | 구성 변경·프로세스 종료 | 저장/복원 |

**`remember(key)`와 `rememberUpdated*`의 차이가 핵심이다.** 전자는 객체를 버리고 새로 만들고, 후자는 객체를 유지한 채 내용만 바꾼다. 드래그 상태처럼 **잃으면 안 되는 것**이 있으면 후자다.

### 라이브러리 deprecation 메시지를 읽는다

이번 사례처럼 **deprecation 메시지에 이유와 대안이 같이 적혀 있다.**

```
Use 'rememberUpdatedMarkerState' instead
- It may be confusing to think that the state is automatically updated as the position changes.
```

IDE에서 취소선이 그어진 API를 만나면 **툴팁이나 `ReplaceWith`를 먼저 읽는다.** 검색보다 빠르고 정확하다.

### 버전을 확인하고 쓴다

주석 처리된 API가 컴파일되지 않는다면 **버전이 낮은 것**일 수 있다.

```kotlin
// libs.versions.toml 로 옮기고 버전을 올린다
implementation("com.google.maps.android:maps-compose:6.x.x")
```

이 프로젝트는 maps-compose와 play-services-maps를 **문자열로 직접** 선언하고 있다. 저장소의 다른 챕터처럼 버전 카탈로그로 옮기면 버전 관리가 쉬워진다.

→ [`007-gradle-version-catalog-and-build-files.md`](007-gradle-version-catalog-and-build-files.md)

## 체크리스트

- [ ] `MarkerState`를 본문에서 직접 만들면 안 되는 이유를 설명할 수 있다.
- [ ] 바뀐 것이 "직접 생성 → remember"가 아님을 안다.
- [ ] `rememberMarkerState`가 deprecated된 이유를 말할 수 있다.
- [ ] `rememberUpdatedMarkerState`가 하는 두 가지 일을 안다.
- [ ] `rememberUpdatedState`와 같은 관용구임을 안다.
- [ ] `remember(key)`와 `rememberUpdated*`의 차이를 설명할 수 있다.
- [ ] `MarkerState`가 드래그·정보창 상태를 들고 있음을 안다.
- [ ] 현재 버전에서 같은 효과를 내는 대안을 쓸 수 있다.
- [ ] 컴포저블 본문에서 객체 생성을 피해야 할 기준을 안다.
- [ ] `rememberSaveable`이 필요한 상황을 판단할 수 있다.
- [ ] deprecation 메시지에서 대안을 찾는 습관이 있다.

## 공식 참고 자료

- [Google for Developers: Maps Compose Library](https://developers.google.com/maps/documentation/android-sdk/maps-compose)
- [GitHub: googlemaps/android-maps-compose](https://github.com/googlemaps/android-maps-compose)
- [GitHub: android-maps-compose `Marker.kt`](https://github.com/googlemaps/android-maps-compose/blob/main/maps-compose/src/main/java/com/google/maps/android/compose/Marker.kt)
- [GitHub: android-maps-compose CHANGELOG](https://github.com/googlemaps/android-maps-compose/blob/main/CHANGELOG.md)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: Side-effects in Compose (`rememberUpdatedState`)](https://developer.android.com/develop/ui/compose/side-effects)
- [Android Developers: Lifecycle of composables](https://developer.android.com/develop/ui/compose/lifecycle)
- [Android Developers: Compose performance](https://developer.android.com/develop/ui/compose/performance)
