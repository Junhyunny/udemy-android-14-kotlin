# `androidx.navigation`과 `androidx.navigation.compose`의 차이

## 질문이 나온 코드

- [`chapter186/app/src/main/java/com/example/chapter_186/MainActivity.kt`](../chapter186/app/src/main/java/com/example/chapter_186/MainActivity.kt)
- 질문: `NavHost`, `NavController` compose 패키지와 compose가 아닌 패키지의 차이점을 설명해줘. 그리고 compose가 있는 패키지와 없는 패키지 각각 언제 사용하는지, 어떻게 사용하는지, 사용 케이스에 대해 정리해줘.

```kotlin
import androidx.navigation.NavHostController          // compose 없음
import androidx.navigation.compose.NavHost            // compose 있음
import androidx.navigation.compose.composable         // compose 있음
import androidx.navigation.compose.dialog             // compose 있음
import androidx.navigation.compose.rememberNavController
```

## 질문 전제 점검

- **두 패키지는 "같은 것의 두 버전"이 아니다. 층이 다르다.** 고르는 문제가 아니라 **아래층과 위층**의 관계다.

  ```
  androidx.navigation.compose   ← Compose 어댑터 (화면을 컴포저블로 그린다)
  ─────────────────────────────
  androidx.navigation           ← 코어 (백스택, 목적지, 인자, 상태 관리)
  ```

  Compose 앱이라고 해서 `androidx.navigation`을 안 쓰는 게 아니다. **밑에서 항상 돌아가고 있다.** 그래서 import가 섞이는 것이 정상이다.

- **`NavHost`라는 이름이 양쪽에 다 있어서 혼동이 생긴다. 그런데 둘은 완전히 다른 것이다.**

  | | 정체 |
  | --- | --- |
  | `androidx.navigation.NavHost` | **인터페이스** — "나는 내비게이션을 담는 그릇이다"라는 계약. `getNavController()` 하나뿐 |
  | `androidx.navigation.compose.NavHost` | **컴포저블 함수** — 실제로 화면을 그린다 |

  지금 코드가 부르는 것은 **컴포저블 쪽**이다. 코어의 `NavHost` 인터페이스는 직접 쓸 일이 거의 없다.

- **`NavController`와 `NavHostController`도 구분이 필요하다.** 둘 다 `androidx.navigation` 패키지에 있다.

  ```kotlin
  val navController: NavHostController = rememberNavController()
  ```

  | 타입 | 할 수 있는 것 |
  | --- | --- |
  | `NavController` | `navigate()`, `popBackStack()` — **이동만** |
  | `NavHostController` | 위 + `setGraph()`, `setLifecycleOwner()` — **그래프 설정까지** |

  `NavHostController`가 `NavController`를 상속한다. **`NavHost`에 넘길 때는 `NavHostController`가 필요하고, 화면에 넘길 때는 `NavController`로 충분하다.**

  `chapter246`에는 이런 코드가 있었다.

  ```kotlin
  NavHost(navController = navController as NavHostController, ...)
  ```

  `NavController`로 받아 놓고 다시 내려 캐스팅하는 형태인데, **처음부터 `NavHostController`로 받으면 캐스팅이 필요 없다.** → [`kotlin-is-operator-and-smart-cast.md`](kotlin-is-operator-and-smart-cast.md)

## 공부할 내용

### 아티팩트 구조

패키지 이름과 Gradle 의존성이 대응한다.

| 아티팩트 | 주요 패키지 | 역할 |
| --- | --- | --- |
| `navigation-common` | `androidx.navigation` | `NavDestination`, `NavArgument` 등 기본 타입 |
| `navigation-runtime` | `androidx.navigation` | `NavController`, `NavHostController`, 백스택 |
| `navigation-compose` | `androidx.navigation.compose` | `NavHost` 컴포저블, `composable`, `dialog`, `rememberNavController` |
| `navigation-fragment` | `androidx.navigation.fragment` | `NavHostFragment` — View 기반 |
| `navigation-ui` | `androidx.navigation.ui` | `Toolbar`, `BottomNavigationView` 연동 |

**`navigation-compose`를 넣으면 `navigation-runtime`이 전이 의존성으로 딸려 온다.** 그래서 `androidx.navigation.*`을 따로 선언하지 않아도 import가 된다.

이 프로젝트의 `build.gradle.kts`에는 둘이 다 적혀 있다.

```kotlin
implementation(libs.androidx.navigation.runtime.ktx)        // 명시적
implementation("androidx.navigation:navigation-compose:2.10.1")  // 이게 위를 포함한다
```

`navigation-compose`만 남겨도 동작한다. 다만 명시해 두는 것이 나쁜 것은 아니다.

### 어느 쪽을 쓰는가 — 판단 기준은 "화면이 무엇인가"

> "If your app is built entirely with Jetpack Compose, use Navigation Compose, where destinations in your graph are composables. If your app uses Views or a mix of Views and Compose, use the Fragment-based Navigation component."

| 앱 구성 | 선택 |
| --- | --- |
| 전부 Compose | **Navigation Compose** — 목적지가 컴포저블 |
| 전부 View/Fragment | **Navigation Fragment** — 목적지가 프래그먼트 |
| 섞여 있다 | **Fragment 기반**으로 두고, 프래그먼트 안에 Compose를 담는다 |

섞인 경우의 권장 경로가 명확하다. **프래그먼트를 껍데기로 두고 내용만 Compose로 바꾼 뒤, 전부 Compose가 되면 Navigation Compose로 옮긴다.**

### 각각 어떻게 쓰는가

**Compose 방식 — 그래프를 코틀린 DSL로 선언한다**

```kotlin
@Composable
fun Navigation() {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = "shoppinglistscreen") {
        composable("shoppinglistscreen") { ShoppingListApp(...) }
        dialog("locationscreen") { LocationSelectionScreen(...) }
    }
}
```

**Fragment 방식 — 그래프를 XML로 선언한다**

```xml
<!-- res/navigation/nav_graph.xml -->
<navigation app:startDestination="@id/shoppingListFragment">
    <fragment android:id="@+id/shoppingListFragment"
              android:name="com.example.ShoppingListFragment" />
    <dialog android:id="@+id/locationDialogFragment"
            android:name="com.example.LocationDialogFragment" />
</navigation>
```

```kotlin
val navHostFragment = supportFragmentManager
    .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
val navController = navHostFragment.navController
```

**공통점이 많다는 점이 중요하다.** `navigate()`, `popBackStack()`, `NavOptions`, 백스택 개념은 **양쪽이 완전히 같다.** 코어가 같기 때문이다. → [`android-navigation-navoptions-builder.md`](android-navigation-navoptions-builder.md)

### 왜 `rememberNavController()`가 필요한가

코어의 `NavHostController`를 그냥 만들면 Compose에서 제대로 동작하지 않는다.

```kotlin
// 이렇게 쓰지 않는다
val navController = NavHostController(context)

// 이렇게 쓴다
val navController = rememberNavController()
```

`rememberNavController()`는 세 가지를 해 준다.

1. **`remember`** — 재구성 때마다 새로 만들어지지 않는다
2. **`rememberSaveable`로 백스택 복원** — 프로세스가 죽었다 살아나도 화면 스택이 유지된다
3. **필요한 `Navigator` 등록** — `ComposeNavigator`, `DialogNavigator`를 붙인다

3번이 `composable`과 `dialog`가 동작하는 이유다. **Navigator가 등록돼 있지 않으면 해당 목적지 타입을 쓸 수 없다.**

→ [`compose-remember-mutablestate-and-by.md`](compose-remember-mutablestate-and-by.md)

### 화면 컴포저블에는 어떤 타입을 넘기나

```kotlin
// 권장: 최소 권한
@Composable
fun ShoppingListApp(navController: NavController, ...)

// 더 권장: 아예 컨트롤러를 넘기지 않는다
@Composable
fun ShoppingListApp(onNavigateToLocation: () -> Unit, ...)
```

**화면 컴포저블이 `NavController`를 직접 들고 있으면 미리보기와 테스트가 어려워진다.** 콜백으로 바꾸면 화면은 "어디로 가는지" 모르고 "무슨 일이 일어났는지"만 알린다.

→ [`compose-navigation-prop-drilling.md`](compose-navigation-prop-drilling.md)

지금 코드는 `ShoppingListApp(navController = navController, ...)`로 컨트롤러를 통째로 넘기고 있다. 강의 단계에서는 흔한 형태지만, 화면이 늘어나면 정리 대상이 된다.

### 그리고 세 번째 선택지 — Navigation 3

`build.gradle.kts`에 이런 줄도 들어 있다.

```kotlin
implementation("androidx.navigation3:navigation3-runtime:1.2.0-alpha07")
implementation("androidx.navigation3:navigation3-ui:1.2.0-alpha07")
```

**패키지가 `androidx.navigation3`으로 완전히 다르다.** 기존 Navigation의 후속으로 설계된 별개 라이브러리이고, 현재 코드에서는 실제로 쓰이지 않는다. → [`android-navigation-compose-vs-navigation3.md`](android-navigation-compose-vs-navigation3.md)

**쓰지 않는 의존성은 지우는 것이 맞다.** 특히 `alpha` 버전은 빌드 시간과 혼란만 늘린다.

## 관련 아키텍처와 베스트 프랙티스

### import를 보고 층을 읽는 습관

```kotlin
import androidx.navigation.NavHostController     // 코어 타입
import androidx.navigation.compose.NavHost       // Compose 어댑터
import androidx.navigation.compose.composable    // Compose 어댑터
```

**패키지 이름이 그 클래스의 소속 층을 말해 준다.** `.compose`가 붙어 있으면 "컴포저블 세계의 물건", 없으면 "UI 방식과 무관한 코어"다. 같은 규칙이 다른 Jetpack 라이브러리에도 적용된다.

| 코어 | Compose 어댑터 |
| --- | --- |
| `androidx.lifecycle.ViewModel` | `androidx.lifecycle.viewmodel.compose.viewModel()` |
| `androidx.paging.PagingData` | `androidx.paging.compose.collectAsLazyPagingItems()` |
| `androidx.navigation.NavController` | `androidx.navigation.compose.rememberNavController()` |

### `NavController`는 하나의 `NavHost`에 속한다

> "Each `NavHost` you create has its own corresponding `NavController`."

중첩 그래프를 만들면 `NavController`가 여러 개가 될 수 있다. 어느 컨트롤러로 `navigate()`하느냐에 따라 **어느 백스택에 쌓이는지가 달라진다.** 바텀 내비게이션처럼 탭마다 스택이 필요한 구조에서 중요해진다.

### 타입 안전 route로 가는 흐름

문자열 route는 오타를 컴파일러가 못 잡는다.

```kotlin
composable("shoppinglistscreen") { ... }
navController.navigate("locationscreen")   // 오타 나면 런타임 크래시
```

최신 Navigation은 `@Serializable` 타입을 목적지로 쓸 수 있다.

```kotlin
@Serializable data object ShoppingList
@Serializable data class LocationPicker(val lat: Double, val lng: Double)

composable<ShoppingList> { ... }
navController.navigate(LocationPicker(37.5, 127.0))
```

**route 문자열과 인자 파싱이 사라진다.** → [`android-navigation-string-route-vs-type-safe.md`](android-navigation-string-route-vs-type-safe.md)

## 체크리스트

- [ ] 두 패키지가 선택지가 아니라 층 관계임을 설명할 수 있다.
- [ ] `androidx.navigation.NavHost`가 인터페이스임을 안다.
- [ ] `NavController`와 `NavHostController`의 차이를 안다.
- [ ] 어느 쪽을 화면 파라미터로 받아야 하는지 판단할 수 있다.
- [ ] 아티팩트와 패키지의 대응 관계를 안다.
- [ ] Compose 앱 / View 앱 / 혼합 앱의 선택 기준을 안다.
- [ ] 혼합 앱의 권장 마이그레이션 경로를 설명할 수 있다.
- [ ] `rememberNavController()`가 해 주는 세 가지를 안다.
- [ ] Navigator 등록이 `composable`/`dialog` 동작의 전제임을 안다.
- [ ] `.compose` 접미 규칙이 다른 라이브러리에도 적용됨을 안다.
- [ ] 화면에 `NavController`를 넘기는 것의 단점을 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation)
- [Android Developers: Navigation overview](https://developer.android.com/guide/navigation)
- [Android Developers: Create a navigation controller](https://developer.android.com/guide/navigation/navcontroller)
- [Android Developers: `androidx.navigation.compose` package summary](https://developer.android.com/reference/kotlin/androidx/navigation/compose/package-summary)
- [Android Developers: `NavController`](https://developer.android.com/reference/androidx/navigation/NavController)
- [Android Developers: `NavHostController`](https://developer.android.com/reference/androidx/navigation/NavHostController)
- [Android Developers: `NavHost` (interface)](https://developer.android.com/reference/androidx/navigation/NavHost)
- [Android Developers: Navigation release notes](https://developer.android.com/jetpack/androidx/releases/navigation)
