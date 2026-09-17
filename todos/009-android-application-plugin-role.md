# `com.android.application` 플러그인의 역할

## 질문이 나온 코드

- [`chapter036/app/build.gradle.kts`](../chapter036/app/build.gradle.kts)
- 질문: `plugins` 블록에 선언한 `alias(libs.plugins.android.application)`은 어떤 역할을 하는가? 이 플러그인 때문에 코틀린 파일의 `main` 함수가 실행되지 않은 것인가?

## 질문 전제 점검

- **"이 플러그인 때문에 코틀린 파일의 `main` 메서드가 실행되지 않은 것인가?"** → 인과 관계가 반대에 가깝다. 플러그인이 `main` 실행을 막은 것이 아니라, 이 모듈을 **안드로이드 앱으로 빌드하도록 만든 결과** JVM 애플리케이션 실행 모델이 애초에 존재하지 않게 된 것이다. 플러그인을 걷어낸다고 `main`이 실행되는 것도 아니다. 그때는 `android { }` 블록을 해석하지 못해 빌드 자체가 깨진다.
- **"아래 플러그인 역할은 뭐야?"** → 방향이 정확한 질문이다. 다만 "플러그인이 기능을 켠다"보다 **"플러그인이 빌드 태스크를 등록하고 DSL을 제공한다"**로 이해하면 이후 내용이 잘 연결된다.

## 공부할 내용

Gradle 자체는 빌드 방법을 모른다. "플러그인은 태스크와 그 구성을 정의"하고, "빌드 파일에 플러그인을 적용하면 그 플러그인의 태스크가 등록되며 입력과 출력으로 서로 연결"된다. 즉 `plugins` 블록은 이 모듈을 어떤 종류의 프로젝트로 빌드할지 결정하는 선언이다.

`com.android.application`은 안드로이드 Gradle 플러그인(AGP)의 애플리케이션 변형이다. 이 플러그인을 적용하면 "APK 또는 안드로이드 라이브러리를 빌드하는 데 필요한 모든 태스크가 등록"된다. 구체적으로 다음을 제공한다.

- `android { ... }` DSL 블록. `namespace`, `compileSdk`, `defaultConfig`, `buildTypes` 같은 안드로이드 전용 설정은 이 플러그인이 적용되어야 인식된다.
- 리소스 처리, 매니페스트 병합, 코드 컴파일, DEX 변환, 패키징, 서명으로 이어지는 빌드 태스크.
- 빌드 타입과 제품 플레이버를 조합한 빌드 변형(variant) 처리.
- 최종 산출물로 APK와 AAB 생성.

버전 카탈로그를 쓰는 프로젝트에서는 플러그인 ID와 버전을 `gradle/libs.versions.toml`의 `[plugins]`에 정의하고, 빌드 파일에서는 `alias(libs.plugins.android.application)`으로 별칭을 참조한다. `chapter036`에서는 `android-application = { id = "com.android.application", version.ref = "agp" }`가 그 정의다.

`com.android.library`는 같은 AGP의 라이브러리 변형으로, APK 대신 AAR을 만든다. `kotlin("jvm")`은 안드로이드와 무관하게 JVM을 타깃으로 하는 코틀린 프로젝트를 빌드하는 플러그인이다. 하나의 모듈이 어떤 플러그인을 적용했는지가 그 모듈의 빌드 방식과 산출물을 결정한다.

`main` 함수가 실행되지 않은 것과의 관계는 간접적이다. 플러그인이 `main` 함수 실행을 막는 것이 아니라, 이 플러그인을 적용한 모듈은 JVM 애플리케이션이 아니라 안드로이드 앱으로 빌드되기 때문에 `main` 함수를 진입점으로 삼는 실행 방식 자체가 성립하지 않는다. 자세한 내용은 [`011-android-module-main-function-run.md`](011-android-module-main-function-run.md)에 정리한다.

## 관련 아키텍처와 베스트 프랙티스

### 플러그인은 빌드 로직의 재사용 단위다

Gradle 코어는 태스크를 실행하는 엔진일 뿐 자바도 안드로이드도 모른다. "무엇을 어떻게 빌드하는가"는 전부 플러그인이 채운다. 그래서 빌드 파일을 읽을 때 **`plugins` 블록이 그 파일의 나머지를 해석하는 문법을 결정한다**고 보면 된다. `android { }`를 쓸 수 있는 이유도, `dependencies`에 `implementation` 구성이 존재하는 이유도 플러그인이 만들어 준 것이다.

### 모듈 타입 선택 기준

| 플러그인 | 모듈 성격 | 산출물 |
| --- | --- | --- |
| `com.android.application` | 설치 가능한 앱. 보통 프로젝트에 하나 | APK, AAB |
| `com.android.library` | 안드로이드 리소스와 매니페스트를 갖는 라이브러리 | AAR |
| `kotlin("jvm")` | 안드로이드에 의존하지 않는 순수 로직 | JAR |

권장되는 구성은 `:app`을 얇게 유지하고(조립과 진입점 담당) 기능과 로직을 라이브러리 모듈로 미는 것이다. 안드로이드 API가 필요 없는 로직을 `kotlin("jvm")` 모듈에 두면 JVM 단위 테스트로 빠르게 검증할 수 있고, 에뮬레이터가 필요 없다.

### 버전 정합성

AGP, Gradle, 코틀린, JDK는 서로 호환 범위가 정해져 있다. 하나만 올리면 빌드가 깨지기 쉬우므로 공식 호환성 표를 기준으로 함께 움직인다. 버전 카탈로그로 버전을 한곳에 모으면 이 작업이 쉬워진다.

### `apply false`의 의미

루트 빌드 파일의 `alias(...) apply false`는 "플러그인을 이 빌드에서 사용할 수 있도록 해석은 하되, 루트 프로젝트에는 적용하지 말라"는 뜻이다. 루트 프로젝트는 실제 코드가 없는 조립 지점이므로 안드로이드 앱으로 빌드될 이유가 없다. 버전 선언과 적용을 분리하는 패턴이다.

## 체크리스트

- [ ] Gradle에서 플러그인이 하는 일을 태스크 관점에서 설명할 수 있다.
- [ ] `com.android.application`을 적용해야 `android { }` 블록을 쓸 수 있는 이유를 설명할 수 있다.
- [ ] `com.android.application`과 `com.android.library`의 산출물 차이를 설명할 수 있다.
- [ ] `alias(libs.plugins.android.application)`이 어떤 값을 참조하는지 추적할 수 있다.
- [ ] AGP 적용과 `main` 함수 실행 불가가 인과적으로 어떻게 연결되는지 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Android Gradle plugin overview](https://developer.android.com/build/gradle-build-overview)
- [Android Developers: Configure your build](https://developer.android.com/build)
- [Android Developers: Migrate your build to version catalogs](https://developer.android.com/build/migrate-to-catalogs)
- [Gradle: Using plugins](https://docs.gradle.org/current/userguide/plugins.html)
- [Android Developers: Android Gradle plugin release notes (호환성)](https://developer.android.com/build/releases/gradle-plugin)
- [Android Developers: Guide to Android app modularization](https://developer.android.com/topic/modularization)
- [Gradle: Developing custom Gradle plugins](https://docs.gradle.org/current/userguide/custom_plugins.html)
