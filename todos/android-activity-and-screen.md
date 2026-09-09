# 액티비티와 화면의 관계

## 질문이 나온 문서

- [`chapter-019/index.md`](../chapter-019/index.md)
- 질문: 액티비티는 화면 자체일까? 화면이 바뀔 때마다 다른 액티비티로 이동할까?

## 공부할 내용

액티비티는 사용자와 상호작용하기 위한 Android 앱 컴포넌트다. Android 시스템은 앱 전체가 아니라 특정 액티비티를 실행해 사용자 여정의 진입점을 만든다. 액티비티는 앱이 UI를 그릴 창을 제공하며, 이 창은 보통 화면 전체를 채우지만 더 작게 표시될 수도 있다. 그러므로 액티비티와 화면을 항상 일대일 관계로 이해하면 안 된다.

화면 전환을 구현하는 방법도 앱 구조에 따라 다르다. 여러 액티비티를 사용하는 앱은 새 액티비티를 실행할 수 있지만, 현대적인 Compose 앱에서는 하나의 액티비티가 여러 컴포저블 목적지를 호스팅하는 단일 액티비티 구조를 사용할 수 있다. 이 구조에서는 `NavController`가 현재 목적지를 바꾸고 `NavHost`가 해당 컴포저블을 표시하므로, 사용자에게 보이는 화면이 바뀌어도 액티비티는 그대로일 수 있다.

## 체크리스트

- [ ] 액티비티가 Android의 앱 컴포넌트이자 사용자 상호작용의 진입점임을 설명할 수 있다.
- [ ] 액티비티가 제공하는 창과 기기 화면의 차이를 설명할 수 있다.
- [ ] 액티비티와 사용자에게 보이는 화면이 항상 일대일 관계가 아닌 이유를 설명할 수 있다.
- [ ] 다중 액티비티 구조와 Compose의 단일 액티비티 구조를 비교할 수 있다.
- [ ] `NavController`, `NavGraph`, `NavHost`의 역할을 구분할 수 있다.

## 공식 참고 자료

- [Android Developers: Introduction to activities](https://developer.android.com/guide/components/activities/intro-activities)
- [Android Developers: Design your navigation graph](https://developer.android.com/guide/navigation/design)
- [Android Developers: Navigate between screens with Compose](https://developer.android.com/codelabs/basic-android-kotlin-compose-navigation)
