package com.example.chapter_143

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

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
                _categoriesState.value = _categoriesState.value.copy(
                    loading = false,
                    error = "Error fetching categories, ${e.message}"
                )
            }
        }
    }

    data class RecipeState(
        val loading: Boolean,
        val list: List<Category> = emptyList(),
        val error: String? = null
    )
}