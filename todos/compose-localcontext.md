# `LocalContext`의 역할

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- [`chapter078/app/src/main/java/com/example/chapter_078/ui/theme/Theme.kt`](../chapter078/app/src/main/java/com/example/chapter_078/ui/theme/Theme.kt)
- 질문: `LocalContext`의 역할은 무엇인가? 언제 사용하는가? 상태를 저장할 수 있는가? 어떤 정보를 담고 있는 객체인가? 사용 예제를 정리해 달라.

## 공부할 내용

### `CompositionLocal`이라는 전달 수단

`LocalContext`는 특별한 API가 아니라 `CompositionLocal`의 한 사례다. `CompositionLocal`은 "컴포지션을 통해 데이터를 암묵적으로 아래로 전달하는 도구"다. "`CompositionLocal` 요소는 보통 UI 트리의 특정 노드에서 값이 제공되고, 그 컴포저블 자손들은 해당 `CompositionLocal`을 파라미터로 선언하지 않고도 그 값을 사용할 수 있다."

값은 `.current`로 읽는다. "`CompositionLocal.current`는 그 `CompositionLocal`에 값을 제공하는 가장 가까운 `CompositionLocalProvider`가 제공한 값을 반환한다." `LocalContext`의 값은 `setContent`가 컴포지션을 시작할 때 호스트 뷰의 컨텍스트로 제공된다.

### 담고 있는 것

`LocalContext.current`가 돌려주는 값은 안드로이드의 `Context`다. 액티비티 안에서 `setContent`로 시작한 컴포지션이라면 실질적으로 그 액티비티 컨텍스트다. `Context`는 리소스, 애셋, 시스템 서비스, 액티비티 시작이나 인텐트 브로드캐스트 같은 앱 수준 동작에 접근하는 통로다. 자세한 내용은 [`android-context-and-toast.md`](android-context-and-toast.md)에 정리한다.

### 상태를 저장할 수 있는가

아니다. `LocalContext`는 상태 저장소가 아니라 이미 존재하는 플랫폼 객체를 컴포지션 트리 아래로 전달하는 통로다. 상태를 기억하려면 `remember`, `rememberSaveable`, `ViewModel`을 쓴다. 다만 `CompositionLocal` 자체는 값이 바뀌면 그 값을 읽는 컴포저블이 재구성된다는 점에서 상태와 연결될 수는 있다.

### 사용 예제

```kotlin
// 1. 리소스 접근
val resources = LocalContext.current.resources
val text = resources.getQuantityString(R.plurals.fruit_title, count)

// 2. Toast 표시
val context = LocalContext.current
Button(onClick = {
    Toast.makeText(context, "Thanks for clicking", Toast.LENGTH_LONG).show()
}) { Text("Click Me!") }

// 3. 동적 색상 스킴 생성 (Theme.kt에서 실제로 쓰는 방식)
val context = LocalContext.current
val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

// 4. 인텐트로 화면·앱 전환
val context = LocalContext.current
Button(onClick = {
    context.startActivity(Intent(Intent.ACTION_VIEW, "https://developer.android.com".toUri()))
}) { Text("열기") }

// 5. 시스템 서비스 접근
val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
```

### 사용 시 주의

- `LocalContext.current`는 컴포저블 안에서만 읽을 수 있다. 이벤트 콜백 안에서 바로 읽을 수 없으므로 위 예제처럼 컴포저블 본문에서 변수로 꺼내 둔다.
- 액티비티가 필요하면 `LocalContext.current as Activity` 캐스팅 대신 `LocalActivity.current`를 쓴다. 미리보기나 다른 호스트에서는 캐스팅이 실패할 수 있다.
- 문서는 `CompositionLocal` 남용을 경계한다. "`CompositionLocal`은 컴포저블의 동작을 추론하기 어렵게 만든다. 암묵적 의존성을 만들기 때문이다." 재사용성과 테스트 용이성을 위해 가능하면 명시적 파라미터 전달을 우선한다.
- 컨텍스트를 컴포저블 외부에 오래 보관하면 액티비티 누수가 생길 수 있다.

## 체크리스트

- [ ] `CompositionLocal`이 해결하는 문제를 설명할 수 있다.
- [ ] `.current`가 어떤 값을 반환하는지 설명할 수 있다.
- [ ] `LocalContext`가 상태 저장소가 아닌 이유를 설명할 수 있다.
- [ ] 리소스, Toast, 인텐트, 시스템 서비스 접근 예제를 각각 쓸 수 있다.
- [ ] 콜백 안에서 `LocalContext.current`를 직접 읽지 않는 이유를 설명할 수 있다.
- [ ] `CompositionLocal`을 남용하면 안 되는 이유를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Locally scoped data with CompositionLocal](https://developer.android.com/develop/ui/compose/compositionlocal)
- [Android Developers API: LocalContext](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/package-summary#LocalContext())
- [Android Developers API: `androidx.activity.compose` 패키지 (`LocalActivity`)](https://developer.android.com/reference/kotlin/androidx/activity/compose/package-summary)
- [Android Developers API: Context](https://developer.android.com/reference/android/content/Context)
