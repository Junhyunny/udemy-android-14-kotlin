# `LaunchedEffect`와 `snapshotFlow`는 언제 실행되는가

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/HomeView.kt`](../chapter205/app/src/main/java/com/example/chapter_205/HomeView.kt)
- 질문: `LaunchedEffect` 개념을 알려 달라. 언제 실행되는 건가? `snapshotFlow`도 개념과 실행 시점, 다양한 사용 케이스를 정리해 달라.
- 메모: `LaunchedEffect(currentValue)`보다 `snapshotFlow`를 쓰는 쪽을 더 추천한다. 상태 변화를 명시적으로 관찰한다는 의도가 분명하기 때문이다.

```kotlin
LaunchedEffect(dismissState) {
    snapshotFlow { dismissState.currentValue }
        .distinctUntilChanged()
        .collect { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                viewModel.deleteWish(wish)
            }
        }
}
```

## 질문 전제 점검

- **메모의 판단은 이 코드에서 타당하다.** 다만 이유를 정확히 해 두는 편이 좋다. `snapshotFlow`가 "더 명시적이라서" 좋은 것이 아니라, **두 방식이 잡아내는 사건이 다르기 때문**이다.

  ```kotlin
  // ① 키 방식: 재구성될 때 키를 비교한다
  LaunchedEffect(dismissState.currentValue) {
      if (dismissState.currentValue == EndToStart) viewModel.deleteWish(wish)
  }

  // ② snapshotFlow: 스냅샷이 커밋될 때마다 값을 관찰한다
  LaunchedEffect(dismissState) {
      snapshotFlow { dismissState.currentValue }.collect { ... }
  }
  ```

  ①은 **재구성이 일어나야만** 감지한다. 그 상태를 읽는 컴포저블이 재구성되지 않으면 값이 바뀌어도 모른다. ②는 재구성과 무관하게 **스냅샷 시스템이 값 변경을 알려 준다.**

  `dismissState.currentValue`는 `SwipeToDismissBox` 내부에서 바뀌는데, 이 값을 `HomeView`의 컴포저블 본문이 읽고 있지 않다. 그래서 **①은 감지를 놓칠 수 있다.** ②를 고른 것이 맞다.

- **"언제 실행되는가"의 답은 세 가지 사건으로 나뉜다.**

  ```
  ① 컴포지션에 진입할 때        → 코루틴 시작
  ② 키가 바뀔 때                 → 기존 코루틴 취소 후 새로 시작
  ③ 컴포지션을 떠날 때           → 코루틴 취소
  ```

  **"한 번 실행하고 끝"이 아니다.** 키가 바뀌면 재시작되고, 화면을 떠나면 취소된다. 이 세 가지를 모두 기억해야 한다.

- **한 가지 짚어 둘 것.** 지금 코드의 `distinctUntilChanged()`는 사실 필요 없다. `snapshotFlow`가 이미 같은 동작을 한다.

  > State 읽기 값이 변경되고 이전값과 다를 때 새 값 emit. `distinctUntilChanged` 동작과 유사

  해가 되지는 않지만, **`snapshotFlow`가 중복을 걸러 준다는 사실을 알고 있으면** 코드가 줄어든다.

## 공부할 내용

### 부수 효과가 왜 필요한가

컴포저블 본문은 **언제, 몇 번 실행될지 보장되지 않는다.** 재구성은 프레임마다 일어날 수도 있고, 순서가 바뀔 수도 있고, 취소될 수도 있다. 그래서 이런 코드는 위험하다.

```kotlin
@Composable
fun Screen() {
    viewModel.load()            // ❌ 재구성마다 호출된다
    Log.d("TAG", "그려짐")       // ❌ 몇 번 찍힐지 모른다
    analytics.logScreen()       // ❌ 중복 집계
}
```

**"컴포저블 밖 세계에 영향을 주는 일"은 부수 효과 API 안에서 해야 한다.** Compose가 실행 시점을 보장해 주는 자리다.

### `LaunchedEffect`

> **실행 시점**
> - LaunchedEffect가 Composition에 진입할 때 코루틴 실행
> - 키 파라미터가 변경되면 기존 코루틴 취소 후 새 코루틴 실행
> - LaunchedEffect가 Composition을 떠날 때 코루틴 취소

```kotlin
var pulseRateMs by remember { mutableLongStateOf(3000L) }
val alpha = remember { Animatable(1f) }
LaunchedEffect(pulseRateMs) {
    while (isActive) {
        delay(pulseRateMs)
        alpha.animateTo(0f)
        alpha.animateTo(1f)
    }
}
```

#### 키를 무엇으로 줄지가 전부다

```kotlin
LaunchedEffect(Unit) { }         // 화면에 들어올 때 딱 한 번. 이후 재시작 없음
LaunchedEffect(userId) { }       // userId 가 바뀔 때마다 다시 실행
LaunchedEffect(a, b) { }         // 둘 중 하나라도 바뀌면 다시 실행
LaunchedEffect(viewModel) { }    // 사실상 한 번 (viewModel 은 안 바뀐다)
```

**`LaunchedEffect(Unit)`은 "화면 진입 시 1회"의 관용구다.** 다만 남용하면 위험하다. 안에서 읽는 값이 바뀌어도 재시작되지 않아 **오래된 값을 계속 쓰게 된다.**

```kotlin
// 함정: id 가 바뀌어도 예전 id 로 로드한 채 그대로
LaunchedEffect(Unit) { viewModel.load(id) }

// 올바름
LaunchedEffect(id) { viewModel.load(id) }
```

지금 코드의 `LaunchedEffect(dismissState)`는 **항목마다 `dismissState`가 다르므로** 항목별로 독립적인 코루틴이 뜬다. 적절한 키다.

### `snapshotFlow`

> **정의**: Compose의 State를 Cold Flow로 변환
>
> **실행 시점**
> - Flow가 수집될 때 블록 실행
> - State 읽기 값이 변경되고 이전값과 다를 때 새 값 emit
> - `distinctUntilChanged` 동작과 유사

```kotlin
val listState = rememberLazyListState()

LazyColumn(state = listState) { /* ... */ }

LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .map { index -> index > 0 }
        .distinctUntilChanged()
        .filter { it == true }
        .collect {
            MyAnalyticsService.sendScrolledPastFirstItemEvent()
        }
}
```

**방향이 `collectAsState`의 반대다.**

```
collectAsState :  Flow  →  State     (데이터가 UI로)
snapshotFlow   :  State →  Flow      (UI 상태를 관찰하러)
```

그래서 `Flow` 연산자를 전부 쓸 수 있게 된다. 이것이 실질적인 장점이다.

```kotlin
snapshotFlow { searchText }
    .debounce(300)                  // 타이핑이 멈추면
    .filter { it.length >= 2 }
    .distinctUntilChanged()
    .collect { viewModel.search(it) }
```

**`LaunchedEffect(searchText)`만으로는 `debounce`를 이렇게 쓸 수 없다.**

### 두 방식을 언제 나눠 쓰나

| | `LaunchedEffect(key)` | `snapshotFlow { }` |
| --- | --- | --- |
| 감지 계기 | **재구성 시 키 비교** | 스냅샷 커밋 |
| 그 상태를 본문에서 읽지 않아도 | ❌ 놓칠 수 있다 | ✅ 감지한다 |
| 값마다 실행 | 매번 코루틴 재시작 | 같은 코루틴에서 계속 |
| `Flow` 연산자 | ❌ | ✅ |
| 코드 길이 | 짧다 | 조금 길다 |

**판단 기준.**

- 단순히 "이 값이 바뀌면 다시 해라" → `LaunchedEffect(key)`
- 스크롤 위치, 드래그 상태처럼 **자주 바뀌고 본문에서 안 읽는 값** → `snapshotFlow`
- `debounce`, `filter`, `map` 등이 필요 → `snapshotFlow`

한 가지 성능 차이도 있다. **`LaunchedEffect(key)`는 키가 바뀔 때마다 코루틴을 취소하고 새로 만든다.** 스크롤 위치처럼 초당 수십 번 바뀌는 값을 키로 쓰면 그 비용이 쌓인다. `snapshotFlow`는 코루틴 하나로 계속 처리한다.

### 부수 효과 API 정리

#### `rememberCoroutineScope`

```kotlin
val scope = rememberCoroutineScope()
Button(onClick = { scope.launch { snackbarHostState.showSnackbar("...") } })
```

**컴포저블이 아닌 콜백에서 코루틴을 시작할 때.** → [`kotlin-coroutine-scope-concept.md`](kotlin-coroutine-scope-concept.md)

#### `DisposableEffect`

정리가 필요한 효과다.

```kotlin
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event -> /* ... */ }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)    // 반드시 해제
    }
}
```

**리스너 등록/해제, 센서 구독, 브로드캐스트 리시버**에 쓴다. `onDispose`가 필수다.

#### `SideEffect`

> 매 성공적인 recomposition 이후 실행

```kotlin
SideEffect {
    analytics.setUserProperty("userType", user.userType)
}
```

**Compose 상태를 Compose 밖 객체에 전달할 때.** 코루틴이 아니므로 `suspend` 함수를 쓸 수 없다.

#### `rememberUpdatedState`

오래 사는 효과 안에서 최신 람다를 참조해야 할 때 쓴다.

```kotlin
@Composable
fun Timer(onTimeout: () -> Unit) {
    val currentOnTimeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(Unit) {          // 재시작하고 싶지 않다
        delay(5000)
        currentOnTimeout()          // 그래도 최신 람다를 부른다
    }
}
```

**`LaunchedEffect(Unit)`의 함정을 푸는 도구다.**

#### 정리표

| API | 언제 | 코루틴 |
| --- | --- | --- |
| `LaunchedEffect(key)` | 진입 시 / 키 변경 시 | ✅ |
| `rememberCoroutineScope()` | 콜백에서 직접 시작 | ✅ |
| `DisposableEffect(key)` | 정리가 필요한 등록 | ❌ |
| `SideEffect` | 매 재구성 후 | ❌ |
| `snapshotFlow { }` | 상태를 Flow로 관찰 | ✅ (안에서) |
| `rememberUpdatedState` | 효과 안에서 최신 값 참조 | — |

### 사용 케이스 모음

```kotlin
// ① 화면 진입 시 데이터 로드
LaunchedEffect(id) { viewModel.load(id) }

// ② 스크롤이 끝에 닿으면 다음 페이지
LaunchedEffect(listState) {
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
        .filterNotNull()
        .collect { last -> if (last >= items.size - 3) viewModel.loadMore() }
}

// ③ 검색어 디바운스
LaunchedEffect(Unit) {
    snapshotFlow { query }.debounce(300).collect { viewModel.search(it) }
}

// ④ 일회성 이벤트 수신 (스낵바, 내비게이션)
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            is Event.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            is Event.NavigateBack -> navController.popBackStack()
        }
    }
}

// ⑤ 상태가 특정 조건이 되면 애니메이션
LaunchedEffect(isError) { if (isError) shakeAnimation.animateTo(1f) }

// ⑥ 리스너 등록/해제
DisposableEffect(Unit) {
    val listener = SensorEventListener { /* ... */ }
    sensorManager.registerListener(listener, sensor, RATE)
    onDispose { sensorManager.unregisterListener(listener) }
}
```

## 관련 아키텍처와 베스트 프랙티스

### 지금 코드를 다듬는다면

```kotlin
LaunchedEffect(dismissState) {
    snapshotFlow { dismissState.currentValue }
        .distinctUntilChanged()                    // snapshotFlow 가 이미 한다
        .collect { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                viewModel.deleteWish(wish)
            }
        }
}
```

```kotlin
// 간결하게
LaunchedEffect(dismissState) {
    snapshotFlow { dismissState.currentValue }
        .filter { it == SwipeToDismissBoxValue.EndToStart }
        .collect { viewModel.deleteWish(wish) }
}
```

`filter`로 조건을 앞으로 옮기면 의도가 더 분명해진다.

### `LaunchedEffect(Unit)`에서 읽는 값을 점검한다

```kotlin
LaunchedEffect(Unit) {
    viewModel.load(id)      // id 를 읽는데 키에 없다 → 오래된 값 함정
}
```

**효과 블록 안에서 읽는 값은 전부 키 후보다.** 재시작을 원하지 않는다면 `rememberUpdatedState`를 쓴다.

### 컴포저블 본문에 상태를 쓰지 않는다

```kotlin
// ❌ 컴포지션 중에 상태를 바꾼다
if (id != 0L) {
    viewModel.wishTitleState.value = wish.value.title
}

// ✅ 효과 안에서
LaunchedEffect(wish.value) {
    viewModel.wishTitleState.value = wish.value.title
}
```

`AddEditDetailView`가 첫 번째 형태다. → [`android-viewmodel-encapsulation-and-async-timing.md`](android-viewmodel-encapsulation-and-async-timing.md)

### 일회성 이벤트는 상태로 만들지 않는다

```kotlin
// 문제: 회전하면 스낵바가 다시 뜬다
val error by viewModel.errorState.collectAsStateWithLifecycle()
if (error != null) { LaunchedEffect(error) { showSnackbar(error) } }

// 대안: Channel 기반 이벤트
viewModel.events.collect { ... }     // 한 번 소비되면 사라진다
```

### 효과 안에서도 취소를 고려한다

`LaunchedEffect`의 코루틴은 컴포지션을 떠날 때 취소된다. **화면을 나가면서 끝나야 할 작업**을 여기 넣으면 중간에 끊긴다. 저장처럼 완료가 보장되어야 하는 일은 `viewModelScope`나 더 넓은 스코프에서 한다.

## 체크리스트

- [ ] 컴포저블 본문에 부수 효과를 두면 안 되는 이유를 설명할 수 있다.
- [ ] `LaunchedEffect`의 세 가지 실행 시점을 말할 수 있다.
- [ ] `LaunchedEffect(Unit)`의 오래된 값 함정을 설명할 수 있다.
- [ ] `snapshotFlow`가 `State`를 `Flow`로 바꾼다는 것을 안다.
- [ ] `collectAsState`와 `snapshotFlow`의 방향 차이를 설명할 수 있다.
- [ ] `snapshotFlow`가 `distinctUntilChanged` 동작을 이미 한다는 것을 안다.
- [ ] 키 방식과 `snapshotFlow`가 잡는 사건이 다른 이유를 설명할 수 있다.
- [ ] 자주 바뀌는 값을 키로 쓸 때의 비용을 안다.
- [ ] `DisposableEffect`의 `onDispose`가 필요한 상황을 예로 들 수 있다.
- [ ] `SideEffect`가 코루틴이 아니라는 것을 안다.
- [ ] `rememberUpdatedState`가 어떤 문제를 푸는지 설명할 수 있다.
- [ ] 효과 안에서 읽는 값을 키 후보로 점검하는 습관이 있다.

## 공식 참고 자료

- [Android Developers: Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: Lifecycle of composables](https://developer.android.com/develop/ui/compose/lifecycle)
- [Android Developers: Thinking in Compose](https://developer.android.com/develop/ui/compose/mental-model)
- [Android Developers: Kotlin flows on Android](https://developer.android.com/kotlin/flow)
- [Android Developers: Compose performance](https://developer.android.com/develop/ui/compose/performance/bestpractices)
