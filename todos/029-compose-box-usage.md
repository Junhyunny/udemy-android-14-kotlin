# `Box` 컴포저블은 언제 사용하는가

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `Box` 컴포저블은 언제 사용하는가?

## 질문 전제 점검

질문에 잘못된 전제는 없다. 다만 `Box`를 "다용도 컨테이너"로 받아들이면 남용하기 쉬워서 한 가지를 분명히 해둔다.

`Box`는 범용 `div`가 아니라 **자식을 같은 좌표 공간에 겹쳐 놓는 레이아웃**이다. 겹칠 일도, 정렬할 일도, 앵커로 삼을 일도 없다면 `Box`는 아무 일도 하지 않는 래퍼일 뿐이다. 웹의 `div`처럼 "일단 감싸고 보는" 습관을 그대로 가져오면 불필요한 레이아웃 노드가 쌓인다.

## 공부할 내용

`Box`는 세 가지 기본 레이아웃 컴포저블 중 "요소를 다른 요소 위에 올려놓는" 역할을 한다. `Column`은 수직, `Row`는 수평, `Box`는 겹침이다. 자식들은 선언 순서대로 그려지므로 나중에 선언한 것이 위에 온다.

### 대표적인 사용 상황

1. **겹치기**: 이미지 위에 배지나 체크 아이콘을 올리는 경우.

   ```kotlin
   Box {
       Image(bitmap = artist.image, contentDescription = "Artist image")
       Icon(Icons.Filled.Check, contentDescription = "Check mark")
   }
   ```

2. **기준점 잡기**: 자식 하나를 특정 위치에 정렬해야 할 때 `contentAlignment`나 자식의 `Modifier.align()`을 쓴다.

   ```kotlin
   Box(contentAlignment = Alignment.Center) { Text("가운데") }
   ```

3. **앵커 제공**: 팝업, 드롭다운, 툴팁처럼 특정 요소를 기준으로 떠 있는 UI를 붙일 때. `chapter078`의 사용이 여기에 해당한다.

   ```kotlin
   Box {
       Button(onClick = { open = true }) { ... }
       DropdownMenu(expanded = open, onDismissRequest = { open = false }) { ... }
   }
   ```

   `DropdownMenu`는 자신을 감싼 부모를 기준으로 위치를 잡는다. `Box`가 버튼과 메뉴를 한 좌표 공간에 묶어주므로 메뉴가 버튼 위치에 맞춰 열린다. 이때 `Box`는 자식 중 가장 큰 것의 크기를 따르며, 메뉴는 팝업으로 그려지므로 `Box`의 크기를 늘리지 않는다.

4. **배경·오버레이**: 콘텐츠 위에 반투명 레이어나 로딩 인디케이터를 덮을 때.

### 주의할 점

- 자식이 하나뿐이고 겹칠 일도, 정렬할 일도 없다면 `Box`는 불필요한 래퍼다. 그 경우 자식에 `Modifier`를 직접 붙이는 편이 낫다.
- 콘텐츠 없이 공간이나 배경만 필요하다면 `Box(modifier)` 형태의 오버로드를 쓸 수 있다. 단순한 빈 공간은 `Spacer`가 더 적절하다. [`030-compose-padding-vs-spacer.md`](030-compose-padding-vs-spacer.md) 참고.
- 겹침 순서를 바꾸려면 선언 순서를 바꾸거나 `Modifier.zIndex()`를 사용한다.

## 관련 아키텍처와 베스트 프랙티스

### 세 레이아웃의 선택 기준

```
자식들을 나란히 놓아야 한다  → 수직이면 Column, 수평이면 Row
자식들을 겹쳐 놓아야 한다     → Box
자식을 감싸지 않고 꾸미기만    → Modifier (background, border, padding)
```

마지막 항목이 특히 자주 놓친다. 배경색이나 테두리를 주려고 `Box`로 감싸는 경우가 많은데, 대개는 대상 컴포저블에 `Modifier.background(...)`를 붙이면 끝난다.

### 앵커 패턴

`chapter078`처럼 버튼과 그 버튼에 붙는 팝업을 함께 두는 구성은 흔한 UI 패턴이다.

```kotlin
Box {
    Button(onClick = { open = true }) { ... }   // 앵커
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) { ... }
}
```

`DropdownMenu`는 별도의 팝업 창에 그려지므로 `Box`의 크기에 영향을 주지 않는다. `Box`는 "메뉴가 어느 지점을 기준으로 열려야 하는가"를 알려주는 좌표 기준 역할만 한다. `ExposedDropdownMenuBox`처럼 텍스트 필드와 메뉴를 묶어주는 전용 컴포넌트가 있는 경우에는 그쪽을 먼저 검토한다.

### `Box`, `Surface`, `Scaffold` 구분

혼동하기 쉬운 세 가지를 역할로 나눠 두면 좋다.

| 컴포저블 | 역할 |
| --- | --- |
| `Box` | 순수 레이아웃. 겹침과 정렬만 담당 |
| `Surface` | Material 개념. 배경색·모양·그림자·콘텐츠 색상을 함께 제공 |
| `Scaffold` | 화면 골격. 앱 바, FAB, 스낵바 슬롯과 인셋 처리 제공 |

Material 디자인을 따르는 배경면이 필요하면 `Box` + `background`보다 `Surface`가 낫다. 콘텐츠 색상이 자동으로 대비되도록 맞춰지기 때문이다.

### 접근성 고려

겹친 UI는 화면 낭독기 사용자에게 순서가 모호해질 수 있다. 장식용 요소라면 `contentDescription = null`로 두고, 여러 요소가 하나의 의미 단위라면 `Modifier.semantics(mergeDescendants = true)`로 묶어 하나로 읽히게 한다.

## 체크리스트

- [ ] `Box`가 자식을 겹쳐 배치한다는 점과 그리기 순서를 설명할 수 있다.
- [ ] `contentAlignment`와 자식의 `Modifier.align()` 차이를 설명할 수 있다.
- [ ] `DropdownMenu`를 `Box`로 감싸는 이유를 설명할 수 있다.
- [ ] `Box`가 불필요한 상황을 판단할 수 있다.
- [ ] 겹침 순서를 제어하는 방법을 두 가지 말할 수 있다.

## 공식 참고 자료

- [Android Developers: Compose layout basics](https://developer.android.com/develop/ui/compose/layouts/basics)
- [Android Developers API: Box](https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/package-summary#Box(androidx.compose.ui.Modifier,androidx.compose.ui.Alignment,kotlin.Boolean,kotlin.Function1))
- [Android Developers API: DropdownMenu](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#DropdownMenu(kotlin.Boolean,kotlin.Function0,androidx.compose.ui.Modifier,androidx.compose.ui.unit.DpOffset,androidx.compose.foundation.ScrollState,androidx.compose.ui.window.PopupProperties,androidx.compose.foundation.layout.PaddingValues,androidx.compose.ui.graphics.Shape,androidx.compose.ui.graphics.Color,androidx.compose.ui.unit.Dp,androidx.compose.ui.graphics.Color,androidx.compose.foundation.BorderStroke,kotlin.Function1))
- [Android Developers: Menus in Compose](https://developer.android.com/develop/ui/compose/components/menu)
- [Android Developers: Accessibility in Compose](https://developer.android.com/develop/ui/compose/accessibility)
- [Android Developers: Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Android Developers: Scaffold](https://developer.android.com/develop/ui/compose/components/scaffold)
