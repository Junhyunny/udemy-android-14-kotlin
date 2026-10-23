# 수동 DI에서 Hilt로 옮기면 무엇이 바뀌고, 테스트는 무엇을 준비해야 하나

## 질문이 나온 코드

이 저장소의 코드가 아니라 외부에서 받은 설명을 검토한 문서다. [`articles/compose-navigation-example-and-testing.md`](../articles/compose-navigation-example-and-testing.md) 5장의 `AppContainer` 예제를 Hilt로 옮기는 상황이다.

> `AppContainer`를 직접 관리하기 번거로워지면 Hilt로 옮길 차례입니다. Hilt는 위 컨테이너를 코드 생성으로 대신 만들어 줍니다.
>
> - `AppNavHost`에 파라미터가 아예 필요 없어지고, 화면에서는 `hiltViewModel()`만 호출합니다.
> - 테스트에서는 `@TestInstallIn`이나 `@BindValue`로 모듈 단위로 Fake를 바꿔 끼웁니다.
> - 대신 처음 설정할 것이 꽤 있습니다.
>   - kapt/KSP 설정
>   - 커스텀 `HiltTestRunner`
>   - `HiltAndroidRule`
>   - 테스트용 `@AndroidEntryPoint` Activity
>   - 그래서 `createComposeRule()` 대신 `createAndroidComposeRule<HiltTestActivity>()`를 써야 합니다.

질문: 이 설명을 바탕으로 공부할 내용을 정리해 달라.

이 문서의 코드는 임시 프로젝트에서 빌드하고 에뮬레이터(Pixel_10_Pro)에서 실행해 확인했다. 환경은 chapter157과 같은 AGP 9.4.0, Kotlin 2.2.10에 Hilt 2.60.1, KSP 2.3.12, androidx.hilt 1.4.0을 더한 것이다. 실험으로 확인한 빌드 오류도 그대로 옮겼다.

## 질문 전제 점검

- **"Hilt는 위 컨테이너를 코드 생성으로 대신 만들어 준다"** → **맞다.** Hilt(내부적으로는 Dagger)가 컴파일 시점에 컨테이너 역할을 하는 컴포넌트 클래스를 생성한다. 실험 중 빌드 오류 메시지에 생성된 파일 이름이 그대로 찍혔다.

  ```
  app/build/generated/hilt/component_sources/debug/com/example/chapter_157/NavSampleApp_HiltComponents.java
      … SingletonC → ActivityRetainedC → ViewModelC
  ```

  `AppContainer`에 손으로 쓰던 `by lazy` 조립 코드를 Hilt가 이 파일로 만들어 준다. 생성 시점이 **컴파일 타임**이라는 점이 중요하다. 바인딩을 빠뜨리거나 두 번 등록하면 앱을 실행하기 전에 빌드가 실패한다. 수동 DI에서는 같은 실수가 런타임 크래시로 드러난다.

- **"`AppNavHost`에 파라미터가 아예 필요 없어진다"** → **절반만 맞다.**
  - 저장소 같은 **의존성 파라미터**를 없앤 것은 Hilt가 아니다. "`ViewModel`이 생성자로 받고 팩토리가 채운다"는 구조가 없앴다. 수동 DI 버전의 `AppNavHost`도 이미 `modifier`와 `navController`만 받는다(아티클 5.3절).
  - **`navController` 파라미터는 Hilt를 써도 남겨야 한다.** 테스트에서 `TestNavHostController`를 꽂는 통로이기 때문이다. Hilt가 주입하는 것은 앱 스코프 객체와 `ViewModel`이지 `NavController`가 아니다.

- **"`@TestInstallIn`이나 `@BindValue`로 모듈 단위로 Fake를 바꿔 끼운다"** → **둘은 교체 단위가 다르고, 같은 타입에 함께 쓸 수 없다.**

  | | `@TestInstallIn` | `@UninstallModules` + `@BindValue` |
  | --- | --- | --- |
  | 무엇을 바꾸나 | **모듈** 하나를 통째로 | **바인딩(타입)** 하나 |
  | 어디에 적용되나 | 그 테스트 소스 세트(`androidTest`)의 **모든** 테스트 | 그 **테스트 클래스 하나** |
  | 어디에 쓰나 | 별도 파일의 `@Module` | 테스트 클래스 안의 필드 |

  `@BindValue`는 혼자 쓸 수 없다. 원래 바인딩을 제공하는 모듈을 `@UninstallModules`로 빼야 한다. 그런데 실험해 보니 두 방식을 같은 타입에 섞으면 빌드가 실패했다.

  ```
  // FakeDataModule 이 @TestInstallIn 으로 DataModule 을 이미 대체한 상태에서
  @UninstallModules(DataModule::class) + @BindValue val repository: ItemRepository
  → error: [Dagger/DuplicateBindings] com.example.chapter_157.ItemRepository is bound multiple times

  // 그렇다면 대체 모듈 자체를 빼려고 하면
  @UninstallModules(FakeDataModule::class)
  → [Hilt] @UninstallModules should only include modules annotated with both @Module and @InstallIn,
    but found: [com.example.chapter_157.FakeDataModule].
  ```

  **`@TestInstallIn` 모듈은 테스트 하나에서 뺄 수 없다.** 그래서 한 타입에 대해 "전체 기본 가짜(`@TestInstallIn`)"와 "테스트별 가짜(`@BindValue`)" 중 하나를 골라야 한다. 테스트별로 데이터만 달라지면 되는 경우는 세 번째 방법이 있다(공부할 내용 ⑤).

- **"kapt/KSP 설정"** → **KSP로 한다.** 두 도구 모두 애너테이션을 읽고 코드를 생성하지만, kapt는 Kotlin 코드를 Java 스텁으로 바꾼 뒤 Java 애너테이션 프로세서를 돌리는 방식이라 느리다. Kotlin 쪽에서도 kapt는 유지보수 모드이고, Dagger/Hilt는 KSP를 지원한다. **AGP 9의 내장 Kotlin에서는 kapt를 쓰려면 별도 플러그인(`com.android.legacy-kapt`, AGP와 같은 버전으로 9.0.0부터 배포)이 필요하다.** 이름부터 "legacy"다. 이 문서의 프로젝트는 KSP만 썼고 kapt 설정은 시험하지 않았다.

- **"테스트용 Activity가 필요해서 `createComposeRule()` 대신 `createAndroidComposeRule<HiltTestActivity>()`를 써야 한다"** → **조건부로만 맞다.** `HiltTestActivity`가 필요한 것은 **`hiltViewModel()`을 부르는 컴포저블(Route, `AppNavHost`)을 띄울 때뿐이다.** 같은 Hilt 프로젝트에서 실제로 확인했다.

  | 테스트 | Hilt 준비물 | 결과 |
  | --- | --- | --- |
  | `ItemDetailViewModelTest` (JVM + Robolectric) | 없음. `ItemDetailViewModel(SavedStateHandle(...), FakeItemRepository())`로 직접 생성 | 통과 |
  | `ItemListScreenTest` (Screen만) | 없음. `createComposeRule()` 그대로 | 통과 |
  | `AppNavHostTest` (그래프 전체) | `HiltTestRunner`, `HiltAndroidRule`, `HiltTestActivity`, 가짜 모듈 | 통과 |

  나열된 준비물은 **그래프·통합 테스트 전용**이다. Screen이 Hilt를 모르게(상태와 콜백만 받게) 짜 두었기 때문에 테스트 대부분은 Hilt 없이 돈다. 이게 Route/Screen 분리의 실익이다.

- **목록에 빠진 것** → 전제 목록에는 앱 쪽 준비물(`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `hiltViewModel()` 의존성)과 함정 두 개가 없다. Kotlin 생성자 기본값은 Hilt가 쓰지 않고, `hiltViewModel()`의 import 경로가 바뀌었다. 아래에서 다룬다.

## 공부할 내용

### ① Hilt가 만드는 컨테이너 — 컴포넌트와 스코프

Hilt는 안드로이드 클래스의 수명마다 컴포넌트(컨테이너)를 하나씩 만들고, 부모-자식으로 잇는다. 자식은 부모의 객체를 받을 수 있지만 반대는 안 된다.

```
SingletonComponent            @Singleton               Application 과 함께 산다
└── ActivityRetainedComponent @ActivityRetainedScoped  화면 회전을 넘어 산다
    ├── ViewModelComponent    @ViewModelScoped         ViewModel 과 함께 산다
    └── ActivityComponent     @ActivityScoped          Activity 와 함께 산다 (회전 시 다시 생성)
        └── FragmentComponent, ViewComponent ...
```

수동 DI와 대응시키면 이렇다.

| 수동 DI (아티클 5.3절) | Hilt |
| --- | --- |
| `NavSampleApp.container` | `@HiltAndroidApp` → `SingletonComponent` |
| `DefaultAppContainer` 안의 조립 코드 | `@Module @InstallIn(SingletonComponent::class)` |
| `by lazy` (앱에 하나만) | `@Singleton` |
| `companion object Factory` + `appContainer()` + `createSavedStateHandle()` | `@HiltViewModel` + `@Inject constructor` |
| `viewModel(factory = ...Factory)` | `hiltViewModel()` |

**스코프 애너테이션이 없으면 요청할 때마다 새로 만든다.** `@Singleton`을 빼면 `ItemRepository`를 원하는 곳마다 다른 인스턴스가 생긴다. 저장소, HTTP 클라이언트, DB는 `@Singleton`을 붙인다.

### ② 바인딩을 알려 주는 세 가지 방법

Hilt에게 "이 타입은 이렇게 만든다"를 알려 주는 방법이다.

| 방법 | 언제 | 예 |
| --- | --- | --- |
| `@Inject constructor` | 내가 만든 클래스 | `DefaultItemRepository` |
| `@Binds` (추상 함수) | 인터페이스 → 구현 연결 | `ItemRepository` → `DefaultItemRepository` |
| `@Provides` (함수 본문) | 생성자를 고칠 수 없는 외부 클래스, 빌더가 필요한 객체 | Retrofit 서비스, Room DB, `Dispatchers.IO` |

검증한 코드다.

```kotlin
// DataModule.kt
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    // 인터페이스 → 구현 연결. 구현체는 @Inject constructor 로 Hilt 가 직접 만든다.
    @Binds
    @Singleton
    abstract fun bindItemRepository(impl: DefaultItemRepository): ItemRepository

    companion object {
        // 내가 생성자를 고칠 수 없는 객체(외부 라이브러리)는 @Provides 로 만드는 법을 알려 준다.
        @Provides
        @Singleton
        fun provideItemApi(): ItemApi = InMemoryItemApi()  // 실제 앱: retrofit.create(ItemApi::class.java)

        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
```

```kotlin
// ItemRepository.kt
// Hilt 는 Kotlin 기본값을 쓰지 않는다. 디스패처도 한정자로 주입받는다.
class DefaultItemRepository @Inject constructor(
    private val api: ItemApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ItemRepository { /* 아티클 5.3절과 같다 */ }
```

**함정 — Kotlin 기본값.** 수동 DI에서는 `ioDispatcher: CoroutineDispatcher = Dispatchers.IO`처럼 기본값을 두었다. 이 상태로 `@Inject constructor`만 붙이면 빌드가 실패한다. Dagger는 Kotlin 기본값을 모르고 모든 파라미터를 채우려 하기 때문이다.

```
error: [Dagger/MissingBinding] kotlinx.coroutines.CoroutineDispatcher cannot be provided without an @Provides-annotated method.
```

같은 타입이 여러 개(`Dispatchers.IO`, `Dispatchers.Default`)일 수 있는 의존성은 **한정자(`@Qualifier`)** 로 이름을 붙여 구분한다.

### ③ 앱 쪽 준비물

```kotlin
@HiltAndroidApp                       // SingletonComponent 를 만든다. 매니페스트의 android:name 은 그대로 둔다
class NavSampleApp : Application()

@AndroidEntryPoint                    // 이 액티비티 안에서 hiltViewModel() 이 동작한다
class MainActivity : ComponentActivity() { ... }

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,   // Hilt 가 이 백스택 엔트리의 SavedStateHandle(route 인자 포함)을 넣어 준다
    repository: ItemRepository,
) : ViewModel() { ... }

@Composable
fun ItemDetailRoute(
    onBack: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) { ... }
```

- `SavedStateHandle`은 따로 바인딩하지 않아도 `@HiltViewModel`의 생성자에 들어온다. `composable<ItemDetail>` 안에서 `hiltViewModel()`을 부르면 그 엔트리의 인자가 담긴 핸들이다. 그래서 `toRoute<ItemDetail>()`가 수동 DI 때와 똑같이 동작한다.
- **`hiltViewModel()` import 경로가 바뀌었다.** androidx.hilt 1.3.0부터 `hilt-lifecycle-viewmodel-compose` 아티팩트의 `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`이다. 예전 자료의 `androidx.hilt.navigation.compose.hiltViewModel`(`hilt-navigation-compose`)과 다르다.

Gradle 설정은 아티클 5.6절 ①에 있다. 핵심은 `ksp(libs.hilt.compiler)`와 `kspAndroidTest(libs.hilt.compiler)`를 둘 다 넣는 것이다. 테스트 소스의 `@HiltAndroidTest`, `@TestInstallIn`도 코드 생성 대상이기 때문이다.

### ④ 테스트 준비물 — 무엇이 왜 필요한가

| 준비물 | 왜 필요한가 | 없으면 |
| --- | --- | --- |
| `HiltTestRunner` + `testInstrumentationRunner` 설정 | 계측 테스트에서 앱의 `NavSampleApp` 대신 `HiltTestApplication`을 쓴다. 테스트마다 컴포넌트를 새로 만들 수 있는 특수 `Application`이다 | `@HiltAndroidTest`가 동작하지 않는다 |
| `@HiltAndroidTest` | 이 테스트 클래스 전용 컴포넌트를 생성하라는 표시 | 주입 대상이 아니다 |
| `HiltAndroidRule` (`order = 0`) | 테스트마다 컴포넌트를 만들고 `inject()`로 테스트의 `@Inject` 필드를 채운다 | 컴포넌트가 없어 액티비티 주입이 실패한다 |
| `HiltTestActivity` (debug 소스 세트) | `hiltViewModel()`은 `@AndroidEntryPoint` 액티비티 안에서만 동작한다. `createComposeRule()`의 빈 액티비티는 주입 대상이 아니다 | Route, `AppNavHost`를 띄울 수 없다 |
| 가짜 모듈 또는 `@BindValue` | 실제 네트워크·DB 대신 가짜 저장소 | 테스트가 느려지고 IO 작업을 기다려야 한다 |

`HiltAndroidRule`을 `order = 0`으로 두는 이유는 순서다. Compose 규칙이 먼저 액티비티를 띄우면 그 시점에는 아직 컴포넌트가 없다.

**테스트마다 컴포넌트가 새로 만들어진다.** `@Singleton`도 테스트 단위로 새것이다. 순서를 고정한 테스트 두 개로 확인했다. 앞 테스트가 가짜 저장소의 데이터를 바꿔도 다음 테스트는 원래 데이터로 시작했다. 수동 DI의 `app.container`는 프로세스 전역이라 이 격리가 없었다.

> Robolectric으로 JVM에서 Hilt 통합 테스트를 돌리는 방법도 있다(`@Config(application = HiltTestApplication::class)`). 이 문서에서는 시험하지 않았다.

### ⑤ 가짜를 바꿔 끼우는 세 가지 방법

**방법 1 — `@TestInstallIn`: 모든 테스트의 기본 가짜**

```kotlin
// app/src/androidTest/.../FakeDataModule.kt
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object FakeDataModule {

    @Provides
    @Singleton
    fun provideItemRepository(): ItemRepository = FakeItemRepository(
        listOf(Item(1L, "Kotlin"), Item(2L, "Compose"), Item(3L, "Navigation")),
    )
}
```

**방법 2 — `@UninstallModules` + `@BindValue`: 이 테스트만의 가짜**

`androidTest`에 같은 타입을 대체하는 `@TestInstallIn` 모듈이 **없어야** 한다(전제 점검의 실험 결과).

```kotlin
@HiltAndroidTest
@UninstallModules(DataModule::class)
class BindValueTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @BindValue
    @JvmField
    val repository: ItemRepository = FakeItemRepository(listOf(Item(1L, "이 테스트만의 가짜")))

    @Before
    fun setUp() {
        hiltRule.inject()
        composeTestRule.setContent { AppNavHost() }
    }

    @Test
    fun 이_테스트에서만_다른_가짜를_쓴다() {
        composeTestRule.onNodeWithText("로그인").performClick()
        composeTestRule.onNodeWithText("이 테스트만의 가짜").assertIsDisplayed()
    }
}
```

`@UninstallModules(DataModule::class)`는 모듈 전체를 뺀다. `DataModule`이 `ItemApi`, `@IoDispatcher`도 제공했다면 그것들도 사라진다. 그래서 모듈을 작게 나누어 두면 테스트별 교체가 쉬워진다.

**방법 3 — `@TestInstallIn` + 가짜를 `@Inject`로 받아 데이터만 바꾸기**

기본 가짜는 유지하면서 테스트마다 데이터만 다르게 하고 싶을 때 쓴다. `@TestInstallIn`과 충돌하지 않는다.

```kotlin
// FakeDataModule.kt
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object FakeDataModule {

    // 가짜 자체를 싱글톤으로 등록해 두면 테스트가 @Inject 로 받아 데이터를 바꿀 수 있다.
    @Provides
    @Singleton
    fun provideFakeItemRepository(): FakeItemRepository = FakeItemRepository(
        listOf(Item(1L, "Kotlin"), Item(2L, "Compose"), Item(3L, "Navigation")),
    )

    @Provides
    fun provideItemRepository(fake: FakeItemRepository): ItemRepository = fake
}
```

```kotlin
@HiltAndroidTest
class InjectedFakeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    // hiltRule.inject() 가 이 필드를 채운다. 앱이 쓰는 것과 같은 인스턴스다.
    @Inject
    lateinit var fakeRepository: FakeItemRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        fakeRepository.items = listOf(Item(1L, "테스트가 바꾼 데이터"))
        composeTestRule.setContent { AppNavHost() }
    }

    @Test
    fun 주입받은_가짜의_데이터를_화면이_그대로_쓴다() {
        composeTestRule.onNodeWithText("로그인").performClick()
        composeTestRule.onNodeWithText("테스트가 바꾼 데이터").assertIsDisplayed()
    }
}
```

(`FakeItemRepository`의 `items`를 `var`로 바꿔 둔 것만 다르다.)

| | 방법 1 | 방법 2 | 방법 3 |
| --- | --- | --- | --- |
| 적용 범위 | `androidTest` 전체 | 테스트 클래스 하나 | `androidTest` 전체 + 테스트마다 데이터 조정 |
| 같은 타입의 다른 방법과 함께 | 방법 2와 충돌 | 방법 1과 충돌 | 방법 1을 확장한 것 |
| 언제 | 대부분의 테스트가 같은 가짜를 쓸 때 | 특정 테스트만 완전히 다른 구현(예: 항상 실패하는 저장소)이 필요할 때 | 같은 가짜에 데이터만 다르게 넣을 때 |

## 관련 아키텍처와 베스트 프랙티스

### Screen은 Hilt를 모른다

`hiltViewModel()`은 Route의 **기본값 파라미터**에서만 부른다. Screen은 상태와 콜백만 받는다. 그러면 Screen 테스트와 미리보기에 Hilt가 필요 없다. 화면 깊숙한 곳에서 `hiltViewModel()`을 부르면 그 화면의 모든 테스트가 `HiltTestActivity`를 요구하게 된다. → [`082-compose-state-hoisting-and-callbacks.md`](082-compose-state-hoisting-and-callbacks.md)

### `ViewModel` 단위 테스트에 Hilt를 쓰지 않는다

`@Inject constructor`도 평범한 생성자다. 단위 테스트에서는 가짜를 넣어 직접 만든다. Hilt 테스트 도구는 "모듈 배선이 올바른가"를 확인하는 통합 테스트에만 쓴다.

### 생성자 주입을 먼저, `@Provides`는 외부 클래스에만

내가 만든 클래스는 `@Inject constructor`로 두고, 인터페이스 연결은 `@Binds`로 한다. `@Provides`는 Retrofit, Room처럼 생성자를 고칠 수 없는 객체에만 쓴다. `@Binds`는 함수 본문이 없어 Dagger가 생성할 코드가 더 적다.

### 가짜는 저장소 경계에서

가짜를 `ItemApi`(클라이언트) 단위로 넣을 수도 있지만, 대부분의 화면·그래프 테스트는 저장소 단위 가짜로 충분하다. 저장소 구현 자체(`DefaultItemRepository`가 IO 디스패처로 넘기는가, API 응답을 어떻게 바꾸는가)는 가짜 API를 넣은 별도 단위 테스트로 검증한다.

### 모듈을 작게 나눈다

`@UninstallModules`와 `@TestInstallIn`은 **모듈 단위**로 동작한다. 네트워크, DB, 디스패처, 저장소를 한 모듈에 몰면 저장소만 바꾸고 싶어도 전부 다시 제공해야 한다. 역할별로 모듈을 나누면 바꿔 끼울 범위를 정확히 고를 수 있다.

### 언제 옮기나

| 상황 | 권장 |
| --- | --- |
| 강의 예제, 저장소 한두 개 | 수동 DI. 구조가 눈에 보여 배우기 좋다 |
| 저장소·클라이언트가 계속 늘어난다, 스코프가 여러 개다, 기능 모듈로 나눈다 | Hilt |
| 테스트 사이 격리가 중요해졌다 | Hilt. 테스트마다 컴포넌트를 새로 만든다 |

수동 DI 단계에서 "`ViewModel`은 생성자로 받는다, Screen은 상태와 콜백만 받는다"를 지켜 두었다면, 옮길 때 바뀌는 것은 조립 코드뿐이다. → [`050-android-manual-di-graph-object.md`](050-android-manual-di-graph-object.md)

## 체크리스트

- [ ] Hilt가 컨테이너를 컴파일 타임에 생성한다는 것과, 그 덕분에 바인딩 실수가 빌드 오류로 드러난다는 것을 설명할 수 있다.
- [ ] `SingletonComponent` → `ActivityRetainedComponent` → `ViewModelComponent` 계층과 각 스코프 애너테이션을 말할 수 있다.
- [ ] 스코프 애너테이션이 없으면 요청마다 새 인스턴스가 생긴다는 것을 안다.
- [ ] `@Inject constructor`, `@Binds`, `@Provides`를 언제 쓰는지 구분할 수 있다.
- [ ] Kotlin 생성자 기본값이 Hilt에서 `MissingBinding`이 되는 이유와 한정자로 푸는 방법을 안다.
- [ ] `AppNavHost`에서 의존성 파라미터를 없앤 것이 Hilt가 아니라 생성자 주입 구조라는 것과, `navController` 파라미터는 남겨야 하는 이유를 설명할 수 있다.
- [ ] `hiltViewModel()`의 현재 import 경로를 안다.
- [ ] kapt 대신 KSP를 쓰는 이유와, AGP 9에서 kapt에 `com.android.legacy-kapt`가 필요하다는 것을 안다.
- [ ] `HiltTestRunner`, `HiltAndroidRule`, `HiltTestActivity`가 각각 왜 필요한지 말할 수 있다.
- [ ] `HiltAndroidRule`이 `order = 0`이어야 하는 이유를 안다.
- [ ] `HiltTestActivity`가 필요한 테스트와 필요 없는 테스트를 구분할 수 있다.
- [ ] `@TestInstallIn`과 `@BindValue`의 교체 단위 차이, 그리고 같은 타입에 함께 쓸 수 없는 이유를 설명할 수 있다.
- [ ] 테스트마다 데이터만 다른 가짜가 필요할 때 `@Inject`로 가짜를 받아 조정하는 방법을 안다.
- [ ] Hilt가 테스트마다 컴포넌트를 새로 만들어 `@Singleton`도 테스트 사이에 공유되지 않는다는 것을 안다.

## 공식 참고 자료

- [Android Developers: Dependency injection with Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- [Android Developers: Use Hilt with other Jetpack libraries](https://developer.android.com/training/dependency-injection/hilt-jetpack)
- [Android Developers: Hilt testing guide](https://developer.android.com/training/dependency-injection/hilt-testing)
- [Android Developers: Hilt and Dagger annotations cheat sheet](https://developer.android.com/training/dependency-injection/hilt-cheatsheet)
- [Android Developers: Migrate from kapt to KSP](https://developer.android.com/build/migrate-to-ksp)
- [Dagger: Hilt components](https://dagger.dev/hilt/components)
- [Dagger: Hilt testing](https://dagger.dev/hilt/testing)
- [Dagger: KSP support](https://dagger.dev/dev-guide/ksp)
- [Now in Android](https://github.com/android/nowinandroid)
