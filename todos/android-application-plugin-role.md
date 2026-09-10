# `com.android.application` 플러그인의 역할

## 질문이 나온 코드

- [`chapter036/app/build.gradle.kts`](../chapter036/app/build.gradle.kts)
- 질문: `plugins` 블록에 선언한 `alias(libs.plugins.android.application)`은 어떤 역할을 하는가? 이 플러그인 때문에 코틀린 파일의 `main` 함수가 실행되지 않은 것인가?

## 공부할 내용

Gradle 자체는 빌드 방법을 모른다. "플러그인은 태스크와 그 구성을 정의"하고, "빌드 파일에 플러그인을 적용하면 그 플러그인의 태스크가 등록되며 입력과 출력으로 서로 연결"된다. 즉 `plugins` 블록은 이 모듈을 어떤 종류의 프로젝트로 빌드할지 결정하는 선언이다.

`com.android.application`은 안드로이드 Gradle 플러그인(AGP)의 애플리케이션 변형이다. 이 플러그인을 적용하면 "APK 또는 안드로이드 라이브러리를 빌드하는 데 필요한 모든 태스크가 등록"된다. 구체적으로 다음을 제공한다.

- `android { ... }` DSL 블록. `namespace`, `compileSdk`, `defaultConfig`, `buildTypes` 같은 안드로이드 전용 설정은 이 플러그인이 적용되어야 인식된다.
- 리소스 처리, 매니페스트 병합, 코드 컴파일, DEX 변환, 패키징, 서명으로 이어지는 빌드 태스크.
- 빌드 타입과 제품 플레이버를 조합한 빌드 변형(variant) 처리.
- 최종 산출물로 APK와 AAB 생성.

버전 카탈로그를 쓰는 프로젝트에서는 플러그인 ID와 버전을 `gradle/libs.versions.toml`의 `[plugins]`에 정의하고, 빌드 파일에서는 `alias(libs.plugins.android.application)`으로 별칭을 참조한다. `chapter036`에서는 `android-application = { id = "com.android.application", version.ref = "agp" }`가 그 정의다.

`com.android.library`는 같은 AGP의 라이브러리 변형으로, APK 대신 AAR을 만든다. `kotlin("jvm")`은 안드로이드와 무관하게 JVM을 타깃으로 하는 코틀린 프로젝트를 빌드하는 플러그인이다. 하나의 모듈이 어떤 플러그인을 적용했는지가 그 모듈의 빌드 방식과 산출물을 결정한다.

`main` 함수가 실행되지 않은 것과의 관계는 간접적이다. 플러그인이 `main` 함수 실행을 막는 것이 아니라, 이 플러그인을 적용한 모듈은 JVM 애플리케이션이 아니라 안드로이드 앱으로 빌드되기 때문에 `main` 함수를 진입점으로 삼는 실행 방식 자체가 성립하지 않는다. 자세한 내용은 [`android-module-main-function-run.md`](android-module-main-function-run.md)에 정리한다.

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
