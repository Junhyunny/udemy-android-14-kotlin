# `@Composable`과 재구성

## 질문이 나온 문서

- [`chapter-019/index.md`](../chapter-019/index.md)
- 질문: `@Composable` 애너테이션은 무엇이며, 컴포저블 함수는 어떻게 UI를 갱신할까?

## 공부할 내용

`@Composable`은 함수가 데이터를 UI로 변환하는 데 사용된다는 사실을 Compose 컴파일러에 알린다. Compose 컴파일러 플러그인은 이 함수를 일반 Kotlin 함수와 다르게 변환하여 Compose 런타임이 함수의 호출 위치, 입력값, 읽은 상태를 추적할 수 있게 한다. 컴포저블 함수는 원칙적으로 다른 컴포저블 함수 또는 `setContent`처럼 컴포저블 실행 환경을 제공하는 곳에서 호출한다.

컴포저블 함수는 UI 객체를 직접 만들어 반환하기보다 현재 상태에서 보여야 할 UI를 선언한다. 다른 컴포저블을 호출해 UI 계층을 구성할 수 있지만, 모든 컴포저블이 화면 요소 하나와 대응하는 것은 아니다. 여러 UI 요소를 묶거나, 다른 컴포저블에 상태와 동작을 제공하거나, 이펙트를 관리하는 컴포저블도 있다.

초기 컴포지션에서는 컴포저블을 실행해 UI를 설명하는 `Composition`을 만든다. 이후 컴포저블이 읽은 `State` 값이 바뀌면 Compose는 영향을 받을 수 있는 부분의 재구성을 예약한다. 재구성 과정에서 해당 컴포저블은 다시 실행될 수 있고, 입력값이 바뀌지 않은 부분은 건너뛸 수도 있다. 따라서 컴포저블은 여러 번 또는 순서와 다르게 실행되거나 실행이 생략될 가능성을 고려해 빠르고 멱등적이며 부수 효과가 없도록 작성하는 것이 원칙이다.

## 체크리스트

- [ ] `@Composable`이 Compose 컴파일러에 전달하는 의미를 설명할 수 있다.
- [ ] 컴포저블 함수와 일반 Kotlin 함수의 호출 규칙 차이를 설명할 수 있다.
- [ ] 컴포저블 함수가 UI 객체를 반환하는 대신 UI 상태를 선언한다는 의미를 설명할 수 있다.
- [ ] 초기 컴포지션과 재구성의 차이를 설명할 수 있다.
- [ ] `State` 읽기와 재구성의 관계를 설명할 수 있다.
- [ ] 컴포저블을 빠르고 멱등적이며 부수 효과 없이 작성해야 하는 이유를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Thinking in Compose](https://developer.android.com/develop/ui/compose/mental-model)
- [Android Developers: Android Compose tutorial](https://developer.android.com/develop/ui/compose/tutorial)
- [Android Developers: Lifecycle of composables](https://developer.android.com/develop/ui/compose/lifecycle)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
