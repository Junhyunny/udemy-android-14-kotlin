# 문자열 route 방식은 최신·권장 방식인가

## 질문이 나온 코드

- [`chapter157/app/src/main/java/com/example/chapter_157/MainActivity.kt`](../chapter157/app/src/main/java/com/example/chapter_157/MainActivity.kt)
- 질문: 네비게이션 하는 방식이 최신이고 권장하는 방식인가? 공식 문서에 "Navigation 3은 다음과 같은 방법으로 원래 Jetpack Navigation API를 개선합니다"라는 설명이 있던데?

```kotlin
val navController = rememberNavController()
NavHost(navController = navController, startDestination = "firstScreen") {
    composable("firstScreen") {
        FirstScreen { name, age -> navController.navigate("secondScreen/${name}/${age}") }
    }
    composable(route = "secondScreen/{name}/{age}") {
        val name = it.arguments?.getString("name") ?: "no name"
        val ageString = it.arguments?.getString("age") ?: "0"
        SecondScreen(name, ageString.toInt()) { navController.navigate("firstScreen") }
    }
}
```

## 질문 전제 점검

- **"이 방식이 최신이고 권장하는 방식인가"** → **아니다. 두 단계 뒤처져 있다.**

  ```
  ① 문자열 route (지금 코드)           ← Nav2 초기 방식
      ↓ Navigation 2.8.0 (2024)
  ② 타입 안전 route (@Serializable)    ← Nav2에서 권장하는 방식
      ↓ Navigation 3 1.0 stable (2025-11-19)
  ③ Nav3 (NavDisplay + 백스택 직접 소유) ← 현재 구글 권장
  ```

  즉 "Nav2냐 Nav3냐" 이전에, **Nav2 안에서도 구식**이다. 이 구분이 중요하다. Nav3로 당장 못 가더라도 ②는 지금 바로 적용할 수 있다.

- **"공식 문서에 Nav3 개선사항 설명이 있다"** → 맞고, 실제로 읽어 볼 가치가 있다. 다만 그 개선사항이 지금 코드와 어떻게 이어지는지가 핵심이다. 공식 문서가 꼽는 세 가지는 이렇다.

  1. **Compose와의 간단한 통합**
  2. **백스택에 대한 완전한 제어**
  3. **적응형 레이아웃 지원** — 여러 목적지가 동시에 백스택을 읽을 수 있다

  지금 코드는 2번이 특히 아프다. 백스택이 `NavController` 안에 숨어 있어서, 아래에서 볼 **백스택이 무한히 쌓이는 버그**가 눈에 띄지 않는다.

- **그리고 지금 코드에는 동작하는 버그가 둘 있다.** "최신인가"를 따지기 전에 이것부터다.

  **① 이름에 공백이나 `/`가 들어가면 화면 이동이 깨진다.**

  ```kotlin
  navController.navigate("secondScreen/${name}/${age}")
  ```

  `name`이 `"Hong Gildong"`이면 route는 `"secondScreen/Hong Gildong/20"`이 된다. route는 URI로 파싱되므로 공백·`/`·`?`·`#`·`%`가 들어가면 매칭이 실패하거나 값이 잘린다. `name`이 빈 문자열이면 `"secondScreen//20"`이 되어 아예 매칭되지 않는다. **문자열을 손으로 조립하는 방식에서 반드시 만나는 문제다.**

  **② 백스택이 무한히 쌓인다.**

  ```kotlin
  SecondScreen(name, ageString.toInt()) { navController.navigate("firstScreen") }
  ```

  두 번째 화면에서 "첫 화면으로"를 누르면 `navigate`가 **첫 화면을 새로 쌓는다.** 돌아가는 게 아니다. 두 화면을 왔다 갔다 하면 백스택은 `1 → 2 → 1 → 2 → 1 …`로 계속 길어지고, 시스템 뒤로 가기를 누르면 지나온 화면이 전부 다시 나온다. 뒤로 가는 동작은 `navigate`가 아니라 `popBackStack()`이다.

- **"`it.arguments?.getString("age") ?: "0"` 후 `.toInt()`"** → 여기도 위험하다. 문자열 route에서는 모든 인자가 `String`으로 오고, `toInt()`는 실패하면 예외를 던진다. `age` 자리에 숫자가 아닌 값이 들어오면 크래시다. 타입 안전 route를 쓰면 `Int`로 선언하는 것만으로 사라지는 문제다.

## 공부할 내용

### ① 지금 할 수 있는 개선 — 타입 안전 route (Nav2 2.8.0+)

문자열 대신 **직렬화 가능한 클래스**를 route로 쓴다.

```kotlin
@Serializable
object FirstScreenRoute

@Serializable
data class SecondScreenRoute(val name: String, val age: Int)
```

규칙은 단순하다.

- 인자가 없으면 `object`
- 인자가 있으면 `data class`

그래프도 타입으로 쓴다.

```kotlin
NavHost(navController, startDestination = FirstScreenRoute) {
    composable<FirstScreenRoute> {
        FirstScreen { name, age ->
            navController.navigate(SecondScreenRoute(name, age))   // 객체를 그대로 넘긴다
        }
    }
    composable<SecondScreenRoute> { backStackEntry ->
        val route: SecondScreenRoute = backStackEntry.toRoute()     // 객체로 되돌린다
        SecondScreen(route.name, route.age) { navController.popBackStack() }
    }
}
```

무엇이 사라졌는지 보자.

| 사라진 것 | 이유 |
| --- | --- |
| `"secondScreen/${name}/${age}"` | 문자열 조립이 없다 → 인코딩 문제 없음 |
| `arguments?.getString("name")` | `route.name`으로 바로 꺼낸다 |
| `?: "no name"` 같은 기본값 방어 | 없을 수 없다. 타입이 보장한다 |
| `ageString.toInt()` | `age`가 처음부터 `Int`다 |
| route 문자열 오타의 런타임 크래시 | 컴파일러가 잡는다 |

공식 문서가 정리한 이점이다.

> - 타입 파라미터로 destination 지정: `composable<Profile>`
> - 문자열 route 사용보다 더 견고함
> - `NavArgument` 불필요 - 클래스 정의에서 타입 자동 결정
> - `toRoute()` 확장 함수로 객체 재생성

이 API는 XML 시절의 **Safe Args**를 Compose에서 대체하는 것이다.

필요한 설정은 `kotlinx-serialization` 플러그인 하나다.

```kotlin
// build.gradle.kts
plugins {
    kotlin("plugin.serialization") version "2.2.10"
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```

### `ViewModel`에서도 인자를 받을 수 있다

컴포저블을 거쳐 인자를 넘기지 않아도 된다.

```kotlin
class ProfileViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val profile = savedStateHandle.toRoute<Profile>()
    private val userInfo: Flow<UserInfo> = userInfoRepository.getUserInfo(profile.id)
}
```

`SavedStateHandle`에서 바로 route 객체를 꺼낸다. 화면이 인자를 받아 `ViewModel`에 다시 전달하는 배관이 사라진다.

### ② 한 단계 더 — Navigation 3

Nav3에서는 **백스택이 그냥 리스트**다.

```kotlin
@Serializable data object FirstScreenRoute : NavKey
@Serializable data class SecondScreenRoute(val name: String, val age: Int) : NavKey

@Composable
fun MyApp() {
    val backStack = rememberNavBackStack(FirstScreenRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<FirstScreenRoute> {
                FirstScreen { name, age ->
                    backStack.add(SecondScreenRoute(name, age))   // 앞으로 = 추가
                }
            }
            entry<SecondScreenRoute> { key ->
                SecondScreen(key.name, key.age) {
                    backStack.removeLastOrNull()                  // 뒤로 = 제거
                }
            }
        }
    )
}
```

여기서 앞의 **버그 ②가 구조적으로 사라진다.** "뒤로 간다"가 `removeLastOrNull()`이라고 코드에 그대로 쓰여 있으니, `navigate("firstScreen")`처럼 헷갈릴 여지가 없다. 백스택이 보이는 것 자체가 개선이다.

> "Developers own the back stack as a simple list backed by Compose state (specifically `SnapshotStateList<T>`), and can navigate by adding or removing items with state changes observed and reflected by Nav3's UI."

인자도 **객체 그대로** 백스택에 들어간다. 직렬화는 상태 저장 시점에만 관여하고, 화면에서는 `key.name`으로 바로 읽는다.

Nav2 → Nav3 대응은 이렇다.

| 항목 | Nav2 | Nav3 |
| --- | --- | --- |
| UI 호스트 | `NavHost(navController)` | `NavDisplay(backStack)` |
| 목적지 정의 | `composable<T> { }` | `entry<T> { }` |
| 앞으로 가기 | `navController.navigate(T(...))` | `backStack.add(T(...))` |
| 뒤로 가기 | `navController.popBackStack()` | `backStack.removeLastOrNull()` |
| 인자 꺼내기 | `entry.toRoute<T>().name` | 람다의 `key.name` |

### 세 방식을 나란히 놓고 보기

```kotlin
// ① 지금 코드 — 문자열
navController.navigate("secondScreen/${name}/${age}")
val name = it.arguments?.getString("name") ?: "no name"
val age = (it.arguments?.getString("age") ?: "0").toInt()

// ② 타입 안전 route (Nav2)
navController.navigate(SecondScreenRoute(name, age))
val route = backStackEntry.toRoute<SecondScreenRoute>()

// ③ Nav3
backStack.add(SecondScreenRoute(name, age))
// entry<SecondScreenRoute> { key -> ... key.name, key.age }
```

**아래로 갈수록 코드가 짧아지고, 컴파일러가 잡아 주는 실수가 많아진다.**

### 강의를 따라가는 중이라면

강의가 문자열 route로 설명한다면 그 흐름 자체는 지켜도 된다. 저장소 지침도 "강의의 학습 단계와 예제 의도를 보존한다"고 되어 있다. 문자열 route를 한 번 겪어 봐야 타입 안전 route가 무엇을 해결하는지 체감된다.

다만 **버그 두 개는 강의 진도와 무관하다.** 뒤로 가기를 `popBackStack()`으로 고치는 것 정도는 지금 해 두는 편이 좋다.

## 관련 아키텍처와 베스트 프랙티스

### 컴포저블은 `NavController`를 모르는 편이 좋다

지금 코드는 이 원칙을 이미 잘 지키고 있다.

```kotlin
fun FirstScreen(navigationToSecondScreen: (String, Int) -> Unit)   // 람다로 받는다
```

`FirstScreen` 안에서 `navController.navigate(...)`를 직접 부르지 않고 **람다를 받는다.** 덕분에 `FirstScreen`은 내비게이션 라이브러리를 몰라도 되고, 프리뷰와 테스트에서 그냥 `{ _, _ -> }`를 넘기면 된다. Nav2에서 Nav3로 갈아타도 **화면 컴포저블은 한 줄도 바뀌지 않는다.**

다만 프리뷰의 캐스팅은 불필요하다.

```kotlin
// 지금
FirstScreen({ } as (String, Int) -> Unit)   // 캐스팅이 필요 없다

// 이렇게
FirstScreen { _, _ -> }
```

`{ } as (String, Int) -> Unit`은 인자 개수가 맞지 않는 람다를 강제로 캐스팅하는 것이라, 호출되면 실패할 수 있다. 프리뷰에서는 호출되지 않아 드러나지 않을 뿐이다.

### 내비게이션 함수 이름은 동작을 말한다

```kotlin
// SecondScreen.kt — 첫 화면으로 가는데 이름이 'ToSecondScreen'이다
fun SecondScreen(name: String, age: Int, navigateToSecondScreen: () -> Unit)
```

`navigateBack` 또는 `onBackClick`이 맞다. 이름이 동작과 어긋나면 읽는 사람이 코드를 의심하게 된다.

### `navigate`와 `popBackStack`의 차이

| 하고 싶은 일 | Nav2 | Nav3 |
| --- | --- | --- |
| 새 화면으로 | `navigate(Route)` | `backStack.add(key)` |
| 이전 화면으로 | `popBackStack()` | `backStack.removeLastOrNull()` |
| 특정 화면까지 되돌리기 | `popBackStack(Route, false)` | 리스트를 직접 자른다 |
| 현재 화면 대체 | `navigate(R) { popUpTo(현재) { inclusive = true } }` | `removeLastOrNull()` 후 `add()` |
| 로그인 후 홈으로(되돌아가기 금지) | `navigate(Home) { popUpTo(0) }` | `backStack.clear(); backStack.add(Home)` |

Nav3 쪽이 **리스트 연산으로 읽힌다**는 점이 장점이다. `popUpTo`/`inclusive`/`launchSingleTop` 같은 옵션 조합을 외우지 않아도 된다.

### 상태를 어디에 둘 것인가

```kotlin
val name = remember { mutableStateOf("") }
val age = remember { mutableIntStateOf(0) }
```

`remember`로 잡은 상태는 **컴포저블이 컴포지션을 떠나면 사라진다.** 화면을 이동했다가 돌아오면 입력값이 초기화된다. 회전에도 살아남지 않는다.

- 회전만 견디면 된다 → `rememberSaveable`
- 화면을 떠났다 와도 유지해야 한다 → 그 화면에 스코프된 `ViewModel`

→ [`compose-state-storage-and-snapshot.md`](compose-state-storage-and-snapshot.md), [`android-viewmodel-role-and-remember.md`](android-viewmodel-role-and-remember.md)

참고로 `mutableIntStateOf`를 쓴 것은 잘한 선택이다. `Int` 박싱을 피한다. 다만 읽을 때는 `age.value`가 아니라 `age.intValue`로 통일하는 편이 낫다. 지금은 두 방식이 섞여 있다.

## 체크리스트

- [ ] 문자열 route → 타입 안전 route → Nav3의 세 단계를 설명할 수 있다.
- [ ] 지금 코드가 Nav2 기준으로도 구식인 이유를 설명할 수 있다.
- [ ] 이름에 공백이나 `/`가 들어갈 때 문자열 route가 깨지는 이유를 설명할 수 있다.
- [ ] `navigate("firstScreen")`이 백스택을 쌓는다는 것과 `popBackStack()`의 차이를 안다.
- [ ] `@Serializable` 클래스를 route로 정의하고 `toRoute()`로 꺼낼 수 있다.
- [ ] 타입 안전 route가 없애 주는 방어 코드가 무엇인지 나열할 수 있다.
- [ ] `SavedStateHandle.toRoute<T>()`로 `ViewModel`에서 인자를 받을 수 있다.
- [ ] Nav3에서 `backStack.add()` / `removeLastOrNull()`이 내비게이션이 되는 구조를 안다.
- [ ] 화면 컴포저블이 `NavController`를 직접 알지 않아야 하는 이유를 설명할 수 있다.
- [ ] `remember`와 `rememberSaveable`, `ViewModel`의 생존 범위 차이를 안다.

## 공식 참고 자료

- [Android Developers: Type safety in Navigation Compose](https://developer.android.com/guide/navigation/design/type-safety)
- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation)
- [Android Developers: Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
- [Android Developers: Navigation 3 basics](https://developer.android.com/guide/navigation/navigation-3/basics)
- [Android Developers: Migrate from Navigation 2 to Navigation 3](https://developer.android.com/guide/navigation/navigation-3/migration-guide)
- [Android Developers Blog: Jetpack Navigation 3 is stable](https://developer.android.com/blog/posts/jetpack-navigation-3-is-stable)
- [Android Developers: Navigation overview](https://developer.android.com/guide/navigation)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
