# `viewModelScope.launch`로 감싸야 하는 이유

## 질문이 나온 코드

- [`chapter143/app/src/main/java/com/example/chapter_143/MainViewModel.kt`](../chapter143/app/src/main/java/com/example/chapter_143/MainViewModel.kt)
- [`chapter205/app/src/main/java/com/example/chapter_205/WishViewModel.kt`](../chapter205/app/src/main/java/com/example/chapter_205/WishViewModel.kt)
- 질문: 코루틴 API 요청을 백그라운드에서 실행하려면 `viewModelScope.launch` 안에 로직을 넣어야 하는가? 별도의 `launch`가 필요한 이유는 무엇이고, 그냥 실행하면 어떤 문제가 생기는가?

```kotlin
private fun fetchCategories() {
    viewModelScope.launch {
        try {
            val response = recipieService.getCategories()
            _categoriesState.value = _categoriesState.value.copy(...)
        } catch (e: Exception) { ... }
    }
}
```

## 질문 전제 점검

- **"백그라운드에서 실행하기 위해 `viewModelScope.launch`가 필요하다"** → 이 전제가 틀렸다. **`launch`는 스레드를 바꾸지 않는다.** `viewModelScope`의 기본 디스패처는 `Dispatchers.Main.immediate`이고, 그래서 저 블록 안의 코드는 **메인 스레드에서 시작한다**. 로그를 찍어 보면 바로 확인된다.

  ```kotlin
  viewModelScope.launch {
      println(Thread.currentThread().name)   // main
  }
  ```

  네트워크 I/O를 실제로 백그라운드에서 돌리는 것은 `launch`가 아니라 **Retrofit/OkHttp**다. OkHttp가 자기 스레드 풀에서 요청을 처리하고, 응답이 오면 코루틴을 다시 메인 스레드에서 재개한다. → [`052-kotlin-coroutines-continuation-state-machine.md`](052-kotlin-coroutines-continuation-state-machine.md)

- **그러면 `launch`는 왜 필요한가** → 이유가 두 가지인데, 둘 다 "백그라운드"와는 무관하다.

  1. **문법적 강제** — `suspend` 함수는 코루틴 안에서만 호출할 수 있다. `launch` 없이 쓰면 컴파일 자체가 안 된다.
  2. **생명주기 연결** — `viewModelScope`는 `ViewModel`이 정리될 때 그 안의 코루틴을 전부 취소한다. 이게 진짜 이유다.

- **"그냥 실행하면 어떤 문제가 발생하나"** → 실행조차 되지 않는다. **컴파일 에러**다. 런타임에 무언가 잘못되는 문제가 아니다.

  ```
  Suspend function 'getCategories' should be called only from a coroutine or another suspend function.
  ```

  "문제가 생긴다"가 아니라 "애초에 컴파일러가 막는다"가 정확한 답이다. 그래서 진짜로 따져 볼 질문은 **"`viewModelScope.launch` 말고 다른 방법을 쓰면 어떻게 되는가"**이고, 여기서 차이가 드러난다.

## 공부할 내용

### 왜 컴파일러가 막는가

`suspend` 함수는 컴파일 단계에서 `Continuation` 파라미터를 하나 더 받는 함수로 바뀐다. 그 `Continuation`을 만들어 줄 사람이 필요하다. 일반 함수 안에는 그런 것이 없다.

```kotlin
// 우리가 쓴 것
suspend fun getCategories(): CategoriesResponse

// 컴파일된 것
Object getCategories(Continuation<CategoriesResponse> completion)
                                  //  ↑ 이걸 누가 넘겨주나?
```

`launch { ... }`가 바로 그 `Continuation`을 만들어 주는 주체다. 그래서 코루틴 빌더 없이는 `suspend` 함수를 호출할 수 없다.

### 대안들과 각각의 문제

#### ① `runBlocking` — 최악

```kotlin
private fun fetchCategories() {
    runBlocking {                            // 컴파일은 된다
        val response = recipieService.getCategories()
        ...
    }
}
```

컴파일은 통과하지만 **메인 스레드를 네트워크 응답이 올 때까지 통째로 막는다**. `ViewModel`의 `init`에서 이걸 하면 화면이 그려지기도 전에 멈추고, 응답이 느리면 ANR(Application Not Responding)로 앱이 죽는다. `runBlocking`은 `main()` 함수나 테스트 코드처럼 **코루틴 세계로 들어가는 다리**가 필요할 때만 쓴다.

#### ② `GlobalScope.launch` — 누수

```kotlin
private fun fetchCategories() {
    GlobalScope.launch { ... }               // 동작은 한다
}
```

동작은 한다. 문제는 **아무도 이 코루틴을 취소하지 않는다**는 점이다. 사용자가 화면을 나가 `ViewModel`이 정리된 뒤에도 요청은 계속 진행되고, 응답이 오면 이미 버려진 `ViewModel`의 상태를 갱신한다. 이 작업이 `ViewModel`을 참조하고 있으므로 **`ViewModel`이 GC되지 못한다.** 공식 문서가 `GlobalScope`를 피하라고 하는 이유다.

#### ③ `fetchCategories()`를 `suspend`로 만들기 — 책임 전가

```kotlin
suspend fun fetchCategories() { ... }        // 호출자에게 떠넘긴다
```

문제가 사라지는 게 아니라 **위로 밀린다**. 이제 컴포저블이 `LaunchedEffect`로 감싸야 하고, `ViewModel`은 자기 일을 스스로 못 하는 반쪽짜리가 된다. 공식 베스트 프랙티스가 이것을 명시적으로 금지한다.

> ❌ DON'T: Expose suspend functions from ViewModel
> ```kotlin
> suspend fun loadNews() = getLatestNewsWithAuthors()
> ```

이유는 셋이다.

> - Business logic is easier to test
> - Coroutines survive configuration changes automatically
> - Views trigger UI logic only

가운데 항목이 특히 중요하다. **`ViewModel`에서 시작한 코루틴은 화면 회전을 견딘다.** 컴포저블 스코프에서 시작했다면 회전할 때마다 요청이 다시 나간다.

#### ④ `viewModelScope.launch` — 정답

```kotlin
viewModelScope.launch { ... }
```

권장 형태 그대로다.

```kotlin
// 공식 문서의 권장 패턴
class LatestNewsViewModel(private val getLatestNewsWithAuthors: GetLatestNewsWithAuthorsUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow<LatestNewsUiState>(LatestNewsUiState.Loading)
    val uiState: StateFlow<LatestNewsUiState> = _uiState

    fun loadNews() {
        viewModelScope.launch {
            val latestNewsWithAuthors = getLatestNewsWithAuthors()
            _uiState.value = LatestNewsUiState.Success(latestNewsWithAuthors)
        }
    }
}
```

### `viewModelScope`가 정확히 무엇인가

`lifecycle-viewmodel-ktx`가 `ViewModel`에 붙여 주는 확장 프로퍼티다.

> "`viewModelScope` is a predefined `CoroutineScope` that is included with the `ViewModel` KTX extensions. Note that all coroutines must run in a scope. A `CoroutineScope` manages one or more related coroutines."

내부 구성은 이렇다.

```kotlin
// 개념적으로
val ViewModel.viewModelScope: CoroutineScope
    get() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
```

두 조각이 각각 의미가 있다.

- **`Dispatchers.Main.immediate`** — 이미 메인 스레드에 있으면 큐를 거치지 않고 **바로 실행한다**. 그래서 상태 갱신이 한 프레임 늦게 반영되는 일이 없다.
- **`SupervisorJob`** — 자식 코루틴 하나가 실패해도 형제 코루틴과 스코프 전체가 죽지 않는다. 화면에서 여러 요청을 동시에 날릴 때 하나의 실패가 나머지를 쓸어버리지 않게 해 준다.

### 자동 취소가 해결해 주는 것

> "Since this coroutine is started with `viewModelScope`, it is executed in the scope of the `ViewModel`. If the `ViewModel` is destroyed because the user is navigating away from the screen, `viewModelScope` is automatically cancelled, and all running coroutines are canceled as well."

`ViewModel.onCleared()`가 불릴 때 스코프의 `Job`이 취소되고, 그 순간 진행 중이던 코루틴은 **다음 중단점에서 `CancellationException`을 만나 멈춘다**. Retrofit은 `invokeOnCancellation`으로 HTTP 요청 자체도 취소한다.

이것이 없으면 생기는 일들이다.

- 화면을 나갔는데 네트워크 요청이 계속 돌아 배터리와 데이터를 쓴다.
- 응답이 와서 버려진 `ViewModel`의 상태를 갱신한다.
- 사라진 객체를 참조한 채로 메모리에 남는다.

### 이 코드에서 `init`에 둔 선택

```kotlin
init {
    fetchCategories()
}
```

`ViewModel`이 만들어질 때 한 번만 호출된다. `ViewModel`은 **화면 회전을 견디고 살아남으므로**, 회전해도 요청이 다시 나가지 않는다. 컴포저블에서 직접 불렀다면 재구성마다 호출되었을 것이다.

다만 `init` 블록에서 작업을 시작하는 것이 언제나 좋은 것은 아니다. 테스트에서 인스턴스를 만드는 순간 네트워크가 나가므로 제어가 어려워진다. 규모가 커지면 **화면이 요청하는 시점에 `load()`를 부르는 형태**가 다루기 쉽다.

## 관련 아키텍처와 베스트 프랙티스

### 코루틴을 어디서 시작할 것인가

```
컴포저블        코루틴을 시작하지 않는다. 이벤트만 ViewModel로 올린다
   ↓
ViewModel       viewModelScope.launch 로 코루틴을 시작한다  ★ 시작 지점
   ↓
Repository      suspend fun 을 노출한다. 코루틴을 시작하지 않는다
   ↓
DataSource      suspend fun 을 노출한다. main-safe를 보장한다
```

**아래 계층은 `suspend fun`만 노출하고, 코루틴을 시작하는 것은 `ViewModel` 한 곳**이라는 원칙이다. 그래야 취소 신호가 위에서 아래로 한 줄기로 흐른다.

예외는 컴포저블이 **화면에 종속된** 부수 효과를 다룰 때다. 이때는 `LaunchedEffect`나 `rememberCoroutineScope()`를 쓴다.

```kotlin
// 화면에 종속된 작업: 스낵바 표시, 스크롤 이동
val scope = rememberCoroutineScope()
Button(onClick = { scope.launch { listState.animateScrollToItem(0) } }) { ... }
```

### 스코프 선택표

| 스코프 | 취소 시점 | 용도 |
| --- | --- | --- |
| `viewModelScope` | `ViewModel` 정리 시 | 화면 데이터 로딩 — **기본 선택** |
| `lifecycleScope` | 액티비티/프래그먼트 소멸 시 | 화면 자체에 묶인 작업 |
| `LaunchedEffect` | 컴포저블이 컴포지션을 떠날 때 | 컴포저블 진입 시 1회 실행 |
| `rememberCoroutineScope()` | 컴포저블이 컴포지션을 떠날 때 | 콜백에서 코루틴 시작 |
| `GlobalScope` | 취소되지 않음 | **쓰지 않는다** |
| 주입된 `CoroutineScope` | 직접 관리 | 앱 수명만큼 사는 작업 |

### `suspend`를 붙이면 백그라운드가 된다는 오해 정리

다시 한번 정리하면 역할이 이렇게 나뉜다.

| 하는 일 | 담당 |
| --- | --- |
| 중단 가능함을 표시 | `suspend` 키워드 |
| 코루틴을 시작 | `launch` / `async` |
| 실행할 스레드를 결정 | `CoroutineDispatcher` / `withContext` |
| 생명주기에 묶기 | `CoroutineScope` |

`viewModelScope.launch`는 이 중 **2번과 4번**을 한다. 3번은 하지 않는다. → [`051-kotlin-coroutines-suspend-and-event-loop.md`](051-kotlin-coroutines-suspend-and-event-loop.md)

### `launch`는 결과를 돌려주지 않는다

`launch`가 반환하는 것은 `Job`이지 결과값이 아니다. 그래서 이 패턴에서는 **결과를 반환하는 대신 상태를 갱신한다**.

```kotlin
viewModelScope.launch {
    val response = recipieService.getCategories()
    _categoriesState.value = ...              // 반환이 아니라 상태 갱신
}
```

결과가 필요하면 `async`/`await`를 쓴다. 다만 `ViewModel`에서 UI 상태를 다룰 때는 상태 갱신 쪽이 자연스럽다. `launch`가 즉시 반환한다는 성질은 예외 처리에도 영향을 준다. → [`055-kotlin-coroutines-exception-handling-try-catch.md`](055-kotlin-coroutines-exception-handling-try-catch.md)

## 체크리스트

- [ ] `viewModelScope.launch`가 스레드를 백그라운드로 바꾸지 않는다는 것을 설명할 수 있다.
- [ ] `viewModelScope`의 기본 디스패처가 `Dispatchers.Main.immediate`임을 안다.
- [ ] `launch` 없이 `suspend` 함수를 부르면 컴파일 에러가 나는 이유를 설명할 수 있다.
- [ ] `runBlocking`을 쓰면 왜 ANR로 이어지는지 설명할 수 있다.
- [ ] `GlobalScope`가 만드는 누수 시나리오를 설명할 수 있다.
- [ ] `ViewModel`이 `suspend fun`을 노출하면 안 되는 이유 세 가지를 말할 수 있다.
- [ ] `SupervisorJob`이 `viewModelScope`에 들어 있는 이유를 설명할 수 있다.
- [ ] `ViewModel`이 정리될 때 코루틴과 HTTP 요청이 어떻게 취소되는지 설명할 수 있다.
- [ ] 코루틴을 시작할 스코프를 상황에 맞게 고를 수 있다.

## 공식 참고 자료

- [Android Developers: Best practices for coroutines in Android](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Android Developers: Use Kotlin coroutines with lifecycle-aware components](https://developer.android.com/topic/libraries/architecture/coroutines)
- [Android Developers: Kotlin coroutines on Android](https://developer.android.com/kotlin/coroutines)
- [Android Developers: ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Android Developers: UI layer architecture](https://developer.android.com/topic/architecture/ui-layer)
- [Kotlin Docs: Coroutine context and dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)
