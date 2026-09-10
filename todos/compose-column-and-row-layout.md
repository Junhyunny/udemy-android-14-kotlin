# `Column`과 `Row` 레이아웃

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `Column`, `Row`는 `div` 같은 엘리먼트인가? `Column`은 자식을 수직으로, `Row`는 수평으로 나열하는 것인가?

## 질문 전제 점검

- **"`Column`은 수직, `Row`는 수평으로 나열하는 것인가?"** → 맞다. 방향에 대한 이해는 정확하다.
- **"`div` 같은 엘리먼트야?"** → 절반만 맞다. "자식을 담는 컨테이너"라는 점은 같지만, `div`는 방향이 없는 범용 요소이고 배치는 CSS가 따로 결정한다. `Column`/`Row`는 **배치 방향이 타입 자체에 들어 있는 레이아웃**이다. 굳이 대응시키면 `div`보다 `display: flex; flex-direction: column/row`를 적용한 요소에 가깝다.
- 더 중요한 차이는 산출물이다. `div`는 문서 트리에 남는 마크업 요소지만, `Column`은 **함수 호출**이고 결과로 남는 것은 레이아웃 노드다. HTML/CSS의 정신 모델을 그대로 가져오면 이후 모디파이어 순서나 재구성 개념에서 어긋나기 시작한다.

## 공부할 내용

### 방향에 대한 이해는 맞다

Compose의 기본 레이아웃 컴포저블 세 가지 중 두 가지가 `Column`과 `Row`다.

- `Column`: 아이템을 화면에 **수직**으로 배치한다.
- `Row`: 아이템을 화면에 **수평**으로 배치한다.
- `Box`: 아이템을 서로 겹쳐 쌓는다. [`compose-box-usage.md`](compose-box-usage.md) 참고.

### `div`와 같은가

비슷한 점은 "자식을 담는 컨테이너"라는 역할뿐이다. 차이가 더 크다.

- `div`는 방향이 중립적인 범용 컨테이너이고, 배치는 CSS(`display: flex`, `flex-direction` 등)가 별도로 결정한다. `Column`과 `Row`는 배치 방향 자체가 타입에 담겨 있다. 비유하자면 `div` 하나보다 `display: flex; flex-direction: column`을 적용한 요소에 가깝다.
- `div`는 DOM에 실제로 남는 요소지만, `Column`과 `Row`는 마크업 노드를 만들지 않는다. 컴포지션 단계에서 레이아웃 노드를 만들고 측정·배치 규칙을 제공할 뿐이다.
- 스타일이 외부 CSS가 아니라 `Modifier` 체인과 파라미터로 전달된다.

### 정렬과 배치 파라미터

- `Column`: `verticalArrangement`(주축 배치), `horizontalAlignment`(교차축 정렬)
- `Row`: `horizontalArrangement`(주축 배치), `verticalAlignment`(교차축 정렬)

`chapter078`의 코드가 그 예다.

```kotlin
Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) { ... }
```

`fillMaxSize()`로 화면을 가득 채우고, 자식을 가로 방향 가운데 정렬하며, 세로 방향으로는 가운데 모아 배치한다.

### 중첩 비용

뷰 시스템과 다른 점 하나를 문서가 명시한다. "Compose는 중첩 레이아웃을 효율적으로 처리하므로 복잡한 UI를 설계하기 좋은 방법이다. 성능 때문에 중첩 레이아웃을 피해야 했던 안드로이드 뷰에 비해 개선된 점이다." 레이아웃 단계에서 트리를 한 번만 순회하기 때문이다.

많은 아이템을 나열해야 한다면 `Column`/`Row` 대신 `LazyColumn`/`LazyRow`를 쓴다. 화면에 보이는 아이템만 구성하기 때문이다.

## 관련 아키텍처와 베스트 프랙티스

### flexbox와 대응시켜 익히기

웹 경험이 있다면 다음 대응표가 초기 학습을 빠르게 해준다. 다만 어디까지나 학습용 근사이며, 완전히 같지는 않다.

| Compose | CSS flexbox |
| --- | --- |
| `Column` | `flex-direction: column` |
| `Row` | `flex-direction: row` |
| `verticalArrangement`(Column) | `justify-content` |
| `horizontalAlignment`(Column) | `align-items` |
| `Modifier.weight(1f)` | `flex-grow: 1` |
| `Arrangement.spacedBy(8.dp)` | `gap: 8px` |

주축(main axis)과 교차축(cross axis) 개념이 같으므로, "`Arrangement`는 주축, `Alignment`는 교차축"만 기억하면 파라미터 이름을 헷갈리지 않는다.

### 재사용 가능한 레이아웃 컴포저블 작성법

```kotlin
@Composable
fun LabeledField(
    label: String,
    modifier: Modifier = Modifier,   // 호출자가 배치를 결정한다
) {
    Column(modifier = modifier) { ... }
}
```

- 컴포저블은 자신의 바깥 여백이나 화면 내 위치를 스스로 정하지 않는다. `modifier` 파라미터를 받아 호출자에게 넘긴다.
- 받은 `modifier`는 최상위 레이아웃에 한 번만 적용한다.
- 크기를 내부에서 `fillMaxSize()`로 고정해 버리면 다른 화면에서 재사용할 수 없다.

### 성능과 선택 기준

- 아이템 수가 많거나 개수를 예측할 수 없으면 `LazyColumn`/`LazyRow`를 쓴다. 화면에 보이는 것만 구성한다.
- 스크롤이 필요한 고정 개수 콘텐츠라면 `Column(Modifier.verticalScroll(rememberScrollState()))`가 적절하다.
- `Column`/`Row` 중첩으로 표현이 어려운 복잡한 상호 제약 관계에서만 `ConstraintLayout`을 고려한다. 뷰 시스템과 달리 Compose에서는 중첩 자체가 성능 문제를 만들지 않으므로, 평탄화를 위해 `ConstraintLayout`을 먼저 꺼낼 이유가 없다.

### 적응형 레이아웃

`Column`과 `Row`의 선택을 화면 크기에 따라 바꾸면 폰과 태블릿을 함께 지원할 수 있다. 창 크기 클래스(WindowSizeClass)로 분기하는 것이 권장 방식이며, 기기 종류가 아니라 **사용 가능한 창 너비**를 기준으로 판단한다.

## 체크리스트

- [ ] `Column`, `Row`, `Box`의 배치 방향을 구분할 수 있다.
- [ ] `Column`/`Row`가 `div`와 다른 점을 두 가지 이상 설명할 수 있다.
- [ ] `Arrangement`와 `Alignment`가 각각 어느 축을 다루는지 구분할 수 있다.
- [ ] `Modifier.fillMaxSize()`의 효과를 설명할 수 있다.
- [ ] Compose에서 중첩 레이아웃 비용이 낮은 이유를 설명할 수 있다.
- [ ] `Column`과 `LazyColumn`의 사용 기준을 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Compose layout basics](https://developer.android.com/develop/ui/compose/layouts/basics)
- [Android Developers: Compose layouts](https://developer.android.com/develop/ui/compose/layouts)
- [Android Developers API: Alignment](https://developer.android.com/reference/kotlin/androidx/compose/ui/Alignment)
- [Android Developers API: Arrangement](https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/Arrangement)
- [Android Developers: Lists and grids](https://developer.android.com/develop/ui/compose/lists)
- [Android Developers: Compose layouts](https://developer.android.com/develop/ui/compose/layouts)
- [Android Developers: Compose API guidelines](https://developer.android.com/develop/ui/compose/api-guidelines)
- [Android Developers: Support different screen sizes](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-screen-sizes)
- [Android Developers: Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)
