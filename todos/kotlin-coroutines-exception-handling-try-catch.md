# 코루틴 안의 `try-catch`는 왜 필요한가

## 질문이 나온 코드

- [`chapter143/app/src/main/java/com/example/chapter_143/MainViewModel.kt`](../chapter143/app/src/main/java/com/example/chapter_143/MainViewModel.kt)
- 질문: `viewModelScope.launch` 안에서 `try-catch`로 예외를 삼킨 이유는 무엇인가? 예외 처리 없이 쓰면 안 되는가? `try-catch`가 없다면 예외가 앱을 크래시시킬 정도로 이전 스택에 던져지는가?

```kotlin
viewModelScope.launch {
    try {
        val response = recipieService.getCategories()
        _categoriesState.value = _categoriesState.value.copy(list = response.categories, loading = false, error = null)
    } catch (e: Exception) {
        _categoriesState.value = _categoriesState.value.copy(loading = false, error = "Error fetching categories, ${e.message}")
    }
}
```

## 질문 전제 점검

- **"예외를 삼킨다"** → 삼키는 게 아니다. `catch` 블록이 **예외를 UI 상태로 바꾸고 있다**. `error` 필드에 담긴 값은 `RecipeScreen`이 읽어 에러 화면을 그리는 데 쓰인다. 로그만 찍고 아무것도 안 하는 진짜 "삼키기"와는 다르다. 다만 **`e.message`만 남기고 예외 객체는 버리고 있어서**, 나중에 원인을 추적할 스택 트레이스가 사라진다. 이 부분은 개선 여지가 있다.

- **"예외가 이전 스택으로 던져지는가"** → **아니다.** 이것이 가장 중요한 정정이다. `launch`는 블록을 실행하지 않고 **즉시 반환한다**. `fetchCategories()`는 이미 끝났고, 그것을 부른 `init`도 끝났고, 호출 스택은 사라졌다. 그러니 `try-catch`가 없어도 아래 코드로는 **절대 잡히지 않는다**.

  ```kotlin
  // 이렇게 해도 잡히지 않는다
  try {
      viewModelScope.launch {
          recipieService.getCategories()   // 여기서 난 예외는
      }
  } catch (e: Exception) {
      // 여기로 오지 않는다
  }
  ```

  예외는 호출 스택이 아니라 **`Job` 계층(코루틴의 부모-자식 관계)을 따라 위로 전파된다**. 완전히 다른 경로다.

- **"앱이 크래시할 정도인가"** → **그렇다. 잡지 않으면 앱이 죽는다.** 다만 경로가 다르다. `launch`에서 잡히지 않은 예외는 부모 `Job`으로 올라가고, 처리해 줄 `CoroutineExceptionHandler`가 없으면 마지막에 **스레드의 uncaught exception handler**로 넘어간다. 안드로이드에서 그 지점은 곧 앱 종료다.

  ```
  FATAL EXCEPTION: main
  Process: com.example.chapter_143, PID: 12345
  java.net.UnknownHostException: Unable to resolve host "themealdb.com"
  ```

  그리고 네트워크는 **반드시 실패한다.** 비행기 모드, 지하철, 서버 점검, DNS 실패. `try-catch`가 없으면 이런 평범한 상황마다 앱이 죽는다. 그래서 이 `try-catch`는 선택이 아니다.

- **`catch (e: Exception)`은 이대로 두어도 되는가** → 여기에 실제 문제가 하나 있다. **`CancellationException`까지 잡아 버린다.** 공식 베스트 프랙티스가 명시적으로 경고하는 지점이다.

  > "Never catch or suppress `CancellationException` - always rethrow it to enable proper coroutine cancellation. Prefer catching specific exceptions over generic types like `Exception` or `Throwable`."

  사용자가 화면을 나가면 `viewModelScope`가 취소되고 중단점에서 `CancellationException`이 던져지는데, 지금 코드는 그것을 잡아서 **"Error fetching categories"라는 에러 상태로 바꿔 버린다.** 정상적인 취소가 에러로 둔갑한다.

## 공부할 내용

### 예외가 흐르는 경로

일반 함수와 코루틴은 예외가 가는 길이 다르다.

```
[ 일반 함수 ]
c() 에서 예외 → b() → a() → main()   (호출 스택을 거슬러 올라간다)

[ launch 코루틴 ]
자식 코루틴에서 예외
  → 부모 Job 으로 전파 (호출 스택과 무관)
  → 부모도 취소되고, 형제 코루틴도 취소됨
  → CoroutineExceptionHandler 가 있으면 여기서 처리
  → 없으면 Thread.uncaughtExceptionHandler → 앱 크래시
```

`launch`와 `async`의 차이를 보면 더 분명하다.

```kotlin
// launch: 예외를 즉시 던진다 (자동 전파)
val job = GlobalScope.launch {
    throw IndexOutOfBoundsException()
}
// 출력: Exception in thread "..." java.lang.IndexOutOfBoundsException

// async: 예외를 Deferred에 담아 둔다 (노출)
val deferred = GlobalScope.async {
    throw ArithmeticException()
}
try {
    deferred.await()        // await 할 때 비로소 튀어나온다
} catch (e: ArithmeticException) {
    println("Caught ArithmeticException")
}
```

`launch`는 **던지고**, `async`는 **보관했다가 `await()`에서 내놓는다**. `async`를 쓰고 `await()`를 안 하면 예외가 조용히 사라지는 것도 이 때문이다.

### 예외를 잡는 세 가지 위치

#### ① 코루틴 안쪽에서 `try-catch` — 지금 코드의 방식

```kotlin
viewModelScope.launch {
    try {
        val response = recipieService.getCategories()
        ...
    } catch (e: IOException) {
        ...
    }
}
```

공식 문서가 그대로 제시하는 패턴이다.

```kotlin
class LoginViewModel(private val loginRepository: LoginRepository) : ViewModel() {
    fun login(username: String, token: String) {
        viewModelScope.launch {
            try {
                loginRepository.login(username, token)
                // Update UI on success
            } catch (exception: IOException) {
                // Update UI on failure
            }
        }
    }
}
```

**실패를 화면에 보여 줘야 할 때 가장 적합하다.** 어떤 요청이 실패했는지 그 자리에서 알기 때문에, 그에 맞는 UI 상태를 만들 수 있다.

#### ② `CoroutineExceptionHandler` — 마지막 그물

```kotlin
val handler = CoroutineExceptionHandler { _, exception ->
    println("CoroutineExceptionHandler got $exception")
}

val job = GlobalScope.launch(handler) {
    throw AssertionError()          // handler가 받는다
}

val deferred = GlobalScope.async(handler) {
    throw ArithmeticException()     // handler는 무시된다
}
```

주의할 점이 둘이다.

- **`async`에는 동작하지 않는다.** `await()`로 직접 처리해야 한다.
- **자식 코루틴에 달면 동작하지 않는다.** 예외는 부모로 올라가므로, 핸들러는 **최상위 코루틴**이나 스코프에 달아야 한다.

UI 상태를 세밀하게 나눌 수 없으므로 "로깅하고 일반 에러 화면을 띄우는" 최후의 안전망 정도로 쓴다.

#### ③ `runCatching` / `Result` — 편하지만 함정이 있다

```kotlin
val result = runCatching { recipieService.getCategories() }   // ⚠️ 주의
```

`runCatching`은 **`Throwable`을 전부 잡는다.** `CancellationException`은 물론이고 `OutOfMemoryError`까지 잡는다. 코루틴 안에서는 되도록 쓰지 않거나, 쓴다면 취소 예외를 되던져야 한다.

```kotlin
suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e                                  // 취소는 반드시 통과시킨다
} catch (e: Exception) {
    Result.failure(e)
}
```

### `CancellationException`을 다시 던져야 하는 이유

취소는 **협조적**이다. 코루틴이 취소 신호를 스스로 확인하고 응답해야 한다. 그 신호가 곧 중단점에서 던져지는 `CancellationException`이다. 이것을 잡고 넘어가면 **코루틴은 취소되지 않은 것처럼 계속 진행한다.**

```kotlin
// 나쁨: 취소가 무시되고, 심지어 취소된 뒤에도 상태를 갱신한다
launch {
    try {
        delay(10_000)
    } catch (e: Exception) {
        println("에러!")        // 취소인데 에러로 기록된다
    }
    updateUi()                  // 취소되었는데도 실행된다
}

// 좋음
launch {
    try {
        delay(10_000)
    } catch (e: CancellationException) {
        throw e                 // 취소는 통과
    } catch (e: IOException) {
        println("진짜 에러")
    }
    updateUi()
}
```

정리 작업이 필요하면 `try-finally`를 쓴다.

```kotlin
launch {
    try {
        doWork()
    } finally {
        closeResources()        // 취소되어도 실행된다
    }
}
```

### 이 코드에 적용한다면

```kotlin
private fun fetchCategories() {
    viewModelScope.launch {
        _categoriesState.value = _categoriesState.value.copy(loading = true, error = null)
        try {
            val response = recipieService.getCategories()
            _categoriesState.value = _categoriesState.value.copy(
                list = response.categories, loading = false, error = null
            )
        } catch (e: CancellationException) {
            throw e                                      // ① 취소는 통과시킨다
        } catch (e: IOException) {                       // ② 네트워크 실패
            _categoriesState.value = _categoriesState.value.copy(
                loading = false, error = "네트워크에 연결할 수 없습니다"
            )
        } catch (e: HttpException) {                     // ③ 4xx/5xx 응답
            _categoriesState.value = _categoriesState.value.copy(
                loading = false, error = "서버 오류 (${e.code()})"
            )
        }
    }
}
```

바뀐 점이 셋이다.

1. **`CancellationException`을 되던진다** — 정상 취소가 에러로 표시되지 않는다.
2. **예외 종류를 구분한다** — 연결 실패와 서버 오류는 사용자에게 다른 메시지여야 한다.
3. **`e.message`를 그대로 보여주지 않는다** — `UnknownHostException: Unable to resolve host "themealdb.com"` 같은 문자열은 사용자에게 의미가 없다. 로그에는 예외 객체를 그대로 남기고, 화면에는 읽을 수 있는 문장을 보여 준다.

### 여러 자식이 동시에 실패하면

```kotlin
val handler = CoroutineExceptionHandler { _, exception ->
    println("CoroutineExceptionHandler got $exception")
    println("Suppressed: ${exception.suppressed.contentToString()}")
}

GlobalScope.launch(handler) {
    launch { delay(Long.MAX_VALUE) }
    launch { throw IOException() }
}
// CoroutineExceptionHandler got java.io.IOException
//   with suppressed [java.lang.ArithmeticException]
```

**첫 번째 예외가 우선**이고, 이후 예외들은 `suppressed`로 붙는다.

### 형제까지 죽이지 않으려면 — `supervisorScope`

```kotlin
supervisorScope {
    val child = launch(handler) {
        throw AssertionError()      // 형제에게 전파되지 않는다
    }
    val secondChild = launch {
        // 계속 실행된다
    }
}
```

일반 `coroutineScope`에서는 자식 하나가 실패하면 형제가 모두 취소된다. `supervisorScope`에서는 실패가 **아래로만** 전파된다. 대신 각 자식이 **자기 예외를 스스로 처리**해야 한다.

`viewModelScope`가 `SupervisorJob`을 갖는 것도 같은 이유다. 화면의 한 요청이 실패해도 다른 요청과 스코프 전체가 죽지 않는다. 그래서 **`viewModelScope.launch` 안의 `try-catch`는 더더욱 각자 챙겨야 한다.**

## 관련 아키텍처와 베스트 프랙티스

### 예외를 어느 계층에서 다룰 것인가

```
DataSource    예외를 그대로 던진다. 삼키지 않는다
   ↓
Repository    도메인 예외로 바꾸거나 Result로 감싼다 (선택)
   ↓
ViewModel     여기서 잡아 UI 상태로 바꾼다              ★ 주 처리 지점
   ↓
컴포저블      상태를 보고 화면만 그린다. try-catch 없음
```

`ViewModel`이 주 처리 지점인 이유는 **거기가 UI 상태를 만드는 유일한 곳**이기 때문이다. 컴포저블 안에서 `try-catch`를 쓰는 일은 없어야 한다.

### 에러를 상태로 모델링하기

지금 코드는 `loading`/`list`/`error` 세 필드를 한 데이터 클래스에 담고 있다. 동작하지만 **불가능한 조합이 표현된다**. `loading = true`면서 `error != null`인 상태를 컴파일러가 막지 못한다.

```kotlin
// 지금
data class RecipeState(val loading: Boolean, val list: List<Category> = emptyList(), val error: String? = null)

// 대안: sealed interface로 상태를 배타적으로 만든다
sealed interface RecipeUiState {
    data object Loading : RecipeUiState
    data class Success(val categories: List<Category>) : RecipeUiState
    data class Error(val message: String) : RecipeUiState
}
```

`when`이 모든 분기를 강제하므로 화면에서 빠뜨리는 경우가 없어진다. 다만 학습 단계에서는 지금 형태도 충분히 읽을 만하다.

### 사용자 메시지와 개발자 로그를 분리한다

```kotlin
catch (e: IOException) {
    Log.e(TAG, "카테고리 조회 실패", e)       // 개발자: 예외 객체 전체 (스택 트레이스 보존)
    _state.value = ... .copy(error = "네트워크에 연결할 수 없습니다")   // 사용자: 읽을 수 있는 문장
}
```

`e.message`만 화면에 흘려보내면 둘 다 잃는다. 사용자는 못 알아듣고, 개발자는 스택 트레이스를 못 본다.

### 재시도 경로를 남긴다

에러 화면에 "다시 시도" 버튼이 없으면 사용자는 앱을 껐다 켜야 한다.

```kotlin
fun retry() = fetchCategories()
```

### 잡지 말아야 할 것들

| 예외 | 잡아야 하나 | 이유 |
| --- | --- | --- |
| `IOException` | ✅ | 네트워크는 반드시 실패한다 |
| `HttpException` | ✅ | 4xx/5xx는 정상적인 응답 경로다 |
| `CancellationException` | ❌ 되던진다 | 취소 메커니즘이 망가진다 |
| `Exception` (통째로) | ⚠️ 피한다 | 취소 예외와 버그를 함께 가린다 |
| `Throwable` / `Error` | ❌ | `OutOfMemoryError` 같은 것은 복구 대상이 아니다 |
| `NullPointerException` | ❌ | 코드 버그다. 가리지 말고 고친다 |

## 체크리스트

- [ ] `launch`의 예외가 호출 스택이 아니라 `Job` 계층으로 전파된다는 것을 설명할 수 있다.
- [ ] `launch { }` 밖을 `try-catch`로 감싸도 소용없는 이유를 설명할 수 있다.
- [ ] `try-catch`가 없으면 앱이 크래시하는 경로를 설명할 수 있다.
- [ ] `launch`와 `async`의 예외 처리 방식 차이를 설명할 수 있다.
- [ ] `CancellationException`을 되던져야 하는 이유를 설명할 수 있다.
- [ ] `catch (e: Exception)`이 만드는 문제를 지적하고 고칠 수 있다.
- [ ] `CoroutineExceptionHandler`가 `async`와 자식 코루틴에서 동작하지 않는 이유를 안다.
- [ ] `runCatching`이 코루틴에서 위험한 이유를 설명할 수 있다.
- [ ] `supervisorScope`와 `coroutineScope`의 전파 차이를 설명할 수 있다.
- [ ] 사용자 메시지와 개발자 로그를 분리해 작성할 수 있다.

## 공식 참고 자료

- [Kotlin Docs: Coroutine exceptions handling](https://kotlinlang.org/docs/exception-handling.html)
- [Kotlin Docs: Cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html)
- [Android Developers: Best practices for coroutines in Android](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Android Developers: Kotlin coroutines on Android](https://developer.android.com/kotlin/coroutines)
- [Android Developers: UI layer architecture](https://developer.android.com/topic/architecture/ui-layer)
- [kotlinx.coroutines API: `CoroutineExceptionHandler`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-exception-handler/)
