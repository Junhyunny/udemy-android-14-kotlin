# `Scaffold`는 무엇을 해 주는가

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/HomeView.kt`](../chapter205/app/src/main/java/com/example/chapter_205/HomeView.kt)
- 질문: `Scaffold` 컴포저블은 무슨 기능인가? 어떤 것을 지원하는가?

```kotlin
Scaffold(
    topBar = { AppBarView(title = "Wish List") },
    floatingActionButton = { FloatingActionButton(...) },
) { paddingValues ->
    LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) { ... }
}
```

## 질문 전제 점검

- **`Scaffold`를 "레이아웃 컴포넌트"로 이해하면 절반이다.** `Column`이나 `Box`처럼 자식을 배치하는 것도 맞지만, 그것만이라면 굳이 쓸 이유가 없다. **진짜 값어치는 인셋(inset) 계산과 슬롯 간 조율**에 있다.

  > "In Material Design, a scaffold is a fundamental structure that provides a standardized platform for complex user interfaces. It holds together different parts of the UI, such as app bars and floating action buttons, giving apps a coherent look and feel."

- **`Scaffold`가 실제로 해 주는 일을 손으로 하려면** 이만큼이 필요하다.

  ```kotlin
  // Scaffold 없이 직접 한다면
  Box(Modifier.fillMaxSize()) {
      Column {
          TopAppBar(...)                          // 상태 표시줄 인셋 처리 필요
          LazyColumn(Modifier.weight(1f)) { }     // 남은 높이 계산
      }
      FloatingActionButton(
          modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(16.dp)
              .windowInsetsPadding(...)           // 내비게이션 바 인셋
      )
      SnackbarHost(
          hostState = ...,
          modifier = Modifier.align(Alignment.BottomCenter)   // FAB 와 겹치지 않게
      )
  }
  ```

  **가장 귀찮은 부분이 인셋이다.** `enableEdgeToEdge()`를 쓰면 앱이 상태 표시줄과 내비게이션 바 뒤까지 그려지는데, 각 요소가 시스템 UI에 가리지 않도록 여백을 계산해야 한다. `Scaffold`가 이것을 대신한다.

- **`paddingValues`를 반드시 써야 한다는 점이 가장 흔한 실수다.**

  > "It passes `PaddingValues` to the `content` lambda that **you should apply to your content's root composable to constrain its size**."

  이걸 빼면 **콘텐츠가 앱바 뒤로 들어가 가려진다.** `chapter205`는 `.padding(paddingValues)`를 잘 적용하고 있다. 반면 `AddEditDetailView`는 람다 파라미터 이름을 `it`으로 두고 `.padding(it)`을 쓰는데, 동작은 하지만 이름을 붙이는 편이 읽기 좋다.

## 공부할 내용

### 제공하는 슬롯

| 슬롯 | 위치 | 용도 |
| --- | --- | --- |
| `topBar` | 상단 | `TopAppBar` |
| `bottomBar` | 하단 | `NavigationBar`, `BottomAppBar` |
| `floatingActionButton` | 우하단(기본) | 주요 액션 |
| `floatingActionButtonPosition` | — | FAB 위치 조정 |
| `snackbarHost` | 하단 | 스낵바 표시 영역 |
| `content` | 나머지 전부 | 화면 본문 |

`chapter205`는 `topBar`, `floatingActionButton`, `snackbarHost`(`AddEditDetailView`)를 쓰고 있다.

### 전체 예제

```kotlin
@Composable
fun ScaffoldExample() {
    var presses by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = { Text("Top app bar") }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    text = "Bottom app bar",
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { presses++ }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding),      // ← 반드시 적용
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(modifier = Modifier.padding(8.dp), text = "Your content here")
        }
    }
}
```

### `paddingValues`가 담고 있는 것

```kotlin
{ paddingValues ->
    // paddingValues.calculateTopPadding()     = topBar 높이 + 상태 표시줄
    // paddingValues.calculateBottomPadding()  = bottomBar 높이 + 내비게이션 바
}
```

**슬롯을 안 쓰면 그만큼 0에 가까워진다.** `chapter205`는 `bottomBar`가 없으므로 아래쪽 패딩은 시스템 내비게이션 바 몫만 들어간다.

### 패딩을 어디에 적용하느냐가 결과를 바꾼다

목록 화면에서 자주 겪는 문제다.

```kotlin
// ① 목록 바깥에 패딩 → 스크롤 영역 자체가 줄어든다
LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) { }

// ② contentPadding 으로 → 목록은 전체를 쓰고, 항목만 안쪽으로 밀린다
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = paddingValues
) { }
```

**②가 대개 더 자연스럽다.** 스크롤할 때 항목이 앱바 뒤로 자연스럽게 지나가고, 마지막 항목이 내비게이션 바에 가리지 않는다. ①은 목록 영역이 잘려서 앱바 아래에서 스크롤이 시작된다.

`chapter205`는 ①을 쓰고 있다. 지금은 앱바가 불투명해서 차이가 크지 않지만, 반투명 앱바를 쓰면 ②가 훨씬 낫다.

### 스낵바 호스트

```kotlin
val snackbarHostState = remember { SnackbarHostState() }

Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
) { ... }

// 표시
scope.launch { snackbarHostState.showSnackbar("저장했습니다") }
```

`Scaffold`에 맡기면 **스낵바가 FAB를 밀어 올리고, 내비게이션 바를 피한다.** 직접 배치하면 이 조율을 손으로 해야 한다.

`showSnackbar()`는 **`suspend` 함수**이고 스낵바가 사라질 때까지 중단한다. 반환값으로 사용자가 액션을 눌렀는지 알 수 있다.

```kotlin
val result = snackbarHostState.showSnackbar(
    message = "삭제했습니다",
    actionLabel = "실행 취소",
    duration = SnackbarDuration.Short
)
if (result == SnackbarResult.ActionPerformed) {
    viewModel.undoDelete()
}
```

**스와이프 삭제에 "실행 취소"를 붙이는 것이 Material 권장 패턴이다.** `chapter205`의 삭제는 되돌릴 방법이 없다.

### `contentWindowInsets`

```kotlin
Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing      // 기본값
) { ... }
```

`Scaffold`가 콘텐츠 패딩을 계산할 때 고려할 시스템 영역을 정한다. 직접 인셋을 다루고 싶으면 비울 수 있다.

```kotlin
Scaffold(contentWindowInsets = WindowInsets(0)) { ... }   // 인셋 계산을 끄고 직접 처리
```

`enableEdgeToEdge()`와 짝을 이루는 설정이다. `MainActivity`에서 `enableEdgeToEdge()`를 호출하고 있으므로, 이 계산이 실제로 동작하고 있다.

### 중첩해서 쓰지 않는다

```kotlin
// 피한다: 패딩이 두 번 계산된다
Scaffold { outer ->
    Scaffold { inner -> ... }
}
```

**화면당 `Scaffold` 하나**가 원칙이다. `chapter205`는 `HomeView`와 `AddEditDetailView`가 각각 하나씩 갖고 있어 올바르다.

다만 **하단 내비게이션이 있는 앱**이라면 구조를 바꾼다.

```kotlin
// 바깥 Scaffold 에 bottomBar, 안쪽 화면은 Scaffold 없이
Scaffold(bottomBar = { NavigationBar { ... } }) { padding ->
    NavHost(navController, startDestination, Modifier.padding(padding)) {
        composable<Home> { HomeScreen() }      // 여기서 또 Scaffold 를 쓰지 않는다
    }
}
```

## 관련 아키텍처와 베스트 프랙티스

### `Scaffold`를 쓸지 판단하기

| 상황 | |
| --- | --- |
| 앱바, FAB, 스낵바 중 하나라도 있다 | ✅ `Scaffold` |
| 하단 내비게이션이 있다 | ✅ `Scaffold` |
| 엣지 투 엣지를 쓴다 | ✅ `Scaffold` |
| 전체 화면 이미지, 스플래시 | ❌ `Box`로 충분 |
| 다이얼로그 내용 | ❌ |

### 화면 컴포저블이 `Scaffold`를 소유한다

```kotlin
@Composable
fun HomeView(...) {
    Scaffold(...) { ... }        // 화면 단위 컴포저블이 갖는다
}

@Composable
fun WishItem(...) { ... }        // 부품은 Scaffold 를 모른다
```

부품 컴포저블이 `Scaffold`를 쓰면 재사용이 막힌다.

### 슬롯에 넘기는 컴포저블도 분리한다

```kotlin
Scaffold(
    topBar = { AppBarView(title = "Wish List") }    // 별도 컴포저블로 빼 두었다
)
```

`chapter205`가 `AppBarView`를 분리한 것은 잘한 구조다. 슬롯 람다 안에 긴 코드를 인라인으로 쓰면 화면 함수가 금방 읽기 어려워진다.

### 스낵바 상태는 화면에 둔다

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
```

`ViewModel`에 두지 않는다. **스낵바는 UI 표현이고 회전하면 사라지는 것이 자연스럽다.** 다만 "저장 완료" 같은 이벤트는 `ViewModel`이 발행하고 화면이 받아 표시하는 구조가 낫다.

```kotlin
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            is Event.Saved -> snackbarHostState.showSnackbar("저장했습니다")
        }
    }
}
```

→ [`059-compose-launchedeffect-and-snapshotflow.md`](059-compose-launchedeffect-and-snapshotflow.md)

### 이 코드에서 다듬을 부분

```kotlin
// HomeView: 목록에는 contentPadding 이 더 자연스럽다
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = paddingValues
) { ... }

// AddEditDetailView: 람다 파라미터에 이름을 준다
) { innerPadding ->
    Column(modifier = Modifier.padding(innerPadding).wrapContentSize()) { ... }
}
```

## 체크리스트

- [ ] `Scaffold`가 단순 레이아웃이 아니라 인셋과 슬롯 조율을 한다는 것을 설명할 수 있다.
- [ ] `Scaffold`가 제공하는 슬롯을 나열할 수 있다.
- [ ] `paddingValues`를 적용하지 않으면 무슨 일이 생기는지 안다.
- [ ] `paddingValues`에 무엇이 들어 있는지 설명할 수 있다.
- [ ] 목록에서 `padding`과 `contentPadding`의 차이를 설명할 수 있다.
- [ ] `SnackbarHostState`를 `Scaffold`에 연결할 수 있다.
- [ ] `showSnackbar()`가 `suspend` 함수라는 것과 반환값의 의미를 안다.
- [ ] "실행 취소" 액션을 붙이는 패턴을 안다.
- [ ] `contentWindowInsets`와 `enableEdgeToEdge()`의 관계를 안다.
- [ ] `Scaffold`를 중첩하지 않아야 하는 이유를 안다.
- [ ] 하단 내비게이션이 있을 때의 구조를 설명할 수 있다.
- [ ] 스낵바 상태를 `ViewModel`에 두지 않는 이유를 안다.

## 공식 참고 자료

- [Android Developers: Scaffold](https://developer.android.com/develop/ui/compose/components/scaffold)
- [Android Developers: App bars](https://developer.android.com/develop/ui/compose/components/app-bars)
- [Android Developers: Snackbar](https://developer.android.com/develop/ui/compose/components/snackbar)
- [Android Developers: Floating action button](https://developer.android.com/develop/ui/compose/components/fab)
- [Android Developers: Display content edge-to-edge](https://developer.android.com/develop/ui/compose/layouts/insets)
- [Android Developers: Lists and grids](https://developer.android.com/develop/ui/compose/lists)
- [Material Design 3: Scaffold](https://m3.material.io/foundations/layout/understanding-layout/overview)
