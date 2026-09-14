# 빌드 파일 4형제: `settings`, 루트 `build`, 모듈 `build`, `libs.versions.toml`

## 이 문서가 나온 배경

`chapter143`에서 `@Parcelize`를 쓰려는데 `kotlinx.parcelize`를 찾지 못하는 문제를 고치면서, **네 개의 빌드 파일을 모두 건드렸다.** 왜 한 기능을 켜는 데 파일 네 개가 필요한지 이해하려면 각 파일의 역할과 연결 고리를 알아야 한다.

- [`chapter143/settings.gradle.kts`](../chapter143/settings.gradle.kts)
- [`chapter143/build.gradle.kts`](../chapter143/build.gradle.kts) — 루트
- [`chapter143/app/build.gradle.kts`](../chapter143/app/build.gradle.kts) — 모듈
- [`chapter143/gradle/libs.versions.toml`](../chapter143/gradle/libs.versions.toml) — 버전 카탈로그

관련 개념은 [`android-agp-kgp-and-compiler-plugins.md`](android-agp-kgp-and-compiler-plugins.md)에, 문제를 찾아간 과정은 [`gradle-build-problem-debugging.md`](gradle-build-problem-debugging.md)에 따로 정리했다.

## 한눈에 보는 구조

```
chapter143/
├── settings.gradle.kts      ① 어떤 모듈이 있나 + 어디서 받아오나
├── build.gradle.kts         ② 이 프로젝트가 쓸 플러그인 "목록과 버전"  (선언만)
├── gradle/
│   └── libs.versions.toml   ③ 버전과 좌표를 모아 둔 표           (데이터)
└── app/
    └── build.gradle.kts     ④ 이 모듈을 "실제로" 어떻게 빌드하나  (적용)
```

역할을 한 문장씩으로 줄이면 이렇다.

| 파일 | 한 문장 | 비유 |
| --- | --- | --- |
| `settings.gradle.kts` | 프로젝트에 어떤 모듈이 있고, 라이브러리를 어느 저장소에서 받는가 | 건물 전체 도면 |
| 루트 `build.gradle.kts` | 이 프로젝트에서 쓸 플러그인과 그 버전을 **선언**한다 | 공용 자재 발주서 |
| `gradle/libs.versions.toml` | 버전 번호와 좌표를 한곳에 모은 **데이터 파일** | 자재 규격표 |
| 모듈 `app/build.gradle.kts` | 이 모듈에 플러그인을 **적용**하고 의존성·SDK를 설정한다 | 각 층 시공 지시서 |

**선언(루트)과 적용(모듈)이 분리되어 있다**는 점이 가장 헷갈리는 부분이다. 아래에서 다시 본다.

## 파일별로 자세히

### ① `settings.gradle.kts` — 프로젝트의 경계를 정한다

```kotlin
pluginManagement {
    repositories {
        google { /* com.android.*, com.google.*, androidx.* 만 여기서 찾는다 */ }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "chapter-143"
include(":app")           // 이 프로젝트에 :app 모듈이 있다
```

`google { content { includeGroupByRegex(...) } }`는 **그 저장소에서 찾을 그룹을 제한**하는 설정이다. 안드로이드 관련이 아닌 라이브러리를 구글 저장소에서 헛되이 찾지 않게 해서 빌드가 빨라진다.

`repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`는 **모듈 빌드 스크립트에서 개별적으로 저장소를 선언하면 실패시킨다**는 뜻이다. 저장소 설정을 이 파일 한곳으로 강제하는 장치다.

> "This settings file defines project-level repository settings and informs Gradle which modules it should include when building your app."

두 가지를 구분해서 봐야 한다.

- **`pluginManagement { repositories }`** — **플러그인**을 어디서 받을지
- **`dependencyResolutionManagement { repositories }`** — **라이브러리**를 어디서 받을지

둘은 별개다. 플러그인을 못 찾는 에러와 라이브러리를 못 찾는 에러의 원인이 다른 이유다.

그리고 **`gradle/libs.versions.toml`은 이름 규약만으로 자동 인식된다.** `settings.gradle.kts`에 아무것도 안 써도 Gradle이 그 경로를 보고 `libs`라는 이름의 카탈로그를 만들어 준다.

### ② 루트 `build.gradle.kts` — 선언만 하고 적용하지 않는다

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false   // ← 이번에 추가한 줄
}
```

> "It typically defines the common versions of plugins used by modules in your project."

**`apply false`가 핵심이다.** 뜻은 이렇다.

> "이 플러그인을 **클래스패스에 올려만 두고**, 루트 프로젝트 자체에는 **적용하지 마라**."

루트 프로젝트는 코드가 없는 껍데기라서 안드로이드 플러그인을 적용할 대상이 없다. 그런데도 여기에 적어 두는 이유는 **버전을 한곳에서 정하기 위해서**다. 모듈이 열 개여도 버전은 루트에서 한 번만 정하면 된다.

그래서 이런 분업이 생긴다.

```
루트:    "이 프로젝트는 parcelize 플러그인 2.4.20 버전을 쓴다"   (버전 결정)
        ↓ 클래스패스에 올라감
모듈:    "나는 그 plugin을 쓰겠다"                              (버전 생략)
```

### ③ `gradle/libs.versions.toml` — 버전 카탈로그

> "A version catalog is a selected list of dependencies that can be referenced in build scripts, simplifying dependency management."

섹션이 넷이다.

```toml
[versions]                          # 버전 번호에 이름을 붙인다
agp = "9.4.0"
kotlin = "2.4.20"
composeBom = "2026.02.01"

[libraries]                         # 라이브러리 좌표 (group:name:version)
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

[bundles]                           # 자주 같이 쓰는 것을 묶는다 (이 프로젝트는 미사용)
compose = ["androidx-compose-ui", "androidx-compose-material3"]

[plugins]                           # 플러그인 ID와 버전
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-parcelize = { id = "org.jetbrains.kotlin.plugin.parcelize", version.ref = "kotlin" }
```

#### `version.ref`가 하는 일

```toml
[versions]
kotlin = "2.4.20"                                    # 여기 한 곳만 바꾸면

[plugins]
kotlin-compose   = { id = "...", version.ref = "kotlin" }    # 둘 다 따라온다
kotlin-parcelize = { id = "...", version.ref = "kotlin" }
```

> "In case you want to reference a version declared in the `[versions]` section, use the `version.ref` property"

이번 수정에서 **`kotlin = "2.2.10"`을 `"2.4.20"`으로 바꾼 한 줄**이 compose 플러그인과 parcelize 플러그인 **양쪽에 동시에 적용**된 것이 바로 이 구조 덕분이다. 코틀린 관련 플러그인은 버전이 어긋나면 충돌하기 때문에, 한 `version.ref`로 묶는 것이 정석이다.

#### 대시가 점으로 바뀌는 규칙

가장 헷갈리는 지점이다.

> "Aliases consist of identifiers separated by a dash (`-`) or underscore (`_`). Type-safe accessors are generated for each alias, normalized to dot notation."

```
TOML의 이름                      빌드 스크립트에서 쓰는 이름
─────────────────────────────────────────────────────────
kotlin-parcelize            →   libs.plugins.kotlin.parcelize
androidx-navigation-compose →   libs.androidx.navigation.compose
androidx-compose-ui         →   libs.androidx.compose.ui
junit                       →   libs.junit
```

**대시(`-`)가 점(`.`)이 된다.** 그리고 `[libraries]`는 `libs.`로, `[plugins]`는 `libs.plugins.`로 시작한다. 이 규칙을 모르면 "TOML에는 분명히 썼는데 빌드 스크립트에서 못 찾는다"는 상황에 빠진다.

### ④ 모듈 `app/build.gradle.kts` — 실제로 적용한다

```kotlin
plugins {
    alias(libs.plugins.android.application)   // 버전 없음 — 루트에서 이미 정했다
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)      // ← 이번에 추가한 줄
}

android {
    namespace = "com.example.chapter_143"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 24; targetSdk = 37 }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.navigation.compose)
    implementation("com.squareup.retrofit2:retrofit:3.0.0")   // 카탈로그 밖 (개선 여지)
}
```

> "It lets you configure build settings for the specific module it is located in."

여기가 `android { }` 블록이 있는 유일한 곳이다. SDK 버전, 네임스페이스, 빌드 타입, 의존성이 전부 모듈 단위로 정해진다.

## 네 파일이 맞물리는 전체 경로

이번에 `parcelize`를 켜면서 실제로 일어난 연결이다.

```
gradle/libs.versions.toml
   [versions] kotlin = "2.4.20"
        │ version.ref
        ▼
   [plugins] kotlin-parcelize = { id = "org.jetbrains.kotlin.plugin.parcelize", version.ref = "kotlin" }
        │ 대시 → 점 변환으로 접근자 생성
        ▼
   libs.plugins.kotlin.parcelize
        │
        ├─▶ 루트 build.gradle.kts
        │      alias(libs.plugins.kotlin.parcelize) apply false
        │      → 플러그인 2.4.20 을 클래스패스에 올린다 (적용은 안 함)
        │
        └─▶ app/build.gradle.kts
               alias(libs.plugins.kotlin.parcelize)
               → :app 모듈에 실제로 적용한다
                 → 컴파일러 플러그인 활성화 + 런타임 의존성 자동 추가
                   → import kotlinx.parcelize.Parcelize 가 해결된다
```

**한 줄만 빠져도 끊긴다.** 카탈로그에 안 적으면 접근자가 안 생기고, 루트에 안 적으면 버전이 안 정해지고, 모듈에 안 적으면 적용되지 않는다.

## 흔히 하는 실수

### `alias()`와 `id()`를 섞어 쓰기

```kotlin
plugins {
    alias(libs.plugins.android.application)    // 카탈로그
    id("kotlin-parcelize")                     // 하드코딩 ← 원래 코드
}
```

원래 코드가 이 상태였다. `id("kotlin-parcelize")`는 **버전 없이** 플러그인을 요청하는데, 이러면 "이미 클래스패스에 있는 것"을 기대한다. 마침 AGP가 코틀린 플러그인을 딸려 오기 때문에 **에러 없이 적용까지 되었지만, 동작은 하지 않았다.** 조용히 실패하는 가장 나쁜 형태다.

판단 기준은 단순하다.

| 상황 | 쓰는 법 |
| --- | --- |
| 카탈로그에 있다 | `alias(libs.plugins.xxx)` |
| 버전을 여기서 정해야 한다 | `id("...") version "x.y.z"` |
| 이미 클래스패스에 있다(루트에서 선언됨) | `id("...")` — 버전 생략 |
| **클래스패스에 있는데 버전을 또 지정** | ❌ 에러 |

마지막 경우의 에러 메시지는 이렇게 나온다. 실제로 이번에 만났다.

```
The request for this plugin could not be satisfied because the plugin is
already on the classpath with an unknown version, so compatibility cannot be checked.
```

### 카탈로그를 반만 쓰기

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)                   // 카탈로그
    implementation("com.squareup.retrofit2:retrofit:3.0.0")  // 하드코딩
}
```

`chapter143`의 현재 상태다. 버전을 한곳에서 관리하려고 카탈로그를 두었는데 절반만 넣으면 그 이점이 사라진다. Retrofit 버전을 올리려면 여전히 파일을 뒤져야 한다.

```toml
[versions]
retrofit = "3.0.0"
coil = "2.4.0"

[libraries]
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
```

`retrofit`과 `converter-gson`처럼 **함께 버전을 맞춰야 하는 것들**을 하나의 `version.ref`로 묶는 것이 카탈로그의 진짜 값어치다.

### BOM과 버전을 같이 적기

```kotlin
implementation(platform(libs.androidx.compose.bom))   // BOM이 버전을 정한다
implementation(libs.androidx.compose.ui)              // 그래서 여기는 버전 없음 ✅
```

`[libraries]`의 compose 항목들에 `version.ref`가 없는 이유가 이것이다. BOM(Bill of Materials)이 compose 라이브러리들의 버전을 한꺼번에 맞춰 준다. 여기에 버전을 또 적으면 BOM을 무시하게 된다.

## 체크리스트

- [ ] 네 개 빌드 파일의 역할을 각각 한 문장으로 말할 수 있다.
- [ ] `settings.gradle.kts`의 `pluginManagement`와 `dependencyResolutionManagement`를 구분할 수 있다.
- [ ] 루트 `build.gradle.kts`의 `apply false`가 무엇을 뜻하는지 설명할 수 있다.
- [ ] 선언(루트)과 적용(모듈)이 분리된 이유를 설명할 수 있다.
- [ ] 카탈로그의 네 섹션을 말할 수 있다.
- [ ] `version.ref`로 여러 항목의 버전을 한곳에서 관리할 수 있다.
- [ ] TOML의 대시가 접근자에서 점으로 바뀌는 규칙을 안다.
- [ ] `alias()`와 `id()`를 언제 쓰는지 구분할 수 있다.
- [ ] "already on the classpath with an unknown version" 에러의 원인을 안다.
- [ ] BOM을 쓰는 라이브러리에 버전을 적지 않는 이유를 설명할 수 있다.

## 공식 참고 자료

- [Gradle Docs: Version catalogs](https://docs.gradle.org/current/userguide/version_catalogs.html)
- [Gradle Docs: Using plugins](https://docs.gradle.org/current/userguide/plugins.html)
- [Android Developers: Configure your build](https://developer.android.com/build)
- [Android Developers: Migrate your build to version catalogs](https://developer.android.com/build/migrate-to-catalogs)
- [Android Developers: Add build dependencies](https://developer.android.com/build/dependencies)
- [Gradle Docs: Sharing dependency versions between projects](https://docs.gradle.org/current/userguide/platforms.html)
