# `Box` 컴포저블은 언제 사용하는가

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `Box` 컴포저블은 언제 사용하는가?

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
- 콘텐츠 없이 공간이나 배경만 필요하다면 `Box(modifier)` 형태의 오버로드를 쓸 수 있다. 단순한 빈 공간은 `Spacer`가 더 적절하다. [`compose-padding-vs-spacer.md`](compose-padding-vs-spacer.md) 참고.
- 겹침 순서를 바꾸려면 선언 순서를 바꾸거나 `Modifier.zIndex()`를 사용한다.

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
