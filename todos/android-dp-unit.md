# 안드로이드의 `dp` 단위

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: 안드로이드에서 픽셀의 단위가 `dp`인가? 화면에 보이는 점 하나가 `dp`인가? 어떤 개념인가?
- 질문: `dp`는 무엇이고 `sp`는 무엇인가? 관련 개념을 정리해 달라.

## 질문 전제 점검

- **"안드로이드에서 픽셀의 단위가 `dp`야?"** → 아니다. `dp`는 픽셀의 단위가 아니라 **픽셀과 독립적인 가상 단위**다. 실제 픽셀 수는 기기 밀도에 따라 달라진다. 이름에 "pixel"이 들어 있어 생기는 흔한 오해다.
- **"화면에 보이는 포인트 하나가 `dp`인가?"** → 기준 밀도인 160dpi 화면에서만 1dp가 1픽셀이다. 320dpi 화면에서 16dp는 32픽셀이 된다.
- 관점을 바꾸면 이해가 쉬워진다. `dp`는 **"픽셀이 몇 개인가"가 아니라 "실제로 얼마나 크게 보이는가"를 지정하는 단위**다. 개발자는 물리적 크기를 말하고, 픽셀 환산은 시스템이 담당한다.

## 공부할 내용

### `dp`는 물리 픽셀이 아니다

`dp`는 밀도 독립 픽셀(density-independent pixel)이다. 문서 정의는 이렇다. "1dp는 중밀도 화면(160dpi, 기준 밀도)에서 대략 1픽셀에 해당하는 가상 픽셀 단위다. 안드로이드는 이 값을 다른 각 밀도에 맞는 실제 픽셀 수로 변환한다."

그러니 "화면에 보이는 점 하나가 1dp"라는 이해는 160dpi 기준 화면에서만 맞다. 실제 픽셀 수는 다음 관계로 결정된다.

```
px = dp × (dpi / 160)
```

즉 320dpi 화면에서 16dp는 32px이 된다. 문서는 "이 식을 하드코딩하지 말고 `TypedValue.applyDimension()`을 사용하라"고 안내한다. Compose에서는 `Density` 스코프의 `Dp.toPx()`를 쓴다.

### 왜 `px` 대신 `dp`인가

"픽셀로 치수를 정의하는 것은 문제가 된다. 화면마다 픽셀 밀도가 다르므로 같은 픽셀 수가 기기마다 다른 물리적 크기에 대응하기 때문이다." 문서의 예시로, 16px은 160dpi 화면에서 약 2.5mm지만 240dpi 화면에서는 약 1.7mm다. `dp`를 쓰면 기기가 달라도 물리적 크기가 비슷하게 유지된다.

### `sp`와의 구분

텍스트 크기에는 `sp`(scalable pixel)를 쓴다. "`sp` 단위는 기본적으로 `dp`와 같은 크기지만 사용자가 설정한 선호 텍스트 크기에 따라 크기가 변한다. 레이아웃 크기에는 절대 `sp`를 쓰지 말라." 반대로 텍스트 크기에 `dp`를 쓰면 접근성 설정을 무시하게 된다.

### Compose에서의 표현

Compose에서는 `Dp`가 값 클래스이고 `Int`, `Float`에 대한 `dp` 확장 프로퍼티로 만든다.

```kotlin
Spacer(modifier = Modifier.height(16.dp))
Modifier.padding(10.dp, 75.dp)
```

`16.dp`는 숫자가 아니라 `Dp` 타입 값이다. 그래서 `Modifier.height(16)`처럼 쓰면 컴파일되지 않는다. 현재 밀도는 `LocalDensity.current`로 얻으며, 이 `Density`가 `dp`와 픽셀 변환을 담당한다.

## 관련 아키텍처와 베스트 프랙티스

### 단위 선택 규칙

| 대상 | 단위 | 이유 |
| --- | --- | --- |
| 여백, 크기, 반지름, 두께 | `dp` | 기기 밀도가 달라도 물리적 크기 유지 |
| 텍스트 크기 | `sp` | 사용자의 글꼴 크기 설정을 반영 |
| 캔버스 계산, 픽셀 단위 API | `px` | 필요한 순간에만 변환해서 사용 |

`sp`를 레이아웃 크기에 쓰지 않는다는 규칙도 함께 기억한다. 사용자가 글꼴을 키우면 레이아웃까지 함께 커져 예상치 못한 깨짐이 생긴다.

### 접근성과 큰 글꼴

`sp`를 쓰는 것만으로 끝나지 않는다. 사용자가 글꼴 크기를 200%까지 키울 수 있으므로, 텍스트를 담는 컨테이너를 고정 높이로 잡으면 글자가 잘린다. 실무에서는 다음을 지킨다.

- 텍스트를 감싸는 요소에 고정 `height`를 주지 않고 내용에 맞게 늘어나게 둔다.
- 최소 터치 영역은 48dp를 확보한다. 아이콘이 24dp여도 클릭 영역은 48dp가 되도록 패딩이나 `minimumInteractiveComponentSize`를 활용한다.
- 개발자 옵션이나 설정에서 글꼴 크기와 표시 크기를 최대로 올려 한 번은 확인한다.

### 간격 스케일을 정해두기

값을 그때그때 정하면 화면마다 여백이 미묘하게 달라진다. Material 디자인은 4dp 배수 그리드를 기준으로 하며, 실무에서는 보통 4·8·16·24·32dp 같은 스케일을 미리 정의해 쓴다.

```kotlin
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
}
```

디자인 토큰을 코드 상수로 고정하면 리뷰에서 "여기는 왜 15dp인가" 같은 논쟁이 사라진다.

### `Dp`는 타입이다

Compose의 `Dp`는 값 클래스라서 컴파일 시점에 단위 실수를 잡아준다. `Modifier.height(16)`은 컴파일되지 않고 `16.dp`를 요구한다. 뷰 시스템에서 `setPadding(16)`이 조용히 16픽셀로 동작해 밀도에 따라 크기가 달라지던 문제를 타입으로 막은 것이다.

픽셀 값이 정말 필요할 때만 `Density` 스코프에서 변환한다.

```kotlin
val px = with(LocalDensity.current) { 16.dp.toPx() }
```

## 체크리스트

- [ ] `dp`의 정의와 기준 밀도 160dpi를 설명할 수 있다.
- [ ] `px = dp × (dpi / 160)` 관계를 적용해 계산할 수 있다.
- [ ] `px`를 직접 쓰면 안 되는 이유를 예로 설명할 수 있다.
- [ ] `dp`와 `sp`의 사용 기준을 구분할 수 있다.
- [ ] Compose에서 `16.dp`가 어떤 타입인지 설명할 수 있다.
- [ ] `LocalDensity`가 하는 일을 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Support different pixel densities](https://developer.android.com/training/multiscreen/screendensities)
- [Android Developers: Screen compatibility overview](https://developer.android.com/guide/practices/screens_support)
- [Android Developers API: Dp](https://developer.android.com/reference/kotlin/androidx/compose/ui/unit/Dp)
- [Android Developers API: Density](https://developer.android.com/reference/kotlin/androidx/compose/ui/unit/Density)
- [Android Developers: Accessibility in Compose](https://developer.android.com/develop/ui/compose/accessibility)
- [Android Developers: Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)
- [Android Developers: Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
