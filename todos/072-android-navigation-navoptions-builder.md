# `navigate` 뒤에 오는 람다는 무엇인가 — `NavOptionsBuilder`

## 질문이 나온 코드

- [`chapter186/app/src/main/java/com/example/chapter_186/ShoppingList.kt`](../chapter186/app/src/main/java/com/example/chapter_186/ShoppingList.kt)
- 질문: `navigate` 이후에 등장하는 함수는 무엇이야? 해당 참조를 반환하는 건가? 정확한 용도를 잘 모르겠어. 이해하기 쉽게 설명하고, 사용 케이스들을 정리해줘.

```kotlin
navController.navigate("locationscreen") {
    this.launchSingleTop
}
```

## 질문 전제 점검

- **"참조를 반환하는 건가" → 아니다.** `navigate()`의 반환 타입은 `Unit`이다. 뒤의 `{ }`는 반환값이 아니라 **마지막 파라미터로 넘기는 람다**다. 코틀린의 후행 람다 문법이다.

  ```kotlin
  // 실제 시그니처
  fun navigate(route: String, builder: NavOptionsBuilder.() -> Unit)

  // 그래서 이 둘은 같다
  navController.navigate("locationscreen", { launchSingleTop = true })
  navController.navigate("locationscreen") { launchSingleTop = true }
  ```

  `NavOptionsBuilder.() -> Unit`은 **수신 객체가 있는 람다**다. 블록 안에서 `this`가 `NavOptionsBuilder`가 되므로 `launchSingleTop`을 바로 쓸 수 있다.

  → [`002-kotlin-trailing-lambda-and-content-slot.md`](002-kotlin-trailing-lambda-and-content-slot.md)

- **그리고 지금 코드는 아무 일도 하지 않는다. 이게 이 문서에서 가장 중요한 부분이다.**

  ```kotlin
  navController.navigate("locationscreen") {
      this.launchSingleTop        // ← 값을 읽기만 하고 버린다
  }
  ```

  `launchSingleTop`은 **프로퍼티**다. 저 줄은 "프로퍼티 값을 읽어서 아무 데도 쓰지 않는" 표현식이라 **효과가 전혀 없다.** 의도한 코드는 이것이다.

  ```kotlin
  navController.navigate("locationscreen") {
      launchSingleTop = true      // ← 대입해야 한다
  }
  ```

  **컴파일 에러도 아니고 경고도 잘 안 뜨기 때문에** 알아채기 어렵다. Android Studio에서는 회색으로 흐려지는 정도다. 비슷한 실수가 `popUpTo`, `restoreState`에서도 자주 나온다.

- **이 상황에서 `launchSingleTop`이 실제로 필요한가**도 따져볼 만하다. "Address" 버튼을 빠르게 두 번 누르면 다이얼로그가 두 개 쌓인다. `launchSingleTop = true`를 제대로 넣으면 그게 막힌다. **즉 의도는 맞았고 문법만 틀렸다.**

## 공부할 내용

### `NavOptionsBuilder`가 제공하는 것

```kotlin
navController.navigate("route") {
    launchSingleTop = true
    restoreState = true
    popUpTo("home") {
        inclusive = false
        saveState = true
    }
    anim { enter = R.anim.slide_in; exit = R.anim.slide_out }   // View 기반에서
}
```

| 옵션 | 하는 일 |
| --- | --- |
| `launchSingleTop` | 이미 그 목적지가 **맨 위에 있으면** 새로 쌓지 않는다 |
| `popUpTo(route)` | 지정한 목적지까지 백스택을 **걷어낸 뒤** 이동한다 |
| `popUpTo { inclusive = true }` | 지정한 목적지 **자신까지** 걷어낸다 |
| `popUpTo { saveState = true }` | 걷어내면서 그 스택의 **상태를 저장**한다 |
| `restoreState` | 저장해 둔 상태가 있으면 **복원**한다 |

### `launchSingleTop` — 중복 쌓임 방지

> "`launchSingleTop = true` navigates to a destination only if you're not already on that destination, avoiding multiple copies on the top of the back stack."

```
[없을 때]  버튼 연타 → home → detail → detail → detail
[있을 때]  버튼 연타 → home → detail
```

**"맨 위에 있을 때만"** 동작한다는 점이 중요하다. `home → detail → settings` 상태에서 `detail`로 가면, `detail`이 맨 위가 아니므로 **새로 쌓인다.**

가장 흔한 용도는 **버튼 연타 방지**다. 사용자가 빠르게 두 번 누르면 같은 화면이 두 개 쌓이고, 뒤로 가기를 두 번 눌러야 빠져나가진다.

### `popUpTo` — 스택 걷어내기

```kotlin
// 로그인 성공 → 로그인 화면으로 못 돌아가게
navController.navigate("home") {
    popUpTo("login") { inclusive = true }
}
```

```
이동 전: splash → login
이동 후: home                    (login 까지 inclusive 로 제거)
```

`inclusive = false`(기본값)라면 `login`은 남는다.

```
이동 후: splash → login → home
```

**로그인, 온보딩, 결제 완료처럼 "돌아가면 안 되는" 흐름에서 필수다.**

시작 목적지까지 한 번에 걷어내려면 그래프의 시작점을 참조한다.

```kotlin
navController.navigate("home") {
    popUpTo(navController.graph.startDestinationId) { inclusive = true }
}
```

### 바텀 내비게이션의 정석 조합

세 옵션이 함께 쓰이는 대표적인 자리다.

```kotlin
navController.navigate(item.route) {
    popUpTo(navController.graph.findStartDestination().id) {
        saveState = true          // 떠나는 탭의 상태를 저장
    }
    launchSingleTop = true        // 같은 탭 연타 시 중복 방지
    restoreState = true           // 돌아온 탭의 상태를 복원
}
```

각각이 없으면 어떻게 되는지가 이해의 핵심이다.

| 빠뜨리면 | 증상 |
| --- | --- |
| `popUpTo` | 탭을 옮길 때마다 스택이 무한히 쌓인다. 뒤로 가기를 수십 번 눌러야 한다 |
| `launchSingleTop` | 같은 탭을 두 번 누르면 화면이 겹친다 |
| `saveState` / `restoreState` | 탭을 옮겼다 오면 **스크롤 위치와 입력값이 초기화**된다 |

`saveState`와 `restoreState`는 **짝이다.** 하나만 쓰면 효과가 없다.

> "`restoreState = true` in navigation options automatically restores the back stack and the state associated with the destination."

### 사용 케이스 정리

| 상황 | 옵션 |
| --- | --- |
| 목록 → 상세 (평범한 이동) | **옵션 없음** |
| 버튼 연타 방지 | `launchSingleTop = true` |
| 로그인 성공 후 홈 | `popUpTo("login") { inclusive = true }` |
| 온보딩 완료 후 본 화면 | `popUpTo("onboarding") { inclusive = true }` |
| 바텀 탭 전환 | `popUpTo(start) { saveState = true }` + `launchSingleTop` + `restoreState` |
| 딥링크로 특정 화면 진입 | `popUpTo(start)` 로 스택 정리 |
| 결제 완료 → 주문 내역 | `popUpTo("cart") { inclusive = true }` |
| 알림에서 진입 | 상황에 따라 `popUpTo` + `launchSingleTop` |

**대부분의 이동에는 옵션이 필요 없다.** 필요한 곳에만 쓰는 것이 원칙이다. 습관적으로 붙이면 백스택이 의도와 다르게 동작한다.

### 미리 만들어 재사용하기

같은 옵션을 여러 곳에서 쓴다면 `navOptions { }`로 만들어 둘 수 있다.

```kotlin
val singleTop = navOptions { launchSingleTop = true }
navController.navigate("locationscreen", singleTop)
```

`navigate()`에는 `NavOptions` 객체를 직접 받는 오버로드가 있다. **람다 형태가 결국 이 객체를 만드는 빌더**라는 것을 보여 준다.

### 확인하는 방법

옵션이 실제로 먹었는지 눈으로 확인하려면 백스택을 찍어 본다.

```kotlin
navController.addOnDestinationChangedListener { controller, destination, _ ->
    Log.d("nav", "→ ${destination.route} / 스택: ${controller.currentBackStack.value.map { it.destination.route }}")
}
```

`this.launchSingleTop` 같은 실수를 **로그로 바로 잡아낼 수 있다.** 연타했을 때 스택에 같은 route가 두 번 찍히면 옵션이 안 먹은 것이다.

## 관련 아키텍처와 베스트 프랙티스

### 수신 객체 람다에서 대입을 빠뜨리지 않는다

같은 실수가 나오는 자리들이다.

```kotlin
// 잘못
navigate("x") { launchSingleTop }
LocationRequest.Builder(...).apply { setMinUpdateIntervalMillis(50) }  // 이건 함수라 괜찮다

// 올바름
navigate("x") { launchSingleTop = true }
```

**"프로퍼티인가 함수인가"**를 구분하는 습관이 필요하다. 프로퍼티는 `= 값`, 함수는 `(인자)`다. IDE에서 흐려 보이는 줄은 대개 효과가 없는 줄이다.

### 백스택 설계를 먼저 정한다

옵션은 수단이고, 먼저 정해야 할 것은 **"사용자가 뒤로 가기를 눌렀을 때 어디로 가야 하는가"**다.

```
로그인 → 홈 :  뒤로 가기 = 앱 종료   → popUpTo inclusive
목록 → 상세 :  뒤로 가기 = 목록      → 옵션 없음
탭 A → 탭 B :  뒤로 가기 = 앱 종료   → popUpTo start
```

이 표를 먼저 그리면 어떤 옵션이 필요한지 자동으로 나온다.

### `navigate`를 화면에서 직접 부르지 않는다

```kotlin
// 지금 코드
Button(onClick = {
    locationUtils.requestLocationUpdate(viewModel)
    navController.navigate("locationscreen") { launchSingleTop = true }
})

// 권장: 화면은 "무슨 일이 일어났는지"만 알린다
Button(onClick = onAddressClick)
```

내비게이션 옵션이 화면 코드에 흩어지면 **백스택 정책이 한눈에 안 보인다.** 상위에서 모아 두면 정책을 한 곳에서 관리할 수 있다.

→ [`070-compose-navigation-prop-drilling.md`](070-compose-navigation-prop-drilling.md)

### 뒤로 가기는 `popBackStack`과 `navigateUp`을 구분한다

이동 옵션과 함께 알아 둘 짝이다.

→ [`071-android-navigation-popbackstack-vs-navigateup.md`](071-android-navigation-popbackstack-vs-navigateup.md)

## 체크리스트

- [ ] `navigate` 뒤의 `{ }`가 후행 람다임을 설명할 수 있다.
- [ ] `NavOptionsBuilder.() -> Unit`이 수신 객체 람다임을 안다.
- [ ] `this.launchSingleTop`이 아무 효과가 없는 이유를 설명할 수 있다.
- [ ] 프로퍼티와 함수를 구분해서 쓰는 습관이 있다.
- [ ] `launchSingleTop`이 "맨 위일 때만" 동작한다는 것을 안다.
- [ ] `popUpTo`의 `inclusive` 차이를 설명할 수 있다.
- [ ] `saveState`와 `restoreState`가 짝이라는 것을 안다.
- [ ] 바텀 내비게이션 정석 조합과 각 옵션의 역할을 말할 수 있다.
- [ ] 로그인 후 이동에 `popUpTo inclusive`가 필요한 이유를 안다.
- [ ] 대부분의 이동에는 옵션이 필요 없다는 것을 안다.
- [ ] 백스택을 로그로 확인하는 방법을 안다.

## 공식 참고 자료

- [Android Developers: Navigate with options (`NavOptions`)](https://developer.android.com/guide/navigation/use-graph/navoptions)
- [Android Developers: Navigate to a destination](https://developer.android.com/guide/navigation/navigation-navigate)
- [Android Developers: Navigation and the back stack](https://developer.android.com/guide/navigation/backstack)
- [Android Developers: Support multiple back stacks](https://developer.android.com/guide/navigation/backstack/multi-back-stacks)
- [Android Developers: `NavOptionsBuilder`](https://developer.android.com/reference/kotlin/androidx/navigation/NavOptionsBuilder)
- [Android Developers: `NavOptions`](https://developer.android.com/reference/androidx/navigation/NavOptions)
- [Android Developers: `NavController`](https://developer.android.com/reference/androidx/navigation/NavController)
- [Kotlin Docs: Higher-order functions and lambdas](https://kotlinlang.org/docs/lambdas.html)
- [Kotlin Docs: Type-safe builders](https://kotlinlang.org/docs/type-safe-builders.html)
