# `@Composable`과 재구성

## 질문이 나온 문서와 코드

- [`chapter019/index.md`](../chapter019/index.md)
- 질문: `@Composable` 애너테이션은 무엇이며, 컴포저블 함수는 어떻게 UI를 갱신할까?
- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `@Composable`이 붙은 함수는 화면에 표시할 수 있는 뷰(view)나 위젯(widget)으로 동작하는 것 같은데 맞는가? 용어는 뷰인가 위젯인가?

## 질문 전제 점검

- **"`@Composable`이 있으면 화면에 표시할 수 있는 뷰나 위젯으로 동작한다"**는 이해 → 결과적으로는 맞지만 모델이 조금 어긋나 있다. 컴포저블은 **뷰 객체를 만들어 반환하는 팩터리가 아니라**, 지금 상태에서 UI가 어떠해야 하는지를 기술하는 함수다. 반환값이 없다는 점이 그 차이를 잘 보여준다.
- **"뷰인가 위젯인가"** → 둘 다 뷰 시스템의 용어다. Compose에서는 **컴포저블(composable)**이라고 부른다.
- 또 하나 흔한 오해가 있다. 컴포저블 함수를 "화면 요소 하나"와 일대일로 보는 관점이다. `Chapter078Theme`처럼 아무것도 그리지 않고 자식에게 값만 제공하는 컴포저블도 있다.

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

## 관련 아키텍처와 베스트 프랙티스

### 함수가 곧 컴포넌트다

뷰 시스템에서 재사용 단위는 클래스였다. 커스텀 뷰를 만들려면 `View`를 상속하고 생성자, 속성, `onMeasure`, `onDraw`를 다뤄야 했다. Compose에서 재사용 단위는 **함수**다. 상속 대신 함수 호출로 조합하며, 이것이 Compose가 "상속보다 조합" 원칙을 UI 계층에 적용한 방식이다.

### 명명과 시그니처 관례

Compose API 가이드라인이 정한 관례를 따르면 다른 사람이 읽기 쉬운 코드가 된다.

- UI를 방출하는 컴포저블은 **파스칼 케이스 명사**로 짓는다. `UnitConverter`, `Greeting`처럼 "무엇인지"를 이름에 담는다. `drawUnitConverter` 같은 동사형은 쓰지 않는다.
- 값을 반환하지 않는다. 무언가를 반환한다면 그것은 UI를 방출하는 컴포저블이 아니라 상태를 만드는 컴포저블(`rememberXxx`)이다.
- 선택적 `modifier: Modifier = Modifier` 파라미터를 받아 호출자가 배치와 크기를 결정하게 한다.
- 자식 콘텐츠는 마지막 `content` 파라미터로 받는다.

### 상태는 위로, 이벤트는 아래로

컴포저블을 재사용 가능하게 만드는 핵심 패턴은 상태 호이스팅이다. 컴포저블이 상태를 직접 소유하면 그 화면에서만 쓸 수 있지만, 상태와 콜백을 파라미터로 받으면 테스트와 프리뷰가 쉬워진다. 이것이 단방향 데이터 흐름(UDF)이 UI 계층에서 구현되는 모습이다.

```kotlin
// 상태를 가진 컴포저블 (호출 지점)
@Composable
fun UnitConverterRoute(viewModel: UnitConverterViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UnitConverter(uiState = uiState, onValueChange = viewModel::onValueChange)
}

// 상태가 없는 컴포저블 (재사용·테스트·프리뷰 가능)
@Composable
fun UnitConverter(uiState: UnitConverterUiState, onValueChange: (String) -> Unit) { ... }
```

### 재구성을 전제로 코드를 쓴다

컴포저블은 언제, 몇 번, 어떤 순서로 실행될지 보장되지 않는다. 그래서 다음이 원칙이 된다.

- 본문에서 외부 변수를 바꾸거나 네트워크를 호출하지 않는다. 이런 작업은 `LaunchedEffect`, `SideEffect` 같은 부수 효과 API로 명시한다.
- 파라미터 타입이 안정(stable)해야 Compose가 재구성을 건너뛸 수 있다. 불변 데이터 클래스와 불변 컬렉션을 UI 상태로 쓰는 이유다.
- 비싼 계산은 `remember(key)`로 캐시한다.

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
- [Android Developers: Compose API guidelines](https://developer.android.com/develop/ui/compose/api-guidelines)
- [Android Developers: Where to hoist state](https://developer.android.com/develop/ui/compose/state-hoisting)
- [Android Developers: Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects)
- [Android Developers: Stability in Compose](https://developer.android.com/develop/ui/compose/performance/stability)
