# `Repository` 호출이 없어도 동작하는데 왜 필요한가

## 질문이 나온 코드

- [`chapter127/app/src/main/java/com/example/chapter_127/CounterViewModel.kt`](../chapter127/app/src/main/java/com/example/chapter_127/CounterViewModel.kt)
- 질문: `repository.incrementCounter()` 호출은 주석 처리해도 동작하는데, 굳이 필요한 코드인가?

## 질문 전제 점검

- **"없어도 동작한다"** → 지금 코드에서는 맞다. UI가 읽는 값은 `ViewModel`의 `_count` 하나뿐이고 저장소를 읽는 사람이 아무도 없기 때문이다. 즉 저장소 호출이 "불필요"한 것이 아니라, **저장소가 이미 아무 역할도 하지 않는 상태**다.
- 하지만 그 상태 자체가 문제다. 지금은 진실 공급원이 둘로 갈라져 있다. `CounterRepository._counter.count`와 `CounterViewModel._count`가 서로 다른 값을 가질 수 있고, 실제로 주석 처리하는 순간 곧바로 어긋난다.
- **"굳이 필요한 코드인가"** → 질문을 뒤집는 편이 낫다. 저장소를 지우고 `ViewModel`만 남기거나, 저장소를 진실 공급원으로 삼고 `ViewModel`이 그것을 반영하거나 **둘 중 하나를 골라야 한다**. 지금처럼 저장소를 두고 쓰지 않는 상태가 가장 나쁘다. 읽는 사람이 구조를 오해하게 만든다.
- 한 가지 더, 저장소 호출을 되살려도 지금 구조로는 여전히 부족하다. `CounterModel(var count: Int)`은 관찰 가능한 타입이 아니라서, 저장소 값이 바뀌어도 UI는 알 수 없다.

## 공부할 내용

### 지금 구조에서 값이 흐르는 경로

```kotlin
class CounterRepository {
    private var _counter = CounterModel(0)
    fun getCounter() = _counter
    fun incrementCounter() { _counter.count++ }
}

class CounterViewModel(private val repository: CounterRepository) : ViewModel() {
    private val _count = mutableStateOf(repository.getCounter().count)   // 최초 1회만 읽는다
    fun increment() {
        _count.value++              // UI가 보는 값
        repository.incrementCounter()   // 저장소가 가진 값
    }
}
```

`repository.getCounter().count`는 `ViewModel`이 만들어질 때 딱 한 번 읽힌다. 이후에는 두 값이 각자 움직인다. 두 줄을 모두 실행하면 우연히 같은 값을 유지하지만, 이것은 **두 벌의 상태를 손으로 동기화**하고 있는 것이다. 한쪽만 빠뜨리면 즉시 어긋난다.

### 단일 진실 공급원

공식 아키텍처 가이드의 원칙은 분명하다.

> "각 저장소가 단일 진실 공급원을 정의하는 것이 중요하다. 진실 공급원은 항상 일관되고 정확하며 최신인 데이터를 담는다. 실제로 저장소가 노출하는 데이터는 항상 진실 공급원에서 직접 오는 데이터여야 한다."

이 원칙을 적용하면 값은 한 곳에만 있어야 하고, `ViewModel`은 그 값을 **복사해 두는 것이 아니라 구독**해야 한다.

```kotlin
class CounterRepository {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()   // 진실 공급원

    fun increment() { _count.update { it + 1 } }
    fun decrement() { _count.update { it - 1 } }
}

class CounterViewModel(private val repository: CounterRepository) : ViewModel() {
    val count: StateFlow<Int> = repository.count
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun increment() = repository.increment()
    fun decrement() = repository.decrement()
}
```

이제 `ViewModel`은 값을 따로 들고 있지 않는다. 동기화할 것이 없으니 어긋날 수도 없다. 질문의 "굳이 필요한가"에 대한 답이 여기서 나온다. 저장소 호출 한 줄이 필요한 것이 아니라, **값의 소유자를 하나로 정하는 설계**가 필요한 것이다.

### 저장소가 하는 일

문서가 정리한 저장소의 책임은 다섯 가지다.

- 앱의 나머지 부분에 데이터를 노출한다.
- 데이터 변경을 한곳으로 모은다.
- 여러 데이터 소스 간 충돌을 해결한다.
- 데이터 소스를 나머지 코드로부터 추상화한다.
- 비즈니스 로직을 담는다.

카운터 예제에서는 과해 보인다. 값 하나뿐이고 데이터 소스도 없기 때문이다. 하지만 다음 요구가 하나라도 생기면 이야기가 달라진다.

- 카운트를 앱 재실행 후에도 유지해야 한다 → DataStore
- 서버와 동기화해야 한다 → 네트워크 데이터 소스
- 다른 화면에서도 같은 카운트를 보여줘야 한다 → 공유 지점 필요
- 카운트에 상한이나 규칙이 생긴다 → 비즈니스 로직의 자리

그때 `ViewModel`마다 값을 따로 들고 있으면 화면 수만큼 진실이 생긴다.

### 레이어 접근 규칙

> "계층의 다른 레이어는 데이터 소스에 직접 접근해서는 안 된다. 데이터 레이어의 진입점은 항상 저장소 클래스다."

그리고 "이 레이어가 노출하는 데이터는 변경 불가능해야 한다. 변조와 스레딩 문제를 방지하기 위해서다." 현재 `getCounter()`가 가변 객체 `CounterModel`을 그대로 반환하는 것은 이 지침과 어긋난다. 호출자가 `getCounter().count = 100`으로 저장소 내부를 바꿀 수 있다.

## 관련 아키텍처와 베스트 프랙티스

### 레이어별 책임

```
UI (컴포저블)      화면에 그리고 이벤트를 올린다. 상태를 소유하지 않는다
   ↓ 상태          ↑ 이벤트
ViewModel          화면 상태를 만들고 노출한다. UI 로직을 담는다
   ↓ 요청          ↑ 데이터 스트림
Repository         데이터의 단일 진실 공급원. 데이터 소스를 추상화한다
   ↓
DataSource         DB, 네트워크, DataStore
```

각 화살표가 한 방향이라는 점이 핵심이다. 위 레이어는 아래를 알지만 아래는 위를 모른다. 그래서 저장소와 데이터 레이어는 UI 없이 단위 테스트할 수 있다.

### 예제 수준에서의 현실적인 판단

학습 예제에서 레이어를 다 갖추는 것이 항상 옳지는 않다. 판단 기준을 두면 이렇다.

- 값이 화면 하나에서만 쓰이고 저장할 필요도 없다 → `ViewModel`까지만. 저장소를 만들지 않는다.
- 값이 여러 화면에서 공유되거나 저장·동기화가 필요하다 → 저장소를 만들고 진실 공급원을 그쪽에 둔다.

지금 코드는 "저장소를 만들어 두고 쓰지 않는" 중간 상태다. 강의 흐름상 이후에 저장소를 활용하는 단계로 이어진다면 남겨두되, 진실 공급원을 어느 쪽에 둘지는 지금 정해 두는 편이 좋다.

### 가변 모델을 노출하지 않기

```kotlin
// 위험: 호출자가 내부 상태를 바꿀 수 있다
class CounterModel(var count: Int)
fun getCounter() = _counter

// 안전: 불변 값 또는 스트림으로 노출한다
data class CounterState(val count: Int)
val count: StateFlow<Int>
```

불변 데이터로 노출하면 변경 경로가 저장소의 공개 함수로 한정되고, Compose의 재구성 판정에도 유리하다. 관련 내용은 [`compose-state-list-reference-change.md`](compose-state-list-reference-change.md)에 정리했다.

### 죽은 코드는 남기지 않는다

읽는 사람은 코드가 쓰이고 있다고 가정한다. 지금처럼 호출이 주석 처리된 저장소는 "이 저장소가 진실 공급원인가?"라는 오해를 만든다. 당장 쓰지 않기로 했다면 지우고, 곧 쓸 예정이라면 왜 비활성화되어 있는지 한 줄로 남긴다.

## 체크리스트

- [ ] 지금 코드에서 진실 공급원이 둘로 갈라져 있다는 점을 설명할 수 있다.
- [ ] 저장소 호출을 빼면 어떤 불일치가 생기는지 설명할 수 있다.
- [ ] 단일 진실 공급원 원칙을 설명할 수 있다.
- [ ] `ViewModel`이 값을 복사하지 않고 구독하는 형태로 바꿀 수 있다.
- [ ] 저장소의 다섯 가지 책임을 말할 수 있다.
- [ ] 데이터 레이어가 불변 데이터를 노출해야 하는 이유를 설명할 수 있다.
- [ ] 예제 규모에서 저장소를 둘지 말지 판단할 기준을 세울 수 있다.

## 공식 참고 자료

- [Android Developers: Data layer](https://developer.android.com/topic/architecture/data-layer)
- [Android Developers: Guide to app architecture](https://developer.android.com/topic/architecture)
- [Android Developers: UI layer architecture](https://developer.android.com/topic/architecture/ui-layer)
- [Android Developers: Architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
