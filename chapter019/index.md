## Chapter 019. `MainActivity`의 `onCreate` 메서드와 `@Composable` 애너테이션 이해하기

### 액티비티란 무엇일까?

- 액티비티(`Activity`)는 사용자와 상호작용하기 위한 진입점이자, 앱이 UI를 그릴 수 있는 창(window)을 제공하는 Android 컴포넌트다.
- 액티비티의 창은 일반적으로 화면 전체를 채우지만, 다이얼로그처럼 화면보다 작게 표시될 수도 있다.
- 따라서 액티비티를 단순히 하나의 화면과 동일한 개념으로 보기는 어렵다. 특히 Compose 앱에서는 하나의 액티비티 안에서 여러 컴포저블 목적지(destination) 사이를 이동하는 단일 액티비티 구조를 사용할 수 있다.

> 학습 TODO: [액티비티와 화면의 관계](../todos/android-activity-and-screen.md)

#### `super.onCreate`는 무엇을 할까?

- `onCreate`는 시스템이 액티비티를 생성할 때 호출하는 생명주기 콜백이다.
- 이 메서드에서는 액티비티가 살아 있는 동안 한 번만 수행할 초기 설정을 한다. Compose 앱에서는 일반적으로 `setContent`를 호출하여 최상위 컴포저블을 설정한다.
- `super.onCreate(savedInstanceState)`는 부모 클래스인 `Activity` 또는 `ComponentActivity`가 담당하는 필수 초기화와 상태 복원 절차를 실행한다. 부모 구현을 호출하지 않으면 예외가 발생할 수 있으므로 반드시 호출해야 한다.

> 학습 TODO: [`onCreate`와 `super.onCreate`의 역할](../todos/activity-oncreate-and-super.md)

### `@Composable` 애너테이션은 무엇일까?

- `@Composable`은 해당 함수가 Compose 컴파일러의 특별한 처리를 받는 컴포저블 함수임을 나타낸다.
- 컴포저블 함수는 데이터를 입력받고, 다른 컴포저블 함수를 호출하여 현재 상태에 맞는 UI 구조를 선언한다.
- 컴포저블 함수는 UI 요소 하나만을 의미하지 않는다. 여러 컴포저블을 조합하거나, UI를 직접 만들지 않고 상태와 동작을 제공할 수도 있다.
- 상태가 바뀌면 Compose는 영향을 받는 컴포저블을 다시 실행하는 재구성(recomposition)을 예약하여 UI를 갱신한다.

> 학습 TODO: [`@Composable`과 재구성](../todos/composable-annotation-and-recomposition.md)
