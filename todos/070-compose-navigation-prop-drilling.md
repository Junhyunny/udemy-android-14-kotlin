# 내비게이션 콜백이 계속 내려가는 프롭 드릴링, 개선할 수 있나

## 질문이 나온 코드

- [`chapter143/app/src/main/java/com/example/chapter_143/RecipeScreen.kt`](../chapter143/app/src/main/java/com/example/chapter_143/RecipeScreen.kt)
- 질문: `navigateToDetail` 네비게이션 기능이 계속 프롭스 드릴링 하듯이 내부로 전달되어야 하는데, 개선할 수 있는 방법이 있나? 이 컴포저블에서 직접 네비게이션 할 수 있는 함수를 만드는 방식은?

```kotlin
fun RecipeScreen(..., navigateToDetail: (Category) -> Unit)      // 1단계
    → CategoryScreen(viewState.list, navigateToDetail)           // 2단계
        → CategoryItem(category, navigateToDetail)               // 3단계
            → Modifier.clickable { navigateToDetail(category) }  // 실제 사용
```

## 질문 전제 점검

- **"프롭 드릴링 같다"** → 정확한 관찰이다. 세 단계를 거쳐 내려가고 있고, 중간의 `CategoryScreen`은 이 람다를 **쓰지 않고 전달만 한다.** 전형적인 프롭 드릴링이다.

- **하지만 "그러니 나쁘다"로 바로 이어지지는 않는다.** 이 코드는 오히려 컴포즈가 권장하는 방향을 지키고 있다.

  > "A _unidirectional data flow_ (UDF) is a design pattern where state flows down and events flow up."

  상태는 아래로, 이벤트는 위로. `navigateToDetail`은 **위로 올라가는 이벤트 통로**다. 그래서 `CategoryItem`은 내비게이션을 모르고, 자기가 눌렸다는 사실만 알린다. 덕분에 얻는 것이 크다.

  - `CategoryItem`을 프리뷰로 띄울 수 있다 (`{ }`만 넘기면 된다)
  - 테스트에서 "클릭하면 콜백이 불리는가"만 검증하면 된다
  - Nav2에서 Nav3로 바꿔도 **이 파일은 한 줄도 안 바뀐다**

  **드릴링 3단계는 비용이고, 위 세 가지는 이득이다.** 지금 규모에서는 이득이 더 크다.

- **"이 컴포저블에서 직접 네비게이션 할 수 있는 함수를 만드는 방식"** → 가능하지만 **권장하지 않는다.** `CategoryItem`이 `NavController`를 직접 알게 되면 위의 세 가지 이득이 전부 사라진다. 프리뷰도 못 만들고, 테스트하려면 `NavController`를 만들어야 하고, 내비게이션 라이브러리를 바꾸면 화면 코드까지 고쳐야 한다.

  질문의 방향을 살짝 틀면 답이 보인다. **"어떻게 하면 `NavController`를 내려보내지 않으면서도 단계를 줄일까?"**

- **개선 여지는 다른 곳에 있다.** 진짜 문제는 람다가 내려가는 것이 아니라 **`CategoryItem`이 `Category` 객체 전체를 받는 것**이다.

  > "To promote decoupling and reuse, each composable should hold the least amount of information possible."

  그리고 상태가 내려오는 쪽도 같은 상황이다. `viewState`(`RecipeState` 통째)가 `RecipeScreen`에 들어온다. 이쪽이 재구성 범위에 더 직접적인 영향을 준다.

## 공부할 내용

### 프롭 드릴링이 문제가 되는 기준

무조건 나쁜 것이 아니다. 판단 기준은 **깊이**와 **중간 단계의 역할**이다.

| 상황 | 판단 |
| --- | --- |
| 2~3단계, 중간이 관련 있는 컴포저블 | **괜찮다** ← 지금 코드 |
| 5단계 이상 | 구조를 다시 본다 |
| 중간 단계가 완전히 무관한데 통과만 시킨다 | 개선 대상 |
| 같은 값을 여러 갈래로 내려보낸다 | `CompositionLocal` 검토 |

지금은 3단계이고 중간의 `CategoryScreen`도 같은 화면의 일부다. **"고쳐야만 하는" 수준은 아니다.**

### 개선 ① 레이아웃 단계를 없앤다 — 가장 실용적

`CategoryScreen`은 그리드를 만드는 것 외에 하는 일이 없다. 이 한 단계를 접으면 드릴링이 3단계에서 2단계가 된다.

```kotlin
@Composable
fun RecipeScreen(
    viewState: MainViewModel.RecipeState,
    navigateToDetail: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            viewState.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            viewState.error != null -> Text("Error Occurred")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewState.list) { category ->
                    CategoryItem(
                        name = category.strCategory,
                        thumbnailUrl = category.strCategoryThumb,
                        onClick = { navigateToDetail(category) }   // ← 여기서 닫힌다
                    )
                }
            }
        }
    }
}
```

**핵심은 `onClick = { navigateToDetail(category) }`다.** `CategoryItem`에게 "`Category`를 받아서 내비게이션하는 함수"를 넘기는 대신, **"눌렸다"만 알리는 `() -> Unit`**을 넘긴다. `CategoryItem`은 이제 `Category` 타입도, 내비게이션도 모른다.

```kotlin
@Composable
fun CategoryItem(
    name: String,
    thumbnailUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(model = thumbnailUrl, contentDescription = null, ...)
        Text(text = name, ...)
    }
}
```

이제 `CategoryItem`은 **문자열 둘과 콜백 하나**만 안다. 어느 앱에 갖다 놔도 쓸 수 있고, 프리뷰도 간단하다.

```kotlin
@Preview
@Composable
fun CategoryItemPreview() {
    CategoryItem(name = "Beef", thumbnailUrl = "", onClick = {})
}
```

### 개선 ② 상태도 최소로 내린다

```kotlin
// 지금: 상태 덩어리 전체를 받는다
fun RecipeScreen(viewState: MainViewModel.RecipeState, ...)
```

공식 문서가 드는 예와 같은 모양이다.

```kotlin
// ❌ 피할 것
@Composable
fun Header(news: News) { }

// ✅ 권장
@Composable
fun Header(title: String, subtitle: String) { }
```

필요한 것만 받으면 **재구성 범위가 좁아진다.** `RecipeState`의 `error`만 바뀌어도 지금은 `RecipeScreen` 전체가 재구성 대상이 된다. → [`036-compose-recomposition-timing-and-scope.md`](036-compose-recomposition-timing-and-scope.md)

다만 화면 최상위 컴포저블이 UI 상태 객체 하나를 받는 것은 흔하고 권장되는 패턴이기도 하다. **화면 단위는 상태 객체로, 하위 부품은 원시값으로** 정도가 현실적인 기준이다.

### 개선 ③ 화면을 둘로 나눈다 — 상태 있는/없는 컴포저블

컴포즈의 표준 패턴이다.

```kotlin
// 상태 있는(stateful) 컴포저블 — ViewModel과 내비게이션을 안다
@Composable
fun RecipeRoute(
    onCategoryClick: (String) -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val viewState by viewModel.categoriesState
    RecipeScreen(
        loading = viewState.loading,
        error = viewState.error,
        categories = viewState.list,
        onCategoryClick = onCategoryClick
    )
}

// 상태 없는(stateless) 컴포저블 — 순수하게 그리기만 한다
@Composable
fun RecipeScreen(
    loading: Boolean,
    error: String?,
    categories: List<Category>,
    onCategoryClick: (String) -> Unit
) { /* ... */ }
```

`RecipeScreen`은 `ViewModel`도 `NavController`도 모르므로 **프리뷰와 UI 테스트가 전부 가능**해진다. `RecipeRoute`는 배선만 담당하는 얇은 층이다.

내비게이션 그래프도 깔끔해진다.

```kotlin
composable<RecipeListRoute> {
    RecipeRoute(onCategoryClick = { id -> navController.navigate(CategoryDetailRoute(id)) })
}
```

### 방법 ④ `CompositionLocal` — 쓸 수는 있지만 권하지 않는다

프롭 드릴링을 없애는 컴포즈의 공식 도구가 있긴 하다.

```kotlin
val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("NavController not provided")
}

// 제공
CompositionLocalProvider(LocalNavController provides navController) {
    RecipeApp()
}

// 어디서든 사용
val navController = LocalNavController.current
Modifier.clickable { navController.navigate(...) }
```

드릴링은 사라진다. 하지만 대가가 크다.

| 문제 | 내용 |
| --- | --- |
| **의존성이 숨는다** | 함수 시그니처만 봐서는 내비게이션을 쓰는지 알 수 없다 |
| **프리뷰가 깨진다** | Provider 없이 띄우면 `error(...)`로 죽는다 |
| **테스트가 어려워진다** | 매번 Provider로 감싸야 한다 |
| **재사용이 막힌다** | 그 컴포저블은 내비게이션이 있는 곳에서만 쓸 수 있다 |

`CompositionLocal`은 **테마, 폰트, `Context`처럼 거의 모든 곳에서 쓰이고 자주 안 바뀌는 값**에 어울린다. 내비게이션 콜백은 그런 성격이 아니다. → [`021-compose-localcontext.md`](021-compose-localcontext.md)

### 네 가지 방법 비교

| 방법 | 드릴링 | 테스트/프리뷰 | 권장 |
| --- | --- | --- | --- |
| 지금처럼 람다 전달 | 3단계 | 쉽다 | ✅ 지금 규모에 적절 |
| 레이아웃 단계 제거 + `() -> Unit` | 2단계 | 더 쉽다 | ✅✅ **가장 실용적** |
| Route/Screen 분리 | 2단계 | 가장 쉽다 | ✅✅ 규모가 커지면 |
| `CompositionLocal` | 0단계 | 어렵다 | ⚠️ 내비게이션엔 부적합 |
| 컴포저블이 `NavController` 직접 사용 | 0단계 | 어렵다 | ❌ 피한다 |

## 관련 아키텍처와 베스트 프랙티스

### 이벤트는 콜백으로 올린다

> "The UI layer should never change state outside of an event handler because this can introduce inconsistencies and bugs in your application."

```kotlin
@Composable
fun MyAppTopAppBar(
    topAppBarText: String,
    onBackPressed: () -> Unit    // 이벤트는 콜백으로
) { }
```

**이름은 `on...`으로 시작하는 것이 관례다.** `navigateToDetail`보다 `onCategoryClick`이 나은 이유가 여기 있다. `navigateToDetail`은 **"내비게이션을 해라"라는 지시**지만, `onCategoryClick`은 **"카테고리가 눌렸다"는 사실 통보**다. 후자여야 컴포저블이 무엇을 하는지 몰라도 된다.

```kotlin
// 컴포넌트가 결과를 알고 있다 — 결합이 생긴다
CategoryItem(category, navigateToDetail = { ... })

// 컴포넌트는 사실만 알린다 — 무엇을 할지는 호출자가 정한다
CategoryItem(name, thumbnailUrl, onClick = { ... })
```

### 파라미터 순서와 `modifier` 관례

```kotlin
// 지금
fun RecipeScreen(
    modifier: Modifier = Modifier,        // 기본값이 앞에 있다
    viewState: MainViewModel.RecipeState,
    navigateToDetail: (Category) -> Unit
)
```

컴포즈 API 가이드라인의 순서는 이렇다.

```kotlin
fun RecipeScreen(
    viewState: MainViewModel.RecipeState,     // ① 필수 파라미터
    onCategoryClick: (String) -> Unit,        // ② 필수 콜백
    modifier: Modifier = Modifier             // ③ modifier — 선택 파라미터의 첫 번째
)
```

기본값 없는 파라미터를 앞에 두어야 **이름 없이 호출**할 수 있다. 그리고 `modifier`는 선택 파라미터 중 첫 번째에 두는 것이 컴포즈 전체의 관례다.

지금 코드에는 관련된 버그성 실수도 있다.

```kotlin
viewState.loading -> CircularProgressIndicator(modifier.align(Alignment.Center))
```

**바깥에서 받은 `modifier`를 진행 표시기에 붙이고 있다.** 호출자가 준 `modifier`는 `RecipeScreen` 전체에 적용되어야 하는데, 로딩 스피너에만 적용된다. `Modifier.align(...)`으로 새로 만들고, 받은 `modifier`는 최상위 `Box`에 넘기는 것이 맞다.

```kotlin
Box(modifier = modifier.fillMaxSize()) {        // ← 받은 modifier는 여기
    when {
        viewState.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        ...
    }
}
```

### 내비게이션은 한곳에서만

```
MainActivity        NavController 를 만든다
   ↓
RecipeApp           그래프를 정의하고, 여기서만 navigate 한다   ★ 유일한 지점
   ↓ 콜백
RecipeScreen        이벤트를 위로 올린다
   ↓ 콜백
CategoryItem        눌렸다는 사실만 알린다
```

**내비게이션 호출이 한 파일에 모여 있으면 화면 흐름 전체를 한눈에 볼 수 있다.** 여기저기서 `navigate`를 부르면 "이 화면에서 어디로 갈 수 있나"를 추적하기 어려워진다. `RecipeApp`은 이 구조를 이미 지키고 있다.

### 재사용 가능한 컴포넌트의 기준

```kotlin
// 이 컴포저블은 무엇에 의존하는가?
@Composable
fun CategoryItem(name: String, thumbnailUrl: String, onClick: () -> Unit)
// → String, String, 람다. 이 앱의 어떤 것에도 의존하지 않는다. 어디든 옮길 수 있다

@Composable
fun CategoryItem(category: Category, navigateToDetail: (Category) -> Unit)
// → Category 타입에 묶인다. 이 앱 전용이다
```

**"이 컴포저블을 다른 앱에 복사해 붙이면 몇 개를 같이 가져가야 하나"**를 물어보면 결합도가 드러난다.

### Navigation 3에서는

Nav3에서도 이 구조가 그대로 통한다. 화면 컴포저블이 콜백만 받고 있다면 **바꿀 것이 없다.**

```kotlin
entry<RecipeListRoute> {
    RecipeRoute(onCategoryClick = { id -> backStack.add(CategoryDetailRoute(id)) })
}
```

`RecipeScreen`과 `CategoryItem`은 손대지 않는다. **콜백으로 분리해 둔 것의 값어치가 라이브러리를 갈아탈 때 드러난다.** → [`073-android-navigation-compose-vs-navigation3.md`](073-android-navigation-compose-vs-navigation3.md)

## 체크리스트

- [ ] 프롭 드릴링이 항상 나쁜 것은 아니라는 판단 기준을 세울 수 있다.
- [ ] 단방향 데이터 흐름에서 이벤트가 위로 올라간다는 것을 설명할 수 있다.
- [ ] 컴포저블이 `NavController`를 직접 쓰면 잃는 것 세 가지를 말할 수 있다.
- [ ] 중간 레이아웃 단계를 없애 드릴링을 줄일 수 있다.
- [ ] `(Category) -> Unit`을 `() -> Unit`으로 바꾸는 것이 왜 결합을 줄이는지 설명할 수 있다.
- [ ] 컴포저블이 필요한 최소 정보만 받아야 하는 이유를 안다.
- [ ] Route/Screen 분리 패턴을 적용할 수 있다.
- [ ] `CompositionLocal`이 내비게이션에 부적합한 이유를 설명할 수 있다.
- [ ] 콜백 이름을 `on...`으로 짓는 이유를 설명할 수 있다.
- [ ] `modifier` 파라미터의 위치와 사용 관례를 안다.
- [ ] 받은 `modifier`를 엉뚱한 자식에 붙이면 생기는 문제를 안다.
- [ ] 내비게이션 호출을 한곳에 모으는 이유를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Architecting your Compose UI](https://developer.android.com/develop/ui/compose/architecture)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: Compose API guidelines](https://developer.android.com/develop/ui/compose/api-guidelines)
- [Android Developers: Compose modifiers](https://developer.android.com/develop/ui/compose/modifiers)
- [Android Developers: Locally scoped data with CompositionLocal](https://developer.android.com/develop/ui/compose/compositionlocal)
- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation)
- [Android Developers: UI layer architecture](https://developer.android.com/topic/architecture/ui-layer)
