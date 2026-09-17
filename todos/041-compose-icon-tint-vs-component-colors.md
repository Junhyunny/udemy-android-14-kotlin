# 아이콘 색은 `tint`로 바꾸나 `colors`로 지정하나

## 질문이 나온 코드

- [`chapter246/app/src/main/java/com/example/chapter_246/MainView.kt`](../chapter246/app/src/main/java/com/example/chapter_246/MainView.kt)
- 질문: `tint`를 사용해서 아이콘 색상을 바꾸는 것보다 `colors` 속성을 통해서 지정하는 게 BP인가?

```kotlin
val isSelected = currentRoute == item.bRoute
val tint = if (isSelected) Color.Red else Color.Black
NavigationBarItem(
    selected = currentRoute == item.bRoute,
    icon = {
        Icon(painterResource(item.icon), tint = tint, contentDescription = item.bTitle)
    },
    colors = NavigationBarItemColors(
        selectedIconColor = Color.White,
        unselectedIconColor = Color.Black,
        ...
    )
)
```

## 질문 전제 점검

- **결론부터 말하면 `colors`가 맞다. 그리고 지금 코드는 둘을 같이 써서 서로 충돌하고 있다.** `Icon`의 `tint`를 명시하면 `colors`의 `selectedIconColor`/`unselectedIconColor`는 **아무 효과가 없다.** 선택했을 때 `White`가 나와야 하는데 `tint`가 `Red`로 덮어쓴다.

  `Icon`의 기본 동작이 이것을 설명한다.

  > "By default, the `Icon` composable is tinted with `LocalContentColor.current` and is 24.dp in size."

  `NavigationBarItem`은 **선택 상태에 따라 `LocalContentColor`를 갈아 끼우고** 아이콘 슬롯을 호출한다. `tint`를 생략하면 아이콘이 그 값을 받아 자동으로 색이 바뀐다. `tint`를 직접 주는 순간 그 경로가 끊긴다.

- **그래서 `isSelected`와 `tint`를 손으로 계산하는 코드 자체가 불필요하다.** `selected = ...`를 이미 넘기고 있으므로 컴포넌트가 선택 상태를 안다. 같은 정보를 두 번 계산하고 있는 셈이다.

- **`NavigationBarItemColors(...)`를 생성자로 직접 만드는 것도 권장되는 형태가 아니다.** `NavigationBarItemDefaults.colors()`를 쓰고 **바꾸고 싶은 것만 이름 인자로 지정**하는 것이 정석이다. 생성자를 직접 부르면 **모든 색을 빠짐없이 적어야 하고**, 라이브러리가 색 항목을 추가하면 컴파일이 깨진다. 실제로 최근 Material3에는 `selectedTextColorStartIconPosition` 같은 항목이 추가됐다.

- **`Color.Red`, `Color.Black`처럼 색을 직접 박는 것도 별개의 문제다.** 다크 모드에서 그대로 깨진다. 이건 `tint`냐 `colors`냐와 무관하게 **테마 색 역할(color role)을 쓰지 않은 것**이 원인이다.

## 공부할 내용

### 세 겹의 색 결정 구조

Compose Material3에서 아이콘 색은 아래로 갈수록 우선순위가 높다.

```
1. MaterialTheme.colorScheme         — 앱 전체 색 팔레트
2. 컴포넌트의 colors 파라미터          — 이 컴포넌트가 상태별로 쓸 색
3. LocalContentColor                  — 슬롯 안으로 흘러드는 "현재 콘텐츠 색"
4. Icon(tint = ...)                   — 이 아이콘 하나를 못 박는다
```

**아래로 내려갈수록 적용 범위가 좁아지고, 테마와의 연결이 끊긴다.** 그래서 판단 기준은 단순하다. **가능한 한 위쪽에서 해결한다.**

### 고쳐 쓴 형태

```kotlin
NavigationBarItem(
    selected = currentRoute == item.bRoute,
    onClick = { ... },
    icon = {
        // tint 를 주지 않는다 — LocalContentColor 를 따라간다
        Icon(painterResource(item.icon), contentDescription = item.bTitle)
    },
    label = { Text(item.title) },
    colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
    ),
)
```

세 가지가 한꺼번에 해결된다.

- 선택 상태를 **한 곳(`selected`)에서만** 판단한다
- 지정하지 않은 색은 **Material 기본값**이 채운다
- 다크 모드에서 `colorScheme`이 알아서 바뀐다

### `tint`를 써야 하는 자리도 있다

`tint`가 나쁜 게 아니라 **자리가 있다.** `colors` 파라미터가 없는 곳에서는 `tint`가 유일한 수단이다.

```kotlin
// 컴포넌트가 아닌 단독 아이콘
Icon(
    imageVector = Icons.Default.Warning,
    contentDescription = "경고",
    tint = MaterialTheme.colorScheme.error   // 이 자리에서는 tint 가 맞다
)

// 원본 색을 그대로 써야 하는 이미지 (브랜드 로고 등)
Icon(painterResource(R.drawable.logo), contentDescription = null, tint = Color.Unspecified)
```

`Color.Unspecified`는 **틴트를 끄는** 값이다. 다색 아이콘이 단색으로 뭉개질 때 쓴다.

| 상황 | 선택 |
| --- | --- |
| 컴포넌트가 상태(선택/비활성)에 따라 색을 바꿔야 한다 | `colors` |
| 컴포넌트에 `colors`가 있다 | `colors` |
| 단독 `Icon`에 의미색(에러/성공)을 준다 | `tint` |
| 다색 아이콘의 원본 색을 살린다 | `tint = Color.Unspecified` |
| 영역 전체의 콘텐츠 색을 바꾼다 | `CompositionLocalProvider(LocalContentColor provides ...)` |

### `LocalContentColor`가 하는 일

`tint`를 생략했을 때 색이 어디서 오는지 알아야 이 구조가 이해된다. `LocalContentColor`는 **`CompositionLocal`**이고, 컴포지션 트리를 타고 아래로 흐른다.

```kotlin
CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
    Icon(Icons.Default.Warning, contentDescription = null)   // 자동으로 error 색
    Text("문제가 발생했습니다")                                 // 텍스트도 같이 바뀐다
}
```

Material3 컴포넌트들은 슬롯을 호출하기 전에 이 값을 적절히 바꿔 준다. **`Surface`는 `containerColor`에 맞는 `contentColor`를, `Button`은 `contentColor`를, `NavigationBarItem`은 선택 상태에 맞는 아이콘 색을 제공한다.** 그래서 슬롯 안에서 `tint`를 생략하는 것이 "색을 포기하는 것"이 아니라 **"컴포넌트에게 맡기는 것"**이다.

→ [`021-compose-localcontext.md`](021-compose-localcontext.md)에서 다룬 `CompositionLocal`과 같은 메커니즘이다.

### `*Defaults.colors()` 관용구

Material3 컴포넌트는 거의 전부 같은 패턴을 갖는다.

```kotlin
ButtonDefaults.buttonColors(containerColor = ...)
CardDefaults.cardColors(containerColor = ...)
TopAppBarDefaults.topAppBarColors(titleContentColor = ...)
NavigationBarItemDefaults.colors(selectedIconColor = ...)
TextFieldDefaults.colors(focusedIndicatorColor = ...)
```

**이름 인자로 일부만 덮어쓰고 나머지는 기본값을 유지한다**는 점이 핵심이다. `copy()`를 쓸 수 있는 경우도 있다.

```kotlin
val base = NavigationBarItemDefaults.colors()
val custom = base.copy(selectedIconColor = MaterialTheme.colorScheme.primary)
```

## 관련 아키텍처와 베스트 프랙티스

### 색 역할(color role)로 생각한다

Material 3는 색을 **"무슨 색인가"가 아니라 "어떤 역할인가"**로 정의한다.

| 쓰지 않는다 | 쓴다 |
| --- | --- |
| `Color.Red` | `MaterialTheme.colorScheme.error` |
| `Color.Black` | `MaterialTheme.colorScheme.onSurface` |
| `Color.White` | `MaterialTheme.colorScheme.onPrimary` |
| `Color.DarkGray` | `MaterialTheme.colorScheme.surfaceVariant` |

`on-` 접두사는 **"그 배경 위에 올라가는 색"**이라는 뜻이다. `onPrimary`는 `primary` 위에 올릴 때 대비가 보장된다. 이 규칙을 지키면 **접근성 대비와 다크 모드가 공짜로 따라온다.**

→ [`014-android-resources-r-class-and-compose-theme.md`](014-android-resources-r-class-and-compose-theme.md)

### 선택 상태는 컴포넌트에 맡긴다

```kotlin
// 나쁨: 선택 상태를 두 번 계산한다
val isSelected = currentRoute == item.bRoute
val tint = if (isSelected) Color.Red else Color.Black
NavigationBarItem(selected = currentRoute == item.bRoute, icon = { Icon(..., tint = tint) })

// 좋음: 한 번만 계산해서 넘긴다
NavigationBarItem(selected = currentRoute == item.bRoute, icon = { Icon(...) }, colors = ...)
```

상태 계산이 중복되면 **한쪽만 고쳐서 어긋나는 버그**가 생긴다.

### 색만으로 상태를 표현하지 않는다

Material Design 3는 내비게이션 바에서 **아이콘 모양 자체를 바꾸라**고 안내한다.

> "In Material Design 3, inactive destinations are indicated by an outlined version of the icon, while active destinations are indicated by a filled icon enclosed in a pill-shaped container."

색약·저시력 사용자에게는 색 변화만으로 부족하기 때문이다. 채움/윤곽선 변형이 있다면 그것을 쓰고, 없다면 선택된 항목에만 라벨을 보이는 등 **다른 단서**를 함께 준다.

```kotlin
icon = {
    Icon(
        imageVector = if (isSelected) Icons.Filled.Home else Icons.Outlined.Home,
        contentDescription = item.bTitle
    )
}
```

이때는 `isSelected`를 다시 계산하는 대신 위에서 한 번 계산한 값을 재사용한다.

### 색을 컴포저블마다 흩뿌리지 않는다

같은 커스터마이징을 여러 곳에서 반복하게 되면, 테마를 고치거나 감싸는 컴포저블을 만든다.

```kotlin
@Composable
fun AppNavigationBarItem(selected: Boolean, item: Screen.BottomBarScreen, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(painterResource(item.icon), contentDescription = item.bTitle) },
        label = { Text(item.bTitle) },
        colors = NavigationBarItemDefaults.colors(...)   // 색은 여기 한 곳에만
    )
}
```

**"색을 바꾸고 싶으면 몇 군데를 고쳐야 하는가"**가 설계가 맞는지 판별하는 기준이다.

## 체크리스트

- [ ] `Icon`의 `tint` 기본값이 `LocalContentColor.current`임을 안다.
- [ ] `tint`를 명시하면 `colors`의 아이콘 색이 무시되는 이유를 설명할 수 있다.
- [ ] `NavigationBarItemDefaults.colors()`를 생성자 직접 호출 대신 써야 하는 이유를 안다.
- [ ] 색 결정의 네 겹 구조(테마 → colors → LocalContentColor → tint)를 설명할 수 있다.
- [ ] `tint`가 적절한 상황을 구분할 수 있다.
- [ ] `Color.Unspecified`의 용도를 안다.
- [ ] `CompositionLocalProvider`로 영역 전체의 콘텐츠 색을 바꿀 수 있다.
- [ ] `on-` 접두사 색 역할의 의미를 설명할 수 있다.
- [ ] 하드코딩 색이 다크 모드에서 문제가 되는 이유를 안다.
- [ ] 색만으로 선택 상태를 표현하면 안 되는 이유를 안다.

## 공식 참고 자료

- [Android Developers: Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Android Developers: Icons in Compose](https://developer.android.com/develop/ui/compose/graphics/images/material)
- [Android Developers: `NavigationBarItem`](https://developer.android.com/reference/kotlin/androidx/compose/material3/NavigationBarItem.composable)
- [Android Developers: `NavigationBarItemDefaults`](https://developer.android.com/reference/kotlin/androidx/compose/material3/NavigationBarItemDefaults)
- [Android Developers: Locally scoped data with CompositionLocal](https://developer.android.com/develop/ui/compose/compositionlocal)
- [Android Developers: Build adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation)
- [Material Design 3: Navigation bar](https://m3.material.io/components/navigation-bar)
- [Material Design 3: Color roles](https://m3.material.io/styles/color/roles)
