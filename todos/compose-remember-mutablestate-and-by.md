# `remember`, `mutableStateOf`, 그리고 `by` 위임

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: 안드로이드에서 상태 관리는 `var open by remember { mutableStateOf(false) }`처럼 하는가? `by`와 `remember`는 무엇인가? `by`를 따라가면 `getValue`, `setValue`가 보이는데 왜 `by`로 표현하는가?

## 질문 전제 점검

- **"안드로이드에서 상태 관리는 아래처럼 하나?"** → 이 한 줄은 상태 관리 전체가 아니라 **화면 하나에 갇힌 지역 UI 상태**를 다루는 방법이다. 드롭다운이 열렸는지 같은 값에는 적절하지만, 화면을 벗어나 살아남아야 하는 상태(입력값, 서버 응답, 로그인 여부)에는 부족하다. 회전 한 번이면 사라진다.
- **"`by` 키워드, `remember` 키워드"** → 둘 다 키워드가 아니다. `by`는 코틀린 **문법**(위임 프로퍼티)이고, `remember`는 Compose가 제공하는 **함수**다. 성격이 다른 셋(`by` 문법 + `remember` 함수 + `mutableStateOf` 함수)이 한 줄에 겹쳐 있어 하나처럼 보일 뿐이다.
- **"`by`를 타고 들어가면 `getValue`, `setValue` 같은 함수가 보이는데 왜 `by`로 표현하지?"** → 관찰이 정확하다. `by`가 바로 그 두 함수 호출로 컴파일되는 문법이다. Compose가 만든 규칙이 아니라 코틀린 언어 기능이라는 점만 분명히 해두면 된다.

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

## 관련 아키텍처와 베스트 프랙티스

### 단방향 데이터 흐름(UDF)

Compose UI가 전제하는 데이터 흐름은 한 방향이다.

```
        상태(state) ↓                    ↑ 이벤트(event)
상태 홀더 ──────────→ 컴포저블 ──────────→ 상태 홀더
```

상태는 위에서 아래로 흐르고, 사용자 상호작용은 이벤트가 되어 위로 올라간다. 컴포저블이 상태를 직접 고치지 않고 "이런 일이 일어났다"고 알리기만 하면, 상태 변경 지점이 한 곳으로 모여 추적과 테스트가 쉬워진다.

### 상태 호이스팅

지역 상태를 파라미터로 끌어올리는 리팩터링이다.

```kotlin
// 이전: 상태를 스스로 소유해 재사용·테스트가 어렵다
@Composable
fun UnitConverter() {
    var open by remember { mutableStateOf(false) }
    ...
}

// 이후: 상태 없는 컴포저블 + 상태를 가진 호출자
@Composable
fun UnitConverter(open: Boolean, onOpenChange: (Boolean) -> Unit) { ... }
```

호이스팅한 컴포저블은 프리뷰에 원하는 상태를 바로 넣어볼 수 있고, 같은 UI를 다른 화면에서 재사용할 수 있다. 다만 무조건 올리는 것이 답은 아니다. 공식 가이드는 **그 상태를 읽는 모든 컴포저블의 가장 낮은 공통 조상**까지만 올리라고 안내한다. 드롭다운 열림 여부처럼 아무도 관심 없는 상태는 지역에 두는 편이 낫다.

### 상태의 종류에 따라 도구를 고른다

| 상태 성격 | 예 | 도구 |
| --- | --- | --- |
| 일시적 UI 상태 | 드롭다운 열림, 스크롤 위치 | `remember` |
| 구성 변경을 견뎌야 하는 UI 상태 | 입력 중인 텍스트 | `rememberSaveable` |
| 화면 단위 비즈니스 상태 | 변환 결과, 로딩·에러 | `ViewModel` + `StateFlow` |
| 앱 전체가 공유하는 데이터 | 로그인 세션, 설정 | 데이터 레이어(Repository) |

`ViewModel`의 상태는 화면에서 `collectAsStateWithLifecycle()`로 구독한다. UI 상태는 보통 불변 데이터 클래스 하나로 모아 표현한다.

```kotlin
data class UnitConverterUiState(
    val input: String = "",
    val result: String = "",
    val isMenuOpen: Boolean = false,
)
```

### 자주 밟는 함정

- `remember`를 빼먹어 매 재구성마다 상태가 초기화된다.
- 입력이 바뀌어도 갱신되어야 하는 값에 `remember(key)`의 키를 주지 않아 옛 값이 남는다.
- 파생 값을 상태로 따로 들고 있다가 불일치가 생긴다. 계산으로 얻을 수 있는 값은 상태로 만들지 말고, 비싼 경우에만 `derivedStateOf`를 쓴다.
- 컴포저블 본문에서 상태를 변경한다. 상태 변경은 이벤트 콜백이나 부수 효과 API 안에서 한다.

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
- [Android Developers: UI layer architecture](https://developer.android.com/topic/architecture/ui-layer)
- [Android Developers: State holders and UI state](https://developer.android.com/topic/architecture/ui-layer/stateholders)
- [Android Developers: Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects)
- [Android Developers: ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)
