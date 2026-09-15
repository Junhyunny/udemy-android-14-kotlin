# `by lazy`는 무엇이고 언제 쓰는가

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/Graph.kt`](../chapter205/app/src/main/java/com/example/chapter_205/Graph.kt)
- 질문: `by lazy` 키워드는 뭔가? 어떤 기능인지 정리해 달라. 언제 쓰는 게 좋은가?

```kotlin
object Graph {
    lateinit var database: WishDatabase

    val wishRepository by lazy {
        WishRepository(database.wishDao())
    }

    fun provide(context: Context) {
        database = Room.databaseBuilder(context, WishDatabase::class.java, "wishlist.db").build()
    }
}
```

## 질문 전제 점검

- **"`by lazy` 키워드"** → **키워드가 아니다.** `by`는 키워드지만 `lazy`는 **표준 라이브러리의 평범한 함수**다. 언어 기능이 아니라 라이브러리 기능이라는 점이 중요하다. 직접 만들 수도 있다는 뜻이기 때문이다.

  ```kotlin
  fun <T> lazy(initializer: () -> T): Lazy<T>
  ```

  `by`는 **위임(delegation)** 문법이다. "이 프로퍼티의 `get()`을 저 객체에게 맡긴다"는 뜻이고, `lazy()`가 돌려주는 `Lazy<T>`가 그 대리인이다.

- **이 코드에서 `by lazy`가 왜 필요한지**가 핵심이다. 그냥 이렇게 쓰면 안 되나?

  ```kotlin
  object Graph {
      lateinit var database: WishDatabase
      val wishRepository = WishRepository(database.wishDao())   // ❌ 크래시
  }
  ```

  **`object`의 프로퍼티는 클래스가 처음 로드될 때 전부 초기화된다.** `Graph.provide(context)`를 부르기도 전에 `database.wishDao()`가 실행되고, `lateinit var database`가 아직 비어 있으므로 `UninitializedPropertyAccessException`으로 죽는다.

  `by lazy`는 이 순서 문제를 푼다. **`wishRepository`에 처음 접근하는 시점까지 생성을 미루므로**, 그때는 이미 `provide()`가 끝나 `database`가 채워져 있다.

  ```
  앱 시작
    → Application.onCreate()
    → Graph.provide(context)      database 채워짐
    → (화면이 뜨고) WishViewModel 생성
    → Graph.wishRepository 최초 접근  ← 이때 비로소 WishRepository 생성
  ```

- **즉 여기서 `by lazy`는 "성능 최적화"보다 "초기화 순서 보장"의 역할이 크다.** 일반적인 `by lazy` 설명은 대개 성능 이야기부터 하지만, 이 코드에서는 **없으면 동작 자체가 안 된다.**

## 공부할 내용

### 동작 방식

> "The first call to `get()` executes the lambda passed to `lazy()` and remembers the result. Subsequent calls to `get()` simply return the remembered result."

```kotlin
val lazyValue: String by lazy {
    println("computed!")
    "Hello"
}

fun main() {
    println(lazyValue)   // computed!  /  Hello
    println(lazyValue)   // Hello      ← 람다가 다시 돌지 않는다
}
```

**첫 접근에 한 번만 계산하고 결과를 기억한다.** 두 번째부터는 저장된 값을 그대로 돌려준다.

내부적으로는 컴파일러가 이런 필드를 만든다(개념적으로).

```kotlin
private val wishRepository$delegate: Lazy<WishRepository> = lazy { WishRepository(database.wishDao()) }
val wishRepository: WishRepository
    get() = wishRepository$delegate.value
```

`wishRepository`는 필드가 아니라 **getter**가 된다. 값은 `Lazy` 객체 안에 숨어 있다.

### 스레드 안전성

> "By default, the evaluation of lazy properties is synchronized: the value is computed only in one thread, but all threads will see the same value."

**기본값이 이미 스레드 안전하다.** 여러 스레드가 동시에 접근해도 람다는 한 번만 실행된다. `Graph`처럼 여러 화면·여러 코루틴에서 접근하는 싱글톤에 잘 맞는다.

모드를 바꿀 수도 있다.

| 모드 | 동작 | 언제 |
| --- | --- | --- |
| `SYNCHRONIZED` (기본) | 락을 걸어 한 번만 실행 | **대부분** |
| `PUBLICATION` | 여러 스레드가 동시에 실행할 수 있지만 첫 결과만 채택 | 계산이 싸고 부수 효과가 없을 때 |
| `NONE` | 락 없음. 동시 접근 시 여러 번 실행될 수 있다 | **단일 스레드가 확실할 때만** |

```kotlin
val value by lazy(LazyThreadSafetyMode.NONE) { heavyButSingleThreaded() }
```

`NONE`은 UI 스레드에서만 쓰는 값에 종종 쓰지만, **확신이 없으면 기본값을 둔다.** 락 비용은 첫 호출 이후 거의 없다.

### `lateinit`과의 차이

`Graph` 안에 둘 다 있어서 비교하기 좋다.

```kotlin
lateinit var database: WishDatabase          // 누군가 나중에 넣어 준다
val wishRepository by lazy { ... }           // 처음 읽을 때 스스로 만든다
```

| | `by lazy` | `lateinit` |
| --- | --- | --- |
| 선언 | `val`만 | `var`만 |
| 값을 만드는 주체 | **자기 자신**(람다) | **외부**에서 대입 |
| 초기화 시점 | 첫 읽기 | 누군가 대입할 때 |
| 원시 타입(`Int` 등) | ✅ 가능 | ❌ 불가 |
| nullable 타입 | ✅ 가능 | ❌ 불가 |
| 초기화 전 접근 | 그 자리에서 계산 | **예외** |
| 초기화 여부 확인 | 필요 없음 | `::x.isInitialized` |
| 다시 넣기 | ❌ | ✅ |

**판단 기준 한 줄.**

> **값을 만드는 데 필요한 재료가 이미 다 있으면 `by lazy`, 외부에서 나중에 주입받아야 하면 `lateinit`.**

`database`는 `Context`가 있어야 만들 수 있고 그 `Context`는 `Application`에서 오므로 `lateinit`이다. `wishRepository`는 `database`만 있으면 스스로 만들 수 있으므로 `by lazy`다. **이 코드의 선택은 타당하다.**

### `by lazy`를 쓰면 좋은 경우

#### ① 생성 비용이 큰데 안 쓸 수도 있는 것

```kotlin
class ImageScreen {
    private val heavyDecoder by lazy { ImageDecoder() }   // 이미지가 없으면 안 만든다
}
```

#### ② 초기화 순서가 늦어야 하는 것 — 지금 코드

```kotlin
val wishRepository by lazy { WishRepository(database.wishDao()) }
```

#### ③ 앱 시작 시간을 줄일 때

```kotlin
class MyApp : Application() {
    val analytics by lazy { Analytics() }     // Application.onCreate 를 가볍게
    val imageLoader by lazy { ImageLoader() }
}
```

→ [`android-application-class-lifecycle.md`](android-application-class-lifecycle.md)

#### ④ 안드로이드 컴포넌트에서 뷰나 시스템 서비스 참조

```kotlin
class MainActivity : ComponentActivity() {
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)   // onCreate 전에는 부를 수 없다
    }
}
```

### `by lazy`를 쓰면 안 되는 경우

```kotlin
// ❌ 값이 바뀔 수 있는 것
val currentTime by lazy { System.currentTimeMillis() }   // 첫 접근 시각에 고정된다

// ❌ 계산이 싼 것
val doubled by lazy { x * 2 }                            // Lazy 객체 오버헤드가 더 크다

// ❌ 컴포저블 안에서
@Composable
fun Screen() {
    val state by lazy { mutableStateOf(0) }              // 재구성마다 새 Lazy 객체
}
```

마지막이 특히 흔한 실수다. 컴포저블에서는 **`remember`**를 쓴다. `by lazy`는 "이 프로퍼티가 살아 있는 동안 한 번"이지만, 컴포저블의 지역 변수는 재구성마다 새로 만들어지므로 의미가 없다. → [`compose-remember-mutablestate-and-by.md`](compose-remember-mutablestate-and-by.md)

### 위임 프로퍼티라는 더 넓은 개념

`by lazy`는 위임 프로퍼티의 한 사례다. 다른 것들도 이미 써 왔다.

```kotlin
val value by lazy { ... }                       // 지연 초기화
var name by mutableStateOf("")                  // Compose 상태
val state by viewModel.uiState.collectAsState() // Flow → State
val args by navArgs<DetailArgs>()               // 내비게이션 인자
```

**`by` 뒤에 오는 것은 `getValue()`(그리고 `var`이면 `setValue()`)를 제공하는 객체**라는 공통 규칙이 있다. 직접 만들 수도 있다.

```kotlin
class Prefs(private val prefs: SharedPreferences) {
    var userName: String by StringPref("user_name", "")
}
```

## 관련 아키텍처와 베스트 프랙티스

### `Graph`의 `by lazy`가 감추는 문제

`by lazy`가 초기화 순서를 풀어 주긴 하지만, **`lateinit var database`가 남아 있다는 사실 자체가 설계 신호**다.

```kotlin
object Graph {
    lateinit var database: WishDatabase      // 누가 언제 채우는지 타입이 말해 주지 않는다
    val wishRepository by lazy { WishRepository(database.wishDao()) }
}
```

`Graph.provide()`를 부르지 않은 채 `Graph.wishRepository`에 접근하면 **런타임에** 죽는다. 컴파일러가 막아 주지 않는다. 생성자 주입이라면 애초에 불가능한 상황이다. → [`android-manual-di-graph-object.md`](android-manual-di-graph-object.md)

### 테스트에서의 함정

```kotlin
object Graph {
    val wishRepository by lazy { ... }    // 한 번 만들어지면 다시 못 바꾼다
}
```

`by lazy`는 **값을 다시 넣을 수 없다.** 테스트에서 가짜 저장소로 갈아 끼우려면 방법이 없다. 테스트 사이에 상태가 공유되는 문제도 생긴다. 생성자 주입이면 이런 고민이 없다.

```kotlin
class WishViewModel(private val wishRepository: WishRepository) : ViewModel()
// 테스트: WishViewModel(FakeWishRepository())
```

### 초기화 예외는 다시 던져진다

```kotlin
val risky by lazy { error("실패") }

risky   // IllegalStateException
risky   // 같은 예외가 다시 던져진다 (재시도하지 않는다)
```

**실패한 `lazy`는 회복되지 않는다.** 네트워크처럼 실패할 수 있는 작업을 `by lazy`에 넣으면, 한 번 실패한 뒤 앱을 재시작할 때까지 계속 실패한다.

### 프로퍼티 참조로 확인하기

```kotlin
val lazyProp by lazy { compute() }

// 초기화되었는지 확인
val delegate = ::lazyProp.apply { isAccessible = true }.getDelegate() as Lazy<*>
println(delegate.isInitialized())
```

디버깅 용도로만 쓴다. 일반 코드에서 이럴 일이 있다면 설계를 다시 보는 편이 낫다.

## 체크리스트

- [ ] `lazy`가 키워드가 아니라 표준 라이브러리 함수임을 안다.
- [ ] `by`가 위임 문법이라는 것을 설명할 수 있다.
- [ ] `by lazy`가 첫 접근 시 한 번만 계산한다는 것을 안다.
- [ ] `Graph`에서 `by lazy`를 빼면 왜 크래시가 나는지 설명할 수 있다.
- [ ] 기본 `LazyThreadSafetyMode`가 스레드 안전하다는 것을 안다.
- [ ] `by lazy`와 `lateinit`의 차이를 표로 설명할 수 있다.
- [ ] 둘 중 무엇을 쓸지 판단 기준을 세울 수 있다.
- [ ] `by lazy`를 쓰면 좋은 경우 네 가지를 말할 수 있다.
- [ ] 컴포저블 안에서 `by lazy` 대신 `remember`를 써야 하는 이유를 안다.
- [ ] `by lazy`가 값을 다시 넣을 수 없어 테스트가 어려워진다는 것을 안다.
- [ ] 실패한 `lazy`가 재시도되지 않는다는 것을 안다.

## 공식 참고 자료

- [Kotlin Docs: Delegated properties](https://kotlinlang.org/docs/delegated-properties.html)
- [Kotlin API: `lazy()`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/lazy.html)
- [Kotlin API: `LazyThreadSafetyMode`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-lazy-thread-safety-mode/)
- [Kotlin Docs: Properties (`lateinit`)](https://kotlinlang.org/docs/properties.html)
- [Kotlin Docs: Object declarations](https://kotlinlang.org/docs/object-declarations.html)
- [Android Developers: Manual dependency injection](https://developer.android.com/training/dependency-injection/manual)
