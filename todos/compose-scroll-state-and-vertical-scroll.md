# `rememberScrollState`에는 어떤 기능이 숨어 있는가

## 질문이 나온 코드

- [`chapter143/app/src/main/java/com/example/chapter_143/CategoryDetailScreen.kt`](../chapter143/app/src/main/java/com/example/chapter_143/CategoryDetailScreen.kt)
- 질문: 텍스트 영역만 스크롤로 지정하는 방식인 것 같은데, `rememberScrollState`에는 어떤 기능이 숨어 있는가?

```kotlin
Text(
    text = category.strCategoryDescription,
    textAlign = TextAlign.Justify,
    modifier = Modifier.verticalScroll(
        rememberScrollState()
    )
)
```

## 질문 전제 점검

- **"텍스트 영역만 스크롤로 지정하는 방식"** → 맞다. `Modifier.verticalScroll`은 **그 modifier가 붙은 컴포저블 하나**를 스크롤 가능하게 만든다. 지금은 `Text`에 붙었으니 설명 텍스트만 스크롤된다.

- **역할이 둘로 나뉘어 있다는 점을 먼저 봐야 한다.** 한 줄에 두 가지가 섞여 있어서 헷갈리기 쉽다.

  ```kotlin
  Modifier.verticalScroll( rememberScrollState() )
  //       ^^^^^^^^^^^^^^   ^^^^^^^^^^^^^^^^^^^^
  //       동작(behavior)    상태(state)
  //       "스크롤할 수 있게 해라"  "스크롤 위치를 기억하는 그릇"
  ```

  컴포즈 전반의 패턴이다. **동작은 modifier가, 값은 state 객체가 들고 있다.** 그래서 스크롤 위치를 읽거나 바꾸고 싶으면 state를 변수로 꺼내면 된다.

- **"어떤 기능이 숨어 있는가"** → 크게 세 가지다. 지금 코드는 그중 **첫 번째만** 쓰고 있다.

  1. **스크롤 위치를 재구성 너머로 유지한다** ← 지금 쓰는 것
  2. **위치를 읽을 수 있다** (`state.value`, `maxValue`, `isScrollInProgress`)
  3. **위치를 코드로 바꿀 수 있다** (`scrollTo`, `animateScrollTo`)

- **그리고 지금 코드에는 실제 문제가 하나 있다.** `Column`에 `fillMaxSize()`가 걸려 있고 그 안의 `Text`에 `verticalScroll`이 붙어 있는데, **`Text`에 높이 제한이 없다.** 스크롤은 "내용이 주어진 공간보다 클 때" 일어나는데, 공간이 무한정 주어지면 스크롤할 일이 생기지 않는다. 위의 `Image`가 `aspectRatio(1f)`로 자리를 차지하고 남은 높이가 모자랄 때만 우연히 동작한다. **`Modifier.weight(1f)`로 남은 공간을 명시하는 편이 의도가 분명하다.** 아래에서 다룬다.

## 공부할 내용

### `ScrollState`가 들고 있는 것

> "The `ScrollState` lets you change the scroll position or get its current state."

```kotlin
val state = rememberScrollState()

state.value                 // 현재 스크롤 위치 (픽셀). 0 = 맨 위
state.maxValue              // 스크롤 가능한 최대 위치
state.isScrollInProgress    // 지금 스크롤 중인가 (Boolean)

state.scrollTo(100)         // 즉시 이동 (suspend)
state.animateScrollTo(100)  // 부드럽게 이동 (suspend)
```

`value`와 `maxValue`는 **컴포즈 상태**다. 읽는 컴포저블은 스크롤될 때마다 재구성된다. 그래서 "맨 위에 있을 때만 그림자를 숨긴다" 같은 UI를 만들 수 있다.

### `remember`가 하는 일

`rememberScrollState()`는 이름 그대로 `remember`가 들어 있다.

> "Use `rememberScrollState()` to create a `ScrollState` with default parameters."

```kotlin
// 개념적으로
@Composable
fun rememberScrollState(initial: Int = 0): ScrollState =
    rememberSaveable(saver = ScrollState.Saver) { ScrollState(initial) }
```

만약 `remember` 없이 `ScrollState(0)`를 직접 만들면, **재구성될 때마다 새 상태가 만들어져 스크롤 위치가 맨 위로 튀어 오른다.** 다른 상태가 바뀌어 화면이 재구성될 때마다 스크롤이 초기화되는 것이다.

실제로는 `rememberSaveable`이라서 한 단계 더 나아간다. **화면 회전이나 프로세스 종료 후에도 스크롤 위치가 복원된다.** 이것이 "숨어 있는 기능" 중 체감이 큰 부분이다. → [`compose-state-storage-and-snapshot.md`](compose-state-storage-and-snapshot.md)

### 기본 사용

공식 문서의 예제다.

```kotlin
@Composable
private fun ScrollBoxes() {
    Column(
        modifier = Modifier
            .background(Color.LightGray)
            .size(100.dp)                       // ← 크기를 제한한다
            .verticalScroll(rememberScrollState())
    ) {
        repeat(10) {
            Text("Item $it", modifier = Modifier.padding(2.dp))
        }
    }
}
```

**`.size(100.dp)`가 있다는 점이 중요하다.** 100dp 안에 10개 항목이 안 들어가므로 스크롤이 생긴다. 크기 제한이 없으면 스크롤할 이유가 없다.

> "The `verticalScroll` modifier provides the simplest way to allow users to scroll an element when its contents are larger than its maximum size constraints."

**"내용이 최대 크기 제약보다 클 때"**가 조건이다.

### 코드로 스크롤 제어하기

```kotlin
@Composable
private fun ScrollBoxesSmooth() {
    val state = rememberScrollState()
    LaunchedEffect(Unit) { state.animateScrollTo(100) }

    Column(
        modifier = Modifier
            .size(100.dp)
            .verticalScroll(state)
    ) {
        repeat(10) { Text("Item $it") }
    }
}
```

`scrollTo`와 `animateScrollTo`는 **`suspend` 함수**다. 코루틴 안에서만 부를 수 있다. 그래서 두 가지 패턴이 나온다.

```kotlin
// 화면에 들어올 때 한 번
LaunchedEffect(Unit) { state.animateScrollTo(0) }

// 버튼을 눌렀을 때
val scope = rememberCoroutineScope()
Button(onClick = { scope.launch { state.animateScrollTo(0) } }) {
    Text("맨 위로")
}
```

`onClick`은 컴포저블이 아니라 일반 람다라서 `suspend` 함수를 직접 부를 수 없다. `rememberCoroutineScope()`가 필요한 이유다. → [`kotlin-coroutines-suspend-and-event-loop.md`](kotlin-coroutines-suspend-and-event-loop.md)

### 상태를 읽어 UI 만들기

```kotlin
val state = rememberScrollState()
val showScrollToTop by remember { derivedStateOf { state.value > 500 } }

Box {
    Column(Modifier.verticalScroll(state)) { /* 긴 내용 */ }

    if (showScrollToTop) {
        FloatingActionButton(onClick = { scope.launch { state.animateScrollTo(0) } }) {
            Icon(Icons.Filled.KeyboardArrowUp, "맨 위로")
        }
    }
}
```

`derivedStateOf`로 감싼 이유는 **`state.value`가 스크롤 픽셀마다 바뀌기 때문**이다. 그대로 읽으면 1픽셀 움직일 때마다 재구성된다. `derivedStateOf`는 계산 결과(`true`/`false`)가 실제로 바뀔 때만 재구성을 일으킨다.

### 이 코드를 개선한다면

```kotlin
// 지금
Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(text = category.strCategory, textAlign = TextAlign.Center)
    Image(...)
    Text(
        text = category.strCategoryDescription,
        modifier = Modifier.verticalScroll(rememberScrollState())   // 높이 제한이 없다
    )
}
```

두 가지 방향이 있다.

**① 설명만 스크롤하고 제목·이미지는 고정 — 지금 의도**

```kotlin
Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(text = category.strCategory, textAlign = TextAlign.Center)
    AsyncImage(...)
    Text(
        text = category.strCategoryDescription,
        textAlign = TextAlign.Justify,
        modifier = Modifier
            .weight(1f)                                  // ← 남은 높이를 전부 차지
            .verticalScroll(rememberScrollState())
    )
}
```

**`weight(1f)`가 "남은 공간이 네 최대 크기다"라고 알려 준다.** 그래야 내용이 그보다 클 때 스크롤이 확실히 동작한다. 지금처럼 우연에 기대지 않는다.

**② 화면 전체를 스크롤 — 더 흔한 패턴**

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())       // ← Column 에 붙인다
        .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(text = category.strCategory)
    AsyncImage(...)
    Text(text = category.strCategoryDescription)
}
```

상세 화면에서는 보통 ②가 자연스럽다. 이미지까지 함께 밀려 올라가면서 본문을 넓게 볼 수 있기 때문이다.

**주의: 스크롤 modifier는 하나만 쓴다.** `Column`에도 붙이고 안쪽 `Text`에도 붙이면 같은 방향 스크롤이 중첩되어 제스처가 어느 쪽으로 갈지 모호해진다. 컴포즈는 **높이가 무한대로 측정되는 상황**에서 크래시를 내기도 한다.

### `verticalScroll`과 `LazyColumn`

가장 중요한 구분이다.

> "If you want to show a list of items, consider using `LazyColumn` and `LazyRow` instead of these APIs. `LazyColumn` and `LazyRow` feature scrolling, and they are **much more efficient** than the scrolling modifier because they **only compose the items as they're needed**."

| | `Column` + `verticalScroll` | `LazyColumn` |
| --- | --- | --- |
| 구성 시점 | **모든 자식을 한 번에** 만든다 | **보이는 것만** 만든다 |
| 적합한 경우 | 개수가 적고 고정된 화면 | 목록, 개수가 많거나 가변 |
| 상태 객체 | `ScrollState` | `LazyListState` |
| 위치 단위 | 픽셀 (`value`) | 항목 인덱스 (`firstVisibleItemIndex`) |

```kotlin
// 항목 1000개를 Column + verticalScroll 로 → 1000개를 전부 만든다. 느리고 메모리를 먹는다
Column(Modifier.verticalScroll(rememberScrollState())) {
    items.forEach { ItemRow(it) }
}

// LazyColumn → 화면에 보이는 10여 개만 만든다
LazyColumn {
    items(items) { ItemRow(it) }
}
```

**판단 기준: 자식 개수가 고정이고 10개 남짓이면 `Column`+`verticalScroll`, 데이터에 따라 늘어나면 `LazyColumn`.** 지금 상세 화면은 자식이 세 개뿐이라 `Column`이 맞다. 반면 `RecipeScreen`의 격자는 `LazyVerticalGrid`를 쓰고 있어 올바르다.

## 관련 아키텍처와 베스트 프랙티스

### 컴포즈의 "동작 + 상태" 패턴

`verticalScroll`만의 이야기가 아니다. 같은 구조가 반복된다.

| 동작 | 상태 객체 | 만드는 함수 |
| --- | --- | --- |
| `Modifier.verticalScroll` | `ScrollState` | `rememberScrollState()` |
| `LazyColumn` | `LazyListState` | `rememberLazyListState()` |
| `LazyVerticalGrid` | `LazyGridState` | `rememberLazyGridState()` |
| `HorizontalPager` | `PagerState` | `rememberPagerState()` |
| `ModalBottomSheet` | `SheetState` | `rememberModalBottomSheetState()` |

**상태를 바깥에서 제어할 필요가 없으면 인라인으로, 필요하면 변수로 꺼낸다.**

```kotlin
// 제어할 일 없음 — 인라인 (지금 코드)
Modifier.verticalScroll(rememberScrollState())

// 제어할 일 있음 — 꺼낸다
val scrollState = rememberScrollState()
Modifier.verticalScroll(scrollState)
```

### modifier 순서가 결과를 바꾼다

```kotlin
// padding 안쪽이 스크롤된다 → 스크롤해도 여백이 유지된다
Modifier.padding(16.dp).verticalScroll(state)

// 스크롤 영역 안에 padding 이 들어간다 → 여백도 함께 밀려 올라간다
Modifier.verticalScroll(state).padding(16.dp)
```

컴포즈 modifier는 **적은 순서대로** 적용된다. 스크롤과 패딩, 배경, 클릭을 함께 쓸 때 순서에 따라 결과가 달라진다.

### 스크롤 상태를 `ViewModel`에 두지 않는다

스크롤 위치는 **UI의 표현 상태**다. `rememberSaveable` 기반이라 회전과 프로세스 종료를 이미 견딘다. `ViewModel`에 옮기면 관심사만 흐려진다.

### 중첩 스크롤

같은 방향으로 스크롤 영역을 중첩하는 것은 피한다. 서로 다른 방향이면 문제없다.

```kotlin
// 괜찮다: 세로 안에 가로
Column(Modifier.verticalScroll(rememberScrollState())) {
    LazyRow { items(items) { Card(it) } }
}

// 피한다: 세로 안에 세로
Column(Modifier.verticalScroll(rememberScrollState())) {
    LazyColumn { items(items) { Row(it) } }   // 높이 측정 문제 / 크래시 가능
}
```

세로 안에 세로 목록이 필요하면 `LazyColumn` 하나로 합치고 `item { }`과 `items { }`를 섞는다.

## 체크리스트

- [ ] `Modifier.verticalScroll`(동작)과 `ScrollState`(상태)의 역할 분리를 설명할 수 있다.
- [ ] `rememberScrollState`가 `rememberSaveable` 기반이라 회전에도 위치가 유지된다는 것을 안다.
- [ ] `remember` 없이 상태를 만들면 스크롤이 초기화되는 이유를 설명할 수 있다.
- [ ] 스크롤이 동작하려면 크기 제한이 필요하다는 것을 안다.
- [ ] `weight(1f)`로 남은 공간을 확정하는 이유를 설명할 수 있다.
- [ ] `scrollTo`/`animateScrollTo`가 `suspend` 함수라 코루틴이 필요한 이유를 안다.
- [ ] `state.value`를 직접 읽을 때 `derivedStateOf`가 필요한 이유를 설명할 수 있다.
- [ ] `Column`+`verticalScroll`과 `LazyColumn`의 차이를 설명할 수 있다.
- [ ] 언제 `LazyColumn`으로 바꿔야 하는지 기준을 세울 수 있다.
- [ ] modifier 순서에 따라 스크롤과 패딩의 결과가 달라지는 것을 안다.
- [ ] 같은 방향 스크롤 중첩을 피해야 하는 이유를 안다.

## 공식 참고 자료

- [Android Developers: Scroll](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/scroll)
- [Android Developers: Lists and grids](https://developer.android.com/develop/ui/compose/lists)
- [Android Developers: `ScrollState` reference](https://developer.android.com/reference/kotlin/androidx/compose/foundation/ScrollState)
- [Android Developers: `verticalScroll` reference](https://developer.android.com/reference/kotlin/androidx/compose/foundation/package-summary#(androidx.compose.ui.Modifier).verticalScroll(androidx.compose.foundation.ScrollState,kotlin.Boolean,androidx.compose.foundation.gestures.FlingBehavior,kotlin.Boolean))
- [Android Developers: Compose modifiers](https://developer.android.com/develop/ui/compose/modifiers)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: Compose performance (`derivedStateOf`)](https://developer.android.com/develop/ui/compose/performance/bestpractices)
