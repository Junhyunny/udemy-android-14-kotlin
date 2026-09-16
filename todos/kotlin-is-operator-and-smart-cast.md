# `is` 키워드 용도가 뭐야

## 질문이 나온 코드

- [`chapter246/app/src/main/java/com/example/chapter_246/MainView.kt`](../chapter246/app/src/main/java/com/example/chapter_246/MainView.kt)
- 질문: `is` 키워드 용도가 뭐야?

```kotlin
if (currentScreen is Screen.DrawerScreen || currentScreen == Screen.BottomBarScreen.Home) {
    NavigationBar(modifier = Modifier.wrapContentSize()) { ... }
}
```

## 질문 전제 점검

- **`is`는 타입을 검사하는 연산자다.** 자바의 `instanceof`에 해당한다. `==`가 **"값이 같은가"**를 묻는다면 `is`는 **"이 타입인가"**를 묻는다.

  > "Use the `is` operator (or `!is` to negate it) to check if an object matches a type at runtime"

- **그래서 위 조건문은 "성격이 다른 두 질문"을 `||`로 묶고 있다.** 읽을 때 이 점을 분리해야 한다.

  ```kotlin
  currentScreen is Screen.DrawerScreen          // "드로어 화면 부류인가?"  → 타입
  currentScreen == Screen.BottomBarScreen.Home  // "바로 그 Home 인가?"    → 값
  ```

  `Screen.DrawerScreen`은 자식이 셋(`Account`, `Subscription`, `AddAccount`)인 `sealed class`라서 **부류 전체**를 한 번에 검사하려면 `is`가 필요하다. 반면 `Home`은 `object` 하나이므로 `==`로 충분하다. → [`kotlin-sealed-class-and-interface.md`](kotlin-sealed-class-and-interface.md)

- **`object`를 `is`로 검사해도 동작한다.** `currentScreen is Screen.BottomBarScreen.Home`도 컴파일되고 같은 결과를 낸다. `object`는 인스턴스가 하나뿐이라 "타입이 맞다 = 그 인스턴스다"가 성립하기 때문이다. 즉 **여기서 `is`와 `==`를 섞어 쓴 것은 필연이 아니라 스타일**이다. 통일하는 편이 읽기 좋다.

- **`is`의 진짜 값어치는 검사 자체가 아니라 "검사 다음"에 있다.** 검사에 성공하면 컴파일러가 **자동으로 타입을 바꿔 준다**(스마트 캐스트). 지금 코드는 `currentScreen`의 프로퍼티를 꺼내 쓰지 않아서 이 이점을 전혀 안 쓰고 있다.

## 공부할 내용

### 기본 사용법

```kotlin
if (input is String) {
    println("Message length: ${input.length}")   // input 이 String 으로 취급된다
}
if (input !is String) {
    println("Input is not a valid message")
}
```

`!is`는 부정형이다. `!(a is B)`로 쓸 수도 있지만 `a !is B`가 관례다.

### 스마트 캐스트 — 캐스팅을 안 써도 되는 이유

> "The compiler tracks the type checks and explicit casts for immutable values and inserts implicit (safe) casts automatically"

자바라면 이렇게 써야 한다.

```java
if (screen instanceof DrawerScreen) {
    DrawerScreen d = (DrawerScreen) screen;   // 한 번 더 캐스팅
    use(d.getDTitle());
}
```

코틀린은 검사한 뒤부터 **그 변수를 해당 타입으로 다룬다.**

```kotlin
if (currentScreen is Screen.DrawerScreen) {
    Text(currentScreen.dTitle)    // 캐스팅 없이 DrawerScreen 의 프로퍼티에 접근
}
```

`when`, `while`, `&&`, `||`에서도 동작한다.

```kotlin
if (screen is Screen.DrawerScreen && screen.dRoute == "account") { ... }
//                                    ↑ 여기서 이미 DrawerScreen 이다

if (screen !is Screen.DrawerScreen) return
// 이 줄 아래부터는 screen 이 DrawerScreen 이다 (조기 반환 덕분에)
```

### 스마트 캐스트가 안 되는 경우

이게 실무에서 훨씬 자주 부딪힌다. 컴파일러는 **"검사한 뒤 값이 바뀌지 않는다"**를 보장할 수 있을 때만 스마트 캐스트한다.

| 대상 | 스마트 캐스트 |
| --- | --- |
| `val` 지역 변수 | 된다 (위임 프로퍼티 제외) |
| `val` 프로퍼티 (`private`/`internal`/같은 모듈) | 된다 |
| `var` 지역 변수 (검사와 사용 사이에 수정 없음) | 된다 |
| `var` 프로퍼티 | **안 된다** |
| 커스텀 getter가 있는 `val` | **안 된다** |

```kotlin
class A {
    var screen: Screen? = null
    fun f() {
        if (screen is Screen.DrawerScreen) {
            // screen.dTitle  ← 컴파일 에러: smart cast impossible
        }
    }
}
```

다른 스레드가 그 사이에 `screen`을 바꿀 수 있기 때문이다. 해결은 **지역 `val`에 담는 것**이다.

```kotlin
val s = screen
if (s is Screen.DrawerScreen) {
    Text(s.dTitle)   // OK
}
```

### `as`와 `as?`

`is`가 "물어보는" 연산자라면 `as`는 "단언하는" 연산자다.

> "If a cast fails with the `as` operator, a `ClassCastException` is thrown at runtime. That's why it's also called the unsafe operator."

```kotlin
val d = screen as Screen.DrawerScreen    // 틀리면 ClassCastException
val d = screen as? Screen.DrawerScreen   // 틀리면 null
```

> "If you use the `as?` operator instead, and the cast fails, the operator returns `null`. That's why it's also called the safe operator"

**되도록 `as`를 쓰지 않는다.** `is` + 스마트 캐스트, 아니면 `as?` + `?:`로 처리한다.

```kotlin
val title = (screen as? Screen.DrawerScreen)?.dTitle ?: "Unknown"
```

같은 파일의 `Navigation` 함수에 `as`가 하나 쓰이고 있다.

```kotlin
NavHost(navController = navController as NavHostController, ...)
```

이건 **파라미터를 `NavController`로 받아 놓고 `NavHostController`가 필요해서 억지로 내리는 형태**다. 애초에 `NavHostController`로 받으면 캐스팅이 필요 없다. `as`가 보이면 대개 **타입 설계를 다시 볼 신호**다.

### `is`와 `==`, `===`

| 연산자 | 묻는 것 | 예 |
| --- | --- | --- |
| `is` | 타입이 맞는가 | `x is String` |
| `==` | 값이 같은가 (`equals`) | `x == "abc"` |
| `===` | 같은 인스턴스인가 | `x === y` |

`data class`는 `equals`가 생성되어 `==`가 내용 비교가 되지만, 일반 `class`는 `==`가 기본적으로 **참조 비교**다. `Screen.BottomBarScreen.Home`은 `object`라서 인스턴스가 하나뿐이므로 `==`와 `===`가 같은 결과를 낸다.

### 제네릭에서의 제약

JVM은 실행 시점에 제네릭 타입 인자를 지운다(type erasure). 그래서 이런 검사는 불가능하다.

```kotlin
if (list is List<String>) { }   // 컴파일 에러
if (list is List<*>) { }        // OK — 원소 타입은 묻지 않는다
```

`inline` + `reified`를 쓰면 함수 안에서는 가능해진다.

```kotlin
inline fun <reified T> Any.asOrNull(): T? = this as? T
```

## 관련 아키텍처와 베스트 프랙티스

### `is`는 `when`과 함께 쓸 때 가장 강하다

`sealed` 타입을 `when`으로 분기하면 컴파일러가 **모든 경우를 다뤘는지 검사**한다.

```kotlin
when (currentScreen) {
    is Screen.DrawerScreen    -> DrawerLayout(currentScreen)
    is Screen.BottomBarScreen -> BottomBarLayout(currentScreen)
    // else 가 필요 없다 — 화면 종류를 추가하면 여기가 컴파일 에러가 된다
}
```

지금 코드의 `if (... || ...)` 형태는 **화면이 늘어나도 컴파일러가 아무 말을 안 한다.** 조건을 빠뜨려도 조용히 지나간다는 뜻이다.

### 타입 검사가 많아지면 설계를 의심한다

`is`로 분기하는 코드가 여러 곳에 흩어지면, **타입 자체에 그 정보를 담는 편**이 낫다. 지금 코드를 예로 들면 "하단 바를 보여 줄 것인가"를 조건문이 아니라 화면 정의에 담을 수 있다.

```kotlin
sealed class Screen(val title: String, val route: String, val showBottomBar: Boolean)

// 사용하는 쪽
if (currentScreen.showBottomBar) { NavigationBar { ... } }
```

**조건이 한 곳에 모이고, 화면을 추가할 때 그 자리에서 결정하게 된다.** 이것이 다형성이 조건문보다 나은 전형적인 자리다.

### `is`로 분기할 때 `else`를 쓰지 않는다

```kotlin
// 나쁨: 새 타입이 조용히 else 로 흘러든다
when (screen) {
    is Screen.DrawerScreen -> ...
    else -> ...
}
```

`sealed`를 쓴 이유가 사라진다. → [`kotlin-sealed-class-and-interface.md`](kotlin-sealed-class-and-interface.md)

### 코틀린 답게 쓰는 관용구

```kotlin
// 확장 함수로 검사와 사용을 묶는다
fun Screen.drawerTitleOrNull(): String? = (this as? Screen.DrawerScreen)?.dTitle

// filterIsInstance — 컬렉션에서 특정 타입만 고른다
val drawerScreens = screens.filterIsInstance<Screen.DrawerScreen>()
```

`filterIsInstance`는 `filter { it is T }.map { it as T }`를 한 번에 해 주고 **반환 타입까지 좁혀 준다.**

## 체크리스트

- [ ] `is`가 타입 검사, `==`가 값 비교라는 것을 구분할 수 있다.
- [ ] `!is`의 사용법을 안다.
- [ ] 스마트 캐스트가 무엇인지 설명할 수 있다.
- [ ] `var` 프로퍼티에서 스마트 캐스트가 안 되는 이유를 설명할 수 있다.
- [ ] 지역 `val`에 담아 스마트 캐스트 문제를 푸는 방법을 안다.
- [ ] `as`와 `as?`의 차이를 안다.
- [ ] `as`가 보이면 타입 설계를 의심해야 하는 이유를 안다.
- [ ] `==`와 `===`의 차이를 안다.
- [ ] 제네릭 타입 인자는 `is`로 검사할 수 없는 이유를 안다.
- [ ] `sealed` + `when` + `is` 조합의 완전성 검사를 설명할 수 있다.
- [ ] 타입 검사 분기를 다형성이나 프로퍼티로 대체하는 판단을 할 수 있다.

## 공식 참고 자료

- [Kotlin Docs: Type checks and casts](https://kotlinlang.org/docs/typecasts.html)
- [Kotlin Docs: Conditions and loops (`when`)](https://kotlinlang.org/docs/control-flow.html)
- [Kotlin Docs: Sealed classes and interfaces](https://kotlinlang.org/docs/sealed-classes.html)
- [Kotlin Docs: Equality](https://kotlinlang.org/docs/equality.html)
- [Kotlin Docs: Null safety](https://kotlinlang.org/docs/null-safety.html)
- [Kotlin Docs: Inline functions (`reified`)](https://kotlinlang.org/docs/inline-functions.html)
- [Kotlin Docs: Coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
