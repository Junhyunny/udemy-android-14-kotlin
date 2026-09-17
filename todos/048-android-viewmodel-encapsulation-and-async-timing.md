# 화면에서 상태를 직접 바꾸는 게 맞나 — 그리고 그 코루틴은 언제 실행되나

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/AddEditDetailView.kt`](../chapter205/app/src/main/java/com/example/chapter_205/AddEditDetailView.kt)
- 질문: 비동기 요청을 따로 하고 다시 `ViewModel`의 상태를 변경해 주는 게 맞나? 한 번에 처리하면 되지 않나? `ViewModel` 내부에 필요한 정보와 바꿔야 할 상태가 다 있는데 캡슐화하는 게 낫지 않은가? 베스트 프랙티스를 정리해 달라.
- 질문: 비동기로 실행되는데 언제 실행되는 건가? `addWish`, `updateWish`가 끝나면? 근데 `else` 구문인 경우에는 비동기 요청 없이 바로 실행되는 건가?

```kotlin
if (id != 0L) {
    val wish = viewModel.getWishById(id).collectAsState(initial = Wish(0L, "", ""))
    viewModel.wishTitleState.value = wish.value.title              // ← 컴포지션 중 상태 변경
    viewModel.wishDescriptionState.value = wish.value.description
} else {
    viewModel.wishTitleState.value = ""
    viewModel.wishDescriptionState.value = ""
}
// ...
Button(onClick = {
    if (title.isNotEmpty() && description.isNotEmpty()) {
        if (id != 0L) viewModel.updateWish(Wish(id, title, description))
        else viewModel.addWish(Wish(title = title, description = description))
        snackMessage.value = "Success save wish list"
    } else {
        snackMessage.value = "Enter fields to create a wish"
    }
    scope.launch {
        snackbarHostState.showSnackbar(snackMessage.value)
        navController.navigateUp()
        snackMessage.value = ""
    }
})
```

## 질문 전제 점검

- **"캡슐화하는 게 낫지 않은가"** → **맞다. 그리고 지금 코드는 캡슐화가 안 된 정도가 아니라 실제 버그가 있다.**

  ```kotlin
  viewModel.wishTitleState.value = wish.value.title
  ```

  이 줄은 **컴포저블 본문에서 실행된다.** 즉 **컴포지션 중에 상태를 쓰고 있다.** Compose에서 금지된 패턴이다.

  > "The UI layer should never change state outside of an event handler because this can introduce inconsistencies and bugs in your application."

  무슨 일이 벌어지냐면 이렇다.

  ```
  1. 컴포지션 시작
  2. wishTitleState.value = "..." 대입 → 상태 변경
  3. 그 상태를 읽는 WishTextField 가 무효화
  4. 재구성
  5. 다시 2번 대입 → 또 변경...
  ```

  값이 같으면 Compose가 재구성을 건너뛰어 멈추지만, **`Flow`가 새 값을 내보낼 때마다 이 순환이 다시 돈다.** 실제 증상은 더 고약하다. **사용자가 타이핑하는 중에 DB 값으로 덮어쓰인다.** `getWishById(id)`가 `Flow`라 계속 구독되고 있기 때문이다.

- **"한 번에 처리하면 되지 않나"** → 그렇다. `ViewModel`이 스스로 하면 된다. 아래에서 구조를 정리한다.

- **"비동기로 실행되는데 언제 실행되나? `addWish`가 끝나면?"** → **아니다. 기다리지 않는다.**

  ```kotlin
  viewModel.addWish(...)      // 코루틴을 띄우고 즉시 반환
  scope.launch { ... }        // 곧바로 이어서 실행된다
  ```

  `addWish`는 내부에서 `viewModelScope.launch(Dispatchers.IO) { ... }`를 하고 **즉시 돌아온다.** DB 저장이 끝났는지 알려 주는 것이 없다. 그래서 두 코루틴이 **동시에** 돈다.

  ```
  t=0ms   버튼 클릭
  t=0ms   viewModel.addWish()  → IO 코루틴 시작, 즉시 반환
  t=0ms   scope.launch { }     → 스낵바 표시 시작
          ─────────────────────────────
          두 작업이 나란히 진행된다
  ```

  **"저장 성공"이라는 메시지를 저장 결과와 무관하게 띄우고 있다.** 실패해도 성공이라고 나온다.

- **"`else` 구문인 경우에는 비동기 요청 없이 바로 실행되는 건가"** → **그렇다. 그리고 그게 문제의 일부다.**

  `scope.launch { }`가 `if/else` **바깥**에 있어서, 입력이 비어 있을 때도 실행된다. 그 자체는 의도한 것으로 보인다(경고 메시지를 띄워야 하니까). 그런데 그 블록 안에 `navController.navigateUp()`이 들어 있다.

  ```kotlin
  scope.launch {
      snackbarHostState.showSnackbar(snackMessage.value)
      navController.navigateUp()          // ← 입력이 비어 있어도 화면을 나간다
      snackMessage.value = ""
  }
  ```

  **"필드를 입력하세요"라고 말하고는 화면을 닫아 버린다.** 사용자는 입력하려고 해도 화면이 없다.

## 공부할 내용

### 문제 ① 컴포지션 중 상태 변경

```kotlin
// 지금
if (id != 0L) {
    val wish = viewModel.getWishById(id).collectAsState(initial = Wish(0L, "", ""))
    viewModel.wishTitleState.value = wish.value.title
}
```

**부수 효과는 효과 API 안에서 해야 한다.** → [`059-compose-launchedeffect-and-snapshotflow.md`](059-compose-launchedeffect-and-snapshotflow.md)

```kotlin
// 최소 수정
LaunchedEffect(id) {
    if (id != 0L) {
        val wish = viewModel.getWishById(id).first()   // 한 번만 읽는다
        viewModel.onWishTitleChange(wish.title)
        viewModel.onWishDescriptionState(wish.description)
    } else {
        viewModel.onWishTitleChange("")
        viewModel.onWishDescriptionState("")
    }
}
```

`collectAsState` 대신 **`first()`로 한 번만 읽는 것**이 핵심이다. 편집 화면에서는 DB를 계속 구독할 이유가 없다. 계속 구독하면 사용자의 입력을 DB 값이 덮어쓴다. → [`057-kotlin-flow-concepts-and-suspend.md`](057-kotlin-flow-concepts-and-suspend.md)

### 문제 ② 캡슐화 — `ViewModel`이 해야 할 일

지금은 화면이 `ViewModel`의 상태를 **직접 대입**한다.

```kotlin
viewModel.wishTitleState.value = wish.value.title   // 화면이 ViewModel 내부를 건드린다
```

`ViewModel`이 스스로 하면 화면이 이 사정을 몰라도 된다.

```kotlin
class WishViewModel(private val repository: WishRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(WishFormUiState())
    val uiState: StateFlow<WishFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<WishEvent>()
    val events = _events.receiveAsFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            if (id == 0L) {
                _uiState.value = WishFormUiState()
            } else {
                val wish = repository.getWishById(id).first()
                _uiState.value = WishFormUiState(id = id, title = wish.title, description = wish.description)
            }
        }
    }

    fun onTitleChange(value: String) { _uiState.update { it.copy(title = value) } }
    fun onDescriptionChange(value: String) { _uiState.update { it.copy(description = value) } }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank() || state.description.isBlank()) {
            viewModelScope.launch { _events.send(WishEvent.ShowMessage("Enter fields to create a wish")) }
            return
        }
        viewModelScope.launch {
            val wish = Wish(state.id, state.title.trim(), state.description.trim())
            if (state.id == 0L) repository.addWish(wish) else repository.updateWish(wish)
            _events.send(WishEvent.SavedAndClose("Success save wish list"))   // ← 저장이 끝난 뒤
        }
    }
}

data class WishFormUiState(val id: Long = 0L, val title: String = "", val description: String = "")

sealed interface WishEvent {
    data class ShowMessage(val text: String) : WishEvent
    data class SavedAndClose(val text: String) : WishEvent
}
```

화면은 **상태를 보여 주고 이벤트를 올리기만** 한다.

```kotlin
@Composable
fun AddEditDetailView(id: Long, navController: NavHostController, viewModel: WishViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(id) { viewModel.load(id) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WishEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                is WishEvent.SavedAndClose -> {
                    navController.navigateUp()                     // 먼저 나가고
                    snackbarHostState.showSnackbar(event.text)     // 스낵바는 목록 화면에서
                }
            }
        }
    }

    Scaffold(
        topBar = { AppBarView(title = if (id != 0L) "Update Wish" else "Add Wish") { navController.navigateUp() } },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            WishTextField("Title", uiState.title, viewModel::onTitleChange)
            WishTextField("Description", uiState.description, viewModel::onDescriptionChange)
            Button(onClick = viewModel::save) { Text(if (id != 0L) "Update Wish" else "Add Wish") }
        }
    }
}
```

**무엇이 달라졌나.**

| | 지금 | 개선 |
| --- | --- | --- |
| 검증 로직 위치 | 화면 | `ViewModel` |
| 스낵바 메시지 결정 | 화면 | `ViewModel` (이벤트) |
| 저장 완료 시점 | 모른다 | 이벤트로 알려 준다 |
| 상태 변경 주체 | 화면이 직접 대입 | `ViewModel`만 |
| 빈 입력 시 화면 이동 | 나간다 (버그) | 안 나간다 |
| 테스트 | 화면이 필요 | `ViewModel`만으로 가능 |

공식 베스트 프랙티스가 말하는 이유와 같다.

> - Business logic is easier to test
> - Coroutines survive configuration changes automatically
> - Views trigger UI logic only

### 문제 ③ `showSnackbar`는 기다린다

```kotlin
scope.launch {
    snackbarHostState.showSnackbar(snackMessage.value)   // 스낵바가 사라질 때까지 중단
    navController.navigateUp()                           // 그 다음에 화면 이동
}
```

`showSnackbar()`는 **`suspend` 함수이고 스낵바가 닫힐 때까지 반환하지 않는다.** `SnackbarDuration.Short`가 기본이므로 약 4초다.

**즉 저장 버튼을 누르고 약 4초 동안 편집 화면에 머문다.** 사용자는 "저장이 안 됐나?" 하고 다시 누를 수 있다.

그리고 화면을 나가면 이 컴포저블이 컴포지션을 떠나므로 `rememberCoroutineScope()`가 취소된다. **마지막 줄 `snackMessage.value = ""`는 실행되지 않을 수 있다.** → [`053-kotlin-coroutine-scope-concept.md`](053-kotlin-coroutine-scope-concept.md)

순서를 바꾸는 것만으로 크게 나아진다.

```kotlin
navController.navigateUp()                       // 먼저 나가고
snackbarHostState.showSnackbar(message)          // 스낵바는 목록 화면에서
```

### 문제 ④ 저장 결과를 모른다

```kotlin
fun addWish(wish: Wish) {
    viewModelScope.launch(Dispatchers.IO) {
        wishRepository.addWish(wish = wish)      // 성공/실패를 알려 주지 않는다
    }
}
```

**호출자는 저장이 끝났는지, 실패했는지 알 수 없다.** 예외가 나면 코루틴이 조용히 죽거나 앱이 크래시한다. → [`055-kotlin-coroutines-exception-handling-try-catch.md`](055-kotlin-coroutines-exception-handling-try-catch.md)

완료를 알려면 두 가지 방법이 있다.

```kotlin
// ① suspend 함수로 만들고 호출자가 기다린다
suspend fun addWish(wish: Wish) = repository.addWish(wish)
// 하지만 ViewModel 이 suspend 함수를 노출하는 것은 권장되지 않는다

// ② 이벤트/상태로 알린다  ★ 권장
fun save() {
    viewModelScope.launch {
        runCatching { repository.addWish(wish) }
            .onSuccess { _events.send(WishEvent.SavedAndClose("저장했습니다")) }
            .onFailure { _events.send(WishEvent.ShowMessage("저장에 실패했습니다")) }
    }
}
```

## 관련 아키텍처와 베스트 프랙티스

### 단방향 데이터 흐름

```
        상태(State)                    이벤트(Event)
ViewModel ──────────▶ Composable ──────────▶ ViewModel
   ▲                                              │
   └──────────────────────────────────────────────┘
```

**화면은 상태를 읽고 이벤트를 올린다. 상태를 직접 쓰지 않는다.** 지금 코드는 이 화살표가 역류하고 있다.

```kotlin
viewModel.wishTitleState.value = ...     // 역류
```

### 상태를 불변으로 노출한다

```kotlin
// 지금: 밖에서 누구나 바꿀 수 있다
var wishTitleState = mutableStateOf("")

// 개선: 읽기 전용으로 노출하고 변경은 함수로만
private val _uiState = MutableStateFlow(WishFormUiState())
val uiState: StateFlow<WishFormUiState> = _uiState.asStateFlow()
fun onTitleChange(value: String) { _uiState.update { it.copy(title = value) } }
```

**`var`와 `public`이 함께 있으면 캡슐화가 아니다.** 질문에서 느낀 위화감의 정체가 이것이다.

### 일회성 이벤트는 상태가 아니다

```kotlin
// 문제: 회전하면 스낵바가 다시 뜬다
val message by viewModel.message.collectAsStateWithLifecycle()
if (message != null) LaunchedEffect(message) { showSnackbar(message) }

// 대안: Channel 은 한 번 소비되면 사라진다
private val _events = Channel<WishEvent>()
val events = _events.receiveAsFlow()
```

"스낵바 표시", "화면 닫기" 같은 것은 **상태가 아니라 사건**이다.

### 편집 화면은 자기 `ViewModel`을 갖는 편이 낫다

```kotlin
// 지금: 목록과 편집이 같은 WishViewModel 을 공유
composable(Screen.AddScreen.route + "/{id}") { AddEditDetailView(id, navController, viewModel) }
```

목록 화면용 `getAllWishes`와 편집 화면용 폼 상태가 한 클래스에 섞여 있다. 화면마다 `ViewModel`을 두면 각자의 관심사만 갖는다. `viewModel()`을 각 `composable` 안에서 부르면 **그 화면의 `NavBackStackEntry`에 스코프된다.** → [`069-android-navigation-backstackentry-savedstatehandle.md`](069-android-navigation-backstackentry-savedstatehandle.md)

다만 강의 흐름상 하나로 가는 것이라면, 최소한 **폼 상태를 화면 진입 시 초기화**하는 것은 반드시 필요하다. 지금은 그 초기화가 컴포지션 중에 일어나고 있다.

### 저장이 화면 수명보다 오래 살아야 한다면

"저장 중에 화면을 나가도 저장은 끝나야 한다"면 `viewModelScope`도 부족할 수 있다. `ViewModel`이 정리되면 취소되기 때문이다. 주입된 앱 수명 스코프를 쓴다. → [`056-android-viewmodelscope-launch-necessity.md`](056-android-viewmodelscope-launch-necessity.md)

## 체크리스트

- [ ] 컴포지션 중에 상태를 변경하면 안 되는 이유를 설명할 수 있다.
- [ ] `collectAsState`로 계속 구독하면 사용자 입력이 덮어쓰이는 이유를 안다.
- [ ] 편집 화면에서 `first()`로 한 번만 읽어야 하는 이유를 설명할 수 있다.
- [ ] `viewModel.state.value = ...`가 단방향 흐름을 역류시킨다는 것을 안다.
- [ ] `var` + `public` 상태가 캡슐화가 아닌 이유를 설명할 수 있다.
- [ ] `addWish()`가 즉시 반환한다는 것과 그 결과를 알 수 없다는 것을 안다.
- [ ] 스낵바가 저장 결과와 무관하게 뜨는 문제를 지적할 수 있다.
- [ ] `showSnackbar()`가 중단 함수라 화면 이동이 늦어지는 것을 안다.
- [ ] 빈 입력일 때도 화면을 나가는 버그를 설명할 수 있다.
- [ ] 일회성 이벤트를 상태가 아니라 `Channel`로 다루는 이유를 안다.
- [ ] 검증 로직을 `ViewModel`에 두면 테스트가 쉬워지는 이유를 안다.
- [ ] 화면마다 `ViewModel`을 두는 선택의 장단점을 말할 수 있다.

## 공식 참고 자료

- [Android Developers: Architecting your Compose UI](https://developer.android.com/develop/ui/compose/architecture)
- [Android Developers: UI layer architecture](https://developer.android.com/topic/architecture/ui-layer)
- [Android Developers: UI events](https://developer.android.com/topic/architecture/ui-layer/events)
- [Android Developers: State holders and UI State](https://developer.android.com/topic/architecture/ui-layer/stateholders)
- [Android Developers: Best practices for coroutines in Android](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Android Developers: Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
