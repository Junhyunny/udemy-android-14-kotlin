# 바텀 시트는 상태 플래그로 여나 `SheetState`로 여나

## 질문이 나온 코드

- [`chapter246/app/src/main/java/com/example/chapter_246/MainView.kt`](../chapter246/app/src/main/java/com/example/chapter_246/MainView.kt)
- 질문: 이렇게 상태로 처리해도 정상 동작하고, `State` 객체의 `show`, `hide`를 사용해도 정상 동작한다. 어떤 게 BP인가?

```kotlin
var showBottomSheet by remember { mutableStateOf(false) }
val sheetState = rememberModalBottomSheetState()

IconButton(onClick = {
    showBottomSheet = true                       // 방법 1
    scope.launch {                               // 방법 2
        if (sheetState.isVisible) sheetState.hide() else sheetState.show()
    }
})

if (showBottomSheet) {
    ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState) {
        MoreBottomSheet(modifier = Modifier)
    }
}
```

## 질문 전제 점검

- **"둘 다 정상 동작한다"는 전제가 정확하지 않다. 둘은 경쟁 관계가 아니라 역할이 다르다.** 하나를 고르는 문제가 아니라 **각자 무엇을 담당하는지**를 나누는 문제다.

  | | 담당 |
  | --- | --- |
  | `showBottomSheet` (`Boolean`) | `ModalBottomSheet` 컴포저블이 **컴포지션에 있는가 없는가** |
  | `sheetState.show()` / `hide()` | 이미 컴포지션에 있는 시트를 **애니메이션으로 올리고 내리는가** |

  **컴포지션에 없는 시트에는 `show()`를 호출해도 올라올 것이 없다.** 그래서 `showBottomSheet = true`가 먼저 필요하고, `show()`는 그다음 이야기다. 지금 코드에서 `show()`가 동작하는 것처럼 보이는 이유는 `showBottomSheet = true`가 **바로 위 줄에서 이미 실행됐기** 때문이다. 그 줄을 지우면 `show()`만으로는 아무 일도 일어나지 않는다.

- **그리고 지금 코드에서 `scope.launch { ... }` 블록은 사실상 불필요하고, 해롭기까지 하다.** `ModalBottomSheet`는 **컴포지션에 진입할 때 스스로 열리는 애니메이션을 재생**한다. 그 위에 `show()`를 또 호출하는 것이다. 게다가 조건이 `if (sheetState.isVisible) hide() else show()`인데, 바로 위에서 `showBottomSheet = true`를 해 놓고 `hide()` 분기가 실행될 수 있는 구조라 **열자마자 닫히는 경합**이 성립한다. 시트가 이미 보이는 상태에서 버튼을 다시 누르면 이 분기로 들어간다.

- **`hide()`만 호출하는 것도 충분하지 않다.** `hide()`는 시트를 내리는 애니메이션만 재생한다. `showBottomSheet`를 `false`로 되돌리지 않으면 **`ModalBottomSheet`가 컴포지션에 남는다.** 공식 문서가 명시적으로 경고하는 부분이다.

  > "Make sure to remove the `ModalBottomSheet` from composition upon hiding the bottom sheet."

- **`onDismissRequest`는 반드시 `showBottomSheet = false`를 해야 한다.** 지금 코드는 이건 제대로 하고 있다. 사용자가 바깥을 탭하거나 아래로 스와이프하거나 뒤로 가기를 누르면 이 콜백이 불린다.

## 공부할 내용

### 공식 예제 — 전체 구조

```kotlin
val sheetState = rememberModalBottomSheetState()
val scope = rememberCoroutineScope()
var showBottomSheet by remember { mutableStateOf(false) }

Scaffold(
    floatingActionButton = {
        ExtendedFloatingActionButton(
            text = { Text("Show bottom sheet") },
            icon = { Icon(Icons.Filled.Add, contentDescription = "") },
            onClick = { showBottomSheet = true }          // ← 열 때는 플래그만
        )
    }
) { contentPadding ->
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },   // ← 사용자 제스처로 닫힘
            sheetState = sheetState
        ) {
            Button(onClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        showBottomSheet = false              // ← 애니메이션 끝난 뒤 제거
                    }
                }
            }) {
                Text("Hide bottom sheet")
            }
        }
    }
}
```

세 갈래를 구분해서 보면 된다.

| 동작 | 코드 |
| --- | --- |
| **연다** | `showBottomSheet = true` — 이것만 한다 |
| **사용자가 닫는다** (스와이프·바깥 탭·뒤로 가기) | `onDismissRequest = { showBottomSheet = false }` |
| **코드가 닫는다** (시트 안 버튼 등) | `hide()` → 애니메이션 완료 후 `showBottomSheet = false` |

### `invokeOnCompletion`이 필요한 이유

`hide()`는 `suspend` 함수라서 **애니메이션이 끝날 때까지 중단**된다. 바로 `showBottomSheet = false`를 하면 시트가 **내려가지도 못하고 툭 사라진다.**

```kotlin
// 나쁨: 애니메이션이 잘린다
scope.launch {
    sheetState.hide()
    showBottomSheet = false
}
```

...사실 이 형태도 `hide()`가 끝난 뒤에 실행되므로 동작은 한다. 공식 예제가 `invokeOnCompletion`을 쓰는 이유는 **코루틴이 취소됐을 때도 정리하기 위해서**다. 화면을 벗어나 `scope`가 취소되면 `hide()` 다음 줄은 실행되지 않지만, `invokeOnCompletion`은 취소 시에도 불린다. 그래서 `!sheetState.isVisible` 검사가 함께 들어간다.

> "you can use `sheetState.hide()` with `.invokeOnCompletion` to only set `showBottomSheet = false` after the animation completes"

### `rememberModalBottomSheetState`의 옵션

```kotlin
val sheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = true,          // 중간 높이를 건너뛰고 바로 전체 확장
    confirmValueChange = { newValue ->
        newValue != SheetValue.Hidden      // 특정 상태로의 전환을 막는다
    }
)
```

`confirmValueChange`로 **사용자가 스와이프로 닫는 것을 막을 수 있다.** 필수 입력이 있는 시트에서 쓴다. 다만 뒤로 가기까지 막는 것은 접근성 관점에서 좋지 않으므로, 막았다면 **닫는 다른 경로를 반드시 제공**해야 한다.

`SheetValue`는 `Hidden`, `PartiallyExpanded`, `Expanded` 세 가지다. `isVisible`은 `Hidden`이 아닌 상태를 뜻한다.

### 이 코드에서 정리할 부분

```kotlin
IconButton(onClick = {
    showBottomSheet = true          // 이것만 남긴다
}) {
    Icon(imageVector = Icons.Default.Menu, contentDescription = "메뉴 열기")
}
```

`scope.launch { ... }` 블록을 지운다. 만약 **토글**(누르면 열리고, 열려 있으면 닫히는) 동작을 원한다면 이렇게 쓴다.

```kotlin
IconButton(onClick = {
    if (showBottomSheet) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) showBottomSheet = false
        }
    } else {
        showBottomSheet = true
    }
})
```

**판단 기준이 `sheetState.isVisible`이 아니라 `showBottomSheet`라는 점**이 중요하다. 컴포지션 존재 여부가 진짜 기준이기 때문이다.

### `isSheetFullScreen`이 `var`가 아닌 이유

같은 코드에 이런 줄이 있다.

```kotlin
val isSheetFullScreen by remember { mutableStateOf(true) }
val modifier = if (isSheetFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
val roundedCornerRadius = if (isSheetFullScreen) 0.dp else 12.dp
```

`val`로 읽기만 하고 바꾸는 코드가 없으므로 **상태일 필요가 없다.** `val isSheetFullScreen = true`면 충분하다. 값이 변하지 않는데 `mutableStateOf`로 감싸면 **"이건 바뀔 수 있다"는 잘못된 신호**를 준다. → [`034-compose-remember-mutablestate-and-by.md`](034-compose-remember-mutablestate-and-by.md)

## 관련 아키텍처와 베스트 프랙티스

### "표시 여부"는 상태, "애니메이션"은 명령

Compose 전반에 통하는 구분이다.

| | 예 |
| --- | --- |
| **선언적 상태** — 무엇을 보여 줄지 | `if (showBottomSheet) { ... }`, `if (dialogOpen) { ... }` |
| **명령적 호출** — 어떻게 움직일지 | `sheetState.hide()`, `drawerState.open()`, `listState.animateScrollToItem()` |

같은 파일의 드로어도 같은 구조다.

```kotlin
val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
scope.launch { drawerState.open() }
```

**드로어는 `ModalNavigationDrawer`가 항상 컴포지션에 있으므로 플래그가 필요 없다.** 바텀 시트만 "컴포지션에서 빼야 한다"는 제약이 추가로 있다. 이 차이가 헷갈리는 원인이다.

| 컴포넌트 | 컴포지션 상주 | 제어 방법 |
| --- | --- | --- |
| `ModalNavigationDrawer` | 항상 있다 | `drawerState.open()` / `close()` |
| `ModalBottomSheet` | **조건부로 넣는다** | `showBottomSheet` + `sheetState` |
| `AlertDialog` | **조건부로 넣는다** | 플래그만 (애니메이션 상태 없음) |

### 상태를 어디에 둘 것인가

시트 표시 여부가 **화면 상태의 일부**라면 `ViewModel`로 올린다.

```kotlin
class MainViewModel : ViewModel() {
    var showMoreSheet by mutableStateOf(false)
        private set

    fun openMoreSheet() { showMoreSheet = true }
    fun closeMoreSheet() { showMoreSheet = false }
}
```

반면 **`SheetState`는 ViewModel에 두지 않는다.** 애니메이션 상태는 컴포지션에 묶인 UI 요소이고, 화면 회전 시 다시 만들어져야 한다. → [`048-android-viewmodel-encapsulation-and-async-timing.md`](048-android-viewmodel-encapsulation-and-async-timing.md)

| 상태 | 위치 |
| --- | --- |
| "시트를 보여야 하는가" | `ViewModel` 또는 `rememberSaveable` |
| `SheetState` (애니메이션 진행 상태) | `remember` — 컴포저블 안 |

### 구성 변경을 견디려면 `rememberSaveable`

```kotlin
var showBottomSheet by rememberSaveable { mutableStateOf(false) }
```

`remember`만 쓰면 **화면을 회전했을 때 시트가 사라진다.** 강의 예제에서는 문제가 안 보이지만 실제 앱에서는 체감되는 차이다.

### 접근성과 뒤로 가기

`ModalBottomSheet`는 **뒤로 가기 처리와 포커스 가두기를 기본 제공**한다. 직접 `Box`로 시트를 흉내 내면 이런 것들을 전부 다시 만들어야 한다. 컴포넌트를 쓰는 값어치가 여기 있다.

### 시트 내용은 별도 컴포저블로 분리한다

```kotlin
if (showBottomSheet) {
    ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState) {
        MoreBottomSheet(onSettingsClick = { ... })
    }
}
```

지금 코드도 `MoreBottomSheet`로 분리돼 있어 이 부분은 잘 되어 있다. 다만 `MoreBottomSheet(modifier: Modifier)`처럼 **`Modifier`를 필수 파라미터로 받는 것**은 관례에서 벗어난다. Compose API 가이드라인은 `modifier: Modifier = Modifier`를 **기본값 있는 첫 선택 파라미터**로 두라고 안내한다.

## 체크리스트

- [ ] `showBottomSheet` 플래그와 `SheetState`의 역할 차이를 설명할 수 있다.
- [ ] 컴포지션에 없는 시트에 `show()`를 불러도 소용없는 이유를 안다.
- [ ] `hide()`만 호출하면 왜 부족한지 설명할 수 있다.
- [ ] `onDismissRequest`가 언제 불리는지 안다.
- [ ] `invokeOnCompletion`을 쓰는 이유(취소 대응)를 설명할 수 있다.
- [ ] `SheetValue`의 세 상태와 `isVisible`의 의미를 안다.
- [ ] `skipPartiallyExpanded`, `confirmValueChange`의 용도를 안다.
- [ ] 드로어와 바텀 시트의 제어 방식이 다른 이유를 설명할 수 있다.
- [ ] `SheetState`를 ViewModel에 두면 안 되는 이유를 안다.
- [ ] `rememberSaveable`이 필요한 상황을 판단할 수 있다.
- [ ] 값이 변하지 않는 값을 `mutableStateOf`로 감싸면 안 되는 이유를 안다.

## 공식 참고 자료

- [Android Developers: Bottom sheets](https://developer.android.com/develop/ui/compose/components/bottom-sheets)
- [Android Developers: Partial bottom sheet](https://developer.android.com/develop/ui/compose/components/bottom-sheets-partial)
- [Android Developers: `SheetState`](https://developer.android.com/reference/kotlin/androidx/compose/material3/SheetState)
- [Android Developers: `ModalBottomSheetProperties`](https://developer.android.com/reference/kotlin/androidx/compose/material3/ModalBottomSheetProperties)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
- [Android Developers: State hoisting](https://developer.android.com/develop/ui/compose/state-hoisting)
- [Android Developers: Compose API guidelines](https://developer.android.com/develop/ui/compose/api-guidelines)
- [Material Design 3: Bottom sheets](https://m3.material.io/components/bottom-sheets)
