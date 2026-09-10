# `setContent`와 Compose 진입점

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `setContent`는 `ComponentActivity` 클래스의 메서드인가? 상위 컴포넌트의 기능을 이용해 화면을 렌더링하는 것인가?

## 질문 전제 점검

- **"`setContent`는 `ComponentActivity` 클래스의 메서드이지?"** → 아니다. `androidx.activity:activity-compose`가 제공하는 **확장 함수**다. `import androidx.activity.compose.setContent`가 필요한 이유가 이것이다.
- **"상위 컴포넌트의 기능을 이용해 화면을 렌더링하는 거지?"** → 결과는 그렇다. 다만 통로가 상속이 아니다. 확장 함수가 내부에서 `ComposeView`를 만들어 `setContentView`로 심는다. "상속으로 물려받은 렌더링 기능"이 아니라 **"기존 뷰 시스템 위에 Compose를 얹는 어댑터"**로 이해하는 편이 정확하다.
- **"컴포저블을 받을 컴포저블은 마지막 파라미터가 항상 컴포저블 함수겠네?"** → 항상은 아니다. 자식 콘텐츠를 받는 컴포저블에 한해 그것이 관례다. `Text`, `Spacer`처럼 콘텐츠 슬롯이 없는 컴포저블도 많다.

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

## 관련 아키텍처와 베스트 프랙티스

### 왜 멤버가 아니라 확장 함수인가

의존성 방향 때문이다. `ComponentActivity`는 `androidx.activity` 코어에 있고 Compose를 모른다. 만약 `setContent`가 멤버 메서드라면 액티비티 코어가 Compose에 의존하게 되어, Compose를 쓰지 않는 앱까지 Compose를 끌고 와야 한다.

확장 함수는 이 문제를 깔끔하게 푼다. Compose를 아는 별도 아티팩트가 `ComponentActivity`에 기능을 덧붙이되, 원래 클래스는 건드리지 않는다. 라이브러리 설계에서 자주 보이는 형태이며, 코틀린이 확장 함수를 제공하는 대표적인 이유이기도 하다.

읽는 쪽에서 얻는 실용적인 교훈도 있다. **어떤 함수가 어디서 왔는지 궁금하면 import를 보라.** 멤버 메서드는 import가 필요 없고 확장 함수는 필요하다.

### 어댑터로서의 `ComposeView`

안드로이드 창 시스템은 여전히 `View` 계층을 다룬다. `ComposeView`는 그 세계와 Compose 세계를 잇는 접점이다.

```
Window
└ DecorView (View)
  └ ComposeView (View)        ← 여기까지가 뷰 시스템
    └ Composition             ← 여기부터가 Compose
      └ Chapter078Theme
        └ UnitConverter
```

이 구조를 알면 반대 방향도 자연스럽게 이해된다. Compose 안에 기존 뷰를 넣을 때 `AndroidView`를 쓰고, 뷰 화면 일부에 Compose를 넣을 때 `ComposeView`를 쓴다. 점진적 마이그레이션이 가능한 이유가 여기에 있다.

### 액티비티의 `onCreate`는 얇게

권장되는 형태는 다음처럼 진입점 역할만 하는 것이다.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Chapter078Theme {
                UnitConverterApp()
            }
        }
    }
}
```

- 테마는 최상위에서 한 번만 적용한다. 그래야 하위 모든 컴포저블이 같은 색·타이포그래피 토큰을 공유한다.
- 화면 구성과 내비게이션은 컴포저블 쪽으로 내린다. 액티비티에 로직이 쌓이면 테스트와 프리뷰가 어려워진다.
- `enableEdgeToEdge()`를 호출했다면 콘텐츠가 시스템 바 아래까지 그려진다. 잘리지 않도록 `Scaffold`나 `WindowInsets` 기반 패딩으로 인셋을 처리해야 한다.

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
- [Kotlin: Extensions](https://kotlinlang.org/docs/extensions.html)
- [Android Developers: Interoperability APIs (Compose in Views, Views in Compose)](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis)
- [Android Developers: Display content edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge)
