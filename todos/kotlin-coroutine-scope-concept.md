# 코루틴 스코프란 무엇인가

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/AddEditDetailView.kt`](../chapter205/app/src/main/java/com/example/chapter_205/AddEditDetailView.kt)
- 질문: 코루틴 스코프가 어떤 개념인가? 정리해 달라.

```kotlin
val scope = rememberCoroutineScope()
// ...
Button(onClick = {
    scope.launch {
        snackbarHostState.showSnackbar(snackMessage.value)
        navController.navigateUp()
    }
})
```

## 질문 전제 점검

- **스코프를 "코루틴을 실행하는 실행기"로 이해하면 절반만 맞다.** `CoroutineScope`가 실제로 하는 일은 실행이 아니라 **소유와 취소**다. 인터페이스를 보면 멤버가 하나뿐이다.

  ```kotlin
  interface CoroutineScope {
      val coroutineContext: CoroutineContext
  }
  ```

  **컨텍스트를 들고 있는 것이 전부다.** `launch`와 `async`는 이 인터페이스의 확장 함수이고, 스코프의 컨텍스트에서 `Job`을 꺼내 **부모-자식 관계를 맺는 데** 쓴다.

  ```
  CoroutineScope  =  Job(생명주기) + Dispatcher(실행 위치) + 그 밖의 컨텍스트
  ```

- **그래서 스코프의 본질은 "언제 취소되는가"다.** 스코프마다 이름이 다른 이유가 전부 여기 있다.

  | 스코프 | 언제 취소되는가 |
  | --- | --- |
  | `viewModelScope` | `ViewModel`이 정리될 때 |
  | `lifecycleScope` | 액티비티·프래그먼트가 소멸할 때 |
  | `rememberCoroutineScope()` | 컴포저블이 컴포지션을 떠날 때 |
  | `GlobalScope` | **취소되지 않는다** |

  `rememberCoroutineScope()`를 쓴다는 것은 **"이 화면이 사라지면 이 작업도 멈춰라"**라고 선언하는 것이다.

- **그리고 이 코드에는 스코프 선택이 만드는 실제 문제가 있다.** 아래 블록을 보자.

  ```kotlin
  scope.launch {
      snackbarHostState.showSnackbar(snackMessage.value)   // 스낵바가 닫힐 때까지 중단
      navController.navigateUp()                           // 그 다음 화면 이동
  }
  ```

  `navigateUp()`으로 **화면이 사라지면 이 컴포저블도 컴포지션을 떠난다.** 즉 자기 자신을 취소시키는 코루틴이다. 마지막 줄 `snackMessage.value = ""`는 실행되지 않을 수 있다. 지금은 해가 없지만 구조적으로는 위태롭다. → [`android-viewmodel-encapsulation-and-async-timing.md`](android-viewmodel-encapsulation-and-async-timing.md)

## 공부할 내용

### 구조적 동시성 — 스코프가 존재하는 이유

스코프가 없다면 이런 일이 생긴다.

```kotlin
// 스코프 없이 코루틴을 띄운다면
GlobalScope.launch {
    val data = api.load()        // 3초 걸림
    updateUi(data)               // 화면은 이미 사라졌는데?
}
```

**아무도 이 작업을 멈출 수 없다.** 사용자가 화면을 나가도 네트워크는 계속 돌고, 응답이 오면 사라진 화면의 상태를 건드린다.

스코프는 **부모-자식 관계**를 만들어 이 문제를 푼다.

```kotlin
fun main() = runBlocking {
    val parent = launch {
        launch { delay(1000); println("자식 1") }
        launch { delay(1000); println("자식 2") }
    }
    delay(100)
    parent.cancel()          // 부모를 취소하면
    delay(2000)
    println("끝")            // 자식들은 찍히지 않는다
}
```

세 가지 규칙이 따라온다.

1. **부모는 모든 자식이 끝날 때까지 완료되지 않는다**
2. **부모가 취소되면 자식도 전부 취소된다**
3. **자식이 실패하면 부모로 전파된다**(`SupervisorJob`이면 예외)

→ [`kotlin-coroutines-suspend-and-event-loop.md`](kotlin-coroutines-suspend-and-event-loop.md)

### `rememberCoroutineScope()`

> **정의**: 컴포저블 외부에서 코루틴을 실행하되, Composition과 연결된 스코프 반환
>
> - 호출 지점의 Composition 생명주기에 바인딩
> - 수동으로 코루틴 생명주기 제어 가능
> - Composition을 떠날 때 자동 취소

공식 문서의 예제가 지금 코드와 거의 같다.

```kotlin
@Composable
fun MoviesScreen(snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { contentPadding ->
        Column(Modifier.padding(contentPadding)) {
            Button(
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Something happened!")
                    }
                }
            ) {
                Text("Press me")
            }
        }
    }
}
```

**왜 여기서 스코프가 필요한가?** `onClick`은 컴포저블이 아니라 **일반 람다**다. `suspend` 함수인 `showSnackbar()`를 직접 부를 수 없다.

```kotlin
Button(onClick = {
    snackbarHostState.showSnackbar("...")   // ❌ 컴파일 에러
})
```

`rememberCoroutineScope()`가 **컴포저블 세계와 콜백 세계를 잇는 다리**다.

### `LaunchedEffect`와 언제 나눠 쓰나

둘 다 컴포지션에 묶인 코루틴이지만 계기가 다르다.

```kotlin
// LaunchedEffect: 컴포저블이 "화면에 들어오면" 자동 실행
LaunchedEffect(userId) {
    viewModel.load(userId)
}

// rememberCoroutineScope: "사용자가 무언가 하면" 실행
val scope = rememberCoroutineScope()
Button(onClick = { scope.launch { listState.animateScrollToItem(0) } })
```

| | `LaunchedEffect` | `rememberCoroutineScope()` |
| --- | --- | --- |
| 실행 계기 | 컴포지션 진입 / 키 변경 | **직접 `launch`를 호출할 때** |
| 쓸 수 있는 곳 | 컴포저블 본문 | 콜백, 이벤트 핸들러 |
| 재시작 | 키가 바뀌면 자동 | 없음 |

**"화면에 들어오면 한 번"은 `LaunchedEffect`, "버튼을 누르면"은 `rememberCoroutineScope`**로 기억하면 거의 맞다. → [`compose-launchedeffect-and-snapshotflow.md`](compose-launchedeffect-and-snapshotflow.md)

### 스코프를 직접 만들기

```kotlin
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
```

두 조각이 들어간다.

- **`Job`** — 생명주기. `scope.cancel()`로 전부 취소
- **`Dispatcher`** — 실행 스레드

`SupervisorJob`을 쓰면 **자식 하나가 실패해도 형제와 스코프가 죽지 않는다.** `viewModelScope`가 이 조합을 쓰는 이유다.

```kotlin
// viewModelScope 는 개념적으로
CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
```

직접 만들었다면 **반드시 취소해야 한다.**

```kotlin
class MyManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun close() { scope.cancel() }     // 안 하면 누수
}
```

**안드로이드에서 스코프를 직접 만들 일은 드물다.** 이미 생명주기에 묶인 스코프가 준비되어 있기 때문이다.

### `coroutineScope { }`와 `CoroutineScope()`는 다르다

이름이 비슷해 가장 헷갈리는 지점이다.

```kotlin
// ① CoroutineScope(...)  — 생성자. 새 스코프 객체를 만든다. 직접 취소해야 한다
val scope = CoroutineScope(Dispatchers.IO)

// ② coroutineScope { }   — suspend 함수. 블록 안의 모든 자식이 끝날 때까지 기다린다
suspend fun loadAll() = coroutineScope {
    val a = async { loadA() }
    val b = async { loadB() }
    a.await() + b.await()
}

// ③ supervisorScope { }  — ②와 같지만 자식 실패가 형제에게 전파되지 않는다
```

②는 **임시 스코프**다. 블록이 끝나면 사라지므로 취소를 신경 쓸 필요가 없다. `suspend` 함수 안에서 병렬 작업을 묶을 때 쓴다.

### 스코프 선택표

| 상황 | 스코프 |
| --- | --- |
| 화면 데이터 로딩, 저장 | `viewModelScope` |
| 컴포저블 진입 시 1회 실행 | `LaunchedEffect` |
| 버튼 클릭 등 콜백에서 실행 | `rememberCoroutineScope()` |
| `suspend` 함수 안에서 병렬 작업 | `coroutineScope { }` |
| 액티비티 수명에 묶인 작업 | `lifecycleScope` |
| 앱 수명만큼 사는 작업 | 주입된 `CoroutineScope` |
| 무엇이든 | ❌ `GlobalScope` |

### 이 코드에 적용한다면

스낵바를 띄우고 화면을 나가는 작업은 **화면에 종속**되므로 `rememberCoroutineScope()`가 맞는 선택이다. 다만 순서를 바꾸는 편이 낫다.

```kotlin
// 지금: 스낵바가 닫힐 때까지 기다린 뒤 이동 → 약 4초 멈춰 있다
scope.launch {
    snackbarHostState.showSnackbar(snackMessage.value)
    navController.navigateUp()
}

// 대안: 저장하고 바로 나간다. 스낵바는 목록 화면에서 띄운다
viewModel.addWish(...)
navController.navigateUp()
```

`showSnackbar()`는 **스낵바가 사라질 때까지 중단하는 `suspend` 함수**다. 이 성질을 모르면 "왜 화면 전환이 늦지?"에 답할 수 없다.

## 관련 아키텍처와 베스트 프랙티스

### 스코프는 소유자가 만든다

```
ViewModel   → viewModelScope 로 코루틴을 시작한다        ★ 시작 지점
Repository  → suspend fun 만 노출. 스코프를 만들지 않는다
DataSource  → suspend fun 만 노출
```

**아래 계층이 스코프를 만들면 취소 신호가 끊긴다.** 저장소가 `GlobalScope.launch`를 하면 `ViewModel`이 취소돼도 그 작업은 계속 돈다. → [`android-viewmodelscope-launch-necessity.md`](android-viewmodelscope-launch-necessity.md)

### 취소를 견디는 작업은 스코프를 따로 둔다

"화면을 나가도 저장은 끝나야 한다"면 화면 스코프에 두면 안 된다.

```kotlin
// 앱 수명 스코프를 주입받는다
class WishRepository(
    private val dao: WishDao,
    private val externalScope: CoroutineScope
) {
    fun addWishAndForget(wish: Wish) {
        externalScope.launch { dao.addWish(wish) }   // 화면이 사라져도 계속
    }
}
```

`GlobalScope` 대신 **주입 가능한 스코프**를 쓰는 것이 핵심이다. 테스트에서 갈아 끼울 수 있다.

### 스코프를 컴포저블 바깥으로 흘리지 않는다

```kotlin
// 나쁨: 스코프를 파라미터로 내려보낸다
@Composable
fun MyScreen(scope: CoroutineScope) { ... }

// 좋음: 필요한 곳에서 각자 만든다
@Composable
fun MyScreen() {
    val scope = rememberCoroutineScope()
}
```

스코프는 **호출 지점의 생명주기에 묶인다.** 남에게 넘기면 그 관계가 흐려진다.

### 취소는 협조적이다

스코프가 취소돼도 **중단점이 없는 코드는 멈추지 않는다.**

```kotlin
scope.launch {
    while (true) { heavyCompute() }      // 취소되지 않는다
}
scope.launch {
    while (isActive) { heavyCompute() }  // 취소된다
}
```

→ [`kotlin-coroutines-exception-handling-try-catch.md`](kotlin-coroutines-exception-handling-try-catch.md)

## 체크리스트

- [ ] `CoroutineScope` 인터페이스의 멤버가 하나뿐임을 안다.
- [ ] 스코프의 본질이 "실행"이 아니라 "소유와 취소"임을 설명할 수 있다.
- [ ] 스코프 = `Job` + `Dispatcher`라는 구성을 안다.
- [ ] 구조적 동시성의 세 가지 규칙을 말할 수 있다.
- [ ] `onClick`에서 `suspend` 함수를 직접 못 부르는 이유를 설명할 수 있다.
- [ ] `LaunchedEffect`와 `rememberCoroutineScope()`를 언제 나눠 쓰는지 안다.
- [ ] `CoroutineScope(...)`와 `coroutineScope { }`의 차이를 설명할 수 있다.
- [ ] `SupervisorJob`이 하는 일을 안다.
- [ ] 직접 만든 스코프는 반드시 취소해야 한다는 것을 안다.
- [ ] `showSnackbar()`가 중단 함수라서 생기는 타이밍 문제를 설명할 수 있다.
- [ ] 아래 계층이 스코프를 만들면 안 되는 이유를 안다.
- [ ] 취소가 협조적이라 중단점이 필요하다는 것을 안다.

## 공식 참고 자료

- [Kotlin Docs: Coroutine basics](https://kotlinlang.org/docs/coroutines-basics.html)
- [Kotlin Docs: Coroutine context and dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)
- [Kotlin Docs: Cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html)
- [Android Developers: Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects)
- [Android Developers: Use Kotlin coroutines with lifecycle-aware components](https://developer.android.com/topic/libraries/architecture/coroutines)
- [Android Developers: Best practices for coroutines in Android](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [kotlinx.coroutines API: `CoroutineScope`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/)
