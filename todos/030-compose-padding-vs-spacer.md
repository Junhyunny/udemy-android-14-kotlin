# 간격 주기: `padding`과 `Spacer` 중 무엇을 쓸까

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `padding` 방식과 `Spacer` 방식 중 레이아웃을 맞추기 쉬운 쪽은 무엇인가? 안드로이드 진영에서 권장하는 방식이 있는가?

## 질문 전제 점검

- **"`padding` 방식과 `Spacer` 방식이 있는데 어떤 방식이 더 유용해?"** → 질문이 이분법으로 좁혀져 있다. 실제로는 세 번째 수단인 `Arrangement.spacedBy`가 있고, 셋은 우열 관계가 아니라 **목적이 다른 도구**다. "무엇이 더 낫냐"보다 "이 간격은 누구의 책임인가"를 물으면 답이 정해진다.
- **"어떤 방식이 레이아웃이나 디자인을 맞추기 쉬운가"** → 맞추기 쉬운 쪽을 굳이 꼽으면 반복되는 간격에는 `Arrangement.spacedBy`다. 간격 값이 한곳에 모이고 자식이 늘거나 줄어도 따라 수정할 곳이 없기 때문이다.
- 한 가지 더 짚으면, Compose에는 뷰 시스템이나 CSS의 **margin이 없다**. `Spacer`를 margin 대용으로 쓰는 습관이 여기서 생기는데, 대응하는 개념은 부모의 `Arrangement`이거나 호출자가 넘기는 `Modifier.padding`이다.

## 공부할 내용

### 셋은 목적이 다르다

Compose에는 간격을 만드는 수단이 세 가지 있고, 우열이 아니라 용도로 나뉜다.

| 수단 | 의미 | 적합한 상황 |
| --- | --- | --- |
| `Modifier.padding` | 요소 자신의 안쪽 여백 | 컴포넌트가 스스로 확보해야 할 여백, 터치 영역 확장 |
| `Arrangement.spacedBy` | 컨테이너가 자식들 사이에 두는 간격 | `Column`/`Row`에서 자식 사이 균일한 간격 |
| `Spacer` | 공간을 차지하는 빈 컴포저블 | 일회성의 예외적인 간격, 남는 공간 밀어내기(`weight`) |

`Modifier.padding`은 "요소 주위에 공간을 추가"하고 요소의 측정값 자체를 바꾼다. Compose에는 뷰 시스템의 margin 개념이 따로 없고, 바깥 여백도 padding 모디파이어의 순서로 표현한다. 문서는 "모디파이어 함수의 순서가 중요하다"는 점을 강조한다. `clickable` 뒤에 `padding`을 두면 여백까지 클릭 영역이 되고, 앞에 두면 여백은 클릭에 반응하지 않는다.

`Arrangement.spacedBy(space)`는 "인접한 두 자식이 주축에서 고정된 간격만큼 떨어지도록 배치"한다. 자식 개수가 바뀌어도 간격 규칙이 유지된다.

### 권장 기준

공식 문서가 "무조건 이것"이라고 못 박지는 않지만, 문서와 Material 가이드가 제시하는 실무 기준은 정리할 수 있다.

- 컨테이너 내부 자식들 사이의 **반복되는 균일한 간격**은 `Arrangement.spacedBy`가 가장 적합하다. 간격 값이 한 곳에 모이고, 자식을 추가·삭제해도 `Spacer`를 함께 손볼 필요가 없다. `chapter078`의 `Column`은 `Spacer(Modifier.height(16.dp))`를 세 번 반복하는데, `verticalArrangement = Arrangement.spacedBy(16.dp)` 한 줄로 대체할 수 있다.
- 컴포넌트가 **어디에 놓이든 항상 가져야 하는 여백**은 그 컴포넌트 내부에서 `Modifier.padding`으로 처리한다. 재사용 컴포저블은 자신의 바깥 여백을 스스로 정하기보다 `modifier` 파라미터로 호출자에게 위임하는 것이 Compose API 관례다.
- **화면 가장자리 여백**(콘텐츠 인셋)은 컨테이너의 `Modifier.padding`으로 준다.
- `Spacer`는 규칙에서 벗어나는 **예외적인 간격**이나, `Modifier.weight(1f)`를 붙여 남은 공간을 밀어낼 때 쓴다.

### 판단 기준 요약

간격이 "요소의 속성"이면 `padding`, "요소들 사이의 관계"면 `spacedBy`, "그 자리에만 필요한 빈 공간"이면 `Spacer`다.

## 관련 아키텍처와 베스트 프랙티스

### 간격의 책임을 정하는 기준

| 질문 | 답이 '그렇다'면 |
| --- | --- |
| 이 여백은 컴포넌트가 어디에 놓이든 항상 필요한가 | 컴포넌트 내부 `Modifier.padding` |
| 이 간격은 형제 요소들 사이의 규칙인가 | 부모의 `Arrangement.spacedBy` |
| 이 컴포넌트가 화면에서 차지할 위치의 문제인가 | 호출자가 `modifier` 파라미터로 지정 |
| 그 자리에만 필요한 예외적인 빈 공간인가 | `Spacer` |

핵심 원칙은 **재사용 컴포저블은 자신의 바깥 여백을 스스로 정하지 않는다**는 것이다. 바깥 여백을 내부에 박아 두면 다른 화면에서 그 컴포넌트를 쓸 때 여백을 지울 방법이 없다.

### 리팩터링 예시

`chapter078`의 `Column`은 같은 높이의 `Spacer`를 세 번 반복한다.

```kotlin
// 이전
Column {
    Text("Unit Converter")
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(value = "", onValueChange = {})
    Spacer(modifier = Modifier.height(16.dp))
    Row { ... }
    Spacer(modifier = Modifier.height(16.dp))
    Text("Result: ")
}

// 이후
Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Unit Converter")
    OutlinedTextField(value = "", onValueChange = {})
    Row { ... }
    Text("Result: ")
}
```

간격 규칙이 선언 한 줄로 드러나고, 항목을 추가해도 `Spacer`를 함께 넣을 필요가 없다.

### `Spacer`가 오히려 정답인 경우

```kotlin
Row {
    Text("왼쪽")
    Spacer(modifier = Modifier.weight(1f))   // 남는 공간을 모두 차지
    Text("오른쪽")
}
```

고정 간격이 아니라 **남는 공간을 밀어내는** 용도라면 `Spacer` + `weight`가 가장 명확하다. `Arrangement.SpaceBetween`으로도 표현할 수 있으니 자식이 둘뿐이라면 그쪽이 더 간결하다.

### 스크롤 컨테이너의 `contentPadding`

`LazyColumn`에서 위아래 여백을 주려고 `Modifier.padding`을 쓰면 스크롤 영역 자체가 줄어들어 콘텐츠가 가장자리에서 잘린 것처럼 보인다. 이때는 `contentPadding`을 쓴다. 스크롤되는 콘텐츠에만 여백을 주고 스크롤 영역은 그대로 둔다.

```kotlin
LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) { ... }
```

### 인셋도 간격이다

시스템 바나 키보드가 차지하는 영역 역시 여백 문제다. `Scaffold`가 제공하는 `innerPadding`을 콘텐츠에 적용하거나 `Modifier.windowInsetsPadding(...)`을 사용한다. 이 값을 임의의 `dp` 상수로 흉내 내면 기기마다 어긋난다.

## 체크리스트

- [ ] `padding`, `spacedBy`, `Spacer`의 목적 차이를 설명할 수 있다.
- [ ] Compose에 margin이 따로 없는 이유와 대안을 설명할 수 있다.
- [ ] 모디파이어 순서가 클릭 영역에 미치는 영향을 설명할 수 있다.
- [ ] 반복되는 `Spacer`를 `Arrangement.spacedBy`로 바꿀 수 있다.
- [ ] 재사용 컴포저블이 자신의 바깥 여백을 직접 정하면 안 되는 이유를 설명할 수 있다.
- [ ] `Spacer`에 `weight`를 주는 용법을 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Compose modifiers](https://developer.android.com/develop/ui/compose/modifiers)
- [Android Developers: Compose layout basics](https://developer.android.com/develop/ui/compose/layouts/basics)
- [Android Developers API: Arrangement](https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/Arrangement)
- [Android Developers: Compose API guidelines](https://developer.android.com/develop/ui/compose/api-guidelines)
- [Android Developers: Lists and grids](https://developer.android.com/develop/ui/compose/lists)
- [Android Developers: Window insets in Compose](https://developer.android.com/develop/ui/compose/system/insets)
- [Android Developers: Compose API guidelines](https://developer.android.com/develop/ui/compose/api-guidelines)
