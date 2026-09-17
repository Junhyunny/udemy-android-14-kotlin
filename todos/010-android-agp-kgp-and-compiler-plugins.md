# AGP, KGP, 컴파일러 플러그인 — `@Parcelize`가 안 되던 진짜 이유

## 이 문서가 나온 배경

`chapter143`에서 이 두 줄이 해결되지 않았다.

```kotlin
import kotlinx.parcelize.Parcelize   // Unresolved reference 'parcelize'
@Parcelize                            // Unresolved reference 'Parcelize'
```

플러그인은 분명히 적어 두었는데도 안 됐다.

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")            // ← 적혀 있다. 에러도 안 난다. 그런데 동작을 안 한다
}
```

이 문제를 이해하려면 **Gradle, AGP, KGP, 컴파일러 플러그인**이 각각 무엇이고 어떤 순서로 맞물리는지 알아야 한다. 빌드 파일들의 역할은 [`007-gradle-version-catalog-and-build-files.md`](007-gradle-version-catalog-and-build-files.md)에, 문제를 찾아간 과정은 [`012-gradle-build-problem-debugging.md`](012-gradle-build-problem-debugging.md)에 정리했다.

## 등장인물

### 계층 구조

```
Gradle                     범용 빌드 도구. 안드로이드도 코틀린도 모른다
  └─ AGP                   안드로이드를 아는 Gradle 플러그인
       │                   (com.android.application)
       └─ KGP              코틀린을 아는 Gradle 플러그인
            │              (org.jetbrains.kotlin.*)
            └─ 컴파일러 플러그인    코틀린 컴파일러 안에 꽂혀 코드를 바꾸거나 만들어 내는 것
                            (parcelize, compose, serialization)
```

각각을 한 문장으로.

| 이름 | 정식 명칭 | 하는 일 |
| --- | --- | --- |
| **Gradle** | Gradle | 어떤 작업을 어떤 순서로 실행할지 관리하는 범용 빌드 도구 |
| **AGP** | Android Gradle Plugin | Gradle에게 "안드로이드 앱 만드는 법"을 알려 준다. `android { }` DSL, APK 패키징, 리소스 처리 |
| **KGP** | Kotlin Gradle Plugin | Gradle에게 "코틀린 컴파일하는 법"을 알려 준다 |
| **컴파일러 플러그인** | Kotlin compiler plugin | 컴파일 **도중에** 끼어들어 코드를 생성/변형한다 |

### 버전 번호가 뜻하는 것

`chapter143`의 실제 값이다.

```toml
agp = "9.4.0"        # Android Gradle Plugin 버전 — 안드로이드 빌드 기능
kotlin = "2.4.20"    # Kotlin(=KGP) 버전 — 코틀린 언어와 컴파일러
```

여기에 두 개가 더 있다. 헷갈리기 쉬우니 구분해 두자.

| 버전 | 무엇의 버전인가 | 어디에 적나 |
| --- | --- | --- |
| Gradle | 빌드 도구 자체 | `gradle/wrapper/gradle-wrapper.properties` |
| AGP | 안드로이드 플러그인 | `libs.versions.toml`의 `agp` |
| Kotlin / KGP | 코틀린 컴파일러 | `libs.versions.toml`의 `kotlin` |
| compileSdk / targetSdk | 안드로이드 **API 레벨** | 모듈 `build.gradle.kts`의 `android { }` |

**`compileSdk = 37`과 `agp = "9.4.0"`은 전혀 다른 것이다.** 하나는 "어떤 안드로이드 API로 컴파일할지", 다른 하나는 "빌드 도구 버전"이다.

## 컴파일러 플러그인이란

보통의 라이브러리와 근본적으로 다르다.

```
일반 라이브러리 (예: Retrofit)
    이미 만들어진 코드를 가져다 쓴다.
    의존성 한 줄만 추가하면 끝.

컴파일러 플러그인 (예: parcelize)
    컴파일하는 순간에 끼어들어 내가 쓰지 않은 코드를 만들어 넣는다.
    Gradle 플러그인을 적용해야 활성화된다.
```

`@Parcelize`가 실제로 하는 일을 보자. 우리가 쓴 코드는 이게 전부다.

```kotlin
@Parcelize
data class Category(
    val idCategory: String,
    val strCategory: String,
    val strCategoryThumb: String,
    val strCategoryDescription: String
) : Parcelable
```

컴파일 후 바이트코드를 열어 보면 쓰지 않은 것들이 들어 있다.

```
$ javap -p com/example/chapter_143/Category.class

public static final android.os.Parcelable$Creator<...Category> CREATOR;
public final int describeContents();
public final void writeToParcel(android.os.Parcel, int);
```

**손으로 쓰면 30줄쯤 되는 코드를 컴파일러 플러그인이 대신 만들어 넣는다.** 이것이 컴파일러 플러그인의 정체다. 같은 원리로 동작하는 것들이 더 있다.

| 컴파일러 플러그인 | 생성하는 것 |
| --- | --- |
| `parcelize` | `writeToParcel`, `describeContents`, `CREATOR` |
| `compose` | 재구성 추적 코드, `$composer` 파라미터 |
| `kotlinx-serialization` | `serializer()`, 직렬화/역직렬화 로직 |

컴파일러 플러그인은 **거의 항상 두 조각이 짝**이다.

```
① 컴파일러 쪽 : 코드를 생성한다                   ← Gradle 플러그인이 활성화
② 런타임 쪽   : @Parcelize 같은 애노테이션의 정의  ← 의존성으로 클래스패스에 들어감
                (kotlin-parcelize-runtime.jar)
```

**보통은 Gradle 플러그인을 적용하면 ②도 자동으로 따라 들어온다.** 그래서 공식 문서도 플러그인 한 줄만 안내한다.

> ```kotlin
> plugins {
>     id("kotlin-parcelize")
> }
> ```

이번 문제는 **그 자동 연결이 끊어진 사건**이다.

## 사건의 전말 — AGP 9의 built-in Kotlin

### 예전 방식 (AGP 8까지)

```kotlin
plugins {
    id("com.android.application")        // AGP
    id("org.jetbrains.kotlin.android")   // KGP ← 직접 적용해야 했다
    id("kotlin-parcelize")               // parcelize는 KGP에 올라탄다
}
```

`kotlin-parcelize` 플러그인은 **`org.jetbrains.kotlin.android`가 적용되어 있다는 것을 전제로** 만들어졌다. 그 플러그인이 만든 코틀린 컴파일 작업을 찾아가서 컴파일러 플러그인을 꽂고 런타임 의존성을 추가한다.

### 지금 방식 (AGP 9)

> "Android Gradle plugin 9.0 introduces built-in Kotlin support and enables it by default, meaning you no longer have to apply the `org.jetbrains.kotlin.android` plugin in your build files to compile Kotlin source files."

AGP 9부터 **`org.jetbrains.kotlin.android`를 적용하지 않는다.** AGP가 코틀린 컴파일을 직접 맡는다. `chapter143`의 `plugins` 블록에 코틀린 안드로이드 플러그인이 없는데도 `.kt` 파일이 컴파일되던 이유가 이것이다.

### 그래서 무슨 일이 벌어졌나

```
kotlin-parcelize 플러그인이 적용된다
    ↓
"org.jetbrains.kotlin.android 가 적용되면 그때 일을 하겠다" 하고 기다린다
    ↓
AGP 9 built-in Kotlin 이므로 그 플러그인은 영원히 적용되지 않는다
    ↓
parcelize 플러그인은 아무 일도 하지 않고 끝난다  ← 에러도 경고도 없다
    ↓
① 런타임 의존성이 추가되지 않음   → Unresolved reference 'parcelize'
② 컴파일러 플러그인이 안 꽂힘      → Class 'Category' is not abstract and
                                    does not implement abstract members
```

**에러가 두 개인 것이 단서였다.** 하나는 "애노테이션을 못 찾겠다"(런타임 조각 없음), 다른 하나는 "구현이 없다"(컴파일러 조각 안 돎). 두 조각이 **동시에** 빠졌다는 뜻이고, 그건 플러그인 자체가 통째로 no-op이라는 신호다.

### 왜 "플러그인을 찾을 수 없다" 에러가 안 났을까

```kotlin
id("kotlin-parcelize")   // 버전 없이 요청
```

버전 없는 요청은 "이미 클래스패스에 있는 플러그인을 달라"는 뜻이다. 그리고 **AGP 9는 KGP 2.2.10을 딸려 온다.**

> "AGP 9.0 has a runtime dependency on Kotlin Gradle Plugin (KGP) 2.2.10."

그래서 parcelize 플러그인은 클래스패스에 **있었고**, 요청은 성공했고, 적용도 되었다. 다만 **일을 하지 않았을 뿐이다.** 실패했다고 알려 주지 않는 이 조용함이 이 문제를 어렵게 만든 지점이다.

## 해결책과 버려진 선택지

### 시도 ① built-in Kotlin을 끄고 옛날 방식으로 — 실패

```properties
# gradle.properties
android.builtInKotlin=false
```

```kotlin
plugins {
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
}
```

결과는 이랬다.

```
Failed to apply plugin 'org.jetbrains.kotlin.android'.
> class com.android.build.gradle.internal.dsl.ApplicationExtensionImpl$AgpDecorated_Decorated
  cannot be cast to class com.android.build.gradle.BaseExtension
```

AGP 9 릴리스 노트에 이유가 있다.

> "The `android` DSL now uses new public interfaces exclusively (removing old `BaseExtension` implementations)."

**KGP 2.2.10이 AGP 9에서 없어진 옛 클래스(`BaseExtension`)에 캐스팅한다.** 옵트아웃해도 KGP 2.2.10 자체가 AGP 9.4.0과 호환되지 않으므로 이 길은 막혀 있다.

### 시도 ② KGP를 올린다 — 성공

```toml
[versions]
kotlin = "2.4.20"    # 2.2.10 → 2.4.20
```

```kotlin
// 루트 build.gradle.kts
alias(libs.plugins.kotlin.parcelize) apply false

// app/build.gradle.kts
alias(libs.plugins.kotlin.parcelize)
```

빌드가 통과하고 `javap`으로 생성된 멤버까지 확인됐다. **KGP 2.4.20의 parcelize 플러그인은 AGP built-in Kotlin을 지원한다.**

핵심을 한 줄로 줄이면 이렇다.

> **AGP가 코틀린 컴파일 방식을 바꿨는데, AGP가 딸려 오는 KGP 버전이 그 변화에 맞춰지지 않아서 컴파일러 플러그인이 끼어들 자리를 찾지 못했다. KGP를 올리면 해결된다.**

### `buildscript` 방식을 쓰지 않은 이유

릴리스 노트가 안내하는 방법도 있다.

```kotlin
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}
```

이것도 동작한다. 다만 KGP가 **버전 없이** 클래스패스에 올라가기 때문에, 그다음부터는 `alias()`를 쓸 수 없다.

```
The request for this plugin could not be satisfied because the plugin is
already on the classpath with an unknown version, so compatibility cannot be checked.
```

모든 코틀린 플러그인을 `id("...")`로 바꿔야 하고 버전 카탈로그의 이점이 사라진다. 그래서 **루트 `plugins` 블록 + `apply false`**라는 표준 방식을 택했다.

## 버전을 맞출 때의 원칙

### 코틀린 관련은 한 버전으로 묶는다

```toml
[versions]
kotlin = "2.4.20"

[plugins]
kotlin-compose   = { id = "org.jetbrains.kotlin.plugin.compose",   version.ref = "kotlin" }
kotlin-parcelize = { id = "org.jetbrains.kotlin.plugin.parcelize", version.ref = "kotlin" }
```

컴파일러 플러그인은 **컴파일러 내부 구조에 직접 의존**한다. 컴파일러와 플러그인의 버전이 어긋나면 알 수 없는 오류가 난다. `version.ref = "kotlin"`으로 묶어 두면 한 줄만 바꿔도 전부 따라온다.

컴포즈 컴파일러가 코틀린 2.0부터 KGP에 합쳐진 것도 같은 이유다. 예전에는 `composeOptions { kotlinCompilerExtensionVersion = ... }`으로 따로 맞춰야 했고, 코틀린을 올릴 때마다 호환표를 찾아봐야 했다.

### 업그레이드 순서

```
1. Gradle          (gradle-wrapper.properties)
2. AGP             (AGP는 최소 Gradle 버전을 요구한다)
3. Kotlin / KGP    (AGP와 호환되는 범위에서)
4. 라이브러리
```

아래에서 위로 올리면 중간에 막힌다. 이번 경우도 "parcelize가 안 된다"에서 출발했지만 실제 해법은 3번이었다.

### 조용한 실패를 의심하는 습관

이번 문제의 교훈은 **"에러가 없다 ≠ 동작한다"**이다.

| 증상 | 의심할 것 |
| --- | --- |
| 플러그인을 적었는데 애노테이션을 못 찾음 | 플러그인이 런타임 의존성을 안 넣었다 |
| 애노테이션은 찾는데 생성 코드가 없음 | 컴파일러 플러그인이 안 돌았다 |
| 둘 다 | 플러그인 전체가 no-op |
| 버전을 올렸는데 안 바뀜 | 다른 곳에서 버전을 덮어쓰고 있다 |

확인 방법은 [`012-gradle-build-problem-debugging.md`](012-gradle-build-problem-debugging.md)에 정리했다.

## 체크리스트

- [ ] Gradle, AGP, KGP, 컴파일러 플러그인의 계층 관계를 설명할 수 있다.
- [ ] AGP 버전과 `compileSdk`가 다른 것임을 설명할 수 있다.
- [ ] 컴파일러 플러그인이 일반 라이브러리와 어떻게 다른지 설명할 수 있다.
- [ ] `@Parcelize`가 어떤 멤버를 생성하는지 말할 수 있다.
- [ ] 컴파일러 플러그인이 컴파일러 조각과 런타임 조각으로 나뉜다는 것을 안다.
- [ ] AGP 9의 built-in Kotlin이 무엇을 바꿨는지 설명할 수 있다.
- [ ] 에러가 두 개였던 것이 왜 단서가 되는지 설명할 수 있다.
- [ ] `id("...")`를 버전 없이 쓸 때의 의미를 안다.
- [ ] KGP 2.2.10이 AGP 9와 호환되지 않는 이유를 안다.
- [ ] 코틀린 관련 플러그인 버전을 한 `version.ref`로 묶는 이유를 설명할 수 있다.
- [ ] Gradle → AGP → KGP → 라이브러리 순서로 올려야 하는 이유를 안다.

## 공식 참고 자료

- [Android Developers: Android Gradle plugin 9.0.1 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Android Developers: Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Android Developers: Android Gradle plugin and Gradle version compatibility](https://developer.android.com/build/releases/gradle-plugin)
- [Android Developers: Parcelable implementation generator (`kotlin-parcelize`)](https://developer.android.com/kotlin/parcelize)
- [Android Developers: Configure your build](https://developer.android.com/build)
- [Kotlin Docs: Compose compiler Gradle plugin](https://kotlinlang.org/docs/compose-compiler-migration-guide.html)
- [Kotlin Docs: Gradle](https://kotlinlang.org/docs/gradle.html)
