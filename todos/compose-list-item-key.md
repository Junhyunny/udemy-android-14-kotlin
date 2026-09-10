# Compose는 반복 렌더링에 키가 필요 없는가

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: SwiftUI와 리액트는 반복문에서 각 객체의 식별자(id, key)를 요구했는데, Jetpack Compose는 그런 것이 필요 없는가?

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
