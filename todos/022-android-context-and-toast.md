# `Toast`에 컨텍스트를 전달하는 이유

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `Toast.makeText`에 컨텍스트를 전달하는 이유가 무엇인가? 어떤 화면에서 토스트가 보일지 모르기 때문에 액티비티의 컨텍스트를 넘기는 것인가?

## 질문 전제 점검

- **"어떤 화면에서 토스트가 보일지 모르기 때문에 액티비티의 컨텍스트를 넘기는 건가?"** → 아니다. 토스트는 특정 액티비티 창에 그려지지 않고 **시스템이 관리하는 별도 창**에 표시된다. 화면을 옮겨도 그대로 떠 있다. 컨텍스트는 "어디에 그릴지"가 아니라 **"누가, 어떤 리소스와 환경에서 요청하는지"**를 알려주는 값이다.
- **"컨텍스트를 전달하는 이유가 뭐야?"** → 리소스 해석, 테마, 시스템 서비스 접근, 요청 패키지 식별이 필요하기 때문이다.
- 덧붙이면, `Context`를 "화면을 가리키는 핸들"로 이해하면 이후에 계속 어긋난다. `Application`, `Service`도 `Context`이고 이들에는 화면이 없다. **앱 환경에 접근하는 인터페이스**로 이해하는 편이 맞다.

## 공부할 내용

### `Context`는 무엇인가

`Context`는 "애플리케이션 환경에 대한 전역 정보에 접근하는 인터페이스"다. 구현은 안드로이드 시스템이 제공하며, "애플리케이션별 리소스와 클래스에 대한 접근은 물론 액티비티 시작, 인텐트 브로드캐스트 및 수신 같은 애플리케이션 수준 동작에 대한 up-call을 허용"한다. `Activity`, `Service`, `Application`이 모두 `Context`의 하위 타입이다.

### 왜 `Toast`가 컨텍스트를 요구하는가

`Toast.makeText(context, text, duration)`은 문자열을 화면에 띄우기만 하는 것처럼 보이지만, 실제로는 컨텍스트에서 여러 가지를 가져와야 한다.

- **리소스**: 텍스트를 리소스 ID로 넘기는 오버로드에서 문자열을 해석해야 한다. 테마와 레이아웃 inflate에도 리소스가 필요하다.
- **시스템 서비스 접근**: 토스트는 앱의 창이 아니라 시스템이 관리하는 별도 창으로 표시된다. 이를 요청하려면 컨텍스트를 통해 시스템과 통신해야 한다.
- **앱 식별**: 시스템이 어느 패키지가 요청한 토스트인지 알아야 한다. 안드로이드 12(API 31)부터는 앱이 포그라운드에 있는지에 따라 토스트 동작이 달라지므로 이 정보가 중요하다.

### 질문에 대한 답

"어떤 화면에 띄울지 지정하려고" 넘기는 것은 아니다. 토스트는 특정 액티비티 창에 종속되지 않고 시스템 창에 표시되며, 화면이 바뀌어도 그대로 떠 있다. 컨텍스트는 **어디에 그릴지**가 아니라 **누가, 어떤 리소스와 권한 환경에서 요청하는지**를 알려주는 값이다.

공식 문서는 파라미터를 "액티비티 `Context`"라고 설명하고 예제도 액티비티 안에서 `this`를 넘긴다. 액티비티 컨텍스트를 쓰면 액티비티의 테마와 구성이 반영되므로 UI 관련 작업에서 일반적으로 선호된다. 애플리케이션 컨텍스트로도 토스트는 동작하지만 테마 정보가 달라질 수 있다.

### Compose에서

컴포저블에는 `this`가 없으므로 `LocalContext`로 컨텍스트를 얻는다. 자세한 내용은 [`021-compose-localcontext.md`](021-compose-localcontext.md)를 참고한다.

```kotlin
val context = LocalContext.current
Button(onClick = {
    Toast.makeText(context, "Thanks for clicking", Toast.LENGTH_LONG).show()
}) {
    Text("Click Me!")
}
```

`makeText`는 토스트 객체를 만들 뿐이므로 `show()`를 호출해야 표시된다. 표시 시간은 `Toast.LENGTH_SHORT`와 `Toast.LENGTH_LONG` 두 가지만 지정할 수 있다.

### 참고: 컨텍스트 종류와 수명

- 액티비티 컨텍스트: 액티비티와 수명을 같이한다. 테마가 적용된 UI 작업에 쓴다.
- 애플리케이션 컨텍스트: 앱 프로세스와 수명을 같이한다. 액티비티보다 오래 사는 객체가 컨텍스트를 보관해야 할 때 쓴다. 액티비티 컨텍스트를 오래 붙들면 액티비티 누수가 생긴다.

또한 안드로이드 12부터 백그라운드 앱의 커스텀 토스트 뷰는 차단되며, 사용자 피드백은 스낵바 같은 대안을 권장하는 경우가 많다.

## 관련 아키텍처와 베스트 프랙티스

### 컨텍스트를 고르는 기준

| 필요한 것 | 쓸 컨텍스트 |
| --- | --- |
| 테마가 적용된 UI(다이얼로그, 뷰 inflate) | 액티비티 컨텍스트 |
| 액티비티보다 오래 사는 객체가 보관해야 할 때 | 애플리케이션 컨텍스트 |
| 싱글턴, 리포지토리, `WorkManager` | 애플리케이션 컨텍스트 |

기준은 **수명**이다. 액티비티 컨텍스트를 액티비티보다 오래 사는 곳(싱글턴, `ViewModel`, 정적 필드)에 저장하면 액티비티 전체가 GC되지 못하고 남는다. 안드로이드 스튜디오 Lint의 컨텍스트 누수 경고가 잡아내는 대표적인 실수다.

### 사용자 피드백에는 스낵바를 먼저 고려한다

Material 디자인 가이드와 Compose 컴포넌트 문서는 앱 내부 피드백에 스낵바를 권한다. 토스트와 비교하면 차이가 분명하다.

| | Toast | Snackbar |
| --- | --- | --- |
| 소속 | 시스템 창. 앱을 벗어나도 표시 | 앱 UI의 일부 |
| 액션 버튼 | 없음 | 지원(실행 취소 등) |
| 접근성·테마 | 제한적 | 앱 테마와 접근성 설정을 따름 |
| 제어 | 표시 시간 두 가지뿐 | 표시·해제 제어 가능 |

Compose에서는 `Scaffold`의 `snackbarHost`와 `SnackbarHostState`를 사용한다. 안드로이드 12부터는 백그라운드 앱의 커스텀 토스트 뷰가 차단되는 등 제약도 늘었다.

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()

Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
    Button(onClick = {
        scope.launch { snackbarHostState.showSnackbar("Thanks for clicking") }
    }) { Text("Click Me!") }
}
```

### 부수 효과는 UI 바깥으로

토스트나 스낵바 표시는 화면 상태가 아니라 **일회성 이벤트**다. 이벤트를 상태처럼 다루면 화면 회전 후 메시지가 다시 뜨는 문제가 생긴다. 권장 방식은 UI 상태에 "메시지 표시 필요" 같은 플래그를 두고, 표시한 뒤 소비되었음을 알려 플래그를 지우는 것이다.

```kotlin
LaunchedEffect(uiState.userMessage) {
    uiState.userMessage?.let {
        snackbarHostState.showSnackbar(it)
        viewModel.onMessageShown()
    }
}
```

## 체크리스트

- [ ] `Context`가 제공하는 것을 세 가지 이상 말할 수 있다.
- [ ] `Toast`가 컨텍스트를 요구하는 이유를 리소스·시스템 서비스 관점에서 설명할 수 있다.
- [ ] 토스트가 특정 액티비티 창에 종속되지 않는다는 점을 설명할 수 있다.
- [ ] 액티비티 컨텍스트와 애플리케이션 컨텍스트의 차이를 설명할 수 있다.
- [ ] `makeText` 뒤에 `show()`가 필요한 이유를 설명할 수 있다.
- [ ] 컴포저블에서 컨텍스트를 얻는 방법을 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Toasts overview](https://developer.android.com/guide/topics/ui/notifiers/toasts)
- [Android Developers API: Context](https://developer.android.com/reference/android/content/Context)
- [Android Developers API: Toast](https://developer.android.com/reference/android/widget/Toast)
- [Android Developers: Locally scoped data with CompositionLocal](https://developer.android.com/develop/ui/compose/compositionlocal)
- [Android Developers: Snackbar in Compose](https://developer.android.com/develop/ui/compose/components/snackbar)
- [Android Developers: UI events](https://developer.android.com/topic/architecture/ui-layer/events)
- [Android Developers: Avoid memory leaks](https://developer.android.com/topic/performance/memory)
