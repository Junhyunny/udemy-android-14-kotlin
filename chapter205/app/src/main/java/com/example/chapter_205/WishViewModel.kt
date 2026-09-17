package com.example.chapter_205

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chapter_205.data.Wish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ARCH-FIXME: 생성자 기본값으로 전역(`Graph`)에 접근한다. "주입처럼 보이는 하드코딩"이다.
//  이 ViewModel 은 `Graph` 가 초기화된 안드로이드 환경 밖에서는 만들 수 없어, 순수 단위 테스트가 불가능하다.
//  고치기: 기본값을 없애고 Factory 로 주입한다(Graph.kt 의 [2단계] 참고).
//      class WishViewModel(private val wishRepository: WishRepository) : ViewModel()
//
// ARCH-FIXME: MVVM 의 View / ViewModel 경계가 한 군데 뚫려 있다.
//  `getWishById(id)` 가 `Flow<Wish>` 를 그대로 화면에 돌려주고, 화면이 `collectAsState` 로 직접 구독한다.
//  ViewModel 이 "데이터를 전달하는 통로" 역할만 하고, 화면이 데이터 계층의 타입을 알게 된다.
//  고치기: 화면은 id 만 알리고, ViewModel 이 상태로 가공해 노출한다.
//      fun loadWish(id: Long) { viewModelScope.launch { _uiState.update { ... } } }
//      val uiState: StateFlow<WishUiState>
class WishViewModel(
    // TODO: [todos/android-manual-di-graph-object.md](../../../../../../../../todos/android-manual-di-graph-object.md)
    private val wishRepository: WishRepository = Graph.wishRepository
) : ViewModel() {
    // FIXME: `var` + `MutableState` 라 두 겹으로 열려 있다.
    //  화면에서 `viewModel.wishTitleState.value = "x"` 도, `viewModel.wishTitleState = ...` 도 가능하다.
    //  실제로 AddEditDetailView 가 이 상태를 직접 대입해 쓰고 있다.
    //  고치기: 내부는 private, 외부는 읽기 전용으로 내린다.
    //      private val _wishTitleState = mutableStateOf("")
    //      val wishTitleState: State<String> = _wishTitleState
    //  변경은 이미 있는 `onWishTitleChange()` 로만 하게 한다.
    var wishTitleState = mutableStateOf("")
    var wishDescriptionState = mutableStateOf("")

    fun onWishTitleChange(newValue: String) {
        wishTitleState.value = newValue
    }

    fun onWishDescriptionState(newValue: String) {
        wishDescriptionState.value = newValue
    }

    // FIXME: `lateinit` 프로퍼티를 코루틴 안에서 늦게 대입하고 있다. 경합(race)이다.
    //  `HomeView` 가 `viewModel.getAllWishes` 를 읽는 시점에 대입이 끝났다는 보장이 없어
    //  `UninitializedPropertyAccessException` 으로 앱이 죽을 수 있다.
    //  (지금 안 죽는 것은 Dispatchers.Main.immediate 가 블록을 즉시 실행해 주는 우연에 가깝다.)
    //
    //  게다가 `getAllWishes()` 는 `Flow` 를 "만들어 반환"할 뿐 suspend 함수가 아니다.
    //  코루틴이 애초에 필요 없다.
    //  고치기: 프로퍼티 초기화 한 줄이면 된다.
    //      val getAllWishes: Flow<List<Wish>> = wishRepository.getAllWishes()
    lateinit var getAllWishes: Flow<List<Wish>>

    init {
        // TODO: [todos/android-viewmodelscope-launch-necessity.md](../../../../../../../../todos/android-viewmodelscope-launch-necessity.md)
        viewModelScope.launch {
            getAllWishes = wishRepository.getAllWishes()
        }
    }

    // FIXME: 여기서 `Dispatchers.IO` 는 지금 코드에서는 "반드시 필요"하다.
    //  `WishDao` 의 addWish/updateWish/deleteWish 가 `suspend` 가 아닌 blocking 함수라서,
    //  메인 스레드에서 부르면 Room 이 IllegalStateException(Cannot access database on the main thread)을 던진다.
    //  → 이 줄을 지우면 앱이 죽는다.
    //
    //  문제는 "필요하다"는 사실 자체가 설계 결함의 신호라는 점이다.
    //  스레드 전환 책임이 ViewModel 로 새어 나와 있다. 호출하는 쪽이 DAO 의 구현 방식(blocking 여부)을
    //  알아야만 올바르게 부를 수 있다는 뜻이다.
    //  게다가 `WishRepository.addWish` 는 `suspend` 로 선언돼 있으면서 내부에서 blocking 호출을 한다.
    //  suspend 인데 main-safe 하지 않은 "거짓말하는 suspend 함수"다.
    //
    //  고치기(근본): DAO 를 suspend 로 바꾼다. 그러면 Room 이 알아서 자체 실행기로 옮겨 주고,
    //  repository 의 suspend 선언이 진실이 되며, ViewModel 의 `Dispatchers.IO` 는 그때 비로소 불필요해진다.
    //      @Insert suspend fun addWish(wish: Wish)
    //      @Update suspend fun updateWish(wish: Wish)
    //      @Delete suspend fun deleteWish(wish: Wish)
    //  (Flow 를 반환하는 getAll/getWishById 는 지금처럼 non-suspend 가 맞다)
    fun addWish(wish: Wish) {
        // TODO: [todos/kotlin-coroutine-dispatchers.md](../../../../../../../../todos/kotlin-coroutine-dispatchers.md)
        viewModelScope.launch(Dispatchers.IO) {
            wishRepository.addWish(wish = wish)
        }
    }

    fun getWishById(id: Long): Flow<Wish> {
        return wishRepository.getWishById(id)
    }

    fun updateWish(wish: Wish) {
        viewModelScope.launch(Dispatchers.IO) {
            wishRepository.updateWish(wish)
        }
    }

    fun deleteWish(wish: Wish) {
        viewModelScope.launch(Dispatchers.IO) {
            wishRepository.deleteWish(wish)
        }
    }
}