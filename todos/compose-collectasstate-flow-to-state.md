# `collectAsState`로 `Flow`를 받으면 상태처럼 관리되는가

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/HomeView.kt`](../chapter205/app/src/main/java/com/example/chapter_205/HomeView.kt)
- 질문: `Flow` 구현체를 `collectAsState`로 접근하는 경우 state처럼 관리되는 건가? 그리고 비동기적으로 응답이 끝나면 컬렉션으로 만드는 건가?

```kotlin
val wishList = viewModel.getAllWishes.collectAsState(initial = listOf())
LazyColumn { items(items = wishList.value, key = { it.id }) { wish -> ... } }
```

## 질문 전제 점검

- **"state처럼 관리되는 건가"** → **"처럼"이 아니라 진짜 `State`다.** 반환 타입이 `State<T>`이고, Compose 스냅샷 시스템에 등록된 완전한 상태다. 값이 바뀌면 이 값을 읽은 컴포저블이 재구성된다. `mutableStateOf`로 만든 것과 동작이 같다.

  ```kotlin
  fun <T> Flow<T>.collectAsState(initial: T, ...): State<T>
  //                                          ^^^^^^^^  진짜 State
  ```

  하는 일은 단순하다. **`Flow`를 구독해서, 값이 올 때마다 내부 `MutableState`에 대입한다.**

  ```kotlin
  // 개념적으로
  @Composable
  fun <T> Flow<T>.collectAsState(initial: T): State<T> {
      val state = remember { mutableStateOf(initial) }
      LaunchedEffect(this) {
          collect { state.value = it }      // 값이 올 때마다 State 에 넣는다
      }
      return state
  }
  ```

  즉 **`Flow` 세계와 Compose 세계를 잇는 어댑터**다.

- **"비동기적으로 응답이 끝나면 컬렉션으로 만드는 건가"** → 이 전제가 어긋나 있다. **"응답이 끝나는" 시점이라는 것이 없다.**

  `Flow`는 한 번 값을 주고 끝나는 것이 아니라 **계속 흐른다.** Room의 `Flow`는 테이블이 바뀔 때마다 새 리스트를 내보낸다.

  ```
  구독 시작  → [] (initial)
            → [A, B, C]        첫 쿼리 결과
  항목 삭제  → [A, B]           테이블 변경으로 재조회
  항목 추가  → [A, B, D]
  ...        화면이 살아 있는 동안 계속
  ```

  `initial = listOf()`를 주는 이유가 여기 있다. **첫 값이 도착하기 전에도 그릴 것이 필요하기 때문이다.** 그래서 앱을 켜면 아주 잠깐 빈 목록이 보인다.

- **그리고 이 코드에는 실제 문제가 둘 있다.**
  1. **`collectAsState`는 생명주기를 모른다.** 앱이 백그라운드로 가도 구독이 계속된다. 안드로이드에서는 `collectAsStateWithLifecycle()`이 권장된다.
  2. **`collectAsState`가 `Scaffold`의 content 람다 안에 있다.** 재구성 범위 안에서 호출되는 것 자체는 문제없지만, `viewModel.getAllWishes`가 `lateinit var`라 초기화 순서 문제가 잠재해 있다. → [`kotlin-flow-concepts-and-suspend.md`](kotlin-flow-concepts-and-suspend.md)

## 공부할 내용

### `Flow`와 `State`는 무엇이 다른가

둘 다 "값이 변한다"를 다루지만 소속이 다르다.

| | `Flow<T>` | `State<T>` |
| --- | --- | --- |
| 소속 | 코루틴 | Compose 런타임 |
| 읽는 법 | `collect` (suspend) | `.value` (즉시) |
| 현재 값 | 없다(콜드 기준) | **항상 있다** |
| 값이 바뀌면 | collector가 받는다 | **읽은 컴포저블이 재구성된다** |
| 쓰는 곳 | 데이터·도메인 계층 | UI 계층 |

**컴포저블은 `Flow`를 직접 읽을 수 없다.** 컴포저블 본문은 `suspend` 함수가 아니기 때문이다. 그래서 변환이 필요하다.

```
Room  →  Flow  →  [collectAsState]  →  State  →  컴포저블 재구성
```

### 재구성으로 이어지는 경로

```kotlin
val wishList = viewModel.getAllWishes.collectAsState(initial = listOf())
// ...
items(items = wishList.value, ...)     // ← 여기서 State 를 "읽는다"
```

**읽는 행위가 구독이다.** Compose는 컴포지션 중에 어떤 컴포저블이 어떤 상태를 읽었는지 기록해 두고, 그 상태가 바뀌면 그 컴포저블만 다시 실행한다. → [`compose-recomposition-timing-and-scope.md`](compose-recomposition-timing-and-scope.md)

`by` 위임을 쓰면 `.value`를 생략할 수 있다.

```kotlin
import androidx.compose.runtime.getValue

val wishList by viewModel.getAllWishes.collectAsState(initial = listOf())
items(items = wishList, ...)           // .value 없이
```

→ [`compose-remember-mutablestate-and-by.md`](compose-remember-mutablestate-and-by.md)

### `collectAsState` vs `collectAsStateWithLifecycle`

**안드로이드에서는 후자가 권장된다.**

```kotlin
// androidx.lifecycle:lifecycle-runtime-compose
val wishList by viewModel.getAllWishes.collectAsStateWithLifecycle(initialValue = emptyList())
```

> 기본적으로 lifecycle이 `STARTED`일 때 수집을 시작하고, `STOPPED`일 때 중지합니다.

차이가 실제로 드러나는 상황이다.

| | `collectAsState` | `collectAsStateWithLifecycle` |
| --- | --- | --- |
| 앱이 백그라운드로 갈 때 | **계속 수집** | 수집 중단 |
| 화면이 안 보이는데 데이터가 오면 | 상태를 갱신한다 | 갱신하지 않는다 |
| 배터리·네트워크 | 낭비 가능 | 절약 |

Room 쿼리 정도면 비용이 크지 않지만, **네트워크나 위치 정보라면 차이가 크다.** 습관적으로 `WithLifecycle` 쪽을 쓰는 편이 안전하다.

### `initial` 값이 필요한 이유

```kotlin
collectAsState(initial = listOf())
```

컴포저블은 **첫 컴포지션에서 즉시 그릴 값이 필요하다.** `Flow`는 아직 아무 값도 주지 않았으므로 자리를 채울 값을 요구한다.

`StateFlow`는 이미 현재 값을 갖고 있으므로 `initial`이 필요 없다.

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()   // StateFlow 라면 initial 불필요
```

**이것이 `ViewModel`에서 `stateIn`으로 `StateFlow`를 노출하는 실용적인 이유 중 하나다.**

### 매번 새로 구독되지 않는가

```kotlin
val wishList = viewModel.getAllWishes.collectAsState(initial = listOf())
```

재구성될 때마다 이 줄이 다시 실행되는데, 그때마다 쿼리가 다시 돌까? **아니다.** 내부의 `LaunchedEffect(this)`가 **`Flow` 인스턴스를 키로 쓰기 때문**이다. 같은 `Flow` 객체면 구독이 유지된다.

**다만 매번 새 `Flow` 객체를 만들면 이야기가 달라진다.**

```kotlin
// 위험: 재구성마다 새 Flow → 재구독 → 쿼리 재실행
val wish = viewModel.getWishById(id).collectAsState(initial = Wish())

// 안전: Flow 를 remember 로 고정
val flow = remember(id) { viewModel.getWishById(id) }
val wish by flow.collectAsStateWithLifecycle(initialValue = Wish())
```

`AddEditDetailView`가 첫 번째 형태다. `getWishById(id)`가 호출될 때마다 새 `Flow`를 만들므로 재구성마다 재구독이 일어난다.

### `ViewModel`에서 `StateFlow`로 노출하기 — 권장 형태

```kotlin
class WishViewModel(private val repository: WishRepository) : ViewModel() {
    val wishes: StateFlow<List<Wish>> = repository.getAllWishes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
```

```kotlin
@Composable
fun HomeView(viewModel: WishViewModel) {
    val wishes by viewModel.wishes.collectAsStateWithLifecycle()
    // ...
}
```

얻는 것이 셋이다.

- **화면 회전을 견딘다** — `ViewModel`이 살아 있으므로 쿼리가 다시 돌지 않는다
- **여러 컴포저블이 구독해도 쿼리는 하나** — `StateFlow`가 공유한다
- **`initial`이 필요 없다** — `StateFlow`는 항상 값을 갖는다

`SharingStarted.WhileSubscribed(5_000)`은 **구독자가 모두 사라진 뒤 5초까지 기다렸다가 상류를 멈춘다.** 화면 회전처럼 잠깐 구독이 끊기는 상황에서 쿼리를 다시 돌리지 않기 위한 여유 시간이다.

### 관련 변환 함수들

```kotlin
flow.collectAsState(initial)                 // Flow → State
flow.collectAsStateWithLifecycle(initial)    // Flow → State (생명주기 인식)  ★ 권장
stateFlow.collectAsStateWithLifecycle()      // StateFlow → State (initial 불필요)
liveData.observeAsState()                    // LiveData → State
snapshotFlow { state.value }                 // State → Flow (반대 방향)
```

마지막 `snapshotFlow`는 반대 방향 변환이다. `HomeView`의 스와이프 삭제 코드가 쓰고 있다. → [`compose-launchedeffect-and-snapshotflow.md`](compose-launchedeffect-and-snapshotflow.md)

## 관련 아키텍처와 베스트 프랙티스

### 계층별 타입

```
Room / API        Flow<T>                      데이터가 흐른다
    ↓
Repository        Flow<T>                      그대로 또는 변환
    ↓
ViewModel         StateFlow<UiState>           stateIn 으로 고정
    ↓
Composable        State<UiState>               collectAsStateWithLifecycle
```

**각 계층이 자기 계층에 맞는 타입을 쓴다.** `ViewModel`이 콜드 `Flow`를 그대로 노출하면 구독자 수만큼 쿼리가 돈다.

### 컴포저블에서 `collect`를 직접 부르지 않는다

```kotlin
// 나쁨: 생명주기를 놓친다
LaunchedEffect(Unit) {
    viewModel.getAllWishes.collect { /* ... */ }
}

// 좋음
val wishes by viewModel.wishes.collectAsStateWithLifecycle()
```

일회성 이벤트(스낵바, 내비게이션)라면 예외적으로 `LaunchedEffect`에서 수집하지만, 그때도 `repeatOnLifecycle`이나 `Channel`을 쓰는 편이 안전하다.

### 상태를 하나로 묶는다

```kotlin
// 흩어진 상태
val wishes by viewModel.wishes.collectAsStateWithLifecycle()
val loading by viewModel.loading.collectAsStateWithLifecycle()
val error by viewModel.error.collectAsStateWithLifecycle()

// 묶은 상태
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

여러 `State`를 각각 구독하면 **서로 다른 프레임에 갱신되어 화면이 어긋나는 순간**이 생길 수 있다. 하나의 `UiState`로 묶으면 한꺼번에 반영된다. `sealed interface`로 만들면 불가능한 조합도 막을 수 있다. → [`kotlin-sealed-class-and-interface.md`](kotlin-sealed-class-and-interface.md)

### `key`를 잊지 않는다

```kotlin
items(items = wishList.value, key = { it.id }) { wish -> ... }
```

`chapter205`는 `key`를 잘 주고 있다. 이것이 스와이프 애니메이션과 상태 유지의 전제가 된다. → [`compose-lazy-list-animate-item.md`](compose-lazy-list-animate-item.md)

## 체크리스트

- [ ] `collectAsState`가 진짜 `State<T>`를 반환한다는 것을 안다.
- [ ] 내부적으로 `remember` + `LaunchedEffect` + `collect`로 구성된다는 것을 설명할 수 있다.
- [ ] `Flow`가 "응답이 끝나는" 것이 아니라 계속 흐른다는 것을 안다.
- [ ] `initial` 값이 필요한 이유를 설명할 수 있다.
- [ ] `State`를 읽는 행위가 곧 구독이라는 것을 안다.
- [ ] `collectAsState`와 `collectAsStateWithLifecycle`의 차이를 말할 수 있다.
- [ ] `Flow` 인스턴스가 바뀌면 재구독이 일어난다는 것을 안다.
- [ ] 매 재구성마다 새 `Flow`를 만드는 코드의 문제를 지적할 수 있다.
- [ ] `stateIn`으로 `StateFlow`를 노출하면 얻는 이점 세 가지를 말할 수 있다.
- [ ] `SharingStarted.WhileSubscribed(5_000)`의 의미를 안다.
- [ ] 계층별로 어떤 타입을 쓸지 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: Kotlin flows on Android](https://developer.android.com/kotlin/flow)
- [Android Developers: StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [Android Developers: Use Kotlin coroutines with lifecycle-aware components](https://developer.android.com/topic/libraries/architecture/coroutines)
- [Android Developers: UI layer architecture](https://developer.android.com/topic/architecture/ui-layer)
- [Android Developers: Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects)
- [Android Developers: Compose and other libraries](https://developer.android.com/develop/ui/compose/libraries)
