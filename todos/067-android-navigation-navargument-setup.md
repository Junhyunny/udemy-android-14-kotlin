# `navArgument`로 인자를 선언하는 방식이 베스트 프랙티스인가

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/Navigation.kt`](../chapter205/app/src/main/java/com/example/chapter_205/Navigation.kt)
- 질문: 지난번엔 이런 식으로 `arguments`를 셋업해서 쓰지 않았던 것 같은데, 이게 베스트 프랙티스인가? 지난번엔 람다 함수 내부에서 `arguments` 객체에서 직접 꺼내 썼는데.

```kotlin
composable(
    route = Screen.AddScreen.route + "/{id}",
    arguments = listOf(
        navArgument("id") {
            type = NavType.LongType
            defaultValue = 0L
            nullable = false
        }
    )) { entry ->
    val id = if (entry.arguments != null) entry.arguments?.getLong("id") ?: 0L else 0L
    AddEditDetailView(id, navController, viewModel)
}
```

## 질문 전제 점검

- **"지난번 방식과 이번 방식"** → 두 방식이 **경쟁 관계가 아니라 짝**이다. 기억하시는 `chapter157`의 코드와 비교해 보면 분명하다.

  ```kotlin
  // chapter157: 선언 없이 꺼내 쓰기만
  composable(route = "secondScreen/{name}/{age}") {
      val name = it.arguments?.getString("name") ?: "no name"
      val ageString = it.arguments?.getString("age") ?: "0"
  }

  // chapter205: navArgument 로 타입을 선언하고 꺼내 쓰기
  composable(route = "add_screen/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
      val id = it.arguments?.getLong("id") ?: 0L
  }
  ```

  **`navArgument`는 "꺼내는 방식"을 바꾸는 것이 아니라 "타입을 지정하는 것"이다.** 선언이 없으면 모든 인자가 `String`으로 처리되고, 선언하면 `Long`, `Int`, `Boolean` 등으로 파싱된다.

  `chapter157`에서 `ageString.toInt()`로 손수 변환해야 했던 이유가 이것이다. 이번에는 `getLong("id")`로 바로 꺼낼 수 있다. **그 점에서 `chapter205`가 한 걸음 나아간 것은 맞다.**

- **"베스트 프랙티스인가"** → **아니다. 두 방식 모두 현재 권장 방식은 아니다.** Navigation 2.8.0부터 **타입 안전 route**가 나왔고, 그걸 쓰면 `navArgument` 선언 자체가 사라진다.

  ```kotlin
  @Serializable
  data class AddScreenRoute(val id: Long = 0L)

  composable<AddScreenRoute> { entry ->
      val route = entry.toRoute<AddScreenRoute>()
      AddEditDetailView(route.id, navController, viewModel)
  }
  ```

  문자열 route, `navArgument` 선언, `arguments?.getLong(...)`, `?: 0L` 방어 코드가 **전부 사라진다.** → [`066-android-navigation-string-route-vs-type-safe.md`](066-android-navigation-string-route-vs-type-safe.md)

- **그리고 지금 코드에는 불필요하게 장황한 부분이 있다.**

  ```kotlin
  val id = if (entry.arguments != null) entry.arguments?.getLong("id") ?: 0L else 0L
  ```

  `entry.arguments`가 `null`이면 `?.`가 이미 `null`을 만들고 `?: 0L`이 처리한다. **바깥 `if`는 아무 일도 하지 않는다.**

  ```kotlin
  val id = entry.arguments?.getLong("id") ?: 0L     // 이걸로 충분하다
  ```

  게다가 `defaultValue = 0L`을 선언해 두었으므로 인자는 항상 존재한다. 실제로는 `?: 0L`도 방어일 뿐이다.

## 공부할 내용

### `navArgument`가 하는 일

세 가지를 선언한다.

```kotlin
navArgument("id") {
    type = NavType.LongType      // ① 타입
    defaultValue = 0L            // ② 기본값
    nullable = false             // ③ null 허용 여부
}
```

#### ① 타입

선언이 없으면 **전부 `StringType`**이다. 선언하면 URL 문자열을 그 타입으로 파싱해 `Bundle`에 넣는다.

```kotlin
NavType.StringType / IntType / LongType / FloatType / BoolType
NavType.IntArrayType / LongArrayType / ...
NavType.EnumType(MyEnum::class.java)
```

파싱에 실패하면 런타임 예외가 난다.

```
IllegalArgumentException: Navigation destination that matches request cannot be found
```

#### ② 기본값

**기본값이 있으면 그 인자는 선택 사항이 된다.** 다만 **경로 파라미터(`{id}`)에는 사실상 의미가 없다.** 경로에 값이 없으면 route 매칭 자체가 실패하기 때문이다.

기본값이 진짜 효과를 내는 자리는 **쿼리 파라미터**다.

```kotlin
composable(
    route = "search?query={query}&page={page}",
    arguments = listOf(
        navArgument("query") { type = NavType.StringType; defaultValue = "" },
        navArgument("page") { type = NavType.IntType; defaultValue = 1 }
    )
) { ... }

navController.navigate("search?query=coffee")    // page 는 기본값 1
```

`chapter205`의 `/{id}`는 경로 파라미터이므로 `defaultValue = 0L`이 실제로 쓰이는 경우는 거의 없다.

#### ③ nullable

`nullable = true`는 **`StringType`과 참조 타입에만 가능**하다. `Long`, `Int` 같은 원시 타입은 `null`이 될 수 없다.

```kotlin
navArgument("name") { type = NavType.StringType; nullable = true }   // ✅
navArgument("id") { type = NavType.LongType; nullable = true }       // ❌ 런타임 오류
```

`chapter205`가 `nullable = false`라고 명시한 것은 **원시 타입이라 어차피 기본값**이다. 생략해도 같다.

### 경로 파라미터와 쿼리 파라미터

```kotlin
// 경로 파라미터: 반드시 있어야 한다
"detail/{id}"          → navigate("detail/5")

// 쿼리 파라미터: 선택 사항
"search?q={q}"         → navigate("search")  또는  navigate("search?q=coffee")
```

**필수면 경로, 선택이면 쿼리**로 나누는 것이 관례다. `chapter205`가 "새로 만들기"를 `/0`으로 표현하는 것은 이 관례에서 벗어나 있다.

```kotlin
navController.navigate(Screen.AddScreen.route + "/0")     // 0 = 새로 만들기
navController.navigate(Screen.AddScreen.route + "/$id")   // id = 수정
```

**`0`이라는 매직 넘버가 "새로 만들기"를 뜻한다**는 것이 코드 어디에도 적혀 있지 않다. `AddEditDetailView` 안에서 `if (id != 0L)`로 분기하는 것을 보고서야 알 수 있다. 쿼리 파라미터를 쓰면 의도가 드러난다.

```kotlin
composable(
    route = "add_screen?id={id}",
    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })
)
navController.navigate("add_screen")          // 새로 만들기 — 의도가 보인다
navController.navigate("add_screen?id=$id")   // 수정
```

### 인자를 꺼내는 세 가지 자리

```kotlin
// ① composable 람다에서
composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
    val id = entry.arguments?.getLong("id") ?: 0L
}

// ② ViewModel 의 SavedStateHandle 에서  ★ 권장
class DetailViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val id: Long = checkNotNull(savedStateHandle["id"])
}

// ③ 타입 안전 route 에서  ★★ 가장 권장
composable<DetailRoute> { entry ->
    val route = entry.toRoute<DetailRoute>()
    // route.id
}
```

**②가 실용적인 이유**는 화면 컴포저블을 거쳐 인자를 나르지 않아도 되기 때문이다. `chapter205`는 `AddEditDetailView(id, navController, viewModel)`로 `id`를 파라미터로 넘기고 있는데, `ViewModel`이 직접 읽으면 그럴 필요가 없다. → [`069-android-navigation-backstackentry-savedstatehandle.md`](069-android-navigation-backstackentry-savedstatehandle.md)

### 타입 안전 route로 옮기면

```kotlin
@Serializable data object HomeRoute
@Serializable data class AddEditRoute(val id: Long = 0L)

NavHost(navController, startDestination = HomeRoute) {
    composable<HomeRoute> {
        HomeView(navController, viewModel)
    }
    composable<AddEditRoute> { entry ->
        val route = entry.toRoute<AddEditRoute>()
        AddEditDetailView(route.id, navController, viewModel)
    }
}

// 이동
navController.navigate(AddEditRoute())            // 새로 만들기
navController.navigate(AddEditRoute(id = wish.id)) // 수정
```

비교하면 차이가 분명하다.

| | 문자열 + `navArgument` | 타입 안전 route |
| --- | --- | --- |
| route 정의 | `"add_screen/{id}"` 문자열 | `@Serializable data class` |
| 타입 선언 | `navArgument { type = ... }` | 클래스 프로퍼티가 곧 타입 |
| 인자 꺼내기 | `arguments?.getLong("id") ?: 0L` | `route.id` |
| 오타 | 런타임 크래시 | **컴파일 에러** |
| 기본값 | `defaultValue = 0L` | `val id: Long = 0L` |
| "새로 만들기" 표현 | `"/0"` 매직 넘버 | `AddEditRoute()` |

**`navArgument` 블록 전체가 사라지고, 그 정보가 데이터 클래스에 들어간다.**

## 관련 아키텍처와 베스트 프랙티스

### `Screen` sealed class와 함께 쓰기

```kotlin
sealed class Screen(val route: String) {
    object HomeScreen : Screen("home_screen")
    object AddScreen : Screen("add_screen")
}
```

route 문자열을 한곳에 모은 것은 좋은 시작이지만, **인자를 만드는 방법이 호출부에 흩어져 있다.**

```kotlin
navController.navigate(Screen.AddScreen.route + "/0")       // HomeView
navController.navigate(Screen.AddScreen.route + "/$id")     // HomeView 의 다른 곳
```

문자열 방식을 유지한다면 **route를 만드는 함수를 `Screen`에 두는 편**이 낫다.

```kotlin
sealed class Screen(val route: String) {
    object HomeScreen : Screen("home_screen")
    object AddScreen : Screen("add_screen/{id}") {
        fun create(id: Long = 0L) = "add_screen/$id"
    }
}

navController.navigate(Screen.AddScreen.create())          // 새로 만들기
navController.navigate(Screen.AddScreen.create(wish.id))   // 수정
```

**문자열 조립이 한 곳에만 있으면 오타가 한 곳에서만 난다.** → [`003-kotlin-sealed-class-and-interface.md`](003-kotlin-sealed-class-and-interface.md)

### 문자열 조립의 위험은 여전하다

```kotlin
navController.navigate(Screen.AddScreen.route + "/$id")
```

`id`가 숫자라 지금은 안전하다. 하지만 **문자열 인자라면 공백이나 `/`가 들어가는 순간 깨진다.** route는 URI로 파싱되기 때문이다. → [`066-android-navigation-string-route-vs-type-safe.md`](066-android-navigation-string-route-vs-type-safe.md)

### 딥링크를 염두에 둔다

```kotlin
composable(
    route = "add_screen/{id}",
    arguments = listOf(navArgument("id") { type = NavType.LongType }),
    deepLinks = listOf(navDeepLink { uriPattern = "wishapp://add/{id}" })
)
```

`navArgument` 선언은 **딥링크에서 특히 값어치가 있다.** 앱 밖에서 들어오는 URI의 문자열을 `Long`으로 파싱해 주기 때문이다. 선언이 없으면 전부 문자열로 들어온다.

### 인자 개수가 늘어나면

```kotlin
"detail/{a}/{b}/{c}/{d}"      // 이 지점에서 설계를 다시 본다
```

**객체를 통째로 나르지 말고 ID만 넘기는 것**이 공식 권장이다. → [`068-android-navigation-passing-many-arguments.md`](068-android-navigation-passing-many-arguments.md)

### 지금 코드에서 바로 고칠 것

```kotlin
// 불필요한 이중 방어
val id = if (entry.arguments != null) entry.arguments?.getLong("id") ?: 0L else 0L

// 이렇게
val id = entry.arguments?.getLong("id") ?: 0L
```

`nullable = false`도 원시 타입에서는 기본값이라 생략할 수 있다.

## 체크리스트

- [ ] `navArgument`가 "꺼내는 방식"이 아니라 "타입 선언"임을 설명할 수 있다.
- [ ] 선언이 없으면 모든 인자가 `String`이 된다는 것을 안다.
- [ ] `chapter157`에서 `toInt()`가 필요했던 이유를 설명할 수 있다.
- [ ] `defaultValue`가 경로 파라미터에서는 거의 의미가 없는 이유를 안다.
- [ ] `nullable = true`가 원시 타입에서 불가능한 이유를 안다.
- [ ] 경로 파라미터와 쿼리 파라미터를 언제 나눠 쓰는지 안다.
- [ ] `"/0"` 같은 매직 넘버가 왜 문제인지 설명할 수 있다.
- [ ] 인자를 꺼내는 세 가지 자리를 알고 우선순위를 말할 수 있다.
- [ ] 타입 안전 route로 옮기면 무엇이 사라지는지 설명할 수 있다.
- [ ] route 문자열 조립을 한 곳에 모으는 이유를 안다.
- [ ] 딥링크에서 `navArgument`가 특히 유용한 이유를 안다.
- [ ] 불필요한 이중 null 방어를 정리할 수 있다.

## 공식 참고 자료

- [Android Developers: Pass data between destinations](https://developer.android.com/guide/navigation/use-graph/pass-data)
- [Android Developers: Type safety in Navigation Compose](https://developer.android.com/guide/navigation/design/type-safety)
- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation)
- [Android Developers: Create a deep link for a destination](https://developer.android.com/guide/navigation/design/deep-link)
- [Android Developers: `NavType` reference](https://developer.android.com/reference/androidx/navigation/NavType)
- [Android Developers: Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
