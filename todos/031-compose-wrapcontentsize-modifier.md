# `Modifier.wrapContentSize()`는 무엇을 하는가

## 질문이 나온 코드

- [`chapter106/app/src/main/java/com/example/chapter_106/ShoppingList.kt`](../chapter106/app/src/main/java/com/example/chapter_106/ShoppingList.kt)
- 질문: `Modifier.wrapContentSize()`는 어떤 기능인가?

## 질문 전제 점검

질문에 잘못된 전제는 없다. 다만 이름만 보고 오해하기 쉬운 지점이 두 가지 있어 미리 짚어 둔다.

- **"내용 크기에 맞춘다"로만 이해하면 절반이다.** 이 모디파이어는 부모가 강제하는 **최소 크기 제약을 풀어 주는** 역할을 한다. 아무 제약이 없는 상황이라면 붙이든 붙이지 않든 결과가 같다.
- **정렬 기능도 함께 들어 있다.** 제약을 푼 뒤 남은 공간 안에서 콘텐츠를 어디에 둘지 `align` 파라미터가 결정한다. 기본값은 가운데다. `wrapContentSize()`를 붙였는데 위치가 가운데로 옮겨졌다면 이 때문이다.

## 공부할 내용

### 공식 정의

> `Modifier.wrapContentSize`는 "들어오는 측정 최소 너비 또는 최소 높이 제약을 무시하고 콘텐츠가 원하는 크기로 측정되도록 허용한다. 그리고 `unbounded`가 `true`이면 들어오는 최대 제약도 무시한다."

시그니처는 다음과 같다.

```kotlin
fun Modifier.wrapContentSize(
    align: Alignment = Alignment.Center,
    unbounded: Boolean = false
): Modifier
```

### 제약이 흐르는 구조를 알아야 이해된다

Compose 레이아웃은 부모가 자식에게 **제약(Constraints)**을 내려보내고 자식이 그 범위 안에서 크기를 정하는 방식이다. 제약은 최소 너비·최대 너비·최소 높이·최대 높이 네 값이다.

문제는 부모가 최소 크기를 강제하는 경우다. 예를 들어 `Modifier.fillMaxWidth()`가 걸린 부모 안에서는 자식에게 "너비를 꽉 채우라"는 최소 제약이 내려갈 수 있다. 이때 자식이 내용만큼만 차지하고 싶어도 그럴 수 없다. `wrapContentSize()`는 그 최소 제약을 0으로 풀어 준다.

```
부모 제약: minWidth=300, maxWidth=300
   └ wrapContentSize() 적용
      → 자식 제약: minWidth=0, maxWidth=300
      → 자식은 내용 크기(예: 120)로 측정된다
      → 남은 300 공간 안에서 align 위치에 배치된다
```

마지막 줄이 중요하다. 차지하는 **레이아웃 공간은 그대로 300**이고, 그 안에서 콘텐츠만 작아져 정렬된다.

### 형제 모디파이어와의 관계

| 모디파이어 | 하는 일 |
| --- | --- |
| `wrapContentWidth(align)` | 최소 너비 제약만 푼다 |
| `wrapContentHeight(align)` | 최소 높이 제약만 푼다 |
| `wrapContentSize(align)` | 둘 다 푼다 |
| `size(w, h)` | 정확한 크기를 지정한다 |
| `defaultMinSize(w, h)` | 제약이 없을 때만 적용되는 최소 크기 |
| `fillMaxWidth()` | 최대 너비까지 채운다 |

`unbounded = true`는 최대 제약까지 무시한다. 부모보다 큰 콘텐츠를 잘리지 않게 그려야 할 때 쓰는 예외적인 옵션이며, 화면 밖으로 넘칠 수 있으므로 신중하게 쓴다.

### `chapter106` 코드에서의 역할

```kotlin
OutlinedTextField(
    value = editName,
    onValueChange = { editName = it },
    singleLine = true,
    modifier = Modifier
        .wrapContentSize()
        .padding(8.dp)
)
```

이 `OutlinedTextField`는 `Column` 안에 있고, 그 `Column`은 `fillMaxWidth()`가 걸린 `Row` 안에 있다. 여기서 `wrapContentSize()`는 텍스트 필드가 부모 폭에 맞춰 늘어나지 않고 자기 기본 크기를 쓰도록 만든다.

다만 실제로는 텍스트 필드가 스스로 기본 너비를 갖기 때문에 이 모디파이어가 없어도 결과가 같을 수 있다. 붙이기 전과 후를 프리뷰로 비교해 보면 효과가 있는지 바로 확인된다. **효과가 없다면 빼는 편이 낫다.** 의미 없는 모디파이어는 레이아웃 계산 단계를 늘리고 읽는 사람을 혼란스럽게 한다.

### 순서가 결과를 바꾼다

```kotlin
Modifier.wrapContentSize().padding(8.dp)   // 제약을 푼 뒤 안쪽 여백
Modifier.padding(8.dp).wrapContentSize()   // 여백을 뺀 공간에서 제약을 품
```

모디파이어는 체인 순서대로 적용되며 "각 함수가 이전 함수가 반환한 `Modifier`를 수정하므로 순서가 최종 결과에 직접 영향을 준다." 크기 관련 모디파이어는 특히 순서에 민감하다.

## 관련 아키텍처와 베스트 프랙티스

### 크기는 호출자가 정한다

재사용 컴포저블 안에서 `wrapContentSize()`나 `fillMaxWidth()`를 고정해 버리면 다른 화면에서 쓸 때 조정할 방법이 없다. Compose API 가이드라인이 권하는 형태는 `modifier` 파라미터를 받아 호출자에게 결정권을 넘기는 것이다.

```kotlin
@Composable
fun NameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = modifier)
}

// 호출자가 상황에 맞게 정한다
NameField(..., modifier = Modifier.fillMaxWidth())
NameField(..., modifier = Modifier.wrapContentSize())
```

### 크기 조정보다 배치로 푸는 편이 낫다

"이 요소만 작게 하고 싶다"는 요구는 대개 크기 모디파이어가 아니라 배치로 푸는 것이 더 명확하다.

```kotlin
// 크기를 억지로 줄이는 대신
Row(horizontalArrangement = Arrangement.SpaceBetween) { ... }
Column(horizontalAlignment = Alignment.CenterHorizontally) { ... }
Modifier.weight(1f)   // 남는 공간을 비율로 나눈다
```

`Arrangement`와 `Alignment`로 표현할 수 있는 의도를 크기 모디파이어로 흉내 내면, 화면 크기가 달라졌을 때 깨지기 쉽다.

### 레이아웃을 눈으로 확인하기

크기 모디파이어는 말로 추론하기 어렵다. 다음 도구로 확인하는 편이 빠르다.

- `@Preview`에서 붙인 경우와 뗀 경우를 나란히 본다.
- Layout Inspector로 실제 노드의 크기와 좌표, 전달된 제약을 확인한다.
- 디버깅 중에는 `Modifier.border(1.dp, Color.Red)`를 임시로 붙여 실제 차지하는 영역을 눈으로 본다.

### 사용자 상호작용 영역 확인

크기를 줄였을 때 터치 영역까지 함께 줄어드는지 확인한다. 최소 터치 영역 48dp를 밑돌면 접근성 검사에서 지적된다. Material 3 컴포넌트는 기본적으로 이 크기를 확보하지만, 크기 모디파이어로 강제로 줄이면 깨질 수 있다.

## 체크리스트

- [ ] `wrapContentSize()`가 최소 제약을 푼다는 점을 설명할 수 있다.
- [ ] 레이아웃 공간과 콘텐츠 크기가 다를 수 있음을 설명할 수 있다.
- [ ] `align` 파라미터의 기본 동작을 설명할 수 있다.
- [ ] `unbounded = true`가 필요한 상황과 위험을 설명할 수 있다.
- [ ] `wrapContentSize`, `size`, `defaultMinSize`, `fillMaxWidth`를 구분해 쓸 수 있다.
- [ ] 모디파이어 순서가 결과를 바꾸는 예를 들 수 있다.
- [ ] 재사용 컴포저블에서 크기를 고정하면 안 되는 이유를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers API: `androidx.compose.foundation.layout` 패키지 (`wrapContentSize`)](https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/package-summary)
- [Android Developers: Compose modifiers](https://developer.android.com/develop/ui/compose/modifiers)
- [Android Developers: Constraints and modifier order](https://developer.android.com/develop/ui/compose/layouts/constraints-modifiers)
- [Android Developers: Compose API guidelines](https://developer.android.com/develop/ui/compose/api-guidelines)
- [Android Developers: Accessibility in Compose](https://developer.android.com/develop/ui/compose/accessibility)
