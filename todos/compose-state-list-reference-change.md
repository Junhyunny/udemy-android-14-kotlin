# 새 리스트를 대입했는데 재구성이 되는 이유

## 질문이 나온 코드

- [`chapter106/app/src/main/java/com/example/chapter_106/ShoppingList.kt`](../chapter106/app/src/main/java/com/example/chapter_106/ShoppingList.kt)
- 질문: 상태로 관리하는 리스트인데 새로운 리스트를 만들어 대입해도 상태 객체가 잘 변경되는 이유가 무엇인가? 참조가 다른 객체로 바뀌니 재구성이 잘 안 될 것 같은데.

## 질문 전제 점검

- **"참조가 바뀌니까 재구성이 잘 안 될 것 같다"** → 정확히 반대다. **참조가 바뀌었기 때문에 재구성이 일어난다.** 감지 대상은 리스트의 내용물이 아니라 `MutableState`의 `value` 슬롯에 대한 **쓰기**다. 새 리스트를 대입하는 것이 바로 그 쓰기다.
- 오히려 위험한 쪽은 반대 경우다. 같은 리스트 객체를 제자리에서 고치면(`list.add(x)`) `value`에 쓰기가 일어나지 않아 Compose가 아무것도 알아채지 못한다. 불변 리스트를 새로 만들어 대입하는 현재 코드가 권장 방식이다.
- 한 가지 더 정정하면, 트리거는 "참조가 달라졌는가"가 아니라 **"`equals`로 다른가"**다. `mutableStateOf`의 기본 정책이 구조적 동등성이기 때문에, 참조가 다른 새 리스트여도 내용이 같으면 변경으로 치지 않는다.

## 공부할 내용

### 관찰 대상은 상태 객체 하나다

```kotlin
var sItems by remember { mutableStateOf(listOf<ShoppingItem>()) }
```

여기서 만들어지는 관찰 가능한 객체는 `MutableState<List<ShoppingItem>>` **하나**다. 리스트 안의 항목들은 관찰 대상이 아니다. 공식 문서는 `MutableState`를 이렇게 설명한다.

> "`MutableState` 클래스는 읽기와 쓰기가 Compose에 의해 관찰되는 단일 값 보유자다. 또한 이에 대한 쓰기는 스냅샷 시스템의 일부로 트랜잭션 처리된다."

그림으로 보면 이렇다.

```
MutableState.value ──→ List (불변)
      ↑                   └ ShoppingItem, ShoppingItem, ...
   여기만 관찰된다
```

`sItems = sItems + newItem`은 새 리스트를 만들어 **화살표가 가리키는 대상을 바꾸는** 일이다. 화살표가 바뀌는 순간이 곧 관찰되는 쓰기다.

### 쓰기 이후에 일어나는 일

1. `by` 위임이 `setValue`를 호출해 `MutableState.value`에 새 리스트를 대입한다.
2. 상태 객체가 새 값과 이전 값을 **변경 정책(`SnapshotMutationPolicy`)**으로 비교한다.
3. 다르다고 판정되면 스냅샷 시스템이 변경을 기록하고, 그 상태를 읽었던 스코프에 알린다.
4. 해당 스코프가 무효화되고 다음 프레임에 재구성된다. 자세한 과정은 [`compose-recomposition-timing-and-scope.md`](compose-recomposition-timing-and-scope.md)에 정리했다.

### 비교 정책이 실제 트리거다

`mutableStateOf`의 시그니처에 답이 들어 있다.

```kotlin
fun <T> mutableStateOf(
    value: T,
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy()
): MutableState<T>
```

기본값인 `structuralEqualityPolicy()`는 "`MutableState`의 값들이 구조적으로(`==`) 같으면 동등한 것으로 취급하는 정책"이다. 선택할 수 있는 정책이 셋 있다.

| 정책 | 같다고 보는 기준 | 결과 |
| --- | --- | --- |
| `structuralEqualityPolicy()` (기본) | `==` (`equals`) | 내용이 같으면 재구성하지 않는다 |
| `referentialEqualityPolicy()` | `===` (참조) | 참조만 다르면 재구성한다 |
| `neverEqualPolicy()` | 항상 다르다고 봄 | 대입할 때마다 재구성한다 |

그래서 정확한 문장은 이렇게 된다.

> 새 리스트를 대입해서 재구성되는 것이 아니라, **새로 대입한 값이 이전 값과 `equals`로 다르기 때문에** 재구성된다.

항목이 추가된 리스트는 이전 리스트와 `equals`가 다르므로 알림이 발생한다. 반대로 `sItems = sItems.toList()`처럼 내용이 같은 새 리스트를 대입하면 참조는 달라져도 재구성은 일어나지 않는다.

### 제자리 수정이 실패하는 이유

```kotlin
// 동작하지 않는다: value에 쓰기가 없다
val list = remember { mutableListOf<ShoppingItem>() }
list.add(newItem)          // Compose는 아무것도 모른다

// 동작한다: value에 새 값을 쓴다
var items by remember { mutableStateOf(listOf<ShoppingItem>()) }
items = items + newItem

// 동작한다: 리스트 자체가 관찰 가능한 타입이다
val items = remember { mutableStateListOf<ShoppingItem>() }
items.add(newItem)
```

`mutableStateListOf`는 리스트 연산 자체가 스냅샷 시스템에 통합된 관찰 가능한 리스트를 만든다. 항목 추가·삭제가 잦다면 매번 리스트를 복사하지 않아도 되므로 이쪽이 효율적이다.

## 관련 아키텍처와 베스트 프랙티스

### 불변 상태 + 통째로 교체

Compose가 권장하는 형태는 "상태를 불변으로 두고 바뀔 때마다 새 값을 대입"하는 것이다. 이유가 여럿이다.

- 변경 시점이 대입 한 줄로 드러나 추적이 쉽다.
- 스냅샷 안에서 값이 일관되게 유지된다. 재구성 도중 다른 스레드가 리스트를 고쳐 목록이 깨지는 일이 없다.
- `equals` 비교로 건너뛰기 판정이 가능하다.

이는 Compose만의 규칙이 아니라 리액트의 불변 상태 갱신, Redux의 리듀서와 같은 계열의 설계다.

### 항목 안의 `var`는 관찰되지 않는다

`chapter106`의 `ShoppingItem`은 가변 프로퍼티를 갖는다.

```kotlin
data class ShoppingItem(
    val id: Int, var name: String, var quantity: Int, var isEditing: Boolean = false
)
```

이 상태에서 다음 코드는 위험하다.

```kotlin
val editedItem = sItems.find { it.id == item.id }
editedItem?.let {
    it.name = editedName        // 관찰되지 않는 변경
    it.quantity = editedQuantity
}
```

`name`을 바꿔도 `MutableState.value`는 그대로이므로 알림이 발생하지 않는다. 바로 앞 줄에서 `sItems = sItems.map { ... }`로 리스트를 교체했기 때문에 화면이 갱신되는 것처럼 보일 뿐, 이 두 줄 자체는 재구성을 유발하지 않는다. 권장 형태는 항목도 불변으로 두고 `copy`로 새 리스트를 만드는 것이다.

```kotlin
data class ShoppingItem(
    val id: Int, val name: String, val quantity: Int, val isEditing: Boolean = false
)

sItems = sItems.map { current ->
    if (current.id == item.id) {
        current.copy(name = editedName, quantity = editedQuantity, isEditing = false)
    } else {
        current
    }
}
```

리스트 교체 한 번으로 편집 완료와 값 반영이 함께 끝나고, 상태 변경 지점이 하나로 모인다.

### 안정성과 건너뛰기

`List<T>`는 인터페이스라 Compose 컴파일러가 불안정(unstable)으로 판정할 수 있다. 불안정한 파라미터를 받는 컴포저블은 재구성을 건너뛰지 못한다. 목록이 커지고 성능이 문제가 되면 다음을 고려한다.

- `kotlinx.collections.immutable`의 `ImmutableList`, `PersistentList` 사용
- UI 상태를 `@Immutable` 표시한 `data class`로 감싸기
- 최신 Compose 컴파일러의 강한 건너뛰기(strong skipping) 동작 확인

### 항목 식별자 챙기기

```kotlin
items(sItems) { item -> ... }                 // 위치가 키가 된다
items(sItems, key = { it.id }) { item -> ... } // 안정적인 키
```

추가·삭제·편집이 일어나는 목록이므로 키를 주는 편이 안전하다. 자세한 이유는 [`compose-list-item-key.md`](compose-list-item-key.md)에 정리했다.

덧붙여 현재 코드의 `id = sItems.size + 1`은 안정적인 식별자가 아니다. 항목을 지운 뒤 추가하면 기존 id와 겹칠 수 있다. 단조 증가 카운터나 UUID처럼 재사용되지 않는 값을 쓰는 편이 낫다.

## 체크리스트

- [ ] 관찰 대상이 리스트 내용이 아니라 `MutableState.value`임을 설명할 수 있다.
- [ ] 새 리스트 대입이 재구성을 유발하는 과정을 순서대로 설명할 수 있다.
- [ ] 재구성 트리거가 "참조 변경"이 아니라 "`equals` 불일치"임을 설명할 수 있다.
- [ ] 세 가지 `SnapshotMutationPolicy`의 차이를 설명할 수 있다.
- [ ] `mutableListOf`와 `mutableStateListOf`의 차이를 설명할 수 있다.
- [ ] 항목 내부 `var` 수정이 재구성을 유발하지 않는 이유를 설명할 수 있다.
- [ ] 불변 데이터로 목록을 갱신하는 코드를 작성할 수 있다.

## 공식 참고 자료

- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers API: `androidx.compose.runtime` 패키지 (`mutableStateOf`, `SnapshotMutationPolicy`)](https://developer.android.com/reference/kotlin/androidx/compose/runtime/package-summary)
- [Android Developers API: SnapshotStateList](https://developer.android.com/reference/kotlin/androidx/compose/runtime/snapshots/SnapshotStateList)
- [Android Developers: Stability in Compose](https://developer.android.com/develop/ui/compose/performance/stability)
- [Android Developers: Lists and grids](https://developer.android.com/develop/ui/compose/lists)
