# `remember`가 만든 상태는 어디에 저장되는가

## 질문이 나온 코드

- [`chapter092/app/src/main/java/com/example/chapter_092/MainActivity.kt`](../chapter092/app/src/main/java/com/example/chapter_092/MainActivity.kt)
- 질문: `remember`에서 `mutableStateOf`를 호출하면 어딘가에 저장되는가? 메모리의 어떤 영역인가? JPA의 영속성 컨텍스트처럼 캐싱이나 변경 전파를 효율적으로 만드는 아키텍처가 적용되어 있는가?

## 질문 전제 점검

- **"어딘가에 저장되는 건가?"** → 맞다. 특별한 저장소가 아니라 **컴포지션(Composition)이 들고 있는 일반 JVM 객체**다. 문서 표현 그대로 "`remember`로 계산한 값은 초기 컴포지션 시 컴포지션에 저장되고, 재구성 시 저장된 값이 반환된다."
- **"메모리 어디인가겠지만"** → 힙이다. 정확히 말하면 특별한 영역이 아니라, 컴포지션 객체가 참조를 들고 있어 GC 대상이 되지 않을 뿐이다. 컴포저블이 컴포지션에서 제거되면 참조가 끊기고 일반 객체처럼 회수된다.
- **"JPA의 영속성 컨텍스트처럼 ... 아키텍처가 적용되어 있나?"** → 직관이 상당히 정확하다. 이름은 다르지만 **스냅샷(Snapshot) 시스템**이라는 대응 구조가 실제로 있다. 다만 JPA의 더티 체킹과는 변경을 감지하는 방식이 다르다. 아래에서 대응 관계와 차이를 정리한다.
- 한 가지만 정정하면, `remember`가 저장하는 위치와 `mutableStateOf`가 값 변경을 전파하는 구조는 **서로 다른 두 메커니즘**이다. 하나로 묶어 보면 헷갈린다. `remember`는 "재구성 사이에 값을 유지"하고, `mutableStateOf`는 "값이 바뀌면 알린다".

## 공부할 내용

### 두 메커니즘 분리해서 보기

```kotlin
var treasuresFound by remember { mutableIntStateOf(0) }
//                    ───┬───    ────────┬────────
//                       │               └ 관찰 가능한 상태 객체를 만든다 (변경 전파)
//                       └ 그 객체를 컴포지션에 보관한다 (재구성 간 유지)
```

`remember`가 없으면 재구성마다 새 상태 객체가 만들어져 값이 0으로 돌아간다. `mutableStateOf`가 없으면 값은 유지되지만 바뀌어도 아무도 모른다. 둘은 서로를 대체하지 않는다.

### `remember`의 저장 위치

컴포지션은 "Compose가 컴포저블을 실행해 만든 UI 기술"이며 트리 구조다. `remember`는 이 트리의 **해당 호출 위치에 값을 매달아 둔다**. 그래서 다음 성질이 따라온다.

- 같은 호출 위치에서 다시 실행되면 저장된 값이 그대로 반환된다.
- 조건문 때문에 그 컴포저블이 호출되지 않게 되면 값은 잊힌다. 문서는 "`remember`는 컴포지션에 객체를 저장하며, `remember`를 호출한 컴포저블이 컴포지션에서 제거되면 그 객체를 잊는다"고 명시한다.
- 컴포지션 자체는 `ComposeView`가 소유하므로, 액티비티가 소멸되면 컴포지션도 함께 사라진다. 화면 회전으로 상태가 사라지는 이유가 이것이다.

호출 위치로 값을 식별한다는 점에서, 컴포지션은 "함수 호출 트리에 대응하는 슬롯에 값을 채워 넣는 구조"로 이해하면 된다. 런타임 내부에서는 이를 슬롯 테이블이라 부르지만 공개 API는 아니므로, 앱 코드에서는 "컴포지션이 호출 위치별로 값을 보관한다"까지만 알면 충분하다.

객체가 정리 작업을 해야 한다면 `RememberObserver`를 구현해 컴포지션 진입·이탈 시점을 통지받을 수 있다.

### 변경 전파: 스냅샷 시스템

`mutableStateOf`가 만드는 `MutableState`는 스냅샷 시스템 위에서 동작한다. 공식 API 문서의 설명이 핵심을 담고 있다.

> `Snapshot`은 "가변 상태와 다른 상태 객체가 반환하는 값들의 스냅샷"이다. "모든 상태 객체는 스냅샷 안에서 명시적으로 변경되지 않는 한, 스냅샷이 생성될 때 가지고 있던 값과 같은 값을 갖는다."

동작 방식을 요약하면 이렇다.

1. **읽기 관찰**: 컴포지션은 상태 읽기를 관찰한다. 어떤 스코프가 어떤 상태를 읽었는지 기록된다.
2. **격리된 변경**: 상태 변경은 스냅샷 안에서 이루어진다. 스냅샷은 스레드 로컬로 격리되며 "다른 모든 스레드는 영향을 받지 않는다."
3. **적용(apply)**: 변경이 적용되면 전역 상태에 반영되고, 등록된 관찰자에게 알림이 전달된다.
4. **무효화**: 그 상태를 읽었던 스코프가 무효화되고 다음 프레임에 재구성이 예약된다.

각 상태 객체는 값 하나가 아니라 **버전이 붙은 레코드들**을 갖는다. 읽는 쪽은 자신의 스냅샷에서 유효한 레코드를 본다. 데이터베이스의 다중 버전 동시성 제어(MVCC)와 같은 발상이다.

### JPA 영속성 컨텍스트와 나란히 보기

| JPA | Compose | 비고 |
| --- | --- | --- |
| 영속성 컨텍스트(1차 캐시) | 컴포지션 | 식별자별로 객체를 보관 |
| 엔티티 식별자(`@Id`) | 컴포저블 호출 위치(+ `key`) | 같은 것을 같다고 판단하는 기준 |
| 트랜잭션 | 스냅샷 | 격리된 작업 단위 |
| `flush`/`commit` | 스냅샷 `apply` | 변경을 바깥에 반영 |
| 더티 체킹(스냅샷 비교) | 읽기·쓰기 관찰 | 변경 감지 방식이 다르다 |
| 변경 감지 → SQL 발행 | 무효화 → 재구성 예약 | 반영 대상이 DB냐 UI냐의 차이 |
| 준영속(detached) | 컴포지션에서 제거됨 | 더 이상 추적되지 않음 |

가장 큰 차이는 **변경 감지 방식**이다. JPA는 트랜잭션 종료 시점에 최초 스냅샷과 현재 값을 비교해 바뀐 필드를 찾는다. Compose는 비교하지 않고, 관찰 가능한 상태 객체가 쓰기 시점에 스스로 알린다. 그래서 Compose에서는 `mutableStateOf`로 감싸지 않은 일반 변수의 변경이 절대 감지되지 않는다. JPA에서 엔티티가 아닌 객체를 고쳐도 DB에 반영되지 않는 것과 같은 이치다.

또 하나의 차이는 시점이다. JPA는 트랜잭션 커밋 시 한 번에 반영하지만, Compose는 프레임 단위로 반영한다. 한 프레임 안의 여러 변경은 합쳐져 한 번의 재구성이 된다.

### 어떤 상태 API를 쓸 것인가

```kotlin
mutableStateOf(0)        // 제네릭. Int를 박싱한다
mutableIntStateOf(0)     // Int 전용. 박싱을 피한다
mutableStateListOf<T>()  // 리스트 자체의 변경을 관찰 가능하게
mutableStateMapOf<K,V>() // 맵 버전
```

`chapter092`가 `mutableIntStateOf`를 쓴 것은 적절하다. 원시 타입 전용 API는 박싱을 피해 불필요한 할당을 줄인다. 반대로 일반 `mutableStateOf(0)`에 `Int`를 담으면 값이 바뀔 때마다 박싱 객체가 생긴다.

주의할 점은 `mutableStateOf(listOf(...))`처럼 **불변 컬렉션을 상태에 담는 경우**다. 리스트 내용을 바꾸려면 새 리스트를 대입해야 한다. 리스트에 항목을 추가·삭제하는 방식으로 쓰려면 `mutableStateListOf`가 필요하다.

## 관련 아키텍처와 베스트 프랙티스

### 상태의 수명은 저장 위치가 결정한다

| 저장 위치 | 살아남는 범위 | 사라지는 시점 |
| --- | --- | --- |
| 지역 변수 | 없음 | 함수 실행이 끝나면 |
| `remember` | 컴포지션에 있는 동안 | 컴포저블이 컴포지션에서 제거될 때, 구성 변경 시 |
| `rememberSaveable` | 구성 변경, 프로세스 종료 | 사용자가 앱을 명시적으로 종료할 때 |
| `ViewModel` | 화면 수명 | `ViewModel` 스코프가 끝날 때 |
| DataStore, Room | 앱 재설치 전까지 | 명시적으로 지울 때 |

"어디에 저장되는가"라는 질문은 결국 "언제까지 살아 있어야 하는가"와 같은 질문이다.

### 상태 객체 자체를 넘길 것인가, 값을 넘길 것인가

`chapter092`의 `DirectionButton`은 `MutableState<String>`을 그대로 받는다. 동작은 하지만 권장 방식은 아니다.

```kotlin
// 현재: 자식이 부모의 상태를 직접 쓴다
fun DirectionButton(direction: String, directionState: MutableState<String>, ...)

// 권장: 값을 내려주고 이벤트를 올려받는다
fun DirectionButton(direction: String, onClick: () -> Unit)
```

상태 객체를 넘기면 자식이 부모의 상태를 임의로 바꿀 수 있어 변경 지점이 흩어진다. 값과 콜백으로 나누면 상태 변경이 한곳에 모이고, 자식은 상태 타입을 몰라도 되므로 재사용과 프리뷰가 쉬워진다. 이것이 상태 호이스팅과 단방향 데이터 흐름의 실천 형태다.

### 값 타입을 넘길 때 주의할 점

`Int`처럼 불변 값을 넘기면 자식은 **그 시점의 값 복사본**을 받는다. 자식에서 그 값을 증가시켜도 부모 상태는 바뀌지 않는다. 상태를 바꾸려면 상태 객체를 참조하거나 콜백으로 부모에게 요청해야 한다. 코틀린의 `Int.inc()`가 값을 변경하지 않고 새 값을 반환하는 함수라는 점까지 겹치면 더 헷갈리기 쉽다.

```kotlin
var count = 0
count.inc()      // 반환값을 버렸으므로 count는 그대로 0
count = count.inc()  // 또는 count++
```

### 상태를 최소한으로 유지하기

파생 가능한 값은 상태로 만들지 않는다. 상태가 둘이면 둘이 어긋날 가능성이 생긴다.

```kotlin
// 나쁨: 두 상태가 불일치할 수 있다
var items by remember { mutableStateOf(emptyList<Item>()) }
var itemCount by remember { mutableStateOf(0) }

// 좋음: 계산으로 얻는다
val itemCount = items.size
```

계산 비용이 크고 자주 읽힌다면 `derivedStateOf`로 감싸 결과가 실제로 달라질 때만 재구성되게 한다.

## 체크리스트

- [ ] `remember`와 `mutableStateOf`가 서로 다른 문제를 푼다는 점을 설명할 수 있다.
- [ ] `remember`가 값을 어디에, 어떤 기준으로 보관하는지 설명할 수 있다.
- [ ] 저장된 값이 언제 잊히는지 설명할 수 있다.
- [ ] 스냅샷 시스템이 변경을 전파하는 과정을 순서대로 설명할 수 있다.
- [ ] JPA 영속성 컨텍스트와의 유사점과 차이점을 각각 말할 수 있다.
- [ ] `mutableStateOf`와 `mutableIntStateOf`의 차이를 설명할 수 있다.
- [ ] 상태 객체를 넘기는 것과 값+콜백을 넘기는 것의 차이를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: Lifecycle of composables](https://developer.android.com/develop/ui/compose/lifecycle)
- [Android Developers API: Snapshot](https://developer.android.com/reference/kotlin/androidx/compose/runtime/snapshots/Snapshot)
- [Android Developers API: RememberObserver](https://developer.android.com/reference/kotlin/androidx/compose/runtime/RememberObserver)
- [Android Developers: Where to hoist state](https://developer.android.com/develop/ui/compose/state-hoisting)
