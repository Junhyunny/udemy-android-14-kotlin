# `Graph` object로 의존성을 주입하는 방식은 베스트 프랙티스인가

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/WishViewModel.kt`](../chapter205/app/src/main/java/com/example/chapter_205/WishViewModel.kt)
- [`chapter205/app/src/main/java/com/example/chapter_205/Graph.kt`](../chapter205/app/src/main/java/com/example/chapter_205/Graph.kt)
- 질문: 강의에서는 의존성 주입 방식을 `Graph` object 객체를 쓰는 방식으로 구현했는데, 이게 베스트 프랙티스인지 정리해 달라.

```kotlin
object Graph {
    lateinit var database: WishDatabase
    val wishRepository by lazy { WishRepository(database.wishDao()) }
    fun provide(context: Context) { database = Room.databaseBuilder(...).build() }
}

class WishViewModel(
    private val wishRepository: WishRepository = Graph.wishRepository   // ← 기본값으로 꺼내 쓴다
) : ViewModel()
```

## 질문 전제 점검

- **결론부터: 베스트 프랙티스는 아니다. 다만 "틀렸다"고 하기도 어렵다.** 학습 예제 규모에서는 합리적인 타협이고, 공식 문서도 수동 DI를 하나의 단계로 소개한다. 문제는 **이 패턴이 규모가 커질 때 무너지는 지점이 분명하다**는 것이다.

- **먼저 용어를 바로잡을 필요가 있다. 지금 코드는 엄밀히 말해 "주입"이 아니라 "서비스 로케이터"에 가깝다.**

  ```kotlin
  // 주입(injection): 외부에서 넣어 준다. 클래스는 어디서 왔는지 모른다
  class WishViewModel(private val repo: WishRepository)

  // 서비스 로케이터: 클래스가 스스로 전역 저장소에서 꺼내 온다
  class WishViewModel(private val repo: WishRepository = Graph.wishRepository)
  ```

  기본값이 `Graph.wishRepository`이므로 **`WishViewModel`은 `Graph`를 알고 있다.** 생성자 파라미터가 있어서 주입처럼 보이지만, 실제 호출부는 `WishViewModel()`로 만들기 때문에 항상 전역을 거친다. 이 차이가 테스트와 결합도에서 그대로 드러난다.

- **공식 문서가 권하는 형태와 무엇이 다른가**를 보면 문제가 또렷해진다.

  > "`AppContainer`는 싱글톤 패턴을 따르지 않습니다. Kotlin에서 `object`가 아니고, Java에서 `Singleton.getInstance` 메서드로 접근하지 않습니다."

  공식 문서의 `AppContainer`는 **`Application`의 프로퍼티로 들고 있는 일반 클래스**다. `chapter205`의 `Graph`는 `object`, 즉 전역 싱글톤이다. **이 한 글자 차이가 테스트 가능성을 가른다.**

- **그럼에도 잘한 부분이 있다.** `WishRepository`와 `WishDao`가 계층으로 분리되어 있고, `ViewModel`이 `Context`나 `Room`을 직접 알지 않는다. **의존성의 방향 자체는 올바르다.** 고칠 것은 "어떻게 전달하느냐"뿐이다.

## 공부할 내용

### 지금 구조의 구체적인 문제

#### ① 테스트에서 갈아 끼울 수 없다

```kotlin
// 테스트에서 가짜 저장소를 쓰고 싶다
val viewModel = WishViewModel(FakeWishRepository())   // 이건 된다
```

여기까지는 괜찮아 보인다. 문제는 **실제 앱 코드가 그 경로를 쓰지 않는다**는 것이다.

```kotlin
// RecipeApp / Navigation.kt
val viewModel: WishViewModel = viewModel()   // 기본 생성자 → Graph.wishRepository
```

`viewModel()`은 인자 없는 생성자를 부르므로 **언제나 `Graph`를 거친다.** 화면을 포함한 통합 테스트에서는 가짜를 넣을 방법이 없다. `Graph`가 `object`라서 `Graph.database`를 테스트용으로 바꾸는 것도 전역 상태를 오염시킨다.

```kotlin
// 테스트 A 가 바꿔 놓은 값이 테스트 B 에 남는다
Graph.database = inMemoryDb()     // lateinit var 라 대입은 되지만...
Graph.wishRepository              // by lazy 라 이미 만들어졌으면 안 바뀐다
```

`by lazy`는 **한 번 만들어지면 다시 못 바꾼다.** → [`004-kotlin-by-lazy-delegate.md`](004-kotlin-by-lazy-delegate.md)

#### ② 초기화 누락을 컴파일러가 못 잡는다

```kotlin
lateinit var database: WishDatabase
```

`Graph.provide()`를 부르지 않으면 `UninitializedPropertyAccessException`이 **런타임에** 터진다. 매니페스트에서 `android:name=".WishListApp"`을 빠뜨려도 마찬가지다. 생성자 주입이라면 애초에 컴파일이 안 된다.

#### ③ 숨은 의존성

```kotlin
class WishViewModel(private val wishRepository: WishRepository = Graph.wishRepository)
```

시그니처만 봐서는 이 클래스가 `Graph`에, 나아가 `Room`과 `Context`에 묶여 있다는 것을 알 수 없다. **의존성이 기본값 안에 숨는다.**

#### ④ 확장되지 않는다

의존성이 늘어나면 `Graph`가 계속 부푼다.

```kotlin
object Graph {
    lateinit var database: WishDatabase
    val wishRepository by lazy { ... }
    val userRepository by lazy { ... }
    val settingsRepository by lazy { ... }
    val analytics by lazy { ... }          // 전부 앱 수명 싱글톤이 된다
}
```

**모든 것이 앱 수명 싱글톤이 되는 것**이 문제다. "로그인한 동안만 사는 객체", "이 화면에만 있는 객체" 같은 범위를 표현할 방법이 없다.

### 공식 문서가 제시하는 단계

안드로이드 문서는 DI를 세 단계로 소개한다.

```
1단계: 수동 생성자 주입     (컨테이너 없음)
2단계: 컨테이너 + Application (AppContainer 패턴)
3단계: Hilt                 (자동 생성)
```

> "When possible, it's recommended to use Hilt rather than manual dependency injection."

#### 2단계 — `AppContainer` 패턴

```kotlin
class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext, WishDatabase::class.java, "wishlist.db"
    ).build()

    val wishRepository: WishRepository by lazy { WishRepository(database.wishDao()) }
}

class WishListApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

`Graph`와 거의 같아 보이지만 **`object`가 아니라는 점**이 다르다.

- 테스트에서 `AppContainer(testContext)`를 새로 만들 수 있다
- 인스턴스가 여러 개 존재할 수 있어 테스트끼리 격리된다
- `Application`이 소유하므로 수명이 명확하다

#### `ViewModel`에 전달하기 — 팩토리

```kotlin
class WishViewModel(private val wishRepository: WishRepository) : ViewModel() {
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as WishListApp)
                WishViewModel(app.container.wishRepository)
            }
        }
    }
}

// 사용
val viewModel: WishViewModel = viewModel(factory = WishViewModel.Factory)
```

**기본값이 사라지고 의존성이 생성자에만 남는다.** `WishViewModel`은 이제 `Graph`도 `Application`도 모른다. → [`046-compose-viewmodel-function-vs-manual.md`](046-compose-viewmodel-function-vs-manual.md)

#### 3단계 — Hilt

```kotlin
@HiltAndroidApp
class WishListApp : Application()

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WishDatabase =
        Room.databaseBuilder(context, WishDatabase::class.java, "wishlist.db").build()

    @Provides
    fun provideWishDao(db: WishDatabase): WishDao = db.wishDao()
}

@HiltViewModel
class WishViewModel @Inject constructor(
    private val wishRepository: WishRepository
) : ViewModel()

// 사용 — 팩토리도 필요 없다
val viewModel: WishViewModel = hiltViewModel()
```

`Graph`, `provide()`, `lateinit`, 팩토리가 전부 사라진다. **의존성 그래프를 컴파일 타임에 검증**하므로 빠뜨리면 빌드가 실패한다.

### 단계별 비교

| | `object Graph` (지금) | `AppContainer` | Hilt |
| --- | --- | --- | --- |
| 보일러플레이트 | 적다 | 중간 | 초기 설정 후 적다 |
| 테스트에서 교체 | ❌ 어렵다 | ✅ 가능 | ✅ `@TestInstallIn` |
| 누락 검출 | 런타임 | 런타임 | **컴파일 타임** |
| 범위(scope) 표현 | ❌ 전부 싱글톤 | 수동 | ✅ 애노테이션 |
| 학습 비용 | 거의 없다 | 낮다 | 높다 |
| 적합한 규모 | 예제·프로토타입 | 소~중형 | 중형 이상 |

## 관련 아키텍처와 베스트 프랙티스

### 이 프로젝트에 무엇을 권하나

**강의를 따라가는 중이라면 `Graph`를 그대로 두어도 된다.** 저장소 지침도 "강의의 학습 단계와 예제 의도를 보존한다"고 되어 있고, DI 프레임워크는 지금 배우는 주제가 아니다.

다만 **두 가지는 지금 고쳐도 흐름을 해치지 않는다.**

```kotlin
// ① 기본값으로 전역을 끌어오지 않는다
class WishViewModel(private val wishRepository: WishRepository) : ViewModel()

// ② 생성 지점에서만 Graph 를 안다
val viewModel: WishViewModel = viewModel(factory = viewModelFactory {
    initializer { WishViewModel(Graph.wishRepository) }
})
```

이렇게만 해도 **`WishViewModel`이 `Graph`를 모르게 된다.** 나중에 Hilt로 옮길 때 이 클래스는 손대지 않아도 된다.

### 의존성 방향은 이미 맞다

```
WishViewModel  →  WishRepository  →  WishDao  →  Room
```

각 계층이 아래만 알고 위를 모른다. `ViewModel`이 `Room`을 직접 부르지 않는다는 점이 중요하다. **DI 방식을 바꾸더라도 이 구조는 그대로 간다.** → [`049-android-repository-single-source-of-truth.md`](049-android-repository-single-source-of-truth.md)

### 인터페이스로 추상화할지의 판단

```kotlin
interface WishRepository {
    fun getAllWishes(): Flow<List<Wish>>
    suspend fun addWish(wish: Wish)
}
class WishRepositoryImpl(private val dao: WishDao) : WishRepository
```

이렇게 하면 테스트에서 `FakeWishRepository`를 만들기 쉽다. 다만 **구현이 하나뿐이고 바꿀 계획도 없다면 과한 추상화**다. 코틀린에서는 인터페이스 없이도 `open class`나 목 라이브러리로 대체할 수 있다. 판단 기준은 "구현이 둘 이상이 될 가능성"과 "테스트에서 가짜가 필요한가"다.

### `object` 싱글톤을 쓸 때의 원칙

`object` 자체가 나쁜 것은 아니다. **상태가 없고 부작용이 없는 유틸리티**라면 좋은 선택이다.

```kotlin
object DateFormatter {                  // 상태 없음. 문제없다
    fun format(millis: Long): String = ...
}

object Graph {                          // 가변 상태를 들고 있다. 주의
    lateinit var database: WishDatabase
}
```

**가변 상태를 가진 `object`가 위험 신호다.** 테스트 격리가 깨지고, 초기화 순서에 의존하게 된다.

### 테스트를 먼저 그려 본다

DI 방식을 고를 때 가장 빠른 판단법은 **"이 클래스를 어떻게 테스트할 것인가"**를 먼저 써 보는 것이다.

```kotlin
@Test
fun `위시를 추가하면 목록에 반영된다`() = runTest {
    val fake = FakeWishRepository()
    val viewModel = WishViewModel(fake)        // 이 줄이 자연스럽게 써지는가?
    viewModel.addWish(Wish(title = "a", description = "b"))
    assertEquals(1, fake.wishes.size)
}
```

이 코드가 쉽게 써진다면 DI 구조가 괜찮은 것이다. `Graph`를 건드려야 한다면 신호다.

## 체크리스트

- [ ] 의존성 주입과 서비스 로케이터의 차이를 설명할 수 있다.
- [ ] 기본값에 전역 객체를 넣는 것이 왜 "주입"이 아닌지 설명할 수 있다.
- [ ] 공식 `AppContainer`가 `object`가 아닌 이유를 말할 수 있다.
- [ ] `object Graph`에서 테스트 격리가 깨지는 지점을 짚을 수 있다.
- [ ] `by lazy`가 값을 다시 못 바꾼다는 점이 테스트에 주는 영향을 안다.
- [ ] `lateinit`으로 인한 초기화 누락이 런타임 오류가 되는 것을 안다.
- [ ] 모든 것이 앱 수명 싱글톤이 되는 문제를 설명할 수 있다.
- [ ] `ViewModelProvider.Factory`로 생성자 주입을 연결할 수 있다.
- [ ] 수동 DI → `AppContainer` → Hilt의 단계를 설명할 수 있다.
- [ ] 가변 상태를 가진 `object`가 위험 신호인 이유를 안다.
- [ ] 테스트 코드를 먼저 그려 보고 DI 구조를 판단할 수 있다.

## 공식 참고 자료

- [Android Developers: Manual dependency injection](https://developer.android.com/training/dependency-injection/manual)
- [Android Developers: Dependency injection in Android](https://developer.android.com/training/dependency-injection)
- [Android Developers: Dependency injection with Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- [Android Developers: Hilt and Jetpack integrations](https://developer.android.com/training/dependency-injection/hilt-jetpack)
- [Android Developers: Create ViewModels with dependencies](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories)
- [Android Developers: Guide to app architecture](https://developer.android.com/topic/architecture)
- [Android Developers: Testing apps on Android](https://developer.android.com/training/testing)
