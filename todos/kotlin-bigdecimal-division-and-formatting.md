# `BigDecimal` 나눗셈과 출력 형식

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문(메모): 나눗셈에서 무한 소수면 `ArithmeticException: Non-terminating decimal expansion`이 발생하니 끊는 자릿수와 반올림 규칙을 지정한다. 지수 표현식(`E+2`)을 감추려면 `toPlainString`을 쓴다. 뒤에 `0`이 길게 붙으면 지우는 함수를 쓴다.

## 질문 전제 점검

메모 세 줄은 모두 방향이 맞다. 다만 첫 번째에 흔히 놓치는 정정 포인트가 하나 있다.

- **"끊는 자릿수"** → `MathContext(10, RoundingMode.HALF_UP)`의 `10`은 **소수점 이하 자릿수가 아니라 유효숫자(precision)**다. `0.000123456789012`를 10으로 지정하면 소수점 열 자리가 아니라 유효숫자 열 개까지 남는다. 소수점 이하 자릿수를 고정하고 싶다면 `divide(divisor, scale, RoundingMode)`나 `setScale(scale, RoundingMode)`를 써야 한다.
- **`toPlainString`** → 맞다. `toString()`은 "필요하면 지수 표기를 사용"하지만 `toPlainString()`은 "지수 필드 없이" 출력한다.
- **`stripTrailingZeros`** → 맞다. 다만 이 메서드는 뒤의 `0`을 지우면서 **오히려 지수 표기를 만들어 낼 수 있다**. 그래서 `toPlainString`과 반드시 짝으로 써야 한다. 현재 코드의 순서가 정확하다.

## 공부할 내용

### 왜 예외가 발생하는가

`BigDecimal`은 정확한 십진 값을 표현하는 타입이다. 인자가 하나인 `divide`는 결과를 반올림 없이 정확히 표현하려 하므로, 몫이 유한 소수가 아니면 값을 만들 수 없다. 공식 문서 설명 그대로다.

> `divide(BigDecimal divisor)`는 몫을 반환하며, "정확한 몫을 표현할 수 없으면(무한 소수 전개를 갖기 때문에) `ArithmeticException`이 발생한다."

```kotlin
BigDecimal.ONE.divide(BigDecimal(3))   // ArithmeticException
```

`1/3 = 0.333...`처럼 십진법으로 끝나지 않는 몫이 여기 해당한다. 분모가 2와 5의 곱으로만 이루어지지 않으면 대부분 이 경우다.

### 해결: 반올림 규칙을 함께 지정한다

정확히 표현할 수 없다면 **어디서 끊고 어떻게 반올림할지**를 개발자가 정해야 한다. 두 가지 방법이 있고 의미가 다르다.

```kotlin
// 1. 유효숫자 기준 - MathContext(precision, roundingMode)
value.divide(divisor, MathContext(10, RoundingMode.HALF_UP))

// 2. 소수점 이하 자릿수 기준 - divide(divisor, scale, roundingMode)
value.divide(divisor, 4, RoundingMode.HALF_UP)   // 소수점 넷째 자리까지
```

| 개념 | 의미 | 예 (`0.00123456`) |
| --- | --- | --- |
| precision(유효숫자) | 0이 아닌 첫 자리부터 센 전체 자릿수 | precision 3 → `0.00123` |
| scale(스케일) | 소수점 이하 자릿수 | scale 3 → `0.001` |

단위 변환처럼 값의 크기 범위가 넓은 계산에서는 precision이, 금액처럼 자릿수가 고정된 값에서는 scale이 자연스럽다.

`RoundingMode`는 `HALF_UP`(반올림), `HALF_EVEN`(은행가 반올림), `DOWN`(버림), `UNNECESSARY`(반올림이 필요하면 예외) 등이 있다. `UNNECESSARY`는 인자 하나짜리 `divide`와 같은 동작을 명시적으로 요구하는 값이다.

### 지수 표기와 `toPlainString`

`toString()`은 "필요하면 지수 표기를 사용해" 문자열을 만든다. 반면 `toPlainString()`은 "지수 필드 없이" 출력한다.

```kotlin
BigDecimal("6E2").toString()        // "6E+2"
BigDecimal("6E2").toPlainString()   // "600"
```

사용자에게 보여줄 문자열이라면 `toPlainString()`이 안전하다.

### `stripTrailingZeros`가 지수 표기를 만드는 이유

문서의 예가 이 동작을 정확히 보여준다.

> `600.0`(내부 표현 [6000, scale 1])에서 뒤의 0을 제거하면 **`6E2`**(내부 표현 [6, scale -2])가 된다.

`BigDecimal`은 값을 `unscaledValue × 10^(-scale)`로 저장한다. 뒤의 0을 지우는 것은 unscaledValue를 줄이고 scale을 음수로 만드는 일이며, scale이 음수가 되면 `toString()`이 지수 표기를 쓴다. 그래서 다음 조합이 필요하다.

```kotlin
result.stripTrailingZeros().toPlainString()   // 불필요한 0도 없고 지수 표기도 없다
```

두 호출은 취향의 문제가 아니라 서로를 보완하는 짝이다.

## 관련 아키텍처와 베스트 프랙티스

### 왜 `Double`이 아니라 `BigDecimal`인가

`Double`은 이진 부동소수점이라 `0.1 + 0.2 != 0.3`이 된다. 단위 변환이나 금액처럼 사람이 십진수로 검증하는 값에는 `BigDecimal`을 쓴다. 대신 느리고 API가 장황하다는 비용을 진다. 그래픽 좌표나 물리 계산처럼 오차가 허용되는 영역까지 `BigDecimal`로 바꿀 이유는 없다.

### 생성자 함정

```kotlin
BigDecimal(0.1)        // 0.1000000000000000055511151231257827... (Double을 그대로 옮김)
BigDecimal("0.1")      // 0.1
0.1.toBigDecimal()     // 0.1 (내부적으로 문자열 표현 사용)
```

`Double`을 받는 생성자는 이진 오차를 그대로 가져온다. 문자열 생성자나 `valueOf`를 쓴다.

### `equals`와 `compareTo`는 다르다

```kotlin
BigDecimal("2.0") == BigDecimal("2.00")            // false (scale이 다르다)
BigDecimal("2.0").compareTo(BigDecimal("2.00"))    // 0 (값이 같다)
```

`equals`는 scale까지 비교한다. 값이 같은지 판단할 때는 항상 `compareTo`를 쓴다. 현재 코드의 `inConversionFactor.value == BigDecimal.ZERO` 같은 비교는 `BigDecimal.ZERO`와 `BigDecimal("0.00")`을 다르게 판정할 수 있다. `signum() == 0`이나 `compareTo(BigDecimal.ZERO) == 0`이 더 안전하다.

### 계산 정밀도와 표시 형식을 분리한다

실무에서 흔히 쓰는 구조는 다음과 같다.

| 계층 | 책임 |
| --- | --- |
| 도메인 | 높은 정밀도로 계산. 반올림은 마지막에 한 번 |
| UI | 사용자에게 보여줄 형식으로 변환 |

중간 단계마다 반올림하면 오차가 누적된다. 반대로 표시 단계에서 포맷을 정하지 않으면 화면마다 자릿수가 달라진다.

안드로이드에서 사용자에게 숫자를 보여줄 때는 로캘도 고려한다. 지역에 따라 소수점과 자릿수 구분 기호가 다르므로, 최종 표시에는 `NumberFormat`이나 `DecimalFormat`을 쓰는 편이 낫다.

```kotlin
val formatted = NumberFormat.getInstance().format(result)
```

### 변환 계수 테이블 패턴

`chapter078`처럼 기준 단위를 정하고 각 단위의 계수를 두는 방식은 단위 변환의 표준적인 설계다.

```
입력값 × 입력단위계수 ÷ 출력단위계수 = 출력값
```

단위가 늘어나도 계수 하나만 추가하면 되고, 단위 조합마다 변환식을 만들 필요가 없다. 계수를 `BigDecimal`로 두면 `304.8`(피트→밀리미터) 같은 값이 정확히 유지된다.

## 체크리스트

- [ ] 인자 하나짜리 `divide`가 예외를 던지는 조건을 설명할 수 있다.
- [ ] precision과 scale의 차이를 예로 설명할 수 있다.
- [ ] `MathContext`와 `divide(divisor, scale, mode)` 중 상황에 맞는 쪽을 고를 수 있다.
- [ ] `toString()`과 `toPlainString()`의 차이를 설명할 수 있다.
- [ ] `stripTrailingZeros()`가 지수 표기를 만들 수 있는 이유를 내부 표현으로 설명할 수 있다.
- [ ] `BigDecimal`의 `equals`와 `compareTo` 차이를 설명할 수 있다.
- [ ] `BigDecimal(0.1)`과 `BigDecimal("0.1")`의 차이를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers API: BigDecimal](https://developer.android.com/reference/java/math/BigDecimal)
- [Java SE API: BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [Java SE API: RoundingMode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html)
- [Java SE API: MathContext](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/MathContext.html)
- [Android Developers: Localize your app](https://developer.android.com/guide/topics/resources/localization)
