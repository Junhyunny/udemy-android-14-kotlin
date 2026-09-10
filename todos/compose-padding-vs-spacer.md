# 간격 주기: `padding`과 `Spacer` 중 무엇을 쓸까

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `padding` 방식과 `Spacer` 방식 중 레이아웃을 맞추기 쉬운 쪽은 무엇인가? 안드로이드 진영에서 권장하는 방식이 있는가?

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
