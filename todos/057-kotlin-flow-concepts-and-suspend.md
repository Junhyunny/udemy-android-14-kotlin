# `Flow`란 무엇인가 — 왜 `Flow` 반환에는 `suspend`가 없나

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/WishDao.kt`](../chapter205/app/src/main/java/com/example/chapter_205/WishDao.kt)
- [`chapter205/app/src/main/java/com/example/chapter_205/WishRepository.kt`](../chapter205/app/src/main/java/com/example/chapter_205/WishRepository.kt)
- 질문: `Flow`를 쓰면 코루틴으로 비동기 처리를 한다는데, 어떤 메커니즘으로 동작하는가? `Flow` 클래스는 무엇인가? 예제 케이스를 모두 알려 달라.
- 질문: 반환 값이 `Flow`인 경우에는 `suspend`가 없는데, 반환 값이 없는 경우에는 모두 `suspend`를 붙였다. 이유가 뭘까? 성능적으로 얻는 이점이 있나?

```kotlin
class WishRepository(private val wishDao: WishDao) {
    suspend fun addWish(wish: Wish) { wishDao.addWish(wish) }          // suspend
    fun getAllWishes(): Flow<List<Wish>> = wishDao.getAll()            // suspend 없음
    suspend fun deleteWish(wish: Wish) { wishDao.deleteWish(wish) }    // suspend
}
```

## 질문 전제 점검

- **"성능적으로 얻는 이점이 있나"** → **성능 문제가 아니다.** `suspend` 유무는 최적화가 아니라 **반환하는 것의 성격**이 달라서 생긴 차이다.

  ```kotlin
  suspend fun addWish(wish: Wish)              // "작업을 수행한다"
  fun getAllWishes(): Flow<List<Wish>>         // "값이 흘러나올 파이프를 건네준다"
  ```

  `getAllWishes()`는 **아무 일도 하지 않는다.** DB를 읽지도 않는다. 그냥 `Flow` 객체 하나를 즉시 돌려주고 끝난다. 중단할 일이 없으니 `suspend`가 필요 없다.

  > "Like sequences, cold flows are lazy. The code block of a cold flow builder doesn't run until a collector collects it."

  실제 쿼리는 **누군가 `collect`할 때** 실행된다. 그리고 `collect`는 `suspend` 함수다. **중단은 그때 일어난다.**

  ```
  getAllWishes()      → 즉시 반환. 레시피를 건네준 것
       ↓
  .collect { ... }    → 여기서 비로소 쿼리 실행. 여기가 suspend
  ```

- **"`suspend`가 없으면 비동기가 아닌가"** → 아니다. 오히려 반대다. `Flow`는 **`suspend` 함수보다 더 오래 비동기로 동작한다.** `suspend` 함수는 값을 하나 주고 끝나지만, `Flow`는 계속 흘려보낸다.

  > "A flow represents a sequential stream of values that can be produced asynchronously. Unlike a suspending function, which returns one value, you can use flows to work with multiple sequential values over time."

- **한 가지 짚어 둘 점.** 지금 `WishDao`의 메서드에는 `suspend`가 없다.

  ```kotlin
  @Insert abstract fun addWish(wish: Wish)     // suspend 아님 = 블로킹
  ```

  그래서 `WishRepository.addWish`에 `suspend`를 붙여 놓았지만 **실제로는 중단하지 않고 스레드를 막는다.** `ViewModel`이 `Dispatchers.IO`로 감싸야만 동작하는 이유다. `suspend`가 붙었다고 main-safe해지는 것이 아니다. → [`054-kotlin-coroutine-dispatchers.md`](054-kotlin-coroutine-dispatchers.md)

## 공부할 내용

### `Flow`는 무엇인가

**시간에 따라 여러 값이 흘러나오는 파이프**다. 기존 개념과 비교하면 자리가 분명해진다.

| | 값 하나 | 값 여러 개 |
| --- | --- | --- |
| **동기** | `fun get(): T` | `fun getAll(): List<T>` / `Sequence<T>` |
| **비동기** | `suspend fun get(): T` | **`fun getAll(): Flow<T>`** |

`Flow`는 표의 오른쪽 아래 칸을 채우는 타입이다.

### 콜드 스트림 — 레시피와 요리

> "Cold flow builder의 코드 블록은 collector가 이를 수집할 때까지 실행되지 않습니다. 각각의 새로운 collector는 flow의 새로운 독립적인 실행을 시작합니다."

```kotlin
fun main() {
    val pageFlow = flow {
        for (page in 1..3) {
            println("Loading page $page...")
            emit("Page $page")
        }
    }
    println("Creating a cold flow doesn't run it!")
}
// 출력: Creating a cold flow doesn't run it!
// "Loading page..." 는 한 줄도 안 나온다
```

**`Flow`를 만드는 것은 레시피를 적는 것이고, `collect`가 요리를 시작하는 것이다.** `getAllWishes()`가 `suspend`가 아닌 이유가 여기 있다. 레시피를 건네는 데는 시간이 걸리지 않는다.

그리고 `collect`할 때마다 **처음부터 다시 실행된다.**

```kotlin
val f = flow { println("실행!"); emit(1) }
f.collect { }    // 실행!
f.collect { }    // 실행!   ← 또 실행된다
```

### 파이프라인 구조

```kotlin
suspend fun main() {
    flowOf(0x4B, 0x6F, 0x74, 0x6C, 0x69, 0x6E)   // ① 생산자(emitter)
        .map { value -> value.toChar() }          // ② 중간 연산자
        .collect { updatedValue ->                // ③ 소비자(collector) = 종단 연산자
            println("Say '$updatedValue'!")
        }
}
```

세 자리로 나뉜다.

- **생산자** — `flow { }`, `flowOf()`, `asFlow()`, Room의 `@Query`
- **중간 연산자** — `map`, `filter`, `distinctUntilChanged`, `debounce`… **아무것도 실행하지 않는다.** 새 `Flow`를 돌려줄 뿐이다
- **종단 연산자** — `collect`, `first()`, `toList()`, `stateIn`… **여기서 파이프가 돈다.** `suspend` 함수다

**중간 연산자가 지연 평가된다는 점이 핵심이다.** `map`을 열 개 붙여도 `collect` 전에는 아무 일도 일어나지 않는다.

### 어떤 메커니즘으로 비동기가 되는가

`Flow`는 특별한 스레드 모델을 갖지 않는다. **그냥 `suspend` 함수 위에 세워져 있다.**

```kotlin
// 개념적으로
interface Flow<T> {
    suspend fun collect(collector: FlowCollector<T>)
}
interface FlowCollector<T> {
    suspend fun emit(value: T)
}
```

`emit`과 `collect`가 둘 다 `suspend`다. 그래서 값을 만드는 쪽도, 받는 쪽도 **중단할 수 있다.** 생산자가 느리면 소비자가 기다리고, 소비자가 느리면 생산자가 기다린다(배압, backpressure). 콜백 기반 스트림에는 없는 성질이다. → [`052-kotlin-coroutines-continuation-state-machine.md`](052-kotlin-coroutines-continuation-state-machine.md)

### Room이 `Flow`를 주면 생기는 일

```kotlin
@Query("select * from `wish-table`")
abstract fun getAll(): Flow<List<Wish>>
```

Room은 이 `Flow`를 **테이블 변경 감지와 연결**한다.

```
collect 시작
   → 쿼리 1회 실행 → List<Wish> emit
   → InvalidationTracker 가 wish-table 을 감시
deleteWish() 실행
   → 테이블 변경 감지
   → 쿼리 재실행 → 새 List<Wish> emit
   → UI 갱신
```

**아무도 "다시 조회해"라고 말하지 않는다.** `chapter205`에서 항목을 지우면 목록이 저절로 사라지는 이유다. → [`060-android-room-architecture.md`](060-android-room-architecture.md)

### 예제 모음

#### ① 기본 생성

```kotlin
flowOf(1, 2, 3)                                   // 고정 값
listOf(1, 2, 3).asFlow()                          // 컬렉션에서
flow { emit(api.load()); emit(api.loadMore()) }   // 빌더 안에서 suspend 호출 가능
(1..5).asFlow()
```

#### ② 변환

```kotlin
wishFlow
    .map { list -> list.filter { it.title.isNotBlank() } }
    .filter { it.isNotEmpty() }
    .distinctUntilChanged()          // 같은 값이 연속되면 건너뛴다
    .onEach { Log.d("TAG", "$it") }  // 부수 효과. 값은 그대로 흘려보낸다
    .catch { e -> emit(emptyList()) } // 상류 예외 처리
```

#### ③ 검색어 입력 디바운스 — 대표적 활용

```kotlin
class SearchViewModel(private val repository: WishRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val results: StateFlow<List<Wish>> = query
        .debounce(300)                       // 300ms 동안 입력이 없으면
        .filter { it.length >= 2 }
        .distinctUntilChanged()
        .flatMapLatest { q ->                // 새 검색어가 오면 이전 검색을 취소
            repository.search(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) { query.value = value }
}
```

`flatMapLatest`가 **이전 요청을 자동 취소**한다는 점이 콜백 방식과 크게 다른 부분이다.

#### ④ 여러 소스 합치기

```kotlin
combine(wishFlow, settingsFlow) { wishes, settings ->
    if (settings.hideCompleted) wishes.filter { !it.done } else wishes
}
```

#### ⑤ 예외 처리와 완료

```kotlin
repository.getAllWishes()
    .onStart { _isLoading.value = true }
    .onCompletion { _isLoading.value = false }
    .catch { e -> _error.value = e.message }
    .collect { _wishes.value = it }
```

**`catch`는 상류(upstream)의 예외만 잡는다.** `collect` 블록 안에서 난 예외는 못 잡는다.

### 콜드와 핫 — `StateFlow`, `SharedFlow`

`Flow`가 전부 콜드인 것은 아니다.

| | 콜드 (`Flow`) | 핫 (`StateFlow`, `SharedFlow`) |
| --- | --- | --- |
| 실행 시점 | collect할 때 | 만들어질 때부터 계속 |
| 구독자별 | 각자 처음부터 | 같은 스트림을 공유 |
| 현재 값 | 없다 | `StateFlow.value`로 읽는다 |
| 용도 | DB 쿼리, 네트워크 | **UI 상태** |

```kotlin
// ViewModel 의 UI 상태에는 StateFlow 가 어울린다
val uiState: StateFlow<List<Wish>> = repository.getAllWishes()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

`stateIn`이 **콜드 `Flow`를 핫 `StateFlow`로 바꾼다.** 화면이 회전해도 쿼리가 다시 돌지 않고, 항상 마지막 값을 갖고 있다. `chapter205`처럼 `collectAsState`로 바로 받으면 이 이점이 없다. → [`058-compose-collectasstate-flow-to-state.md`](058-compose-collectasstate-flow-to-state.md)

### `suspend`와 `Flow` 중 무엇을 반환할까

공식 가이드의 기준이 명확하다.

> Data/Business Layer: Expose `suspend fun` for one-shot calls and `Flow<T>` for continuous updates

```kotlin
suspend fun addWish(wish: Wish)                  // 한 번 하고 끝  → suspend
suspend fun getWishOnce(id: Long): Wish          // 한 번 읽고 끝  → suspend
fun getAllWishes(): Flow<List<Wish>>             // 계속 지켜본다  → Flow
fun getWishById(id: Long): Flow<Wish>            // 계속 지켜본다  → Flow
```

**`WishRepository`의 `suspend` 유무는 정확히 이 기준을 따르고 있다.** 질문의 답이 여기 있다. 성능이 아니라 **"한 번인가, 계속인가"**다.

## 관련 아키텍처와 베스트 프랙티스

### 계층별로 노출하는 타입

```
DataSource / DAO    suspend fun  또는  Flow
      ↓
Repository          suspend fun  또는  Flow      (그대로 전달하거나 변환)
      ↓
ViewModel           StateFlow                    (stateIn 으로 변환)
      ↓
Compose             State                        (collectAsStateWithLifecycle)
```

**`ViewModel`이 콜드 `Flow`를 그대로 화면에 노출하지 않는 것**이 권장 형태다. 화면이 여러 번 구독하면 쿼리가 여러 번 돈다.

### `lateinit var getAllWishes`는 위험하다

```kotlin
lateinit var getAllWishes: Flow<List<Wish>>

init {
    viewModelScope.launch {
        getAllWishes = wishRepository.getAllWishes()   // 코루틴 안에서 대입
    }
}
```

`chapter205`의 현재 코드다. 두 가지 문제가 있다.

1. **코루틴 안에서 대입하므로 초기화 시점이 보장되지 않는다.** 화면이 먼저 `getAllWishes`를 읽으면 `UninitializedPropertyAccessException`이 난다. 지금은 `Dispatchers.Main.immediate` 덕분에 대개 먼저 실행되어 우연히 동작한다.
2. **`getAllWishes()`는 `suspend`가 아니므로 코루틴이 애초에 필요 없다.**

```kotlin
// 이렇게 충분하다
val getAllWishes: Flow<List<Wish>> = wishRepository.getAllWishes()

// 더 낫게
val wishes: StateFlow<List<Wish>> = wishRepository.getAllWishes()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

### 불변 타입으로 노출한다

```kotlin
private val _query = MutableStateFlow("")
val query: StateFlow<String> = _query.asStateFlow()   // 밖에서는 못 바꾼다
```

### `Flow`를 `ViewModel` 밖에서 수집하지 않는다

컴포저블에서 `collect`를 직접 부르면 생명주기를 놓친다. `collectAsStateWithLifecycle()`을 쓴다.

### 한 번만 읽고 싶을 때

```kotlin
val wish = repository.getWishById(id).first()    // 첫 값만 받고 끝낸다
```

`first()`는 종단 연산자이자 `suspend` 함수다. **한 번 읽고 끝낼 것을 `Flow`로 계속 구독하고 있지 않은지** 점검할 만하다. `AddEditDetailView`가 편집 화면에서 `collectAsState`로 계속 구독하는 것이 그런 경우다.

## 체크리스트

- [ ] `Flow`가 "시간에 따라 여러 값이 흐르는 파이프"임을 설명할 수 있다.
- [ ] `suspend fun`(값 하나)과 `Flow`(값 여러 개)의 자리 차이를 안다.
- [ ] 콜드 플로우가 `collect` 전에는 실행되지 않는다는 것을 설명할 수 있다.
- [ ] `Flow`를 반환하는 함수에 `suspend`가 필요 없는 이유를 말할 수 있다.
- [ ] 생산자·중간 연산자·종단 연산자의 역할을 구분할 수 있다.
- [ ] 중간 연산자가 지연 평가된다는 것을 안다.
- [ ] `emit`과 `collect`가 모두 `suspend`라서 배압이 생긴다는 것을 안다.
- [ ] Room의 `Flow`가 테이블 변경으로 재실행되는 흐름을 설명할 수 있다.
- [ ] 콜드 `Flow`와 핫 `StateFlow`의 차이를 안다.
- [ ] `stateIn`이 무엇을 바꾸는지 설명할 수 있다.
- [ ] "한 번인가 계속인가"로 `suspend`와 `Flow`를 고를 수 있다.
- [ ] `lateinit var Flow` 패턴의 문제를 지적할 수 있다.

## 공식 참고 자료

- [Kotlin Docs: Asynchronous Flow](https://kotlinlang.org/docs/coroutines-flow.html)
- [Kotlin Docs: Flow operators](https://kotlinlang.org/docs/flow-operators.html)
- [Android Developers: Kotlin flows on Android](https://developer.android.com/kotlin/flow)
- [Android Developers: StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [Android Developers: Best practices for coroutines in Android](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Android Developers: Write asynchronous DAO queries](https://developer.android.com/training/data-storage/room/async-queries)
- [kotlinx.coroutines API: `Flow`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/)
