# 안드로이드 모듈에서 `main` 함수가 실행되지 않는 이유

## 질문이 나온 코드

- [`chapter036/lib/build.gradle.kts`](../chapter036/lib/build.gradle.kts)
- 질문: 빈 액티비티 프로젝트에 `main` 함수가 있는 코틀린 파일을 만들어 실행했더니 `SourceSet with name 'main' not found` 에러가 났다. 왜 실패했고, 안드로이드 스튜디오는 모듈의 `build.gradle.kts`를 기준으로 빌드하고 실행하는가?

## 공부할 내용

### 안드로이드 앱에는 `main` 함수가 없다

안드로이드 앱은 일반 자바 애플리케이션처럼 하나의 진입점에서 시작하지 않는다. 공식 문서는 "다른 대부분의 시스템의 앱과 달리 안드로이드 앱에는 단일 진입점이 없다. `main()` 함수가 없다"라고 설명한다.

대신 시스템은 액티비티, 서비스, 브로드캐스트 리시버, 콘텐츠 프로바이더라는 앱 구성요소를 진입점으로 사용한다. 실행 흐름은 다음과 같다.

1. 시스템이 `AndroidManifest.xml`에 선언된 구성요소를 확인한다.
2. 인텐트 등으로 특정 구성요소를 시작한다.
3. 앱 프로세스가 없으면 새로 만들고, 필요한 클래스를 인스턴스화한다.

그래서 안드로이드 앱 모듈에 `fun main()`을 작성해도 앱 실행 경로에서 호출되지 않는다.

### `SourceSet with name 'main' not found`가 발생한 이유

에러 메시지의 `SourceSet`은 Gradle 자바 플러그인이 만드는 소스셋을 가리킨다. 자바 플러그인은 "프로젝트의 프로덕션 소스 코드를 담는" `main`과 테스트용 `test` 소스셋을 만들고, `Application` 계열 실행 구성은 이 `main` 소스셋의 런타임 클래스패스를 사용한다.

안드로이드 앱 모듈은 `com.android.application` 플러그인으로 빌드되며, 이 플러그인은 자바 플러그인과 다른 방식으로 소스를 관리한다. 안드로이드에도 `src/main/`이라는 디렉터리와 `android.sourceSets`의 `main` 항목이 있지만, 이는 "모든 빌드 변형이 공유하는 기본 소스셋"으로 자바 플러그인의 `sourceSets` 컨테이너와는 별개다. 안드로이드 모듈의 컴파일 단위는 소스셋이 아니라 빌드 변형이며, 우선순위에 따라 여러 소스셋이 병합된다.

IDE가 `main` 함수 옆의 실행 버튼으로 만든 실행 구성은 JVM 애플리케이션 실행용 초기화 스크립트를 생성한다. 그 스크립트가 `:app` 프로젝트에서 자바 플러그인의 `main` 소스셋을 찾다가 없으므로 `SourceSet with name 'main' not found`로 태스크 생성에 실패한다. 즉 코드 문법 문제가 아니라, 그 모듈이 JVM 애플리케이션 실행 모델을 제공하지 않기 때문에 발생한 구성 단계 실패다.

### 별도 JVM 모듈을 만들면 성공하는 이유

`lib` 모듈은 `kotlin("jvm")` 플러그인을 적용한다. Kotlin JVM 플러그인은 JVM을 타깃으로 하는 코틀린 프로젝트를 빌드하며, `main`과 `test` 소스셋을 포함한 Gradle 표준 소스셋 구조를 사용한다. 안드로이드 스튜디오의 모듈 분류로 보면 "코틀린 또는 자바 소스 파일만 담고 빌드 결과로 JAR을 만드는" Java or Kotlin Library 모듈에 해당한다. 이 모듈에서는 `main` 소스셋이 존재하므로 IDE가 만든 JVM 실행 구성이 정상적으로 태스크를 만들고 `main` 함수를 실행한다.

소스 위치도 규약을 따라야 한다. Kotlin JVM 플러그인의 기본 레이아웃은 `src/main/kotlin`과 `src/main/java`이며, `chapter036/lib/src/main/java/com/example/lib/RockPaperScissors.kt`는 `src/main/java`에 놓여 있어 `main` 소스셋에 포함된다.

### 안드로이드 스튜디오는 무엇을 기준으로 빌드하고 실행하는가

"안드로이드 스튜디오는 코드를 실행, 디버그, 테스트할 때 실행/디버그 구성을 사용해 작업 방식을 결정"한다. 실행 구성에는 대상 모듈을 지정하는 Module 필드가 있고, 그 모듈에 어떤 플러그인과 설정이 적용되어 있는지는 해당 모듈의 `build.gradle.kts`가 결정한다.

정리하면 다음 두 층으로 나뉜다.

- 빌드 구성: 모듈의 `build.gradle.kts`가 그 모듈의 플러그인, 소스셋, 산출물을 결정한다.
- 실행 방식: 실행 구성 템플릿이 결정한다. 안드로이드 앱 모듈은 Android App 템플릿으로 매니페스트의 시작 액티비티를 실행하고, JVM 모듈은 IntelliJ 계열 Application 템플릿으로 `main` 함수를 실행한다.

따라서 "모듈의 `build.gradle.kts`를 기준으로 빌드한다"는 맞지만, 실행 대상과 방식은 실행 구성이 함께 결정한다.

## 체크리스트

- [ ] 안드로이드 앱에 단일 진입점 `main()`이 없다는 사실과 그 대안을 설명할 수 있다.
- [ ] 앱 구성요소와 `AndroidManifest.xml`이 실행 시작에 어떤 역할을 하는지 설명할 수 있다.
- [ ] Gradle 자바 플러그인의 `main` 소스셋과 안드로이드의 `src/main/`이 어떻게 다른지 설명할 수 있다.
- [ ] `SourceSet with name 'main' not found`가 구성 단계 실패인 이유를 설명할 수 있다.
- [ ] `kotlin("jvm")` 모듈에서는 같은 코드가 실행되는 이유를 설명할 수 있다.
- [ ] Android App 실행 구성과 JVM Application 실행 구성의 차이를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Application fundamentals](https://developer.android.com/guide/components/fundamentals)
- [Android Developers: Build variants and source sets](https://developer.android.com/build/build-variants)
- [Android Developers: Create and edit run/debug configurations](https://developer.android.com/studio/run/rundebugconfig)
- [Android Developers: Projects overview](https://developer.android.com/studio/projects)
- [Kotlin: Configure a Gradle project (Targeting the JVM)](https://kotlinlang.org/docs/gradle-configure-project.html#targeting-the-jvm)
- [Gradle: The Java Plugin (source sets)](https://docs.gradle.org/current/userguide/java_plugin.html#source_sets)
