# 코틀린 코루틴 비동기 처리 아키텍처(Kotlin Coroutine Asynchronous Task Architecture)

## 0. 들어가면서

[스위프트 비동기 처리 아키텍처](https://junhyunny.github.io/swift/asnyc-await/task/executor/thread/swift-asynchronous-task-architecture/)를 정리하면서 태스크(Task)-실행자(Executor)-스레드(Thread) 구조를 살펴봤다. 안드로이드(Android)를 공부하는 김에 코틀린(Kotlin) 코루틴(coroutine)은 같은 문제를 어떻게 푸는지 정리한다.

결론부터 말하면 **목표는 같고 해결한 계층이 다르다.** 두 언어 모두 "스레드를 붙잡지 않고 멈췄다가 나중에 이어서 실행한다"를 목표로 한다. 그런데,

- **스위프트는 런타임(runtime)이 주역이다.** 동시성 런타임이 태스크를 만들고, 실행자가 잡을 스케줄링하고, 비동기 프레임(async frame)을 관리한다. `actor`, `Task`, `await`가 전부 언어와 런타임에 박혀 있다.
- **코틀린은 컴파일러(compiler)가 주역이다.** 언어가 제공하는 것은 `suspend` 키워드와 [`Continuation`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines/-continuation/) 인터페이스 딱 두 가지뿐이다. 컴파일러가 `suspend` 함수를 **상태 머신 클래스**로 바꿔 놓고, 나머지(`launch`, `Job`, `Dispatchers`, `Flow`)는 전부 [kotlinx.coroutines라는 라이브러리](https://github.com/Kotlin/kotlinx.coroutines)가 한다.

이 차이 하나가 뒤에 나오는 거의 모든 차이를 만든다. 코틀린에서 디스패처(dispatcher)를 테스트용으로 갈아 끼울 수 있는 것도, 코루틴이 JVM·안드로이드·네이티브·JS에서 똑같이 동작하는 것도 여기서 나온다.

## 1. Kotlin Coroutine Architecture

### 1.1. 네 가지 개념

스위프트 글에서 정리했던 태스크-잡-실행자-스레드에 대응하는 개념은 이렇다.

- **코루틴(Coroutine)** - 비동기 작업 전체에 대한 논리적 단위. 스위프트의 태스크에 해당한다.
- **컨티뉴에이션(`Continuation`)** - 중단된 코루틴을 재개하는 데 필요한 모든 정보를 담은 **힙(heap) 객체**. 스위프트의 잡(Job) + 비동기 프레임(async frame)을 **하나로 합친 것**에 가깝다.
- **디스패처(`CoroutineDispatcher`)** - 재개할 작업을 어느 스레드에서 실행할지 정하는 정책 객체. 스위프트의 실행자(Executor)에 해당한다.
- **스레드(Thread)** - 실제로 CPU에서 명령어를 실행하는 OS 실행 단위. 이건 같다.

여기에 코틀린에만 있는 개념이 둘 더 붙는다.

- **`Job`** - 코루틴의 **생명주기 핸들**이다. 취소하고, 완료를 기다리고, 부모-자식 관계를 만든다. → [`Job` API 문서](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/)
- **`CoroutineContext`** - `Job` + 디스패처 + 이름 + 예외 핸들러를 담은 불변 맵(map)이다. 코루틴이 들고 다니는 실행 환경이다. → [Coroutine context and dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)

> **이름 때문에 헷갈리는 지점 하나.** 스위프트의 **잡(Job)** 은 "실행자가 실제로 실행하는 태스크의 일부"를 뜻하지만, 코틀린의 **`Job`** 은 "코루틴의 생명주기 핸들"이다. **이름만 같고 전혀 다른 것이다.** 스위프트의 잡에 대응하는 코틀린 개념은 `Job`이 아니라 **디스패처 큐에 올라가는 [`Runnable`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-runnable/) 하나**다. 스위프트의 `Task` 핸들에 대응하는 것이 코틀린의 `Job`이다.

### 1.2. 중단 지점은 어디에 표시되는가

스위프트 예제와 같은 모양의 코틀린 코드를 보자.

```kotlin
viewModelScope.launch {
    println("in Coroutine")
    val nameInCoroutine = fetchName()
    println(nameInCoroutine)
}
```

스위프트라면 `await fetchName()`처럼 호출 지점에 `await`가 붙는다. **코틀린은 호출 지점에 아무 표시가 없다.** 중단 가능 지점을 알려 주는 표시는 `fetchName` **선언부**에 있다.

```kotlin
suspend fun fetchName(): String = ...
//  ^^^^^^^ 이 함수는 중간에 멈췄다가 나중에 이어서 실행될 수 있다
```

`suspend`는 **비동기로 만들어 주는 키워드가 아니다.** "여기서 중단될 수 있다"는 표시일 뿐이고, 실제로 스레드를 옮기는 것은 디스패처, 동시에 실행하는 것은 `launch`/`async` 같은 [코루틴 빌더](https://kotlinlang.org/docs/coroutines-basics.html)다.

그래서 코틀린은 **"어디서 멈출 수 있는지 코드만 보고는 알기 어렵다"**는 단점이 있다. 대신 IntelliJ·Android Studio가 중단점이 있는 줄의 좌측 여백에 `⇅` 아이콘을 그려 준다.

### 1.3. 중단이 일어나면 코루틴은 어떻게 쪼개지는가

스위프트에서 하나의 태스크가 `await`를 기준으로 잡 두 개로 나뉘었듯, 코틀린도 중단점을 기준으로 실행 구간이 나뉜다. 다만 그 구간을 컴파일러가 **`label` 값**으로 구분한다.

```
                    하나의 Coroutine

      label = 0                        label = 1
┌────────────────────────┐       ┌────────────────────────┐
│ println("in Coroutine")│       │ println(name)          │
│ call fetchName()       │       │ ...                    │
└───────────┬────────────┘       └───────────▲────────────┘
            │                                │
      suspend 함수 호출                       │
            │                                │
  COROUTINE_SUSPENDED 반환                    │
            │                                │
            ▼                                │
 Continuation 객체에 상태 저장                  │
 (label = 1, 살아남을 지역 변수)                │
            │                                │
  스레드 스택 프레임이 사라진다                  │
            │                                │
   스레드는 곧바로 다른 코루틴을 실행             │
            │                                │
      fetchName 완료                          │
            │                                │
 cont.resumeWith(result) ────────────────────┘
```

스위프트가 힙에 비동기 프레임을 만들어 두고 스택 프레임을 걷어 내듯, 코틀린은 **컴파일 시점에 만들어 둔 상태 머신 객체**에 값을 옮겨 놓고 스택 프레임을 걷어 낸다. 이 변환을 CPS(Continuation Passing Style) 변환이라고 한다. → [Kotlin KEEP: Coroutines design proposal](https://github.com/Kotlin/KEEP/blob/master/proposals/coroutines.md)

### 1.4. 스케줄링 흐름

스위프트가 `Task → runnable Job → Executor → worker thread` 순서였다면, 코틀린은 이렇다.

```
Coroutine
  ↓
Continuation.resumeWith(result)      재개 요청
  ↓
DispatchedContinuation (Runnable)    "실행해야 할 일" 한 덩어리
  ↓
CoroutineDispatcher.dispatch()       어느 스레드에서 돌릴지 결정
  ↓
사용 가능한 worker thread
  ↓
CPU에서 실행
```

핵심은 **재개 요청이 `Runnable` 한 개로 포장되어 디스패처 큐에 들어간다**는 것이다. 이 `Runnable`이 스위프트에서 말하는 "잡"이다. 실제 구현은 [`DispatchedContinuation`](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/internal/DispatchedContinuation.kt)에서 볼 수 있다.

### 1.5. 디스패처 - 코틀린의 실행자

스위프트 실행자가 Generic / Serial / MainActor로 나뉘었듯, 코틀린 디스패처도 역할이 나뉜다. → [`Dispatchers` API 문서](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/)

```
                      CoroutineDispatcher

     ┌───────────────┬───────────────┬───────────────┐
     ▼               ▼               ▼               ▼

  Default            IO             Main          Unconfined

 CPU 바운드 작업    블로킹 I/O      UI / 메인 상태    호출한 스레드에서
 코어 수로 제한     64개 이상 확장   Looper 큐 사용    시작 (특수 목적)

     │               │               │
     └───────┬───────┘               │
             ▼                       ▼

     CoroutineScheduler           Main Thread
     (공유 워커 스레드 풀)          (Looper / Handler)
```

- [`Dispatchers.Default`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-default.html) - CPU 집약적 계산용이다. 스레드 수가 **CPU 코어 수**(최소 2)로 제한된다. 스위프트의 Generic Executor + 협력 스레드 풀(cooperative thread pool)에 해당한다.
- [`Dispatchers.IO`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html) - **블로킹(blocking) I/O 전용**이다. **스위프트에는 대응하는 것이 없다.** 아래에서 따로 다룬다.
- [`Dispatchers.Main`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-main.html) - 안드로이드의 메인 스레드다. 내부적으로 메인 `Looper`의 `Handler`에 작업을 올린다. 스위프트의 MainActor Executor에 해당한다. [`Dispatchers.Main.immediate`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-main-coroutine-dispatcher/immediate.html)는 **이미 메인 스레드라면 큐를 거치지 않고 즉시 실행**하는 변형이고, `viewModelScope`의 기본값이다.
- [`Dispatchers.Unconfined`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-unconfined.html) - 호출한 스레드에서 시작했다가 첫 중단 이후에는 재개시킨 쪽 스레드에서 이어진다. 스위프트에는 없는 개념이고, 일반 코드에서는 쓰지 않는다.

**스위프트와 결정적으로 다른 지점은 `Dispatchers.IO`의 존재다.** 스위프트 협력 스레드 풀에서 블로킹 호출을 하면 코어 수만큼밖에 없는 스레드를 붙잡아 풀 전체를 굶긴다. 그래서 애플은 "협력 스레드 풀에서 절대 블로킹하지 말라"고 안내한다. 반면 코틀린은 **블로킹을 흡수하는 전용 풀을 따로 만들어 현실과 타협했다.** 자바 생태계에는 블로킹 API(JDBC, 파일 I/O, 구버전 Room DAO)가 워낙 많기 때문이다.

```kotlin
// 스위프트에서는 이런 코드가 풀을 망가뜨린다. 코틀린은 IO 풀이 받아 준다
withContext(Dispatchers.IO) {
    File("data.txt").readText()     // 스레드가 멈춰서 기다린다
    dao.addWish(wish)               // 블로킹 DAO 호출
}
```

### 1.6. 워커 스레드 풀 - CoroutineScheduler

`Default`와 `IO`는 **서로 다른 풀이 아니다. 같은 스레드 풀을 공유한다.** 공식 소스 주석이 이 사실을 명시한다.

> "This dispatcher and its views share threads with the `Default` dispatcher, so using `withContext(Dispatchers.IO) { ... }` when already running on the `Default` dispatcher typically does not lead to an actual switching to another thread."
>
> — [kotlinx.coroutines `Dispatchers.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/src/Dispatchers.kt)

그 공유 풀이 [`CoroutineScheduler`](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/src/scheduling/CoroutineScheduler.kt)다. 스위프트 협력 스레드 풀보다 구조가 한 겹 더 복잡하다.

```
CoroutineScheduler

  global CPU queue        [ T ][ T ]        외부 스레드에서 던진 CPU 태스크
  global blocking queue   [ B ]             외부 스레드에서 던진 블로킹 태스크

  Worker 1   local queue [ T ][ T ]    ← CPU 퍼밋 보유. CPU 태스크 실행 중
  Worker 2   local queue [ ]           ← 비어 있음. 다른 워커 큐에서 훔쳐 온다
  Worker 3   local queue [ B ]         ← 블로킹 태스크. CPU 퍼밋을 반납했다
  Worker 4   (parked)                  ← 일이 없어 주차(park)된 상태
```

공식 소스 주석의 설명이 구조를 그대로 말해 준다.

> "The scheduler consists of `corePoolSize` worker threads to execute CPU-bound tasks and up to `maxPoolSize` lazily created threads to execute blocking tasks. ... Every worker has a local queue in addition to global scheduler queues. ... Work-stealing is implemented on top of that queues to provide even load distribution and an illusion of centralized run queue."

여기서 두 가지를 기억하면 된다.

1. **워커마다 자기 로컬 큐가 있고, 비면 다른 워커 큐에서 작업을 훔친다(work-stealing).** 중앙 큐 하나를 두고 경쟁하는 방식보다 확장성이 좋다.
2. **CPU 퍼밋(permit)이라는 토큰이 `corePoolSize`개 있다.** 블로킹 태스크를 만난 워커는 퍼밋을 반납하고, 스케줄러는 그만큼 새 스레드를 만들어 보충한다. 그래서 **블로킹 작업이 아무리 많아도 CPU 작업용 스레드는 항상 코어 수만큼 확보된다.**

> "When a worker encounters a blocking task, it releases its permit to the scheduler to keep an invariant 'scheduler always has at least min(pending CPU tasks, core pool size) and at most core pool size threads to execute CPU tasks'."

`Dispatchers.IO`의 기본 상한은 **64개 또는 코어 수 중 큰 값**이다. 이 값은 `kotlinx.coroutines.io.parallelism` 시스템 프로퍼티로 바꿀 수 있다. 그리고 [`limitedParallelism`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/limited-parallelism.html)으로 하위 풀을 잘라 쓸 수 있다.

```kotlin
val dbDispatcher = Dispatchers.IO.limitedParallelism(4)     // DB 전용 4개
val single = Dispatchers.IO.limitedParallelism(1)           // 사실상 직렬 실행자
```

**`limitedParallelism(1)`이 스위프트의 Serial Executor를 손으로 흉내 내는 방법이다.** 코틀린에는 `actor` 같은 언어 차원의 격리(isolation)가 없기 때문에, 상태를 보호하려면 이렇게 직렬 디스패처를 만들거나 [`Mutex`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/)를 써야 한다. → [Shared mutable state and concurrency](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html)

### 1.7. 구조적 동시성 - Job 계층

스위프트에 태스크 트리(task tree)가 있듯 코틀린에는 `Job` 계층이 있다. 코루틴을 만들면 **부모 `Job` 아래에 자식 `Job`이 달린다.**

```
viewModelScope (Job)
   │
   ├── launch { }            child Job
   │      │
   │      └── async { }      grandchild Job
   │
   └── launch { }            child Job
```

규칙은 세 가지다. → [Structured concurrency](https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency)

- **부모는 모든 자식이 끝날 때까지 완료되지 않는다.**
- **부모를 취소하면 자식이 전부 취소된다.**
- **자식에서 난 예외는 부모로 전파된다.** (예외를 자식 단위로 격리하고 싶으면 [`SupervisorJob`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-supervisor-job.html)을 쓴다)

안드로이드에서 메모리 누수를 막아 주는 장치가 정확히 이것이다. `ViewModel`이 죽으면 `viewModelScope`의 `Job`이 취소되고, 그 아래 매달린 네트워크 요청과 DB 작업이 전부 함께 정리된다. → [Android Developers: Use Kotlin coroutines with lifecycle-aware components](https://developer.android.com/topic/libraries/architecture/coroutines)

## 2. Coroutine, Thread and Memory

이제 스레드와 메모리 관점에서 살펴보자. 스위프트 글에서 다뤘던 것과 같은 순서로 간다.

### 2.1. 동기 함수는 스택에 쌓인다

이건 두 언어가 똑같다.

```kotlin
fun update() {
    val value = add(10, 20)
    println(value)
}

fun add(a: Int, b: Int): Int {
    val result = save(a + b)
    return result
}

fun save(value: Int): Int {
    return value * 2
}

update()
```

`save` 함수까지 호출되었을 때 스레드의 스택 메모리 모습이다.

```
┌──────────────────────┐
│ save                 │
│ value = 30           │
│ return address       │
├──────────────────────┤
│ add                  │
│ a = 10               │
│ b = 20               │
│ return address       │
├──────────────────────┤
│ update               │
│ return address       │
└──────────────────────┘
```

### 2.2. suspend 함수는 무엇이 다른가

스위프트 글과 같은 예제를 코틀린으로 옮겨 보자.

```kotlin
suspend fun updateDatabase(feed: Feed, newArticles: List<Article>) {
    feed.add(newArticles)
}

suspend fun Feed.add(newArticles: List<Article>) {
    for (article in newArticles) {
        val id = article.id
    }

    val ids = save(newArticles)              // 중단점

    val pairs = newArticles.zip(ids)
}
```

**여기서 스위프트와 코틀린이 갈라진다.**

- **스위프트**는 스택 프레임(stack frame)과 비동기 프레임(async frame)을 **동시에** 유지한다. 서스펜션 이후에도 살아남아야 할 값은 힙의 비동기 프레임에, 그렇지 않은 값은 스택 프레임에 둔다. 비동기 함수는 최상단 스택 프레임을 **대체**할 수도 있다.
- **코틀린**에는 "비동기 프레임"이라는 별도 개념이 없다. 컴파일러가 **`suspend` 함수 자체를 클래스로 바꾼다.** 그 클래스의 인스턴스가 곧 비동기 프레임 역할을 하고, 살아남아야 할 지역 변수는 그 클래스의 **필드**가 된다.

컴파일러가 만드는 것을 개념적으로 옮기면 이렇다.

```java
class AddContinuation extends ContinuationImpl {
    int label;                  // ← 지금 몇 번째 구간인가
    Object newArticles;         // ← 중단점을 넘어 살아남아야 하는 값
    Object result;
    Continuation completion;    // ← 나를 호출한 쪽의 Continuation

    Object invokeSuspend(Object res) {
        switch (label) {
            case 0:
                for (...) { int id = article.getId(); }   // 여긴 살아남을 필요 없다
                label = 1;
                Object r = save(newArticles, this);
                if (r == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED;
                res = r;
            case 1:
                Object ids = res;
                zip(newArticles, ids);                    // newArticles 는 필드에서 읽는다
                return Unit.INSTANCE;
        }
    }
}
```

`article`, `id`처럼 **중단점 이전에만 필요한 값은 여전히 스택**에 있고, `newArticles`처럼 **중단점을 넘어 쓰이는 값만 필드로 올라간다.** 판단은 전부 컴파일 시점에 끝난다. 결론적으로 스위프트가 런타임에 비동기 프레임을 만들어 하는 일을, 코틀린은 컴파일러가 클래스를 생성해서 한다.

### 2.3. 중단 직전 - 스택과 힙

`save()`를 호출한 순간의 메모리 모습이다. 각 `Continuation`은 **자기를 호출한 쪽의 `Continuation`을 참조**한다.

```
Thread Stack                          Heap
┌──────────────────────┐            ┌──────────────────────────┐
│ save                 │ ─────────▶ │ save$Continuation        │
├──────────────────────┤            │  label / 지역 변수         │
│ add                  │            │  completion ──┐          │
├──────────────────────┤            ├───────────────▼──────────┤
│ updateDatabase       │            │ add$Continuation         │
├──────────────────────┤            │  newArticles             │
│ launch 람다           │            │  completion ──┐          │
└──────────────────────┘            ├───────────────▼──────────┤
                                    │ updateDatabase$Cont.     │
                                    └──────────────────────────┘
```

### 2.4. 중단 순간 - 스택이 통째로 풀린다

`save()`가 DB 응답을 기다리게 되면 `COROUTINE_SUSPENDED`라는 표지 객체를 반환한다. 그러면 그 값을 받은 호출자도 똑같이 `COROUTINE_SUSPENDED`를 반환하고, 이 반환이 **맨 아래까지 연쇄적으로 올라간다.**

```
Thread Stack                          Heap (그대로 남는다)
┌──────────────────────┐            ┌──────────────────────────┐
│                      │            │ save$Continuation        │
│   (전부 풀렸다)        │            │  label = 1               │
│                      │            ├──────────────────────────┤
│ 이 스레드는 즉시       │            │ add$Continuation         │
│ 다른 코루틴을 실행한다  │            │  newArticles             │
│                      │            ├──────────────────────────┤
└──────────────────────┘            │ updateDatabase$Cont.     │
                                    └──────────────────────────┘
```

**여기가 스위프트와 가장 크게 다른 부분이다.** 스위프트는 태스크의 비동기 프레임이 힙에 살아 있는 채로 스택 프레임만 걷히지만, 코틀린은 **스택 프레임이 "정상 반환"으로 전부 풀린다.** 스레드 입장에서는 그냥 함수가 끝난 것이고, 자기가 중단된 코루틴을 남겨 뒀다는 사실조차 모른다.

살아남는 것은 힙의 `Continuation` 체인뿐이다. **이 연결 리스트가 논리적인 콜 스택 역할을 한다.**

```
save$Cont ──completion──▶ add$Cont ──completion──▶ updateDatabase$Cont ──▶ launch 람다
```

그래서 코루틴의 콜 스택은 **스레드에 있지 않고 힙에 있다.**

### 2.5. 어떻게 재개하는가

이 글에서 가장 중요한 부분이다. 재개는 다섯 단계를 거친다.

**① 누군가 `resumeWith`를 호출한다.**

코틀린에는 재개를 담당하는 중앙 런타임이 없다. **결과를 만든 쪽이 직접 `Continuation`을 깨운다.**

| 무엇을 기다렸나 | 누가 깨우나 |
| --- | --- |
| `delay(1000)` | `DefaultExecutor`의 타이머 스레드 |
| Retrofit 네트워크 응답 | OkHttp의 콜백 스레드 |
| Room `suspend` 쿼리 | Room 자체 실행자 스레드 |
| 직접 만든 콜백 API | 콜백을 호출하는 그 스레드 |

콜백 API를 코루틴으로 감싸는 [`suspendCancellableCoroutine`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/suspend-cancellable-coroutine.html)을 보면 이 구조가 분명하게 보인다.

```kotlin
suspend fun <T> Call<T>.await(): T = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            cont.resume(response.body()!!)        // ← 여기서 코루틴이 깨어난다
        }
        override fun onFailure(call: Call<T>, t: Throwable) {
            cont.resumeWithException(t)
        }
    })
    cont.invokeOnCancellation { cancel() }        // 코루틴이 취소되면 요청도 취소
}
```

**② 디스패처가 필요한지 판단한다.**

`resumeWith`가 불리면 [`DispatchedContinuation`](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/internal/DispatchedContinuation.kt)이 먼저 [`isDispatchNeeded`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/is-dispatch-needed.html)를 확인한다. 실제 구현이다.

```kotlin
override fun resumeWith(result: Result<T>) {
    val state = result.toState()
    if (dispatcher.safeIsDispatchNeeded(context)) {
        _state = state
        resumeMode = MODE_ATOMIC
        dispatcher.safeDispatch(context, this)        // 큐에 올린다
    } else {
        executeUnconfined(state, MODE_ATOMIC) {       // 지금 이 스레드에서 바로 실행
            withCoroutineContext(context, countOrElement) {
                continuation.resumeWith(result)
            }
        }
    }
}
```

**`Dispatchers.Main.immediate`가 빠른 이유가 이 분기다.** 이미 메인 스레드에서 깨우는 상황이라면 `isDispatchNeeded`가 `false`를 반환해 큐를 건너뛰고 그 자리에서 실행한다. 한 프레임 늦어지는 일이 없다.

**③ 디스패처 큐에 `Runnable`로 올라간다.**

`dispatch`가 필요하면 [`CoroutineDispatcher.dispatch(context, block)`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/dispatch.html)가 호출된다. `Dispatchers.Default`/`IO`면 `CoroutineScheduler`의 큐에, `Dispatchers.Main`이면 메인 `Looper`의 `MessageQueue`에 올라간다.

**④ 유휴 워커 스레드가 집어 간다.**

스위프트 실행자가 유휴 워커 스레드를 찾아 잡을 배치하듯, `CoroutineScheduler`는 주차된 워커를 깨우거나 새 스레드를 만든다. **이전에 중단했던 그 스레드로 돌아간다는 보장은 없다.**

**⑤ `label`을 보고 멈췄던 자리로 점프한다.**

```
Thread 3 Stack                        Heap
┌──────────────────────┐            ┌──────────────────────────┐
│ save$Cont            │ ◀───────── │ save$Continuation        │
│  invokeSuspend()     │            │  label = 1               │
│  switch(label) → 1   │            │  result = ids            │
├──────────────────────┤            ├──────────────────────────┤
│ DispatchedTask.run() │            │ add$Continuation         │
├──────────────────────┤            │  newArticles             │
│ Worker.run()         │            ├──────────────────────────┤
└──────────────────────┘            │ updateDatabase$Cont.     │
                                    └──────────────────────────┘
```

**힙에 있는 값을 스택으로 복사해 오는 것이 아니다.** 상태 머신 객체의 필드를 그대로 읽어서 실행을 이어 간다. 처음부터 다시 실행하지도 않는다. `switch(label)`이 멈췄던 지점으로 곧바로 점프한다.

`save()`가 끝나면 `completion` 체인을 타고 `add$Continuation`이 깨어나고, 그다음 `updateDatabase$Continuation`이 깨어난다. **힙에 있는 연결 리스트를 되감는 것이 코루틴의 "리턴"이다.**

중간에 나오는 동기 함수(`zip` 같은)는 그냥 평범한 스택 프레임을 만든다.

```
Thread 3 Stack                        Heap
┌──────────────────────┐            ┌──────────────────────────┐
│ zip                  │            │ add$Continuation         │
├──────────────────────┤            │  newArticles             │
│ add$Cont             │            │  ids                     │
│  invokeSuspend()     │            ├──────────────────────────┤
├──────────────────────┤            │ updateDatabase$Cont.     │
│ Worker.run()         │            └──────────────────────────┘
└──────────────────────┘
```

### 2.6. 그래서 무엇을 얻었나

스위프트와 결론이 같다. **중단이 발생해도 스레드는 기다리지 않는다.** 스레드는 즉시 풀려나 다른 코루틴을 실행하고, 태스크 수만큼 스레드를 만들 필요가 없다. 공식 문서가 그대로 말한다.

> "On the other hand, a coroutine isn't bound to a specific thread. It can suspend on one thread and resume on another, so many coroutines can share the same thread pool. When a coroutine suspends, the thread isn't blocked and remains free to run other tasks."
>
> — [Kotlin Docs: Coroutine basics](https://kotlinlang.org/docs/coroutines-basics.html)

메모리 차이는 숫자로 보면 확실하다.

> "Running this version uses much more memory because each thread needs its own memory stack. For 50,000 threads, that can be up to 100 GB, compared to roughly 500 MB for the same number of coroutines."

스레드는 각자 메가바이트 단위 스택이 필요하지만, 코루틴은 **필드 몇 개짜리 힙 객체**면 된다. 직접 확인해 볼 수 있다.

```kotlin
fun main() = runBlocking {
    val jobs = List(100_000) {
        launch { delay(1000); print(".") }
    }
    jobs.joinAll()
}
```

자바에서 `Thread.sleep()`으로 막힌 스레드는 깨어날 때까지 아무 일도 못 한다. 그래서 톰캣(Tomcat)은 스레드 풀을 200개씩 잡아 두고, 그 이상 요청이 몰리면 부하가 커진다. 코루틴은 그 200이라는 숫자를 지울 수 있게 해 준다. (자바도 JDK 21의 가상 스레드로 같은 문제를 다르게 풀었다.)

### 2.7. 대가도 있다

공짜는 아니다.

- **스택 트레이스가 끊긴다.** 중단 후 재개된 코루틴에서 예외가 나면, 스레드 스택이 이미 사라졌기 때문에 "누가 이 코루틴을 시작했는지"가 트레이스에 남지 않는다. `-Dkotlinx.coroutines.debug` 옵션이나 IDE의 코루틴 디버거로 보완한다. → [Debug coroutines using IntelliJ IDEA](https://kotlinlang.org/docs/debug-coroutines-with-idea.html), [kotlinx.coroutines: Debugging coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/debugging.md)
- **`ThreadLocal`을 쓸 수 없다.** 중단 전후로 스레드가 바뀔 수 있기 때문이다. 대신 `CoroutineContext`에 값을 담는다.
- **취소가 협조적이다.** 상태 머신은 중단점에서만 취소 여부를 확인한다. 중단점이 하나도 없는 긴 계산 루프는 취소되지 않는다. → [Cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html)

```kotlin
launch { while (true) { heavyCompute() } }                    // 취소되지 않는다
launch { while (isActive) { heavyCompute() } }                // 취소된다
launch { while (true) { ensureActive(); heavyCompute() } }    // 취소된다
```

[`ensureActive()`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/ensure-active.html), [`yield()`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/yield.html)가 그래서 필요하다. 스위프트의 `Task.checkCancellation()`, `Task.yield()`와 같은 역할이다.

## 3. Swift와 Kotlin 비교

지금까지 살펴본 내용을 한 표로 정리한다.

| 관점 | Swift Concurrency | Kotlin Coroutines |
| --- | --- | --- |
| 비동기 작업 단위 | `Task` | 코루틴 (`Job` / `Deferred`가 핸들) |
| 실행자에게 넘어가는 단위 | 잡(Job) | `Runnable` (`DispatchedContinuation`) |
| 중단 상태 저장소 | 비동기 프레임(async frame) | 컴파일러가 생성한 상태 머신 객체(`Continuation`) |
| 저장 위치 | 힙 | 힙 |
| 중단 표시 | 호출 지점의 `await` | 선언부의 `suspend` (호출 지점엔 없음) |
| 중단 시 스택 | 태스크 몫 스택 프레임이 걷힘 | 스택 프레임이 **정상 반환으로 전부 풀림** |
| 논리적 콜 스택 | 비동기 프레임 체인 | `Continuation` 연결 리스트 |
| 실행 위치 결정 | Executor (런타임) | `CoroutineDispatcher` (라이브러리) |
| 워커 풀 | cooperative thread pool | `CoroutineScheduler` (Default·IO 공유) |
| 블로킹 전용 풀 | **없다** (블로킹은 풀을 굶긴다) | **`Dispatchers.IO`** |
| 메인 스레드 | MainActor Executor | `Dispatchers.Main` (안드로이드 `Looper`) |
| 상태 보호 | `actor` (언어 기능) | 언어 기능 없음. `Mutex`, `limitedParallelism(1)` |
| 구조적 동시성 | 태스크 트리 | `Job` 계층, `coroutineScope` |
| 취소 | 협조적, `checkCancellation()` | 협조적, 중단점 확인 / `ensureActive()` |
| 비동기 스트림 | `AsyncSequence` | [`Flow`](https://kotlinlang.org/docs/flow.html) |
| 구현 계층 | 컴파일러 + **런타임** | **컴파일러** + 라이브러리 |
| 도입 시기 | Swift 5.5 (2021) | Kotlin 1.3 정식 (2018) |

**"구현 계층" 한 줄이 실무에서 만드는 차이**를 몇 가지만 짚는다.

- **디스패처를 갈아 끼울 수 있다.** 테스트에서 `TestDispatcher`를 주입하면 시간을 제어할 수 있고 결과가 결정적이 된다. 스위프트는 커스텀 실행자를 [SE-0392](https://github.com/swiftlang/swift-evolution/blob/main/proposals/0392-custom-actor-executors.md)에서야 열었다. → [Testing Kotlin coroutines on Android](https://developer.android.com/kotlin/coroutines/test)
- **플랫폼을 가리지 않는다.** 같은 코루틴 코드가 서버에서는 스레드 풀 위에서, 안드로이드에서는 메인 `Looper` 위에서 돈다. `Dispatchers.Main` 구현체만 갈아 끼우면 되기 때문이다.
- **대신 데이터 레이스(data race)를 컴파일러가 막아 주지 않는다.** 스위프트는 `actor`와 `Sendable`로 컴파일 시점에 잡지만, 코틀린은 **전적으로 개발자 책임**이다. 이 부분은 스위프트가 확실히 앞선다.

## 4. 안드로이드에서는 어떻게 보이는가

지금까지의 구조가 실제 안드로이드 코드에서 어떻게 드러나는지 이 저장소 예제로 확인해 보자.

### 4.1. 네트워크 요청 - chapter143

[`chapter143/app/src/main/java/com/example/chapter_143/MainViewModel.kt`](../chapter143/app/src/main/java/com/example/chapter_143/MainViewModel.kt)

```kotlin
viewModelScope.launch {
    val response = recipieService.getCategories()        // 중단점
    _categoriesState.value = ...copy(list = response.categories)
}
```

시간 순서로 일어나는 일이다.

```
1. 메인 스레드에서 람다 시작 (label = 0)
2. getCategories() 호출 → Retrofit이 OkHttp에 enqueue
   → COROUTINE_SUSPENDED 반환
3. label = 1 로 저장하고 스택 프레임이 전부 풀린다
   → 메인 스레드는 즉시 UI를 계속 그린다     ★ 여기가 핵심
4. (몇 백 ms 뒤) OkHttp 워커 스레드에서 응답 도착
   → Retrofit이 continuation.resume(response) 호출
5. Dispatchers.Main.immediate 가 isDispatchNeeded 확인
   → 지금은 OkHttp 스레드이므로 true → 메인 Looper 큐에 올린다
6. 메인 스레드가 꺼내서 invokeSuspend 실행 → switch(label = 1) → 다음 줄
7. 상태 갱신 → 재구성(recomposition) → 화면 갱신
```

**3번과 6번 사이에 메인 스레드는 전혀 막혀 있지 않다.** 그리고 4번은 OkHttp 스레드, 6번은 메인 스레드다. 중단한 스레드와 재개한 스레드가 다르다.

Retrofit의 `suspend fun`은 OkHttp가 자기 스레드 풀에서 I/O를 처리하므로 **메인 스레드에서 호출해도 안전하다.** `withContext(Dispatchers.IO)`로 다시 감쌀 필요가 없다.

### 4.2. 데이터베이스 - chapter205

[`chapter205/app/src/main/java/com/example/chapter_205/WishViewModel.kt`](../chapter205/app/src/main/java/com/example/chapter_205/WishViewModel.kt)

```kotlin
fun addWish(wish: Wish) {
    viewModelScope.launch(Dispatchers.IO) {
        wishRepository.addWish(wish = wish)
    }
}
```

`WishDao`의 메서드에 `suspend`가 없어서 **블로킹 호출**이기 때문에 `Dispatchers.IO`가 필요한 경우다. DAO에 `suspend`만 붙이면 Room이 알아서 자기 실행자에서 돌리고, `ViewModel`에서 디스패처 지정이 사라진다. → [`060-android-room-architecture.md`](../todos/060-android-room-architecture.md)

여기서 공식 베스트 프랙티스 한 줄을 기억해 두면 좋다.

> "Suspend functions should be safe to call from the main thread."
>
> — [Android Developers: Best practices for coroutines in Android](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)

**스레드를 옮기는 책임은 호출자가 아니라 함수를 만든 쪽에 있다.**

### 4.3. Compose와 코루틴

Compose는 코루틴을 세 가지 방식으로 쓴다. → [Compose: Side-effects](https://developer.android.com/develop/ui/compose/side-effects)

```kotlin
// ① LaunchedEffect: 컴포지션에 진입할 때 코루틴을 시작하고, 이탈하면 취소한다
LaunchedEffect(key) { loadData() }

// ② rememberCoroutineScope: 콜백에서 코루틴을 시작할 스코프를 얻는다
val scope = rememberCoroutineScope()
Button(onClick = { scope.launch { sheetState.show() } }) { ... }

// ③ collectAsState: Flow 를 Compose 상태로 변환한다
val items by viewModel.items.collectAsState()
```

**①이 구조적 동시성의 안드로이드 버전**이다. 컴포저블이 화면에서 사라지면 그 안에서 시작한 코루틴이 자동으로 취소된다.

## 5. 공부 순서

이 주제를 처음부터 훑는다면 이 순서를 권한다.

**1단계 - 개념 잡기**

- [Kotlin Docs: Coroutines overview](https://kotlinlang.org/docs/coroutines-overview.html) - 전체 지도
- [Kotlin Docs: Coroutine basics](https://kotlinlang.org/docs/coroutines-basics.html) - 중단과 블로킹의 차이, 구조적 동시성
- [Kotlin Docs: Coroutines guide](https://kotlinlang.org/docs/coroutines-guide.html) - 가이드 목차

**2단계 - 실행 모델**

- [Kotlin Docs: Coroutine context and dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html) - 디스패처와 컨텍스트
- [Kotlin Docs: Composing suspending functions](https://kotlinlang.org/docs/composing-suspending-functions.html) - `async`와 병렬 실행
- [Kotlin Docs: Cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html) - 협조적 취소
- [Kotlin Docs: Coroutine exceptions handling](https://kotlinlang.org/docs/exception-handling.html) - 예외 전파와 `SupervisorJob`

**3단계 - 내부 구조 (이 글의 2장)**

- [Kotlin KEEP: Coroutines for Kotlin](https://github.com/Kotlin/KEEP/blob/master/proposals/coroutines.md) - CPS 변환과 상태 머신의 설계 문서. **가장 중요한 자료다.**
- [Kotlin Specification: Asynchronous programming with coroutines](https://kotlinlang.org/spec/asynchronous-programming-with-coroutines.html) - 언어 명세가 정의하는 코루틴
- [`Continuation` API 문서](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines/-continuation/), [`suspendCoroutine` API 문서](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines/suspend-coroutine.html)
- [kotlinx.coroutines `CoroutineScheduler.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/src/scheduling/CoroutineScheduler.kt) - 스레드 풀 구현. **주석만 읽어도 구조가 보인다.**
- [kotlinx.coroutines `DispatchedContinuation.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/internal/DispatchedContinuation.kt) - 재개 경로 구현

**4단계 - 안드로이드 적용**

- [Android Developers: Kotlin coroutines on Android](https://developer.android.com/kotlin/coroutines)
- [Android Developers: Best practices for coroutines in Android](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Android Developers: Use Kotlin coroutines with lifecycle-aware components](https://developer.android.com/topic/libraries/architecture/coroutines)
- [Android Developers: Kotlin flows on Android](https://developer.android.com/kotlin/flow)
- [Android Developers: Improve app performance with Kotlin coroutines](https://developer.android.com/kotlin/coroutines/coroutines-adv)

**5단계 - 디버깅**

- [Kotlin Docs: Debug coroutines using IntelliJ IDEA](https://kotlinlang.org/docs/debug-coroutines-with-idea.html)
- [kotlinx.coroutines: Debugging coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/debugging.md)

## CLOSING

스위프트와 코틀린을 나란히 놓고 보니 두 언어가 같은 문제를 다른 층에서 풀었다는 점이 분명해졌다. 스위프트는 런타임에 태스크와 실행자를 심었고, 코틀린은 컴파일러가 함수를 상태 머신으로 바꿔 놓고 나머지를 라이브러리에 맡겼다.

개인적으로 가장 흥미로웠던 것은 **중단 순간 스택이 어떻게 되는가**였다. 스위프트는 힙에 비동기 프레임을 따로 두고 스택 프레임을 걷어 내는데, 코틀린은 `COROUTINE_SUSPENDED`라는 값을 반환하며 **스택을 정상적으로 전부 풀어 버린다.** 스레드 입장에서는 그냥 함수 하나가 끝난 것이고, 중단된 코루틴이 힙에 남아 있다는 사실조차 모른다. 처음 알았을 때 꽤 놀랐다.

`Dispatchers.IO`의 존재도 인상적이었다. 스위프트는 "협력 스레드 풀에서 블로킹하지 말라"고 원칙을 세웠는데, 코틀린은 블로킹 API가 넘쳐 나는 자바 생태계를 인정하고 **블로킹을 흡수하는 전용 풀**을 만들었다. 이론적 우아함보다 현실과의 타협을 택한 셈이다. 안드로이드에서 Room 블로킹 DAO를 `Dispatchers.IO`로 감쌀 수 있는 이유가 바로 여기에 있다.

## REFERENCE

- <https://kotlinlang.org/docs/coroutines-overview.html>
- <https://kotlinlang.org/docs/coroutines-basics.html>
- <https://kotlinlang.org/docs/coroutines-guide.html>
- <https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html>
- <https://kotlinlang.org/docs/cancellation-and-timeouts.html>
- <https://kotlinlang.org/docs/composing-suspending-functions.html>
- <https://kotlinlang.org/docs/exception-handling.html>
- <https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html>
- <https://kotlinlang.org/docs/flow.html>
- <https://kotlinlang.org/docs/debug-coroutines-with-idea.html>
- <https://kotlinlang.org/spec/asynchronous-programming-with-coroutines.html>
- <https://github.com/Kotlin/KEEP/blob/master/proposals/coroutines.md>
- <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines/-continuation/>
- <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines/suspend-coroutine.html>
- <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/>
- <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/>
- <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/limited-parallelism.html>
- <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/>
- <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/suspend-cancellable-coroutine.html>
- <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/>
- <https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/src/Dispatchers.kt>
- <https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/src/scheduling/CoroutineScheduler.kt>
- <https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/internal/DispatchedContinuation.kt>
- <https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/debugging.md>
- <https://developer.android.com/kotlin/coroutines>
- <https://developer.android.com/kotlin/coroutines/coroutines-best-practices>
- <https://developer.android.com/kotlin/coroutines/coroutines-adv>
- <https://developer.android.com/kotlin/coroutines/test>
- <https://developer.android.com/kotlin/flow>
- <https://developer.android.com/topic/libraries/architecture/coroutines>
- <https://developer.android.com/develop/ui/compose/side-effects>
- <https://github.com/swiftlang/swift-evolution/blob/main/proposals/0392-custom-actor-executors.md>
