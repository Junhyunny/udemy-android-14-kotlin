# `contentDescription`은 왜 필수 값이고 `null`은 무슨 뜻인가

## 질문이 나온 코드

- [`chapter143/app/src/main/java/com/example/chapter_143/RecipeScreen.kt`](../chapter143/app/src/main/java/com/example/chapter_143/RecipeScreen.kt)
- 질문: `contentDescription`에 `null`을 넣었는데 어떤 용도인가? 왜 필수 값인가? `null`을 넣는 게 일반적인 방법인가?

```kotlin
Image(
    painter = rememberAsyncImagePainter(category.strCategoryThumb),
    contentDescription = null,
    modifier = Modifier.fillMaxSize().aspectRatio(1f)
)
```

## 질문 전제 점검

- **"어떤 용도인가"** → 화면에 보이는 값이 아니다. **접근성 서비스(TalkBack 등)가 읽어 주는 텍스트**다. 이 이미지가 무엇인지 눈으로 볼 수 없는 사용자에게 전달되는 유일한 정보다. 접근성 외에 UI 테스트에서 노드를 찾을 때도 쓰인다.

- **"왜 필수 값인가"** → 기본값을 주지 않은 것이 **의도된 API 설계**다. 타입은 `String?`이라 `null`이 허용되지만 **기본값이 없어서 반드시 무언가를 적어야 한다.** 즉 "깜빡 잊어서 빠뜨렸다"가 불가능하고, 개발자가 **"이 이미지는 의미가 있는가, 장식인가"를 매번 판단**하게 만든다. XML 시절 `android:contentDescription`이 선택 속성이라 대부분 비어 있던 문제를 API 차원에서 막은 것이다.

- **"`null`을 넣는 게 일반적인 방법인가"** → 조건이 붙는다. 공식 문서의 기준은 분명하다.

  > "If a UI element has a `contentDescription` parameter but is purely decorative (such as an `Icon` that is part of another UI element), pass `null` to avoid redundant labeling."

  **순수한 장식일 때만** `null`이다. 습관처럼 `null`을 넣는 것은 "일반적인 방법"이 아니라 **접근성 결함**이다.

- **그러면 지금 코드는 맞는가** → 애매하다. 이 이미지는 장식이 아니라 **카테고리를 대표하는 사진**이다. 하지만 바로 아래 `Text(category.strCategory)`가 같은 정보를 글자로 주고 있다. 그래서 이미지에 `"Beef"`라는 설명을 또 붙이면 TalkBack이 **"Beef, Beef"라고 두 번 읽는다.** `null`을 넣은 판단 자체는 이 "중복 라벨 회피" 기준에 들어맞는다.

  다만 지금 구조에는 별도의 문제가 있다. `Column`에 시맨틱 병합이 없어서 **이미지와 텍스트가 각각 따로 초점을 받는다.** 항목 하나가 카드 하나로 읽히지 않는다. `null`을 넣는 것으로 끝낼 게 아니라 **항목 전체를 하나의 의미 단위로 묶는 것**이 제대로 된 해법이다. 아래에서 다룬다.

## 공부할 내용

### `contentDescription`이 실제로 하는 일

컴포즈는 화면을 그리는 컴포지션 트리와 나란히 **시맨틱 트리(semantics tree)** 를 만든다. 접근성 서비스, 자동 완성, 테스트 프레임워크가 보는 것은 이 트리다.

> Semantics in Jetpack Compose provide additional context about composables to services like accessibility, autofill, and testing. They convey the **meaning and role** of UI components beyond their visual representation.

> For example, a camera icon might visually be just an image, but its semantic meaning is "Take a photo".

`contentDescription`에 넣은 문자열은 그 노드의 시맨틱 속성이 된다. `null`을 넣으면 **속성 자체가 설정되지 않아** 접근성 서비스가 읽을 것이 없다.

### 언제 무엇을 넣는가

| 이미지의 성격 | 값 | 예 |
| --- | --- | --- |
| 정보를 담고 있고, 다른 데서 설명되지 않음 | 설명 문자열 | 프로필 사진 → `"홍길동의 프로필 사진"` |
| 동작을 나타냄 | **동작**을 적는다 | 공유 아이콘 → `"공유하기"` (`"공유 아이콘"` ❌) |
| 옆의 텍스트가 같은 정보를 줌 | `null` + 부모에서 병합 | 카테고리 썸네일 + 이름 |
| 순수 장식 (구분선, 배경 무늬) | `null` | 그라데이션 배경 |

동작 아이콘의 예시는 공식 문서에 그대로 있다.

```kotlin
@Composable
private fun ShareButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = stringResource(R.string.label_share)
        )
    }
}
```

**"이 이미지가 무엇처럼 생겼는가"가 아니라 "사용자에게 무엇을 뜻하는가"를 적는다.** 그리고 하드코딩한 문자열이 아니라 `stringResource`를 쓴다. 접근성 텍스트도 번역 대상이다.

### 지금 코드를 제대로 고치면

문제는 `CategoryItem` 하나가 TalkBack에서 **두 번 초점을 받는다**는 것이다. 이미지에서 한 번(설명 없음), 텍스트에서 한 번. 사용자는 "이게 한 덩어리"라는 것을 알 수 없다.

```kotlin
@Composable
fun CategoryItem(category: Category) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxSize()
            .semantics(mergeDescendants = true) { },   // ← 항목 하나로 묶는다
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = rememberAsyncImagePainter(category.strCategoryThumb),
            contentDescription = null,                 // 아래 Text가 설명한다
            modifier = Modifier.fillMaxSize().aspectRatio(1f)
        )
        Text(text = category.strCategory, ...)
    }
}
```

`mergeDescendants = true`를 주면 자식들의 시맨틱이 하나로 합쳐진다. TalkBack은 **"Beef"** 한 번만 읽고, 초점도 한 번만 간다. 이때 `contentDescription = null`은 "텍스트가 대신 설명하니 나는 빠진다"는 의미가 되어 앞뒤가 맞는다.

이미지만 있고 텍스트가 없는 경우라면 반대로 설명이 필수다.

```kotlin
// 텍스트 라벨이 없다면 이미지가 유일한 정보원이다
Image(
    painter = rememberAsyncImagePainter(category.strCategoryThumb),
    contentDescription = category.strCategory      // null이면 안 된다
)
```

### `null`과 빈 문자열은 다르다

```kotlin
contentDescription = null    // 시맨틱 속성 자체가 없음 → 서비스가 무시
contentDescription = ""      // 빈 설명이 "있음" → 초점은 가는데 읽을 게 없다
```

**`""`를 쓰지 않는다.** 초점만 받고 아무것도 말하지 않는 노드가 생겨서 더 나쁘다.

### 검증하는 방법

- **TalkBack** — 설정 › 접근성 › TalkBack을 켜고 화면을 훑어 본다. 가장 확실하다.
- **접근성 검사기(Accessibility Scanner)** — Play 스토어 앱. 라벨 누락을 자동으로 잡아 준다.
- **레이아웃 인스펙터** — 병합/비병합 시맨틱 트리를 눈으로 확인한다.
- **테스트에서 트리 출력**

  ```kotlin
  composeTestRule.onRoot(useUnmergedTree = true).printToLog("MY TAG")
  ```

## 관련 아키텍처와 베스트 프랙티스

### 병합과 초점의 규칙

`Button`, `clickable`, `toggleable`은 **자동으로 자식을 병합한다.** 그래서 버튼 안의 아이콘에는 보통 `contentDescription = null`을 주고, 버튼 자체가 라벨을 갖는다.

```kotlin
Button(onClick = { }) {
    Icon(Icons.Filled.Add, contentDescription = null)   // 버튼이 병합한다
    Text("추가")                                        // 이게 라벨이 된다
}
// TalkBack: "추가, 버튼"
```

반대로 `Column`, `Row`, `Box`는 병합하지 않는다. 그래서 위의 `CategoryItem`처럼 **직접 `semantics(mergeDescendants = true)`를 붙여야** 한다.

더 복잡한 경우에는 자식의 시맨틱을 비우고 부모에 새로 설정하는 방법도 있다.

```kotlin
ArticleListItemRow(
    modifier = Modifier.semantics {
        customActions = listOf(
            CustomAccessibilityAction(label = "Open article", action = { openArticle(); true }),
            CustomAccessibilityAction(label = "Add to bookmarks", action = { addToBookmarks(); true }),
        )
    }
) {
    Article(modifier = Modifier.clearAndSetSemantics { }, onClick = openArticle)
    BookmarkButton(modifier = Modifier.clearAndSetSemantics { }, onClick = addToBookmarks)
}
```

### 이미지 로딩 상태도 접근성에 영향을 준다

`rememberAsyncImagePainter`는 로딩/에러 상태를 표현하지 않으면 **빈 칸**으로 남는다. 시각적으로도, 접근성 측면에서도 좋지 않다. Coil에서는 `placeholder`/`error`를 지정하거나 `AsyncImage`를 쓰는 편이 낫다.

```kotlin
AsyncImage(
    model = category.strCategoryThumb,
    contentDescription = null,
    placeholder = painterResource(R.drawable.placeholder),
    error = painterResource(R.drawable.image_error),
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxSize().aspectRatio(1f)
)
```

### 접근성은 나중에 붙이는 것이 아니다

`contentDescription`을 필수 파라미터로 만든 이유가 여기 있다. 화면을 다 만든 뒤 접근성을 "추가"하려면 모든 컴포저블을 다시 훑어야 한다. **처음 쓸 때 한 번 판단하면** 그런 일이 없다.

판단 기준은 질문 하나로 줄일 수 있다.

> **이 이미지를 지웠을 때 사용자가 잃는 정보가 있는가?**
>
> - 있다 → 그 정보를 `contentDescription`에 적는다.
> - 없다(옆 텍스트가 이미 말해 준다, 순수 장식이다) → `null`.

### 그 밖에 자주 쓰는 시맨틱 속성

```kotlin
Modifier.semantics { heading() }                          // 제목임을 알린다
Modifier.semantics { stateDescription = "구독 중" }        // 상태를 말로 설명
Modifier.semantics { liveRegion = LiveRegionMode.Polite } // 변경되면 알려 준다
Modifier.semantics { error("이메일을 입력하세요") }         // 오류 상태
Modifier.testTag("category_item")                         // 테스트용 식별자
```

`testTag`는 접근성 서비스에 노출되지 않으므로, **테스트 식별용으로 `contentDescription`을 쓰지 않는다.**

## 체크리스트

- [ ] `contentDescription`이 화면이 아니라 접근성 서비스에 전달되는 값임을 설명할 수 있다.
- [ ] 기본값 없이 필수 파라미터로 만든 API 설계 의도를 설명할 수 있다.
- [ ] `null`이 "순수 장식"을 뜻한다는 공식 기준을 말할 수 있다.
- [ ] 습관적으로 `null`을 넣는 것이 접근성 결함이 되는 이유를 설명할 수 있다.
- [ ] 동작 아이콘에는 모양이 아니라 동작을 적는다는 원칙을 안다.
- [ ] `null`과 `""`의 차이를 설명할 수 있다.
- [ ] `semantics(mergeDescendants = true)`로 항목을 하나의 의미 단위로 묶을 수 있다.
- [ ] `Button`/`clickable`이 자동 병합한다는 것을 알고 활용할 수 있다.
- [ ] TalkBack이나 접근성 검사기로 결과를 확인할 수 있다.
- [ ] `contentDescription`을 테스트 식별자로 쓰지 않고 `testTag`를 쓴다.

## 공식 참고 자료

- [Android Developers: Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)
- [Android Developers: Accessibility in Jetpack Compose](https://developer.android.com/develop/ui/compose/accessibility/key-steps)
- [Android Developers: Semantics in Compose](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- [Android Developers: Merging and clearing semantics](https://developer.android.com/develop/ui/compose/accessibility/merging-clearing)
- [Android Developers: `androidx.compose.foundation` (Image)](https://developer.android.com/reference/kotlin/androidx/compose/foundation/package-summary)
- [Android Developers: Principles for improving app accessibility](https://developer.android.com/guide/topics/ui/accessibility/principles)
