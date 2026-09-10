# 안드로이드 모듈에서 `main` 함수가 실행되지 않는 이유

## 질문이 나온 코드

- [`chapter036/lib/build.gradle.kts`](../chapter036/lib/build.gradle.kts)
- 질문: 빈 액티비티 프로젝트에 `main` 함수가 있는 코틀린 파일을 만들어 실행했더니 `SourceSet with name 'main' not found` 에러가 났다. 왜 실패했고, 안드로이드 스튜디오는 모듈의 `build.gradle.kts`를 기준으로 빌드하고 실행하는가?

## 질문 전제 점검

- **"빈 액티비티로 만든 후에 `main` 메서드가 있는 코틀린 파일을 만들어 실행"**하려 한 시도 자체가 안드로이드 앱 모듈에서는 성립하지 않는다. 안드로이드 앱에는 단일 진입점 `main()`이 없기 때문이다. 이 부분이 질문에 깔린 가장 큰 전제 오류다.
- **에러 원인 추정("안드로이드 앱이 일반 자바 애플리케이션이 아니기 때문")** → 맞다. 다만 한 단계 더 정확히 말하면, 실패한 지점은 코드 실행이 아니라 **Gradle 구성 단계**다. IDE가 만든 JVM 실행용 초기화 스크립트가 자바 플러그인의 `main` 소스셋을 찾지 못해 태스크 생성에 실패했다.
- **"안드로이드 스튜디오는 기본적으로 해당 모듈에 있는 `build.gradle.kts` 파일을 기준으로 빌드, 실행하나?"** → 절반만 맞다. **빌드 방식**은 모듈의 빌드 파일이 결정하지만, **무엇을 어떻게 실행할지**는 실행/디버그 구성이 결정한다. 두 층을 분리해서 보는 것이 중요하다.
- 참고로 `src/main/`이라는 디렉터리가 있으니 `main` 소스셋도 있으리라 생각하기 쉬운데, 안드로이드의 `main`과 Gradle 자바 플러그인의 `main`은 이름만 같고 서로 다른 개념이다.

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

## 관련 아키텍처와 베스트 프랙티스

### 진입점이 다르면 실행 모델도 다르다

| | JVM 애플리케이션 | 안드로이드 앱 |
| --- | --- | --- |
| 진입점 | `main()` 함수 하나 | 매니페스트에 선언된 구성요소 여러 개 |
| 시작 주체 | JVM | 안드로이드 시스템(인텐트) |
| 수명 관리 | 프로세스가 `main` 종료까지 | 시스템이 생명주기 콜백으로 관리 |
| 실행 구성 | Application/Kotlin | Android App |

이 차이 때문에 "코드를 실행한다"는 말의 의미부터 달라진다. 안드로이드에서는 코드를 실행하는 것이 아니라 **기기에 앱을 설치하고 컴포넌트를 띄우도록 시스템에 요청**한다.

### 순수 로직은 안드로이드 밖으로

이번에 겪은 문제를 회피 수단이 아니라 설계 원칙으로 승격하면 유용하다. 가위바위보 판정처럼 안드로이드 API가 필요 없는 로직은 안드로이드 모듈 밖에 두는 편이 낫다.

- 빌드와 테스트가 빠르다. 에뮬레이터도, 계측 테스트도 필요 없다.
- 다른 플랫폼이나 다른 앱에서 재사용할 수 있다.
- UI 프레임워크 변화(뷰 → Compose)에 영향받지 않는다.

이는 아키텍처 가이드가 말하는 관심사 분리, 그리고 도메인 로직을 프레임워크에서 떼어내는 일반적인 원칙과 같은 방향이다.

### `main` 대신 테스트로 실행하기

학습 중 로직을 빠르게 돌려보고 싶을 때 `main` 함수보다 단위 테스트가 더 나은 도구다. 실행 구성 문제를 겪지 않고, 입력과 기대값을 남길 수 있으며, 나중에 회귀 검증에도 쓰인다.

```kotlin
class RockPaperScissorsTest {
    @Test
    fun `같은 선택이면 무승부다`() {
        assertEquals("Tie", judge(player = "rock", computer = "rock"))
    }
}
```

이 형태로 쓰려면 `main` 안에 뒤섞인 입출력과 판정 로직을 분리해 판정 함수를 순수 함수로 뽑아야 한다. 테스트하기 쉬운 구조가 곧 잘 분리된 구조라는 신호이기도 하다.

### 에러 메시지 읽는 법

`SourceSet with name 'main' not found`처럼 프레임워크 내부 용어가 나오는 에러는 다음 순서로 접근하면 원인에 빨리 닿는다.

1. 어느 단계에서 실패했는가. 구성(configuration)인가 실행(execution)인가. 여기서는 태스크를 만들다 실패했으니 구성 단계다.
2. 그 용어가 어느 도구의 개념인가. `SourceSet`은 Gradle 자바 플러그인의 개념이다.
3. 지금 모듈에 그 개념이 존재하는가. 안드로이드 앱 모듈에는 없다. 여기서 원인이 드러난다.

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
- [Android Developers: Guide to app architecture](https://developer.android.com/topic/architecture)
- [Android Developers: Test apps on Android](https://developer.android.com/training/testing)
- [Android Developers: Build local unit tests](https://developer.android.com/training/testing/local-tests)
