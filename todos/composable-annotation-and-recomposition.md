# `@Composable`과 재구성

## 질문이 나온 문서와 코드

- [`chapter-019/index.md`](../chapter-019/index.md)
- 질문: `@Composable` 애너테이션은 무엇이며, 컴포저블 함수는 어떻게 UI를 갱신할까?
- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `@Composable`이 붙은 함수는 화면에 표시할 수 있는 뷰(view)나 위젯(widget)으로 동작하는 것 같은데 맞는가? 용어는 뷰인가 위젯인가?

## 공부할 내용

`@Composable`은 함수가 데이터를 UI로 변환하는 데 사용된다는 사실을 Compose 컴파일러에 알린다. Compose 컴파일러 플러그인은 이 함수를 일반 Kotlin 함수와 다르게 변환하여 Compose 런타임이 함수의 호출 위치, 입력값, 읽은 상태를 추적할 수 있게 한다. 컴포저블 함수는 원칙적으로 다른 컴포저블 함수 또는 `setContent`처럼 컴포저블 실행 환경을 제공하는 곳에서 호출한다.

컴포저블 함수는 UI 객체를 직접 만들어 반환하기보다 현재 상태에서 보여야 할 UI를 선언한다. 다른 컴포저블을 호출해 UI 계층을 구성할 수 있지만, 모든 컴포저블이 화면 요소 하나와 대응하는 것은 아니다. 여러 UI 요소를 묶거나, 다른 컴포저블에 상태와 동작을 제공하거나, 이펙트를 관리하는 컴포저블도 있다.

초기 컴포지션에서는 컴포저블을 실행해 UI를 설명하는 `Composition`을 만든다. 이후 컴포저블이 읽은 `State` 값이 바뀌면 Compose는 영향을 받을 수 있는 부분의 재구성을 예약한다. 재구성 과정에서 해당 컴포저블은 다시 실행될 수 있고, 입력값이 바뀌지 않은 부분은 건너뛸 수도 있다. 따라서 컴포저블은 여러 번 또는 순서와 다르게 실행되거나 실행이 생략될 가능성을 고려해 빠르고 멱등적이며 부수 효과가 없도록 작성하는 것이 원칙이다.

### 뷰인가 위젯인가

"화면에 표시할 수 있는 무언가"라는 이해는 결과적으로 맞다. 다만 용어는 구분해서 쓰는 편이 좋다.

- **View**: 뷰 시스템의 클래스다. "`View`는 보통 사용자가 보고 상호작용할 수 있는 무언가를 그린다." `Button`, `TextView`가 그 하위 클래스다.
- **위젯(widget)**: 뷰 시스템에서 `View` 객체를 부르는 통칭이다. "`View` 객체는 흔히 위젯이라 불리며 `Button`이나 `TextView` 같은 여러 하위 클래스 중 하나일 수 있다." 참고로 홈 화면에 놓는 앱 위젯(App Widget)은 또 다른 개념이라 문맥을 봐야 한다.
- **컴포저블(composable)**: Compose에서 쓰는 용어다. `@Composable`이 붙은 함수, 또는 그 함수가 만들어 내는 UI 조각을 가리킨다.

Compose에서 정확한 표현은 "컴포저블 함수"이며, 이 함수는 뷰 객체를 만들어 반환하지 않는다. "Compose 함수는 UI 위젯을 생성하는 대신 목표 화면 상태를 기술하므로 아무것도 반환할 필요가 없다." 그래서 컴포저블은 값을 반환하는 함수라기보다 UI를 방출(emit)하는 함수로 설명한다.

또 하나 주의할 점은 모든 컴포저블이 화면 요소 하나에 대응하지는 않는다는 것이다. `Chapter078Theme`처럼 자식에게 값을 제공하기만 하고 스스로는 아무것도 그리지 않는 컴포저블도 있다.

## 체크리스트

- [ ] `@Composable`이 Compose 컴파일러에 전달하는 의미를 설명할 수 있다.
- [ ] 컴포저블 함수와 일반 Kotlin 함수의 호출 규칙 차이를 설명할 수 있다.
- [ ] 컴포저블 함수가 UI 객체를 반환하는 대신 UI 상태를 선언한다는 의미를 설명할 수 있다.
- [ ] 초기 컴포지션과 재구성의 차이를 설명할 수 있다.
- [ ] `State` 읽기와 재구성의 관계를 설명할 수 있다.
- [ ] 컴포저블을 빠르고 멱등적이며 부수 효과 없이 작성해야 하는 이유를 설명할 수 있다.
- [ ] 뷰, 위젯, 컴포저블이라는 용어를 문맥에 맞게 구분해 쓸 수 있다.
- [ ] 컴포저블 함수가 값을 반환하지 않는 이유를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Thinking in Compose](https://developer.android.com/develop/ui/compose/mental-model)
- [Android Developers: Android Compose tutorial](https://developer.android.com/develop/ui/compose/tutorial)
- [Android Developers: Lifecycle of composables](https://developer.android.com/develop/ui/compose/lifecycle)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: Layouts and binding expressions (View, ViewGroup, widget 용어)](https://developer.android.com/develop/ui/views/layout/declaring-layout)
- [Android Developers: Jetpack Compose phases](https://developer.android.com/develop/ui/compose/phases)
