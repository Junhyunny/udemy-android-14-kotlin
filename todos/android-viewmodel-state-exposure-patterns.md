# ViewModel에서 state를 따로 관리하는 이유 — 베스트 프랙티스인가

## 질문이 나온 코드

- [`chapter186/app/src/main/java/com/example/chapter_186/LocationViewModel.kt`](../chapter186/app/src/main/java/com/example/chapter_186/LocationViewModel.kt)
- 질문: 이렇게 viewModel에서 state를 따로 관리하는 이유가 뭐야? 이게 베스트 프랙티스야?

```kotlin
private val _location = mutableStateOf<LocationData?>(null)
val location: MutableState<LocationData?> = _location

private val _address = mutableStateOf(listOf<GeocodingResult>())
val address: MutableState<List<GeocodingResult>> = _address
```

## 질문 전제 점검

- **"따로 관리한다"는 것이 두 가지를 동시에 뜻해서 질문이 뭉쳐 있다.** 나눠서 봐야 한다.

  1. **왜 화면(`remember`)이 아니라 ViewModel에 두는가** → 맞는 결정이다
  2. **왜 `_location` / `location` 두 개로 쪼개는가** → 의도는 맞지만 **지금 코드는 그 의도를 달성하지 못했다**

- **2번이 핵심 정정이다. 백킹 프로퍼티 패턴이 깨져 있다.**

  ```kotlin
  private val _location = mutableStateOf<LocationData?>(null)
  val location: MutableState<LocationData?> = _location
  //            ^^^^^^^^^^^^ 타입이 MutableState 다
  ```

  `_`를 붙여 숨기는 목적은 **"바깥에서 값을 바꾸지 못하게"** 하는 것이다. 그런데 공개 프로퍼티의 타입이 `MutableState`라서 **화면에서 그냥 바꿀 수 있다.**

  ```kotlin
  viewModel.location.value = LocationData(0.0, 0.0)   // 컴파일된다
  ```

  실제로 `chapter246`에서는 이런 코드가 있었다.

  ```kotlin
  viewModel.currentScreen.value = item   // 화면이 ViewModel 상태를 직접 변경
  ```

  **타입을 `State`로 내리면 의도대로 동작한다.**

  ```kotlin
  private val _location = mutableStateOf<LocationData?>(null)
  val location: State<LocationData?> = _location      // 읽기 전용
  ```

  `State`는 `value`가 `val`이고, `MutableState`만 `var`다. 한 글자 차이로 캡슐화가 완성된다.

- **"이게 베스트 프랙티스야?"에 대한 답은 "절반은"이다.** ViewModel에 상태를 두는 것은 맞다. 다만 두 가지가 권장에서 벗어나 있다.

  | | 지금 | 권장 |
  | --- | --- | --- |
  | 노출 타입 | `MutableState` | `State` 또는 `StateFlow` |
  | 상태 구조 | 필드 두 개가 따로 논다 | 화면 상태를 **하나의 UI State**로 |

## 공부할 내용

### 왜 ViewModel에 두는가 — `remember`와의 차이

```kotlin
// 화면에 두면
@Composable
fun Screen() {
    var location by remember { mutableStateOf<LocationData?>(null) }
    // 화면 회전 → 날아간다
    // 다른 화면으로 이동 후 복귀 → 날아간다
}

// ViewModel 에 두면
class LocationViewModel : ViewModel() {
    private val _location = mutableStateOf<LocationData?>(null)
    // 구성 변경을 견딘다
}
```

**`ViewModel`은 구성 변경(화면 회전, 다크 모드 전환, 언어 변경)에도 살아남는다.** 액티비티가 파괴되고 다시 만들어져도 같은 인스턴스가 붙는다.

이 프로젝트에서는 이유가 하나 더 있다. **두 화면이 같은 상태를 공유**한다.

```kotlin
composable("shoppinglistscreen") { ShoppingListApp(viewModel = viewModel, ...) }
dialog("locationscreen")         { LocationSelectionScreen(location = viewModel.location.value, ...) }
```

위치 선택 다이얼로그가 고른 값을 쇼핑 목록 화면이 읽는다. `remember`로는 **불가능하다.** 각 컴포저블의 `remember`는 서로 격리돼 있기 때문이다.

→ [`android-viewmodel-role-and-remember.md`](android-viewmodel-role-and-remember.md)

### 백킹 프로퍼티 패턴이 하려는 일

```
ViewModel 안:  _location (쓰기 가능) ← 여기서만 바꾼다
ViewModel 밖:   location (읽기 전용) ← 화면은 읽기만
```

**"상태를 바꾸는 경로를 하나로 만든다"**는 것이 목적이다. 화면이 아무 데서나 상태를 바꿀 수 있으면, 버그가 났을 때 **어디서 바뀌었는지 추적이 불가능해진다.**

```kotlin
// 단방향 데이터 흐름
화면 ──이벤트(함수 호출)──> ViewModel ──상태──> 화면
     updateLocation()              location
```

화면은 **"무슨 일이 일어났다"만 알리고**, 상태를 어떻게 바꿀지는 ViewModel이 정한다.

### `State`와 `StateFlow` 중 무엇을 쓰나

Compose에서 상태를 노출하는 방법은 둘이다.

```kotlin
// 방법 1: Compose State
private val _location = mutableStateOf<LocationData?>(null)
val location: State<LocationData?> = _location
// 화면에서: val loc by viewModel.location

// 방법 2: StateFlow
private val _location = MutableStateFlow<LocationData?>(null)
val location: StateFlow<LocationData?> = _location.asStateFlow()
// 화면에서: val loc by viewModel.location.collectAsStateWithLifecycle()
```

| 기준 | `State` | `StateFlow` |
| --- | --- | --- |
| 의존성 | ViewModel이 **Compose를 import** 한다 | 순수 코틀린 |
| 테스트 | Compose 규칙이 필요할 수 있다 | 일반 코루틴 테스트 |
| 연산자 | 없다 | `map`, `combine`, `debounce`, `flatMapLatest` |
| 다른 UI 툴킷 | 못 쓴다 | View에서도 쓴다 |
| 라이프사이클 인식 수집 | 자동 | `collectAsStateWithLifecycle()` 필요 |

**`StateFlow`가 일반적인 권장이다.** 가장 큰 이유는 **ViewModel이 UI 프레임워크를 몰라야 한다**는 것이다. 지금 코드의 ViewModel은 이런 import를 갖고 있다.

```kotlin
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
```

**ViewModel이 Compose에 묶여 있다는 신호다.**

다만 `State`가 틀린 것은 아니다. Compose 전용 앱에서 단순한 상태라면 충분하고, 공식 문서도 두 방식을 모두 소개한다. **강의 단계에서는 이대로 두고, 나중에 `Flow` 연산이 필요해지면 옮기는 것이 현실적이다.**

→ [`kotlin-flow-concepts-and-suspend.md`](kotlin-flow-concepts-and-suspend.md), [`compose-collectasstate-flow-to-state.md`](compose-collectasstate-flow-to-state.md)

### 상태를 하나로 묶기

지금은 필드가 따로 논다.

```kotlin
val location: State<LocationData?>
val address: State<List<GeocodingResult>>
```

화면에서 쓸 때 문제가 드러난다.

```kotlin
address = viewModel.address.value.firstOrNull()?.formatted_address ?: "No Address"
```

**"주소를 불러오는 중"과 "주소가 없음"이 구분되지 않는다.** 둘 다 빈 리스트다. 실패했을 때도 마찬가지다.

```kotlin
data class LocationUiState(
    val location: LocationData? = null,
    val address: String? = null,
    val isLoadingAddress: Boolean = false,
    val errorMessage: String? = null,
)

class LocationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()
}
```

**화면이 필요한 모든 것이 한 객체에 있다.** 상태 조합이 하나라서 "로딩 중인데 에러도 있는" 모순이 생기지 않는다.

더 엄격하게 가면 `sealed interface`로 불가능한 조합 자체를 없앤다.

→ [`kotlin-sealed-class-and-interface.md`](kotlin-sealed-class-and-interface.md), [`android-viewmodel-encapsulation-and-async-timing.md`](android-viewmodel-encapsulation-and-async-timing.md)

### 이 ViewModel을 정리한다면

```kotlin
class LocationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    fun updateLocation(newLocation: LocationData) {
        _uiState.update { it.copy(location = newLocation) }
    }

    fun fetchAddress(latLng: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAddress = true, errorMessage = null) }
            try {
                val result = RetrofitClient.service.getAddressFromCoordinate(latLng, BuildConfig.MAPS_API_KEY)
                _uiState.update {
                    it.copy(
                        address = result.results.firstOrNull()?.formatted_address,
                        isLoadingAddress = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("fetchAddress", "주소 조회 실패: $latLng", e)
                _uiState.update { it.copy(isLoadingAddress = false, errorMessage = "주소를 불러오지 못했습니다") }
            }
        }
    }
}
```

`update { }`를 쓰는 이유는 **읽고-고치고-쓰기를 원자적으로** 하기 위해서다. `_uiState.value = _uiState.value.copy(...)`는 동시 갱신에서 값을 잃을 수 있다.

## 관련 아키텍처와 베스트 프랙티스

### 노출 타입을 좁힌다

| 내부 | 외부 |
| --- | --- |
| `MutableState<T>` | `State<T>` |
| `MutableStateFlow<T>` | `StateFlow<T>` (`asStateFlow()`) |
| `MutableLiveData<T>` | `LiveData<T>` |
| `mutableListOf<T>()` | `List<T>` |

**"바깥에서 바꿀 수 있는가"를 타입으로 막는다.** 규칙이나 리뷰가 아니라 컴파일러가 강제하게 만드는 것이 핵심이다.

### `var` + `private set`도 선택지다

```kotlin
class LocationViewModel : ViewModel() {
    var location by mutableStateOf<LocationData?>(null)
        private set

    fun updateLocation(newLocation: LocationData) { location = newLocation }
}
```

백킹 프로퍼티 없이 같은 효과를 낸다. **필드가 절반으로 줄어든다.** Compose State를 쓸 때 깔끔한 형태다.

→ [`compose-remember-mutablestate-and-by.md`](compose-remember-mutablestate-and-by.md)

### ViewModel은 안드로이드/UI 타입을 들고 있지 않는다

```kotlin
// 피한다
class LocationViewModel : ViewModel() {
    lateinit var context: Context        // 메모리 누수
    var navController: NavController     // UI 결합
}
```

`ViewModel`은 액티비티보다 오래 산다. **액티비티에 묶인 것을 들고 있으면 누수가 된다.** `Context`가 필요하면 `AndroidViewModel`의 `Application`을 쓴다.

→ [`android-context-types-and-application-context.md`](android-context-types-and-application-context.md)

같은 이유로 `LocationUtils`가 ViewModel을 파라미터로 받는 지금 구조도 방향이 거꾸로다.

```kotlin
// 현재: 하위 유틸이 상위 ViewModel 을 안다
fun requestLocationUpdate(viewModel: LocationViewModel)

// 권장: 결과를 흘려보낸다
fun locationUpdates(): Flow<LocationData>
```

→ [`android-fused-location-provider-and-looper.md`](android-fused-location-provider-and-looper.md)

### 화면은 상태를 읽고 이벤트만 올린다

```kotlin
@Composable
fun LocationScreen(
    uiState: LocationUiState,              // 읽기
    onRequestLocation: () -> Unit,         // 이벤트
    onAddressSelected: (LocationData) -> Unit,
)
```

**ViewModel을 통째로 넘기는 대신 상태와 콜백을 넘기면** 미리보기와 테스트가 쉬워진다. 지금 코드는 `ShoppingListApp(viewModel = viewModel, ...)`처럼 통째로 넘기고 있다.

→ [`compose-navigation-prop-drilling.md`](compose-navigation-prop-drilling.md)

### 상태를 어디에 둘지 판단하는 기준

| 상태 | 위치 |
| --- | --- |
| 텍스트 필드 포커스, 스크롤 위치, 펼침 여부 | `remember` — 화면 안 |
| 다이얼로그 표시 여부 (일시적) | `remember` / `rememberSaveable` |
| 화면 간 공유, 네트워크 결과, 비즈니스 데이터 | **ViewModel** |
| 앱 전역 설정, 로그인 상태 | Repository / DataStore |

**"화면이 사라지면 같이 사라져도 되는가"**가 기준이다.

## 체크리스트

- [ ] ViewModel에 상태를 두는 이유 두 가지를 말할 수 있다.
- [ ] 백킹 프로퍼티 패턴의 목적을 설명할 수 있다.
- [ ] 지금 코드에서 그 패턴이 깨진 이유를 지적할 수 있다.
- [ ] `State`와 `MutableState`의 차이를 안다.
- [ ] 단방향 데이터 흐름을 설명할 수 있다.
- [ ] `State`와 `StateFlow`의 장단점을 비교할 수 있다.
- [ ] ViewModel이 Compose를 import 하는 것이 왜 신호인지 안다.
- [ ] `collectAsStateWithLifecycle()`이 필요한 이유를 안다.
- [ ] 상태를 하나의 UI State로 묶는 이점을 설명할 수 있다.
- [ ] "로딩 중"과 "결과 없음"이 구분되지 않는 문제를 안다.
- [ ] `update { }`를 쓰는 이유를 안다.
- [ ] `var` + `private set` 방식을 안다.
- [ ] ViewModel이 `Context`나 `NavController`를 들면 안 되는 이유를 안다.
- [ ] 상태를 `remember`에 둘지 ViewModel에 둘지 판단할 수 있다.

## 공식 참고 자료

- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: State holders and UI State](https://developer.android.com/topic/architecture/ui-layer/stateholders)
- [Android Developers: UI layer](https://developer.android.com/topic/architecture/ui-layer)
- [Android Developers: ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Android Developers: State hoisting](https://developer.android.com/develop/ui/compose/state-hoisting)
- [Android Developers: Guide to app architecture](https://developer.android.com/topic/architecture)
- [Android Developers: Architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Kotlin Docs: `StateFlow` and `SharedFlow`](https://kotlinlang.org/docs/flow.html)
- [Kotlin Docs: Properties (backing properties)](https://kotlinlang.org/docs/properties.html)
