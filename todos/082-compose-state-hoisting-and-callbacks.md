# 자식 컴포저블에 `MutableState`를 넘기는 것과 값 + 콜백을 넘기는 것

## 질문이 나온 코드

이 저장소의 코드가 아니라 외부에서 받은 답변을 검토한 문서다.

```kotlin
// ① 지금 코드 — MutableState 자체를 넘긴다
@Composable
fun Keypad(roomCode: MutableState<String>) {
    roomCode.value += item
}

// ② 받은 제안 A — by 위임으로 깔끔하게
@Composable
fun Keypad(roomCodeState: MutableState<String>) {
    var roomCode by roomCodeState
}

// ③ 받은 제안 B (권장이라고 함) — 값 + 콜백
@Composable
fun Keypad(
    roomCode: String,
    onRoomCodeChange: (String) -> Unit
)
```

질문: 이 말이 맞는가? 콜백으로 주는 것이 베스트 프랙티스인가? 왜 유익한가?

## 질문 전제 점검

- **"`var roomCode by roomCodeState`로 쓸 수 있다"** → **맞다.** `MutableState<T>`에는 `getValue`/`setValue` 연산자 확장이 정의되어 있어서 이미 존재하는 상태 객체에도 위임을 걸 수 있다. 필요한 import 두 개(`androidx.compose.runtime.getValue`, `androidx.compose.runtime.setValue`)도 정확하다. → [`034-compose-remember-mutablestate-and-by.md`](034-compose-remember-mutablestate-and-by.md)

- **"자식에게 `MutableState`를 넘기기보다 값 + 콜백이 낫다"** → **맞다. 공식 가이드라인 그대로다.**

  > "**Prefer stateless and controlled `@Composable` functions.** In this context, 'stateless' refers to `@Composable` functions that retain no state of their own, but instead accept external state parameters that are owned and provided by the caller."
  >
  > — [Compose API guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md)

  공식 문서가 제시하는 표준 변환 공식도 똑같다.

  > "The general pattern for state hoisting in Jetpack Compose is to replace the state variable with two parameters: `value: T` ... `onValueChange: (T) -> Unit`"
  >
  > — [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)

- **"그래서 `Keypad(roomCode, onRoomCodeChange)`가 정답이다"** → **방향은 맞지만 이 경우엔 더 나은 답이 있다. 여기가 이 문서의 핵심이다.**

  `Keypad`를 다시 보면, **`roomCode`를 컴포지션 중에 한 번도 읽지 않는다.** 오직 `onClick` 람다 안에서 "기존 값 + 누른 키"를 만들 때만 쓴다. 즉 **키패드는 현재 방 번호가 뭔지 알 필요가 없다.**

  ```kotlin
  // 자식이 값을 알 필요가 없다면, 값을 주지 않는 것이 맞다
  @Composable
  fun Keypad(onKeyPress: (String) -> Unit)
  ```

  공식 문서도 `onValueChange` 형태에 얽매이지 말라고 명시한다.

  > "However, you are **not limited to** `onValueChange`. **If more specific events are appropriate for the composable, you should define them using lambdas.**"

  성능 문서의 원칙과도 같은 방향이다.

  > "**Defer reads as long as possible.** Deferring state reads will ensure that Compose re-runs the minimum possible code on recomposition."
  >
  > — [Android Developers: Follow best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)

- **"`MutableState`를 넘기는 것은 언제나 나쁘다"** → **아니다.** 가이드라인은 파라미터가 많아지면 오히려 **묶으라고** 한다. 다만 묶는 대상은 `MutableState<String>` 같은 **원시 상태 홀더**가 아니라 **도메인 이름을 가진 `@Stable` 타입**이다.

  > "A pattern of stateless parameters and multiple event callback parameters will eventually reach a point of scale where it becomes unwieldy. As a composable function's parameter list grows it may become appropriate to **factor a collection of state and callbacks into an interface**, allowing a caller to provide a cohesive policy object as a unit."

  `ScrollState`, `LazyListState`, `SheetState`가 바로 그 예다. → [`044-compose-modal-bottom-sheet-state-and-sheetstate.md`](044-compose-modal-bottom-sheet-state-and-sheetstate.md), [`033-compose-scroll-state-and-vertical-scroll.md`](033-compose-scroll-state-and-vertical-scroll.md)

  **그래서 정확한 규칙은 "상태 객체를 넘기지 마라"가 아니라 "날것의 `MutableState<T>`를 넘기지 마라"다.**

- **받은 답변의 코드 불일치 하나.** "부모도 이렇게 바꿀 수 있어"라며 제시한 첫 번째 부모 코드가 실제로는 `MutableState` 버전이 아니라 이미 hoisting이 끝난 `Keypad(roomCode = ..., onRoomCodeChange = ...)`를 부르고 있다. 단순 실수로 보이지만, 그대로 따라 쓰면 컴파일되지 않는다.

- **"`delete`를 enum이나 sealed class로"** → **맞다.** 덧붙이면 지금 코드에는 **길이 제한 버그**도 있다. `roomCode += item`이 4자리를 넘어도 계속 붙고, 빈 문자열 버튼 `""`과 `"delete"`도 문자 그대로 붙는다.

- **`LazyVerticalGrid` 선택** → **이 경우 과하다.** 항목이 12개로 고정이고 스크롤되지 않는다. 지연 레이아웃은 화면에 보이지 않는 항목을 만들지 않으려는 장치라서, 전부 보이는 격자에는 이점이 없고 오버헤드만 남는다.

  > "If you need to display a large number of items (or a list of an unknown length), using a layout such as `Column` can cause performance issues, since all the items will be composed and laid out whether or not they are visible."
  >
  > — [Android Developers: Lists and grids](https://developer.android.com/develop/ui/compose/lists)

  **반대로 읽으면, 항목 수가 적고 전부 보이면 `Column` + `Row`로 충분하다는 뜻이다.**

## 공부할 내용

### 상태 호이스팅(state hoisting)이란

**상태를 자식에게서 꺼내 호출자에게 올리는 것**이다. 상태 변수 하나를 파라미터 두 개로 바꾼다.

```kotlin
// Before — 자식이 상태를 소유한다 (stateful)
@Composable
fun Keypad() {
    var roomCode by remember { mutableStateOf("") }   // 부모가 값을 볼 방법이 없다
}

// After — 호출자가 소유한다 (stateless)
@Composable
fun Keypad(
    roomCode: String,                     // 값은 아래로
    onRoomCodeChange: (String) -> Unit,   // 이벤트는 위로
)
```

**상태는 내려가고 이벤트는 올라간다.** 이 흐름을 단방향 데이터 흐름(unidirectional data flow, UDF)이라고 부른다.

```
        EnterRoomScreen  (roomCode 소유)
              │   ▲
   roomCode   │   │   onRoomCodeChange("1")
   (상태)     ▼   │   (이벤트)
           Keypad
```

### 공식 문서가 말하는 다섯 가지 이점

공식 문서가 나열한 항목을 그대로 옮기고, 이 코드에 대입해 본다.

| 이점 | 뜻 | 이 코드에서는 |
| --- | --- | --- |
| **Single source of truth** | 상태를 복제하지 않고 옮기므로 진실은 하나 | `roomCode`는 `EnterRoomScreen`에만 있다 |
| **Encapsulated** | 상태를 가진 컴포저블만 상태를 바꾼다 | 키패드가 몰래 4자리를 넘기지 못한다 |
| **Shareable** | 올린 상태를 여러 컴포저블이 함께 읽는다 | 자릿수 `Text` 4개와 "통화 시작" 버튼이 같은 값을 본다 |
| **Interceptable** | 호출자가 이벤트를 **무시하거나 고칠 수 있다** | 4자리 초과 입력을 부모가 막을 수 있다 |
| **Decoupled** | 상태를 어디에 두든 자식은 모른다 | 나중에 `ViewModel`로 옮겨도 `Keypad`는 그대로 |

**네 번째 Interceptable이 이 코드에서 당장 값어치가 있다.**

```kotlin
// 부모가 정책을 강제한다. 자식은 이 규칙을 몰라도 된다
Keypad(
    onKeyPress = { key ->
        when (key) {
            "delete" -> roomCode = roomCode.dropLast(1)
            ""       -> Unit
            else     -> if (roomCode.length < 4) roomCode += key    // ← 여기서 막는다
        }
    }
)
```

`MutableState`를 넘겼다면 이 검증을 걸 자리가 없다. **자식이 `roomCode.value += item`을 직접 하는 순간 부모는 통제권을 잃는다.**

### `by` 위임이 실제로 하는 일

받은 답변의 `var roomCode by roomCodeState`가 왜 동작하는지 짚고 넘어가자.

```kotlin
var roomCode by roomCodeState
// 컴파일되면
// 읽기: roomCodeState.getValue(thisRef, property)  →  roomCodeState.value
// 쓰기: roomCodeState.setValue(thisRef, property, v) → roomCodeState.value = v
```

**`by`는 문법 설탕일 뿐 구조를 바꾸지 않는다.** `.value`를 안 써도 되게 해 줄 뿐, 자식이 상태를 직접 쓴다는 사실은 그대로다. 그래서 ②는 ①보다 **읽기 좋아졌을 뿐 설계가 나아진 것은 아니다.** 이 구분이 중요하다.

### 네 가지 방식의 장단점

질문의 핵심이다. 전부 비교한다.

#### ① `MutableState<T>`를 그대로 넘긴다

```kotlin
@Composable fun Keypad(roomCode: MutableState<String>)
```

| | |
| --- | --- |
| 👍 | 파라미터가 하나라 짧다 |
| 👍 | 부모 코드가 `Keypad(roomCodeState)`로 끝난다 |
| 👎 | **캡슐화가 깨진다.** 자식이 무엇이든 대입할 수 있고 부모는 막을 수 없다 |
| 👎 | **검증·가로채기가 불가능하다.** 4자리 제한 같은 정책을 걸 자리가 없다 |
| 👎 | **읽기 전용 화면에 재사용할 수 없다.** 항상 쓰기 가능한 상태를 요구한다 |
| 👎 | **`ViewModel`로 옮기기 어렵다.** `StateFlow`는 `MutableState`가 아니다 |
| 👎 | **프리뷰와 테스트가 번거롭다.** 호출할 때마다 `mutableStateOf()`를 만들어야 한다 |
| 👎 | 파라미터 이름만 보고 "자식이 이 값을 바꾸는구나"를 알 수 없다 |

#### ② `MutableState<T>` + `by` 위임

```kotlin
@Composable fun Keypad(roomCodeState: MutableState<String>) { var roomCode by roomCodeState }
```

| | |
| --- | --- |
| 👍 | ①보다 본문이 읽기 좋다 (`.value`가 사라진다) |
| 👎 | **①의 단점이 하나도 해결되지 않는다.** 소유권 구조가 같다 |

#### ③ 값 + 콜백 (공식 표준)

```kotlin
@Composable fun Keypad(roomCode: String, onRoomCodeChange: (String) -> Unit)
```

| | |
| --- | --- |
| 👍 | **공식 권장 형태.** 다른 개발자가 시그니처만 보고 의도를 안다 |
| 👍 | 위 표의 다섯 가지 이점을 전부 얻는다 |
| 👍 | **프리뷰가 쉽다.** `Keypad("12", {})` 한 줄이면 된다 |
| 👍 | 상태 소유자를 컴포저블 → `ViewModel`로 바꿔도 자식은 그대로다 |
| 👎 | 파라미터가 늘어난다 |
| 👎 | 상태가 많아지면 파라미터 목록이 길어진다 (→ ⑤로 해결) |
| 👎 | **이 코드에서는 자식이 쓰지도 않는 값을 받는다** |

#### ④ 이벤트 콜백만 (이 코드에 가장 맞는 형태)

```kotlin
@Composable fun Keypad(onKeyPress: (String) -> Unit)
```

| | |
| --- | --- |
| 👍 | **자식이 알 필요 없는 것을 모른다.** 키패드는 "어떤 키를 눌렀다"만 알린다 |
| 👍 | **재사용성이 가장 높다.** 방 번호든 비밀번호든 전화번호든 같은 키패드를 쓴다 |
| 👍 | 부모가 문자열 조립 정책(길이 제한, 삭제, 빈 키)을 전부 소유한다 |
| 👍 | **재구성 범위가 좁아진다.** 아래에서 설명한다 |
| 👎 | 자식 UI가 값에 의존한다면(예: 4자리가 차면 버튼 비활성) 값을 따로 받아야 한다 |

#### ⑤ 호이스팅된 상태 타입 (`@Stable` 인터페이스)

```kotlin
@Stable
interface RoomCodeState {
    val code: String
    val isComplete: Boolean
    fun append(digit: Char)
    fun deleteLast()
}
```

| | |
| --- | --- |
| 👍 | 파라미터 폭발을 막는다. 서로 얽힌 상태를 한 덩어리로 다룬다 |
| 👍 | 정책(4자리 제한)을 상태 타입 안에 캡슐화한다 |
| 👍 | `ScrollState`, `LazyListState`, `SheetState`가 쓰는 방식이다 |
| 👎 | **작은 컴포넌트에는 과하다.** 설계와 유지 비용이 든다 |
| 👎 | 프리뷰에서 가짜 구현이 필요하다 |

**판단 기준 한 줄.**

> 파라미터 2~3개로 끝나면 ③·④, 서로 얽힌 상태가 4개를 넘으면 ⑤.

### 재구성 관점에서의 차이

③과 ④의 차이가 여기서 드러난다.

```kotlin
// ③ 값을 받는다 → roomCode 가 바뀔 때마다 Keypad 가 재구성 대상이 된다
Keypad(roomCode = roomCode, onRoomCodeChange = { roomCode = it })

// ④ 값을 안 받는다 → 람다만 넘긴다
Keypad(onKeyPress = { key -> roomCode = ... })
```

④에서 넘기는 람다는 `roomCode`라는 **문자열 값을 캡처하지 않는다.** `roomCode`가 `by` 위임이므로 람다 본문은 `MutableState` 객체를 통해 값을 읽고 쓴다. **그 상태 객체의 정체성(identity)은 절대 바뀌지 않으므로** Compose가 람다를 메모이즈해 두고, `roomCode`가 바뀌어도 `Keypad`는 **재구성을 건너뛴다.**

```
③ roomCode 변경 → Keypad 파라미터 변경 → Keypad 본문 재실행
④ roomCode 변경 → Keypad 파라미터 동일   → Keypad 건너뜀(skip)
```

**정직하게 말하면 버튼 12개짜리 키패드에서 이 차이는 체감되지 않는다.** 하지만 같은 원리가 스크롤 오프셋처럼 **1초에 수십 번 바뀌는 값**에 적용되면 결정적이다. 공식 성능 문서가 든 예가 정확히 그 경우다. → [Follow best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)

성능을 빼더라도 ④를 고를 이유는 충분하다. **"키패드는 방 번호를 모른다"는 설계가 그 자체로 옳기 때문이다.**

### 상태를 어디까지 올릴 것인가

무조건 위로 올리면 되는 것이 아니다. 공식 문서가 규칙 세 개를 준다.

> 1. State should be hoisted to at least the **lowest common parent** of all composables that use the state (read).
> 2. State should be hoisted to at least the **highest level it may be changed** (write).
> 3. If **two states change in response to the same events** they should be hoisted together.
>
> "You can hoist state higher than these rules require, but **underhoisting** state makes it difficult or impossible to follow unidirectional data flow."

그리고 별도 문서의 한 줄 요약이다.

> "You should hoist UI state to the **lowest common ancestor** between all the composables that read and write it. You should **keep state closest to where it is consumed**."
>
> — [Android Developers: Where to hoist state](https://developer.android.com/develop/ui/compose/state-hoisting)

`roomCode`를 읽는 곳은 자릿수 `Text` 4개와 "통화 시작" 버튼, 쓰는 곳은 `Keypad`다. **이 셋의 가장 낮은 공통 조상이 `EnterRoomScreen`이므로 지금 위치가 정확하다.** 더 올릴 이유가 없다.

### 올리지 않아도 되는 경우

공식 문서는 **호이스팅이 항상 필요한 것은 아니라고** 분명히 적는다.

> "Keeping UI element state internal to composable functions is acceptable. This is a good solution if the state and logic you apply to it is simple and other parts of the UI hierarchy don't need the state. For example, this is usually the case of animation state."

```kotlin
// 올릴 필요 없다: 이 컴포저블 밖에서 아무도 안 본다
@Composable
fun ChatBubble(message: Message) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
}
```

**애니메이션 상태, 펼침/접힘, 포커스처럼 바깥에서 아무도 안 보는 상태는 안에 두는 것이 맞다.**

### stateful / stateless 두 벌로 내보내기

재사용 컴포넌트를 만든다면 공식 문서가 권하는 방식이 있다.

> "As you develop reusable composables, you often want to expose **both a stateful and a stateless version** of the same composable."

```kotlin
// stateful — 간편하게 쓰고 싶은 호출자용
@Composable
fun Keypad(modifier: Modifier = Modifier) {
    var code by rememberSaveable { mutableStateOf("") }
    Keypad(code = code, onKeyPress = { ... }, modifier = modifier)
}

// stateless — 상태를 통제해야 하는 호출자용
@Composable
fun Keypad(code: String, onKeyPress: (String) -> Unit, modifier: Modifier = Modifier)
```

### 정리한 최종 코드

```kotlin
enum class KeypadKey(val label: String) {
    One("1"), Two("2"), Three("3"),
    Four("4"), Five("5"), Six("6"),
    Seven("7"), Eight("8"), Nine("9"),
    Empty(""), Zero("0"), Delete("⌫"),
}

@Composable
fun Keypad(
    onKeyPress: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 항목이 12개 고정이고 스크롤되지 않으므로 Lazy 가 필요 없다
    Column(modifier = modifier.testTag("keypad")) {
        KeypadKey.entries.chunked(3).forEach { row ->
            Row {
                row.forEach { key ->
                    if (key == KeypadKey.Empty) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Button(
                            onClick = { onKeyPress(key) },
                            modifier = Modifier.weight(1f).testTag("key_${key.label}"),
                        ) {
                            Text(key.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EnterRoomScreen() {
    // 회전해도 입력이 남도록 rememberSaveable 을 쓴다
    var roomCode by rememberSaveable { mutableStateOf("") }

    Column {
        repeat(ROOM_CODE_LENGTH) { index ->
            Text(
                text = roomCode.getOrNull(index)?.toString() ?: "",
                modifier = Modifier.testTag("room_code_digit_$index"),
            )
        }

        Keypad(
            onKeyPress = { key ->
                roomCode = when (key) {
                    KeypadKey.Delete -> roomCode.dropLast(1)
                    KeypadKey.Empty -> roomCode
                    // 정책은 부모가 소유한다 — 자식은 4자리 제한을 모른다
                    else -> if (roomCode.length < ROOM_CODE_LENGTH) roomCode + key.label else roomCode
                }
            }
        )

        Button(
            onClick = { },
            enabled = roomCode.length == ROOM_CODE_LENGTH,
        ) {
            Text("통화 시작")
        }
    }
}

private const val ROOM_CODE_LENGTH = 4
```

받은 답변 대비 바뀐 곳은 네 군데다.

1. `roomCode: String` 파라미터를 **없앴다.** 키패드는 방 번호를 모른다.
2. `String` 대신 **`enum`**으로 키를 모델링했다. `"delete"`가 문자열로 붙는 사고를 타입이 막는다.
3. **4자리 제한**을 부모에 넣었다. 호이스팅의 Interceptable 이점을 실제로 쓰는 자리다.
4. `remember` → **`rememberSaveable`**. 회전해도 입력이 살아남는다.
5. `LazyVerticalGrid` → `Column` + `Row`. 12개 고정 격자에 지연 레이아웃은 불필요하다.

## 관련 아키텍처와 베스트 프랙티스

### `ViewModel`까지 올릴 것인가

공식 문서는 **로직의 종류**로 가른다.

| 상태 | 어디에 두나 |
| --- | --- |
| UI 요소 상태 (펼침, 스크롤, 포커스, 애니메이션) | **컴포저블** |
| 화면 UI 상태 + 비즈니스 로직 (서버 조회, 검증, 저장) | **`ViewModel`** |

`roomCode` 입력 자체는 UI 상태라 컴포저블에 두는 것이 맞다. 다만 **"방 번호로 실제 통화방을 조회한다"가 들어오는 순간** `ViewModel`로 올라간다.

```kotlin
class EnterRoomViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EnterRoomUiState())
    val uiState: StateFlow<EnterRoomUiState> = _uiState.asStateFlow()

    fun onKeyPress(key: KeypadKey) { /* 정책 + 검증 */ }
    fun onStartCall() { viewModelScope.launch { /* 서버 조회 */ } }
}
```

이때도 **`Keypad`는 한 글자도 바뀌지 않는다.** 그것이 Decoupled의 값어치다. → [`047-android-viewmodel-state-exposure-patterns.md`](047-android-viewmodel-state-exposure-patterns.md), [`048-android-viewmodel-encapsulation-and-async-timing.md`](048-android-viewmodel-encapsulation-and-async-timing.md)

### 밖으로는 불변, 안으로는 가변

호이스팅과 같은 원리가 계층마다 반복된다.

> "From the state owner, expose to consumers **immutable state and events** to modify the state."

```kotlin
// ViewModel
private val _uiState = MutableStateFlow(...)      // 가변 — 안에서만
val uiState: StateFlow<...> = _uiState.asStateFlow()   // 불변 — 밖으로

// 컴포저블
var roomCode by remember { mutableStateOf("") }   // 가변 — 부모가 소유
Keypad(onKeyPress = { ... })                      // 이벤트 — 자식에게
```

**`MutableState`를 자식에게 넘기는 것은 `MutableStateFlow`를 화면에 그대로 노출하는 것과 똑같은 실수다.** 계층만 다를 뿐 구조가 같다.

### 콜백 이름 짓기

| 상황 | 이름 |
| --- | --- |
| 값 변경 요청 | `onValueChange`, `onRoomCodeChange` |
| 구체적 사용자 행동 | `onKeyPress`, `onStartCallClick` |
| 완료 알림 | `onDismissRequest`, `onNavigateBack` |

**`on~Change`는 "이 값으로 바꿔 달라는 요청"이고, 실제로 바꿀지는 부모가 정한다.** 이름이 `onValueChanged`(과거형)가 아니라 `onValueChange`(현재형)인 이유가 여기 있다. 이미 바뀐 사실을 통보하는 것이 아니라 요청이기 때문이다.

### 프리뷰와 테스트에서 드러나는 차이

호이스팅의 실익이 가장 눈에 보이는 자리다.

```kotlin
// stateless 라서 상태별 프리뷰를 마음껏 만든다
@Preview @Composable
private fun KeypadPreview() = Keypad(onKeyPress = {})

@Preview @Composable
private fun EnterRoomScreenFilledPreview() = EnterRoomScreenContent(roomCode = "1234", onKeyPress = {})

@Preview @Composable
private fun EnterRoomScreenEmptyPreview() = EnterRoomScreenContent(roomCode = "", onKeyPress = {})
```

테스트에서도 콜백이 불렸는지만 확인하면 된다.

```kotlin
@Test
fun 키를_누르면_콜백이_불린다() {
    val pressed = mutableListOf<KeypadKey>()
    composeTestRule.setContent { Keypad(onKeyPress = { pressed += it }) }

    composeTestRule.onNodeWithTag("key_1").performClick()

    assertEquals(listOf(KeypadKey.One), pressed)
}
```

`MutableState`를 넘기는 구조였다면 테스트마다 상태 객체를 만들어 주입하고, 검증도 그 객체를 들여다봐야 한다.

### 프롭 드릴링이 깊어지면

값과 콜백을 3단계 이상 내려보내야 한다면 구조를 의심할 때다. → [`070-compose-navigation-prop-drilling.md`](070-compose-navigation-prop-drilling.md)

- 중간 컴포저블을 **슬롯(slot)** 형태로 바꿔 자식을 통째로 받게 한다
- 서로 얽힌 상태를 **호이스팅된 상태 타입**으로 묶는다 (위 ⑤)
- 정말 전역적인 값(테마, 로케일)이라면 `CompositionLocal`을 쓴다. 다만 **암묵적 의존이 생기므로 마지막 수단이다**

## 체크리스트

- [ ] 상태 호이스팅이 "상태 변수 하나를 `value` + `onValueChange` 두 파라미터로 바꾸는 것"임을 설명할 수 있다.
- [ ] 단방향 데이터 흐름에서 상태와 이벤트가 각각 어느 방향인지 말할 수 있다.
- [ ] 공식 문서가 말하는 다섯 가지 이점을 나열할 수 있다.
- [ ] `MutableState`를 자식에게 넘기면 Interceptable 이점을 잃는다는 것을 예로 들 수 있다.
- [ ] `by` 위임이 소유권 구조를 바꾸지 않는다는 것을 설명할 수 있다.
- [ ] 자식이 값을 읽지 않는다면 값 파라미터를 빼는 편이 나은 이유를 설명할 수 있다.
- [ ] 람다가 `MutableState` 객체를 캡처해 재구성을 건너뛸 수 있는 원리를 설명할 수 있다.
- [ ] 상태를 어디에 올릴지 정하는 규칙 세 가지를 말할 수 있다.
- [ ] 호이스팅하지 않아도 되는 상태의 예를 들 수 있다.
- [ ] stateful/stateless 두 벌을 내보내는 패턴을 안다.
- [ ] 파라미터가 많아졌을 때 호이스팅된 상태 타입으로 묶는 판단 기준을 안다.
- [ ] `remember`와 `rememberSaveable`을 언제 나눠 쓰는지 안다.
- [ ] `MutableState`를 넘기는 것과 `MutableStateFlow`를 노출하는 것이 같은 실수인 이유를 설명할 수 있다.
- [ ] 항목이 적고 스크롤이 없으면 지연 레이아웃이 불필요한 이유를 안다.

## 공식 참고 자료

- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: Where to hoist state](https://developer.android.com/develop/ui/compose/state-hoisting)
- [Android Developers: Architecting your Compose UI](https://developer.android.com/develop/ui/compose/architecture)
- [Android Developers: Follow best practices (performance)](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Android Developers: Lists and grids](https://developer.android.com/develop/ui/compose/lists)
- [Android Developers: Thinking in Compose](https://developer.android.com/develop/ui/compose/mental-model)
- [Android Developers: Testing your Compose layout](https://developer.android.com/develop/ui/compose/testing)
- [Android Developers: UI layer](https://developer.android.com/topic/architecture/ui-layer)
- [Compose API guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md)
