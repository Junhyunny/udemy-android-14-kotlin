# 재구성은 언제, 어디까지 일어나는가

## 질문이 나온 코드

- [`chapter092/app/src/main/java/com/example/chapter_092/MainActivity.kt`](../chapter092/app/src/main/java/com/example/chapter_092/MainActivity.kt)
- 질문: 상태가 변경되었을 때 어느 시점에 리컴포즈가 발생하는가? 부분 리컴포즈인가 전체 화면 리컴포즈인가? 리컴포즈가 일어나는 과정을 순서대로 알고 싶다.

## 질문 전제 점검

- **"리컴포즈(리렌더링)"** → 두 단어를 같은 것으로 두면 이후 설명이 어긋난다. 재구성(recomposition)은 **컴포저블 함수를 다시 실행해 UI 기술을 갱신하는 일**이고, 화면에 실제로 그리는 것은 그 뒤의 드로잉 단계다. 재구성 없이 다시 그려지는 경우도 있고(레이아웃·드로잉 단계에서만 상태를 읽은 경우), 재구성했지만 그릴 내용이 같아 화면이 그대로인 경우도 있다.
- **"부분 리컴포즈인지 전체 화면인지"** → 부분이다. Compose는 "입력이 바뀌지 않은 컴포저블은 재구성을 피한다." 다만 **자동으로 최소 범위가 되는 것은 아니고**, 상태를 어디에서 읽었느냐가 범위를 결정한다. 화면 최상단에서 읽으면 그 아래 전체가 후보가 된다.
- **"상태가 변경되었을 때"의 시점** → 상태를 바꾸는 그 줄에서 즉시 함수가 다시 실행되지는 않는다. 변경은 **기록되고 예약**되며, 실제 재구성은 다음 프레임에 일어난다.

## 공부할 내용

### 순서대로 보는 재구성 과정

1. **읽기 추적**: 컴포지션 중 컴포저블이 `State.value`를 읽으면, Compose는 "그 값을 읽었을 때 무엇을 하고 있었는지" 자동으로 기록한다. 어떤 스코프가 어떤 상태에 의존하는지가 이때 만들어진다.
2. **쓰기 발생**: 버튼 콜백 등에서 `state.value = x`로 값을 바꾼다. 이 시점에는 UI가 즉시 바뀌지 않는다. 값 변경이 스냅샷 시스템에 기록될 뿐이다.
3. **변경 알림**: 변경이 적용되면 그 상태를 읽었던 스코프들에 알림이 전달되고, 해당 스코프가 무효(invalid) 표시된다.
4. **재구성 예약**: 무효화된 스코프는 다음 프레임에 다시 실행되도록 예약된다. 한 프레임 안에서 같은 상태를 여러 번 바꿔도 재구성은 한 번으로 합쳐진다.
5. **재구성 실행**: 다음 프레임에서 무효화된 컴포저블만 다시 실행된다. 이때 입력이 바뀌지 않은 자식은 건너뛴다. "입력이 이전 컴포지션과 달라지지 않았다면 일부 컴포저블 함수는 실행이 완전히 생략될 수 있다."
6. **레이아웃 → 드로잉**: 변경된 노드에 대해 측정·배치와 그리기가 이어진다. 세 단계는 컴포지션 → 레이아웃 → 드로잉 순으로 한 방향으로 흐른다.

즉 "상태 변경 → 즉시 렌더링"이 아니라 **"상태 변경 → 무효화 → 다음 프레임에 필요한 만큼만 재실행"**이다.

### 범위는 무엇이 결정하는가

재구성 단위는 화면이 아니라 **재시작 가능한 컴포저블 호출**이다. 중요한 규칙은 하나다.

> 상태를 읽은 가장 가까운 스코프가 재구성된다.

공식 성능 문서의 예가 이를 잘 보여준다. 부모에서 `scroll.value`를 읽어 자식에게 넘기면 부모 전체가 재구성 대상이 되지만, 값을 람다로 넘겨 자식이 직접 읽게 하면 "스크롤 값이 바뀔 때 가장 가까운 재구성 스코프가 `Title` 컴포저블이 되어, Compose가 `Box` 전체를 재구성할 필요가 없어진다."

### `chapter092` 코드에 적용해 보기

```kotlin
@Composable
fun CaptainGame() {
    var treasuresFound by remember { mutableIntStateOf(0) }
    val direction = remember { mutableStateOf("North") }
    val stormOrTreasure = remember { mutableStateOf("") }

    Column(...) {
        Text(text = "Treasures Found: $treasuresFound")
        Text(text = "Current Direction: ${direction.value}")
        Text(text = stormOrTreasure.value)
        DirectionButton("East", direction, stormOrTreasure, treasuresFound)
        ...
    }
}
```

세 상태를 모두 `CaptainGame` 본문에서 읽고 있다. 따라서 어느 하나가 바뀌어도 재구성 후보는 `CaptainGame` 전체가 된다. 다만 그 안에서 모든 것이 다시 실행되지는 않는다.

- `direction.value`가 바뀌면 `CaptainGame`이 다시 실행되고, 문자열이 바뀐 `Text`는 갱신된다.
- `DirectionButton`은 전달되는 인자(`String`, `MutableState`, `Int`)가 그대로라면 건너뛸 수 있다. 문자열과 `MutableState`는 안정(stable) 타입이기 때문이다.
- 범위를 더 좁히려면 상태를 읽는 위치를 낮춘다. 예를 들어 `Text(text = "...")`를 감싼 별도 컴포저블에서 값을 읽게 하면 그 컴포저블만 재구성된다.

### 재구성에 대해 보장되지 않는 것

- 실행 순서: 컴포저블은 선언 순서대로 실행된다고 보장되지 않는다.
- 실행 횟수: 애니메이션 프레임마다 매우 자주 실행될 수 있다.
- 실행 여부: 건너뛸 수 있다.

그래서 컴포저블 본문은 빠르고, 멱등적이며, 부수 효과가 없어야 한다. 카운터 증가나 로깅처럼 "한 번만" 일어나야 하는 일은 본문이 아니라 이벤트 콜백이나 `LaunchedEffect` 안에서 한다.

## 관련 아키텍처와 베스트 프랙티스

### 상태 읽기를 낮은 곳으로 미루기

성능 문제가 확인되었을 때 가장 먼저 적용하는 기법이다. "상태 읽기를 미루면 Compose가 재구성 시 실행하는 코드를 최소로 유지할 수 있다."

```kotlin
// 이전: 부모가 값을 읽어 넘긴다 → 부모가 재구성된다
Header(count = counter.value)

// 이후: 자식이 필요할 때 읽는다 → 자식만 재구성된다
Header(count = { counter.value })
```

모디파이어에서도 같은 원리가 적용된다. 값이 빠르게 바뀌는 상태는 람다 버전 모디파이어(`Modifier.offset { }`, `Modifier.drawBehind { }`)로 읽어 컴포지션 단계를 아예 건너뛴다.

### 건너뛰기를 가능하게 하는 조건

Compose는 "모든 입력이 안정(stable)하고 바뀌지 않았을 때" 재구성을 건너뛴다. 비교는 `equals`로 한다.

- 원시 타입, `String`, 함수 타입, `MutableState`는 기본적으로 안정하다.
- `var` 프로퍼티를 가진 클래스나 `List` 같은 인터페이스 타입은 불안정으로 취급될 수 있다. UI 상태는 불변 `data class`와 불변 컬렉션으로 만든다.
- 직접 통제할 수 없는 타입에는 `@Stable`, `@Immutable`로 계약을 명시한다.

### 측정하고 나서 고치기

재구성 범위는 추측하지 말고 확인하는 편이 빠르다.

- **Layout Inspector**: 각 컴포저블의 재구성 횟수와 건너뛴 횟수를 보여준다. 조작하지 않은 영역의 카운트가 계속 오른다면 상태 읽기 위치를 의심한다.
- **Composition tracing**: 어떤 컴포저블이 얼마나 자주 재구성되는지 추적한다.

또한 성능은 반드시 **릴리스 빌드와 R8 최적화가 적용된 상태**에서 측정한다. 디버그 빌드의 성능 수치는 실제와 다르다.

### 상태 구조를 먼저 손보기

읽기 위치를 옮기는 것은 최적화이고, 그보다 앞서는 것은 상태를 적절한 크기로 나누는 일이다. 화면 전체가 하나의 거대한 상태 객체를 공유하면 필드 하나만 바뀌어도 넓은 범위가 무효화된다. 화면 단위 UI 상태를 두되, 자주 바뀌는 값(스크롤 위치, 입력 중인 텍스트)은 그것을 쓰는 컴포저블 가까이에 둔다.

## 체크리스트

- [ ] 재구성과 다시 그리기(드로잉)를 구분해 설명할 수 있다.
- [ ] 상태를 바꾼 시점과 재구성이 실행되는 시점이 다른 이유를 설명할 수 있다.
- [ ] 재구성이 예약되고 실행되기까지의 단계를 순서대로 말할 수 있다.
- [ ] 재구성 범위를 결정하는 것이 "상태를 읽은 위치"임을 설명할 수 있다.
- [ ] `chapter092` 코드에서 어느 범위가 재구성되는지 설명할 수 있다.
- [ ] Compose가 재구성을 건너뛰는 조건을 설명할 수 있다.
- [ ] 컴포저블 본문에 부수 효과를 두면 안 되는 이유를 재구성 보장 관점에서 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Lifecycle of composables](https://developer.android.com/develop/ui/compose/lifecycle)
- [Android Developers: Jetpack Compose phases](https://developer.android.com/develop/ui/compose/phases)
- [Android Developers: Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Android Developers: Stability in Compose](https://developer.android.com/develop/ui/compose/performance/stability)
- [Android Developers: Compose performance tooling](https://developer.android.com/develop/ui/compose/performance/tooling)
