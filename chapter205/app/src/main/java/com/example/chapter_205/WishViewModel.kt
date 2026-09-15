package com.example.chapter_205

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chapter_205.data.Wish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class WishViewModel(
    // TODO: [todos/android-manual-di-graph-object.md](../../../../../../../../todos/android-manual-di-graph-object.md)
    private val wishRepository: WishRepository = Graph.wishRepository
) : ViewModel() {
    var wishTitleState = mutableStateOf("")
    var wishDescriptionState = mutableStateOf("")

    fun onWishTitleChange(newValue: String) {
        wishTitleState.value = newValue
    }

    fun onWishDescriptionState(newValue: String) {
        wishDescriptionState.value = newValue
    }

    lateinit var getAllWishes: Flow<List<Wish>>

    init {
        // TODO: [todos/android-viewmodelscope-launch-necessity.md](../../../../../../../../todos/android-viewmodelscope-launch-necessity.md)
        viewModelScope.launch {
            getAllWishes = wishRepository.getAllWishes()
        }
    }

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