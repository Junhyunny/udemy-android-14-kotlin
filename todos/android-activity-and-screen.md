# 액티비티와 화면의 관계

## 질문이 나온 문서

- [`chapter-019/index.md`](../chapter-019/index.md)
- 질문: 액티비티는 화면 자체일까? 화면이 바뀔 때마다 다른 액티비티로 이동할까?

## 질문 전제 점검

- **"액티비티는 화면 자체일까?"** → 부분적으로만 맞다. 액티비티는 화면이 아니라 UI를 그릴 **창(window)을 제공하는 앱 컴포넌트**다. 창은 보통 화면을 가득 채우지만 다이얼로그처럼 더 작을 수도 있고, 멀티 윈도우에서는 화면 일부만 차지한다.
- **"화면이 바뀔 때마다 다른 액티비티로 이동할까?"** → 아니다. 그렇게 만들 수도 있지만 현재 권장 구조가 아니다. Compose 앱의 기본은 액티비티 하나가 여러 컴포저블 목적지를 호스팅하는 단일 액티비티 구조다.

정리하면 액티비티는 "화면"이 아니라 **시스템과 앱 사이의 진입점이자 호스트**로 이해하는 편이 정확하다.

## 공부할 내용

액티비티는 사용자와 상호작용하기 위한 Android 앱 컴포넌트다. Android 시스템은 앱 전체가 아니라 특정 액티비티를 실행해 사용자 여정의 진입점을 만든다. 액티비티는 앱이 UI를 그릴 창을 제공하며, 이 창은 보통 화면 전체를 채우지만 더 작게 표시될 수도 있다. 그러므로 액티비티와 화면을 항상 일대일 관계로 이해하면 안 된다.

화면 전환을 구현하는 방법도 앱 구조에 따라 다르다. 여러 액티비티를 사용하는 앱은 새 액티비티를 실행할 수 있지만, 현대적인 Compose 앱에서는 하나의 액티비티가 여러 컴포저블 목적지를 호스팅하는 단일 액티비티 구조를 사용할 수 있다. 이 구조에서는 `NavController`가 현재 목적지를 바꾸고 `NavHost`가 해당 컴포저블을 표시하므로, 사용자에게 보이는 화면이 바뀌어도 액티비티는 그대로일 수 있다.

## 관련 아키텍처와 베스트 프랙티스

### 단일 액티비티 아키텍처

현재 안드로이드의 기본 권장 구조다. 액티비티 하나가 `NavHost`를 띄우고, 사용자가 인식하는 "화면"은 내비게이션 그래프의 목적지(컴포저블)가 된다.

```
Activity (진입점 · 호스트)
└ setContent
  └ Theme
    └ NavHost
      ├ 목적지 A (화면)
      ├ 목적지 B (화면)
      └ 목적지 C (화면)
```

액티비티를 화면마다 만들면 화면 전환마다 인텐트 계약, 백스택, 상태 전달, 생명주기 관리 비용을 시스템 경계 너머로 지불해야 한다. 반면 목적지끼리는 같은 프로세스·같은 컴포지션 안에 있으므로 타입 안전한 인자 전달과 공유 상태가 쉽다.

### 액티비티를 여러 개 두는 것이 타당한 경우

- 다른 앱이 진입할 수 있는 별도의 공개 진입점이 필요할 때(공유 대상, 딥링크 전용 화면 등)
- 런처 아이콘이 여럿이거나, 별도 태스크·프로세스로 띄워야 할 때
- 시스템이 요구하는 특수 컴포넌트(설정 화면, 위젯 구성 액티비티 등)

즉 액티비티는 "화면 단위"가 아니라 **시스템과의 계약 단위**로 나눈다.

### 화면 = UI 상태의 단위

앱 아키텍처 가이드는 UI 레이어를 화면 단위로 구성하고, 각 화면이 하나의 UI 상태와 상태 홀더(주로 `ViewModel`)를 갖도록 권장한다. 액티비티는 이 구조에서 얇은 껍데기가 된다.

### 화면 = 물리 화면도 아니다

폴더블과 태블릿에서는 창 크기 클래스(WindowSizeClass)에 따라 하나의 목적지가 목록·상세 두 패널로 펼쳐질 수 있다. "액티비티 = 화면 = 기기 화면" 등식은 세 지점 모두에서 깨진다고 이해하면 좋다.

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
- [Android Developers: Guide to app architecture](https://developer.android.com/topic/architecture)
- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation)
- [Android Developers: Support different screen sizes](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-screen-sizes)
