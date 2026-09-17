# `sealed` 키워드는 왜 사용하는가

## 질문이 나온 코드

- [`chapter143/app/src/main/java/com/example/chapter_143/Screen.kt`](../chapter143/app/src/main/java/com/example/chapter_143/Screen.kt)
- 질문: `sealed` 키워드는 왜 사용하는 건가?

```kotlin
sealed class Screen(val route: String) {
    object RecipeScreen : Screen("recipe-screen")
    object DetailScreen : Screen("detail-screen")
}
```

## 질문 전제 점검

- **`sealed`는 "상속을 막는" 키워드가 아니다.** `final`과 헷갈리기 쉽다. 정확히는 **"상속을 허용하되, 누가 상속할 수 있는지를 컴파일 시점에 고정한다"**는 뜻이다.

  > "All direct subclasses of a sealed class are known at compile time. No other subclasses may appear outside the module and package within which the sealed class is defined."

  `open`은 누구나 상속할 수 있고, `final`은 아무도 못 하고, **`sealed`는 같은 모듈·패키지 안에서 미리 정해진 것만** 상속한다.

- **그리고 솔직하게 말하면, 지금 코드에서는 `sealed`의 진짜 값어치가 거의 쓰이지 않고 있다.** `sealed`의 핵심 이점은 `when`에서 나오는데, 이 코드는 `when`을 쓰지 않고 `Screen.RecipeScreen.route`처럼 **문자열을 꺼내 쓰는 용도**로만 쓴다.

  ```kotlin
  composable(route = Screen.RecipeScreen.route) { ... }
  ```

  이 용도만 보면 `object`를 담은 일반 클래스나 상수 모음으로도 똑같이 동작한다. 그래서 질문을 한 단계 나누는 편이 정확하다.

  1. **`sealed`가 일반적으로 왜 좋은가** → `when`의 완전성 검사
  2. **이 코드에서 실제로 얻는 것은 무엇인가** → 화면 목록을 한곳에 모으고 오타를 막는 정도
  3. **더 나은 방법은 없나** → 타입 안전 route로 가면 `sealed`가 제 역할을 한다

- **강의 흐름상 이 코드가 틀린 것은 아니다.** 문자열 route 방식에서 route를 모아 두는 흔한 패턴이다. 다만 `sealed`를 쓰는 이유를 제대로 체감하려면 `when`과 함께 보는 예제가 필요하다.

## 공부할 내용

### `sealed`의 핵심 — `when`이 완전해진다

> "The key benefit of using sealed classes comes into play when you use them in a `when` expression. The `when` expression, used with a sealed class, allows the Kotlin compiler to check exhaustively that all possible cases are covered."

```kotlin
sealed class Error {
    class FileReadError(val file: String) : Error()
    class DatabaseError(val source: String) : Error()
    object RuntimeError : Error()
}

fun log(e: Error) = when (e) {
    is Error.FileReadError -> println("Error while reading file ${e.file}")
    is Error.DatabaseError -> println("Error while reading from database ${e.source}")
    Error.RuntimeError     -> println("Runtime error")
    // else 가 필요 없다 — 컴파일러가 모든 경우를 다뤘음을 안다
}
```

`sealed`가 아니라 `open`이었다면 컴파일러는 "어디선가 또 다른 자식이 생길 수 있다"고 보기 때문에 `else`를 요구한다. 그런데 `else`가 있으면 **나중에 새 타입을 추가해도 컴파일러가 알려 주지 않는다.**

```kotlin
// open class 였다면
fun log(e: Error) = when (e) {
    is Error.FileReadError -> ...
    is Error.DatabaseError -> ...
    else -> println("알 수 없는 에러")   // ← 새 타입이 조용히 여기로 흘러든다
}
```

**이것이 `sealed`의 진짜 값어치다.** 타입을 추가하면 그 타입을 다루는 모든 `when`이 컴파일 에러가 되어, 고쳐야 할 곳을 컴파일러가 전부 찾아 준다. "빠뜨린 곳이 있나"를 사람이 기억할 필요가 없어진다.

### `enum`과 무엇이 다른가

둘 다 "정해진 몇 가지 중 하나"를 표현하고, 둘 다 `when`에서 완전성 검사를 받는다. 결정적 차이는 이것이다.

> "Each enum constant exists only as a single instance, while subclasses of a sealed class may have multiple instances."

```kotlin
// enum: 각 상수는 인스턴스 하나. 값을 담을 수 없다
enum class ErrorType { FILE, DATABASE, RUNTIME }

// sealed: 각 자식이 서로 다른 데이터를 가질 수 있다
sealed class Error {
    data class FileReadError(val file: String) : Error()      // 파일 경로를 갖는다
    data class DatabaseError(val source: String) : Error()    // 소스를 갖는다
    object RuntimeError : Error()                              // 값이 없다
}
```

판단 기준은 단순하다.

| 상황 | 선택 |
| --- | --- |
| 경우마다 **다른 데이터**를 들고 다녀야 한다 | `sealed` |
| 그냥 **이름표**만 필요하다 | `enum` |
| 인스턴스가 여러 개 필요하다 (`FileReadError("a.txt")`, `FileReadError("b.txt")`) | `sealed` |
| 목록을 순회하거나 이름으로 찾아야 한다 (`values()`, `valueOf()`) | `enum` |

섞어 쓸 수도 있다.

```kotlin
enum class ErrorSeverity { MINOR, MAJOR, CRITICAL }

sealed class Error(val severity: ErrorSeverity) {
    class FileReadError(val file: File) : Error(ErrorSeverity.MAJOR)
    class DatabaseError(val source: DataSource) : Error(ErrorSeverity.CRITICAL)
    object RuntimeError : Error(ErrorSeverity.CRITICAL)
}
```

그리고 `enum`은 sealed class를 상속할 수는 없지만 **sealed interface는 구현할 수 있다.**

```kotlin
sealed interface Error
enum class ErrorType : Error { FILE_ERROR, DATABASE_ERROR }
```

### `sealed class`와 `sealed interface`

```kotlin
// sealed class: 공통 상태(프로퍼티)를 가질 수 있다. 단일 상속
sealed class Screen(val route: String)

// sealed interface: 상태를 가질 수 없지만 다중 구현이 가능하다
sealed interface UiState
```

지금 코드는 `route`라는 **공통 프로퍼티**가 필요해서 `sealed class`가 맞는 선택이다. 공통 상태가 없다면 `sealed interface`가 더 가볍다.

### `object`와 `data class`를 섞어 쓰기

```kotlin
sealed interface RecipeUiState {
    data object Loading : RecipeUiState                              // 값 없음 → object
    data class Success(val categories: List<Category>) : RecipeUiState  // 값 있음 → data class
    data class Error(val message: String) : RecipeUiState
}
```

`object`는 **인스턴스가 하나뿐**이다. `Loading` 상태는 어느 화면에서든 똑같으므로 매번 새로 만들 필요가 없다. 반면 `Success`는 담는 목록이 다르므로 인스턴스가 여럿이어야 한다.

`data object`는 코틀린 1.9부터 쓸 수 있고, `toString()`이 `Loading`으로 예쁘게 나온다는 점만 `object`와 다르다.

### 이 프로젝트에 적용해 보면

`MainViewModel`의 상태가 `sealed`가 어울리는 대표적인 자리다. 현재는 이렇다.

```kotlin
data class RecipeState(
    val loading: Boolean,
    val list: List<Category> = emptyList(),
    val error: String? = null
)
```

문제는 **불가능한 조합이 표현된다는 것**이다. `loading = true`이면서 `error != null`인 상태를 컴파일러가 막지 못한다. 그래서 화면 쪽에서 `when { }`으로 순서를 잘 따져야 한다.

```kotlin
when {
    viewState.loading -> CircularProgressIndicator(...)
    viewState.error != null -> Text("Error Occurred")
    else -> CategoryScreen(viewState.list)     // 순서에 의존한다
}
```

`sealed`로 바꾸면 조합 자체가 불가능해진다.

```kotlin
sealed interface RecipeUiState {
    data object Loading : RecipeUiState
    data class Success(val categories: List<Category>) : RecipeUiState
    data class Error(val message: String) : RecipeUiState
}

when (uiState) {
    is RecipeUiState.Loading -> CircularProgressIndicator()
    is RecipeUiState.Success -> CategoryScreen(uiState.categories)   // 스마트 캐스트
    is RecipeUiState.Error   -> ErrorScreen(uiState.message)
    // else 없음 — 상태를 추가하면 여기가 컴파일 에러가 된다
}
```

`is RecipeUiState.Success` 분기 안에서는 **`uiState`가 자동으로 `Success` 타입이 된다.** 이것을 스마트 캐스트라고 하고, `uiState.categories`를 캐스팅 없이 바로 쓸 수 있다. `sealed`와 `when`이 함께 쓰일 때 나오는 편의다.

### `Screen`을 개선한다면

문자열 route를 계속 쓴다면 지금 형태로 충분하다. 다만 `sealed`의 값어치를 살리려면 타입 안전 route로 가는 편이 낫다.

```kotlin
@Serializable
sealed interface Screen {
    @Serializable data object RecipeList : Screen
    @Serializable data class CategoryDetail(val categoryId: String) : Screen
}
```

이러면 세 가지를 동시에 얻는다.

- 화면 목록이 한곳에 모인다 (지금도 얻고 있는 것)
- **인자가 타입으로 표현된다** — `categoryId`가 `String`임이 보장된다
- **`when`이 완전해진다** — 화면을 추가하면 처리 안 한 곳이 컴파일 에러가 된다

→ [`066-android-navigation-string-route-vs-type-safe.md`](066-android-navigation-string-route-vs-type-safe.md)

## 관련 아키텍처와 베스트 프랙티스

### `sealed`를 쓰기 좋은 자리

| 자리 | 예 |
| --- | --- |
| **UI 상태** | `Loading` / `Success` / `Error` |
| **화면(내비게이션 목적지)** | `Home` / `Detail(id)` |
| **사용자 이벤트** | `OnClick(id)` / `OnRefresh` / `OnSearch(query)` |
| **결과 타입** | `Success(data)` / `Failure(exception)` |
| **네트워크 응답** | `Ok(body)` / `HttpError(code)` / `NetworkError` |

공통점은 **"정해진 몇 가지 중 하나이고, 각각이 다른 데이터를 갖는다"**는 것이다.

### 불가능한 상태를 표현할 수 없게 만든다

```kotlin
// 나쁨: 네 가지 조합 중 두 개는 말이 안 된다
data class State(val isLoading: Boolean, val data: List<X>?, val error: String?)

// 좋음: 세 가지 상태만 존재할 수 있다
sealed interface State {
    data object Loading : State
    data class Success(val data: List<X>) : State
    data class Error(val message: String) : State
}
```

**타입으로 막을 수 있는 실수는 테스트나 리뷰로 막지 않는다.** `sealed`의 설계적 의미가 여기 있다.

### `when`에 `else`를 쓰지 않는다

```kotlin
// 나쁨: 새 상태를 추가해도 컴파일러가 침묵한다
when (state) {
    is Loading -> ...
    is Success -> ...
    else -> ErrorScreen()
}

// 좋음: 모든 분기를 명시한다
when (state) {
    is Loading -> ...
    is Success -> ...
    is Error   -> ...
}
```

`else`를 쓰는 순간 `sealed`를 쓴 이유가 사라진다. 귀찮아도 전부 적는다.

### 표현식으로 쓰면 완전성이 강제된다

```kotlin
// 문(statement)으로 쓰면 코틀린 구버전에서는 완전성 검사가 느슨했다
when (state) { is Loading -> doA() }

// 식(expression)으로 쓰면 반드시 모든 분기가 필요하다
val text = when (state) {
    is Loading -> "불러오는 중"
    is Success -> "완료"
    is Error   -> state.message
}
```

값을 돌려받는 형태로 쓰면 컴파일러가 완전성을 반드시 검사한다. 습관적으로 표현식 형태를 쓰면 안전하다.

### 같은 파일에 모아 둔다

`sealed`의 자식은 같은 패키지·모듈 안에 있어야 한다. 관례적으로 **같은 파일에 중첩해서 선언**한다. 전체 경우의 수가 한 화면에 보이는 것이 이 패턴의 장점이기 때문이다.

## 체크리스트

- [ ] `sealed`가 `final`과 다르다는 것을 설명할 수 있다.
- [ ] "모든 직접 자식이 컴파일 시점에 알려진다"는 의미를 설명할 수 있다.
- [ ] `sealed` + `when`에서 `else`가 필요 없는 이유를 설명할 수 있다.
- [ ] `else`를 쓰면 `sealed`의 이점이 사라지는 이유를 안다.
- [ ] `enum`과 `sealed`를 언제 나눠 쓸지 판단할 수 있다.
- [ ] `sealed class`와 `sealed interface`의 차이를 안다.
- [ ] `object`와 `data class` 자식을 언제 쓰는지 구분할 수 있다.
- [ ] 스마트 캐스트가 `when` 분기 안에서 어떻게 동작하는지 안다.
- [ ] 불가능한 상태를 타입으로 막는다는 원칙을 설명할 수 있다.
- [ ] 현재 `Screen` 코드가 `sealed`의 이점을 얼마나 쓰고 있는지 말할 수 있다.

## 공식 참고 자료

- [Kotlin Docs: Sealed classes and interfaces](https://kotlinlang.org/docs/sealed-classes.html)
- [Kotlin Docs: Conditions and loops (`when`)](https://kotlinlang.org/docs/control-flow.html)
- [Kotlin Docs: Enum classes](https://kotlinlang.org/docs/enum-classes.html)
- [Kotlin Docs: Object declarations and expressions](https://kotlinlang.org/docs/object-declarations.html)
- [Kotlin Docs: Type checks and casts](https://kotlinlang.org/docs/typecasts.html)
- [Android Developers: UI layer architecture](https://developer.android.com/topic/architecture/ui-layer)
