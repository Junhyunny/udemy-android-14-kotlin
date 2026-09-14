# `currentBackStackEntry`와 `savedStateHandle`은 무엇인가

## 질문이 나온 코드

- [`chapter143/app/src/main/java/com/example/chapter_143/RecipeApp.kt`](../chapter143/app/src/main/java/com/example/chapter_143/RecipeApp.kt)
- 질문: `currentBackStackEntry`는 어떤 객체인가? 네비게이션 변경 히스토리 스택이 쌓이는 객체인가? `savedStateHandle` 함수는? 해당 API 사용 예제를 다양하게 정리해줘.

```kotlin
composable(route = Screen.RecipeScreen.route) {
    RecipeScreen(viewState = viewState) {
        navController.currentBackStackEntry?.savedStateHandle?.set("cat", it)
        navController.navigate(Screen.DetailScreen.route)
    }
}
composable(route = Screen.DetailScreen.route) {
    val value = navController.previousBackStackEntry?.savedStateHandle?.get<Category>("cat")
        ?: Category("", "", "", "")
    CategoryDetailScreen(category = value)
}
```

## 질문 전제 점검

- **"네비게이션 변경 히스토리 스택이 쌓이는 객체인가"** → 아니다. 이름 때문에 생기는 오해다. **`NavBackStackEntry`는 스택 그 자체가 아니라 스택에 쌓이는 "한 칸"이다.**

  ```
  백스택 (NavController 가 소유)
  ┌─────────────────────────────┐
  │ [1] NavBackStackEntry  ← RecipeScreen   ← previousBackStackEntry
  │ [2] NavBackStackEntry  ← DetailScreen   ← currentBackStackEntry
  └─────────────────────────────┘
       ↑ 이 하나하나가 NavBackStackEntry
  ```

  스택을 들고 있는 것은 `NavController`이고, `currentBackStackEntry`는 그중 **맨 위 한 칸**을 가리키는 프로퍼티다.

- **`savedStateHandle`은 함수가 아니라 프로퍼티다.** `entry.savedStateHandle`로 접근한다. 그리고 그 정체는 **키-값 저장소**다.

  > "`SavedStateHandle`은 데이터를 저장하고 검색할 수 있는 키-값 맵으로, 프로세스 종료를 포함한 설정 변경을 통해 지속되며, 같은 객체를 통해 계속 사용 가능합니다."

  `Map<String, Any?>`인데 **회전과 프로세스 종료를 견딘다**는 점이 보통 맵과 다르다.

- **그런데 지금 코드는 이 API를 의도와 반대 방향으로 쓰고 있다.** 이게 가장 중요한 지적이다.

  `savedStateHandle`로 값을 주고받는 공식 패턴은 **"뒤 화면 → 앞 화면"으로 결과를 돌려주는 것**이다. 화면 B에서 고른 값을 화면 A로 되돌릴 때 쓴다. 지금 코드는 반대로 **"앞 화면 → 뒤 화면"으로 인자를 넘기는 데** 쓰고 있다.

  ```kotlin
  // 지금: A에서 값을 쓰고 → B로 이동 → B가 A의 handle을 읽는다
  currentBackStackEntry?.savedStateHandle?.set("cat", it)   // A의 handle에 쓴다
  navigate(DetailScreen)
  previousBackStackEntry?.savedStateHandle?.get("cat")      // B에서 A의 handle을 읽는다
  ```

  동작은 한다. 하지만 **인자 전달에는 route 인자를 쓰는 것이 정석**이다. 이 방식의 실제 문제는 아래에서 정리한다.

- **`?: Category("", "", "", "")`라는 기본값도 신호다.** 인자가 없을 수 있다는 것을 코드가 인정하고 있다. route 인자로 넘기면 **없을 수가 없다.** 방어 코드가 필요하다는 것 자체가 전달 방식이 어긋났다는 증거다.

## 공부할 내용

### `NavBackStackEntry`의 정체

화면 하나가 백스택에 올라갈 때 만들어지는 객체다. 세 가지 역할을 겸한다.

```kotlin
class NavBackStackEntry : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner
```

| 겸하는 역할 | 덕분에 가능한 일 |
| --- | --- |
| `LifecycleOwner` | 화면별 생명주기 — 백스택에 묻히면 `STARTED` 아래로 내려간다 |
| `ViewModelStoreOwner` | **화면마다 독립된 `ViewModel`**을 가질 수 있다 |
| `SavedStateRegistryOwner` | `savedStateHandle`로 상태를 저장·복원한다 |

이 중 두 번째가 특히 중요하다. 컴포저블에서 `viewModel()`을 부르면 **그 화면의 `NavBackStackEntry`에 스코프된 `ViewModel`**이 만들어진다. 화면이 백스택에서 빠지면 `onCleared()`가 불린다. → [`compose-viewmodel-function-vs-manual.md`](compose-viewmodel-function-vs-manual.md)

`NavController`가 제공하는 접근자들이다.

```kotlin
navController.currentBackStackEntry              // 맨 위 (지금 화면)
navController.previousBackStackEntry             // 그 아래 (이전 화면)
navController.getBackStackEntry(route)           // 특정 route 의 항목
navController.currentBackStackEntryAsState()     // 현재 항목을 State 로 (재구성 유발)
navController.currentBackStackEntryFlow          // 변경을 Flow 로
```

### `currentBackStackEntryAsState()` 활용

현재 화면이 무엇인지 알아야 하는 UI, 예를 들어 하단 탭 선택 표시에 쓴다.

```kotlin
@Composable
fun BottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,     // ← 현재 화면과 비교
                onClick = { navController.navigate(tab.route) },
                icon = { Icon(tab.icon, null) },
                label = { Text(tab.label) }
            )
        }
    }
}
```

`...AsState()`이므로 화면이 바뀌면 **재구성이 일어나 선택 표시가 따라온다.** 그냥 `currentBackStackEntry`를 읽으면 재구성이 안 되어 표시가 갱신되지 않는다.

### `SavedStateHandle`의 세 가지 용도

#### ① 화면에서 결과 돌려받기 — 공식 용도

목록에서 항목을 고르고 이전 화면으로 돌아가는 경우다.

**받는 쪽 (화면 A)**

```kotlin
composable(Screen.Form.route) { entry ->
    val savedStateHandle = entry.savedStateHandle
    val selected by savedStateHandle
        .getStateFlow<String?>("selectedCategory", null)
        .collectAsStateWithLifecycle()

    LaunchedEffect(selected) {
        selected?.let {
            viewModel.applyCategory(it)
            savedStateHandle["selectedCategory"] = null   // ← 한 번만 처리하고 비운다
        }
    }
    FormScreen(...)
}
```

**보내는 쪽 (화면 B)**

```kotlin
PickerScreen(onPick = { category ->
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set("selectedCategory", category)
    navController.popBackStack()                          // ← navigate 가 아니라 popBackStack
})
```

공식 문서의 설명 그대로다.

> `getCurrentBackStackEntry()` API를 사용하여 `NavBackStackEntry`를 가져오고, `SavedStateHandle`이 제공하는 `LiveData`를 `observe`합니다.
>
> ```kotlin
> navController.previousBackStackEntry?.savedStateHandle?.set("key", result)
> ```

**결과를 비우는 것**이 중요하다.

> "결과를 한 번만 처리하려면 `remove()`를 호출하여 결과를 초기화해야 합니다"

비우지 않으면 그 화면으로 돌아올 때마다 예전 결과가 다시 처리된다.

#### ② `ViewModel`에서 route 인자 읽기

`ViewModel` 생성자에 `SavedStateHandle`을 받으면 내비게이션 인자가 들어 있다.

```kotlin
class DetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: CategoryRepository
) : ViewModel() {
    // 문자열 route 방식
    private val categoryId: String = checkNotNull(savedStateHandle["categoryId"])

    // 타입 안전 route 방식
    private val route = savedStateHandle.toRoute<DetailRoute>()

    val category = repository.getCategory(route.categoryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
```

**컴포저블을 거쳐 인자를 전달할 필요가 없어진다.** 이것이 가장 실용적인 사용처다.

#### ③ 프로세스 종료를 견디는 화면 상태 저장

```kotlin
class SearchViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    val query: StateFlow<String> = savedStateHandle.getStateFlow("query", "")

    fun updateQuery(value: String) {
        savedStateHandle["query"] = value     // 앱이 죽었다 살아나도 유지된다
    }
}
```

`ViewModel`은 화면 회전은 견디지만 **프로세스 종료는 견디지 못한다.** `SavedStateHandle`에 넣은 값은 견딘다. 사용자가 앱을 백그라운드에 두고 한참 뒤 돌아와도 검색어가 남는다.

### 담을 수 있는 타입

`Bundle`에 들어갈 수 있어야 한다.

```
원시 타입과 String / Parcelable / Serializable / 이들의 배열·ArrayList
```

`Category`에 `@Parcelize`가 붙은 이유가 이것이다. → [`android-parcelable-vs-serializable.md`](android-parcelable-vs-serializable.md)

그리고 **크기 제한이 따라온다.**

> "Binder 트랜잭션 버퍼는 프로세스 레벨에서 1MB의 고정 크기 제한이 있습니다."

객체 하나는 괜찮지만 목록을 통째로 넣으면 `TransactionTooLargeException`이 난다.

### 지금 코드를 고친다면

**① 타입 안전 route로 인자 전달 (권장)**

```kotlin
@Serializable data object RecipeListRoute
@Serializable data class CategoryDetailRoute(val categoryId: String)

NavHost(navController, startDestination = RecipeListRoute) {
    composable<RecipeListRoute> {
        RecipeScreen(viewState) { category ->
            navController.navigate(CategoryDetailRoute(category.idCategory))
        }
    }
    composable<CategoryDetailRoute> { entry ->
        val route = entry.toRoute<CategoryDetailRoute>()
        val detailViewModel: DetailViewModel = viewModel()    // ID로 조회한다
        CategoryDetailScreen(...)
    }
}
```

이러면 `savedStateHandle`도, `@Parcelize`도, `?: Category("", "", "", "")` 방어 코드도 전부 필요 없어진다. → [`android-navigation-passing-many-arguments.md`](android-navigation-passing-many-arguments.md)

**② 강의 흐름을 유지하면서 최소한만 고치기**

지금 방식을 유지하더라도 **쓰는 쪽을 바꿔야** 한다.

```kotlin
// 지금: 자기 자신의 handle 에 쓰고, 다음 화면이 previous 를 읽는다
currentBackStackEntry?.savedStateHandle?.set("cat", it)
navigate(DetailScreen.route)
```

이 방식의 실질적 문제는 셋이다.

| 문제 | 결과 |
| --- | --- |
| 딥링크로 상세 화면에 바로 들어올 수 없다 | `previousBackStackEntry`가 없어 빈 `Category`가 뜬다 |
| 상세 화면에서 회전하면? | handle은 살아 있지만, 백스택 구조에 의존하는 취약한 구조다 |
| 데이터가 상세 화면 진입 시점의 스냅샷이다 | 목록이 갱신돼도 상세는 옛 값을 본다 |

특히 첫 번째가 치명적이다. **`?: Category("", "", "", "")`가 화면에 빈 상세 페이지를 그리게 된다.**

## 관련 아키텍처와 베스트 프랙티스

### 세 가지 저장소를 구분하기

| | 회전 견딤 | 프로세스 종료 견딤 | 용도 |
| --- | --- | --- | --- |
| `remember` | ❌ | ❌ | 순수 UI 상태 |
| `rememberSaveable` | ✅ | ✅ | 입력값, 스크롤 위치 |
| `ViewModel` | ✅ | ❌ | 화면 상태, 비즈니스 로직 |
| `SavedStateHandle` | ✅ | ✅ | route 인자, 꼭 남아야 하는 값 |

**`ViewModel` + `SavedStateHandle` 조합**이 실무의 기본형이다. 큰 상태는 `ViewModel`에, 복원에 필요한 최소 키는 `SavedStateHandle`에 둔다.

### 방향을 헷갈리지 않기

```
인자 전달 (앞으로)     →  route 인자를 쓴다
결과 반환 (뒤로)       →  savedStateHandle 을 쓴다
```

이 두 가지를 섞으면 코드가 금방 이해하기 어려워진다. 지금 코드가 그 경계에 서 있다.

### `NavController`를 화면 컴포저블에 넘기지 않는다

`RecipeApp`은 이 원칙을 잘 지키고 있다.

```kotlin
RecipeScreen(viewState = viewState) { category -> /* 내비게이션은 여기서 */ }
```

`RecipeScreen`은 `NavController`를 모른다. 덕분에 프리뷰와 테스트가 쉽고, 나중에 Navigation 3로 옮겨도 화면 코드는 그대로다. 다만 콜백이 여러 단계 내려가는 문제는 따로 있다. → [`compose-navigation-prop-drilling.md`](compose-navigation-prop-drilling.md)

### 로그를 컴포저블 본문에 두지 않는다

```kotlin
Log.i("before detail screen call", value.toString())
CategoryDetailScreen(category = value)
Log.i("after detail screen call", value.toString())
```

컴포저블 본문은 **재구성될 때마다 다시 실행**되고, 실행 횟수와 순서를 보장하지 않는다. 그래서 이 로그는 예상보다 많이 또는 적게 찍힌다. 부수 효과는 `LaunchedEffect`나 `SideEffect`에 둔다.

```kotlin
LaunchedEffect(value) { Log.i("detail", value.toString()) }   // value가 바뀔 때만 한 번
```

→ [`compose-recomposition-timing-and-scope.md`](compose-recomposition-timing-and-scope.md)

### Navigation 3에서는

Nav3에는 `NavBackStackEntry`도 `NavController`도 없다. 백스택이 그냥 리스트이고, 키 객체가 곧 인자다.

```kotlin
backStack.add(CategoryDetailRoute(category.idCategory))
// entry<CategoryDetailRoute> { key -> ... key.categoryId }
```

`savedStateHandle`로 값을 실어 나르는 패턴 자체가 사라진다. → [`android-navigation-compose-vs-navigation3.md`](android-navigation-compose-vs-navigation3.md)

## 체크리스트

- [ ] `NavBackStackEntry`가 스택이 아니라 스택의 한 칸임을 설명할 수 있다.
- [ ] `NavBackStackEntry`가 겸하는 세 가지 역할을 말할 수 있다.
- [ ] `currentBackStackEntry`와 `previousBackStackEntry`의 관계를 그림으로 설명할 수 있다.
- [ ] `currentBackStackEntryAsState()`를 써야 재구성이 일어나는 이유를 안다.
- [ ] `SavedStateHandle`이 회전과 프로세스 종료를 견딘다는 것을 안다.
- [ ] 결과 반환 패턴에서 `previousBackStackEntry`에 쓰는 이유를 설명할 수 있다.
- [ ] 결과를 한 번만 처리하려면 비워야 하는 이유를 안다.
- [ ] `ViewModel` 생성자의 `SavedStateHandle`에서 route 인자를 읽을 수 있다.
- [ ] `SavedStateHandle`에 담을 수 있는 타입과 크기 제한을 안다.
- [ ] 인자 전달(앞으로)과 결과 반환(뒤로)의 도구가 다르다는 것을 구분할 수 있다.
- [ ] 지금 코드가 딥링크에서 깨지는 이유를 설명할 수 있다.
- [ ] 컴포저블 본문에 `Log`를 두면 안 되는 이유를 안다.

## 공식 참고 자료

- [Android Developers: Navigate to a destination (결과 반환 포함)](https://developer.android.com/guide/navigation/use-graph/programmatic)
- [Android Developers: `NavBackStackEntry` reference](https://developer.android.com/reference/androidx/navigation/NavBackStackEntry)
- [Android Developers: `SavedStateHandle` reference](https://developer.android.com/reference/androidx/lifecycle/SavedStateHandle)
- [Android Developers: Saved State module for ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate)
- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation)
- [Android Developers: Pass data between destinations](https://developer.android.com/guide/navigation/use-graph/pass-data)
- [Android Developers: Save UI states](https://developer.android.com/topic/libraries/architecture/saving-states)
