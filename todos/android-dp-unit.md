# 안드로이드의 `dp` 단위

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: 안드로이드에서 픽셀의 단위가 `dp`인가? 화면에 보이는 점 하나가 `dp`인가? 어떤 개념인가?

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
