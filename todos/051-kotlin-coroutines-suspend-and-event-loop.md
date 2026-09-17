# `suspend` 키워드와 코루틴은 이벤트 루프인가

## 질문이 나온 코드

- [`chapter143/app/src/main/java/com/example/chapter_143/ApiService.kt`](../chapter143/app/src/main/java/com/example/chapter_143/ApiService.kt)
- 질문: `suspend` 키워드는 뭐고 어떻게 동작하는가? 비동기 처리를 위한 것이라면 이벤트 루프를 사용하는가?

```kotlin
interface ApiService {
    @GET("categories.php")
    suspend fun getCategories(): CategoriesResponse
}
```

## 질문 전제 점검

- **"`suspend`는 비동기 처리를 위한 것"** → 절반만 맞다. `suspend`는 **비동기를 만들어 주는 키워드가 아니다**. "이 함수는 중간에 멈췄다가 나중에 이어서 실행될 수 있다"는 **표시**일 뿐이다. `suspend fun`을 선언한다고 해서 다른 스레드로 옮겨 가지도, 동시에 실행되지도 않는다. 아래 코드는 완전히 순차적이고, 호출한 스레드에서 그대로 실행된다.

  ```kotlin
  suspend fun a() { println("a") }   // 중단점이 하나도 없다. 그냥 함수다
  suspend fun b() { println("b") }
  suspend fun main2() { a(); b() }   // a 끝나고 b. 아무것도 비동기가 아니다
  ```

  실제로 스레드를 바꾸는 것은 **디스패처**(`Dispatchers.IO`, `withContext`)이고, 동시에 돌리는 것은 **빌더**(`launch`, `async`)다. `suspend`는 그 둘을 쓸 수 있게 해 주는 자격 조건에 가깝다.

- **"그러면 이벤트 루프를 사용하는건가?"** → 아니다. 이것이 이 질문에서 가장 크게 바로잡아야 할 전제다. JavaScript처럼 **런타임에 박혀 있는 단일 이벤트 루프는 코틀린에 없다**. 코루틴의 중단/재개는 **컴파일 타임 변환**(상태 머신)으로 만들어지고, 재개된 코드를 "어디서 실행할지"는 **교체 가능한 `CoroutineDispatcher`**가 정한다.

  - `Dispatchers.Default`, `Dispatchers.IO` → 스레드 풀. 이벤트 루프가 아니다.
  - `Dispatchers.Main` (Android) → 메인 스레드의 `Looper`/`MessageQueue`에 작업을 올린다. **이것 하나만 우리가 아는 이벤트 루프에 해당한다.** 그리고 이것은 코루틴의 구조가 아니라 안드로이드 플랫폼의 구조다.

  즉 "코루틴 = 이벤트 루프"가 아니라, **"코루틴은 실행 위치를 디스패처에 위임하고, 그중 하나가 우연히 이벤트 루프"**라고 보는 편이 정확하다. 그래서 같은 코루틴 코드가 서버에서는 스레드 풀 위에서, 안드로이드에서는 메인 루퍼 위에서 돌 수 있다.

- **"코루틴은 가벼운 스레드"** → 자주 쓰이는 비유지만 오해를 부른다. 코루틴은 스레드에 묶여 있지 않다. 공식 문서의 표현이 정확하다.

  > "On the other hand, a coroutine isn't bound to a specific thread. It can suspend on one thread and resume on another, so many coroutines can share the same thread pool."

  한 코루틴이 A 스레드에서 멈췄다가 B 스레드에서 깨어날 수 있다는 뜻이다. 스레드였다면 불가능한 일이다.

## 공부할 내용

### 중단(suspend)과 블로킹(block)의 차이

이 구분이 코루틴 이해의 전부라고 해도 좋다.

```kotlin
// 블로킹: 스레드가 1초 동안 아무것도 못 한다
Thread.sleep(1000)

// 중단: 코루틴만 1초 쉬고, 스레드는 그동안 다른 코루틴을 실행한다
delay(1000)
```

공식 문서의 설명이 이 차이를 그대로 말한다.

> "Coroutines can suspend their execution instead of blocking a thread. This allows one coroutine to suspend while waiting for some data to arrive and another coroutine to run on the same thread, ensuring effective resource utilization."

직접 확인해 보는 예제다.

```kotlin
fun main() = runBlocking {
    val start = System.currentTimeMillis()
    val jobs = List(3) { i ->
        launch {
            delay(1000)                       // 중단
            println("$i 완료 ${System.currentTimeMillis() - start}ms")
        }
    }
    jobs.joinAll()
}
// 0 완료 1005ms
// 1 완료 1006ms
// 2 완료 1006ms   ← 3초가 아니라 1초. 셋이 같은 스레드를 나눠 썼다
```

`delay(1000)`을 `Thread.sleep(1000)`으로 바꾸면 약 3초가 걸린다. 스레드를 붙잡고 있으면 다른 코루틴이 끼어들 틈이 없기 때문이다.

### `suspend` 함수는 아무 데서나 부를 수 없다

```kotlin
fun normal() {
    delay(1000)   // 컴파일 에러
    // Suspend function 'delay' should be called only from a coroutine or another suspend function.
}
```

`suspend` 함수는 **코루틴 안** 또는 **다른 `suspend` 함수 안**에서만 호출할 수 있다. 컴파일러가 강제하는 규칙이다. 안드로이드 공식 문서의 표현이 간결하다.

> "This keyword is Kotlin's way to enforce a function to be called from within a coroutine."

`ApiService.getCategories()`가 `suspend`이기 때문에, 이것을 부르려면 `viewModelScope.launch { ... }` 같은 코루틴 빌더가 필요해진다. 뒤에서 다시 다룬다. → [`056-android-viewmodelscope-launch-necessity.md`](056-android-viewmodelscope-launch-necessity.md)

### 코루틴을 이해하기 위한 예제 모음

#### 1. 가장 작은 코루틴

```kotlin
import kotlinx.coroutines.*

fun main() = runBlocking {          // 코루틴 세계로 들어가는 다리
    launch {                        // 새 코루틴을 시작하고 곧바로 반환한다
        delay(500)
        println("2. 코루틴 안")
    }
    println("1. 코루틴 밖")         // launch는 기다리지 않으므로 이게 먼저 찍힌다
}
// 1. 코루틴 밖
// 2. 코루틴 안
```

`launch`는 **실행 결과를 기다리지 않고 즉시 반환**한다. 이 성질이 뒤에서 예외 처리 이야기로 이어진다. → [`055-kotlin-coroutines-exception-handling-try-catch.md`](055-kotlin-coroutines-exception-handling-try-catch.md)

#### 2. 순차 실행 vs 동시 실행

```kotlin
suspend fun loadUser(): String { delay(1000); return "user" }
suspend fun loadPosts(): String { delay(1000); return "posts" }

// 순차: 2초. suspend 함수를 그냥 부르면 순서대로다
suspend fun sequential() {
    val user = loadUser()
    val posts = loadPosts()
    println("$user, $posts")
}

// 동시: 1초. async로 감싸야 비로소 병렬이 된다
suspend fun concurrent() = coroutineScope {
    val user = async { loadUser() }
    val posts = async { loadPosts() }
    println("${user.await()}, ${posts.await()}")
}
```

"`suspend`를 붙였으니 알아서 비동기가 되겠지"라는 오해를 가장 잘 깨 주는 예제다. **동시성은 `async`/`launch`가 만든다.**

#### 3. 스레드를 바꾸는 것은 디스패처

```kotlin
fun main() = runBlocking {
    println("A: ${Thread.currentThread().name}")        // main
    withContext(Dispatchers.IO) {
        println("B: ${Thread.currentThread().name}")    // DefaultDispatcher-worker-1
    }
    println("C: ${Thread.currentThread().name}")        // main — 알아서 돌아온다
}
```

`withContext`는 블록이 끝나면 **원래 컨텍스트로 되돌아온다**. 콜백 방식에서 "메인 스레드로 다시 돌아가는" 코드를 손으로 쓰던 것이 사라진다.

#### 4. 콜백 지옥이 사라지는 모습

```kotlin
// 콜백 방식
fun load(callback: (Result) -> Unit) {
    api.getUser { user ->
        api.getPosts(user.id) { posts ->
            api.getComments(posts.first().id) { comments ->
                callback(Result(user, posts, comments))   // 오른쪽으로 계속 밀린다
            }
        }
    }
}

// 코루틴
suspend fun load(): Result {
    val user = api.getUser()
    val posts = api.getPosts(user.id)
    val comments = api.getComments(posts.first().id)
    return Result(user, posts, comments)                  // 위에서 아래로 읽힌다
}
```

공식 문서가 말하는 `suspend`의 핵심 가치가 이것이다.

> "It allows a running operation to pause and resume later without affecting the structure of your code."

**코드의 구조를 바꾸지 않고** 멈췄다 재개한다. 콜백은 구조를 바꾸지만 코루틴은 바꾸지 않는다.

#### 5. 구조적 동시성 — 부모가 자식을 책임진다

```kotlin
fun main() = runBlocking {
    val parent = launch {
        launch { delay(1000); println("자식 1") }
        launch { delay(1000); println("자식 2") }
    }
    delay(100)
    parent.cancel()      // 부모를 취소하면 자식도 전부 취소된다
    delay(2000)
    println("끝")        // "자식 1", "자식 2"는 찍히지 않는다
}
```

부모 코루틴은 **자식이 모두 끝날 때까지 완료되지 않고**, 부모가 취소되면 자식도 함께 취소된다. 안드로이드에서 메모리 누수를 막는 장치가 바로 이것이다. 공식 문서도 구조적 동시성의 이점을 "Fewer memory leaks"로 적고 있다.

#### 6. 코루틴이 정말 가벼운지 확인하기

```kotlin
fun main() = runBlocking {
    val jobs = List(100_000) {
        launch { delay(1000); print(".") }
    }
    jobs.joinAll()
}
```

스레드 10만 개는 만들 수 없지만 코루틴은 된다. 공식 문서가 제시한 수치는 이렇다.

> "For 50,000 threads, that can be up to 100 GB, compared to roughly 500 MB for the same number of coroutines."

코루틴은 스레드가 아니라 **힙에 올라가는 객체**이기 때문이다. 중단된 코루틴의 상태가 어디에 저장되는지는 다음 문서에서 다룬다. → [`052-kotlin-coroutines-continuation-state-machine.md`](052-kotlin-coroutines-continuation-state-machine.md)

#### 7. Retrofit의 `suspend fun`이 실제로 하는 일

```kotlin
@GET("categories.php")
suspend fun getCategories(): CategoriesResponse
```

Retrofit은 2.6.0부터 `suspend`를 1차 지원한다. 내부적으로는 기존의 `Call.enqueue()`(비동기 콜백)를 `suspendCancellableCoroutine`으로 감싸서, **OkHttp의 스레드 풀**에서 응답이 오면 코루틴을 재개한다.

```kotlin
// Retrofit이 하는 일의 개념적인 모습
suspend fun <T> Call<T>.await(): T = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            cont.resume(response.body()!!)      // 여기서 코루틴이 깨어난다
        }
        override fun onFailure(call: Call<T>, t: Throwable) {
            cont.resumeWithException(t)
        }
    })
    cont.invokeOnCancellation { cancel() }      // 코루틴이 취소되면 요청도 취소
}
```

여기서 중요한 사실 하나. **`getCategories()`는 메인 스레드에서 호출해도 안전하다.** 네트워크 I/O는 OkHttp가 자기 스레드 풀에서 처리하고, 코루틴은 그동안 중단되어 있을 뿐이기 때문이다. 그래서 `withContext(Dispatchers.IO)`로 다시 감쌀 필요가 없다.

## 관련 아키텍처와 베스트 프랙티스

### `suspend` 함수는 main-safe해야 한다

> "Suspend functions should be safe to call from the main thread."

호출자가 "이 함수는 어느 스레드에서 불러야 하나"를 고민하게 만들면 안 된다. 스레드를 옮겨야 하는 책임은 **함수를 만든 쪽**에 있다.

```kotlin
// 나쁨: 호출자가 Dispatchers.IO를 기억해야 한다
suspend fun readFile(): String = File("a.txt").readText()   // 블로킹!

// 좋음: 함수가 스스로 책임진다
class FileRepository(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun readFile(): String = withContext(ioDispatcher) {
        File("a.txt").readText()
    }
}
```

디스패처를 하드코딩하지 않고 **생성자로 주입**하는 것도 공식 권장 사항이다. 테스트에서 `TestDispatcher`로 갈아 끼울 수 있기 때문이다.

### 코루틴을 처음 배울 때 흔히 하는 실수

| 실수 | 왜 문제인가 | 대신 |
| --- | --- | --- |
| `suspend fun`이면 자동으로 백그라운드라고 생각 | `suspend`는 스레드를 바꾸지 않는다 | `withContext`로 명시 |
| `runBlocking`을 프로덕션 코드에 사용 | 스레드를 통째로 막는다. 메인에서 쓰면 ANR | `viewModelScope.launch` |
| `GlobalScope.launch` 사용 | 생명주기와 무관해 누수·불필요 작업 | 생명주기 스코프 사용 |
| `Thread.sleep`을 코루틴 안에서 사용 | 스레드를 막아 다른 코루틴을 굶긴다 | `delay` |
| 이미 main-safe한 라이브러리를 `Dispatchers.IO`로 또 감싸기 | 불필요한 스레드 전환 비용 | 그대로 호출 |

### 계층별로 무엇을 노출할 것인가

> "Data and business layer should expose suspend functions and Flows."

- 한 번 가져오고 끝나는 값 → `suspend fun`
- 계속 흘러드는 값 → `Flow<T>`
- `ViewModel`은 `suspend fun`을 밖으로 노출하지 말고, **자기 안에서 코루틴을 시작**한다. → [`056-android-viewmodelscope-launch-necessity.md`](056-android-viewmodelscope-launch-necessity.md)

## 체크리스트

- [ ] `suspend` 키워드가 "비동기로 만든다"가 아니라 "중단될 수 있다"는 표시임을 설명할 수 있다.
- [ ] 코틀린 코루틴에 고정된 이벤트 루프가 없고, 실행 위치는 디스패처가 정한다는 것을 설명할 수 있다.
- [ ] `Dispatchers.Main`만이 안드로이드의 `Looper` 위에서 도는 이벤트 루프에 해당한다는 것을 안다.
- [ ] 중단(`delay`)과 블로킹(`Thread.sleep`)의 차이를 예제로 보일 수 있다.
- [ ] `suspend` 함수만으로는 동시성이 생기지 않고 `launch`/`async`가 필요하다는 것을 설명할 수 있다.
- [ ] `withContext`가 블록이 끝나면 원래 컨텍스트로 돌아온다는 것을 안다.
- [ ] 구조적 동시성에서 부모 취소가 자식 취소로 이어지는 것을 설명할 수 있다.
- [ ] Retrofit의 `suspend fun`이 main-safe한 이유를 설명할 수 있다.
- [ ] main-safety 원칙에 따라 블로킹 코드를 `withContext`로 감쌀 수 있다.

## 공식 참고 자료

- [Kotlin Docs: Coroutine basics](https://kotlinlang.org/docs/coroutines-basics.html)
- [Kotlin Docs: Coroutines guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Kotlin Docs: Coroutine context and dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)
- [Kotlin Docs: Composing suspending functions](https://kotlinlang.org/docs/composing-suspending-functions.html)
- [Android Developers: Kotlin coroutines on Android](https://developer.android.com/kotlin/coroutines)
- [Android Developers: Best practices for coroutines in Android](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [square/retrofit PR #2886: Add first-party Kotlin coroutine suspend support](https://github.com/square/retrofit/pull/2886)
