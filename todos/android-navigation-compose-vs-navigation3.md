# `navigation-compose`와 `navigation3`은 무엇이 다른가

## 질문이 나온 코드

- [`chapter157/app/build.gradle.kts`](../chapter157/app/build.gradle.kts)
- 질문: 이 의존성은 아래 navigation3 의존성들과 다른 것인가? 어떻게 다른가? 이 의존성을 쓰면 모두 최신 방식인가? 무엇이 정석이고 현재 추천하는 방식인가?

```kotlin
implementation(libs.androidx.navigation.compose)                        // ← 질문 대상
...
implementation("androidx.navigation3:navigation3-runtime:1.2.0-alpha07")
implementation("androidx.navigation3:navigation3-ui:1.2.0-alpha07")
```

## 질문 전제 점검

- **"다른 것인가"** → **완전히 다른 라이브러리다.** 이름이 비슷해서 같은 라이브러리의 버전 차이처럼 보이지만 그렇지 않다.

  | | `androidx.navigation:navigation-compose` | `androidx.navigation3:navigation3-*` |
  | --- | --- | --- |
  | 통칭 | **Nav2** (원래의 Jetpack Navigation) | **Nav3** (새로 만든 라이브러리) |
  | 그룹 ID | `androidx.navigation` | `androidx.navigation3` |
  | 설계 시점 | 7년 전, View 시스템 기준 | Compose 전용으로 새로 설계 |
  | 백스택 소유자 | **라이브러리**(`NavController` 내부) | **개발자**(그냥 `List`) |
  | 핵심 API | `NavHost` + `composable` | `NavDisplay` + `entryProvider` |

  패키지 이름도 다르고(`androidx.navigation.compose` vs `androidx.navigation3.runtime`), 클래스도 겹치지 않는다. **한 프로젝트에 둘 다 넣을 수는 있지만 섞어 쓸 이유는 없다.**

- **"이 의존성을 사용하면 모두 최신 방식인가"** → 아니다. **의존성 버전과 코드 작성 방식은 별개다.** 최신 버전 라이브러리를 넣어도 옛날 방식으로 코드를 쓸 수 있다. 지금 프로젝트가 정확히 그 상태다.
  - `navigation3` 의존성을 **넣어 두고 쓰지 않는다.** `MainActivity.kt`는 Nav2의 `NavHost`를 쓴다.
  - 그 Nav2 코드마저 **문자열 route** 방식이라, Nav2 기준으로도 구식이다(2.8.0부터 타입 안전 route 권장). → [`android-navigation-string-route-vs-type-safe.md`](android-navigation-string-route-vs-type-safe.md)

  즉 **한 세대가 아니라 두 세대 뒤에 있다.**

- **"무엇이 정석인가"** → 오늘(2026년 9월) 기준으로 구글의 답은 분명하다. Nav3가 2025년 11월 19일에 1.0 안정 버전이 되었다.

  > "Jetpack Navigation 3 version 1.0 is stable 🎉. Go ahead and use it in your production apps today."

  그리고 마이그레이션을 권한다.

  > "If you're already using Nav2, specifically Navigation Compose, **you should consider migrating to Nav3.**"

  다만 **Nav2가 폐기된 것은 아니다.** 계속 지원되고, 이미 Nav2로 만든 앱을 당장 갈아엎어야 하는 것도 아니다. 새로 시작하는 Compose 앱이라면 Nav3가 기본 선택이다.

- **버전에도 문제가 있다.** `1.2.0-alpha07`은 아직 나오지 않은 1.2.0 라인의 **오래된 알파**다. 현재 상태는 이렇다.
  - 안정 버전: **1.1.7** (2026년 8월 26일)
  - 릴리스 후보: **1.2.0-rc01** (2026년 9월 9일)

  학습 프로젝트라도 알파를 쓸 이유는 없다. 안정 버전을 쓰는 편이 낫다.

## 공부할 내용

### Nav3가 Nav2를 개선한 세 가지

공식 문서가 꼽는 개선점이다.

1. **Compose와의 간단한 통합** — Compose에 맞춘 더 단순한 API
2. **백스택에 대한 완전한 제어** — 개발자가 백스택을 직접 소유한다
3. **적응형 레이아웃 지원** — 여러 목적지가 동시에 백스택을 읽을 수 있어, 창 크기 변화에 대응하기 쉽다

가장 큰 차이는 **2번**이다. Nav2에서는 백스택이 `NavController` 안에 숨어 있어서, "지금 백스택이 어떤 상태인가"를 알려면 라이브러리에 물어봐야 했다. 상태의 진실 공급원이 둘로 갈라지는 문제가 생긴다.

> "Nav2 can make it difficult to have a single source of truth for navigation state because it has its own internal state, while with Nav3 you supply your own state, giving complete control."

Nav3에서 백스택은 그냥 **Compose 상태로 뒷받침되는 리스트**다.

> "Developers own the back stack as a simple list backed by Compose state (specifically `SnapshotStateList<T>`), and can navigate by adding or removing items with state changes observed and reflected by Nav3's UI."

### 두 라이브러리의 코드가 실제로 어떻게 다른가

#### Nav2 — 지금 프로젝트의 방식

```kotlin
val navController = rememberNavController()      // 라이브러리가 백스택을 들고 있다
NavHost(navController = navController, startDestination = "firstScreen") {
    composable("firstScreen") { ... }
    composable("secondScreen/{name}/{age}") { ... }
}
navController.navigate("secondScreen/Jun/20")    // 라이브러리에게 "가 줘"라고 부탁
```

#### Nav3 — 백스택이 그냥 리스트다

공식 문서의 기본 예제다.

```kotlin
data object Home
data class Product(val id: String)

@Composable
fun NavExample() {
    val backStack = remember { mutableStateListOf<Any>(Home) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                is Home -> NavEntry(key) {
                    ContentGreen("Welcome to Nav3") {
                        Button(onClick = { backStack.add(Product("123")) }) {
                            Text("Click to navigate")
                        }
                    }
                }
                is Product -> NavEntry(key) { ContentBlue("Product ${key.id}") }
                else -> NavEntry(Unit) { Text("Unknown route") }
            }
        }
    )
}
```

차이가 눈에 보인다.

```kotlin
backStack.add(Product("123"))    // 앞으로 가기 = 리스트에 추가
backStack.removeLastOrNull()     // 뒤로 가기  = 리스트에서 제거
```

**내비게이션이 특별한 API 호출이 아니라 그냥 상태 변경**이다. `mutableStateListOf`가 바뀌면 `NavDisplay`가 재구성되어 화면이 따라온다. 컴포즈에서 다른 모든 상태를 다루는 방식과 똑같다. 그래서 Nav3는 배울 개념이 오히려 적다.

그리고 **인자 전달에 문자열 조립이 없다.** `Product("123")`이라는 **객체 자체**가 백스택에 들어간다. `key.id`로 바로 꺼내 쓴다. 직렬화, URL 인코딩, 파싱이 전부 사라진다.

### 백스택을 저장하려면 — `NavKey` + `@Serializable`

화면 회전이나 프로세스 종료를 견디려면 `rememberNavBackStack`을 쓴다. 조건이 둘이다.

1. 키가 `NavKey` 인터페이스를 구현할 것
2. `@Serializable`이 붙을 것

```kotlin
@Serializable
data object Home : NavKey

@Composable
fun NavBackStack() {
    val backStack = rememberNavBackStack(Home)
}
```

앱 전체에서 쓸 키 타입을 `sealed interface`로 묶으면 더 안전하다.

```kotlin
@Serializable
sealed interface MyAppNavKey : NavKey

@Serializable
data object ScreenA : MyAppNavKey

@Serializable
data class ScreenB(val id: String) : MyAppNavKey
```

`when`이 모든 키를 강제로 다루게 되어, 화면을 추가하고 분기를 빠뜨리는 실수가 사라진다.

### `ViewModel`을 화면에 붙이기

Nav2에서는 `NavBackStackEntry`가 `ViewModelStoreOwner`였다. Nav3에서는 별도 아티팩트로 분리되어 있다.

```kotlin
// androidx.lifecycle:lifecycle-viewmodel-navigation3
NavDisplay(
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator()      // ← ViewModel 스코핑
    ),
    backStack = backStack,
    entryProvider = entryProvider { },
)
```

`ViewModel`은 항목이 백스택에 들어올 때 만들어지고, 빠질 때 정리된다. 이런 식으로 **필요한 기능만 골라 끼우는 것**이 Nav3의 설계 방향이다.

> "Nav3 is designed to be open and extensible, providing building blocks and helpful defaults, and offers smaller, decoupled APIs that can be combined to create complex functionality rather than a single monolithic API."

Nav2가 "다 들어 있는 하나의 큰 API"였다면, Nav3는 **작은 조각들의 조합**이다.

### 이 프로젝트를 정리한다면

지금은 Nav2와 Nav3 의존성이 함께 들어 있고 Nav2만 쓰인다. 둘 중 하나를 고른다.

**① Nav3로 간다 (권장)**

```kotlin
dependencies {
    implementation("androidx.navigation3:navigation3-runtime:1.1.7")
    implementation("androidx.navigation3:navigation3-ui:1.1.7")
    // navigation-compose 는 제거
}
```

**② 강의 진도를 따라 Nav2를 유지한다**

```kotlin
dependencies {
    implementation(libs.androidx.navigation.compose)
    // navigation3-runtime / navigation3-ui 는 제거
}
```

강의가 Nav2 기준이라면 ②도 합리적인 선택이다. 저장소 지침도 "강의의 학습 단계와 예제 의도를 보존한다"고 되어 있다. **다만 쓰지 않는 의존성을 남겨 두지는 않는다.** 읽는 사람이 "이 프로젝트는 Nav3를 쓰는구나"라고 오해한다. 쓰지 않는 코드를 남기지 않는다는 원칙은 저장소 예제에서도 같다. → [`android-repository-single-source-of-truth.md`](android-repository-single-source-of-truth.md)

②를 고르더라도 **문자열 route만큼은 타입 안전 route로 바꾸는 편이 좋다.** Nav2 안에서 가능한 개선이고, 나중에 Nav3로 옮길 때도 그대로 이어진다. → [`android-navigation-string-route-vs-type-safe.md`](android-navigation-string-route-vs-type-safe.md)

## 관련 아키텍처와 베스트 프랙티스

### 버전 카탈로그를 쓰기로 했으면 일관되게 쓴다

지금 `build.gradle.kts`는 두 방식이 섞여 있다.

```kotlin
implementation(libs.androidx.navigation.compose)                         // 카탈로그
implementation("androidx.navigation3:navigation3-runtime:1.2.0-alpha07") // 하드코딩
```

버전 카탈로그(`gradle/libs.versions.toml`)를 두는 이유는 **버전을 한곳에서 관리**하기 위해서다. 절반만 카탈로그에 넣으면 그 이점이 사라진다.

```toml
[versions]
navigation3 = "1.1.7"

[libraries]
androidx-navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }
```

같은 이야기가 `chapter143`에도 해당한다. Retrofit과 Coil을 문자열로 직접 적고 있다. → [`gradle-build-files-in-android-project.md`](gradle-build-files-in-android-project.md)

### 알파/베타 버전을 쓸 때의 기준

| 접미사 | 의미 | 학습 프로젝트에서 |
| --- | --- | --- |
| (없음) | 안정. API가 바뀌지 않는다 | **기본 선택** |
| `-rc` | 출시 후보. 사실상 확정 | 필요하면 써도 된다 |
| `-beta` | API 확정, 버그 수정 중 | 이유가 있으면 |
| `-alpha` | **API가 언제든 바뀐다** | 피한다 |

알파 버전의 코드는 다음 알파에서 컴파일되지 않을 수 있다. 강의를 따라가다 예제가 갑자기 깨지는 흔한 원인이다.

### "최신"과 "권장"은 다르다

이 질문의 핵심이 여기 있다.

- **최신** — 가장 나중에 나온 것. `1.2.0-alpha07`이 `1.1.7`보다 최신이다.
- **권장** — 지금 프로덕션에 쓸 만한 것. 그건 `1.1.7`이다.

라이브러리 선택도 같다. Nav3가 새롭지만, 팀이 Nav2에 익숙하고 마이그레이션 비용이 크다면 Nav2를 유지하는 것도 합리적인 판단이다. **공식 문서가 "consider migrating"이라고 쓴 것이지 "must migrate"라고 하지 않았다.**

### 어느 쪽을 고를지 판단표

| 상황 | 선택 |
| --- | --- |
| 새 Compose 앱을 시작한다 | **Nav3** |
| 적응형 레이아웃(태블릿 목록-상세)이 필요하다 | **Nav3** — Scenes API |
| 내비게이션 상태를 직접 제어/테스트하고 싶다 | **Nav3** |
| Nav2로 이미 만든 앱이 잘 돌아간다 | 유지, 여유 있을 때 이전 검토 |
| Fragment/View 시스템이 섞여 있다 | **Nav2** — Nav3는 Compose 전용 |
| 강의가 Nav2 기준이다 | **Nav2**, 단 타입 안전 route로 |

## 체크리스트

- [ ] `navigation-compose`와 `navigation3-*`가 별개 라이브러리임을 설명할 수 있다.
- [ ] Nav2와 Nav3의 그룹 ID와 패키지가 다르다는 것을 안다.
- [ ] Nav3가 Nav2를 개선한 세 가지를 말할 수 있다.
- [ ] Nav3에서 백스택이 개발자 소유의 리스트라는 의미를 설명할 수 있다.
- [ ] `backStack.add()` / `removeLastOrNull()`이 내비게이션이 되는 구조를 설명할 수 있다.
- [ ] `rememberNavBackStack`에 필요한 두 조건(`NavKey`, `@Serializable`)을 안다.
- [ ] Nav3 1.0이 안정 버전이 된 시점과 공식 마이그레이션 권고를 안다.
- [ ] 현재 프로젝트가 Nav3 의존성을 쓰지 않고 있다는 점을 확인할 수 있다.
- [ ] `-alpha` 버전을 피해야 하는 이유를 설명할 수 있다.
- [ ] "최신"과 "권장"이 다르다는 것을 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
- [Android Developers: Navigation 3 basics](https://developer.android.com/guide/navigation/navigation-3/basics)
- [Android Developers: Save and manage navigation state (Navigation 3)](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Android Developers: Migrate from Navigation 2 to Navigation 3](https://developer.android.com/guide/navigation/navigation-3/migration-guide)
- [Android Developers Blog: Jetpack Navigation 3 is stable](https://developer.android.com/blog/posts/jetpack-navigation-3-is-stable)
- [Android Developers Blog: Announcing Jetpack Navigation 3](https://android-developers.googleblog.com/2025/05/announcing-jetpack-navigation-3-for-compose.html)
- [Jetpack releases: navigation3](https://developer.android.com/jetpack/androidx/releases/navigation3)
- [Jetpack releases: navigation](https://developer.android.com/jetpack/androidx/releases/navigation)
- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation)
