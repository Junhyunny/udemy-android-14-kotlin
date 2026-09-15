# `popBackStack`과 `navigateUp`은 무엇이 다른가

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/AddEditDetailView.kt`](../chapter205/app/src/main/java/com/example/chapter_205/AddEditDetailView.kt)
- 질문: 두 메서드의 차이점은 뭔가? `popBackStack`과 `navigateUp`은 앱에서 봤을 때 동작이 동일하다.

```kotlin
AppBarView(title = ...) {
    // navController.popBackStack()
    navController.navigateUp()
}
```

## 질문 전제 점검

- **"앱에서 봤을 때 동작이 동일하다"** → **관찰이 정확하다. 그리고 공식 문서도 그렇게 말한다.**

  > "The Back button appears in the system navigation bar at the bottom of the screen and is used to navigate in reverse-chronological order through the history of screens the user has recently worked with. When you press the Back button, the current destination is popped off the top of the back stack, and you then navigate to the previous destination.
  >
  > The Up button appears in the app bar at the top of the screen. **Within your app's task, the Up and Back buttons behave identically.**"

  **앱 안에서만 오간다면 둘은 같다.** 지금 `chapter205`처럼 화면이 둘뿐이고 딥링크도 없는 앱에서는 차이가 드러날 상황 자체가 없다.

- **그러면 왜 두 개가 있나** → **앱 경계를 넘을 때 달라지기 때문이다.** 차이는 딱 두 가지 상황에서 나타난다.

  > "If a user is at the app's start destination, then the Up button does not appear, because **the Up button never exits the app**. The Back button, however, is shown and does exit the app.
  >
  > When your app is launched using a deep link on another app's task, **Up transitions users back to your app's task** and through a simulated back stack and not to the app that triggered the deep link. The Back button, however, does take you back to the other app."

  ```
  ① 시작 화면에서 누르면
     popBackStack()  → 스택이 비어 앱을 나간다 (false 반환)
     navigateUp()    → 아무 일도 안 한다 (false 반환)

  ② 딥링크로 다른 앱에서 들어왔을 때
     popBackStack()  → 그 앱으로 돌아간다
     navigateUp()    → 내 앱의 상위 화면으로 간다 (가상 백스택 생성)
  ```

- **그래서 "무엇을 쓸 것인가"의 답은 버튼의 성격으로 정해진다.**

  | 버튼 | 메서드 |
  | --- | --- |
  | 앱바의 **Up**(←, 뒤로 화살표) | **`navigateUp()`** |
  | 시스템 **Back**(제스처, 하단 버튼) | `popBackStack()` (또는 시스템에 맡긴다) |
  | "저장 후 닫기" 같은 프로그램적 이동 | `popBackStack()` |

  `chapter205`는 **앱바의 뒤로 화살표**에 `navigateUp()`을 쓰고 있다. **올바른 선택이다.**

## 공부할 내용

### 반환값이 다른 의미를 갖는다

둘 다 `Boolean`을 돌려주는데 뜻이 다르다.

```kotlin
val popped = navController.popBackStack()
// true  = 스택에서 하나 꺼냈다
// false = 꺼낼 것이 없었다 (그래프의 시작 화면이었다)

val wentUp = navController.navigateUp()
// true  = 위로 이동했다
// false = 이동하지 않았다 (시작 화면이라 올라갈 곳이 없다)
```

**`popBackStack()`이 `false`를 돌려줬다면 화면이 그대로 남아 있다는 뜻**이므로, 액티비티를 직접 닫아야 할 수도 있다.

```kotlin
if (!navController.popBackStack()) {
    activity.finish()
}
```

`navigateUp()`은 이 처리를 안에서 해 준다. 액티비티에 `NavController`가 연결되어 있으면 적절히 상위로 보낸다.

### `popBackStack`의 여러 형태

```kotlin
// ① 하나만 꺼낸다
navController.popBackStack()

// ② 특정 목적지까지 꺼낸다
navController.popBackStack(route = Screen.HomeScreen.route, inclusive = false)
//   inclusive = false → home_screen 은 남긴다 (home 으로 돌아간다)
//   inclusive = true  → home_screen 까지 꺼낸다

// ③ 타입 안전 route
navController.popBackStack<HomeRoute>(inclusive = false)
```

`navigateUp()`에는 이런 옵션이 없다. **"한 단계 위로"만 한다.**

### 백스택을 정리하며 이동하기

"저장 후 목록으로 돌아가되, 뒤로 가기로 편집 화면에 다시 오지 않게" 같은 요구는 `navigate`의 옵션으로 푼다.

```kotlin
navController.navigate(Screen.HomeScreen.route) {
    popUpTo(Screen.HomeScreen.route) { inclusive = true }
    launchSingleTop = true
}
```

| 옵션 | 뜻 |
| --- | --- |
| `popUpTo(route)` | 그 목적지가 나올 때까지 스택을 꺼낸다 |
| `inclusive = true` | 그 목적지까지 포함해서 꺼낸다 |
| `launchSingleTop = true` | 맨 위가 같은 목적지면 새로 쌓지 않는다 |
| `restoreState / saveState` | 탭 전환 시 각 탭의 상태를 보존 |

**로그인 후 홈으로 보낼 때** 자주 쓰는 형태다.

```kotlin
navController.navigate(Home) {
    popUpTo(0) { inclusive = true }     // 스택 전체를 비운다
}
```

`chapter157`에서 "돌아가기"를 `navigate("firstScreen")`으로 구현해 백스택이 무한히 쌓이던 문제가 바로 이 도구를 몰라서 생긴 것이다. → [`android-navigation-string-route-vs-type-safe.md`](android-navigation-string-route-vs-type-safe.md)

### 시스템 Back은 직접 처리하지 않아도 된다

`NavHost`가 **시스템 뒤로 가기를 자동으로 처리한다.** 별도로 `popBackStack()`을 연결할 필요가 없다.

가로채야 할 때만 개입한다.

```kotlin
// 편집 중이면 확인 다이얼로그를 띄운다
BackHandler(enabled = hasUnsavedChanges) {
    showConfirmDialog = true
}
```

`chapter205`처럼 **입력 중인 폼이 있는 화면**에서는 이런 처리가 어울린다. 지금은 뒤로 가면 입력이 그냥 사라진다.

### 중복 호출을 조심한다

빠르게 두 번 누르면 두 화면이 한꺼번에 사라질 수 있다.

```kotlin
// 방어
val lifecycleState = navController.currentBackStackEntry?.lifecycle?.currentState
if (lifecycleState == Lifecycle.State.RESUMED) {
    navController.navigateUp()
}
```

`AddEditDetailView`에서 **저장 버튼과 앱바 뒤로 가기가 모두 `navigateUp()`을 부르는** 구조라, 타이밍에 따라 두 번 호출될 여지가 있다.

### Navigation 3에서는

Nav3에는 `NavController`가 없고 백스택이 그냥 리스트다.

```kotlin
backStack.removeLastOrNull()        // popBackStack() 에 해당
```

`navigateUp()`에 해당하는 것은 없다. **"위로"라는 개념이 Nav2의 계층 구조 가정에서 나온 것**이라, 백스택을 직접 소유하는 모델에서는 필요가 없다. 딥링크로 들어온 경우의 가상 백스택도 직접 구성한다.

```kotlin
// 딥링크로 상세 화면에 바로 들어왔다면
val backStack = rememberNavBackStack(HomeRoute, DetailRoute(id))   // 직접 쌓는다
```

→ [`android-navigation-compose-vs-navigation3.md`](android-navigation-compose-vs-navigation3.md)

## 관련 아키텍처와 베스트 프랙티스

### 화면 컴포저블은 어느 쪽인지 몰라야 한다

```kotlin
// AppBarView 는 콜백만 받는다 — 잘한 구조
fun AppBarView(title: String, onBackNavClick: () -> Unit = {})

// 호출부에서 결정한다
AppBarView(title = "Add Wish") { navController.navigateUp() }
```

`AppBarView`가 `navigateUp`인지 `popBackStack`인지 모르므로, 정책이 바뀌어도 이 컴포저블은 안 바뀐다. → [`compose-navigation-prop-drilling.md`](compose-navigation-prop-drilling.md)

다만 `chapter205`의 `AppBarView`는 **`onBackNavClick` 파라미터를 받아 놓고 `navigationIcon` 슬롯에 연결하지 않은 상태**였다. 현재는 연결되어 뒤로 화살표가 보인다.

### Up 버튼을 시작 화면에 두지 않는다

> "If a user is at the app's start destination, then the Up button does not appear, because the Up button never exits the app."

`HomeView`의 `AppBarView`에는 뒤로 화살표가 없어야 한다. 실제로 `AppBarView(title = "Wish List")`로 콜백을 안 넘기고 있다. **기본값 `{}`가 들어가므로 눌러도 아무 일이 없다.** 아이콘 자체를 감추는 편이 더 낫다.

```kotlin
@Composable
fun AppBarView(
    title: String,
    onBackNavClick: (() -> Unit)? = null      // null 이면 아이콘을 그리지 않는다
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBackNavClick != null) {
                IconButton(onClick = onBackNavClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                }
            }
        }
    )
}
```

`Icons.AutoMirrored`를 쓰면 **아랍어처럼 오른쪽에서 왼쪽으로 읽는 언어에서 화살표가 자동으로 뒤집힌다.**

### 정리표

| 하고 싶은 일 | 방법 |
| --- | --- |
| 앱바 Up 버튼 | `navigateUp()` |
| 한 단계 뒤로 | `popBackStack()` |
| 특정 화면까지 되돌리기 | `popBackStack(route, inclusive)` |
| 현재 화면 대체 | `navigate(R) { popUpTo(현재) { inclusive = true } }` |
| 스택 비우고 이동 | `navigate(R) { popUpTo(0) { inclusive = true } }` |
| 같은 화면 중복 방지 | `launchSingleTop = true` |
| 시스템 Back 가로채기 | `BackHandler { }` |

## 체크리스트

- [ ] 앱 안에서는 두 메서드의 동작이 같다는 공식 설명을 안다.
- [ ] 시작 화면에서 둘의 동작이 갈리는 이유를 설명할 수 있다.
- [ ] 딥링크로 들어왔을 때 둘의 동작 차이를 설명할 수 있다.
- [ ] Up 버튼이 앱을 벗어나지 않는다는 원칙을 안다.
- [ ] 앱바 뒤로 가기에 `navigateUp()`을 쓰는 이유를 말할 수 있다.
- [ ] 두 메서드의 반환값 의미를 구분할 수 있다.
- [ ] `popBackStack(route, inclusive)`의 동작을 안다.
- [ ] `popUpTo`, `inclusive`, `launchSingleTop`의 역할을 설명할 수 있다.
- [ ] 시스템 Back을 `NavHost`가 자동 처리한다는 것을 안다.
- [ ] `BackHandler`가 필요한 상황을 예로 들 수 있다.
- [ ] 시작 화면에 Up 버튼을 두지 않아야 하는 이유를 안다.
- [ ] `Icons.AutoMirrored`를 쓰는 이유를 안다.

## 공식 참고 자료

- [Android Developers: Principles of navigation](https://developer.android.com/guide/navigation/principles)
- [Android Developers: Navigate back to a destination (back stack)](https://developer.android.com/guide/navigation/backstack)
- [Android Developers: Navigate to a destination](https://developer.android.com/guide/navigation/use-graph/programmatic)
- [Android Developers: `NavController` reference](https://developer.android.com/reference/androidx/navigation/NavController)
- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation)
- [Android Developers: Provide custom back navigation](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
- [Android Developers: App bars](https://developer.android.com/develop/ui/compose/components/app-bars)
