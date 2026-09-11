package com.example.chapter_127

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

// TODO: [todos/android-viewmodel-role-and-remember.md](../../../../../../../../todos/android-viewmodel-role-and-remember.md)
class CounterViewModel(private val repository: CounterRepository) : ViewModel() {
    private val _count = mutableStateOf(repository.getCounter().count)

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