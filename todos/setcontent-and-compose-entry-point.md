# `setContent`와 Compose 진입점

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `setContent`는 `ComponentActivity` 클래스의 메서드인가? 상위 컴포넌트의 기능을 이용해 화면을 렌더링하는 것인가?

## 공부할 내용

### 멤버 메서드가 아니라 확장 함수다

`setContent`는 `ComponentActivity`가 직접 가진 멤버 메서드가 아니다. `androidx.activity:activity-compose` 아티팩트가 제공하는 `ComponentActivity`의 **확장 함수**다. 공식 레퍼런스의 선언은 다음과 같다.

```kotlin
fun ComponentActivity.setContent(
    parent: CompositionContext? = null,
    content: @Composable () -> Unit
): Unit
```

그래서 `MainActivity.kt`에는 `import androidx.activity.compose.setContent`가 필요하다. 상속으로 물려받은 기능이었다면 이 import는 없어도 된다. 확장 함수는 클래스 외부에서 정의되지만 그 클래스의 멤버처럼 호출할 수 있게 해주는 코틀린 기능이며, 실제로 클래스에 멤버를 추가하지는 않는다.

혼동하기 쉬운 이름이 두 개 더 있다.

- `Activity.setContentView(view: View)`: 프레임워크 `Activity`의 실제 멤버 메서드. 뷰 시스템의 진입점이다.
- `ComposeView.setContent { }`: `ComposeView`의 멤버 메서드.

### 하는 일

문서 설명은 "주어진 컴포저블을 해당 액티비티에 컴포즈한다. 그 콘텐츠가 액티비티의 루트 뷰가 된다"이며, "대략 다음과 동등하다"고 밝힌다.

```kotlin
setContentView(
    ComposeView(this).apply {
        setContent { MyComposableContent() }
    }
)
```

즉 Compose는 액티비티를 대체하는 것이 아니라, 액티비티의 콘텐츠 뷰 자리에 `ComposeView`라는 안드로이드 `View` 하나를 심고 그 안에서 컴포지션을 구동한다. "상위 컴포넌트의 기능을 이용해 화면을 렌더링한다"는 이해는 이런 의미에서 맞다. 다만 그 통로는 상속이 아니라 확장 함수와 `setContentView`다. 컴포저블이 실제 픽셀이 되기까지의 과정은 [`compose-rendering-pipeline.md`](compose-rendering-pipeline.md)에 정리한다.

`ComponentActivity`를 상속하는 이유는 따로 있다. `setContent`가 확장 대상으로 요구하는 타입이 `ComponentActivity`이며, 이 클래스가 `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner`, `OnBackPressedDispatcherOwner` 같은 Jetpack 계약을 구현해 컴포지션이 이들에 접근할 수 있게 한다.

### 마지막 파라미터가 컴포저블인 이유

`setContent { ... }`처럼 중괄호를 괄호 밖에 쓸 수 있는 것은 `content`가 마지막 파라미터이기 때문이다. Compose API가 콘텐츠 슬롯을 관례적으로 마지막 파라미터에 두는 이유는 [`kotlin-trailing-lambda-and-content-slot.md`](kotlin-trailing-lambda-and-content-slot.md)에 정리한다.

## 체크리스트

- [ ] `setContent`가 확장 함수라는 사실을 import로 확인할 수 있다.
- [ ] `setContent`와 `setContentView`, `ComposeView.setContent`를 구분할 수 있다.
- [ ] `setContent`가 내부적으로 `ComposeView`를 루트 뷰로 심는다는 점을 설명할 수 있다.
- [ ] `ComponentActivity`를 상속해야 하는 이유를 설명할 수 있다.
- [ ] `parent: CompositionContext?` 파라미터가 무엇을 조정하는지 찾아볼 수 있다.

## 공식 참고 자료

- [Android Developers API: `androidx.activity.compose` 패키지 (`ComponentActivity.setContent`)](https://developer.android.com/reference/kotlin/androidx/activity/compose/package-summary)
- [Android Developers API: ComponentActivity](https://developer.android.com/reference/androidx/activity/ComponentActivity)
- [Android Developers API: ComposeView](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/ComposeView)
- [Kotlin: Extensions](https://kotlinlang.org/docs/extensions.html)
