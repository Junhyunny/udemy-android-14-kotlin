# 중단된 코루틴의 상태는 어디에 저장되는가

## 질문이 나온 코드

- [`chapter143/app/src/main/java/com/example/chapter_143/ApiService.kt`](../chapter143/app/src/main/java/com/example/chapter_143/ApiService.kt)
- 질문: 코루틴이 요청을 보내고 응답이 올 때까지, 그 사이의 스택 정보는 어디에 저장되어 있다가 다시 복귀하는가?

## 질문 전제 점검

- **"스택 정보가 어딘가에 저장되었다가 복귀한다"** → 방향은 맞지만 한 가지가 다르다. **스레드의 콜 스택을 어딘가에 통째로 떠 놓는 것이 아니다.** 코루틴이 중단되면 그 스레드의 스택 프레임은 **그냥 사라진다**(함수가 `return`한다). 살아남는 것은 컴파일러가 미리 만들어 둔 **힙 위의 객체**, 즉 `Continuation`이다.
- 그래서 표현을 바꾸면 이렇다. "스택을 저장했다가 되돌린다"가 아니라 **"애초에 스택에 두지 않고, 살아남아야 할 값만 필드로 옮겨 놓는다"**. 컴파일 시점에 어떤 지역 변수가 중단점을 넘어 살아남아야 하는지 이미 알기 때문에 가능한 일이다.
- 이 차이가 실제 결과를 만든다. 스택에 매여 있지 않기 때문에 **중단한 스레드와 재개하는 스레드가 달라도 된다**. 코루틴 10만 개를 만들 수 있는 이유이기도 하다. 스레드 하나당 1~2MB짜리 스택이 필요 없고, 객체 하나만 있으면 된다.
- 반대로 **대가도 있다**. 스레드 스택이 끊기므로, 예외가 났을 때 스택 트레이스에 "어디서 이 코루틴을 시작했는지"가 남지 않는다. 이 문제를 어떻게 다루는지도 아래에서 정리한다.

## 공부할 내용

### 1단계 — CPS 변환: 컴파일러가 파라미터를 하나 몰래 추가한다

`suspend` 함수는 바이트코드 수준에서 그대로 남지 않는다. 컴파일러가 **CPS(Continuation Passing Style) 변환**을 적용한다.

```kotlin
// 우리가 쓴 코드
suspend fun getCategories(): CategoriesResponse
```

```java
// 컴파일 결과 (개념적으로)
Object getCategories(Continuation<? super CategoriesResponse> $completion)
```

두 가지가 바뀐다.

1. `Continuation<T>` 파라미터가 **마지막에 추가된다**. — "다 끝나면 이 사람한테 결과를 돌려줘"라는 콜백이다.
2. 반환 타입이 `Any?`(`Object`)가 된다. — 실제 결과를 돌려줄 수도 있고, **"나 중단했어"라는 표시**를 돌려줄 수도 있기 때문이다.

KEEP 문서의 설명 그대로다.

> "a declaration like `suspend fun <T> CompletableFuture<T>.await(): T` becomes a function with an additional `Continuation<T>` parameter and return type `Any?` after compilation."

`Continuation`은 표준 라이브러리에 이렇게 정의되어 있다.

```kotlin
interface Continuation<in T> {
    val context: CoroutineContext
    fun resumeWith(result: Result<T>)
}
```

**멤버가 두 개뿐이다.** 실행 컨텍스트와, "결과를 들고 나를 재개시켜 줘"라는 함수 하나. 코루틴의 중단/재개는 전부 이 인터페이스 위에 세워져 있다.

### 2단계 — `COROUTINE_SUSPENDED`: 중단했다는 신호

`suspend` 함수가 호출되면 두 가지 중 하나가 일어난다.

```
① 중단 없이 바로 끝남  → 결과값 T 를 그대로 반환
② 진짜로 중단됨        → COROUTINE_SUSPENDED 라는 표지 객체를 반환
```

②의 경우, **함수는 `return`하고 스레드는 즉시 풀려난다.** 스레드는 다른 코루틴을 실행하러 간다. 이것이 "블로킹하지 않는다"의 실체다.

```kotlin
// suspendCoroutine: 중단의 원시 연산
suspend fun <T> suspendCoroutine(block: (Continuation<T>) -> Unit): T
```

> "When `suspendCoroutine` is called inside a coroutine it captures the execution state of a coroutine in a _continuation_ instance."

`block` 안에서 받은 `Continuation`을 어딘가(콜백, 큐, 타이머)에 넘겨 두면, 나중에 그쪽에서 `cont.resume(value)`를 불러 코루틴을 깨울 수 있다.

### 3단계 — 상태 머신: 중단점이 여러 개일 때

중단점이 둘 이상이면 컴파일러는 함수 본문을 **상태 머신 클래스**로 바꾼다.

```kotlin
suspend fun load() {
    val a = stepA()      // 중단점 1
    val b = stepB(a)     // 중단점 2
    println(a + b)
}
```

컴파일러가 만드는 것(개념적으로):

```java
class LoadContinuation extends ContinuationImpl {
    int label;          // ← 지금 몇 번째 상태인가
    Object a;           // ← 중단점을 넘어 살아남아야 하는 지역 변수
    Object result;

    Object invokeSuspend(Object res) {
        switch (label) {
            case 0:
                label = 1;
                Object r = stepA(this);                  // this = 내 continuation
                if (r == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED;
                res = r;
                // fall through
            case 1:
                a = res;                                 // ← 지역 변수를 필드에 보관
                label = 2;
                Object r2 = stepB(a, this);
                if (r2 == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED;
                res = r2;
                // fall through
            case 2:
                System.out.println((int) a + (int) res);
                return Unit.INSTANCE;
        }
    }
}
```

KEEP 문서가 기술하는 구조와 같다.

> "The compiler generates a state machine class for suspending lambdas with multiple suspension points. Each state corresponds to code between suspension points. The class maintains: an `int label` field tracking current state, fields for local variables shared across states, and a `resumeWith()` method implementing the state machine logic."

여기서 질문에 대한 직접적인 답이 나온다.

> "Local variables requiring persistence across suspension points are stored as fields in the generated state machine class, enabling their values to survive suspension and resumption cycles."

**중단점을 넘어 살아남아야 하는 지역 변수는 상태 머신 클래스의 필드가 된다.** 그 클래스의 인스턴스는 힙에 있다. 스택이 사라져도 값은 그대로 남는다.

재개될 때는 `resumeWith()`가 다시 호출되고, `switch(label)`이 **멈췄던 지점으로 곧바로 점프**한다. 처음부터 다시 실행하지 않는다.

### "그러면 콜 스택은 어떻게 되는가"

중요한 부분이다. `a()` → `b()` → `c()` 처럼 `suspend` 함수가 서로를 호출하면, 각 `Continuation`이 **자기를 호출한 쪽의 `Continuation`을 참조**한다.

```
c의 Continuation ──completion──▶ b의 Continuation ──completion──▶ a의 Continuation
```

**연결 리스트가 논리적인 콜 스택 역할을 한다.** 스레드 스택은 사라졌지만, 이 체인이 힙에 남아 있으므로 `c`가 끝나면 `b`로, `b`가 끝나면 `a`로 돌아갈 수 있다. 그래서 코루틴의 콜 스택은 **스레드에 있지 않고 힙에 있다**.

### 이 코드에 대입해 보기

```kotlin
viewModelScope.launch {
    val response = recipieService.getCategories()   // ← 중단점
    _categoriesState.value = ...copy(list = response.categories)
}
```

시간 순서대로 일어나는 일이다.

```
1. 메인 스레드에서 람다 시작 (label = 0)
2. getCategories() 호출
   → Retrofit이 OkHttp에 enqueue하고 COROUTINE_SUSPENDED 반환
3. label = 1 로 바꾸고, 람다도 COROUTINE_SUSPENDED 반환
   → 메인 스레드는 즉시 풀려나 UI를 계속 그린다  ★ 여기가 핵심
4. (몇 백 ms 뒤) OkHttp의 워커 스레드에서 응답 도착
   → Retrofit이 continuation.resume(response) 호출
5. viewModelScope의 디스패처(Dispatchers.Main.immediate)가
   재개 작업을 메인 스레드의 큐에 올린다
6. 메인 스레드에서 resumeWith 실행 → switch(label=1) → 다음 줄부터 계속
7. _categoriesState.value 갱신 → 재구성 → 화면 갱신
```

3번과 6번 사이에 **메인 스레드는 전혀 막혀 있지 않다.** 그리고 4번은 OkHttp 스레드, 6번은 메인 스레드다. **중단한 스레드와 재개한 스레드가 다르다.** 스택을 떠 놓는 방식이었다면 불가능한 일이다.

### 직접 확인해 보는 예제

```kotlin
fun main() = runBlocking {
    println("1: ${Thread.currentThread().name}")
    withContext(Dispatchers.IO) {
        println("2: ${Thread.currentThread().name}")
    }
    println("3: ${Thread.currentThread().name}")
}
// 1: main
// 2: DefaultDispatcher-worker-1
// 3: main
```

지역 변수가 살아남는 것도 확인할 수 있다.

```kotlin
fun main() = runBlocking(Dispatchers.Default) {
    val local = "중단 전에 만든 값"
    println("before: ${Thread.currentThread().name}")
    delay(100)                                   // 여기서 스레드가 바뀔 수 있다
    println("after : ${Thread.currentThread().name}, local = $local")
}
```

`local`은 스택이 아니라 상태 머신 객체의 필드에 있기 때문에, 스레드가 바뀌어도 값이 그대로다.

`Continuation`을 손으로 다뤄 보면 더 분명해진다.

```kotlin
var saved: Continuation<String>? = null

suspend fun waitForManualResume(): String = suspendCoroutine { cont ->
    saved = cont                 // continuation을 그냥 변수에 담아 둔다
    println("중단됨. 아무도 재개하지 않으면 영원히 멈춰 있다")
}

fun main() = runBlocking {
    launch { println("받은 값: ${waitForManualResume()}") }
    delay(100)
    saved?.resume("나중에 넣은 값")   // 원할 때 깨운다
    delay(100)
}
// 중단됨. 아무도 재개하지 않으면 영원히 멈춰 있다
// 받은 값: 나중에 넣은 값
```

중단된 코루틴이 **변수에 담아 둘 수 있는 객체**라는 사실이 눈으로 보인다.

## 관련 아키텍처와 베스트 프랙티스

### 스택 트레이스가 끊기는 문제

중단 후 재개된 코루틴에서 예외가 나면, 스택 트레이스에 원래 호출 지점이 없다. 스레드 스택이 이미 사라졌기 때문이다.

```
java.io.IOException
    at okhttp3...
    at com.example.MainViewModel$fetchCategories$1.invokeSuspend(MainViewModel.kt:29)
    (여기서 끝. fetchCategories()를 누가 불렀는지는 안 나온다)
```

디버그 모드를 켜면 `kotlinx.coroutines`가 코루틴 생성 지점을 기록해 트레이스를 이어 붙여 준다.

```
-Dkotlinx.coroutines.debug
```

안드로이드에서는 `kotlinx-coroutines-debug` 아티팩트를 디버그 빌드에만 넣는 방법이 있다. 운영 빌드에 넣으면 성능 비용이 크다.

### 중단점은 취소 지점이기도 하다

상태 머신이 `label`을 갱신하기 전에 **취소되었는지 확인**한다. 그래서 취소는 "협조적"이다. 중단점이 하나도 없는 긴 계산 루프는 취소되지 않는다.

```kotlin
// 취소되지 않는다: 중단점이 없다
launch { while (true) { heavyCompute() } }

// 취소된다
launch { while (isActive) { heavyCompute() } }
launch { while (true) { ensureActive(); heavyCompute() } }
launch { while (true) { yield(); heavyCompute() } }
```

취소되면 중단점에서 `CancellationException`이 던져진다. 이 예외를 삼키면 안 되는 이유는 따로 정리했다. → [`kotlin-coroutines-exception-handling-try-catch.md`](kotlin-coroutines-exception-handling-try-catch.md)

### `suspend` 함수를 잘게 쪼개는 비용

중단점마다 상태 머신의 `case`가 하나씩 늘고, 살아남을 지역 변수가 필드로 올라간다. 비용이 아주 크지는 않지만 공짜도 아니다. 중단할 일이 전혀 없는 함수에 습관적으로 `suspend`를 붙이지 않는 편이 좋다.

```kotlin
// 불필요: 중단점이 없다
suspend fun format(x: Int): String = "$x"

// 적절: 실제로 중단한다
suspend fun fetch(): Data = api.get()
```

### 스레드 로컬을 쓰면 안 되는 이유

중단 전후로 스레드가 바뀔 수 있으므로, `ThreadLocal`에 담아 둔 값은 재개 후 사라질 수 있다. 코루틴에서는 **`CoroutineContext`**가 그 자리를 대신한다.

```kotlin
// 코루틴 전용 컨텍스트 요소
class UserId(val value: String) : AbstractCoroutineContextElement(UserId) {
    companion object Key : CoroutineContext.Key<UserId>
}

launch(UserId("abc")) {
    delay(100)
    println(coroutineContext[UserId]?.value)   // 재개 후에도 남아 있다
}
```

## 체크리스트

- [ ] 코루틴이 중단될 때 스레드 스택 프레임이 사라진다는 것을 설명할 수 있다.
- [ ] CPS 변환으로 `Continuation` 파라미터가 추가되고 반환 타입이 `Any?`가 되는 이유를 설명할 수 있다.
- [ ] `Continuation` 인터페이스의 두 멤버가 무엇인지 말할 수 있다.
- [ ] `COROUTINE_SUSPENDED`가 무엇을 뜻하는지 설명할 수 있다.
- [ ] `label` 필드와 `switch` 점프로 재개 지점을 찾는 구조를 설명할 수 있다.
- [ ] 중단점을 넘어가는 지역 변수가 상태 머신 클래스의 필드가 된다는 것을 안다.
- [ ] `Continuation` 체인이 논리적 콜 스택 역할을 한다는 것을 설명할 수 있다.
- [ ] 중단한 스레드와 재개한 스레드가 다를 수 있는 이유를 설명할 수 있다.
- [ ] 코루틴 스택 트레이스가 끊기는 이유와 디버그 모드 해결책을 안다.
- [ ] 코루틴에서 `ThreadLocal` 대신 `CoroutineContext`를 써야 하는 이유를 설명할 수 있다.

## 공식 참고 자료

- [Kotlin KEEP: Coroutines for Kotlin (design proposal)](https://github.com/Kotlin/KEEP/blob/master/proposals/coroutines.md)
- [Kotlin Docs: Coroutine basics](https://kotlinlang.org/docs/coroutines-basics.html)
- [Kotlin Docs: Cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html)
- [Kotlin Docs: Coroutine context and dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)
- [Kotlin API: `kotlin.coroutines.Continuation`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines/-continuation/)
- [Kotlin API: `suspendCoroutine`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines/suspend-coroutine.html)
- [kotlinx.coroutines: Debugging coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/debugging.md)
