package com.example.chapter_143

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

// ARCH-FIXME: Repository 계층이 없어 ViewModel 이 네트워크를 직접 호출한다.
//      MainViewModel → recipieService(전역) → themealdb.com
//  ViewModel 의 역할은 "화면 상태를 만드는 것"인데, 여기서는 데이터를 어떻게 가져오는지까지 안다.
//  캐싱·재시도·오프라인 대응을 넣으려면 ViewModel 이 계속 부풀어야 한다.
//  고치기:
//      class RecipeRepository(private val api: ApiService) {
//          suspend fun categories(): List<Category> = api.getCategories().categories
//      }
//      class MainViewModel(private val repo: RecipeRepository) : ViewModel()
//
// ARCH-FIXME: 의존성을 전혀 주입받지 않는다. 생성자가 비어 있고 내부에서 전역 `recipieService` 를 쓴다.
//  → 이 ViewModel 은 항상 실제 서버를 호출한다. 단위 테스트가 불가능하다.
//  고치기: 생성자 주입 + ViewModelProvider.Factory.
//
// ARCH-FIXME: `RecipeState` 가 ViewModel 안에 중첩 선언돼 있다(`MainViewModel.RecipeState`).
//  화면 코드가 `viewState: MainViewModel.RecipeState` 처럼 ViewModel 타입을 경유해야 해서
//  화면이 특정 ViewModel 에 묶인다. UI 상태는 독립된 타입으로 꺼내는 편이 낫다.
class MainViewModel : ViewModel() {

    // FIXME: 초기 상태가 `loading = false` 다. 네트워크 응답이 오기 전까지
    //  loading=false / list=empty / error=null 이라서 화면의 `when` 이 `else` 로 빠지고,
    //  로딩 스피너 대신 빈 그리드가 보인다. 초기값은 `loading = true` 여야 한다.
    private val _categoriesState = mutableStateOf(
        RecipeState(
            loading = false,
        )
    )
    val categoriesState: State<RecipeState> = _categoriesState

    init {
        fetchCategories()
    }

    private fun fetchCategories() {
        // TODO: [todos/android-viewmodelscope-launch-necessity.md](../../../../../../../../todos/android-viewmodelscope-launch-necessity.md)
        viewModelScope.launch {
            // TODO: [todos/kotlin-coroutines-exception-handling-try-catch.md](../../../../../../../../todos/kotlin-coroutines-exception-handling-try-catch.md)
            try {
                val response = recipieService.getCategories()
                _categoriesState.value = _categoriesState.value.copy(
                    list = response.categories,
                    loading = false,
                    error = null
                )
            } catch (e: Exception) {
                // FIXME: `Exception` 을 통째로 잡아 `CancellationException` 까지 삼킨다.
                //  사용자가 화면을 벗어나 코루틴이 정상 취소된 것을 "에러"로 표시하게 된다.
                //  고치기: 취소는 먼저 되던진다.
                //      catch (e: CancellationException) { throw e }
                //      catch (e: Exception) { ... }
                // FIXME: `e.message` 만 남기고 예외 객체를 버려서 원인 추적이 불가능하다.
                //  `Log.e("MainViewModel", "카테고리 조회 실패", e)` 로 스택 트레이스를 함께 남긴다.
                _categoriesState.value = _categoriesState.value.copy(
                    loading = false,
                    error = "Error fetching categories, ${e.message}"
                )
            }
        }
    }

    // FIXME: 불가능한 상태 조합이 타입으로 표현된다(loading=true 이면서 error!=null 등).
    //  그래서 화면이 `when { loading -> ...; error != null -> ...; else -> ... }` 처럼
    //  "분기 순서"에 의존하게 된다. 순서를 바꾸면 동작이 달라진다.
    //  고치기: sealed interface 로 세 상태만 존재하게 만든다.
    //      sealed interface RecipeUiState {
    //          data object Loading : RecipeUiState
    //          data class Success(val categories: List<Category>) : RecipeUiState
    //          data class Error(val message: String) : RecipeUiState
    //      }
    data class RecipeState(
        val loading: Boolean,
        val list: List<Category> = emptyList(),
        val error: String? = null
    )
}