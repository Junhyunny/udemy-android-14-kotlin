package com.example.chapter_127

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

// TODO: [todos/android-viewmodel-role-and-remember.md](../../../../../../../../todos/android-viewmodel-role-and-remember.md)
// ARCH-FIXME: 이 챕터는 MVVM 세 계층이 형태만 갖춰져 있고 실제로는 연결이 끊겨 있다.
//      Model(CounterRepository) — 존재하지만 호출이 주석 처리돼 아무도 쓰지 않는다
//      ViewModel                — repository 를 받아 놓고 자체 상태만 증감시킨다
//      View                     — ViewModel 을 직접 생성한다(스코프 밖)
//  결과: repository 의 값과 화면의 값이 영영 달라진다. "계층을 나눴다"는 형식만 남았다.
//  고치기: repository 를 단일 진실 공급원으로 삼는다.
//      fun increment() {
//          repository.incrementCounter()
//          _count.value = repository.getCounter().count
//      }
//  더 나아가면 repository 가 `Flow<Int>` 를 내보내고 ViewModel 이 구독하는 형태가 정석이다.
//  그러면 다른 곳에서 값을 바꿔도 화면이 자동으로 따라온다.
//
// ARCH-FIXME: 생성자 주입은 하고 있지만 주입해 주는 주체가 없다.
//  `MainActivity` 가 `CounterViewModel(CounterRepository())` 로 직접 조립하고 있어
//  ViewModel 스코프를 벗어났고, repository 인스턴스도 매번 새로 만들어진다.
//  고치기: ViewModelProvider.Factory 로 주입하고 repository 는 한 곳에서 관리한다.
class CounterViewModel(private val repository: CounterRepository) : ViewModel() {
    private val _count = mutableStateOf(repository.getCounter().count)

    // FIXME: `_count` 로 숨겨 놓고 공개 타입이 `MutableState` 라 캡슐화가 무의미하다.
    //  화면에서 `viewModel.count.value = 100` 처럼 상태를 직접 바꿀 수 있다.
    //  고치기: 읽기 전용 타입으로 내린다. → `val count: State<Int> = _count`
    val count: MutableState<Int> = _count

    fun increment() {
        _count.value++
        // TODO: [todos/android-repository-single-source-of-truth.md](../../../../../../../../todos/android-repository-single-source-of-truth.md)
//        repository.incrementCounter()
    }

    fun decrement() {
        _count.value--
//        repository.decrementCounter()
    }
}