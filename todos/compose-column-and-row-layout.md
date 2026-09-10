# `Column`과 `Row` 레이아웃

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `Column`, `Row`는 `div` 같은 엘리먼트인가? `Column`은 자식을 수직으로, `Row`는 수평으로 나열하는 것인가?

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
