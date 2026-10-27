# Compose 내비게이션 예제와 테스트(Compose Navigation Example and Testing)

## 0. 들어가면서

chapter157, chapter205를 따라가며 내비게이션(navigation) 질문을 [todos/064 ~ 073](../todos/README.md#10-navigation)에 하나씩 정리했다. 질문마다 답은 모았지만 "그래서 처음부터 제대로 짜면 어떤 모양이고, 그걸 어떻게 테스트하는가"를 한 번에 보여 주는 코드가 없었다. 이 글은 그 빈칸을 채운다.

결론부터 말하면 **좋은 내비게이션 코드의 핵심은 "이동 결정을 한곳에 모으는 것"이고, 그렇게 짜면 테스트가 세 층으로 자연스럽게 나뉜다.**

- **화면(screen)은 `NavController`를 모른다.** "무엇이 눌렸는지"만 콜백으로 올린다. → 화면 테스트에 내비게이션이 필요 없다.
- **`ViewModel`은 route 인자(id)만 받고 데이터는 스스로 조회한다.** → `SavedStateHandle` 하나만 만들어 주면 테스트할 수 있다.
- **어디로, 어떤 백스택 규칙으로 가는지는 `NavHost` 한곳이 정한다.** → `TestNavHostController`로 백스택만 검증하면 된다.
- **저장소·클라이언트 같은 의존성은 화면이나 `NavHost`로 내려보내지 않는다.** `ViewModel`이 만들어지는 순간 팩토리(또는 Hilt)가 채운다. → 테스트에서는 가짜 저장소 하나만 바꿔 끼우면 된다(5장).

이 글의 코드는 모두 chapter157과 같은 빌드 설정(AGP 9.4.0, Kotlin 2.2.10, navigation-compose 2.10.1, Compose BOM 2026.02.01, compileSdk 37)으로 만든 임시 프로젝트에서 **실제로 빌드하고 테스트를 돌려 확인했다.**

| 테스트 | 실행 환경 | 결과 |
| --- | --- | --- |
| `ItemDetailViewModelTest` (2개) | JVM + Robolectric 4.17 (`./gradlew testDebugUnitTest`) | 통과 |
| `ItemListScreenTest` (1개) | 에뮬레이터 Pixel_10_Pro (`./gradlew connectedDebugAndroidTest`) | 통과 |
| `AppNavHostTest` (5개) | 에뮬레이터 Pixel_10_Pro | 통과 |

5장(의존성 주입)의 코드는 이 예제를 수동 DI 버전과 Hilt 버전으로 각각 고쳐 따로 확인했다. 결과는 5장 첫머리에 있다.

## 1. 예제 앱 구조

로그인 → 목록 → 상세로 이어지는 세 화면짜리 앱이다. 강의에서 다룬 거의 모든 내비게이션 요소(시작 목적지, 인자 전달, 뒤로 가기, 백스택 정리)가 들어간다.

```
 ┌─────────┐  로그인 성공            ┌──────────┐  항목 클릭(id)       ┌──────────────┐
 │  Login  │ ───────────────────▶ │ ItemList │ ─────────────────▶ │ ItemDetail   │
 └─────────┘  popUpTo<Login>      └──────────┘  launchSingleTop   │ (itemId=2)   │
              { inclusive = true }      ▲                         └──────────────┘
                                        │           navigateUp          │
                                        └───────────────────────────────┘

 백스택 변화
   시작           [Login]
   로그인 후       [ItemList]                 ← Login 이 지워졌으므로 뒤로 가면 앱 종료
   항목 클릭 후    [ItemList, ItemDetail(2)]
   뒤로           [ItemList]
```

파일은 역할별로 나눈다.

```
app/src/main/java/.../
├── Routes.kt            목적지 타입 (@Serializable)
├── ItemRepository.kt    데이터 (예제라 메모리 목록)
├── LoginScreen.kt       화면 — 콜백만 받는다
├── ItemListScreen.kt    화면 — 콜백만 받는다
├── ItemDetail.kt        ViewModel + 화면
├── AppNavigation.kt     기능별 확장 함수 + AppNavHost  ← 이동 결정은 여기에만
└── MainActivity.kt

app/src/test/.../          JVM 테스트 (Robolectric)
└── ItemDetailViewModelTest.kt

app/src/androidTest/.../   계측 테스트 (에뮬레이터·기기)
├── ItemListScreenTest.kt
└── AppNavHostTest.kt
```

## 2. 예제 코드

### 2.1. Gradle 설정

chapter157에 없는 것만 추가한다. 타입 안전(type-safe) route를 쓰려면 **Kotlin 직렬화(serialization) 컴파일러 플러그인**이 필요하다. 런타임 라이브러리(`kotlinx-serialization-core`)는 navigation-compose가 이미 끌어온다.

```toml
# gradle/libs.versions.toml
[versions]
navigationCompose = "2.10.1"
lifecycleViewmodelCompose = "2.11.0"
robolectric = "4.17"

[libraries]
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-navigation-testing = { group = "androidx.navigation", name = "navigation-testing", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

```kotlin
// build.gradle.kts (루트)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // ...
    testOptions {
        unitTests.isIncludeAndroidResources = true  // Robolectric 이 리소스·매니페스트를 읽는다
    }
}

dependencies {
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.robolectric)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.navigation.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

> AGP 9부터는 Kotlin 지원이 AGP에 내장되어 `org.jetbrains.kotlin.android` 플러그인을 따로 적용하지 않는다. 직렬화 플러그인은 이 내장 Kotlin과 함께 그대로 동작했다. → [`010-android-agp-kgp-and-compiler-plugins.md`](../todos/010-android-agp-kgp-and-compiler-plugins.md)

### 2.2. 목적지 — 문자열 대신 타입

```kotlin
// Routes.kt
import kotlinx.serialization.Serializable

// 목적지 = 타입. 인자는 프로퍼티로 표현한다.
@Serializable
data object Login

@Serializable
data object ItemList

@Serializable
data class ItemDetail(val itemId: Long)
```

chapter157의 `"secondScreen/{name}/{age}"`와 비교하면 차이가 분명하다.

| | 문자열 route (chapter157) | 타입 안전 route (이 글) |
| --- | --- | --- |
| 인자 선언 | `"{age}"` + `navArgument("age") { type = NavType.IntType }` | `val itemId: Long` |
| 이동 | `navigate("secondScreen/$name/$age")` — 오타·인코딩 문제가 런타임에 터진다 | `navigate(ItemDetail(2L))` — 컴파일러가 검사한다 |
| 인자 읽기 | `arguments?.getString("age")?.toInt()` | `entry.toRoute<ItemDetail>().itemId` |

인자가 없는 목적지는 `data object`, 있는 목적지는 `data class`로 쓴다. 자세한 비교는 [`066-android-navigation-string-route-vs-type-safe.md`](../todos/066-android-navigation-string-route-vs-type-safe.md)에 있다.

### 2.3. 데이터

```kotlin
// ItemRepository.kt
data class Item(val id: Long, val title: String)

class ItemRepository {
    private val items = listOf(
        Item(1L, "Kotlin"),
        Item(2L, "Compose"),
        Item(3L, "Navigation"),
    )

    fun getItems(): List<Item> = items

    fun getItem(id: Long): Item? = items.find { it.id == id }
}
```

### 2.4. 화면 — `NavController`를 모른다

```kotlin
// LoginScreen.kt
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onLoginSuccess) {
            Text("로그인")
        }
    }
}
```

```kotlin
// ItemListScreen.kt
// 화면은 NavController 를 모른다. "무엇이 클릭됐는지"만 위로 알린다.
@Composable
fun ItemListScreen(
    items: List<Item>,
    onItemClick: (itemId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(items, key = { it.id }) { item ->
            ListItem(
                headlineContent = { Text(item.title) },
                modifier = Modifier.clickable { onItemClick(item.id) },
            )
        }
    }
}
```

콜백 이름이 `onNavigateToDetail`이 아니라 **`onItemClick`** 인 점을 보자. 화면은 "무슨 일이 일어났는지"만 말하고, 그 결과 어디로 갈지는 모른다. 나중에 태블릿에서 목록·상세를 한 화면에 나란히 보여 주기로 바뀌어도 이 화면은 고칠 필요가 없다. → [`070-compose-navigation-prop-drilling.md`](../todos/070-compose-navigation-prop-drilling.md), [`082-compose-state-hoisting-and-callbacks.md`](../todos/082-compose-state-hoisting-and-callbacks.md)

### 2.5. 상세 — `ViewModel`이 id로 직접 조회한다

```kotlin
// ItemDetail.kt
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute

class ItemDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: ItemRepository,
) : ViewModel() {
    // route 인자는 id 하나뿐이다. 데이터는 ViewModel 이 저장소에서 직접 가져온다.
    private val route = savedStateHandle.toRoute<ItemDetail>()

    val item: Item? = repository.getItem(route.itemId)
}

@Composable
fun ItemDetailScreen(
    item: Item?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(item?.title ?: "항목을 찾을 수 없습니다")
        Button(onClick = onBack) {
            Text("뒤로")
        }
    }
}
```

두 가지가 핵심이다.

1. **route에는 id만 싣는다.** `Item` 객체 전체를 넘기지 않는다. 내비게이션 인자는 "무엇을 보여 줄지 식별하는 값"이다. 객체를 실으면 route가 길어지고, 프로세스가 죽었다 살아날 때 오래된 사본이 복원되며, 원본 데이터가 바뀌어도 반영되지 않는다. → [`068-android-navigation-passing-many-arguments.md`](../todos/068-android-navigation-passing-many-arguments.md)
2. **`ViewModel`이 `SavedStateHandle`로 인자를 읽는다.** 목적지의 `NavBackStackEntry`가 route 인자를 `SavedStateHandle`에 넣어 준다. 화면 컴포저블이 `entry.arguments`를 꺼내 `ViewModel`에 전달할 필요가 없다. → [`069-android-navigation-backstackentry-savedstatehandle.md`](../todos/069-android-navigation-backstackentry-savedstatehandle.md)

예제에서는 데이터가 메모리에 있어 `item`을 바로 계산했지만, 실제 앱이라면 저장소가 `suspend` 함수나 `Flow`를 돌려주고 `ViewModel`이 `StateFlow`로 노출한다. 그 모양은 5.3절에 있다. → [`047-android-viewmodel-state-exposure-patterns.md`](../todos/047-android-viewmodel-state-exposure-patterns.md)

### 2.6. 내비게이션 — 한곳에 모은다

```kotlin
// AppNavigation.kt
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// ── 기능별 내비게이션 확장 함수 ─────────────────────────────

fun NavController.navigateToItemList() {
    navigate(ItemList) {
        // 로그인 화면을 백스택에서 제거한다. 목록에서 뒤로 가면 앱이 종료된다.
        popUpTo<Login> { inclusive = true }
    }
}

fun NavController.navigateToItemDetail(itemId: Long) {
    navigate(ItemDetail(itemId)) {
        // 같은 상세 화면을 연타해도 한 번만 쌓인다.
        launchSingleTop = true
    }
}

fun NavGraphBuilder.loginScreen(onLoginSuccess: () -> Unit) {
    composable<Login> {
        LoginScreen(onLoginSuccess = onLoginSuccess)
    }
}

fun NavGraphBuilder.itemListScreen(
    repository: ItemRepository,
    onItemClick: (Long) -> Unit,
) {
    composable<ItemList> {
        ItemListScreen(items = repository.getItems(), onItemClick = onItemClick)
    }
}

fun NavGraphBuilder.itemDetailScreen(
    repository: ItemRepository,
    onBack: () -> Unit,
) {
    composable<ItemDetail> {
        // NavBackStackEntry 가 ViewModelStoreOwner 이므로, 이 화면이 백스택에서 빠질 때 함께 정리된다.
        val viewModel = viewModel { ItemDetailViewModel(createSavedStateHandle(), repository) }
        ItemDetailScreen(item = viewModel.item, onBack = onBack)
    }
}

// ── 앱 전체 그래프: 이동 결정은 여기 한곳에서만 한다 ────────────

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    repository: ItemRepository = ItemRepository(),
) {
    NavHost(
        navController = navController,
        startDestination = Login,
        modifier = modifier,
    ) {
        loginScreen(onLoginSuccess = navController::navigateToItemList)
        itemListScreen(
            repository = repository,
            onItemClick = navController::navigateToItemDetail,
        )
        itemDetailScreen(
            repository = repository,
            onBack = navController::navigateUp,
        )
    }
}
```

`NavGraphBuilder.xxxScreen()`과 `NavController.navigateToXxx()`를 짝으로 만드는 패턴은 공식 문서의 [Encapsulate your navigation code](https://developer.android.com/guide/navigation/design/encapsulate)와 [Now in Android](https://github.com/android/nowinandroid)에서 쓰는 방식이다. `NavHost` 본문이 목차처럼 읽히고, 화면이 늘어나도 `NavHost`는 한 줄씩만 늘어난다. 멀티 모듈로 나눌 때는 이 두 함수만 `public`으로 두고 화면 컴포저블은 `internal`로 숨긴다.

`AppNavHost`가 `navController`를 **기본값이 있는 파라미터로 받는 이유**는 테스트 때문이다. 앱에서는 `rememberNavController()`가 쓰이고, 테스트에서는 `TestNavHostController`를 꽂는다(4.4절).

> **`repository` 파라미터는 이 단계에서만 쓰는 임시방편이다.** 저장소를 `NavHost`에서 목적지로 넘기는 방식은 의존성이 늘면 무너지고, 컴포저블 파라미터 기본값(`= ItemRepository()`)은 리컴포지션마다 새 인스턴스를 만든다는 문제도 숨어 있다. 바른 방식은 5장에서 다룬다.

`navigate` 뒤의 람다(`popUpTo`, `launchSingleTop`)는 → [`072-android-navigation-navoptions-builder.md`](../todos/072-android-navigation-navoptions-builder.md), `navigateUp`과 `popBackStack`의 차이는 → [`071-android-navigation-popbackstack-vs-navigateup.md`](../todos/071-android-navigation-popbackstack-vs-navigateup.md)

### 2.7. 진입점

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Chapter157Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
```

## 3. 베스트 프랙티스

강의 코드(chapter157, chapter205)에 달아 둔 FIXME와 나란히 놓고 정리한다.

| # | 원칙 | 강의 코드 | 이 글의 코드 |
| --- | --- | --- | --- |
| 1 | **타입 안전 route를 쓴다** (Navigation 2.8.0+) | `"secondScreen/{name}/{age}"` | `@Serializable data class ItemDetail(val itemId: Long)` |
| 2 | **route에는 식별자만 싣는다** | 이름·나이를 route에 실어 나름 | `itemId` 하나 |
| 3 | **화면은 `NavController`를 받지 않는다** | chapter205 `HomeView(navController, viewModel)` | `ItemListScreen(items, onItemClick)` |
| 4 | **콜백 이름은 사건을 말한다** | — | `onItemClick`, `onLoginSuccess` (`onNavigateToX` 아님) |
| 5 | **이동 결정은 `NavHost` 한곳에서** | 화면 안에서 `navController.navigate(...)` 호출 | `AppNavHost`에서만 호출 |
| 6 | **뒤로 가기는 `navigateUp`/`popBackStack`** | `navigate("firstScreen")`로 "뒤로" → 백스택이 무한히 쌓임 | `navController::navigateUp` |
| 7 | **돌아오지 않을 화면은 `popUpTo`로 지운다** | — | 로그인 후 `popUpTo<Login> { inclusive = true }` |
| 8 | **`ViewModel`은 `SavedStateHandle`로 인자를 읽는다** | 화면이 `entry.arguments`를 꺼내 `ViewModel`에 전달 | `savedStateHandle.toRoute<ItemDetail>()` |
| 9 | **목적지별 `ViewModel`은 목적지 안에서 만든다** | chapter205는 `Navigation()`에서 하나를 만들어 모든 화면이 공유 | `composable<ItemDetail> { viewModel { ... } }` — 백스택 엔트리 스코프 |
| 10 | **`NavHost`는 `navController`를 주입받는다** | 함수 안에서 `rememberNavController()` 고정 | 기본값 있는 파라미터 → 테스트에서 교체 |
| 11 | **의존성은 `NavHost`로 내려보내지 않는다** | — | 5장: `ViewModel` 팩토리 또는 Hilt가 채운다 |
| 12 | **`ViewModel` 생성자는 가볍게** | chapter205 `WishViewModel`은 기본값으로 전역 `Graph`에 접근 | 5장: 받기만 하고, 로딩은 구독 시작 시(`stateIn`) |

몇 가지는 부연이 필요하다.

**3번·5번 — 왜 화면이 `NavController`를 몰라야 하는가.** 공식 문서의 표현을 빌리면 `NavController`를 컴포저블 깊숙이 넘기면 그 컴포저블은 **미리보기(preview)도, 단독 테스트도 할 수 없다.** 미리보기를 그리려면 가짜 `NavController`를 만들어야 하고, 테스트하려면 그래프 전체를 띄워야 한다. 콜백만 받으면 4.2절처럼 람다 하나로 끝난다. 또 "어디로 갈지"가 여러 파일에 흩어지면 화면 흐름을 파악하려고 모든 화면을 열어 봐야 한다.

**6번 — "뒤로"를 `navigate`로 구현하면 안 되는 이유.** chapter157의 두 번째 화면은 `navigate("firstScreen")`으로 첫 화면에 "새로" 간다. 백스택이 `[first, second, first, second, ...]`로 계속 자라서 시스템 뒤로 가기를 아무리 눌러도 앱을 빠져나올 수 없다. 뒤로 가기는 백스택에서 **빼는** 동작이지 **넣는** 동작이 아니다.

**9번 — `ViewModel`의 스코프.** `composable<T> { viewModel() }` 안에서 만든 `ViewModel`은 그 `NavBackStackEntry`에 붙는다. 상세 화면에서 뒤로 가면 엔트리가 백스택에서 빠지고 `ViewModel.onCleared()`가 불린다. 화면 여러 개가 **정말로** 상태를 공유해야 할 때만 중첩 그래프(nested graph)의 엔트리에 스코프를 걸어 공유한다(`navController.getBackStackEntry<Graph>()`). chapter205처럼 앱 전체가 `ViewModel` 하나를 쓰면, 목록의 상태와 편집 화면의 입력 상태가 한 객체에 섞인다.

## 4. 테스트

### 4.1. 무엇을 어디서 테스트하는가

2장처럼 역할을 나누면 테스트도 세 층으로 나뉜다. 아래로 갈수록 빠르고 싸다.

```
               ┌──────────────────────────────┐
               │ 4.4 그래프 테스트              │  AppNavHost + TestNavHostController
               │  "어디로 가고, 백스택은 어떤가" │  androidTest (에뮬레이터)
               ├──────────────────────────────┤
               │ 4.2 화면 테스트                │  ItemListScreen + 람다
               │  "누르면 콜백이 불리는가"       │  androidTest (에뮬레이터)
               ├──────────────────────────────┤
               │ 4.3 ViewModel 테스트           │  ItemDetailViewModel + SavedStateHandle
               │  "인자로 올바른 데이터를 찾는가" │  test (JVM + Robolectric)
               └──────────────────────────────┘
```

| 무엇을 확인하나 | 어느 테스트 | 필요한 것 |
| --- | --- | --- |
| 버튼을 누르면 올바른 값으로 콜백이 불린다 | 화면 테스트 | `createComposeRule()`, 람다 |
| route 인자로 올바른 데이터를 조회한다 | `ViewModel` 테스트 | `SavedStateHandle(route = ...)` |
| 콜백이 올바른 목적지로 연결되어 있다 | 그래프 테스트 | `TestNavHostController` |
| `popUpTo`, `launchSingleTop` 같은 백스택 규칙 | 그래프 테스트 | `TestNavHostController` |

**Navigation 라이브러리 자체가 동작하는지는 테스트하지 않는다.** "`navigate()`를 부르면 화면이 바뀐다"는 구글이 이미 검증했다. 우리가 검증할 것은 **우리가 연결한 배선**(이 콜백이 저 목적지로 가는가, 이 백스택 규칙이 원하는 결과를 내는가)이다.

### 4.2. 화면 테스트 — 내비게이션 없이

```kotlin
// app/src/androidTest/.../ItemListScreenTest.kt
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// 화면 단독 테스트: NavController 없이 콜백만 검증한다.
@RunWith(AndroidJUnit4::class)
class ItemListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun 항목을_누르면_그_항목의_id로_콜백이_호출된다() {
        var clickedId: Long? = null
        composeTestRule.setContent {
            ItemListScreen(
                items = listOf(Item(1L, "Kotlin"), Item(2L, "Compose")),
                onItemClick = { clickedId = it },
            )
        }

        composeTestRule.onNodeWithText("Compose").performClick()

        assertEquals(2L, clickedId)
    }
}
```

화면이 `NavController`를 받았다면 이 테스트는 `TestNavHostController`와 그래프 전체가 필요했을 것이다. **3장 3번 원칙이 테스트를 이만큼 줄여 준다.** 데이터도 저장소가 아니라 파라미터로 받으니 테스트가 원하는 목록을 바로 꽂는다.

### 4.3. `ViewModel` 테스트 — `SavedStateHandle`을 직접 만든다

`navigation-testing`은 route 객체로 `SavedStateHandle`을 만드는 팩토리를 제공한다.

```kotlin
// app/src/test/.../ItemDetailViewModelTest.kt
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// toRoute() 는 내부에서 android.os.Bundle 을 쓰므로 순수 JVM 에서는 동작하지 않는다.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemDetailViewModelTest {

    @Test
    fun route_인자의_id로_항목을_조회한다() {
        // navigation-testing 이 제공하는 팩토리: route 객체로 SavedStateHandle 을 만든다.
        val savedStateHandle = SavedStateHandle(route = ItemDetail(itemId = 2L))

        val viewModel = ItemDetailViewModel(savedStateHandle, ItemRepository())

        assertEquals("Compose", viewModel.item?.title)
    }

    @Test
    fun 없는_id면_항목이_null이다() {
        val savedStateHandle = SavedStateHandle(route = ItemDetail(itemId = 999L))

        val viewModel = ItemDetailViewModel(savedStateHandle, ItemRepository())

        assertNull(viewModel.item)
    }
}
```

**여기서 두 번 막혔다.** 둘 다 알아 둘 만한 함정이다.

**① Robolectric 없이는 실패한다.** 처음에는 JUnit만으로 돌렸는데 두 테스트 모두 이렇게 실패했다.

```
java.lang.RuntimeException: Method containsKey in android.os.BaseBundle not mocked.
    at androidx.savedstate.SavedStateReader.contains-impl(SavedStateReader.android.kt:789)
    at androidx.navigation.testing.SavedStateHandleFactoryKt.invoke(SavedStateHandleFactory.kt:81)
```

`SavedStateHandle(route = ...)` 대신 `SavedStateHandle(mapOf("itemId" to 2L))`로 직접 만들어도 이번엔 `toRoute()`가 실패한다.

```
java.lang.RuntimeException: Method putLong in android.os.BaseBundle not mocked.
    at androidx.core.os.BundleKt.bundleOf(Bundle.kt:52)
    at androidx.navigation.serialization.SavedStateHandleArgStore.get(RouteDecoder.kt:208)
```

`src/test`는 순수 JVM에서 돌고, 여기서 `android.*` 클래스는 **몸통 없는 껍데기(stub)** 다. 타입 안전 route의 직렬화·역직렬화는 안드로이드에서 `Bundle`을 거치므로 `toRoute()`를 부르는 순간 껍데기에 닿는다. 그래서 **Robolectric**(JVM에서 안드로이드 프레임워크를 흉내 내는 라이브러리)이 필요하다.

**② Robolectric 기본 SDK에서 실패했다.** `@Config` 없이 돌리면 이 환경(JDK 21, compileSdk·targetSdk 37)에서 다음처럼 실패했다.

```
java.lang.RuntimeException: Failed to interact with raw FileDescriptor internals; perhaps JRE has changed?
    at com.android.internal.os.ApplicationSharedMemory.create(ApplicationSharedMemory.java:106)
```

Robolectric은 앱의 `targetSdk`(또는 자신이 지원하는 최대 SDK)에 맞는 안드로이드 이미지로 돈다. 최신 SDK 이미지가 이 JDK와 맞지 않아 생긴 문제로, `@Config(sdk = [35])`로 SDK를 고정하자 통과했다. 프로젝트 전체에 적용하려면 `app/src/test/resources/robolectric.properties`에 `sdk=35`를 적는다. 이 값은 Robolectric·JDK 버전이 바뀌면 다시 확인해야 한다.

> **Robolectric을 들이고 싶지 않다면.** `ViewModel`이 `SavedStateHandle` 대신 `itemId: Long`을 생성자로 받게 하고, 목적지에서 `val id = entry.toRoute<ItemDetail>().itemId`로 꺼내 넘기는 방법도 있다. 그러면 `ViewModel` 테스트는 순수 JUnit이 된다. 대신 프로세스 재생성 시 인자 복원을 `SavedStateHandle`에 맡기는 공식 패턴에서 한 발 벗어난다. 이 글은 공식 패턴을 유지하고 Robolectric을 쓰는 쪽을 택했다. Hilt를 쓰면 `SavedStateHandle` 주입이 자동이라 대부분 공식 패턴을 그대로 쓴다.

### 4.4. 그래프 테스트 — `TestNavHostController`

실제 `AppNavHost`에 `TestNavHostController`를 꽂는다. `TestNavHostController`는 `NavHostController`를 상속하고, 테스트에서 백스택을 직접 만들 수 있게 `setCurrentDestination()` 같은 도구를 더한 클래스다.

```kotlin
// app/src/androidTest/.../AppNavHostTest.kt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// 그래프 테스트: 실제 NavHost 에 TestNavHostController 를 꽂아 백스택을 검증한다.
@RunWith(AndroidJUnit4::class)
class AppNavHostTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            AppNavHost(navController = navController)
        }
    }

    @Test
    fun 시작_목적지는_로그인이다() {
        assertTrue(navController.currentDestination?.hasRoute<Login>() == true)
    }

    @Test
    fun 로그인하면_목록으로_가고_로그인은_백스택에서_사라진다() {
        composeTestRule.onNodeWithText("로그인").performClick()

        assertTrue(navController.currentDestination?.hasRoute<ItemList>() == true)
        // popUpTo<Login> { inclusive = true } 의 결과
        assertNull(navController.previousBackStackEntry)
    }

    @Test
    fun 항목을_누르면_해당_id로_상세_화면에_간다() {
        composeTestRule.onNodeWithText("로그인").performClick()
        composeTestRule.onNodeWithText("Compose").performClick()

        val entry = navController.currentBackStackEntry!!
        assertTrue(entry.destination.hasRoute<ItemDetail>())
        assertEquals(2L, entry.toRoute<ItemDetail>().itemId)
        composeTestRule.onNodeWithText("Compose").assertIsDisplayed()
    }

    @Test
    fun 상세에서_뒤로_누르면_목록으로_돌아온다() {
        composeTestRule.onNodeWithText("로그인").performClick()
        composeTestRule.onNodeWithText("Compose").performClick()
        composeTestRule.onNodeWithText("뒤로").performClick()

        assertTrue(navController.currentDestination?.hasRoute<ItemList>() == true)
    }

    @Test
    fun 특정_화면에서_시작하도록_상태를_직접_만들_수_있다() {
        // UI 조작 없이 컨트롤러로 바로 이동시켜 그 화면만 검증한다.
        composeTestRule.runOnUiThread {
            navController.navigate(ItemDetail(itemId = 3L))
        }

        composeTestRule.onNodeWithText("Navigation").assertIsDisplayed()
    }
}
```

요점을 짚는다.

- **`ComposeNavigator`를 직접 등록한다.** `rememberNavController()`는 내부에서 `ComposeNavigator`와 `DialogNavigator`를 등록해 준다. `TestNavHostController`는 빈 상태로 시작하므로 `composable<T>`를 쓰려면 `ComposeNavigator`를, `dialog<T>`를 쓴다면 `DialogNavigator`도 추가해야 한다.
- **`setContent` 안에서 만든다.** `LocalContext.current`를 쓰려면 컴포지션 안이어야 한다. 공식 문서도 이 모양을 권한다.
- **목적지 비교는 `hasRoute<T>()`로 한다.** 타입 안전 route에서 `currentDestination?.route`는 `"com.example.chapter_157.ItemDetail/{itemId}"`라는 내부 문자열이다(에뮬레이터에서 확인한 실제 값). 이 문자열에 기대지 말고 `hasRoute<ItemDetail>()`로 묻는다. 인자는 `toRoute<ItemDetail>()`로 꺼낸다.
- **백스택 규칙은 `previousBackStackEntry`로 확인한다.** `popUpTo<Login> { inclusive = true }`가 제대로 걸렸다면 목록 화면 아래에 아무것도 없어야 한다. 이런 규칙은 앱을 손으로 눌러 봐서는 놓치기 쉽다(뒤로 가기를 눌러 앱이 꺼지는지까지 매번 확인하지 않는다).
- **`navigate()`는 UI 스레드에서 부른다.** 테스트 본문은 계측 스레드에서 돈다. 컨트롤러를 직접 조작할 때는 `composeTestRule.runOnUiThread { ... }`로 감싼다.

### 4.5. 실행

```bash
# JVM 테스트 (Robolectric) — 에뮬레이터 불필요
./gradlew :app:testDebugUnitTest

# 계측 테스트 — 에뮬레이터나 기기가 연결되어 있어야 한다
./gradlew :app:connectedDebugAndroidTest
```

> **그래프 테스트도 에뮬레이터 없이 돌릴 수 있다.** `src/test`에 같은 테스트를 두고 `@RunWith(RobolectricTestRunner::class)`로 돌리면 Compose UI 테스트와 `TestNavHostController`가 JVM에서 동작한다. CI에서 에뮬레이터를 띄우기 부담스러울 때 쓰는 방법이다. 이 글에서는 계측 테스트로만 확인했다.

## 5. 의존성이 많아지면 — 주입과 `ViewModel` 생성

2장 예제는 저장소가 하나뿐이라 `AppNavHost`에서 `repository`를 파라미터로 받아 목적지마다 넘겼다. 실제 앱에서는 저장소가 네트워크 클라이언트(Retrofit), 데이터베이스(Room), 디스패처 같은 외부 의존성을 여럿 물고 있고, 저장소 자체도 여러 개가 된다. 이 장에서는 그런 의존성을 어떻게 조립하고, `ViewModel`이 만들어지는 순간 무슨 일이 일어나야 하는지 정리한다.

이 장의 코드는 2장 예제를 두 번 고쳐 만든 임시 프로젝트 두 개에서 확인했다.

| 버전 | 단위 테스트 (JVM + Robolectric) | 계측 테스트 (에뮬레이터) | 앱 직접 실행 |
| --- | --- | --- | --- |
| 수동 DI (5.3 ~ 5.5절) | 3개 통과 | 7개 통과 | 로그인 → 목록 로딩 → 상세 → 뒤로 두 번 → 런처 복귀 확인 |
| Hilt (5.6절) — Hilt 2.60.1, KSP 2.3.12 | 3개 통과 | 7개 통과 | 같은 시나리오 확인 |

### 5.1. 질문 전제 점검 — "의존성을 화면까지 어떻게 내려보내나"

**내려보내지 않는다.** 2장 방식을 그대로 키우면 이렇게 된다.

```kotlin
// ❌ 의존성이 늘어날 때마다 내비게이션 그래프를 타고 내려간다
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    itemRepository: ItemRepository = ItemRepository(),
    userRepository: UserRepository = ...,
    cartRepository: CartRepository = ...,
) {
    NavHost(navController, startDestination = Login) {
        itemListScreen(itemRepository, cartRepository, onItemClick = ...)
        itemDetailScreen(itemRepository, userRepository, onBack = ...)
    }
}
```

세 가지 문제가 있다.

1. **`NavHost`가 앱의 모든 의존성을 알게 된다.** 내비게이션 그래프의 일은 "어디로 가는가"인데 "무엇으로 만드는가"까지 떠안는다. 3장 5번 원칙("이동 결정은 한곳에서")과 짝을 이루는 원칙이 **"조립은 한곳에서"** 다. 둘은 같은 곳이 아니다.
2. **수명이 불분명하다.** 사실 2장 코드에도 이 문제가 숨어 있다. `repository: ItemRepository = ItemRepository()`처럼 **컴포저블 파라미터의 기본값으로 객체를 만들면, `AppNavHost`가 리컴포지션될 때마다 새 인스턴스가 만들어진다.** 2장 예제는 저장소가 상태 없는 메모리 목록이라 티가 안 났을 뿐이다. 캐시를 들고 있거나 DB 연결을 여는 저장소였다면 화면마다 다른 인스턴스를 보게 된다. 앱에 하나만 있어야 하는 객체는 컴포지션 바깥(`Application`)에서 만들어야 한다.
3. **화면이 늘 때마다 목적지 함수의 파라미터가 불어난다.** 기능 모듈로 나누면 모듈 경계를 넘어 의존성이 새어 나간다.

바른 구조는 이렇다. **화면과 내비게이션 그래프는 의존성을 모른다. `ViewModel`이 만들어지는 순간, 팩토리(또는 Hilt)가 앱 스코프 객체와 route 인자를 채워 넣는다.**

### 5.2. 수명으로 나눈다

의존성을 어디에 둘지는 **"얼마나 오래 살아야 하는가"** 로 정한다.

| 칸 | 무엇이 들어가나 | 몇 개 | 누가 들고 있나 | 언제 사라지나 |
| --- | --- | --- | --- | --- |
| 앱 스코프 | HTTP 클라이언트, Retrofit 서비스, Room DB, 저장소 | 앱에 하나 | `Application`의 컨테이너 (Hilt: `SingletonComponent`) | 프로세스 종료 |
| 화면 스코프 | `ViewModel` | 백스택 엔트리마다 하나 | `NavBackStackEntry`의 `ViewModelStore` | 엔트리가 백스택에서 빠질 때 |
| 인자 | route 인자 (`itemId`) | 엔트리마다 | 엔트리의 `SavedStateHandle` | 엔트리와 함께. 프로세스 재생성 시 복원됨 |

```
 Application (NavSampleApp)
 └── AppContainer ─────────────────────────────── 앱 스코프: 하나만 만든다
     ├── ItemApi            (클라이언트 — Retrofit 이 만든 구현)
     └── ItemRepository  ◀── DefaultItemRepository(api, Dispatchers.IO)
                ▲
                │ 팩토리가 꺼내서 넣는다
 NavBackStackEntry(ItemDetail(itemId = 2)) ──────── 화면 스코프
 ├── ViewModelStore
 │   └── ItemDetailViewModel(savedStateHandle, repository)
 └── SavedStateHandle { itemId = 2 } ─────────────── 인자
```

비싸고 상태를 공유해야 하는 객체(HTTP 커넥션 풀, DB)는 앱에 하나만 있어야 한다. 반대로 `ViewModel`은 화면마다 따로 있어야 한다. 상세 화면 두 개가 백스택에 쌓이면(`ItemDetail(1)` → `ItemDetail(2)`) `ViewModel`도 둘이다.

### 5.3. 수동 DI — `AppContainer` + `ViewModel` 팩토리

공식 문서 [Manual dependency injection](https://developer.android.com/training/dependency-injection/manual)의 `AppContainer` 패턴이다. chapter205의 `Graph` object와 비슷해 보이지만 결정적으로 다르다. → [`050-android-manual-di-graph-object.md`](../todos/050-android-manual-di-graph-object.md)

**① 클라이언트와 저장소 — 인터페이스로 경계를 만든다**

```kotlin
// ItemApi.kt
import kotlinx.coroutines.delay

data class Item(val id: Long, val title: String)

// 외부 시스템과 통신하는 클라이언트. 실제 앱이라면 Retrofit 이 만든 구현체다.
//   retrofit.create(ItemApi::class.java)
interface ItemApi {
    suspend fun fetchItems(): List<Item>
    suspend fun fetchItem(id: Long): Item?
}

// 예제용 구현: 네트워크 지연만 흉내 낸다.
class InMemoryItemApi : ItemApi {
    private val items = listOf(
        Item(1L, "Kotlin"),
        Item(2L, "Compose"),
        Item(3L, "Navigation"),
    )

    override suspend fun fetchItems(): List<Item> {
        delay(300)
        return items
    }

    override suspend fun fetchItem(id: Long): Item? {
        delay(300)
        return items.find { it.id == id }
    }
}
```

```kotlin
// ItemRepository.kt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ViewModel 은 이 인터페이스만 안다. 테스트에서는 가짜 구현을 넣는다.
interface ItemRepository {
    suspend fun getItems(): List<Item>
    suspend fun getItem(id: Long): Item?
}

class DefaultItemRepository(
    private val api: ItemApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ItemRepository {
    override suspend fun getItems(): List<Item> = withContext(ioDispatcher) { api.fetchItems() }

    override suspend fun getItem(id: Long): Item? = withContext(ioDispatcher) { api.fetchItem(id) }
}
```

`ItemRepository`를 인터페이스로 둔 이유는 테스트다. `ViewModel`은 인터페이스만 알고, 테스트에서는 네트워크 없이 즉시 답하는 가짜 구현(5.5절)을 넣는다. 디스패처도 생성자로 받는다. 기본값은 `Dispatchers.IO`지만 테스트에서는 테스트 디스패처로 바꿀 수 있다.

**② 컨테이너 — 앱 스코프 객체를 한곳에서 조립한다**

```kotlin
// AppContainer.kt
// 앱 전체에서 하나만 있어야 하는 객체(클라이언트, DB, 저장소)를 조립하는 곳.
interface AppContainer {
    val itemRepository: ItemRepository
}

class DefaultAppContainer : AppContainer {
    // Room 처럼 Context 가 필요한 객체가 생기면 생성자로 applicationContext 를 받는다.
    private val itemApi: ItemApi by lazy { InMemoryItemApi() }

    override val itemRepository: ItemRepository by lazy { DefaultItemRepository(itemApi) }
}
```

```kotlin
// NavSampleApp.kt
import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras

class NavSampleApp : Application() {
    // 테스트에서 가짜 컨테이너로 바꿔 끼울 수 있도록 var 로 둔다.
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer()
    }
}

// ViewModel 팩토리 안에서 컨테이너를 꺼내는 도우미.
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as NavSampleApp).container
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:name=".NavSampleApp"
    ... >
```

`by lazy`로 만들면 처음 쓰일 때 한 번만 생성된다. → [`004-kotlin-by-lazy-delegate.md`](../todos/004-kotlin-by-lazy-delegate.md) 의존성이 여러 개면 컨테이너 안에서 서로를 참조하며 조립한다.

```kotlin
// 의존성이 많아진 컨테이너의 모양 (예시)
class DefaultAppContainer(context: Context) : AppContainer {
    private val okHttpClient by lazy { OkHttpClient.Builder().build() }
    private val retrofit by lazy { Retrofit.Builder().client(okHttpClient).baseUrl(BASE_URL)....build() }
    private val database by lazy { Room.databaseBuilder(context, AppDatabase::class.java, "app.db").build() }

    override val itemRepository: ItemRepository by lazy {
        DefaultItemRepository(retrofit.create(ItemApi::class.java))
    }
    override val cartRepository: CartRepository by lazy {
        DefaultCartRepository(database.cartDao())
    }
}
```

(위 블록은 모양을 보여 주는 스케치다. 빌드해 본 코드는 바로 위의 `DefaultAppContainer`다.)

**③ `ViewModel` — 생성자는 정직하게, 조립은 팩토리에서**

```kotlin
// ItemDetail.kt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed interface ItemDetailUiState {
    data object Loading : ItemDetailUiState
    data class Success(val item: Item) : ItemDetailUiState
    data object NotFound : ItemDetailUiState
}

class ItemDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: ItemRepository,
) : ViewModel() {
    // 생성자에서는 인자를 읽고 "어떻게 불러올지"만 정의한다. 실제 호출은 화면이 구독할 때 시작된다.
    private val itemId = savedStateHandle.toRoute<ItemDetail>().itemId

    val uiState: StateFlow<ItemDetailUiState> =
        flow {
            val item = repository.getItem(itemId)
            emit(if (item != null) ItemDetailUiState.Success(item) else ItemDetailUiState.NotFound)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState.Loading)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ItemDetailViewModel(
                    savedStateHandle = createSavedStateHandle(),  // route 인자가 들어 있다
                    repository = appContainer().itemRepository,   // 앱 스코프 객체
                )
            }
        }
    }
}

@Composable
fun ItemDetailRoute(
    onBack: () -> Unit,
    viewModel: ItemDetailViewModel = viewModel(factory = ItemDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ItemDetailScreen(uiState = uiState, onBack = onBack)
}

@Composable
fun ItemDetailScreen(
    uiState: ItemDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            ItemDetailUiState.Loading -> CircularProgressIndicator()
            is ItemDetailUiState.Success -> Text(uiState.item.title)
            ItemDetailUiState.NotFound -> Text("항목을 찾을 수 없습니다")
        }
        Button(onClick = onBack) {
            Text("뒤로")
        }
    }
}
```

chapter205의 `WishViewModel(repo: WishRepository = Graph.wishRepository)`과 비교하면 차이가 보인다.

| | chapter205 `Graph` | `AppContainer` + 팩토리 |
| --- | --- | --- |
| `ViewModel` 생성자 | 기본값으로 전역 `Graph`에 손을 뻗는다 (서비스 로케이터) | 필요한 것을 받기만 한다 |
| 컨테이너를 아는 곳 | `ViewModel` 자신 | `Factory` 한곳 (`appContainer()`) |
| 테스트 | 전역을 바꿔야 한다 | 생성자에 가짜를 넣으면 끝 |
| 초기화 순서 | `lateinit var database`를 `provide()` 전에 쓰면 크래시 | `Application.onCreate()`가 액티비티보다 먼저 실행되므로 보장된다 |

**④ 화면 — Route와 Screen으로 나눈다**

`ViewModel`을 얻는 컴포저블(Route)과 그리는 컴포저블(Screen)을 나눈다. [Now in Android](https://github.com/android/nowinandroid)가 쓰는 이름이다. Screen은 여전히 상태와 콜백만 받으므로 4.2절의 화면 테스트가 그대로 성립한다.

```kotlin
// ItemListScreen.kt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed interface ItemListUiState {
    data object Loading : ItemListUiState
    data class Success(val items: List<Item>) : ItemListUiState
}

class ItemListViewModel(repository: ItemRepository) : ViewModel() {

    val uiState: StateFlow<ItemListUiState> =
        flow { emit(ItemListUiState.Success(repository.getItems())) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemListUiState.Loading)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ItemListViewModel(appContainer().itemRepository) }
        }
    }
}

// Route: ViewModel 을 얻고 상태를 구독한다. 화면(Screen)과 ViewModel 을 잇는 얇은 층.
@Composable
fun ItemListRoute(
    onItemClick: (itemId: Long) -> Unit,
    viewModel: ItemListViewModel = viewModel(factory = ItemListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ItemListScreen(uiState = uiState, onItemClick = onItemClick)
}

// Screen: 상태와 콜백만 받는다. ViewModel 도 NavController 도 모른다.
@Composable
fun ItemListScreen(
    uiState: ItemListUiState,
    onItemClick: (itemId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        ItemListUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ItemListUiState.Success -> LazyColumn(modifier = modifier) {
            items(uiState.items, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    modifier = Modifier.clickable { onItemClick(item.id) },
                )
            }
        }
    }
}
```

**⑤ 내비게이션 — 의존성 파라미터가 사라진다**

```kotlin
// AppNavigation.kt (바뀐 부분)
// 목적지는 이동 콜백만 받는다. 의존성은 ViewModel 팩토리가 컨테이너에서 가져온다.
fun NavGraphBuilder.itemListScreen(onItemClick: (Long) -> Unit) {
    composable<ItemList> {
        ItemListRoute(onItemClick = onItemClick)
    }
}

fun NavGraphBuilder.itemDetailScreen(onBack: () -> Unit) {
    composable<ItemDetail> {
        ItemDetailRoute(onBack = onBack)
    }
}

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Login,
        modifier = modifier,
    ) {
        loginScreen(onLoginSuccess = navController::navigateToItemList)
        itemListScreen(onItemClick = navController::navigateToItemDetail)
        itemDetailScreen(onBack = navController::navigateUp)
    }
}
```

`AppNavHost`는 다시 "어디로 가는가"만 말한다. 저장소가 열 개로 늘어도 이 파일은 바뀌지 않는다.

### 5.4. `ViewModel`이 만들어질 때 무슨 일이 일어나는가

`navigate(ItemDetail(2L))`부터 뒤로 가기까지 순서대로 따라가 본다.

```
① navigate(ItemDetail(2L))
   └─ NavController 가 NavBackStackEntry 를 만든다
      ├─ 자기 Lifecycle
      ├─ 자기 ViewModelStore (비어 있음)
      └─ 인자 { itemId = 2 }

② composable<ItemDetail> { ItemDetailRoute(...) } 가 컴포즈된다
   └─ viewModel(factory = ItemDetailViewModel.Factory)
      └─ LocalViewModelStoreOwner = 이 엔트리  → 엔트리의 ViewModelStore 를 본다

③ 스토어에 없으므로 Factory.create(modelClass, extras) 호출
   extras(CreationExtras) 안에는
      APPLICATION_KEY                 → appContainer() 가 컨테이너를 꺼낸다
      SAVED_STATE_REGISTRY_OWNER_KEY  ┐
      VIEW_MODEL_STORE_OWNER_KEY      ├→ createSavedStateHandle() 이 인자로 SavedStateHandle 을 만든다
      DEFAULT_ARGS_KEY (route 인자)   ┘
   → 만든 인스턴스를 스토어에 넣는다

④ 생성자 실행: itemId 를 읽고 uiState 를 "정의"만 한다        ← 네트워크 호출 없음
   uiState.value == Loading

⑤ 화면이 collectAsStateWithLifecycle() 로 구독
   └─ WhileSubscribed 가 flow { } 를 시작 → repository.getItem(2) → Success(Compose)

⑥ 리컴포지션·화면 회전
   └─ 스토어에 이미 있으므로 팩토리를 부르지 않고 같은 인스턴스를 돌려준다
      회전은 5초 안에 다시 구독하므로 flow 가 재시작되지 않는다(재요청 없음)

⑦ 뒤로 가기 (navigateUp)
   └─ 엔트리가 백스택에서 빠지고 DESTROYED
      └─ ViewModelStore.clear() → onCleared() → viewModelScope 취소
         (진행 중이던 요청도 함께 취소된다)
```

④와 ⑦은 테스트로 확인했다. ④는 5.5절의 `만들어진_직후에는_Loading이고_저장소를_호출하지_않는다`, ⑦은 `상세에서_뒤로_가면_상세_엔트리가_파괴된다`다. `onCleared()`가 실제로 한 번 불리는지는 임시 프로브(`onCleared()`에서 카운터 증가)를 넣어 에뮬레이터에서 따로 확인했다.

그래서 **"`ViewModel`이 만들어지면 어떻게 되어야 하는가"** 에 대한 답은 이렇다.

- **생성자는 가볍게.** 의존성을 받아 두고, route 인자를 읽고, 상태 흐름을 **정의**만 한다. 무거운 작업을 즉시 시작하지 않는다.
- **로딩은 구독이 시작할 때.** `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)`이면 화면이 실제로 볼 때 시작하고, 화면이 안 보인 지 5초가 지나면 멈춘다. `init { viewModelScope.launch { load() } }`도 흔히 쓰고 공식 코드랩에도 나오지만, 만들자마자 호출이 나가므로 테스트에서 생성만 해도 저장소가 불린다. 둘 다 틀린 방식은 아니다. 이 글은 [Now in Android](https://github.com/android/nowinandroid)처럼 `stateIn`을 쓴다. → [`048-android-viewmodel-encapsulation-and-async-timing.md`](../todos/048-android-viewmodel-encapsulation-and-async-timing.md)
- **받지 말아야 할 것.** `Activity`·`View`의 `Context`, `NavController`, 컴포저블의 상태. 모두 `ViewModel`보다 먼저 죽을 수 있어 메모리 누수가 난다. `Context`가 필요한 일(DB, 파일)은 저장소 쪽에서 `applicationContext`로 한다.
- **route 인자는 `SavedStateHandle`에서.** 컴포저블에서 `entry.toRoute<ItemDetail>().itemId`를 꺼내 팩토리에 직접 넘길 수도 있다. 하지만 `SavedStateHandle`에서 읽으면 프로세스가 죽었다 살아날 때도 인자가 복원되고, Hilt로 옮길 때 코드가 그대로 간다.

### 5.5. 테스트는 어떻게 바뀌나

**가짜 저장소.** 인터페이스 덕분에 네트워크 없이 즉시 답한다. 호출 횟수도 센다.

```kotlin
// app/src/test/.../FakeItemRepository.kt (androidTest 에도 같은 파일)
class FakeItemRepository(
    private val items: List<Item> = listOf(Item(1L, "Kotlin"), Item(2L, "Compose")),
) : ItemRepository {
    var getItemCallCount = 0
        private set

    override suspend fun getItems(): List<Item> = items

    override suspend fun getItem(id: Long): Item? {
        getItemCallCount++
        return items.find { it.id == id }
    }
}
```

**Main 디스패처 규칙.** `viewModelScope`는 `Dispatchers.Main`에서 돈다. 테스트에서는 `kotlinx-coroutines-test`의 디스패처로 바꿔 끼운다. 의존성은 `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")`이다.

```kotlin
// app/src/test/.../MainDispatcherRule.kt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// viewModelScope 는 Dispatchers.Main 에서 돈다. 테스트에서는 테스트 디스패처로 바꿔 끼운다.
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

**`ViewModel` 테스트.** 생성 직후 상태(5.4절 ④)와 구독 후 상태(⑤)를 따로 검증한다.

```kotlin
// app/src/test/.../ItemDetailViewModelTest.kt
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// toRoute() 는 내부에서 android.os.Bundle 을 쓰므로 순수 JVM 에서는 동작하지 않는다.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeItemRepository()

    private fun createViewModel(itemId: Long) = ItemDetailViewModel(
        savedStateHandle = SavedStateHandle(route = ItemDetail(itemId)),
        repository = repository,
    )

    @Test
    fun 만들어진_직후에는_Loading이고_저장소를_호출하지_않는다() {
        val viewModel = createViewModel(itemId = 2L)

        assertEquals(ItemDetailUiState.Loading, viewModel.uiState.value)
        assertEquals(0, repository.getItemCallCount)
    }

    @Test
    fun 구독하면_route_인자의_id로_항목을_불러온다() = runTest {
        val viewModel = createViewModel(itemId = 2L)

        // 화면이 collectAsStateWithLifecycle() 로 구독하는 것을 흉내 낸다.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        assertEquals(ItemDetailUiState.Success(Item(2L, "Compose")), viewModel.uiState.value)
        assertEquals(1, repository.getItemCallCount)
    }

    @Test
    fun 없는_id면_NotFound다() = runTest {
        val viewModel = createViewModel(itemId = 999L)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        assertEquals(ItemDetailUiState.NotFound, viewModel.uiState.value)
    }
}
```

`backgroundScope.launch { uiState.collect() }`는 화면이 구독하는 것을 흉내 낸다. 이게 없으면 `WhileSubscribed`가 시작되지 않아 값이 계속 `Loading`이다. `backgroundScope`는 테스트가 끝날 때 자동으로 취소된다. → [Testing Kotlin flows on Android](https://developer.android.com/kotlin/flow/test)

**화면 테스트.** Screen이 `uiState`를 받도록 바뀐 것만 다르다. `ViewModel`도 컨테이너도 필요 없다.

```kotlin
composeTestRule.setContent {
    ItemListScreen(
        uiState = ItemListUiState.Success(listOf(Item(1L, "Kotlin"), Item(2L, "Compose"))),
        onItemClick = { clickedId = it },
    )
}
```

**그래프 테스트.** 이번에는 실제 `ViewModel`이 팩토리로 만들어지므로, 앱 컨테이너를 가짜로 바꿔 끼운 뒤 `setContent`를 부른다. 4.4절의 테스트는 그대로 두고, `@Before`와 `ViewModel` 정리 테스트만 더한다.

```kotlin
// app/src/androidTest/.../AppNavHostTest.kt (바뀐 부분)
@Before
fun setUp() {
    // 실제 네트워크 대신 가짜 저장소를 쓰도록 앱 컨테이너를 바꿔 끼운다.
    val app = ApplicationProvider.getApplicationContext<NavSampleApp>()
    app.container = object : AppContainer {
        override val itemRepository: ItemRepository = FakeItemRepository(
            listOf(Item(1L, "Kotlin"), Item(2L, "Compose"), Item(3L, "Navigation")),
        )
    }

    composeTestRule.setContent {
        navController = TestNavHostController(LocalContext.current).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }
        AppNavHost(navController = navController)
    }
}

@Test
fun 상세에서_뒤로_가면_상세_엔트리가_파괴된다() {
    composeTestRule.onNodeWithText("로그인").performClick()
    composeTestRule.onNodeWithText("Compose").performClick()
    val detailEntry = navController.currentBackStackEntry!!

    composeTestRule.onNodeWithText("뒤로").performClick()
    composeTestRule.waitForIdle()

    // 엔트리가 DESTROYED 가 되면 그 엔트리의 ViewModelStore 가 비워지고 ViewModel.onCleared() 가 불린다.
    assertEquals(Lifecycle.State.DESTROYED, detailEntry.lifecycle.currentState)
}
```

가짜 저장소는 IO 디스패처로 넘어가지 않고 바로 답한다. 그래서 Compose 테스트가 로딩이 끝나기를 따로 기다리지 않아도 `onNodeWithText("Compose")`가 바로 찾힌다. 실제 저장소(`delay(300)` + `Dispatchers.IO`)를 쓰면 Compose 테스트는 IO 작업을 기다려 주지 않으므로 `waitUntil { ... }`이 필요해진다. **가짜를 주입할 수 있어야 하는 이유가 여기서도 드러난다.**

> **수동 DI 테스트의 약점.** `container`는 프로세스 전역에 있는 `var`다. 같은 프로세스에서 도는 다음 테스트도 바꿔 끼운 가짜를 그대로 본다. 테스트마다 `@Before`에서 새로 넣거나 `@After`에서 되돌려야 한다. 테스트가 많아지면 이 관리가 부담이 되는데, Hilt는 테스트마다 컴포넌트를 새로 만들어 이 문제를 없앤다.

### 5.6. 더 많아지면 — Hilt

의존성이 수십 개가 되고, 스코프가 앱·액티비티·화면으로 나뉘고, 기능 모듈로 쪼개기 시작하면 컨테이너와 팩토리를 손으로 쓰는 일이 커진다. 공식 권장은 [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)다. 5.3절 코드를 Hilt로 옮기면 **팩토리와 컨테이너가 사라지고, `ViewModel`과 화면·내비게이션 코드는 거의 그대로다.**

**① Gradle**

```toml
# gradle/libs.versions.toml
[versions]
hilt = "2.60.1"
hiltAndroidx = "1.4.0"
ksp = "2.3.12"

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }
androidx-hilt-lifecycle-viewmodel-compose = { group = "androidx.hilt", name = "hilt-lifecycle-viewmodel-compose", version.ref = "hiltAndroidx" }

[plugins]
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

```kotlin
// app/build.gradle.kts (추가된 부분)
plugins {
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    defaultConfig {
        testInstrumentationRunner = "com.example.chapter_157.HiltTestRunner"
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
```

루트 `build.gradle.kts`에도 두 플러그인을 `apply false`로 추가한다. AGP 9의 내장 Kotlin과 KSP 2.3.12, Hilt 2.60.1 조합은 이 환경에서 별다른 설정 없이 빌드됐다.

**② 앱·액티비티·모듈**

```kotlin
// NavSampleApp.kt
@HiltAndroidApp
class NavSampleApp : Application()
```

```kotlin
// MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() { /* 2.7절과 같다 */ }
```

```kotlin
// DataModule.kt — AppContainer 가 하던 조립을 Hilt 모듈이 대신한다.
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// AppContainer 가 하던 조립을 Hilt 모듈이 대신한다.
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideItemApi(): ItemApi = InMemoryItemApi()  // 실제 앱: retrofit.create(ItemApi::class.java)

    @Provides
    @Singleton
    fun provideItemRepository(api: ItemApi): ItemRepository = DefaultItemRepository(api)
}
```

**③ `ViewModel`과 Route**

```kotlin
// ItemDetail.kt (바뀐 부분)
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,  // Hilt 가 이 엔트리의 SavedStateHandle(route 인자 포함)을 넣어 준다
    repository: ItemRepository,
) : ViewModel() {
    // 본문은 5.3절과 같다. companion object Factory 만 사라진다.
}

@Composable
fun ItemDetailRoute(
    onBack: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) { /* 5.3절과 같다 */ }
```

`hiltViewModel()`의 import 경로에 주의한다. androidx.hilt 1.3.0부터 `hilt-lifecycle-viewmodel-compose` 아티팩트의 `androidx.hilt.lifecycle.viewmodel.compose` 패키지에 있다. 예전 자료에 나오는 `androidx.hilt.navigation.compose.hiltViewModel`(`hilt-navigation-compose`)과 다르다.

5.3절과 나란히 놓으면 이렇게 대응한다.

| 수동 DI | Hilt |
| --- | --- |
| `AppContainer` + `DefaultAppContainer` | `@Module @InstallIn(SingletonComponent::class)` |
| `by lazy` (하나만 만든다) | `@Singleton` |
| `NavSampleApp.container` | `@HiltAndroidApp` |
| `companion object Factory` + `appContainer()` + `createSavedStateHandle()` | `@HiltViewModel` + `@Inject constructor` |
| `viewModel(factory = ...Factory)` | `hiltViewModel()` |
| 테스트: `app.container = 가짜` | 테스트: `@TestInstallIn`으로 모듈 교체 |

**④ 테스트**

`ViewModel` 단위 테스트는 **한 줄도 바뀌지 않는다.** `@Inject constructor`도 결국 평범한 생성자라 5.5절처럼 가짜를 넣어 직접 만든다. 화면 테스트(`ItemListScreenTest`)도 그대로다. Screen은 Hilt를 모른다.

그래프 테스트에는 준비물이 세 가지 필요하다.

```kotlin
// app/src/androidTest/.../HiltTestRunner.kt
import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

// 계측 테스트에서는 NavSampleApp 대신 HiltTestApplication 을 쓴다.
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
```

```kotlin
// app/src/androidTest/.../FakeDataModule.kt
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

// androidTest 전체에서 DataModule 을 이 모듈로 바꿔 끼운다.
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object FakeDataModule {

    @Provides
    @Singleton
    fun provideItemRepository(): ItemRepository = FakeItemRepository(
        listOf(Item(1L, "Kotlin"), Item(2L, "Compose"), Item(3L, "Navigation")),
    )
}
```

```kotlin
// app/src/debug/.../HiltTestActivity.kt
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

// Compose 테스트의 빈 호스트 액티비티. hiltViewModel() 은 @AndroidEntryPoint 액티비티 안에서만 동작한다.
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
```

```xml
<!-- app/src/debug/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name=".HiltTestActivity"
            android:exported="false" />
    </application>
</manifest>
```

`createComposeRule()`은 Hilt를 모르는 빈 `ComponentActivity`를 띄운다. 그 안에서 `hiltViewModel()`을 부르면 주입할 곳이 없다. 그래서 `@AndroidEntryPoint`가 붙은 빈 액티비티를 debug 소스 세트에 두고 `createAndroidComposeRule<HiltTestActivity>()`로 띄운다. debug에만 두므로 릴리스 APK에는 들어가지 않는다.

```kotlin
// app/src/androidTest/.../AppNavHostTest.kt (바뀐 부분)
@HiltAndroidTest
class AppNavHostTest {

    // Hilt 규칙이 먼저 실행되어야 한다.
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        // 가짜 저장소는 FakeDataModule 이 넣는다. 테스트 코드는 주입에 관여하지 않는다.
        hiltRule.inject()
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            AppNavHost(navController = navController)
        }
    }

    // 테스트 메서드는 수동 DI 버전과 같다.
}
```

`@TestInstallIn`은 `androidTest` 전체에서 `DataModule`을 `FakeDataModule`로 바꾼다. 특정 테스트에서만 다른 가짜가 필요하면 `@UninstallModules(DataModule::class)` + `@BindValue`를 쓴다. → [Hilt testing guide](https://developer.android.com/training/dependency-injection/hilt-testing)

### 5.7. 어느 쪽을 고르나

| 상황 | 권장 |
| --- | --- |
| 강의 예제, 화면 몇 개, 저장소 한두 개 | 수동 DI (`AppContainer` + 팩토리) — 구조를 눈으로 보며 배우기 좋다 |
| 저장소·클라이언트가 계속 늘어나는 앱, 기능 모듈 분리 | Hilt |
| 어느 쪽이든 | **화면·내비게이션은 의존성을 모르고, `ViewModel`은 생성자로 받는다** |

마지막 줄이 핵심이다. 이 원칙만 지키면 수동 DI에서 Hilt로 옮길 때 바뀌는 것은 조립 코드(컨테이너 → 모듈, 팩토리 → `@Inject`)뿐이다. 이 장에서도 Screen, 내비게이션, `ViewModel` 본문, `ViewModel` 테스트는 거의 그대로 옮겨 갔다.

## 6. 자주 틀리는 전제

- **"화면 이동을 테스트하려면 앱 전체를 띄워야 한다"** → 아니다. 화면이 콜백만 받으면 화면 테스트에는 내비게이션이 아예 없다(4.2절). 그래프 테스트도 `Activity` 없이 `createComposeRule()`로 `AppNavHost`만 띄운다(4.4절).
- **"`toRoute()`는 순수 Kotlin이니 JVM 단위 테스트에서 된다"** → 아니다. 안드로이드에서는 `Bundle`을 거치므로 Robolectric이나 계측 테스트가 필요하다(4.3절 ①).
- **"`currentDestination?.route == "itemDetail"`로 비교하면 된다"** → 타입 안전 route에서는 route 문자열이 클래스 전체 이름 + 인자 템플릿이다. `hasRoute<T>()`로 비교한다.
- **"`TestNavHostController`는 가짜(mock)다"** → 아니다. 진짜 `NavHostController`의 하위 클래스라 실제 백스택 로직이 그대로 돈다. 그래서 `popUpTo`, `launchSingleTop` 같은 규칙을 검증할 수 있다.
- **"뒤로 가기 버튼은 이전 화면으로 `navigate`하면 된다"** → 백스택이 쌓이기만 한다. `navigateUp()`이나 `popBackStack()`을 쓴다(3장 6번).
- **"저장소는 `NavHost`에서 만들어 화면에 넘기면 된다"** → 의존성이 늘면 `NavHost`가 앱의 모든 의존성을 떠안는다. 컴포저블 파라미터 기본값으로 만들면 리컴포지션마다 새 인스턴스가 생긴다. 앱 스코프 객체는 `Application`의 컨테이너(또는 Hilt 모듈)에서 한 번 만든다(5.1절).
- **"`ViewModel`이 만들어지면 곧바로 데이터를 불러와야 한다"** → 생성자는 상태 흐름을 정의만 하고, 실제 호출은 화면이 구독할 때 시작하는 편이 낫다(`stateIn` + `WhileSubscribed`). 테스트에서 "만들기"와 "불러오기"를 따로 검증할 수 있다(5.4절).
- **"Hilt를 쓰면 `ViewModel` 테스트에도 Hilt가 필요하다"** → 아니다. `@Inject constructor`도 평범한 생성자라 가짜를 넣어 직접 만든다. Hilt 테스트 도구는 그래프·화면 통합 테스트에서만 쓴다(5.6절).
- **"Navigation 3가 나왔으니 이 내용은 쓸모없다"** → 원칙(화면은 콜백만, 인자는 id만, 이동 결정은 한곳에서)은 그대로다. Navigation 3에서는 백스택이 개발자가 소유하는 `SnapshotStateList`가 되므로, 그래프 테스트가 "리스트에 무엇이 들어 있는가"를 확인하는 더 단순한 모양이 된다. → [`073-android-navigation-compose-vs-navigation3.md`](../todos/073-android-navigation-compose-vs-navigation3.md)

## 7. 강의 코드에 적용하면

chapter157 `MainActivity.kt`를 이 글의 방식으로 바꾸면 이런 모양이 된다(적용하지는 않았다. 강의 흐름을 보존하려고 FIXME로만 남겨 두었다). 이 스케치는 2·4장과 달리 따로 빌드해 보지 않았다.

```kotlin
@Serializable data object First
@Serializable data class Second(val name: String, val age: Int)

@Composable
fun MyApp(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = First) {
        composable<First> {
            FirstScreen { name, age -> navController.navigate(Second(name, age)) }  // 인코딩·toInt() 문제 사라짐
        }
        composable<Second> { entry ->
            val route = entry.toRoute<Second>()
            SecondScreen(route.name, route.age) { navController.popBackStack() }   // 백스택 누적 사라짐
        }
    }
}
```

chapter157은 서버나 DB가 없고 사용자가 입력한 값을 다음 화면에 보여 주는 예제라, 여기서는 이름·나이를 route에 싣는 것이 자연스럽다. 저장소가 있는 chapter205라면 2.5절처럼 id만 넘기고 `ViewModel`이 조회한다.

## CLOSING

정리하면 내비게이션 테스트를 쉽게 만드는 것은 테스트 기법이 아니라 **구조**다. 화면이 `NavController`를 들고 있으면 모든 테스트가 그래프 전체를 필요로 하고, 이동 결정이 `NavHost` 한곳에 모여 있으면 테스트가 세 층으로 쪼개져 각각 가볍게 끝난다. 처음 짤 때 "이 컴포저블을 람다 하나만 넘겨서 미리보기로 그릴 수 있는가"를 물어보면 대부분 올바른 방향으로 간다.

의존성도 같다. 저장소와 클라이언트를 화면이나 그래프로 내려보내지 않고 `ViewModel`의 생성자로만 받게 하면, 조립 방식(수동 DI든 Hilt든)은 나중에 얼마든지 바꿀 수 있고, 테스트는 가짜 하나를 끼우는 일로 줄어든다.

## REFERENCE

- [Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation)
- [Type safety in Kotlin DSL and Navigation Compose](https://developer.android.com/guide/navigation/design/type-safety)
- [Navigate to a destination](https://developer.android.com/guide/navigation/use-graph/navigate)
- [Navigation and the back stack](https://developer.android.com/guide/navigation/backstack)
- [Encapsulate your navigation code](https://developer.android.com/guide/navigation/design/encapsulate)
- [Test navigation](https://developer.android.com/guide/navigation/testing)
- [Test your Compose layout](https://developer.android.com/develop/ui/compose/testing)
- [Build local unit tests — Robolectric](https://developer.android.com/training/testing/local-tests/robolectric)
- [Robolectric — Configuring Robolectric](https://robolectric.org/configuring/)
- [`TestNavHostController` API](https://developer.android.com/reference/androidx/navigation/testing/TestNavHostController)
- [Now in Android — 기능별 내비게이션 확장 함수 예시](https://github.com/android/nowinandroid)
- [Manual dependency injection](https://developer.android.com/training/dependency-injection/manual)
- [Dependency injection with Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- [Use Hilt with other Jetpack libraries — ViewModel, Compose, Navigation](https://developer.android.com/training/dependency-injection/hilt-jetpack)
- [Hilt testing guide](https://developer.android.com/training/dependency-injection/hilt-testing)
- [Create ViewModels with dependencies — `CreationExtras`, `viewModelFactory`](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories)
- [ViewModel lifecycle](https://developer.android.com/topic/libraries/architecture/viewmodel#lifecycle)
- [Testing Kotlin flows on Android](https://developer.android.com/kotlin/flow/test)
- [Testing Kotlin coroutines on Android — Main 디스패처 교체](https://developer.android.com/kotlin/coroutines/test)
