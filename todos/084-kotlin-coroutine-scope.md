# 코루틴 스코프 — 누가 코루틴의 수명을 쥐고 있는가

## 질문이 나온 곳

새 질문에서 나온 문서가 아니다. 여러 TODO 문서에 흩어져 있던 코루틴 스코프 이야기를 한곳에 모았다. 원래 문서에 있던 스코프 설명은 지우고 이 문서로 가는 링크만 남겼다. 각 문서에는 그 주제에 필요한 예제 코드만 남아 있다.

| 문서 | 옮겨 온 스코프 이야기 |
| --- | --- |
| [`022`](022-android-context-and-toast.md), [`032`](032-compose-scaffold.md) | 스낵바를 띄우려고 `rememberCoroutineScope()`를 쓴다 |
| [`033`](033-compose-scroll-state-and-vertical-scroll.md) | `onClick`에서는 `suspend` 함수를 부를 수 없어서 스코프가 필요하다 |
| [`040`](040-compose-remember-async-image-painter.md) | `rememberCoroutineScope()`는 컴포지션을 떠나면 취소된다 |
| [`042`](042-compose-lazy-list-animate-item.md), [`044`](044-compose-modal-bottom-sheet-state-and-sheetstate.md), [`081`](081-compose-maps-camera-position-state.md) | 애니메이션 `suspend` 함수(`hide()`, `reset()`, `animate()`)를 스코프에서 실행한다. 취소될 때 쓰는 `invokeOnCompletion`도 여기서 나온다 |
| [`045`](045-android-viewmodel-role-and-remember.md), [`046`](046-compose-viewmodel-function-vs-manual.md) | `viewModelScope`는 `ViewModel`이 정리될 때 취소된다. 직접 생성한 `ViewModel`의 스코프는 취소되지 않는다 |
| [`048`](048-android-viewmodel-encapsulation-and-async-timing.md) | `launch`는 기다리지 않는다. 화면을 나가면 `rememberCoroutineScope()`가 취소된다. 화면보다 오래 살아야 하는 작업은 앱 수명 스코프에 맡긴다 |
| [`049`](049-android-repository-single-source-of-truth.md), [`068`](068-android-navigation-passing-many-arguments.md), [`069`](069-android-navigation-backstackentry-savedstatehandle.md) | `stateIn(viewModelScope, WhileSubscribed(5_000), ...)` |
| [`060`](060-android-room-architecture.md) | `viewModelScope.launch(Dispatchers.IO)` 대신 `suspend` DAO를 쓴다 |
| [`083`](083-android-hilt-migration-and-testing.md) | Hilt의 `@Singleton`, `@ViewModelScoped` 같은 스코프는 이름만 같고 코루틴 스코프와는 다른 개념이다 |

원래 문서들에는 "이 상황에서는 이 스코프를 쓴다"는 결론만 있었다. 스코프가 **무엇이고 왜 이렇게 동작하는지**는 비어 있어서 공식 문서로 채웠다.

코루틴의 실행 원리(중단·재개, 디스패처, `Job` 계층)는 [`articles/kotlin-coroutine-architecture.md`](../articles/kotlin-coroutine-architecture.md)에 정리했다. 이 문서는 그중 **"스코프"**만 깊게 다룬다.

## 질문 전제 점검

흩어진 문서를 모으다 보면 쉽게 생기는 오해들이다.

- **"스코프는 코루틴을 실행하는 스레드다"** → **아니다.** 어느 스레드에서 실행할지는 디스패처가 정한다. 스코프는 **코루틴의 수명과 기본 컨텍스트를 묶어 두는 객체**다.

  > "new coroutines can only be launched in a `CoroutineScope` that defines and manages their lifecycle."
  >
  > — [Kotlin docs: Coroutines basics](https://kotlinlang.org/docs/coroutines-basics.html)

  실제로 `CoroutineScope` 인터페이스는 `coroutineContext` 프로퍼티 하나만 갖는다. 그 컨텍스트 안에 `Job`(수명)과 `Dispatcher`(스레드)가 들어 있다.

- **"`launch`를 부르면 그 작업이 끝난 뒤에 다음 줄이 실행된다"** → **아니다.** [`048`](048-android-viewmodel-encapsulation-and-async-timing.md)에서 본 버그가 이것이다. `launch`는 자식 코루틴을 **등록만 하고 바로 반환**한다. 기다리게 하는 것은 `coroutineScope { }`, `join()`, `await()`다.

- **"`viewModelScope`에서 시작하면 화면 회전에도 살아남으니 안전하다"** → **절반만 맞다.** 회전에는 살아남지만 `ViewModel`이 정리되면 취소된다. 사용자가 화면을 나가면 저장 중이던 작업도 함께 취소된다. "화면을 나가도 끝나야 하는 작업"이라면 더 오래 사는 스코프가 필요하다.

- **"`rememberCoroutineScope()`와 `LaunchedEffect`는 같은 것이다"** → **취소되는 시점은 같고, 쓰는 자리가 다르다.** 둘 다 컴포지션을 떠나면 취소된다. 하지만 `LaunchedEffect`는 컴포저블 함수라서 **컴포지션 안에서만** 쓸 수 있다. 버튼 클릭 같은 **콜백 안**에서는 `rememberCoroutineScope()`를 써야 한다.

- **"Hilt의 `@ViewModelScoped`도 코루틴 스코프다"** → **아니다.** 이름만 같다. Hilt 스코프는 **의존성 인스턴스를 얼마나 오래 공유할지**를 정한다. 코루틴과는 무관하다. → [`083`](083-android-hilt-migration-and-testing.md)

- **"`GlobalScope`를 쓰면 취소 걱정이 없으니 간단하다"** → **공식 문서가 피하라고 하는 방식이다.** 아래 "베스트 프랙티스"에서 다룬다.

## 공부할 내용

### 스코프 = `Job` + 컨텍스트

```kotlin
public interface CoroutineScope {
    public val coroutineContext: CoroutineContext
}
```

`launch`와 `async`는 `CoroutineScope`의 확장 함수다. 그래서 스코프 없이는 코루틴을 시작할 수 없다.

> "Calling a coroutine builder function, such as `CoroutineScope.launch()` on a `CoroutineScope`, starts a child coroutine of the coroutine associated with that scope."

컨텍스트에 들어가는 대표적인 요소는 다음과 같다.

| 요소 | 역할 | 예 |
| --- | --- | --- |
| `Job` | 수명, 취소, 부모·자식 관계 | `Job()`, `SupervisorJob()` |
| `CoroutineDispatcher` | 어느 스레드에서 실행할지 | `Dispatchers.Main`, `Dispatchers.IO` |
| `CoroutineExceptionHandler` | 처리되지 않은 예외의 마지막 처리기 | 로깅, 크래시 리포트 |
| `CoroutineName` | 디버깅용 이름 | `CoroutineName("save")` |

컨텍스트 요소는 `+`로 합친다. `launch(Dispatchers.IO)`라고 쓰면 스코프의 컨텍스트를 물려받고 **디스패처만 덮어쓴다.** 이때 `Job`은 새로 만들어져 스코프의 `Job` 아래 자식으로 달린다.

### 구조적 동시성 — 스코프가 수명을 책임진다

> "coroutines form a tree hierarchy of parent and child tasks with linked lifecycles."
>
> "A parent coroutine waits for its children to complete before it finishes. If the parent coroutine fails or gets canceled, all its child coroutines are recursively canceled too."
>
> — [Kotlin docs: Coroutines basics](https://kotlinlang.org/docs/coroutines-basics.html)

```
viewModelScope (SupervisorJob)
   ├── launch { 네트워크 요청 }
   │      └── async { 이미지 다운로드 }
   └── launch { DB 저장 }

ViewModel 정리 → viewModelScope 취소 → 아래 전부 취소
```

안드로이드가 스코프를 생명주기에 묶어 두는 이유가 이것이다. **취소를 하나씩 챙기지 않아도 스코프 하나만 끝내면 정리가 끝난다.** 화면이 사라졌는데 네트워크 콜백이 돌아와 죽은 뷰를 건드리는 문제가 구조적으로 사라진다.

### 스코프를 얻는 방법 — 두 종류

**① 이미 있는 스코프를 쓴다 (수명이 정해져 있다)**

| 스코프 | 수명 | 쓰는 곳 |
| --- | --- | --- |
| `viewModelScope` | `ViewModel`이 정리될 때까지 | 비즈니스 로직, 데이터 로드 |
| `lifecycleScope` | `Lifecycle`이 `DESTROYED`될 때까지 | 액티비티·프래그먼트 (View 시스템) |
| `rememberCoroutineScope()` | 호출한 컴포저블이 컴포지션을 떠날 때까지 | 클릭 등 콜백에서 UI 애니메이션 |
| `LaunchedEffect(key) { }` | 컴포지션을 떠나거나 `key`가 바뀔 때까지 | 화면 진입 시 한 번 하는 일, 이벤트 수집 |

**② 새 스코프를 만든다**

| 함수 | 동작 |
| --- | --- |
| `coroutineScope { }` | `suspend` 함수다. 안에서 띄운 자식이 **전부 끝날 때까지 기다린다.** 자식 하나가 실패하면 나머지도 취소된다 |
| `supervisorScope { }` | `coroutineScope`와 같지만 자식의 실패가 형제에게 번지지 않는다 |
| `withContext(ctx) { }` | 컨텍스트를 바꿔 블록을 실행하고 결과를 돌려준다. 주로 디스패처 전환용이다 |
| `CoroutineScope(ctx)` | 직접 수명을 관리하는 스코프를 만든다. **`cancel()`을 직접 불러야 한다** |
| `runBlocking { }` | 현재 스레드를 막고 기다린다. `main` 함수나 테스트 외에는 쓰지 않는다 |

> "`coroutineScope()` executes the suspending block and waits until the block and any coroutines launched in it complete."
>
> "Use `runBlocking()` only when there is no other option to call suspending code from non-suspending code."

`coroutineScope`는 **병렬로 일을 나누고 모아서 기다릴 때** 쓴다.

```kotlin
suspend fun loadScreen(): Screen = coroutineScope {
    val user = async { userRepository.getUser() }
    val news = async { newsRepository.getNews() }
    Screen(user.await(), news.await())       // 둘 다 끝나야 반환
}
```

### `launch`하는 함수에는 `suspend`가 필요 없다

공식 문서가 설명하는 규칙이다.

> "To extract the coroutine builders into another function, that function must declare a `CoroutineScope` receiver, otherwise a compilation error occurs"
>
> "Only mark functions with `suspend` when it makes sense for them to pause and resume before returning."

```kotlin
// 기다리는 함수 → suspend
suspend fun save(wish: Wish) = repository.addWish(wish)

// 띄우고 바로 돌아오는 함수 → suspend 아님
fun saveInBackground(wish: Wish) {
    viewModelScope.launch { repository.addWish(wish) }
}
```

`chapter205`의 `WishViewModel.addWish()`가 두 번째 형태다. 그래서 호출하는 화면은 저장이 끝났는지 알 수 없다. [`048`](048-android-viewmodel-encapsulation-and-async-timing.md)에서 본 "저장 결과와 무관한 성공 메시지" 버그의 원인이다.

### `viewModelScope`의 내부

`ViewModel.kt`의 KDoc은 `viewModelScope`를 이렇게 설명한다. → [AndroidX 소스: `ViewModel.kt`](https://github.com/androidx/androidx/blob/androidx-main/lifecycle/lifecycle-viewmodel/src/commonMain/kotlin/androidx/lifecycle/ViewModel.kt)

- **`SupervisorJob`**: 자식 하나가 실패해도 다른 자식과 스코프가 살아 있다. 한 요청이 실패했다고 화면의 다른 작업이 멈추면 안 되기 때문이다.
- **`Dispatchers.Main.immediate`**: 메인 스레드에서 실행한다. 이미 메인 스레드라면 다음 프레임까지 미루지 않고 즉시 실행한다.
- 스코프는 처음 접근할 때 만들어지고 `ViewModel`에 `Closeable`로 등록된다. `ViewModel`이 정리될 때 `close()`가 불려 취소된다.

그래서 `viewModelScope.launch { }`는 기본적으로 메인 스레드에서 실행된다. 무거운 작업은 `suspend` DAO나 `withContext`로 데이터 레이어 안에서 스레드를 바꾼다. → [`060`](060-android-room-architecture.md)

이 구조 때문에 [`046`](046-compose-viewmodel-function-vs-manual.md)처럼 `ViewModel`을 `viewModel()` 없이 직접 생성하면 문제가 생긴다. 직접 생성한 `ViewModel`은 `ViewModelStore`에 등록되지 않는다. 그러면 `onCleared()`도 `close()`도 불리지 않아 **`viewModelScope`가 영원히 취소되지 않는다.** 화면을 회전할 때마다 새 `ViewModel`이 요청을 다시 시작하므로, 끝나지 않은 이전 요청 위에 중복 호출이 쌓인다.

### `lifecycleScope`와 `repeatOnLifecycle` (View 시스템)

이 저장소는 Compose를 쓰지만 View 코드나 오래된 예제를 읽을 때 자주 만난다.

- `lifecycleScope`는 `Lifecycle`이 파괴될 때 취소된다. `lifecycle-runtime-ktx`가 제공한다.
- `Flow` 수집은 `repeatOnLifecycle(Lifecycle.State.STARTED)`로 감싼다. `ON_STOP`에서 수집을 취소하고 `ON_START`에서 다시 시작한다. → [`repeatOnLifecycle` 레퍼런스](https://developer.android.com/reference/androidx/lifecycle/RepeatOnLifecycleKt)

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { render(it) }
    }
}
```

예전의 `launchWhenStarted`는 수집을 **멈추기만** 해서 업스트림 작업이 계속 돌았다. 그래서 `repeatOnLifecycle`로 바뀌었다.

Compose에서 같은 일을 하는 것이 `collectAsStateWithLifecycle()`이다.

> "By default, collection begins when the lifecycle is `STARTED` and stops when the lifecycle is `STOPPED`."
>
> — [Android Developers: Use Kotlin coroutines with lifecycle-aware components](https://developer.android.com/topic/libraries/architecture/coroutines)

### Compose의 두 스코프 — `LaunchedEffect`와 `rememberCoroutineScope`

> "The coroutine will be cancelled if `LaunchedEffect` leaves the composition."
>
> "If `LaunchedEffect` is recomposed with different keys ..., the existing coroutine will be cancelled and the new suspend function will be launched in a new coroutine."
>
> "In order to launch a coroutine outside of a composable, but scoped so that it will be automatically canceled once it leaves the composition, use `rememberCoroutineScope`."
>
> — [Android Developers: Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects)

| | `LaunchedEffect(key)` | `rememberCoroutineScope()` |
| --- | --- | --- |
| 시작 시점 | 컴포지션에 들어갈 때, `key`가 바뀔 때 | 내가 `launch`할 때 |
| 호출 위치 | 컴포저블 본문 | 콜백 (`onClick` 등) |
| 취소 | 컴포지션을 떠날 때, `key`가 바뀔 때 | 컴포지션을 떠날 때 |
| 이 저장소의 예 | `chapter205` `HomeView`의 `LaunchedEffect(dismissState)` | `chapter205` `AddEditDetailView`, `chapter246` `MainView`의 스낵바·애니메이션 |

`onClick` 같은 콜백은 컴포저블이 아니라 일반 람다다. 그래서 `suspend` 함수를 직접 부를 수 없고 `LaunchedEffect`도 쓸 수 없다. 콜백에 `rememberCoroutineScope()`가 필요한 이유다.

판단 기준은 하나다. **"무엇이 이 코루틴을 시작시키는가?"** 화면 상태(`key`)라면 `LaunchedEffect`를 쓰고, 사용자 이벤트라면 `rememberCoroutineScope()`를 쓴다.

`LaunchedEffect` 안에서 최신 콜백이나 값을 쓰되 그 값이 바뀌어도 재시작하고 싶지 않다면 `rememberUpdatedState`로 감싼다.

### 화면 스코프의 한계 — 취소되면 다음 줄이 실행되지 않는다

[`048`](048-android-viewmodel-encapsulation-and-async-timing.md)의 스낵바 코드와 [`044`](044-compose-modal-bottom-sheet-state-and-sheetstate.md)의 바텀 시트 코드에서 같은 현상이 나온다.

```kotlin
scope.launch {
    snackbarHostState.showSnackbar(msg)  // 약 4초 중단
    navController.navigateUp()           // 화면을 떠남 → scope 취소
    snackMessage.value = ""              // 실행되지 않을 수 있다
}
```

취소는 **다음 중단 지점에서 `CancellationException`을 던지는 방식**으로 전달된다. 그 뒤의 코드는 실행되지 않는다. 취소되어도 꼭 해야 하는 정리 작업은 다음 두 곳에 둔다.

- `try { ... } finally { ... }`
- `job.invokeOnCompletion { ... }` — [`044`](044-compose-modal-bottom-sheet-state-and-sheetstate.md)의 바텀 시트 예제가 이 방식이다.

### 취소는 협조적이다

> "Cancellation in coroutines is cooperative, which means that when a coroutine's `Job` is cancelled, the coroutine isn't cancelled until it suspends or checks for cancellation."
>
> — [Android Developers: Best practices for coroutines](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)

`delay`, `withContext` 같은 `kotlinx.coroutines`의 `suspend` 함수는 취소를 확인한다. 하지만 CPU만 쓰는 반복문은 스코프를 취소해도 끝까지 돈다. 이럴 때는 `ensureActive()`나 `yield()`로 직접 확인한다.

```kotlin
someScope.launch {
    for (file in files) {
        ensureActive()       // 취소됐으면 여기서 멈춘다
        readFile(file)
    }
}
```

### 예외와 스코프

| 상황 | 결과 |
| --- | --- |
| 일반 `Job` 아래에서 자식이 실패한다 | 부모로 전파되어 **형제와 부모가 모두 취소된다** |
| `SupervisorJob`/`supervisorScope` 아래에서 자식이 실패한다 | 그 자식만 실패한다 |
| `launch`에서 예외를 잡지 않는다 | 처리되지 않은 예외가 되어 앱이 크래시한다 |
| `async`에서 예외를 잡지 않는다 | `await()`를 부를 때 던져진다 |

> "Coroutine builders come in two flavors: propagating exceptions automatically ([launch]) or exposing them to users ([async] and [produce])."
>
> "`CoroutineExceptionHandler` is invoked only on uncaught exceptions ... all children coroutines ... delegate handling of their exceptions to their parent coroutine ... so the `CoroutineExceptionHandler` installed in their context is never used."
>
> — [Kotlin docs: Coroutine exceptions handling](https://kotlinlang.org/docs/exception-handling.html)

즉 **자식 `launch`에 `CoroutineExceptionHandler`를 붙여도 동작하지 않는다.** 루트 코루틴이나 `supervisorScope`에서 바로 띄운 코루틴에만 효과가 있다. 일상적인 처리는 코루틴 안에서 `try/catch`로 한다.

`viewModelScope`가 `SupervisorJob`이라도 예외가 사라지지는 않는다. 형제에게 번지지 않을 뿐이고, 잡지 않으면 앱은 여전히 크래시한다. [`048`](048-android-viewmodel-encapsulation-and-async-timing.md)에서 "예외가 나면 앱이 크래시한다"고 한 이유다.

> "To enable coroutine cancellation, don't consume exceptions of type `CancellationException` (don't catch them, or always rethrow them if caught)."

`catch (e: Exception)`이나 `runCatching`은 `CancellationException`까지 삼켜 취소를 무력화할 수 있다. 가능하면 `IOException`처럼 구체적인 예외를 잡는다.

## 관련 아키텍처와 베스트 프랙티스

### 작업의 수명에 맞는 스코프를 고른다

```
짧다 ◀──────────────────────────────────────────────▶ 길다

LaunchedEffect /       viewModelScope       내비게이션 그래프       앱 수명
rememberCoroutineScope                       ViewModel 스코프       externalScope
(컴포저블)             (화면)                (흐름)                 (Application)
```

| 작업 | 스코프 |
| --- | --- |
| 스크롤·시트·스낵바 같은 UI 애니메이션 | `rememberCoroutineScope()` / `LaunchedEffect` |
| 화면 데이터 로드, 입력 처리 | `viewModelScope` |
| 여러 화면에 걸친 흐름(가입 단계 등) | 중첩 그래프에 스코프된 `ViewModel` → [`068`](068-android-navigation-passing-many-arguments.md) |
| 화면을 나가도 끝나야 하는 짧은 작업(북마크, 저장) | 주입한 앱 수명 스코프 |
| 앱을 닫아도 끝나야 하는 작업(업로드, 동기화) | 코루틴이 아니라 `WorkManager` |

### `GlobalScope` 대신 스코프를 주입한다

공식 문서는 `GlobalScope`를 피해야 하는 이유를 세 가지로 든다. 스코프와 함께 디스패처까지 하드코딩하게 되고, 통제할 수 없는 스코프라 테스트가 매우 어렵고, 스코프 전체에 공통 `CoroutineContext`를 줄 수 없다.

> "Instead, consider injecting a `CoroutineScope` for work that needs to outlive the current scope."
>
> "`externalScope` should be created and managed by a class that lives longer than the current screen, it could be managed by the `Application` class or a `ViewModel` scoped to a navigation graph."
>
> — [Android Developers: Best practices for coroutines](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)

```kotlin
class ArticlesRepository(
    private val articlesDataSource: ArticlesDataSource,
    private val externalScope: CoroutineScope,
) {
    suspend fun bookmarkArticle(article: Article) {
        externalScope.launch { articlesDataSource.bookmarkArticle(article) }
            .join()
    }
}
```

`join()`으로 기다리므로 호출한 화면이 살아 있으면 결과를 받는다. 화면이 먼저 사라지면 호출자만 취소되고 `externalScope`의 작업은 끝까지 진행된다.

앱 수명 스코프는 보통 `SupervisorJob()`으로 만든다. 한 작업의 실패가 앱 전체 스코프를 죽이면 안 되기 때문이다. Hilt를 쓴다면 `@Singleton`으로 제공한다. → [`083`](083-android-hilt-migration-and-testing.md)

```kotlin
@Provides @Singleton @ApplicationScope
fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

### 화면은 비즈니스 로직용 코루틴을 직접 띄우지 않는다

> "Views shouldn't directly trigger any coroutines to perform business logic. Instead, defer that responsibility to the `ViewModel`."
>
> "`ViewModel` classes should prefer creating coroutines instead of exposing suspend functions to perform business logic."

정리하면 다음과 같다.

- 컴포저블의 스코프에는 **UI 일**(애니메이션, 스낵바)만 둔다.
- 저장·조회는 `viewModelScope`에서 하고 결과를 **상태나 이벤트**로 알린다. → [`047`](047-android-viewmodel-state-exposure-patterns.md), [`048`](048-android-viewmodel-encapsulation-and-async-timing.md)
- `Repository`와 데이터 소스는 `suspend` 함수나 `Flow`를 노출하고 **스코프를 갖지 않는다.** 예외는 화면보다 오래 살아야 하는 작업을 위해 주입받은 `externalScope`뿐이다.

### 디스패처도 주입한다

`viewModelScope.launch(Dispatchers.IO)`처럼 디스패처를 하드코딩하면 테스트에서 바꿀 수 없다. 공식 문서는 `CoroutineDispatcher`를 생성자로 받으라고 권한다. `suspend` DAO와 Retrofit은 스스로 스레드를 옮기므로 대부분은 `Dispatchers.IO`조차 필요 없다. → [`060`](060-android-room-architecture.md)

### 테스트에서의 스코프

- 테스트는 `runTest { }`(`TestScope`) 안에서 실행한다. `delay`를 가상 시간으로 건너뛴다.
- `viewModelScope`는 `Dispatchers.Main`을 쓰는데, JVM 단위 테스트에는 메인 스레드가 없다. `Dispatchers.setMain(StandardTestDispatcher())`로 바꾸고 끝나면 `resetMain()`한다.
- 주입한 스코프와 디스패처는 테스트용으로 바로 바꿀 수 있다. 스코프를 주입해야 하는 또 하나의 이유다.

→ [Android Developers: Testing Kotlin coroutines on Android](https://developer.android.com/kotlin/coroutines/test)

## 체크리스트

- [ ] 스코프와 디스패처의 차이를 설명할 수 있다.
- [ ] `CoroutineScope`가 `coroutineContext` 하나만 갖는 인터페이스이고 그 안에 `Job`과 디스패처가 있다는 것을 안다.
- [ ] 부모 취소 → 자식 취소, 자식 실패 → 부모 전파라는 구조적 동시성 규칙을 말할 수 있다.
- [ ] `launch`가 즉시 반환된다는 것과 기다리려면 `coroutineScope`/`join`/`await`가 필요하다는 것을 안다.
- [ ] `coroutineScope`, `supervisorScope`, `withContext`, `runBlocking`의 차이를 설명할 수 있다.
- [ ] `viewModelScope`가 `SupervisorJob + Dispatchers.Main.immediate`라는 것과 그 이유를 안다.
- [ ] 직접 생성한 `ViewModel`의 `viewModelScope`가 취소되지 않는 이유를 설명할 수 있다.
- [ ] `LaunchedEffect`와 `rememberCoroutineScope()`를 언제 쓰는지 구분할 수 있다.
- [ ] 스코프가 취소되면 중단 지점 뒤의 코드가 실행되지 않는다는 것과 `finally`/`invokeOnCompletion`으로 정리하는 법을 안다.
- [ ] 취소가 협조적이라는 것과 `ensureActive()`가 필요한 경우를 안다.
- [ ] 자식 코루틴의 `CoroutineExceptionHandler`가 동작하지 않는 이유를 안다.
- [ ] `CancellationException`을 삼키면 안 되는 이유를 안다.
- [ ] `GlobalScope` 대신 주입한 `externalScope`를 쓰는 이유를 말할 수 있다.
- [ ] 작업의 수명에 따라 컴포저블·`ViewModel`·앱·`WorkManager` 중 어디에 맡길지 고를 수 있다.
- [ ] Hilt 스코프와 코루틴 스코프가 다른 개념이라는 것을 안다.

## 공식 참고 자료

- [Kotlin docs: Coroutines basics](https://kotlinlang.org/docs/coroutines-basics.html)
- [Kotlin docs: Coroutine exceptions handling](https://kotlinlang.org/docs/exception-handling.html)
- [Kotlin docs: Cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html)
- [Android Developers: Best practices for coroutines in Android](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Android Developers: Use Kotlin coroutines with lifecycle-aware components](https://developer.android.com/topic/libraries/architecture/coroutines)
- [Android Developers: Use Kotlin coroutines with lifecycle-aware components (Views)](https://developer.android.com/topic/libraries/architecture/views/coroutines-views)
- [Android Developers: `repeatOnLifecycle` reference](https://developer.android.com/reference/androidx/lifecycle/RepeatOnLifecycleKt)
- [Android Developers: Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects)
- [Android Developers: Testing Kotlin coroutines on Android](https://developer.android.com/kotlin/coroutines/test)
- [AndroidX 소스: `ViewModel.kt` (`viewModelScope`)](https://github.com/androidx/androidx/blob/androidx-main/lifecycle/lifecycle-viewmodel/src/commonMain/kotlin/androidx/lifecycle/ViewModel.kt)
