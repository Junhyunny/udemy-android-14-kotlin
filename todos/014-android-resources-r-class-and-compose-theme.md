# `R` 객체는 무엇이고, `res/values` xml과 `ui/theme` 중 무엇을 쓰나

## 질문이 나온 코드

- [`chapter205/app/src/main/res/values/strings.xml`](../chapter205/app/src/main/res/values/strings.xml)
- [`chapter205/app/src/main/java/com/example/chapter_205/AppBar.kt`](../chapter205/app/src/main/java/com/example/chapter_205/AppBar.kt)
- 질문: `res/values` 내부의 xml 파일들은 뭔가? 어떻게 쓰는 게 베스트 프랙티스인가? `R` 객체는 뭔가? 그리고 프로젝트에 `ui.theme` 디렉토리가 있는데 여기 있는 파일을 쓰는 게 맞나, 아니면 xml 파일을 쓰는 게 맞나?

```kotlin
Text(
    title,
    color = colorResource(id = R.color.white),      // xml 리소스
    // ...
)
```

## 질문 전제 점검

- **"`ui.theme`와 xml 중 무엇이 맞나"** → **둘 중 하나를 고르는 문제가 아니다.** 둘은 경쟁 관계가 아니라 **역할이 다르다.**

  | | `res/values/*.xml` | `ui/theme/*.kt` |
  | --- | --- | --- |
  | 읽는 주체 | 안드로이드 **플랫폼**(시스템, XML 레이아웃, 매니페스트) | **Compose 런타임**만 |
  | 대표 용도 | 앱 이름, 런처 아이콘 테마, 문자열, 구성별 대체 리소스 | 화면에 그릴 색·타이포그래피 |
  | 다국어·다크모드 자동 전환 | ✅ 디렉토리 한정자로 시스템이 골라 준다 | ❌ 코드로 분기 |
  | 없앨 수 있나 | ❌ `strings.xml`, `themes.xml`은 필수 | ✅ 없어도 앱은 돈다 |

  **경계를 한 줄로 정리하면 이렇다.**

  > **문자열은 xml, 색·타이포그래피는 Compose 테마.**

  지금 `chapter205`는 그 경계가 섞여 있다. 문자열은 `stringResource(R.string.add_wish)`로 잘 쓰고 있는데, 색은 `colorResource(R.color.white)`로 xml에서 가져온다. 그래서 다크 모드가 와도 색이 따라가지 않는다. 앞서 앱바가 안 보였던 문제도 같은 뿌리였다.

- **"`R` 객체는 뭐야"** → 개발자가 만드는 것이 아니라 **빌드할 때 자동 생성되는 클래스**다.

  > "When your application is compiled, `aapt` generates the `R` class, which contains resource IDs for all the resources in your `res/` directory."

  안에 들어 있는 것은 실제 값이 아니라 **정수 ID**다. `R.color.white`는 흰색이 아니라 `0x7f050123` 같은 숫자다. 그 숫자로 실행 시점에 진짜 값을 찾아온다. 이 구분이 중요한 이유는 아래에서 다룬다.

- **"`res/values` 안의 xml은 뭐야"** → 이름이 아니라 **내용으로 구분된다.** `strings.xml`, `colors.xml`, `themes.xml`은 관례적인 파일명일 뿐, 안드로이드는 파일명을 보지 않는다. `<string>` 태그가 들어 있으면 문자열 리소스가 된다. `values/` 디렉토리 안에서라면 파일을 어떻게 나눠도 된다.

## 공부할 내용

### `R` 클래스가 실제로 하는 일

```
res/values/colors.xml
  <color name="app_bar_color">#DD1E5F</color>
        ↓ 빌드
R.color.app_bar_color  =  0x7f050001    (그냥 정수)
        ↓ 실행 시점
colorResource(R.color.app_bar_color)  →  Color(0xFFDD1E5F)
```

**두 단계로 나뉘어 있다는 것이 핵심이다.** 컴파일 시점에는 ID만 정해지고, 진짜 값은 **실행 시점의 기기 설정에 따라** 결정된다. 그래서 같은 `R.string.app_name`이 한국어 기기에서는 한국어를, 영어 기기에서는 영어를 돌려준다.

접근 문법은 이렇다.

```
R.<리소스 타입>.<리소스 이름>

R.string.add_wish      ← res/values/strings.xml 의 <string name="add_wish">
R.color.app_bar_color  ← res/values/colors.xml 의 <color name="app_bar_color">
R.drawable.ic_launcher_background
R.id.my_view
```

Compose에서 읽는 함수들이다.

```kotlin
stringResource(R.string.add_wish)                    // String
stringResource(R.string.greeting, userName)          // 포맷 인자도 가능
pluralStringResource(R.plurals.items, count, count)  // 복수형
colorResource(R.color.app_bar_color)                 // Color
dimensionResource(R.dimen.padding_large)             // Dp
painterResource(R.drawable.ic_launcher_foreground)   // Painter
```

컴포저블이 아닌 곳에서는 `Context`를 거친다.

```kotlin
val greeting = context.getString(R.string.hello_world)
```

→ [`020-android-context-types-and-application-context.md`](020-android-context-types-and-application-context.md)

### `res/` 디렉토리 종류

> | Directory | Resource Type |
> | --- | --- |
> | `drawable/` | Bitmap files (PNG, `.9.png`, JPG, or GIF) or XML files that are compiled into drawable resource subtypes |
> | `mipmap/` | Drawable files for different launcher icon densities |
> | `raw/` | Arbitrary files to save in their raw form |
> | `values/` | XML files that contain simple values, such as strings, integers, and colors |
> | `xml/` | Arbitrary XML files that can be read at runtime |
> | `font/` | Font files with extensions such as TTF, OTF, or TTC |

`chapter205`에 들어 있는 것들이다.

```
res/
├── values/
│   ├── strings.xml     앱 이름, 화면 문구
│   ├── colors.xml      색 정의
│   └── themes.xml      플랫폼 테마 (스플래시, 상태 표시줄 등)
├── drawable/           벡터 아이콘
├── mipmap-*/           런처 아이콘 (밀도별)
└── xml/                backup_rules, data_extraction_rules
```

**`drawable`과 `mipmap`을 헷갈리기 쉽다.** `mipmap`은 **런처 아이콘 전용**이다. 런처는 기기 밀도보다 큰 아이콘을 요구할 수 있어서, 다른 밀도 리소스가 제거되어도 `mipmap`은 남는다. 앱 안에서 쓰는 이미지는 `drawable`에 둔다.

### 리소스를 분리하는 진짜 이유 — 구성 한정자

> "Always externalize app resources such as images and strings from your code, so that you can maintain them independently. Also, provide alternative resources for specific device configurations by grouping them in specially named resource directories. At runtime, Android uses the appropriate resource based on the current configuration."

**"코드에서 분리하면 나중에 고치기 편하다"는 부수적인 이유다.** 진짜 이유는 **시스템이 상황에 맞는 값을 자동으로 골라 준다**는 것이다.

```
res/values/strings.xml       기본(영어)
res/values-ko/strings.xml    한국어 기기
res/values-ja/strings.xml    일본어 기기

res/values/colors.xml        라이트 모드
res/values-night/colors.xml  다크 모드

res/values/dimens.xml        기본
res/values-sw600dp/dimens.xml 태블릿
```

코드는 `stringResource(R.string.add_wish)` 하나만 쓰면 되고, **어느 파일을 읽을지는 시스템이 정한다.** 이것이 하드코딩 대신 리소스를 쓰는 핵심 동기다.

```kotlin
// 나쁨: 번역할 수 없다
Text("Add Wish")

// 좋음: values-ko/strings.xml 만 추가하면 한국어가 된다
Text(stringResource(R.string.add_wish))
```

`chapter205`가 화면 제목에 `stringResource`를 쓴 것은 잘한 선택이다. 반면 `AppBarView(title = "Wish List")`는 하드코딩이라 번역되지 않는다.

### Compose 테마는 다른 계층이다

```kotlin
// ui/theme/Color.kt
val Purple40 = Color(0xFF6650a4)

// ui/theme/Theme.kt
private val LightColorScheme = lightColorScheme(primary = Purple40, ...)

@Composable
fun Chapter205Theme(darkTheme: Boolean = isSystemInDarkTheme(), ...) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
```

`MaterialTheme`은 `CompositionLocal`로 색·타이포그래피를 컴포지션 트리 아래로 내려보낸다. 읽을 때는 이렇게 쓴다.

```kotlin
MaterialTheme.colorScheme.primary
MaterialTheme.colorScheme.surface
MaterialTheme.typography.titleLarge
```

**xml 리소스와 달리 시스템이 개입하지 않는다.** 다크 모드 분기도 `isSystemInDarkTheme()`을 코드에서 직접 읽어 처리한다. 대신 얻는 것이 있다.

- **의미 기반 색 이름**: `Color.Red`가 아니라 `colorScheme.error`. 테마를 바꾸면 전부 따라온다
- **동적 색상**(Android 12+): 사용자 배경화면에서 색을 뽑아 쓴다
- **자동 대비**: `Surface`가 `onSurface`를 `LocalContentColor`로 내려 준다

### `chapter205`에 적용한다면

지금은 색을 xml에서 가져오고 있다.

```kotlin
// AppBar.kt
containerColor = colorResource(id = R.color.app_bar_color)
color = colorResource(id = R.color.white)

// HomeView.kt
colors = CardColors(
    containerColor = colorResource(id = R.color.white),
    contentColor = colorResource(id = R.color.black),
    // ...
)
```

**동작은 하지만 다크 모드가 오면 전부 손으로 고쳐야 한다.** 흰 카드에 검은 글씨가 다크 모드에서도 그대로 흰 카드다. Compose 테마로 옮기면 이렇게 된다.

```kotlin
// ui/theme/Color.kt
val BrandPink = Color(0xFFDD1E5F)

// ui/theme/Theme.kt
private val LightColorScheme = lightColorScheme(
    primary = BrandPink,
    onPrimary = Color.White,
)
private val DarkColorScheme = darkColorScheme(
    primary = BrandPink,
    onPrimary = Color.White,
)

// AppBar.kt
TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary,
    ),
    title = { Text(title) }          // 색을 따로 안 줘도 onPrimary 가 적용된다
)

// HomeView.kt
Card(colors = CardDefaults.cardColors())   // 테마가 알아서 맞춘다
```

`titleContentColor`를 테마에서 받으면 `Text`에 색을 직접 줄 필요가 없어진다. **앞서 흰 글씨가 안 보였던 문제가 구조적으로 사라진다.**

다만 `dynamicColor = true`가 기본이라 브랜드 색이 무시될 수 있다. 브랜드 색을 지키려면 꺼야 한다.

```kotlin
fun Chapter205Theme(dynamicColor: Boolean = false, ...)
```

## 관련 아키텍처와 베스트 프랙티스

### 무엇을 어디에 둘지

| 대상 | 위치 | 이유 |
| --- | --- | --- |
| 사용자에게 보이는 **문자열** | `res/values/strings.xml` | 번역 |
| 앱 이름, 런처 아이콘 | `res/values`, `mipmap` | 시스템이 읽는다 |
| 스플래시·상태 표시줄 테마 | `res/values/themes.xml` | Compose 시작 전에 필요 |
| **색, 타이포그래피** | `ui/theme/*.kt` | Compose 테마 |
| 간격·크기 | 컴포저블 안에 `dp` 직접 | 화면별로 다름 |
| 기기 구성별 대체 값 | `res/values-*` | 시스템 자동 선택 |

**"Compose 앱이니 xml은 안 쓴다"는 틀렸다.** `strings.xml`과 `themes.xml`은 어차피 필요하다.

### 문자열 하드코딩을 피한다

```kotlin
AppBarView(title = "Wish List")              // 번역 불가
AppBarView(title = stringResource(R.string.app_title))   // 번역 가능
```

컴포저블이 아닌 곳에서 문자열이 필요하면 리소스 ID를 넘긴다.

```kotlin
// ViewModel 은 Context 를 갖지 않는다
data class UiState(val errorResId: Int? = null)

// 화면에서 해석한다
state.errorResId?.let { Text(stringResource(it)) }
```

이렇게 하면 `ViewModel`이 `Context`를 들지 않아도 된다.

### `themes.xml`을 방치하지 않는다

```xml
<style name="Theme.Chapter205" parent="android:Theme.Material.Light.NoActionBar" />
```

`chapter205`의 현재 상태다. **플랫폼 기본 테마를 그대로 쓰고 있어서**, 앱이 뜨기 전 잠깐 보이는 배경색이 Compose 테마와 어긋날 수 있다. 실제 앱에서는 스플래시 배경을 맞춰 주는 편이 좋다.

### 리소스 이름 규칙

```
✅  add_wish, app_bar_color, ic_launcher_foreground
❌  addWish, AppBarColor, icon1
```

**소문자와 밑줄만 쓴다.** 대문자나 하이픈을 넣으면 빌드가 실패한다. 접두사로 묶으면 찾기 쉽다(`ic_`, `bg_`, `error_`).

### 사용하지 않는 리소스는 지운다

`colors.xml`의 `purple_200`, `teal_700` 등은 프로젝트 템플릿이 만들어 둔 것으로 지금 쓰이지 않는다. 읽는 사람이 "이 색 체계를 따라야 하나" 오해하게 만든다. Android Studio의 **Refactor > Remove Unused Resources**로 정리할 수 있다.

## 체크리스트

- [ ] `R` 클래스가 빌드 시 자동 생성된다는 것을 안다.
- [ ] `R.color.white`가 색이 아니라 정수 ID라는 것을 설명할 수 있다.
- [ ] 리소스 ID와 실제 값이 결정되는 시점이 다르다는 것을 안다.
- [ ] `res/values` 안의 파일이 이름이 아니라 태그로 구분된다는 것을 안다.
- [ ] `drawable`과 `mipmap`의 차이를 안다.
- [ ] 구성 한정자(`values-ko`, `values-night`)가 어떻게 동작하는지 설명할 수 있다.
- [ ] 리소스를 코드에서 분리하는 진짜 이유를 말할 수 있다.
- [ ] `res/values` xml과 Compose 테마의 역할 차이를 설명할 수 있다.
- [ ] "문자열은 xml, 색은 Compose 테마"라는 기준을 적용할 수 있다.
- [ ] `colorResource` 대신 `MaterialTheme.colorScheme`을 쓰면 무엇이 좋아지는지 안다.
- [ ] `ViewModel`에서 문자열이 필요할 때 리소스 ID를 넘기는 방법을 안다.
- [ ] 리소스 이름 규칙을 안다.

## 공식 참고 자료

- [Android Developers: App resources overview](https://developer.android.com/guide/topics/resources/providing-resources)
- [Android Developers: String resources](https://developer.android.com/guide/topics/resources/string-resource)
- [Android Developers: Resources in Compose](https://developer.android.com/develop/ui/compose/resources)
- [Android Developers: Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Android Developers: Theming in Compose](https://developer.android.com/develop/ui/compose/designsystems)
- [Android Developers: Support different languages and cultures](https://developer.android.com/guide/topics/resources/localization)
- [Android Developers: Dark theme](https://developer.android.com/develop/ui/views/theming/darktheme)
