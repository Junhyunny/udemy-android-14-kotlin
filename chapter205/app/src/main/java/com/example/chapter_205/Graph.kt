package com.example.chapter_205

import android.content.Context
import androidx.room.Room

// ARCH-FIXME: `Graph` 는 의존성 주입(DI)이 아니라 서비스 로케이터(Service Locator) 패턴이다.
//  둘의 차이가 중요하다.
//    DI       : 필요한 것을 "받는다". 생성자만 보면 무엇에 의존하는지 전부 드러난다.
//    로케이터 : 필요한 것을 "가져온다". 코드 안에서 전역에 손을 뻗으므로 의존 관계가 숨는다.
//
//  지금 구조의 구체적인 문제:
//   1) `WishViewModel(private val wishRepository: WishRepository = Graph.wishRepository)`
//      기본값으로 전역에 접근한다. 겉보기엔 주입 가능해 보이지만 실제로는 아무도 주입하지 않고,
//      테스트에서 가짜 repository 를 넣으려면 호출부를 전부 찾아 고쳐야 한다.
//   2) `lateinit var database` 는 가변 전역이다. `provide()` 전에 접근하면 즉시 크래시다.
//      컴파일러가 순서를 보장해 주지 못한다.
//   3) 앱 전체가 `Graph` 라는 한 점에 묶여, 모듈을 나누는 순간 순환 의존이 생기기 쉽다.
//
//  고치기(단계별):
//   [1단계] 최소한 불변으로 만들고 초기화 순서를 타입으로 강제한다.
//        object Graph {
//            private var _database: WishDatabase? = null
//            val database get() = checkNotNull(_database) { "Graph.provide() 를 먼저 호출해야 한다" }
//        }
//   [2단계] ViewModel 은 기본값 대신 Factory 로 주입받는다.
//        class WishViewModel(private val repo: WishRepository) : ViewModel() {
//            companion object {
//                val Factory = viewModelFactory { initializer { WishViewModel(Graph.wishRepository) } }
//            }
//        }
//        // 화면에서: viewModel(factory = WishViewModel.Factory)
//        → 전역 접근이 "조립 지점 한 곳"에만 남고, 생성자는 정직해진다.
//   [3단계] 규모가 커지면 Hilt 로 옮긴다. @HiltViewModel + @Inject constructor 로
//        위 보일러플레이트가 사라지고 스코프 관리도 자동이 된다.
object Graph {
    lateinit var database: WishDatabase

    // TODO: [todos/004-kotlin-by-lazy-delegate.md](../../../../../../../../todos/004-kotlin-by-lazy-delegate.md)
    val wishRepository by lazy {
        WishRepository(database.wishDao())
    }

    // TODO: [todos/020-android-context-types-and-application-context.md](../../../../../../../../todos/020-android-context-types-and-application-context.md)
    fun provide(context: Context) {
        database = Room.databaseBuilder(context, WishDatabase::class.java, "wishlist.db").build()
    }
}