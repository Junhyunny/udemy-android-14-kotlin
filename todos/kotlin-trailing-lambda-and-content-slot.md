# 코틀린 후행 람다와 Compose의 `content` 슬롯

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/ui/theme/Theme.kt`](../chapter078/app/src/main/java/com/example/chapter_078/ui/theme/Theme.kt)
- 질문: 함수 호출에서 파라미터 맨 마지막의 람다 코드 블록이 마지막 파라미터에 매칭되는 것인가? 그렇다면 컴포저블은 항상 마지막 파라미터가 컴포저블 함수인가?

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
