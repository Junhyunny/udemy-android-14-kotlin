# `@OptIn`과 실험적 API

## 질문이 나온 코드

- [`chapter106/app/src/main/java/com/example/chapter_106/ShoppingList.kt`](../chapter106/app/src/main/java/com/example/chapter_106/ShoppingList.kt)
- 질문: `BasicAlertDialog`를 사용하려면 `@OptIn(ExperimentalMaterial3Api::class)`를 붙이지 않으면 컴파일 에러가 발생하는데, 이 기능은 무슨 의미인가?

## 질문 전제 점검

- **"이 애너테이션을 붙이지 않으면 컴파일 에러가 발생한다"** → 맞다. 옵트인 요구 수준이 `ERROR`면 "옵트인이 필수이고, 그렇지 않으면 표시된 API를 사용하는 코드가 컴파일되지 않는다."
- 다만 지금 코드에서는 전제가 한 겹 어긋나 있다. **현재 파일은 `BasicAlertDialog`가 아니라 `AlertDialog`를 쓰고 있다.** Material 3의 `AlertDialog`는 안정 API이며 공식 문서 예제에도 `@OptIn`이 없다. 즉 지금 붙어 있는 `@OptIn`은 더 이상 필요 없을 가능성이 높다. 지우고 빌드해 보면 바로 확인된다. 불필요하면 IDE가 "Unnecessary @OptIn" 경고를 준다.
- **"이 기능은 무슨 의미야?"** → 애너테이션이 기능을 켜 주는 스위치가 아니다. **"이 API가 불안정하다는 사실을 알고 쓰겠다"는 동의 표시**다. 붙인다고 동작이 달라지지는 않는다.
- 또 하나, 실험적 표시는 라이브러리 버전에 따라 달라진다. 같은 `BasicAlertDialog`라도 버전이 올라가면서 안정화되어 옵트인이 필요 없어질 수 있다. 에러를 만났다면 "그 버전에서는 실험 API였다"는 뜻으로 읽는 것이 정확하다.

## 공부할 내용

### 옵트인 요구라는 장치

코틀린 표준 라이브러리는 "특정 API 요소를 사용할 때 명시적 동의를 요구하고 표시하는 메커니즘"을 제공한다. 라이브러리 작성자는 아직 바뀔 수 있는 API에 표시를 남기고, 사용자는 그 위험을 인지했다는 사실을 코드로 남긴다.

라이브러리 쪽은 `@RequiresOptIn`으로 마커 애너테이션을 정의한다.

```kotlin
@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "This API is experimental.")
annotation class ExperimentalMaterial3Api
```

수준은 두 가지다.

- `ERROR`(기본): "옵트인이 필수다. 그렇지 않으면 표시된 API를 사용하는 코드가 컴파일되지 않는다."
- `WARNING`: "옵트인이 필수는 아니지만 권장된다. 없으면 컴파일러가 경고를 낸다."

### 동의하는 두 가지 방법

**1. `@OptIn` — 전파하지 않고 여기서 끝낸다**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListApp() { ... }
```

이 함수를 호출하는 쪽은 아무것도 하지 않아도 된다. 문서가 지적하는 한계도 함께 알아둔다. "옵트인 요구가 전파되지 않으므로, 다른 사람이 실험적 API를 사용하고 있다는 사실을 모른 채 쓸 수 있다."

**2. 마커를 내 선언에 붙인다 — 전파한다**

```kotlin
@ExperimentalMaterial3Api
@Composable
fun ShoppingListApp() { ... }
```

이제 호출하는 쪽도 동의해야 한다. 문서의 규칙은 이렇다. "API 요소의 시그니처에 옵트인이 필요한 타입이 포함되어 있다면, 그 시그니처 자체도 옵트인을 요구해야 한다." 실험적 타입이 내 공개 API의 파라미터나 반환 타입으로 새어 나간다면 전파가 맞다.

### 적용 범위 고르기

```kotlin
@OptIn(ExperimentalMaterial3Api::class)   // 선언 하나
@file:OptIn(ExperimentalMaterial3Api::class)   // 파일 전체 (파일 맨 위)
```

모듈 전체에 적용하려면 컴파일러 옵션을 쓴다.

```kotlin
kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}
```

"이 인자로 컴파일하는 것은 모듈의 모든 선언에 `@OptIn(OptInAnnotation::class)`을 붙인 것과 같은 효과를 낸다." 편리하지만 그만큼 위험 신호가 사라지므로, 실험 API를 여러 곳에서 쓰는 것이 확정된 뒤에 적용하는 편이 낫다.

### 무엇이 위험한가

실험적 API는 마이너 버전 업그레이드에서도 시그니처가 바뀌거나 사라질 수 있다. 라이브러리를 올렸을 때 컴파일이 깨지는 대표적인 원인이다. 옵트인은 "그때 깨져도 내 책임"이라는 서명에 가깝다.

## 관련 아키텍처와 베스트 프랙티스

### 안정성 단계로 API 읽기

AndroidX는 API마다 안정성 단계를 표시한다. 코드를 읽을 때 애너테이션을 함께 보는 습관이 도움이 된다.

| 표시 | 의미 |
| --- | --- |
| 없음 | 안정 API. 호환성이 유지된다 |
| `@ExperimentalXxxApi` | 실험 단계. 옵트인 필요, 변경 가능 |
| `@RestrictTo` | 라이브러리 내부용. 앱에서 쓰지 않는다 |
| `@Deprecated` | 대체 API로 이전해야 한다 |

`@RequiresApi`, `@RequiresPermission`처럼 이름이 비슷하지만 성격이 다른 애너테이션도 있다. 이들은 옵트인이 아니라 안드로이드 런타임 조건을 나타내는 Lint용 표시다.

### 실험 API 사용 범위를 좁히기

실험 API에 의존하는 코드가 앱 곳곳에 퍼지면, 나중에 그 API가 바뀔 때 수정 범위가 그만큼 넓어진다. 실무에서 쓰는 완화책은 경계를 만드는 것이다.

```kotlin
// 실험 API 의존을 이 파일 한 곳에 가둔다
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAlertDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss, content = content)
}
```

앱의 나머지는 안정적인 `AppAlertDialog`만 호출한다. 라이브러리가 바뀌면 이 파일 하나만 고치면 된다. 어댑터 패턴을 의존성 안정성 문제에 적용한 형태다.

### 정리 습관

- 애너테이션은 **필요한 가장 좁은 범위**에 붙인다. 파일이나 모듈 단위 옵트인은 마지막 수단이다.
- 라이브러리를 올린 뒤에는 불필요해진 `@OptIn`을 지운다. IDE가 알려준다.
- 학습 중이라면 붙이기 전에 **왜 실험 단계인지** 릴리스 노트를 한 번 확인해 보는 것도 좋다. 어떤 부분이 바뀔 예정인지 알 수 있다.

## 체크리스트

- [ ] 옵트인이 "기능 활성화"가 아니라 "동의 표시"임을 설명할 수 있다.
- [ ] `@RequiresOptIn`과 `@OptIn`의 역할을 구분할 수 있다.
- [ ] 전파하는 방식과 전파하지 않는 방식의 차이를 설명할 수 있다.
- [ ] 선언·파일·모듈 단위 옵트인을 각각 적용할 수 있다.
- [ ] 실험 API를 쓸 때의 실질적 위험을 설명할 수 있다.
- [ ] 실험 API 의존을 한곳에 가두는 방법을 설명할 수 있다.
- [ ] 현재 코드의 `@OptIn`이 여전히 필요한지 직접 확인할 수 있다.

## 공식 참고 자료

- [Kotlin: Opt-in requirements](https://kotlinlang.org/docs/opt-in-requirements.html)
- [Android Developers: Dialogs in Compose](https://developer.android.com/develop/ui/compose/components/dialog)
- [Android Developers API: BasicAlertDialog](https://developer.android.com/reference/kotlin/androidx/compose/material3/BasicAlertDialog.composable)
- [Android Developers: Compose Material 3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
