# `remember`, `mutableStateOf`, 그리고 `by` 위임

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: 안드로이드에서 상태 관리는 `var open by remember { mutableStateOf(false) }`처럼 하는가? `by`와 `remember`는 무엇인가? `by`를 따라가면 `getValue`, `setValue`가 보이는데 왜 `by`로 표현하는가?

## 공부할 내용

한 줄에 서로 다른 세 가지 개념이 겹쳐 있다. 분리해서 보면 이해하기 쉽다.

```kotlin
var open by remember { mutableStateOf(false) }
//        │  │          └ 관찰 가능한 상태 객체를 만든다
//        │  └ 컴포지션에 값을 기억시킨다
//        └ 코틀린 위임 프로퍼티 문법
```

### `mutableStateOf` — 관찰 가능한 상태

"`mutableStateOf`는 관찰 가능한 `MutableState<T>`를 만들며, 이는 Compose 런타임에 통합된 관찰 가능 타입이다." 그리고 "`value`가 변경되면 그 `value`를 읽는 모든 컴포저블 함수의 재구성이 예약된다." 일반 `var`를 쓰면 값은 바뀌어도 Compose가 변경을 알 수 없어 화면이 갱신되지 않는다.

### `remember` — 컴포지션에 기억시키기

"`remember`로 계산한 값은 초기 컴포지션 시 `Composition`에 저장되고, 재구성 시에는 저장된 값이 반환된다." 컴포저블 함수는 재구성 때마다 다시 실행되므로, `remember`가 없으면 매번 `mutableStateOf(false)`가 새로 만들어져 상태가 초기화된다. 두 함수는 역할이 다르고 항상 짝으로 쓰인다.

`remember`는 재구성은 견디지만 구성 변경은 견디지 못한다. "`remember`는 재구성에 걸쳐 상태를 유지하도록 돕지만 구성 변경에 걸쳐서는 유지되지 않는다. 이를 위해서는 `rememberSaveable`을 사용해야 한다."

### `by` — 위임 프로퍼티

`by`는 Compose 문법이 아니라 코틀린 언어 기능이다. "`by` 뒤의 표현식은 델리게이트이며, 프로퍼티에 대응하는 `get()`(그리고 `set()`)이 그 객체의 `getValue()`, `setValue()` 메서드로 위임되기 때문이다." 컴파일러는 다음과 같이 변환한다.

```kotlin
class C { var prop: Type by MyDelegate() }

// 컴파일러가 생성하는 코드
class C {
    private val prop$delegate = MyDelegate()
    var prop: Type
        get() = prop$delegate.getValue(this, this::prop)
        set(value: Type) = prop$delegate.setValue(this, this::prop, value)
}
```

`val`은 `getValue` 연산자만, `var`는 `getValue`와 `setValue`를 모두 요구한다. 그래서 `by`를 따라가면 이 두 함수가 보인다. Compose는 `MutableState<T>`에 대한 이 연산자들을 확장 함수로 제공하며, 그래서 다음 두 import가 필요하다.

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
```

`by`를 쓰는 이유는 `open.value` 대신 `open`으로 읽고 쓸 수 있게 하는 문법 설탕이다.

### 세 가지 선언 방식

문서는 세 가지가 "동등하며 서로 다른 용도를 위한 문법 설탕으로 제공된다. 작성 중인 컴포저블에서 가장 읽기 쉬운 코드가 되는 것을 고르라"고 안내한다.

```kotlin
val mutableState = remember { mutableStateOf(default) }   // mutableState.value
var value by remember { mutableStateOf(default) }         // value
val (value, setValue) = remember { mutableStateOf(default) }
```

### 상태 관리의 다음 단계

이 예제처럼 컴포저블 내부에 상태를 두는 것은 시작점이다. 실제 앱에서는 상태 호이스팅으로 상태를 호출자에게 올리고, 화면 수준 상태는 `ViewModel`에 두는 방식을 권장한다.

## 체크리스트

- [ ] `mutableStateOf`가 일반 변수와 다른 점을 재구성 관점에서 설명할 수 있다.
- [ ] `remember`를 빼면 어떤 일이 생기는지 설명할 수 있다.
- [ ] `remember`와 `rememberSaveable`의 차이를 설명할 수 있다.
- [ ] `by`가 코틀린 위임 프로퍼티 문법임을 설명할 수 있다.
- [ ] `getValue`/`setValue` import가 필요한 이유를 설명할 수 있다.
- [ ] 세 가지 상태 선언 방식을 서로 바꿔 쓸 수 있다.
- [ ] 상태 호이스팅이 필요한 이유를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: Where to hoist state](https://developer.android.com/develop/ui/compose/state-hoisting)
- [Kotlin: Delegated properties](https://kotlinlang.org/docs/delegated-properties.html)
- [Android Developers API: `androidx.compose.runtime` 패키지](https://developer.android.com/reference/kotlin/androidx/compose/runtime/package-summary)
