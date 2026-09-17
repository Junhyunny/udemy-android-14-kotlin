# `animateItem()`은 무엇을 하고, 왜 잔상 문제가 해결됐나

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/HomeView.kt`](../chapter205/app/src/main/java/com/example/chapter_205/HomeView.kt)
- 질문: 삭제한 빨간 박스가 사라지지 않는 현상이 `animateItem` 모디파이어로 고쳐졌다. 어떤 역할이고 왜 쓰는지, 그리고 왜 문제가 해결되었는지도 알려 달라.

```kotlin
items(items = wishList.value, key = { it.id }) { wish ->
    val dismissState = rememberSwipeToDismissBoxState(...)
    SwipeToDismissBox(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .animateItem(),          // ← 이것이 문제를 해결했다
        state = dismissState,
        ...
    )
}
```

## 질문 전제 점검

- **"빨간 박스가 사라지지 않는다"는 증상의 정체는 상태 재사용이다.** `animateItem()`이 애니메이션을 붙여서 "가려 준" 것이 아니라, **잘못 남아 있던 상태가 드러나던 것을 순서 문제로 풀어 준 것**에 가깝다. 정확한 인과를 보려면 지연 목록이 항목을 어떻게 다루는지부터 봐야 한다.

  ```
  삭제 전:  [0] A(빨강 스와이프됨)   [1] B   [2] C
  A 삭제
  삭제 후:  [0] B                    [1] C
  ```

  **`LazyColumn`은 위치를 슬롯으로 재사용한다.** 0번 슬롯에 있던 컴포지션(스와이프되어 빨간 배경이 드러나고 `dismissState`가 `EndToStart`인 상태)이 그대로 남아 있고, 내용만 B로 바뀌면 **"B가 이미 스와이프된 상태"로 보인다.** 이것이 빨간 박스가 남아 보이는 현상이다.

- **그래서 근본 해결책은 `key`다.** `key`를 주면 Compose가 "0번 슬롯"이 아니라 "id=3인 항목"으로 상태를 추적한다.

  > "By default, each item's state is keyed against the position of the item in the list or grid. However, this can cause issues if the dataset changes, since items which change position effectively lose any remembered state."
  >
  > "To combat this, you can provide a stable and unique key for each item... Providing a stable key enables item state to be consistent across dataset changes"

  `chapter205`는 **이미 `key = { it.id }`를 주고 있다.** 그래서 상태 자체는 올바르게 추적된다.

- **그러면 `animateItem()`은 무엇을 더 해 준 것인가?** 핵심은 **제거 애니메이션** 때문이다. `animateItem()`이 없으면 항목이 **한 프레임에 즉시 사라지고** 아래 항목들이 즉시 위로 점프한다. 그 순간에 스와이프 상태가 정리되는 타이밍과 겹치면서 잔상이 보인다. `animateItem()`은 사라지는 항목을 페이드아웃시키고 나머지를 부드럽게 이동시키므로, **제거되는 컴포지션이 정리될 시간이 생긴다.**

  즉 `animateItem()`은 **증상을 없앤 것이지 원인을 고친 것은 아니다.** 근본은 `key`와 상태 추적이고, 이미 맞게 되어 있다. 그래서 "왜 고쳐졌는가"의 정직한 답은 이렇다.

  > **항목 제거가 즉시 일어나지 않고 애니메이션 구간을 갖게 되면서, 슬롯 재사용과 상태 정리가 겹치는 프레임이 사라졌다.**

- **덧붙여, `animateItem()`은 `key` 없이는 제대로 동작하지 않는다.**

  > "It is important to provide a key to each item to ensure `animateItem()` works as expected."
  >
  > "Make sure you provide keys for your items so it is possible to find the new position for the moved element."

  **둘은 짝이다.** `key`가 없으면 "어느 항목이 어디로 이동했는지" 판단할 수 없어 이동 애니메이션이 불가능하다.

## 공부할 내용

### `key`가 하는 일

```kotlin
items(items = wishList.value, key = { it.id }) { wish -> ... }
```

`key`를 주면 Compose는 **항목의 정체성을 위치가 아니라 키로 판단한다.**

```
key 없음:  "0번 자리의 컴포지션"  → 데이터가 바뀌면 자리는 그대로, 내용만 교체
key 있음:  "id=3 항목의 컴포지션" → 데이터가 바뀌면 그 항목을 찾아 이동시킨다
```

그래서 `key`가 지키는 것은 **`remember`로 만든 항목별 상태**다.

```kotlin
items(wishes, key = { it.id }) { wish ->
    val dismissState = rememberSwipeToDismissBoxState()   // 이 상태가 항목을 따라간다
    var expanded by remember { mutableStateOf(false) }    // 이것도
}
```

`key`가 없으면 목록이 재정렬될 때 **펼쳐 둔 항목이 엉뚱한 항목으로 옮겨 간다.**

키 타입에는 제약이 있다.

> Key는 `Bundle`에서 지원하는 타입이어야 합니다: primitives, enums, Parcelable 등

`rememberSaveable` 상태를 액티비티 재생성 후 복원하기 위해서다. `it.id`가 `Long`이므로 조건을 만족한다. → [`024-android-parcelable-vs-serializable.md`](024-android-parcelable-vs-serializable.md)

### `animateItem()`이 하는 일

세 가지 애니메이션을 붙인다.

| 사건 | 애니메이션 | 기본 스펙 |
| --- | --- | --- |
| 항목 **추가** | 페이드 인 | `fadeInSpec` |
| 항목 **제거** | 페이드 아웃 | `fadeOutSpec` |
| 항목 **이동**(재정렬) | 위치 이동 | `placementSpec` |

```kotlin
LazyColumn {
    // It is important to provide a key to each item to ensure animateItem() works as expected.
    items(books, key = { it.id }) {
        Row(Modifier.animateItem()) {
            // ...
        }
    }
}
```

스펙을 바꿀 수도 있다.

```kotlin
Modifier.animateItem(
    fadeInSpec = tween(durationMillis = 250),
    fadeOutSpec = tween(durationMillis = 100),
    placementSpec = spring(
        stiffness = Spring.StiffnessLow,
        dampingRatio = Spring.DampingRatioMediumBouncy
    )
)
```

일부만 끄고 싶으면 `null`을 준다.

```kotlin
Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)   // 이동만 애니메이션
```

### 어디에 붙여야 하는가

**`items` 람다 안의 최상위 컴포저블**에 붙인다.

```kotlin
// ✅ 올바름
items(wishes, key = { it.id }) { wish ->
    SwipeToDismissBox(modifier = Modifier.animateItem(), ...) { ... }
}

// ❌ 안쪽 자식에 붙이면 항목 전체가 아니라 그 자식만 움직인다
items(wishes, key = { it.id }) { wish ->
    Column {
        Text(wish.title, modifier = Modifier.animateItem())
    }
}
```

`animateItem()`은 **`LazyItemScope`의 확장 함수**다. 그래서 `items` 람다 밖에서는 아예 호출할 수 없다.

### `animateItem()`과 `animateItemPlacement()`

예전 이름을 본 적이 있다면 헷갈릴 수 있다.

```kotlin
Modifier.animateItemPlacement()   // 구버전. 이동만 애니메이션. deprecated
Modifier.animateItem()            // 현재. 추가·제거·이동 전부
```

`animateItem()`으로 통합되면서 추가·제거 애니메이션까지 포함하게 됐다. 지금 코드가 쓰는 쪽이 맞다.

### 스와이프 삭제와 함께 쓸 때의 전체 그림

```kotlin
items(wishList.value, key = { it.id }) { wish ->
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.5f }
    )

    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.currentValue }
            .filter { it == SwipeToDismissBoxValue.EndToStart }
            .collect { viewModel.deleteWish(wish) }
    }

    SwipeToDismissBox(
        modifier = Modifier.fillMaxWidth().animateItem(),
        state = dismissState,
        backgroundContent = { /* 빨간 배경 */ },
        enableDismissFromStartToEnd = false,
    ) {
        WishItem(wish = wish) { /* 클릭 */ }
    }
}
```

동작 순서가 이렇게 된다.

```
1. 사용자가 스와이프
2. dismissState.currentValue 가 EndToStart 로
3. snapshotFlow 가 감지 → viewModel.deleteWish(wish)
4. Room 에서 삭제 → Flow 가 새 리스트 emit
5. LazyColumn 재구성, 해당 key 의 항목이 사라짐
6. animateItem() 이 페이드 아웃 + 나머지 항목 이동 애니메이션
```

**3번과 4번 사이에 DB 왕복이 있다는 점**이 중요하다. 즉시 사라지지 않고 수십 ms 뒤에 사라진다. 이 구간에 애니메이션이 없으면 "멈췄다가 툭 사라지는" 느낌이 된다.

### 목록 상태가 유지되는지 확인하는 법

```kotlin
items(wishes, key = { it.id }) { wish ->
    var expanded by remember { mutableStateOf(false) }
    Text(wish.title, Modifier.clickable { expanded = !expanded })
    if (expanded) Text(wish.description)
}
```

항목 하나를 펼친 뒤 **위쪽 항목을 삭제**해 본다.

- `key` 있음 → 펼친 항목이 그대로 펼쳐져 있다
- `key` 없음 → 다른 항목이 펼쳐진다

## 관련 아키텍처와 베스트 프랙티스

### `key`는 기본으로 준다

```kotlin
items(wishes, key = { it.id }) { ... }
```

**목록에 `key`를 주는 것은 선택이 아니라 기본**으로 여기는 편이 좋다. 지금 당장 문제가 없어도, 정렬·필터·삭제가 들어오는 순간 드러난다.

키는 **안정적이고 고유해야** 한다.

```kotlin
key = { it.id }                    // ✅ DB 기본 키
key = { it.uuid }                  // ✅
key = { it.title }                 // ⚠️ 중복 가능
key = { it.hashCode() }            // ❌ 내용이 바뀌면 키도 바뀐다
key = { wishes.indexOf(it) }       // ❌ 위치 기반. key 없는 것과 같다
```

`key`가 중복되면 런타임에 예외가 난다.

```
IllegalArgumentException: Key "1" was already used.
```

### 삭제에는 되돌리기를 붙인다

```kotlin
scope.launch {
    val result = snackbarHostState.showSnackbar(
        message = "${wish.title} 삭제됨",
        actionLabel = "실행 취소"
    )
    if (result == SnackbarResult.ActionPerformed) viewModel.addWish(wish)
}
```

**스와이프는 실수하기 쉬운 제스처다.** Material 가이드도 되돌릴 방법을 두라고 권한다. `chapter205`의 삭제는 현재 되돌릴 수 없다. → [`032-compose-scaffold.md`](032-compose-scaffold.md)

### `SwipeToDismissBox`에서 상태를 되돌리기

임계값을 넘었는데 삭제를 취소해야 하는 경우, 상태를 원위치시켜야 한다.

```kotlin
scope.launch { dismissState.reset() }
```

안 하면 그 항목이 스와이프된 채로 남는다.

### 목록이 크면 다른 것도 챙긴다

```kotlin
LazyColumn(
    state = rememberLazyListState(),
    contentPadding = paddingValues,
    verticalArrangement = Arrangement.spacedBy(8.dp)   // 항목 간 간격
) {
    items(wishes, key = { it.id }, contentType = { "wish" }) { ... }
}
```

`contentType`은 **같은 종류 항목의 컴포지션을 재사용**하게 해 스크롤 성능을 올린다. 여러 타입이 섞인 목록에서 효과가 크다.

### 애니메이션이 과하지 않게

`animateItem()`은 기본값이 이미 적절하다. 스펙을 건드릴 때는 **제거를 추가보다 짧게** 두는 편이 자연스럽다. 사라지는 것은 빨리, 나타나는 것은 조금 여유 있게.

## 체크리스트

- [ ] `LazyColumn`이 위치 기반으로 항목 상태를 추적한다는 기본 동작을 안다.
- [ ] `key`를 주면 정체성이 위치가 아니라 키로 바뀐다는 것을 설명할 수 있다.
- [ ] 빨간 박스 잔상의 원인이 슬롯 재사용이라는 것을 설명할 수 있다.
- [ ] `animateItem()`이 증상을 없앤 경로를 설명할 수 있다.
- [ ] `animateItem()`이 `key` 없이는 제대로 동작하지 않는 이유를 안다.
- [ ] `animateItem()`이 붙이는 세 가지 애니메이션을 말할 수 있다.
- [ ] `animateItem()`을 `items` 람다의 최상위에 붙여야 하는 이유를 안다.
- [ ] `animateItemPlacement()`와의 관계를 안다.
- [ ] 키로 쓰기 좋은 값과 나쁜 값을 구분할 수 있다.
- [ ] 키 중복 시 예외가 난다는 것을 안다.
- [ ] 스와이프 삭제에 되돌리기를 붙이는 이유를 안다.
- [ ] `dismissState.reset()`이 필요한 상황을 안다.

## 공식 참고 자료

- [Android Developers: Lists and grids](https://developer.android.com/develop/ui/compose/lists)
- [Android Developers: Item animations in lazy layouts](https://developer.android.com/develop/ui/compose/animation/quick-guide)
- [Android Developers: `LazyItemScope.animateItem` reference](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyItemScope)
- [Android Developers: Swipe to dismiss](https://developer.android.com/develop/ui/compose/components/swipe-to-dismiss)
- [Android Developers: Compose performance](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Android Developers: Lifecycle of composables](https://developer.android.com/develop/ui/compose/lifecycle)
- [Material Design 3: Snackbar](https://m3.material.io/components/snackbar/guidelines)
