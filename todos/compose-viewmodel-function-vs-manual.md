# `viewModel()` 함수와 직접 생성의 차이

## 질문이 나온 코드

- [`chapter127/app/src/main/java/com/example/chapter_127/MainActivity.kt`](../chapter127/app/src/main/java/com/example/chapter_127/MainActivity.kt)
- 질문: 강의는 `androidx.lifecycle.viewmodel.compose.viewModel`을 쓰는데 `ViewModel` 객체를 직접 만들어도 상태 관리가 잘 된다. 왜 가능한가? `viewModel` 함수를 쓰지 않아도 되는가? 차이와 부작용은 무엇인가? `lifecycle.viewmodel` 패키지에는 어떤 기능이 있는가?

## 질문 전제 점검

- **"직접 만들어 사용해도 정상적으로 상태 관리가 가능하네?"** → 관찰은 정확하지만 결론은 이르다. **재구성에 대해서는 동작하고, 구성 변경에서는 깨진다.** 화면을 회전시키면 액티비티가 재생성되면서 `setContent`가 다시 실행되고, `CounterViewModel(CounterRepository())`도 새로 만들어져 카운트가 0으로 돌아간다. 지금 테스트에서 문제가 없어 보인 이유는 회전을 시키지 않았기 때문일 가능성이 높다.
- **재구성에서 살아남은 이유**도 우연이 아니다. `setContent`의 람다는 상태를 읽지 않으므로 재구성되지 않는다. 상태를 읽는 곳은 `CounterApp` 안이다. 그래서 `viewModel` 변수를 만드는 줄이 다시 실행되지 않았다. 만약 저 람다가 상태를 하나라도 읽게 되면 매 재구성마다 새 `ViewModel`이 생긴다. 사용자가 남긴 로그에서 `re-composable`이 한 번만 찍힌 것이 그 증거다.
- **"`viewModel` 함수를 사용하지 않아도 되는 건지"** → 쓰는 것이 맞다. 다만 현재 프로젝트에는 `androidx.lifecycle:lifecycle-viewmodel-compose` 의존성이 없어서 `viewModel()`을 호출할 수 없는 상태다. 강의와 달라진 지점이 여기일 가능성이 크다.

## 공부할 내용

### `viewModel()`이 하는 일

`viewModel()`은 "기존 `ViewModel`을 반환하거나 새로 만든다." 직접 생성자를 호출하는 것과 결정적으로 다른 점은 **어디에 보관하느냐**다.

```
직접 생성:   CounterViewModel(...)  →  호출한 자리에 그때그때 새 객체
viewModel(): LocalViewModelStoreOwner → ViewModelStore에서 조회, 없으면 생성 후 저장
```

`ViewModelStore`는 액티비티가 구성 변경으로 재생성될 때도 유지되는 저장소다. 그래서 문서는 이렇게 설명한다. "컴포저블이 액티비티에서 사용된다면, `viewModel()`은 액티비티가 끝나거나 프로세스가 종료될 때까지 같은 인스턴스를 반환한다."

### 두 방식 비교

| | 직접 생성 | `viewModel()` |
| --- | --- | --- |
| 재구성 | 읽는 상태가 없으면 유지, 있으면 매번 새로 생성 | 항상 같은 인스턴스 |
| 구성 변경(회전) | **새 인스턴스, 상태 초기화** | 같은 인스턴스, 상태 유지 |
| `onCleared()` | 호출되지 않음 | 스코프 종료 시 호출 |
| `viewModelScope` | 취소되지 않아 코루틴이 남을 수 있음 | 자동 취소 |
| `SavedStateHandle` | 사용 불가 | 주입 가능 |
| 내비게이션 목적지 스코프 | 직접 관리해야 함 | 목적지 단위로 자동 스코프 |

지금 예제는 카운터 하나라 회전 시 값이 0이 되는 정도지만, 여기에 네트워크 요청이나 코루틴이 붙으면 문제가 커진다. `ViewModel`이 정리되지 않으므로 진행 중이던 작업이 취소되지 않고, 회전할 때마다 요청이 새로 시작되어 중복 호출이 쌓인다.

### 부작용을 정리하면

1. **상태 소실**: 구성 변경마다 초기화된다. `ViewModel`을 쓰는 가장 큰 이유가 사라진다.
2. **자원 누수**: `onCleared()`가 호출되지 않아 구독, 코루틴, 리스너가 정리되지 않는다.
3. **프로세스 종료 대응 불가**: `SavedStateHandle`을 받을 수 없어 시스템이 프로세스를 종료한 뒤 상태를 복원할 수 없다.
4. **의존성 주입과의 단절**: Hilt의 `hiltViewModel()`이나 `ViewModelProvider.Factory` 경로를 쓸 수 없다.
5. **테스트와 프리뷰**: 기본값을 `viewModel()`로 두면 프리뷰에서 다른 구현을 넣기 쉬운데, 직접 생성하면 호출부를 고쳐야 한다.

### `lifecycle.viewmodel` 패키지 구성

역할별로 나뉘어 있다.

| 아티팩트 | 제공하는 것 |
| --- | --- |
| `androidx.lifecycle:lifecycle-viewmodel` | `ViewModel`, `ViewModelStore`, `ViewModelStoreOwner`, `ViewModelProvider` |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | `viewModelScope` 확장 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `viewModel()` 컴포저블, `LocalViewModelStoreOwner` |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | `SavedStateHandle`, `SavedStateViewModelFactory` |
| `androidx.hilt:hilt-navigation-compose` | `hiltViewModel()` (Hilt 사용 시) |

`viewModel()`을 쓰려면 다음을 추가한다.

```toml
# gradle/libs.versions.toml
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.androidx.lifecycle.viewmodel.compose)
```

### 생성자에 인자가 있을 때

`CounterViewModel`은 `CounterRepository`를 생성자로 받는다. 인자가 있는 `ViewModel`은 팩터리가 필요하다.

```kotlin
class CounterViewModel(private val repository: CounterRepository) : ViewModel() {
    companion object {
        val Factory = viewModelFactory {
            initializer { CounterViewModel(CounterRepository()) }
        }
    }
}

@Composable
fun CounterRoute(viewModel: CounterViewModel = viewModel(factory = CounterViewModel.Factory)) { ... }
```

의존성이 늘어나면 Hilt 같은 주입 프레임워크로 옮기는 것이 일반적이다. "생성자 인자 때문에 직접 만들었다"가 직접 생성의 흔한 이유인데, 팩터리가 그 자리를 대신한다.

## 관련 아키텍처와 베스트 프랙티스

### `ViewModel`은 화면 수준에서만 얻는다

"보통 화면 수준 컴포저블에서 `ViewModel` 인스턴스에 접근한다. 즉 액티비티, 프래그먼트, 내비게이션 그래프 목적지에서 호출되는 루트 컴포저블 가까이에서다. `ViewModel`이 기본적으로 그 화면 수준 객체에 스코프되기 때문이다."

권장 구조는 화면마다 두 겹을 두는 것이다.

```kotlin
@Composable
fun CounterRoute(viewModel: CounterViewModel = viewModel()) {   // ViewModel을 아는 층
    val count by viewModel.count
    CounterScreen(count = count, onIncrement = viewModel::increment, onDecrement = viewModel::decrement)
}

@Composable
fun CounterScreen(count: Int, onIncrement: () -> Unit, onDecrement: () -> Unit) { ... }  // 순수 UI
```

파라미터 기본값으로 `viewModel()`을 두는 패턴에는 이유가 있다. 평소에는 인자 없이 호출하고, 테스트나 프리뷰에서는 가짜 구현을 넘길 수 있다.

### `ViewModel`과 `Repository`는 어디에 두는가

| 객체 | 소유자 | 이유 |
| --- | --- | --- |
| 컴포저블 지역 UI 상태 | `remember` | 화면 안에서만 의미 있음 |
| 화면 상태와 비즈니스 로직 | `ViewModel` | 구성 변경을 넘어 유지 |
| `Repository`, 데이터 소스 | 앱 수준 컨테이너(또는 DI) | 여러 화면이 공유, 앱 수명과 함께 |

현재 코드처럼 `CounterRepository()`를 컴포저블 안에서 만들면, `ViewModel`이 새로 만들어질 때마다 저장소도 새로 생긴다. 저장소가 데이터의 단일 진실 공급원이어야 한다는 원칙과 충돌한다. 관련 내용은 [`android-repository-single-source-of-truth.md`](android-repository-single-source-of-truth.md)에 정리했다.

DI 프레임워크를 쓰지 않는 단계라면 `Application` 서브클래스에 수동 컨테이너를 두는 방식이 출발점으로 적당하다.

```kotlin
class CounterApplication : Application() {
    val container by lazy { AppContainer() }
}

class AppContainer {
    val counterRepository by lazy { CounterRepository() }
}
```

### 정리하면

"직접 만들어도 동작한다"는 관찰은 재구성 범위 안에서만 참이다. `viewModel()`은 편의 함수가 아니라 **인스턴스의 수명을 플랫폼에 위임하는 장치**이며, 그 위임이 곧 구성 변경 대응, 정리 보장, 저장 상태 복원의 근거가 된다.

## 체크리스트

- [ ] 직접 생성한 `ViewModel`이 재구성에서는 살아남는 이유를 설명할 수 있다.
- [ ] 구성 변경에서 깨지는 이유를 설명할 수 있다.
- [ ] `viewModel()`이 인스턴스를 어디에서 가져오는지 설명할 수 있다.
- [ ] 직접 생성의 부작용을 세 가지 이상 말할 수 있다.
- [ ] `lifecycle.viewmodel` 계열 아티팩트의 역할을 구분할 수 있다.
- [ ] 생성자 인자가 있는 `ViewModel`을 팩터리로 만들 수 있다.
- [ ] `ViewModel`과 `Repository`를 각각 어디에서 소유해야 하는지 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Android Developers: Compose and other libraries (viewModel)](https://developer.android.com/develop/ui/compose/libraries)
- [Android Developers: Create ViewModels with dependencies](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories)
- [Android Developers: Saved State module for ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate)
- [Android Developers: Dependency injection in Android](https://developer.android.com/training/dependency-injection)
