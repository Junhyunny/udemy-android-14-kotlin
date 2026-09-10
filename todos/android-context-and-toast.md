# `Toast`에 컨텍스트를 전달하는 이유

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `Toast.makeText`에 컨텍스트를 전달하는 이유가 무엇인가? 어떤 화면에서 토스트가 보일지 모르기 때문에 액티비티의 컨텍스트를 넘기는 것인가?

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

컴포저블에는 `this`가 없으므로 `LocalContext`로 컨텍스트를 얻는다. 자세한 내용은 [`compose-localcontext.md`](compose-localcontext.md)를 참고한다.

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
