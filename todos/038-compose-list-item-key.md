# Compose는 반복 렌더링에 키가 필요 없는가

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: SwiftUI와 리액트는 반복문에서 각 객체의 식별자(id, key)를 요구했는데, Jetpack Compose는 그런 것이 필요 없는가?

## 질문 전제 점검

- **"Jetpack Compose는 그런 게 필요 없나?"** → "없어도 동작한다"와 "필요 없다"는 다르다. Compose는 키가 없으면 **위치를 키로 사용**한다. 목록이 고정되어 있으면 문제가 없지만, 정렬·필터·삽입·삭제가 일어나는 순간 리액트나 SwiftUI에서 키가 없을 때와 똑같은 문제가 생긴다.
- 즉 Compose가 식별자 개념을 없앤 것이 아니라 **기본값을 제공**한 것이다. 그 기본값이 인덱스라는 점까지 리액트와 같다.
- `chapter078`의 코드는 `0..<4` 고정 범위를 돌고 각 항목이 기억하는 상태도 없으므로 키가 없어도 무방하다. 질문 상황에 한해서는 "필요 없다"가 맞지만, 규칙으로 일반화하면 틀린다.

## 공부할 내용

### 기본은 위치 기반 식별

Compose는 명시적 키가 없으면 호출 위치로 상태를 식별한다. 리스트에서도 마찬가지여서, 문서는 "기본적으로 각 아이템의 상태는 리스트나 그리드에서의 아이템 위치를 기준으로 키가 매겨진다"라고 설명한다.

`chapter078`의 코드는 고정된 범위를 순회하고 각 아이템이 기억하는 상태도 없다.

```kotlin
for (index: Int in 0..<4) {
    DropdownMenuItem(text = { Text("$index") }, onClick = {})
}
```

이 경우에는 키가 없어도 문제가 없다. 목록이 변하지 않고, 위치가 곧 정체성이기 때문이다. 즉 "필요 없다"가 아니라 "이 상황에서는 위치 기반 식별로 충분하다"가 정확하다.

### 키가 필요해지는 순간

문서는 위치 기반 식별의 한계를 분명히 밝힌다. "데이터셋이 변경되면 문제가 될 수 있다. 위치가 바뀐 아이템은 기억된 상태를 사실상 잃기 때문이다." 예로 "`LazyColumn` 안의 `LazyRow`에서 행의 위치가 바뀌면 사용자는 그 행 안의 스크롤 위치를 잃는다"를 든다.

따라서 목록이 정렬·필터·삽입·삭제로 바뀌고 아이템이 상태를 기억한다면 키가 필요하다.

```kotlin
LazyColumn {
    items(
        items = messages,
        key = { message -> message.id }   // 안정적이고 고유한 키
    ) { message -> MessageRow(message) }
}
```

"키를 제공하면 Compose가 재정렬을 올바르게 처리하도록 도울 수 있다. 예를 들어 아이템이 기억된 상태를 가지고 있다면, 키를 설정해 두면 위치가 바뀔 때 Compose가 그 상태를 아이템과 함께 옮길 수 있다."

키 타입에는 제약이 있다. "키의 타입은 `Bundle`이 지원하는 타입이어야 한다." 원시 타입, enum, `Parcelable` 등이다.

### 일반 컴포저블에서의 `key`

`LazyColumn`이 아닌 일반 반복에서도 `key` 컴포저블로 같은 일을 할 수 있다.

```kotlin
Column {
    for (item in items) {
        key(item.id) {
            ItemRow(item)
        }
    }
}
```

### 다른 프레임워크와 비교

- 리액트의 `key`, SwiftUI의 `Identifiable`/`id`는 목록 렌더링에서 사실상 필수로 요구된다.
- Compose는 위치 기반 식별이라는 기본값이 있어 없어도 동작하지만, 동적 목록에서 상태와 애니메이션을 올바르게 유지하려면 결국 같은 정보를 `key`로 제공해야 한다.

## 관련 아키텍처와 베스트 프랙티스

### 다른 프레임워크와 나란히 보기

| | 식별자 지정 | 지정하지 않으면 |
| --- | --- | --- |
| React | `key` prop | 인덱스 기반, 경고 출력 |
| SwiftUI | `Identifiable` 또는 `id:` | 컴파일 단계에서 요구하는 경우가 많음 |
| Compose | `items(key = ...)`, `key(...)` | 위치 기반, 경고 없음 |

Compose는 경고를 내지 않기 때문에 문제가 조용히 지나간다. 목록이 동적이라면 습관적으로 키를 주는 편이 안전하다.

### 안정적인 키의 조건

- **고유하다**: 목록 안에서 중복되지 않는다.
- **안정적이다**: 같은 항목이면 재구성·재정렬 후에도 같은 값이다.
- **`Bundle`이 지원하는 타입이다**: 원시 타입, `String`, enum, `Parcelable` 등.

가장 좋은 키는 데이터 레이어에서 온 도메인 식별자(`message.id`)다. 반대로 인덱스, 화면 표시 문자열, 객체의 해시코드는 안정적이지 않아 키로 부적합하다. 키가 없는 데이터라면 그건 UI 문제가 아니라 **모델에 식별자가 빠져 있다는 신호**로 보는 편이 낫다.

### 키가 만드는 차이

```kotlin
LazyColumn {
    items(messages, key = { it.id }) { message ->
        MessageRow(message)   // 내부의 remember 상태가 항목을 따라 이동한다
    }
}
```

- 항목 내부의 `remember` 상태와 스크롤 위치가 유지된다.
- `Modifier.animateItem()`으로 재정렬 애니메이션이 자연스럽게 동작한다.
- 삽입·삭제 시 필요한 최소한의 항목만 다시 구성한다.

### 곁들여 알아둘 최적화

- `contentType`: 여러 종류의 항목이 섞인 목록에서 같은 종류끼리 구성을 재사용하게 해 성능을 높인다.
- `key(...)` 컴포저블: `LazyColumn`이 아닌 일반 `Column` 반복에서도 동일하게 정체성을 부여할 수 있다.
- 항목이 많다면 애초에 `Column` + 반복 대신 `LazyColumn`을 쓴다. 화면에 보이는 항목만 구성하기 때문이다.

## 체크리스트

- [ ] Compose의 기본 식별 방식이 호출 위치라는 점을 설명할 수 있다.
- [ ] 키 없이도 문제가 없는 상황과 문제가 되는 상황을 구분할 수 있다.
- [ ] `items(key = ...)`를 사용할 수 있다.
- [ ] 키가 없을 때 잃어버리는 것(기억된 상태, 스크롤 위치)을 예로 들 수 있다.
- [ ] 키 타입이 `Bundle` 지원 타입이어야 하는 이유를 설명할 수 있다.
- [ ] 일반 반복에서 `key` 컴포저블을 사용할 수 있다.

## 공식 참고 자료

- [Android Developers: Lists and grids — Item keys](https://developer.android.com/develop/ui/compose/lists)
- [Android Developers: Lifecycle of composables](https://developer.android.com/develop/ui/compose/lifecycle)
- [Android Developers API: `key` 컴포저블](https://developer.android.com/reference/kotlin/androidx/compose/runtime/package-summary#key(kotlin.Array,kotlin.Function0))
- [Android Developers: Compose performance](https://developer.android.com/develop/ui/compose/performance)
- [Android Developers: Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Android Developers: Item animations in lazy layouts](https://developer.android.com/develop/ui/compose/animation/composables-modifiers)
- [Android Developers: UI layer architecture](https://developer.android.com/topic/architecture/ui-layer)
