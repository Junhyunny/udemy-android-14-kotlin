# `stickyHeader` 기능은 뭘까

## 질문이 나온 코드

- [`chapter246/app/src/main/java/com/example/chapter_246/HomeView.kt`](../chapter246/app/src/main/java/com/example/chapter_246/HomeView.kt)
- 질문: `stickyHeader` 기능은 뭘까?

```kotlin
val grouped = listOf("New Released", "Favorites", "Top Rated").groupBy { it[0] }
LazyColumn {
    grouped.forEach { (key, values) ->
        stickyHeader {
            Text(text = values[0], modifier = Modifier.padding(16.dp))
            LazyRow {
                items(categories) {
                    BrowseItem(it, drawable = R.drawable.ic_menu_add)
                }
            }
        }
    }
}
```

## 질문 전제 점검

- **`stickyHeader`는 "머리글을 그리는" 기능이 아니라 "스크롤해도 화면 위에 붙어 있게 하는" 기능이다.** 그냥 제목을 넣고 싶으면 `item { Text(...) }`로 충분하다. `stickyHeader`의 값어치는 **그 그룹의 항목들이 화면에 남아 있는 동안 머리글이 최상단에 고정**되고, 다음 그룹의 머리글이 올라오면 **이전 머리글을 밀어내는** 동작에 있다.

  > "The 'sticky header' pattern is helpful when displaying lists of grouped data."

- **그래서 이 코드는 `stickyHeader`의 동작을 볼 수 없는 구조다.** 공식 예제와 비교하면 차이가 분명하다.

  ```kotlin
  // 공식 예제 — 머리글과 항목이 나뉘어 있다
  grouped.forEach { (initial, contactsForInitial) ->
      stickyHeader { CharacterHeader(initial) }
      items(contactsForInitial) { contact -> ContactListItem(contact) }   // ← 이게 있다
  }

  // 지금 코드 — 전부 머리글이다
  grouped.forEach { (key, values) ->
      stickyHeader { Text(values[0]); LazyRow { ... } }                    // ← items 가 없다
  }
  ```

  **`stickyHeader` 뒤에 `items`가 없으면 "고정될 대상"이 없다.** 머리글 다음에 바로 다음 머리글이 오므로 붙어 있을 구간이 아예 존재하지 않는다. 화면상으로는 `item {}`을 쓴 것과 구분이 안 간다.

- **`values[0]`을 쓰는 것도 의도와 다를 가능성이 높다.** `groupBy { it[0] }`는 첫 글자로 묶는데 `"New Released"`, `"Favorites"`, `"Top Rated"`는 첫 글자가 전부 달라서 **그룹마다 원소가 하나뿐**이다. 즉 `key`(`'N'`, `'F'`, `'T'`)가 머리글이 되어야 할 자리에 원소 자체를 넣고 있다. 이 예제 데이터로는 그룹화가 아무 일도 하지 않는다.

- **중첩은 문제가 아니다.** `LazyColumn` 안에 `LazyRow`를 넣는 것은 **방향이 다르므로 허용**된다. 금지되는 것은 같은 방향의 중첩이다.

  > "This applies only to cases when nesting scrollable children without a predefined size inside another same direction scrollable parent."

  다만 **머리글 안에 `LazyRow`를 넣는 것**은 다른 이유로 좋지 않다. 고정된 머리글은 항상 컴포지션에 남아 있어서, 그 안의 가로 목록도 계속 살아 있게 된다.

- **`@OptIn(ExperimentalFoundationApi::class)`이 안 붙어 있는데 컴파일된다.** 오래된 예제·블로그에는 이 opt-in이 반드시 나온다. `stickyHeader`는 오랫동안 실험적 API였고, Compose Foundation이 발전하면서 안정화됐다. 이 챕터는 `composeBom = "2026.02.01"`을 쓰므로 **opt-in 없이 쓸 수 있는 버전**이다. → [`006-kotlin-optin-experimental-api.md`](006-kotlin-optin-experimental-api.md)

## 공부할 내용

### 동작 원리 — 왜 "붙어" 있는가

`LazyColumn`은 화면에 보이는 항목만 컴포즈하고 나머지는 버린다. `stickyHeader`로 선언한 항목은 **일반 항목과 다르게 취급**된다.

1. 머리글의 원래 자리가 화면 위로 밀려 올라가면
2. Lazy 레이아웃이 그 머리글을 **버리지 않고** 뷰포트 상단에 다시 배치한다
3. 다음 머리글이 상단에 가까워지면 **이전 머리글을 위로 밀어낸다**

핵심은 **"머리글은 자기 그룹이 화면에 남아 있는 동안 살아 있다"**는 점이다. 그래서 머리글에 무거운 내용을 넣으면 스크롤 내내 그 비용을 낸다.

### 제대로 쓰는 형태

```kotlin
@Composable
fun ContactsList(grouped: Map<Char, List<Contact>>) {
    LazyColumn {
        grouped.forEach { (initial, contactsForInitial) ->
            stickyHeader {
                CharacterHeader(initial)
            }
            items(contactsForInitial) { contact ->
                ContactListItem(contact)
            }
        }
    }
}
```

`forEach`는 **컴포저블이 아니라 `LazyListScope`의 DSL 호출을 반복하는 평범한 코틀린 루프**다. `LazyColumn { }` 블록은 "무엇을 그릴지 등록하는 곳"이지 "그리는 곳"이 아니라서, 루프를 돌며 `stickyHeader`/`items`를 여러 번 등록하는 것이 정상적인 사용법이다.

### 이 화면의 의도대로 고친다면

지금 코드가 만들려던 화면은 아마 **"카테고리 이름이 머리글이고, 그 아래 가로 스크롤 카드가 있는 홈 화면"**일 것이다. 그렇다면 가로 목록은 머리글이 아니라 **항목**이어야 한다.

```kotlin
LazyColumn {
    grouped.forEach { (key, values) ->
        stickyHeader {
            Text(text = key.toString(), modifier = Modifier.padding(16.dp))
        }
        items(values) { value ->
            Text(text = value, modifier = Modifier.padding(16.dp))
            LazyRow {
                items(categories) { BrowseItem(it, drawable = R.drawable.ic_menu_add) }
            }
        }
    }
}
```

이러면 세로로 스크롤할 때 그룹 머리글이 상단에 붙고, 각 항목 안에서 가로 스크롤이 동작한다.

### `key`와 `contentType`

`stickyHeader`와 `items` 모두 `key`와 `contentType`을 받는다.

```kotlin
stickyHeader(key = "header-$initial", contentType = "header") { ... }
items(contactsForInitial, key = { it.id }, contentType = { "contact" }) { ... }
```

- **`key`** — 목록이 바뀌어도 항목의 정체성을 유지한다. 스크롤 위치와 `remember` 상태가 보존된다. → [`038-compose-list-item-key.md`](038-compose-list-item-key.md)
- **`contentType`** — 같은 종류끼리만 컴포지션을 재사용하게 한다.

  > "When you provide the `contentType`, Compose is able to reuse compositions only between items of the same type."

  머리글과 항목은 구조가 전혀 다르므로 **섞여서 재사용되면 오히려 손해**다. 머리글이 있는 목록에서 `contentType`을 지정하는 값어치가 여기 있다.

### 여러 머리글이 겹칠 때

머리글 높이가 서로 다르거나 `TopAppBar` 아래에 붙여야 할 때가 있다. 이때는 컨테이너 쪽에서 `contentPadding`을 주는 것이 보통이다.

```kotlin
LazyColumn(
    contentPadding = PaddingValues(bottom = 16.dp)
) { ... }
```

머리글 자체에 배경색을 주지 않으면 **아래 항목이 머리글을 통과해 비쳐 보인다.** 고정 머리글에는 거의 항상 불투명 배경이 필요하다.

```kotlin
stickyHeader {
    Text(
        text = key.toString(),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)   // ← 빠뜨리기 쉽다
            .padding(16.dp)
    )
}
```

## 관련 아키텍처와 베스트 프랙티스

### 그룹화는 컴포저블 밖에서 한다

공식 예제에도 주석으로 명시돼 있다.

> "This ideally would be done in the ViewModel"

```kotlin
// 나쁨: 재구성마다 groupBy 가 다시 돈다
@Composable
fun HomeView() {
    val grouped = contacts.groupBy { it.firstName[0] }
    ...
}

// 좋음: ViewModel 에서 계산해 상태로 내려 준다
class HomeViewModel : ViewModel() {
    val grouped: StateFlow<Map<Char, List<Contact>>> = ...
}
```

지금 코드처럼 컴포저블 안에서 리스트를 만들고 `groupBy`를 돌리면 **재구성될 때마다 새 `Map`이 만들어진다.** 예제 수준에서는 눈에 안 띄지만 항목이 많아지면 그대로 비용이 된다. 최소한 `remember`로 감싸는 것이 맞다. → [`036-compose-recomposition-timing-and-scope.md`](036-compose-recomposition-timing-and-scope.md)

### 머리글은 가볍게 유지한다

고정 머리글은 **그룹이 화면에 있는 내내 살아 있다.** 이미지 로딩, 애니메이션, 중첩 목록처럼 비용이 큰 내용은 머리글에 넣지 않는다.

| 머리글에 넣기 좋은 것 | 피할 것 |
| --- | --- |
| 텍스트 제목 | 중첩 `LazyRow` |
| 작은 아이콘 | 네트워크 이미지 |
| 구분선, 배경 | 무한 애니메이션 |

### `LazyColumn` 하나에 모든 내용을 담는다

머리글 + 목록 + 배너가 섞인 화면을 만들 때, `Column` 안에 `LazyColumn`을 넣지 않는다.

> "Instead, wrap all composables inside one parent `LazyColumn` using its DSL to pass different types of content, including single items and multiple list items."

```kotlin
LazyColumn {
    item { Banner() }                          // 단일 항목
    stickyHeader { SectionHeader("추천") }      // 고정 머리글
    items(recommended) { ItemRow(it) }         // 목록
    item { Footer() }
}
```

### 대안을 알아 둔다

고정 머리글이 항상 최선은 아니다.

| 상황 | 선택 |
| --- | --- |
| 그룹 구분이 스크롤 내내 필요하다 | `stickyHeader` |
| 제목만 한 번 보이면 된다 | `item { }` |
| 화면 전체에 항상 고정돼야 한다 | `Scaffold`의 `topBar` → [`032-compose-scaffold.md`](032-compose-scaffold.md) |
| 스크롤에 따라 접히는 헤더 | `TopAppBar` + `scrollBehavior` |

## 체크리스트

- [ ] `stickyHeader`와 `item`의 차이를 설명할 수 있다.
- [ ] 머리글이 "붙어 있다가 밀려나는" 동작을 설명할 수 있다.
- [ ] `stickyHeader` 뒤에 `items`가 없으면 효과가 없는 이유를 안다.
- [ ] `LazyColumn { }` 안의 `forEach`가 왜 정상적인 사용법인지 설명할 수 있다.
- [ ] 고정 머리글에 불투명 배경이 필요한 이유를 안다.
- [ ] `key`와 `contentType`의 역할을 구분할 수 있다.
- [ ] 같은 방향 중첩 스크롤만 금지된다는 것을 안다.
- [ ] 그룹화 연산을 컴포저블 밖으로 옮겨야 하는 이유를 설명할 수 있다.
- [ ] `stickyHeader` 대신 `topBar`를 써야 하는 상황을 구분할 수 있다.

## 공식 참고 자료

- [Android Developers: Lists and grids](https://developer.android.com/develop/ui/compose/lists)
- [Android Developers: `LazyListScope.stickyHeader`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListScope)
- [Android Developers: `androidx.compose.foundation.lazy` package summary](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/package-summary)
- [Android Developers: Compose Foundation release notes](https://developer.android.com/jetpack/androidx/releases/compose-foundation)
- [Android Developers: Compose performance](https://developer.android.com/develop/ui/compose/performance)
