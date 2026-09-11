package com.example.chapter_127

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