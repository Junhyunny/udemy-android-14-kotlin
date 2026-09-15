# `Dispatchers.IO`는 무엇이고 다른 값들과 어떻게 다른가

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/WishViewModel.kt`](../chapter205/app/src/main/java/com/example/chapter_205/WishViewModel.kt)
- 질문: `Dispatchers.IO`는 뭐고 언제 쓰는가? 다른 값들은 어떤 것들이 있는가? 다른 값을 쓰면 다르게 동작하나? 상세하게 정리해 달라.

```kotlin
fun addWish(wish: Wish) {
    viewModelScope.launch(Dispatchers.IO) {
        wishRepository.addWish(wish = wish)
    }
}
```

## 질문 전제 점검

- **"다른 값을 쓰면 다르게 동작하나"** → **동작한다. 그리고 이 코드에서는 바꾸면 앱이 죽는다.** 여기가 핵심이다.

  `WishDao`의 메서드에는 `suspend`가 없다.

  ```kotlin
  @Insert abstract fun addWish(wish: Wish)     // suspend 가 아니다 = 블로킹 호출
  ```

  Room은 **블로킹 DAO 메서드를 메인 스레드에서 부르면 예외를 던진다.**

  ```
  java.lang.IllegalStateException: Cannot access database on the main thread
  since it may potentially lock the UI for a long period of time.
  ```

  `viewModelScope`의 기본 디스패처는 `Dispatchers.Main.immediate`이므로, `Dispatchers.IO`를 빼면 정확히 이 예외가 난다. **즉 여기서 `Dispatchers.IO`는 선택이 아니라 필수 우회다.**

- **그런데 더 중요한 질문은 "왜 여기서 디스패처를 지정해야만 하는가"다.** 공식 베스트 프랙티스는 **`ViewModel`이 디스패처를 신경 쓰지 않는 것**이다.

  > "Suspend functions should be safe to call from the main thread."

  스레드를 옮기는 책임은 **함수를 만든 쪽**에 있다. `WishDao`에 `suspend`만 붙이면 Room이 알아서 자기 스레드 풀에서 실행하고, `ViewModel`은 `viewModelScope.launch { }`만 쓰면 된다. **지금 코드는 아래 계층의 부실을 위 계층이 떠안고 있는 구조다.** → [`android-room-architecture.md`](android-room-architecture.md)

- **"IO는 I/O 작업용"이라는 이해는 맞지만 절반이다.** 진짜 차이는 **스레드 수**와 **블로킹을 허용하는가**에 있다. 아래에서 다룬다.

## 공부할 내용

### 디스패처란 무엇인가

디스패처는 **"재개된 코루틴 코드를 어느 스레드에서 실행할지"**를 정하는 정책 객체다. 코루틴 자체가 스레드를 만드는 것이 아니다. → [`kotlin-coroutines-suspend-and-event-loop.md`](kotlin-coroutines-suspend-and-event-loop.md)

```kotlin
viewModelScope.launch(Dispatchers.IO) { ... }
//                    ^^^^^^^^^^^^^^  이 블록을 IO 스레드 풀에서 돌려라
```

### 네 가지 기본 디스패처

#### `Dispatchers.Main`

> UI 애플리케이션용 기본 dispatcher

안드로이드의 **메인(UI) 스레드**다. 여기서만 뷰와 Compose 상태를 건드릴 수 있다.

```kotlin
viewModelScope.launch {                    // 기본이 Main.immediate
    _uiState.value = newState              // UI 상태 갱신은 여기서
}
```

`Dispatchers.Main.immediate`는 변형이다. **이미 메인 스레드에 있으면 큐를 거치지 않고 즉시 실행**한다. `viewModelScope`가 이걸 쓰는 이유는 상태 갱신이 한 프레임 늦어지는 것을 막기 위해서다.

#### `Dispatchers.Default`

> "The default dispatcher is used when no other dispatcher is explicitly specified in the scope. It is represented by `Dispatchers.Default` and uses a shared background pool of threads."

**CPU 집약적 계산**용이다. 스레드 수는 **CPU 코어 수**(최소 2)로 제한된다. 코어보다 많은 스레드를 돌려 봐야 문맥 전환 비용만 늘기 때문이다.

```kotlin
withContext(Dispatchers.Default) {
    val sorted = hugeList.sortedBy { it.score }    // 정렬, 파싱, 이미지 처리
    val json = gson.toJson(bigObject)
}
```

#### `Dispatchers.IO`

**블로킹 I/O**용이다. 파일, 데이터베이스, 네트워크처럼 **스레드가 기다리기만 하는** 작업이다.

```kotlin
withContext(Dispatchers.IO) {
    File("data.txt").readText()          // 디스크가 응답할 때까지 스레드가 멈춰 있다
    dao.addWish(wish)                    // 블로킹 DAO 호출
}
```

**`Default`와의 결정적 차이는 스레드 수다.** 기본 64개(또는 코어 수 중 큰 값)까지 늘어난다. 기다리기만 하는 스레드는 CPU를 쓰지 않으므로 많아도 괜찮다는 판단이다.

```
Default:  코어 수만큼 (예: 8개)   — CPU 를 태우는 일
IO:       최대 64개              — 기다리는 일
```

`IO`와 `Default`는 **스레드 풀을 공유한다.** 그래서 둘 사이를 `withContext`로 오가도 실제 스레드 전환이 일어나지 않을 수 있다. 비용이 싸다는 뜻이다.

#### `Dispatchers.Unconfined`

> "The `Dispatchers.Unconfined` coroutine dispatcher starts a coroutine in the caller thread, but only until the first suspension point. After suspension it resumes the coroutine in the thread that is fully determined by the suspending function that was invoked. The unconfined dispatcher is appropriate for coroutines which neither consume CPU time nor update any shared data (like UI) confined to a specific thread."

**호출한 스레드에서 시작했다가, 첫 중단 이후에는 아무 스레드에서나 재개된다.** 예측이 어려워 일반 코드에서는 쓰지 않는다. 라이브러리 내부나 특수한 테스트에서만 쓴다.

### 정리표

| 디스패처 | 스레드 수 | 용도 | 예 |
| --- | --- | --- | --- |
| `Main` | 1 (UI 스레드) | UI 갱신 | 상태 대입, 내비게이션 |
| `Main.immediate` | 1 | 이미 메인이면 즉시 실행 | `viewModelScope` 기본 |
| `Default` | 코어 수 | **CPU 계산** | 정렬, 파싱, 이미지 변환 |
| `IO` | 최대 64+ | **블로킹 I/O** | 파일, DB, 네트워크 |
| `Unconfined` | 불특정 | 고급/내부용 | 일반 코드에서는 ❌ |

**선택 기준 한 줄.**

> **CPU가 바쁜가(`Default`), 아니면 기다리는가(`IO`)?**

### 잘못 고르면 무슨 일이 생기나

```kotlin
// ① IO 작업을 Default 에서 → 스레드 풀 고갈
withContext(Dispatchers.Default) {
    repeat(20) { launch { api.download() } }   // 코어 수만큼만 동시 실행. 나머지는 대기
}

// ② CPU 작업을 IO 에서 → 과도한 병렬화로 문맥 전환 낭비
withContext(Dispatchers.IO) {
    hugeList.sortedBy { it.score }              // 64개 스레드가 CPU를 두고 경쟁
}

// ③ 블로킹 작업을 Main 에서 → ANR 또는 크래시
viewModelScope.launch {
    dao.addWish(wish)                           // IllegalStateException (Room이 막는다)
}
```

**③이 지금 코드가 `Dispatchers.IO`를 붙인 이유다.**

### `launch(Dispatchers.IO)`와 `withContext(Dispatchers.IO)`

둘 다 디스패처를 바꾸지만 의미가 다르다.

```kotlin
// launch: 새 코루틴을 만들고 즉시 반환한다. 결과를 기다리지 않는다
viewModelScope.launch(Dispatchers.IO) { dao.addWish(wish) }

// withContext: 현재 코루틴 안에서 블록만 다른 스레드로 옮긴다. 끝나면 돌아온다
suspend fun add(wish: Wish) = withContext(Dispatchers.IO) { dao.addWish(wish) }
```

**함수 안에서 스레드를 옮길 때는 `withContext`, 새 작업을 띄울 때는 `launch`다.** 지금 코드의 `launch(Dispatchers.IO)`는 "저장을 시작하고 잊는다"는 뜻이고, 그래서 완료 시점을 알 수 없다. 이 성질이 스낵바 타이밍 문제로 이어진다. → [`android-viewmodel-encapsulation-and-async-timing.md`](android-viewmodel-encapsulation-and-async-timing.md)

### 이 프로젝트를 고친다면

**① DAO를 `suspend`로 (권장)**

```kotlin
@Dao
abstract class WishDao {
    @Insert abstract suspend fun addWish(wish: Wish)
    @Update abstract suspend fun updateWish(wish: Wish)
    @Delete abstract suspend fun deleteWish(wish: Wish)
    @Query("select * from `wish-table`") abstract fun getAll(): Flow<List<Wish>>   // Flow 는 그대로
}
```

그러면 `ViewModel`에서 디스패처가 사라진다.

```kotlin
fun addWish(wish: Wish) {
    viewModelScope.launch {              // Dispatchers.IO 불필요
        wishRepository.addWish(wish)
    }
}
```

**Room이 내부적으로 자기 실행자에서 쿼리를 돌려 주기 때문이다.** 호출자는 스레드를 몰라도 된다. 이것이 main-safety 원칙이다.

**② 저장소가 책임지게 하기**

DAO를 못 바꾸는 상황이라면, 최소한 `ViewModel`이 아니라 `Repository`가 감싼다.

```kotlin
class WishRepository(
    private val wishDao: WishDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO   // 주입 가능하게
) {
    suspend fun addWish(wish: Wish) = withContext(ioDispatcher) { wishDao.addWish(wish) }
}
```

### 디스패처를 주입하라

공식 문서가 명시적으로 권하는 사항이다.

```kotlin
// ❌ 하드코딩
class NewsRepository {
    suspend fun loadNews() = withContext(Dispatchers.Default) { /* ... */ }
}

// ✅ 주입
class NewsRepository(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun loadNews() = withContext(defaultDispatcher) { /* ... */ }
}
```

**이유는 테스트다.** 테스트에서 `TestDispatcher`로 갈아 끼우면 시간을 제어할 수 있고 결과가 결정적이 된다.

```kotlin
@Test
fun test() = runTest {
    val repository = NewsRepository(StandardTestDispatcher(testScheduler))
    // ...
}
```

## 관련 아키텍처와 베스트 프랙티스

### 계층별 책임

```
ViewModel     viewModelScope.launch { }        디스패처를 지정하지 않는다
   ↓
Repository    suspend fun ... = withContext(ioDispatcher) { }   여기서 옮긴다
   ↓
DataSource    suspend fun (main-safe 보장)
```

**디스패처 전환은 가능한 한 아래에서 한 번만 한다.** 위에서 옮기고 아래에서 또 옮기면 불필요한 스레드 전환이 쌓인다.

### 이미 main-safe한 라이브러리를 또 감싸지 않는다

```kotlin
// 불필요: Retrofit 의 suspend 함수는 이미 main-safe 하다
withContext(Dispatchers.IO) { api.getCategories() }

// 불필요: suspend DAO 도 마찬가지
withContext(Dispatchers.IO) { dao.getAllSuspend() }
```

**Retrofit, Room(suspend), DataStore는 전부 main-safe하다.** 감싸면 스레드 전환 비용만 는다. `chapter143`의 Retrofit 호출이 `withContext` 없이 쓰인 것이 옳은 예다.

### `Dispatchers.IO.limitedParallelism()`

IO 풀을 무제한으로 쓰면 특정 작업이 풀을 독점할 수 있다. 하위 풀을 잘라 쓸 수 있다.

```kotlin
private val dbDispatcher = Dispatchers.IO.limitedParallelism(4)   // DB 전용 4개
```

### 스레드를 직접 만들지 않는다

```kotlin
// 매우 비싸다
val ctx = newSingleThreadContext("MyThread")
```

> "A dedicated thread is a very expensive resource. In a real application it must be either released, when no longer needed, using the `close` function, or stored in a top-level variable and reused throughout the application."

순서 보장이 필요하면 `Mutex`나 채널을 쓰는 편이 낫다.

### 어느 스레드인지 확인하는 법

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    Log.d("THREAD", Thread.currentThread().name)   // DefaultDispatcher-worker-1
}
```

디버그 모드를 켜면 코루틴 이름까지 나온다.

```
-Dkotlinx.coroutines.debug
```

## 체크리스트

- [ ] 디스패처가 "어느 스레드에서 실행할지 정하는 정책"임을 설명할 수 있다.
- [ ] 이 코드에서 `Dispatchers.IO`를 빼면 왜 크래시가 나는지 설명할 수 있다.
- [ ] `Default`와 `IO`의 스레드 수 차이와 그 이유를 안다.
- [ ] "CPU가 바쁜가, 기다리는가"로 디스패처를 고를 수 있다.
- [ ] `Main.immediate`가 일반 `Main`과 다른 점을 안다.
- [ ] `Unconfined`를 일반 코드에서 쓰지 않는 이유를 설명할 수 있다.
- [ ] 디스패처를 잘못 골랐을 때 생기는 세 가지 문제를 말할 수 있다.
- [ ] `launch(Dispatchers.IO)`와 `withContext(Dispatchers.IO)`의 차이를 안다.
- [ ] DAO에 `suspend`를 붙이면 디스패처 지정이 사라지는 이유를 설명할 수 있다.
- [ ] 디스패처를 주입해야 하는 이유(테스트)를 말할 수 있다.
- [ ] 이미 main-safe한 라이브러리를 감싸면 안 되는 이유를 안다.

## 공식 참고 자료

- [Kotlin Docs: Coroutine context and dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)
- [Android Developers: Best practices for coroutines in Android](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Android Developers: Kotlin coroutines on Android](https://developer.android.com/kotlin/coroutines)
- [Android Developers: Write asynchronous DAO queries](https://developer.android.com/training/data-storage/room/async-queries)
- [Android Developers: Testing Kotlin coroutines on Android](https://developer.android.com/kotlin/coroutines/test)
- [kotlinx.coroutines API: `Dispatchers`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/)
