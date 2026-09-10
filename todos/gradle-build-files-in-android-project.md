# 안드로이드 프로젝트의 `build.gradle.kts` 파일 구성

## 질문이 나온 코드

- [`chapter036/app/build.gradle.kts`](../chapter036/app/build.gradle.kts)
- 질문: `build.gradle.kts` 파일이 여러 개인데 각각 어디에 사용하고 무엇이 다른가? 모듈에 선언된 `build.gradle.kts`에는 어떤 내용이 들어 있는가?

## 질문 전제 점검

- **"`build.gradle.kts` 파일이 두 개야"** → 실제로는 세 개다. 루트에 하나, `app` 모듈에 하나, `lib` 모듈에 하나다. 여기에 성격이 다른 `settings.gradle.kts`까지 포함하면 빌드 설정 파일은 네 개다. "두 개"로 보였다면 루트 파일을 지나쳤을 가능성이 크다.
- **"어디에 사용하는 거고 무엇이 다른지"**라는 질문의 방향은 정확하다. 같은 파일명이지만 **위치가 곧 적용 범위**라는 것이 핵심이다.
- 자주 하는 오해 하나를 덧붙이면, 파일 개수는 모듈 수에 따라 늘어난다. 모듈이 열 개면 `build.gradle.kts`도 열한 개(루트 포함)가 된다.

## 공부할 내용

Gradle 빌드는 여러 설정 파일로 나뉘어 있고, 파일마다 적용 범위가 다르다. 같은 이름의 `build.gradle.kts`라도 루트 디렉터리에 있는 파일과 모듈 디렉터리에 있는 파일의 역할이 다르다.

### 파일별 역할

| 파일 | 위치 | 역할 |
| --- | --- | --- |
| `settings.gradle.kts` | 루트 | 빌드에 포함할 모듈과 프로젝트 수준 저장소를 정의한다. |
| `build.gradle.kts` | 루트 | 모듈들이 공통으로 사용하는 플러그인과 버전을 선언한다. |
| `build.gradle.kts` | 각 모듈 | 그 모듈에만 적용되는 빌드 설정을 구성한다. |
| `gradle.properties` | 루트 | Gradle 데몬 힙 크기 등 프로젝트 전역 Gradle 설정을 둔다. |
| `local.properties` | 루트 | SDK 경로처럼 로컬 환경에 종속된 값을 둔다. 저장소에 올리지 않는다. |
| `gradle/libs.versions.toml` | 루트 | 버전 카탈로그로 의존성과 플러그인 버전을 한곳에서 관리한다. |

`settings.gradle.kts`는 "프로젝트 수준 저장소 설정을 정의하고 Gradle에 어떤 모듈을 빌드에 포함할지 알려주는" 파일이다. `chapter036`에서는 `rootProject.name`, `include(":app")`, `include(":lib")`가 여기에 선언되어 있고, 이 선언이 있어야 두 모듈이 하나의 빌드로 묶인다.

루트 `build.gradle.kts`는 "모듈들이 사용하는 플러그인의 공통 버전을 정의"한다. 이때 `apply false`를 붙이면 플러그인을 클래스패스에 올려 버전만 고정하고 루트 프로젝트에는 적용하지 않는다. 실제 적용은 각 모듈이 담당한다.

모듈 `build.gradle.kts`는 "그 파일이 위치한 모듈의 빌드 설정을 구성"한다. 따라서 모듈이 늘어나면 `build.gradle.kts`도 함께 늘어난다. `chapter036`에는 루트, `app`, `lib`까지 세 개가 있다.

### `chapter036/app/build.gradle.kts`에 담긴 내용

- `plugins`: 이 모듈에 적용할 플러그인. `com.android.application`을 적용해 안드로이드 앱 모듈로 만든다. 자세한 내용은 [`android-application-plugin-role.md`](android-application-plugin-role.md)를 참고한다.
- `android.namespace`: 모듈의 R 클래스와 생성 코드가 놓일 Kotlin/Java 패키지.
- `android.compileSdk`: 소스를 컴파일할 때 사용할 수 있는 안드로이드와 자바 API 버전을 결정한다.
- `android.defaultConfig.applicationId`: 기기와 스토어에서 앱을 식별하는 고유 ID.
- `android.defaultConfig.minSdk`: 앱이 지원하는 가장 낮은 안드로이드 버전.
- `android.defaultConfig.targetSdk`: 앱의 런타임 동작을 결정하고 어떤 버전까지 테스트했는지 표시한다.
- `android.defaultConfig.versionCode`, `versionName`: 내부 버전 번호와 사용자에게 보이는 버전 문자열.
- `android.defaultConfig.testInstrumentationRunner`: 계측 테스트를 실행할 러너.
- `android.buildTypes`: `debug`, `release` 같은 빌드 타입별 설정.
- `android.compileOptions`: 자바 소스와 타깃 호환 버전.
- `dependencies`: 이 모듈이 사용하는 라이브러리. 버전 카탈로그의 `libs.*` 별칭으로 참조한다.

`lib` 모듈의 `build.gradle.kts`는 `kotlin("jvm")` 플러그인을 적용해 안드로이드가 아닌 일반 JVM 모듈로 구성되어 있다. 같은 프로젝트 안이라도 모듈마다 적용 플러그인과 설정이 다를 수 있다는 점을 보여준다.

## 관련 아키텍처와 베스트 프랙티스

### 위치가 곧 스코프다

이 계층을 한 문장으로 요약하면 이렇다.

```
settings.gradle.kts   어떤 모듈이 이 빌드에 참여하는가        (빌드의 경계)
build.gradle.kts      모든 모듈이 공유할 플러그인과 버전      (루트)
app/build.gradle.kts  이 모듈은 무엇이고 무엇에 의존하는가    (모듈)
gradle/libs.versions.toml  버전의 단일 출처                  (카탈로그)
```

Gradle 설정을 읽을 때는 항상 "이 파일이 적용되는 범위가 어디까지인가"를 먼저 묻는 습관이 도움이 된다.

### 버전은 한곳에서만 관리한다

같은 라이브러리 버전이 여러 모듈 빌드 파일에 흩어지면 모듈마다 버전이 어긋나기 시작한다. 버전 카탈로그(`libs.versions.toml`)는 이를 막는 단일 출처다. 공식 문서도 "여러 모듈이 공통 의존성을 갖는다면 버전 카탈로그나 BOM 사용을 권장한다"고 안내한다.

### 공통 설정은 컨벤션 플러그인으로

모듈이 늘어나면 `compileSdk`, `minSdk`, 자바 버전 같은 설정이 모든 모듈 빌드 파일에 복제된다. 이를 해결하는 표준 패턴이 **컨벤션 플러그인**이다. `build-logic` 같은 별도 빌드에 플러그인을 정의하고 각 모듈은 그 플러그인을 적용하기만 한다.

과거에 쓰이던 루트 `build.gradle`의 `allprojects { }`, `subprojects { }` 블록은 현재 권장되지 않는다. 모듈 간 결합을 만들고 구성 캐시와 병렬 빌드에 불리하기 때문이다.

### 모듈 분리 기준

모듈은 폴더 정리가 아니라 **의존성 방향과 빌드 단위**를 나누는 도구다. 공식 모듈화 가이드는 응집도는 높이고 결합도는 낮추는 방향, 그리고 순환 의존을 만들지 않는 방향을 권한다. `chapter036`이 순수 코틀린 로직을 `lib` 모듈로 분리한 것도 같은 맥락으로 볼 수 있다.

### 저장소에 들어가면 안 되는 것

`local.properties`는 SDK 경로처럼 개발자 기기마다 다른 값을 담고, 문서도 "안드로이드 Gradle 플러그인 전용이므로 임의의 값을 넣지 말라"고 명시한다. 서명 키스토어와 API 키도 마찬가지다. 이런 값은 `.gitignore`로 제외하고 환경 변수나 CI 시크릿으로 주입한다.

## 체크리스트

- [ ] `settings.gradle.kts`가 없으면 모듈이 빌드에 포함되지 않는 이유를 설명할 수 있다.
- [ ] 루트 `build.gradle.kts`와 모듈 `build.gradle.kts`의 적용 범위 차이를 설명할 수 있다.
- [ ] 루트 플러그인 선언에 붙는 `apply false`의 의미를 설명할 수 있다.
- [ ] `compileSdk`, `minSdk`, `targetSdk`의 차이를 구분할 수 있다.
- [ ] `namespace`와 `applicationId`의 차이를 설명할 수 있다.
- [ ] 버전 카탈로그(`libs.versions.toml`)를 쓰는 이유를 설명할 수 있다.
- [ ] `local.properties`를 저장소에 올리면 안 되는 이유를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Configure your build](https://developer.android.com/build)
- [Android Developers: Android build structure](https://developer.android.com/build#build-files)
- [Android Developers: Projects overview](https://developer.android.com/studio/projects)
- [Gradle: Structuring projects with Gradle](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [Android Developers: Guide to Android app modularization](https://developer.android.com/topic/modularization)
- [Gradle: Sharing build logic between subprojects](https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html)
- [Gradle: Version catalogs](https://docs.gradle.org/current/userguide/version_catalogs.html)
- [Android Developers: App security best practices](https://developer.android.com/privacy-and-security/security-best-practices)
