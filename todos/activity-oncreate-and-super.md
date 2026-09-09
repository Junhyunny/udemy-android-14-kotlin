# `onCreate`와 `super.onCreate`의 역할

## 질문이 나온 문서

- [`chapter-019/index.md`](../chapter-019/index.md)
- 질문: `onCreate`는 언제 호출되며, `super.onCreate(savedInstanceState)`는 왜 필요할까?

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
