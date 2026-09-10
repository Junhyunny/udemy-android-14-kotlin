# 코틀린 후행 람다와 Compose의 `content` 슬롯

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/ui/theme/Theme.kt`](../chapter078/app/src/main/java/com/example/chapter_078/ui/theme/Theme.kt)
- 질문: 함수 호출에서 파라미터 맨 마지막의 람다 코드 블록이 마지막 파라미터에 매칭되는 것인가? 그렇다면 컴포저블은 항상 마지막 파라미터가 컴포저블 함수인가?

## 질문 전제 점검

- **"파라미터 맨 마지막의 람다 코드 블록이 이 파라미터에 매칭되는 건가?"** → 맞다. 코틀린의 후행 람다 관례 그대로다.
- 다만 이해를 한 단계 정확히 해두면 좋다. 후행 람다는 **"마지막 위치의 파라미터"**에 매칭되는 것이지 "이름이 `content`인 파라미터"에 매칭되는 것이 아니다. `content`가 마지막에 있으니 결과적으로 매칭될 뿐이다.
- 여기서 파생되는 오해가 하나 있다. "컴포저블은 항상 마지막 파라미터가 컴포저블 함수"라는 일반화인데, 이는 사실이 아니다. 콘텐츠를 받지 않는 컴포저블이 훨씬 많다.

## 공부할 내용

### 후행 람다 규칙

코틀린 관례는 명확하다. "함수의 마지막 파라미터가 함수라면, 그에 대응하는 인자로 전달되는 람다식을 괄호 밖에 놓을 수 있다."

```kotlin
val product = items.fold(1, { acc, e -> acc * e })  // 괄호 안
val product = items.fold(1) { acc, e -> acc * e }   // 후행 람다
```

람다가 유일한 인자라면 괄호를 완전히 생략할 수도 있다. 따라서 질문의 이해는 맞다. 괄호 밖 중괄호 블록은 **마지막 파라미터**에 매칭된다.

```kotlin
@Composable
fun Chapter078Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit   // 마지막 파라미터
) { ... }

Chapter078Theme {          // 이 블록이 content에 매칭된다
    UnitConverter()
}
```

`darkTheme`와 `dynamicColor`는 기본값이 있으므로 생략되고, 남은 `content`에 후행 람다가 들어간다.

### 마지막이 아니면 어떻게 되는가

함수 타입 파라미터가 마지막이 아니라면 후행 람다로 쓸 수 없고 괄호 안에서 이름 붙인 인자로 전달해야 한다.

```kotlin
// content가 마지막이 아니라면
Chapter078Theme(content = { UnitConverter() }, darkTheme = true)
```

`Scaffold`처럼 슬롯이 여러 개인 컴포저블도 마찬가지다. `topBar`, `bottomBar`는 이름 붙인 인자로 넘기고 본문 슬롯만 후행 람다로 쓴다.

```kotlin
Scaffold(
    topBar = { /* ... */ }
) { contentPadding ->
    // 본문
}
```

### Compose의 슬롯 API 관례

Compose는 자식 콘텐츠를 받는 파라미터를 관례적으로 `content`라 이름 짓고 마지막에 둔다. 문서는 슬롯 API를 "컴포저블 위에 커스터마이즈 계층을 제공하는" 패턴으로 설명한다. `Column`, `Row`, `Box`, `Button`, `MaterialTheme`이 모두 이 형태다.

다만 "컴포저블은 항상 마지막 파라미터가 컴포저블 함수"라고 단정할 수는 없다. 콘텐츠를 받지 않는 컴포저블도 많다. `Text(text = ...)`나 `Spacer(modifier = ...)`에는 콘텐츠 슬롯이 없다. 정확히 말하면 "자식 콘텐츠를 받는 컴포저블은 그 슬롯을 마지막 파라미터에 두는 것이 관례"다.

관련해 `Modifier` 파라미터에도 관례가 있다. 선택적 `modifier: Modifier = Modifier`를 첫 번째 선택 파라미터로 두고, 콘텐츠 슬롯은 맨 뒤에 둔다.

## 관련 아키텍처와 베스트 프랙티스

### 슬롯 API라는 패턴

`content: @Composable () -> Unit`은 단순한 파라미터가 아니라 **슬롯**이다. 부모는 뼈대와 배치, 스타일 환경을 제공하고 무엇을 채울지는 호출자가 결정한다.

```kotlin
Card {            // 카드는 모양과 그림자를 책임진다
    ProductInfo() // 내용은 호출자가 결정한다
}
```

객체지향으로 보면 상속 대신 조합을 택한 설계다. 뷰 시스템에서 커스텀 컴포넌트를 만들려면 클래스를 상속해 확장했지만, Compose에서는 슬롯에 다른 컴포저블을 꽂는다. 조합은 상속보다 유연하고, 조합 가능한 경우의 수가 클래스 폭발로 이어지지 않는다.

### 파라미터 순서 관례

Compose API 가이드라인이 정한 순서를 따르면 호출부가 일관되게 읽힌다.

```kotlin
@Composable
fun MyComponent(
    value: String,                      // 1. 필수 파라미터
    modifier: Modifier = Modifier,      // 2. modifier는 첫 번째 선택 파라미터
    enabled: Boolean = true,            // 3. 나머지 선택 파라미터
    content: @Composable () -> Unit,    // 4. 콘텐츠 슬롯은 마지막
)
```

`modifier`가 앞쪽에 오는 이유도 같은 맥락이다. 호출자가 배치를 지정할 통로를 항상 열어두되, 후행 람다 자리는 콘텐츠에 양보한다.

### 코틀린 전반에서 반복되는 관례

후행 람다는 Compose만의 것이 아니다. 코틀린 표준 라이브러리와 코루틴 API도 같은 규칙 위에 서 있다.

```kotlin
items.filter { it.isActive }
launch { doWork() }
measureTime { heavyCall() }
```

이 관례 덕분에 라이브러리가 **언어에 내장된 문법처럼 보이는 DSL**을 만들 수 있다. `Column { }`이 제어 구문처럼 읽히는 것도 결국 함수 호출 + 후행 람다다. 새로운 문법을 배우는 것이 아니라 함수 호출을 보고 있다고 인식하면 Compose 코드가 훨씬 단순해 보인다.

### 람다 안의 스코프

`Column { }`의 람다는 단순한 `() -> Unit`이 아니라 `ColumnScope`를 리시버로 갖는다. 그래서 그 안에서만 `Modifier.weight()`나 `Modifier.align()`을 쓸 수 있다. 리시버 스코프로 **사용 가능한 API를 문맥에 맞게 제한**하는 것도 코틀린 DSL의 대표적인 기법이다.

## 체크리스트

- [ ] 후행 람다가 어떤 파라미터에 매칭되는지 규칙으로 설명할 수 있다.
- [ ] 람다가 유일한 인자일 때 괄호를 생략할 수 있는 이유를 설명할 수 있다.
- [ ] 함수 타입 파라미터가 마지막이 아닐 때의 호출 형태를 쓸 수 있다.
- [ ] `Scaffold`처럼 슬롯이 여러 개인 경우의 호출 형태를 쓸 수 있다.
- [ ] "모든 컴포저블의 마지막 파라미터가 컴포저블"이라는 명제가 왜 틀린지 설명할 수 있다.
- [ ] Compose의 `modifier`, `content` 파라미터 순서 관례를 설명할 수 있다.

## 공식 참고 자료

- [Kotlin: Lambdas — Passing trailing lambdas](https://kotlinlang.org/docs/lambdas.html#passing-trailing-lambdas)
- [Kotlin: Higher-order functions and lambdas](https://kotlinlang.org/docs/lambdas.html)
- [Android Developers: Compose layout basics — Slot-based layouts](https://developer.android.com/develop/ui/compose/layouts/basics)
- [Android Developers: Compose API guidelines](https://developer.android.com/develop/ui/compose/api-guidelines)
- [Kotlin: Function literals with receiver (type-safe builders)](https://kotlinlang.org/docs/type-safe-builders.html)
- [Android Developers: Compose modifiers](https://developer.android.com/develop/ui/compose/modifiers)
- [Android Developers API: ColumnScope](https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/ColumnScope)
