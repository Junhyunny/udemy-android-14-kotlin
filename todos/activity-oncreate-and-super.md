# `onCreate`와 `super.onCreate`의 역할

## 질문이 나온 문서

- [`chapter-019/index.md`](../chapter-019/index.md)
- 질문: `onCreate`는 언제 호출되며, `super.onCreate(savedInstanceState)`는 왜 필요할까?

## 질문 전제 점검

질문의 전제에는 잘못된 부분이 없다. `onCreate`는 실제로 시스템이 호출하는 생명주기 콜백이고, `super.onCreate(savedInstanceState)` 호출은 선택이 아니라 프레임워크와의 계약이다.

한 가지만 보태면, `super` 호출이 필요한 이유를 "관례" 정도로 이해하기 쉬운데 그렇지 않다. 프레임워크 `Activity`는 부모 구현이 호출되지 않으면 `SuperNotCalledException`을 던진다. 문법적 예의가 아니라 **런타임에 강제되는 규약**이다.

## 공부할 내용

`onCreate`는 Android 시스템이 액티비티를 처음 생성할 때 호출하는 생명주기 콜백이다. 액티비티는 이 콜백에서 `Created` 상태에 들어간다. 앱은 여기에서 클래스 범위 변수 초기화, `ViewModel` 연결, UI 설정처럼 액티비티 인스턴스의 생애 동안 한 번 수행할 기본 시작 작업을 한다. Compose를 사용하는 `ComponentActivity`에서는 일반적으로 `setContent`를 호출해 Compose UI의 시작점을 설정한다.

매개변수 `savedInstanceState`는 시스템이 이전 액티비티 인스턴스에서 저장한 상태를 담는 `Bundle`이며, 처음 생성된 액티비티라면 `null`이다.

오버라이드한 `onCreate`에서 `super.onCreate(savedInstanceState)`를 호출하면 상위 클래스가 구현한 생성 절차가 실행된다. 프레임워크 `Activity`는 하위 클래스가 부모 구현을 호출하도록 요구하며, 호출하지 않으면 예외를 발생시킨다. `ComponentActivity`의 부모 구현에는 저장 상태 레지스트리 복원과 컨텍스트 사용 가능 리스너 통지 등 Jetpack 구성요소가 의존하는 초기화도 포함된다. 따라서 일반적인 초기화 순서는 부모 구현 호출 후 앱의 UI와 상태를 설정하는 것이다.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
        App()
    }
}
```

## 관련 아키텍처와 베스트 프랙티스

### 템플릿 메서드 패턴

액티비티 생명주기는 템플릿 메서드 패턴의 교과서적 사례다. 프레임워크가 전체 절차(생성 → 시작 → 재개 → …)를 소유하고, 앱은 정해진 훅 지점만 재정의한다. `super` 호출은 "부모가 담당하는 절차를 이 시점에 실행하라"는 지시이며, 호출 위치가 곧 앱 코드와 프레임워크 코드의 실행 순서를 결정한다.

관례적으로 생성 계열 콜백(`onCreate`, `onStart`, `onResume`)은 **맨 처음에** `super`를 호출하고, 해제 계열 콜백(`onPause`, `onStop`, `onDestroy`)은 **맨 마지막에** 호출한다. 자원을 쓰기 전에 준비하고, 정리한 뒤에 반납하는 순서다.

### `onCreate`를 얇게 유지하기

`onCreate`는 앱이 처음 화면을 그리기까지의 시간(스타트업 지연)에 그대로 포함된다. 여기에 파일 읽기, 네트워크, 대형 객체 생성 같은 작업을 넣으면 시작이 느려지고 ANR 위험도 생긴다.

- 앱 전역 초기화는 App Startup 라이브러리나 지연 초기화로 옮긴다.
- 화면 데이터 로딩은 `ViewModel`에서 시작하고 UI는 로딩 상태를 표현한다.
- 컴포넌트가 생명주기를 알아야 한다면 액티비티가 일일이 호출하지 말고, 그 컴포넌트가 `DefaultLifecycleObserver`로 스스로 구독하게 한다(옵저버 패턴).

### 상태 복원의 큰 그림

`savedInstanceState`는 상태 보존 수단의 하나일 뿐이다. 실무에서는 다음 표처럼 나눠 판단한다.

| 살아남아야 하는 범위 | 수단 |
| --- | --- |
| 재구성 | `remember` |
| 구성 변경(회전 등) | `ViewModel`, `rememberSaveable` |
| 시스템에 의한 프로세스 종료 | `SavedStateHandle`, `rememberSaveable` |
| 앱 재실행 이후 | DataStore, Room 등 영구 저장소 |

## 체크리스트

- [ ] 액티비티 생명주기에서 `onCreate`가 호출되는 시점을 설명할 수 있다.
- [ ] `savedInstanceState`가 `null`인 경우와 값이 있는 경우를 구분할 수 있다.
- [ ] `onCreate`에서 수행하기 적합한 초기화 작업을 예로 들 수 있다.
- [ ] `super.onCreate(savedInstanceState)`를 생략하면 안 되는 이유를 설명할 수 있다.
- [ ] Compose 앱에서 `setContent`의 역할을 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: The activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)
- [Android Developers API: Activity](https://developer.android.com/reference/android/app/Activity)
- [Android Developers API: ComponentActivity](https://developer.android.com/reference/androidx/activity/ComponentActivity)
- [Android Developers: Handling lifecycles with lifecycle-aware components](https://developer.android.com/topic/libraries/architecture/lifecycle)
- [Android Developers: App Startup](https://developer.android.com/topic/libraries/app-startup)
- [Android Developers: Save UI states](https://developer.android.com/topic/libraries/architecture/saving-states)
