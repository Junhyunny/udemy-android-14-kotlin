# `NavHost`, `NavController`, `composable`, `dialog`의 역할

## 질문이 나온 코드

- [`chapter186/app/src/main/java/com/example/chapter_186/MainActivity.kt`](../chapter186/app/src/main/java/com/example/chapter_186/MainActivity.kt)
- 질문: `NavHost`와 `NavController`, `composable` 함수, `dialog` 함수는 무슨 역할을 하는 거야? 각자 역할을 설명해줘. `composable`과 `dialog`는 엄연히 다른데 같은 곳에 사용하네.

```kotlin
val navController: NavHostController = rememberNavController()

NavHost(navController = navController, startDestination = "shoppinglistscreen") {
    composable("shoppinglistscreen") {
        ShoppingListApp(...)
    }
    dialog("locationscreen") {
        viewModel.location.value?.let { LocationSelectionScreen(location = it) { ... } }
    }
}
```

## 질문 전제 점검

- **"`composable`과 `dialog`는 엄연히 다른데 같은 곳에 쓴다"는 관찰이 정확하고, 그게 바로 이 설계의 핵심이다.** 둘은 **같은 것의 두 종류**다. 공통점이 "목적지(destination)"이고, 차이는 **그려지는 방식**뿐이다.

  ```
  목적지(destination) ─┬─ composable  → 화면 전체를 차지한다
                      └─ dialog      → 이전 화면 위에 떠 있는 창으로 그린다
  ```

  백스택에 쌓이는 것, `navigate()`로 가는 것, `popBackStack()`으로 돌아오는 것, 인자를 받는 것은 **완전히 동일하다.** "다이얼로그도 하나의 화면으로 취급한다"는 것이 Navigation의 관점이다.

- **네 가지의 역할을 한 줄로 정리하면 이렇다.**

  | | 비유 | 역할 |
  | --- | --- | --- |
  | `NavHost` | **액자** | 현재 목적지를 실제로 그리는 자리 |
  | `NavController` | **리모컨** | "어디로 가라", "돌아가라"를 지시 |
  | `composable` | **등록 카드** | "이 route에 이 화면이 있다"고 그래프에 등록 |
  | `dialog` | **등록 카드(다이얼로그용)** | 위와 같되 창으로 그린다고 표시 |

  **`composable`과 `dialog`는 화면을 그리는 함수가 아니다.** `NavHost { }` 블록이 실행될 때 **그래프를 만드는 등록 작업**만 한다. `LazyColumn { items(...) }`이 항목을 즉시 그리지 않고 등록만 하는 것과 같은 구조다.

- **그래서 `NavHost { }` 안의 코드는 "지금 실행되는 코드"가 아니다.** 지금 코드에서 혼동하기 쉬운 부분이다.

  ```kotlin
  dialog("locationscreen") {
      Log.d("navigation", "hello")      // ← 목적지에 진입했을 때만 찍힌다
      ...
  }
  ```

  이 `Log.d`는 `NavHost`가 만들어질 때가 아니라 **`locationscreen`으로 이동했을 때** 찍힌다. 람다 안은 그 목적지의 **내용물**이기 때문이다.

## 공부할 내용

### `NavController` — 상태를 들고 있는 주체

내비게이션의 **모든 상태가 여기 있다.**

```kotlin
navController.navigate("locationscreen")   // 이동
navController.popBackStack()               // 뒤로
navController.navigateUp()                 // 상위로
navController.currentBackStackEntry        // 현재 목적지 정보
```

들고 있는 것은 셋이다.

| 보유 상태 | 설명 |
| --- | --- |
| **그래프** | 어떤 route에 어떤 목적지가 있는지 |
| **백스택** | 지금까지 쌓인 화면 목록 |
| **Navigator 목록** | 목적지 종류별로 "어떻게 그릴지" 아는 객체들 |

세 번째가 `composable`과 `dialog`를 가르는 지점이다. `rememberNavController()`는 **`ComposeNavigator`와 `DialogNavigator`를 미리 등록**해 둔다. 그래서 두 종류의 목적지를 쓸 수 있다.

**`NavController`는 UI가 아니다.** 화면을 그리지 않는다. "지금 어디에 있어야 하는가"만 안다.

### `NavHost` — 그리는 자리

`NavController`가 아는 "지금 있어야 할 곳"을 **실제 컴포저블로 바꿔 놓는** 것이 `NavHost`다.

```kotlin
NavHost(
    navController = navController,          // 어떤 컨트롤러를 따를 것인가
    startDestination = "shoppinglistscreen" // 처음 보여 줄 곳
) {
    // 그래프 정의 (NavGraphBuilder 스코프)
}
```

동작 순서는 이렇다.

```
1. NavHost 가 처음 컴포즈될 때
   → 블록을 실행해 그래프를 만들고 navController 에 설정한다
2. navController 의 현재 목적지를 관찰한다
3. 목적지가 바뀌면 → 해당 목적지의 람다를 컴포즈한다
```

**2번이 핵심이다.** `NavHost`는 `NavController`의 백스택을 **상태로 구독**한다. 그래서 `navigate()`를 부르면 자동으로 화면이 바뀐다. 직접 화면을 교체하는 코드를 쓸 필요가 없다.

`NavHost`가 하는 일이 하나 더 있다. **각 목적지마다 `ViewModelStoreOwner`, `LifecycleOwner`, `SavedStateRegistryOwner`를 제공**한다. 그래서 목적지별로 `viewModel()`을 부르면 **그 화면 전용 ViewModel**을 얻는다.

→ [`069-android-navigation-backstackentry-savedstatehandle.md`](069-android-navigation-backstackentry-savedstatehandle.md)

### `composable` — 전체 화면 목적지 등록

```kotlin
composable(
    route = "detail/{itemId}",
    arguments = listOf(navArgument("itemId") { type = NavType.StringType })
) { backStackEntry ->
    val itemId = backStackEntry.arguments?.getString("itemId")
    DetailScreen(itemId)
}
```

람다가 받는 `NavBackStackEntry`가 **그 목적지의 신분증**이다. 인자, 저장된 상태, 라이프사이클이 여기 들어 있다.

→ [`067-android-navigation-navargument-setup.md`](067-android-navigation-navargument-setup.md)

### `dialog` — 창으로 뜨는 목적지 등록

```kotlin
dialog(
    route = "locationscreen",
    dialogProperties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        usePlatformDefaultWidth = false   // 전체 너비를 쓰고 싶을 때
    )
) {
    LocationSelectionScreen(...)
}
```

`composable`과의 차이는 **`DialogNavigator`가 내용물을 `Dialog` 컴포저블로 감싼다**는 것뿐이다.

| | `composable` | `dialog` |
| --- | --- | --- |
| 그려지는 방식 | 화면 전체 교체 | 이전 화면 **위에** 겹침 |
| 이전 화면 | 사라진다 | **뒤에 보인다** |
| 백스택 | 쌓인다 | **쌓인다** (같다) |
| 뒤로 가기 | 이전 화면으로 | 다이얼로그 닫힘 |
| 전용 옵션 | 화면 전환 애니메이션 | `DialogProperties` |

**"이전 화면이 뒤에 남는가"**가 실질적인 판단 기준이다.

### 언제 `dialog`를 쓰고 언제 `composable`을 쓰나

| 상황 | 선택 |
| --- | --- |
| 목록 → 상세 | `composable` |
| 지도에서 위치 고르기 (되돌아올 곳이 명확) | `dialog` 또는 `composable` |
| 짧은 확인/입력 | `dialog` |
| 삭제 확인 같은 **일회성 확인** | 목적지로 만들지 않고 `AlertDialog` 상태로 처리 |

마지막 줄이 중요하다. **모든 다이얼로그를 목적지로 만들 필요는 없다.** 지금 프로젝트도 두 방식을 함께 쓰고 있다.

```kotlin
// ShoppingList.kt — 목적지가 아닌 그냥 상태 기반 다이얼로그
var showDialog by remember { mutableStateOf(false) }
if (showDialog) { AlertDialog(...) }

// MainActivity.kt — 목적지로 등록된 다이얼로그
dialog("locationscreen") { LocationSelectionScreen(...) }
```

**판단 기준은 "딥링크로 진입할 수 있어야 하는가", "뒤로 가기 동작이 백스택에 속하는가"다.** 아이템 추가 폼은 화면 안의 일시적 상태이므로 `AlertDialog`가 맞고, 위치 선택은 결과를 들고 돌아오는 독립 단계이므로 목적지가 될 만하다.

→ [`044-compose-modal-bottom-sheet-state-and-sheetstate.md`](044-compose-modal-bottom-sheet-state-and-sheetstate.md)에서 다룬 "컴포지션 존재 여부 vs 애니메이션"과 비슷한 층위 구분이다.

### 그래프에 등록할 수 있는 다른 것들

`NavGraphBuilder`에는 이 둘 말고도 있다.

```kotlin
NavHost(...) {
    composable("home") { ... }
    dialog("confirm") { ... }

    navigation(startDestination = "step1", route = "onboarding") {   // 중첩 그래프
        composable("step1") { ... }
        composable("step2") { ... }
    }
}
```

`navigation { }`은 **여러 목적지를 하나로 묶는다.** 온보딩처럼 "여러 단계가 한 덩어리"인 흐름에서 통째로 `popUpTo("onboarding")` 할 수 있다.

바텀 시트를 목적지로 쓰려면 별도 아티팩트가 필요하다(`bottomSheet { }`).

## 관련 아키텍처와 베스트 프랙티스

### 목적지 람다 안에서 조건부로 그리지 않는다

지금 코드의 이 부분은 함정이 있다.

```kotlin
dialog("locationscreen") {
    viewModel.location.value?.let {          // ← null 이면 빈 다이얼로그
        LocationSelectionScreen(location = it) { ... }
    }
}
```

`location`이 아직 `null`이면 **다이얼로그는 떠 있는데 안이 비어 있다.** 위치를 받아오는 데 시간이 걸리므로 실제로 발생할 수 있다. 사용자는 빈 창을 보게 된다.

```kotlin
dialog("locationscreen") {
    when (val loc = viewModel.location.value) {
        null -> LoadingDialog()                       // 로딩 표시
        else -> LocationSelectionScreen(location = loc) { ... }
    }
}
```

**목적지는 "항상 무언가를 그린다"고 가정하고 만든다.**

### route 문자열을 상수로 모은다

```kotlin
// 흩어져 있으면 오타를 컴파일러가 못 잡는다
composable("shoppinglistscreen") { ... }
navController.navigate("locationscreen")
```

`sealed class`로 모으거나, 더 나아가 타입 안전 route로 간다.

→ [`003-kotlin-sealed-class-and-interface.md`](003-kotlin-sealed-class-and-interface.md), [`066-android-navigation-string-route-vs-type-safe.md`](066-android-navigation-string-route-vs-type-safe.md)

### 결과를 돌려주는 방법

지금 코드는 **콜백으로 결과를 전달**한다.

```kotlin
LocationSelectionScreen(location = it) { location ->
    viewModel.fetchAddress("${location.latitude},${location.longitude}")
    navController.popBackStack()
}
```

두 화면이 **같은 ViewModel을 공유**하기 때문에 가능한 방식이다. ViewModel을 공유하지 않는다면 `savedStateHandle`로 결과를 돌려준다.

```kotlin
// 다이얼로그에서
navController.previousBackStackEntry
    ?.savedStateHandle?.set("selected_location", location)
navController.popBackStack()
```

→ [`069-android-navigation-backstackentry-savedstatehandle.md`](069-android-navigation-backstackentry-savedstatehandle.md)

### `NavHost`는 한 화면에 여러 개 있을 수 있다

바텀 내비게이션에서 탭마다 독립 스택을 두려면 중첩 `NavHost`를 쓴다. 이때 **바깥 컨트롤러와 안쪽 컨트롤러를 헷갈리면** 엉뚱한 스택에 쌓인다. 어느 컨트롤러로 `navigate()`하는지 항상 의식한다.

## 체크리스트

- [ ] `composable`과 `dialog`가 같은 "목적지"의 두 종류임을 설명할 수 있다.
- [ ] 네 가지의 역할을 각각 한 줄로 말할 수 있다.
- [ ] `composable`/`dialog`가 등록만 하고 그리지 않는다는 것을 안다.
- [ ] `NavHost { }` 안의 로그가 언제 찍히는지 설명할 수 있다.
- [ ] `NavController`가 UI가 아니라는 것을 안다.
- [ ] `NavHost`가 백스택을 상태로 구독한다는 것을 설명할 수 있다.
- [ ] Navigator 등록이 목적지 종류를 결정한다는 것을 안다.
- [ ] `NavHost`가 목적지별 ViewModel 스코프를 제공한다는 것을 안다.
- [ ] `dialog`와 `composable`의 실질적 차이를 말할 수 있다.
- [ ] 모든 다이얼로그를 목적지로 만들 필요가 없는 이유를 안다.
- [ ] 목적지를 만들지 말지 판단하는 기준을 말할 수 있다.
- [ ] 중첩 `navigation { }` 그래프의 용도를 안다.

## 공식 참고 자료

- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation)
- [Android Developers: Create a navigation controller](https://developer.android.com/guide/navigation/navcontroller)
- [Android Developers: Design your navigation graph](https://developer.android.com/guide/navigation/design)
- [Android Developers: Navigate to a destination](https://developer.android.com/guide/navigation/navigation-navigate)
- [Android Developers: Navigation and the back stack](https://developer.android.com/guide/navigation/backstack)
- [Android Developers: `androidx.navigation.compose` package summary](https://developer.android.com/reference/kotlin/androidx/navigation/compose/package-summary)
- [Android Developers: `NavGraphBuilder`](https://developer.android.com/reference/kotlin/androidx/navigation/NavGraphBuilder)
- [Android Developers: Dialogs in Compose](https://developer.android.com/develop/ui/compose/components/dialog)
