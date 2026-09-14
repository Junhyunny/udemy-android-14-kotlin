# 라우팅 파라미터가 많아지면 어떻게 전달하는가

## 질문이 나온 코드

- [`chapter157/app/src/main/java/com/example/chapter_157/MainActivity.kt`](../chapter157/app/src/main/java/com/example/chapter_157/MainActivity.kt)
- 질문: 파라미터가 많아지면 지금처럼 path variable을 계속 늘려야 하는가? 좋지 않은 방법 같은데, 객체로 전달하거나 중간 컨텍스트·로컬 데이터베이스를 쓰고 식별자만 넘기는 방식이 있는가? 무엇이 베스트 프랙티스인가?

```kotlin
composable(route = "secondScreen/{name}/{age}") {
    val name = it.arguments?.getString("name") ?: "no name"
    val ageString = it.arguments?.getString("age") ?: "0"
    SecondScreen(name, ageString.toInt()) { ... }
}
```

## 질문 전제 점검

- **"path variable을 계속 늘리는 건 좋지 않은 방법 같다"** → **맞는 직관이다.** 다만 이유를 정확히 짚어야 한다. "지저분해서"가 아니다.
  - 인자가 늘수록 **문자열 조립과 파싱이 길어지고**, 각각이 인코딩 사고의 후보가 된다.
  - 모든 값이 `String`으로 오가므로 **타입이 사라진다.**
  - 무엇보다 **저장 가능한 상태의 총량에 한계가 있다.** 이것이 진짜 제약이다.

- **"객체로 전달하는 방법이 있나"** → 있다. 다만 **두 가지를 구분해야 한다.**
  - **작은 값 몇 개를 담은 객체** → 가능하고 권장된다. 타입 안전 route(`@Serializable data class`)가 정확히 이것이다.
  - **큰 도메인 객체를 통째로** → 하면 안 된다. route 객체는 결국 직렬화되어 저장 상태에 들어간다. "객체로 넘기니까 크기 제한이 없다"가 아니다.

- **"중간 컨텍스트나 로컬 데이터베이스를 쓰고 식별자만 넘기는 방식"** → **이것이 공식 권장 방식이다.** 질문자가 이미 정답을 짚었다.

  > "In general, you should strongly prefer passing only the minimal amount of data between destinations. For example, you should pass a key to retrieve an object rather than passing the object itself, as the total space for all saved states is limited on Android."

  "객체 대신 객체를 가져올 키를 넘겨라." 이유는 **안드로이드에서 저장 상태의 총 공간이 제한되어 있기** 때문이다. 취향 문제가 아니라 플랫폼 제약이다.

- **"무엇이 베스트 프랙티스인가"** → 하나가 아니라 **데이터의 성격에 따라 셋**이다. 아래에서 정리한다. 지금 코드의 `name`, `age`처럼 **작은 원시값 두 개**라면 사실 **지금 방식으로도 충분하다.** 문자열 조립만 타입 안전 route로 바꾸면 된다. 인자가 많아지는 상황을 미리 걱정해 구조를 복잡하게 만들 필요는 없다.

## 공부할 내용

### 왜 크기 제한이 있는가

내비게이션 인자는 결국 `Bundle`에 담겨 `onSaveInstanceState`로 저장된다. 이 데이터는 프로세스 경계를 넘어 시스템에 전달되고, 그 통로에 한계가 있다. 넘기면 예외가 난다.

```
android.os.TransactionTooLargeException: data parcel size ... bytes
```

게다가 이 예외는 **개발 중에는 잘 드러나지 않는다.** 테스트 데이터가 작아서 통과하다가, 실제 사용자의 긴 목록이나 이미지 데이터에서 터진다. 그래서 "작동하니까 괜찮다"로 판단하면 안 된다.

### 세 가지 전략

#### 전략 ① 식별자만 넘기고 목적지에서 조회한다 — **기본값**

가장 중요한 패턴이다. 목적지는 **ID만 받고, 필요한 데이터는 스스로 가져온다.**

```kotlin
@Serializable
data class ProductDetailRoute(val productId: String)      // 넘기는 건 ID 하나

// 목적지
composable<ProductDetailRoute> { entry ->
    val route = entry.toRoute<ProductDetailRoute>()
    val viewModel: ProductDetailViewModel = viewModel()    // ID로 조회한다
    ProductDetailScreen(viewModel)
}

class ProductDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ProductRepository
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ProductDetailRoute>()
    val product: StateFlow<Product?> = repository.getProduct(route.productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
```

공식 문서의 표현 그대로다.

> "Each destination should load UI data based on minimal necessary information (like item IDs)"

이 방식의 장점은 크기 문제를 피하는 것만이 아니다.

- **딥링크가 그냥 된다.** `myapp://product/ABC`만 있으면 화면을 복원할 수 있다. 객체를 통째로 넘기는 구조에서는 딥링크로 들어올 방법이 없다.
- **데이터가 항상 최신이다.** 목록에서 넘겨받은 객체는 목록을 만들 때의 스냅샷이다. 상세 화면에서 다시 조회하면 최신값을 본다. 진실 공급원이 하나로 유지된다. → [`android-repository-single-source-of-truth.md`](android-repository-single-source-of-truth.md)
- **프로세스가 죽었다 살아나도 복원된다.** ID는 작아서 안전하게 저장된다.

#### 전략 ② 타입 안전 route 객체 — 작은 값 몇 개

전부 조회로 미룰 필요는 없다. 원시값 몇 개는 route 객체에 담는 편이 단순하다.

```kotlin
@Serializable
data class SecondScreenRoute(val name: String, val age: Int)

navController.navigate(SecondScreenRoute(name, age))
```

지금 코드의 `name`/`age`가 여기 해당한다. **인자가 3~4개 이하의 원시값이면 이걸로 충분하다.**

기본값을 주면 선택 인자가 된다.

```kotlin
@Serializable
data class SearchRoute(
    val query: String,
    val category: String? = null,
    val sortBy: String = "relevance",
    val page: Int = 1
)

navController.navigate(SearchRoute(query = "커피"))   // 나머지는 기본값
```

문자열 route였다면 `"search/{query}?category={category}&sortBy={sortBy}&page={page}"`에 `navArgument`를 네 개 선언해야 했을 코드다.

**어디까지가 "작은 값"인가**에 대한 기준은 이렇게 잡는다.

- 화면을 그리기 위한 **식별 정보**인가 → route에 담아도 된다 (ID, 필터, 탭 인덱스)
- 화면에 **표시할 데이터**인가 → 담지 않는다. 조회한다 (사용자 프로필, 상품 상세, 목록)

#### 전략 ③ 공유 상태 — 여러 화면에 걸친 데이터

회원가입 3단계처럼 **여러 화면이 하나의 작업을 나눠 진행**하는 경우다. 매 단계마다 인자를 route로 실어 나르면 금방 감당이 안 된다.

```kotlin
// 3단계를 하나의 흐름으로 묶는다
@Serializable data object SignUpFlow
@Serializable data object SignUpStep1 
@Serializable data object SignUpStep2
@Serializable data object SignUpStep3

// 흐름 전체에 스코프된 ViewModel
class SignUpViewModel : ViewModel() {
    private val _form = MutableStateFlow(SignUpForm())
    val form: StateFlow<SignUpForm> = _form.asStateFlow()

    fun updateName(name: String) = _form.update { it.copy(name = name) }
    fun updateAge(age: Int) = _form.update { it.copy(age = age) }
    fun submit() = viewModelScope.launch { repository.signUp(_form.value) }
}
```

Nav2에서는 중첩 그래프의 `NavBackStackEntry`에 `ViewModel`을 스코프한다.

```kotlin
composable<SignUpStep2> { entry ->
    val parentEntry = remember(entry) { navController.getBackStackEntry(SignUpFlow) }
    val viewModel: SignUpViewModel = viewModel(parentEntry)   // 흐름 전체가 공유
    SignUpStep2Screen(viewModel)
}
```

Nav3에서는 `rememberViewModelStoreNavEntryDecorator`로 항목 단위 스코프를 붙인다.

```kotlin
NavDisplay(
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator()
    ),
    backStack = backStack,
    entryProvider = entryProvider { },
)
```

데이터가 **앱을 껐다 켜도 남아야 한다면** `ViewModel`이 아니라 저장소(Room, DataStore)에 두고 ID만 넘긴다. 질문에서 짚은 "로컬 데이터베이스에 넣고 식별자만 넘기는 방식"이 정확히 이 경우다.

### 전략 선택표

| 데이터 | 전략 | 예 |
| --- | --- | --- |
| 원시값 1~3개 | ② route 객체 | `name`, `age`, 탭 인덱스 |
| 서버/DB에 있는 것 | ① ID만 넘기고 조회 | 상품, 게시글, 사용자 |
| 여러 화면이 함께 채우는 것 | ③ 공유 `ViewModel` | 회원가입 폼, 주문서 |
| 앱을 껐다 켜도 남아야 하는 것 | ③ 저장소 + ① ID | 임시 저장된 글 |
| 큰 목록·이미지·파일 | ① 절대 넘기지 않는다 | 검색 결과, 비트맵 |

### 그 밖의 도구들

#### 화면에서 결과를 돌려받기

"목록에서 항목을 골라 이전 화면에 돌려주기"는 인자 전달과 반대 방향이다. `navigate`로 해결하려 들면 꼬인다.

```kotlin
// Nav2: 호출한 쪽의 SavedStateHandle에 결과를 써 준다
navController.previousBackStackEntry
    ?.savedStateHandle
    ?.set("selectedItem", itemId)
navController.popBackStack()

// 받는 쪽
val result by navController.currentBackStackEntry
    ?.savedStateHandle
    ?.getStateFlow<String?>("selectedItem", null)
    !!.collectAsStateWithLifecycle()
```

더 단순한 방법은 **결과를 `ViewModel`이나 저장소에 쓰고, 이전 화면이 그것을 구독**하게 하는 것이다. 화면 간 직접 통신을 없애는 쪽이 대개 더 잘 버틴다.

#### 커스텀 타입을 route에 담기

`Parcelable`/`Serializable`/`enum`도 인자로 쓸 수 있지만, 코드 축소(R8)가 켜지면 난독화로 깨질 수 있다. `@Keep`이나 `keepnames` 규칙이 필요하다. **굳이 커스텀 타입을 넘기지 않는 것이 더 간단한 해법이다.**

`enum`은 예외적으로 안전하고 유용하다.

```kotlin
@Serializable
data class ListRoute(val filter: Filter = Filter.ALL)

enum class Filter { ALL, ACTIVE, DONE }
```

### 지금 코드에 적용한다면

`name`과 `age` 두 개뿐이므로 **전략 ②로 충분하다.** 구조를 바꿀 필요 없이 문자열만 걷어내면 된다.

```kotlin
@Serializable data object FirstScreenRoute
@Serializable data class SecondScreenRoute(val name: String, val age: Int)

NavHost(navController, startDestination = FirstScreenRoute) {
    composable<FirstScreenRoute> {
        FirstScreen { name, age -> navController.navigate(SecondScreenRoute(name, age)) }
    }
    composable<SecondScreenRoute> { entry ->
        val route = entry.toRoute<SecondScreenRoute>()
        SecondScreen(route.name, route.age) { navController.popBackStack() }
    }
}
```

인자가 열 개로 늘어난다면 그때 질문을 바꿔야 한다. **"어떻게 열 개를 넘기지?"가 아니라 "이 화면이 정말 열 개를 밖에서 받아야 하나?"** 대개는 그중 대부분이 ID 하나로 조회할 수 있는 값이다.

## 관련 아키텍처와 베스트 프랙티스

### 인자가 많다는 것은 대개 설계 신호다

```kotlin
// 냄새: 화면이 데이터 운반을 떠맡고 있다
data class DetailRoute(
    val id: String, val title: String, val description: String,
    val imageUrl: String, val price: Int, val sellerName: String,
    val rating: Float, val reviewCount: Int
)

// 정리: 식별자만 받고 나머지는 조회한다
data class DetailRoute(val id: String)
```

내비게이션 인자는 **"어느 화면을 열지 결정하는 정보"**이지 **"화면에 표시할 데이터"**가 아니다. 이 구분을 지키면 인자 개수가 저절로 줄어든다.

### 화면은 데이터를 스스로 가져온다

```
FirstScreen ──(ID만)──▶ SecondScreen
                              │
                              ▼
                        SecondViewModel
                              │
                              ▼
                          Repository ──▶ 최신 데이터
```

화면 사이로 데이터가 흐르는 대신, **각 화면이 저장소에서 직접 가져온다.** 화면 간 결합이 사라지고, 어느 화면이든 딥링크로 바로 열 수 있게 된다. → [`android-repository-single-source-of-truth.md`](android-repository-single-source-of-truth.md)

### route 정의를 한곳에 모은다

```kotlin
// Routes.kt
@Serializable sealed interface AppRoute

@Serializable data object Home : AppRoute
@Serializable data class ProductDetail(val id: String) : AppRoute
@Serializable data class Search(val query: String, val filter: Filter = Filter.ALL) : AppRoute
```

`sealed interface`로 묶으면 `when`이 모든 경우를 강제한다. 화면을 추가하고 분기를 빠뜨리는 실수가 컴파일 단계에서 걸린다.

### 딥링크를 염두에 두고 설계한다

```
myapp://product/ABC123          ← ID만 있으면 복원된다
myapp://product?title=...&price=...&seller=...   ← 복원 불가능한 설계
```

앱 밖에서 들어오는 경로를 생각해 보면 **어떤 인자가 진짜 필요한지** 자연스럽게 걸러진다. 딥링크로 표현할 수 없는 인자는 route에 있으면 안 되는 인자다.

### 크기 제한을 넘기지 않도록

| 넘겨도 되는 것 | 넘기면 안 되는 것 |
| --- | --- |
| ID, 짧은 문자열, 숫자, 불리언, enum | 비트맵, 바이트 배열 |
| 필터·정렬 같은 화면 설정값 | 목록 전체 |
| 검색어 | 본문이 긴 텍스트 |
| — | 서버에서 다시 받을 수 있는 모든 것 |

## 체크리스트

- [ ] path variable을 계속 늘리는 것이 왜 문제인지 세 가지로 설명할 수 있다.
- [ ] "객체가 아니라 객체를 가져올 키를 넘기라"는 공식 원칙을 말할 수 있다.
- [ ] `TransactionTooLargeException`이 언제 발생하는지 설명할 수 있다.
- [ ] 식별자만 넘기는 방식의 장점 세 가지(크기, 딥링크, 최신성)를 말할 수 있다.
- [ ] route에 담아도 되는 값과 조회해야 하는 값을 구분할 수 있다.
- [ ] 타입 안전 route에서 기본값으로 선택 인자를 만들 수 있다.
- [ ] 여러 화면이 공유하는 데이터를 `ViewModel` 스코프로 다룰 수 있다.
- [ ] 화면에서 결과를 돌려받는 방법을 안다.
- [ ] 인자가 많아지는 것이 설계 신호라는 것을 설명할 수 있다.
- [ ] 딥링크로 복원 가능한지로 route 설계를 점검할 수 있다.

## 공식 참고 자료

- [Android Developers: Pass data between destinations](https://developer.android.com/guide/navigation/use-graph/pass-data)
- [Android Developers: Type safety in Navigation Compose](https://developer.android.com/guide/navigation/design/type-safety)
- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation)
- [Android Developers: Save and manage navigation state (Navigation 3)](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Android Developers: Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
- [Android Developers: Data layer](https://developer.android.com/topic/architecture/data-layer)
- [Android Developers: State holders and UI State](https://developer.android.com/topic/architecture/ui-layer/stateholders)
