# `@DrawableRes` 애너테이션 용도가 뭐야

## 질문이 나온 코드

- [`chapter246/app/src/main/java/com/example/chapter_246/Screen.kt`](../chapter246/app/src/main/java/com/example/chapter_246/Screen.kt)
- 질문: `@DrawableRes` 애너테이션 용도가 뭐야?

```kotlin
sealed class Screen(val title: String, val route: String) {
    sealed class DrawerScreen(val dTitle: String, val dRoute: String, @DrawableRes val icon: Int) :
        Screen(dTitle, dRoute) {
        object Account : DrawerScreen("Account", "account", R.drawable.ic_account)
    }
}
```

## 질문 전제 점검

- **`@DrawableRes`는 컴파일러가 처리하는 기능이 아니다.** 코틀린 컴파일러는 이 애너테이션을 보고 아무 것도 하지 않는다. `@DrawableRes val icon: Int`에 `R.string.app_name`을 넣어도 **컴파일은 성공한다.** 이 애너테이션을 읽는 주체는 **Android Lint / Android Studio 인스펙션**이다.

  > "Annotations for resource types, such as `@DrawableRes`, `@DimenRes`, `@ColorRes`, and `@InterpolatorRes` can be added ... and run during the code inspection."

  즉 "타입 안전을 보장한다"기보다 **"정적 분석 도구에게 이 `Int`의 진짜 의미를 알려 주는 메타데이터"**에 가깝다.

- **애초에 왜 이런 게 필요한가**를 먼저 잡아야 한다. 안드로이드에서 리소스 참조는 전부 **`Int` 하나로 표현**된다.

  ```kotlin
  R.drawable.ic_account  // Int
  R.string.app_name      // Int
  R.color.purple_500     // Int
  R.id.container         // Int
  ```

  타입 시스템 입장에서는 넷이 전부 똑같은 `Int`다. 그래서 `painterResource(R.string.app_name)`처럼 **엉뚱한 리소스를 넘겨도 타입 검사를 통과**하고, 런타임에 `Resources.NotFoundException`이나 깨진 화면으로 나타난다.

  > "Validating resource types can be useful because Android references to resources, such as drawable resources, are passed as integers. Code that expects a parameter to reference a specific type of resource can be passed to the expected reference type of `int`, but actually reference a different type of resource."

  `@DrawableRes`는 **타입 시스템이 못 잡는 구멍을 Lint로 메우는 장치**다.

- **애너테이션을 쓰려면 의존성이 있어야 한다.** `androidx.annotation:annotation`이 필요하다. 다만 실무에서는 대부분 `androidx.core`, `appcompat`, `compose` 등이 전이 의존성으로 끌고 오기 때문에 따로 추가하지 않아도 `import androidx.annotation.DrawableRes`가 되는 경우가 많다. 그래서 "언제부터 쓸 수 있었지?"를 못 느끼고 지나간다.

## 공부할 내용

### 무엇을 얻는가 — 실수했을 때 어떻게 되는가

```kotlin
class Item(@DrawableRes val icon: Int)

Item(R.drawable.ic_account)   // OK
Item(R.string.app_name)       // Lint 경고: Expected resource of type drawable
Item(0)                       // 상수도 경고 대상
```

Android Studio에서는 **빨간 줄 또는 노란 줄**이 그어지고, `./gradlew lint`를 돌리면 `ResourceType` 이슈로 리포트된다. **빌드는 막히지 않는다.** 막고 싶으면 lint 설정에서 해당 이슈를 에러로 올려야 한다.

```kotlin
// app/build.gradle.kts
android {
    lint {
        error += "ResourceType"   // 경고를 빌드 실패로 승격
    }
}
```

### 리소스 애너테이션 가족

전부 `androidx.annotation` 패키지에 있고 쓰는 방식은 같다.

| 애너테이션 | 대상 |
| --- | --- |
| `@DrawableRes` | `R.drawable.*` |
| `@StringRes` | `R.string.*` |
| `@ColorRes` | `R.color.*` (색 리소스 **ID**) |
| `@ColorInt` | 실제 색 값 `0xFF0000FF` (ID가 아님) |
| `@DimenRes` | `R.dimen.*` |
| `@LayoutRes` | `R.layout.*` |
| `@IdRes` | `R.id.*` |
| `@AnyRes` | 리소스면 아무거나 |

**`@ColorRes`와 `@ColorInt`의 차이가 가장 자주 헷갈린다.** 전자는 "색을 가리키는 ID", 후자는 "색 그 자체"다. 둘 다 `Int`라서 섞어 쓰면 완전히 엉뚱한 색이 나오고, 이 애너테이션이 없으면 아무도 못 잡는다.

```kotlin
fun setBackground(@ColorInt color: Int)      // 0xFF6200EE 를 기대
fun loadColor(@ColorRes resId: Int): Int     // R.color.purple 을 기대

setBackground(R.color.purple)  // Lint 경고 — ID를 색 값으로 쓰고 있다
```

### 값 범위·스레드 애너테이션도 같은 계열

리소스 애너테이션만 있는 게 아니다. 같은 라이브러리에 **"타입으로 표현이 안 되는 계약"을 적어 두는 애너테이션**이 모여 있다.

```kotlin
@IntRange(from = 0, to = 100) val progress: Int
@FloatRange(from = 0.0, to = 1.0) val alpha: Float
@Size(min = 1) val items: List<String>

@MainThread fun updateUi()
@WorkerThread fun loadFromDisk()
@RequiresPermission(Manifest.permission.CAMERA) fun openCamera()
@CallSuper open fun onCreate()
```

공통 목적은 하나다. **문서에 적어 두면 안 읽히지만, 애너테이션으로 적어 두면 도구가 대신 읽어 준다.**

### Compose에서는 조금 사정이 다르다

XML View 시절에는 `Int` 리소스 ID를 넘기는 것 말고 선택지가 없었다. Compose에는 **더 나은 선택지**가 있다.

```kotlin
// 방법 1: 리소스 ID를 그대로 들고 다닌다 (지금 코드)
sealed class BottomBarScreen(..., @DrawableRes val icon: Int)
Icon(painterResource(item.icon), contentDescription = item.bTitle)

// 방법 2: Painter/ImageVector 를 들고 다닌다 — 타입으로 구분된다
sealed class BottomBarScreen(..., val icon: ImageVector)
Icon(item.icon, contentDescription = item.bTitle)
```

방법 2는 `@DrawableRes`가 아예 필요 없다. `ImageVector`는 `Int`가 아니라서 **Lint가 아니라 타입 시스템이 막아 준다.** 다만 `ImageVector`는 `Icons.Default.*`처럼 Compose가 제공하는 벡터에만 쓸 수 있고, `res/drawable`의 png·xml을 쓰려면 `painterResource`가 필요하므로 방법 1이 남는다.

**지금 코드에서는 방법 1이 맞다.** `res/drawable`의 커스텀 아이콘을 쓰고 있고, `painterResource`는 컴포저블 안에서만 호출할 수 있어서 `object` 선언 시점에 `Painter`를 만들어 둘 수도 없다. 그래서 **`Int`로 들고 다니되 `@DrawableRes`로 의미를 표시하는 것**이 이 상황의 정석이다.

### 애너테이션은 어디에 붙는가

생성자 프로퍼티에 붙일 때는 **적용 대상(use-site target)**을 신경 쓸 필요가 있다.

```kotlin
class A(@DrawableRes val icon: Int)               // 기본값: 생성자 파라미터에 붙는다
class B(@get:DrawableRes val icon: Int)           // getter 반환값에 붙는다
class C(@field:DrawableRes val icon: Int)         // 필드에 붙는다
```

`@DrawableRes`는 `PARAMETER`, `FIELD`, `METHOD`, `LOCAL_VARIABLE` 등 여러 자리에 붙을 수 있게 선언돼 있다. 코틀린은 이 중 **가장 앞선 것 하나**를 고르는데, 생성자 프로퍼티에서는 `PARAMETER`가 선택된다.

**이 챕터를 빌드하면 실제로 이 경고가 뜬다.**

```
w: Screen.kt:7:71 This annotation is currently applied to the value parameter only,
   but in the future it will also be applied to field.
- To opt in to applying to both value parameter and field,
  add '-Xannotation-default-target=param-property' to your compiler arguments.
- To keep applying to the value parameter only, use the '@param:' annotation target.
```

코틀린이 **기본 적용 대상 규칙을 바꾸는 중**이라 나오는 경고다. 지금은 생성자 파라미터에만 붙지만, 앞으로는 파라미터와 필드 양쪽에 붙게 된다. 세 가지 대응이 있다.

```kotlin
// 1. 현재 동작을 명시한다 — 경고가 사라진다
sealed class DrawerScreen(..., @param:DrawableRes val icon: Int)

// 2. 새 동작을 미리 켠다 — build.gradle.kts 의 컴파일러 인자
kotlinOptions { freeCompilerArgs += "-Xannotation-default-target=param-property" }

// 3. 그냥 둔다 — 경고일 뿐이고 Lint 검사는 정상 동작한다
```

**학습용 프로젝트라면 3번으로 두고 넘어가도 된다.** 다만 이 경고가 "애너테이션은 어느 자리에 붙는가"라는 질문 자체를 드러내 준다는 점은 알아 둘 값어치가 있다.

## 관련 아키텍처와 베스트 프랙티스

### 공개 API 경계에 붙인다

내부 지역 변수에까지 강박적으로 붙일 필요는 없다. **남이 값을 넣는 자리**에 붙일 때 값어치가 가장 크다.

```kotlin
// 라이브러리/공용 컴포넌트의 파라미터 — 반드시
@Composable
fun IconRow(@DrawableRes iconRes: Int, @StringRes labelRes: Int)

// 데이터 모델의 리소스 필드 — 권장
data class MenuEntry(val title: String, @DrawableRes val icon: Int)

// 함수 안 임시 변수 — 불필요
val id = R.drawable.ic_account
```

### 도메인 모델에 리소스 ID를 넣을지 고민한다

`@DrawableRes val icon: Int`는 **그 클래스가 안드로이드 리소스에 묶인다**는 뜻이다. `Screen`처럼 UI 정의용 클래스는 문제없다. 하지만 서버에서 받은 도메인 모델(`User`, `Recipe`)에 `@DrawableRes`가 들어가면 UI 관심사가 도메인으로 새어 나온 신호다.

| 클래스 성격 | 리소스 ID 보유 |
| --- | --- |
| 화면 정의 (`Screen`, `TabItem`) | 괜찮다 |
| UI 상태 (`UiState`) | 상황에 따라 괜찮다 |
| 도메인 모델 (`User`, `Order`) | 피한다 — 매핑은 UI 레이어에서 |

### Lint를 CI에서 돌려야 의미가 있다

애너테이션은 **읽어 주는 도구가 실제로 돌아야** 값어치가 생긴다. IDE에서만 보이고 CI에서 안 돌면 결국 놓친다.

```bash
./gradlew :app:lint          # 리포트 생성
./gradlew :app:lintDebug     # 특정 변형만
```

`app/build/reports/lint-results-debug.html`에서 `ResourceType` 항목을 확인한다.

### `@DrawableRes`가 못 막는 것

- **런타임에 계산된 ID** — `resources.getIdentifier("ic_$name", "drawable", packageName)`는 Lint가 추적할 수 없다. 이 API 자체가 난독화·R8과 충돌하므로 권장되지 않는다.
- **`Int`를 한 번 거쳐 간 값** — 다른 변수에 담았다 넘기면 추적이 끊길 수 있다.
- **`0`이 유효한지 여부** — "아이콘 없음"을 `0`으로 표현하는 코드는 Lint 경고가 뜬다. 없을 수 있다면 `Int?`로 표현하는 편이 정확하다.

  ```kotlin
  sealed class Item(@DrawableRes val icon: Int?)   // 아이콘이 선택적이라면
  ```

## 체크리스트

- [ ] `@DrawableRes`가 컴파일러가 아니라 Lint를 위한 표시라는 것을 설명할 수 있다.
- [ ] 안드로이드 리소스 참조가 전부 `Int`여서 생기는 문제를 설명할 수 있다.
- [ ] `@ColorRes`와 `@ColorInt`의 차이를 구분할 수 있다.
- [ ] `@StringRes`, `@LayoutRes` 등 다른 리소스 애너테이션을 안다.
- [ ] `@IntRange`, `@MainThread` 같은 계약 애너테이션이 같은 계열임을 안다.
- [ ] Compose에서 `ImageVector`를 쓰면 애너테이션 없이 타입으로 막을 수 있음을 안다.
- [ ] 이 코드에서 `Int + @DrawableRes`가 맞는 선택인 이유를 설명할 수 있다.
- [ ] 애너테이션을 어디에 붙이는 것이 값어치가 큰지 판단할 수 있다.
- [ ] 빌드 시 나오는 `@param:` 관련 경고가 왜 뜨는지 설명할 수 있다.
- [ ] `./gradlew lint`로 검사를 실행할 수 있다.

## 공식 참고 자료

- [Android Developers: Improve code inspection with annotations](https://developer.android.com/studio/write/annotations)
- [Android Developers: `DrawableRes`](https://developer.android.com/reference/androidx/annotation/DrawableRes)
- [Android Developers: `androidx.annotation` package summary](https://developer.android.com/reference/androidx/annotation/package-summary)
- [Android Developers: Annotation library release notes](https://developer.android.com/jetpack/androidx/releases/annotation)
- [Android Developers: Improve your code with lint checks](https://developer.android.com/studio/write/lint)
- [Android Developers: Resources in Compose](https://developer.android.com/develop/ui/compose/resources)
- [Kotlin Docs: Annotations](https://kotlinlang.org/docs/annotations.html)
