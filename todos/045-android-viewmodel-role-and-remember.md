# `ViewModel`의 역할과 `remember`가 필요 없는 이유

## 질문이 나온 코드

- [`chapter127/app/src/main/java/com/example/chapter_127/CounterViewModel.kt`](../chapter127/app/src/main/java/com/example/chapter_127/CounterViewModel.kt)
- 질문: `ViewModel`의 역할은 무엇인가? `ViewModel`은 `remember` 키워드를 안 써도 되는가?

## 질문 전제 점검

- **"`remember`를 안 써도 되는가?"** → 맞다. 그런데 "안 써도 된다"가 아니라 **"쓸 수 없고, 쓸 필요도 없다"**가 정확하다. `remember`는 컴포저블 함수 안에서만 호출할 수 있는 API이고 값을 컴포지션에 저장한다. `ViewModel`은 애초에 컴포지션 바깥에 살기 때문에 대상이 아니다.
- **"`remember` 키워드"** → 키워드가 아니라 함수다. 코틀린 언어 문법이 아니라 Compose 런타임이 제공하는 API다.
- 한 가지 더 짚으면, 둘은 대체 관계가 아니라 **수명이 다른 서로 다른 저장 장치**다. `remember`는 컴포지션이 살아 있는 동안, `ViewModel`은 `ViewModelStoreOwner`가 살아 있는 동안 값을 유지한다. 화면 회전에서 갈리는 것이 이 차이다.

## 공부할 내용

### `ViewModel`의 정의

공식 문서는 `ViewModel`을 **비즈니스 로직 또는 화면 수준 상태 홀더**로 정의한다. 하는 일은 세 가지다.

- UI에 상태를 노출하고 관련 비즈니스 로직을 캡슐화한다.
- 상태를 캐시하고 구성 변경에 걸쳐 유지한다.
- UI 레이어에서 비즈니스 로직에 접근하는 통로가 된다.

가장 큰 이점은 두 번째다. "구성 변경에 걸쳐 상태를 캐시하고 유지한다. 덕분에 액티비티 간 이동이나 화면 회전 같은 구성 변경 이후에 UI가 데이터를 다시 가져올 필요가 없다."

### 수명 비교

| 저장 위치 | 유지되는 범위 | 사라지는 시점 |
| --- | --- | --- |
| 지역 변수 | 없음 | 재구성 때마다 |
| `remember` | 컴포지션에 있는 동안 | 컴포저블이 컴포지션에서 제거될 때, **구성 변경 시** |
| `rememberSaveable` | 구성 변경, 프로세스 종료 | 사용자가 앱을 종료할 때 |
| `ViewModel` | `ViewModelStoreOwner` 수명 | 액티비티가 finish될 때, 내비게이션 항목이 백스택에서 제거될 때 |

`ViewModel`의 수명을 문서는 이렇게 설명한다. "`ViewModel`은 자신이 스코프된 `ViewModelStoreOwner`가 사라질 때까지 메모리에 남는다." 화면 회전은 액티비티를 재생성하지만 `ViewModelStoreOwner`를 없애지는 않으므로 `ViewModel`은 살아남는다.

### 그래서 `remember`가 등장하지 않는다

`chapter127`의 코드를 보자.

```kotlin
class CounterViewModel(private val repository: CounterRepository) : ViewModel() {
    private val _count = mutableStateOf(repository.getCounter().count)
    val count: MutableState<Int> = _count

    fun increment() { _count.value++ }
}
```

`mutableStateOf`는 있지만 `remember`는 없다. 이유는 명확하다.

- `remember`의 역할은 "재구성 사이에 값을 잃지 않게 컴포지션에 보관"하는 것이다.
- `ViewModel`의 프로퍼티는 `ViewModel` 인스턴스가 들고 있고, 그 인스턴스는 재구성과 무관하게 살아 있다. 보관할 필요가 없다.
- 반대로 `mutableStateOf`는 여전히 필요하다. 값이 바뀌었을 때 Compose에 알리는 역할은 `ViewModel`이 대신해 주지 않는다.

정리하면 두 축이다. **"유지"는 `ViewModel`이, "알림"은 `mutableStateOf`가** 담당한다. 컴포저블 안이라면 "유지"를 `remember`가 맡을 뿐이다.

### `viewModelScope`와 `onCleared`

`ViewModel`은 수명을 갖는 객체이므로 정리 지점도 제공한다.

- `onCleared()`는 "`ViewModelStoreOwner`가 생명주기 과정에서 `ViewModel`을 파괴할 때" 호출된다.
- `viewModelScope`는 "`ViewModel`의 생명주기를 자동으로 따르는 내장 `CoroutineScope`"다. 여기서 시작한 코루틴은 `ViewModel`이 정리될 때 함께 취소된다.

컴포저블에서 `remember`로 만든 객체에는 이런 장치가 없다. 오래 사는 작업을 다뤄야 한다면 `ViewModel`이 있어야 하는 이유다.

## 관련 아키텍처와 베스트 프랙티스

### 상태는 읽기 전용으로 노출한다

현재 코드는 내부 상태를 그대로 공개하고 있다.

```kotlin
private val _count = mutableStateOf(repository.getCounter().count)
val count: MutableState<Int> = _count   // UI가 값을 직접 바꿀 수 있다
```

`MutableState`로 노출하면 UI에서 `viewModel.count.value = 100`이 가능해진다. 상태 변경 경로가 여러 개가 되어 단방향 데이터 흐름이 깨진다. 권장 형태는 읽기 전용 타입으로 좁혀 노출하는 것이다.

```kotlin
private val _count = mutableStateOf(0)
val count: State<Int> = _count          // 읽기만 가능

// 또는 Flow 기반
private val _uiState = MutableStateFlow(CounterUiState())
val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()
```

변경은 `increment()`, `decrement()` 같은 공개 함수로만 일어나게 한다.

### `State`와 `StateFlow` 중 무엇을 쓸까

| | `mutableStateOf` | `MutableStateFlow` |
| --- | --- | --- |
| 소속 | Compose 런타임 | 코루틴 |
| 장점 | Compose와 직접 통합, 간결함 | 플랫폼 독립, 연산자 활용, 테스트 용이 |
| UI에서 읽기 | `state.value` | `collectAsStateWithLifecycle()` |

`ViewModel`이 Compose에 의존하지 않기를 바란다면 `StateFlow`가 낫다. 멀티플랫폼이나 뷰 시스템과의 공존을 고려할 때 특히 그렇다. Compose 전용 앱에서는 `mutableStateOf`도 충분히 허용된다.

### UI 상태는 하나의 불변 객체로

값이 여럿이 되면 개별 상태를 여러 개 두기보다 하나로 묶는 편이 읽기 쉽다.

```kotlin
data class CounterUiState(
    val count: Int = 0,
    val isLoading: Boolean = false,
)
```

### `ViewModel`이 들고 있으면 안 되는 것

문서가 명시적으로 경고한다. "`ViewModel`은 `ViewModelStoreOwner`보다 오래 살 수 있으므로 `Context`나 `Resources` 같은 생명주기 관련 API에 대한 참조를 들고 있으면 안 된다. 메모리 누수를 방지하기 위해서다."

액티비티 컨텍스트, `View`, `Activity` 참조는 넣지 않는다. 애플리케이션 컨텍스트가 필요하면 `AndroidViewModel`을 쓰거나 필요한 기능만 인터페이스로 주입받는다.

### 컴포저블에 `ViewModel`을 넘기지 않기

"다른 컴포저블에 `ViewModel` 인스턴스를 전달하지 않도록 하라. 그 컴포저블들을 테스트하기 어렵게 만들고 프리뷰를 깨뜨릴 수 있다. 대신 필요한 데이터와 함수만 파라미터로 전달하라."

```kotlin
// 화면 수준: ViewModel을 아는 곳
@Composable
fun CounterRoute(viewModel: CounterViewModel = viewModel()) {
    val count by viewModel.count
    CounterScreen(count = count, onIncrement = viewModel::increment)
}

// 표현 전용: 프리뷰와 테스트가 쉬운 컴포저블
@Composable
fun CounterScreen(count: Int, onIncrement: () -> Unit) { ... }
```

`ViewModel` 인스턴스를 어떻게 얻어야 하는지는 [`046-compose-viewmodel-function-vs-manual.md`](046-compose-viewmodel-function-vs-manual.md)에 정리했다.

## 체크리스트

- [ ] `ViewModel`이 담당하는 세 가지 역할을 말할 수 있다.
- [ ] `remember`와 `ViewModel`의 수명 차이를 설명할 수 있다.
- [ ] `ViewModel` 안에서 `remember`를 쓰지 않는 이유를 설명할 수 있다.
- [ ] `ViewModel` 안에서도 `mutableStateOf`는 필요한 이유를 설명할 수 있다.
- [ ] `onCleared`와 `viewModelScope`의 용도를 설명할 수 있다.
- [ ] 상태를 `MutableState`로 노출하면 안 되는 이유를 설명할 수 있다.
- [ ] `ViewModel`이 `Context`를 들고 있으면 안 되는 이유를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Android Developers: UI layer architecture](https://developer.android.com/topic/architecture/ui-layer)
- [Android Developers: State holders and UI state](https://developer.android.com/topic/architecture/ui-layer/stateholders)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
