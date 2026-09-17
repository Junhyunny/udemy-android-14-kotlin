package com.example.chapter_127

// FIXME: `CounterRepository` 가 단일 진실 공급원(single source of truth) 역할을 못 하고 있다.
//  ViewModel 이 `_count` 를 자체적으로 증감시키고 repository 호출은 주석 처리돼 있어서,
//  repository 의 값과 화면의 값이 서로 달라진다.
//  고치기: repository 를 진실로 삼고 ViewModel 은 그 결과를 읽어 상태로 노출한다.
//      fun increment() { repository.incrementCounter(); _count.value = repository.getCounter().count }
//  더 나아가면 repository 가 `Flow<Int>` 를 내보내고 ViewModel 이 구독하는 형태가 정석이다.
class CounterModel(var count: Int)

class CounterRepository {
    private var _counter = CounterModel(0)

    fun getCounter() = _counter

    fun incrementCounter() {
        _counter.count++
    }

    fun decrementCounter() {
        _counter.count--
    }
}