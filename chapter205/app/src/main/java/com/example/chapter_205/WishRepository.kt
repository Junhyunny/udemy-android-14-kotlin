package com.example.chapter_205

import com.example.chapter_205.data.Wish
import kotlinx.coroutines.flow.Flow

// TODO: [todos/kotlin-flow-concepts-and-suspend.md](../../../../../../../../todos/kotlin-flow-concepts-and-suspend.md)
class WishRepository(private val wishDao: WishDao) {

    suspend fun addWish(wish: Wish) {
        wishDao.addWish(wish)
    }

    fun getAllWishes(): Flow<List<Wish>> = wishDao.getAll()

    fun getWishById(id: Long): Flow<Wish> {
        return wishDao.getWishById(id)
    }

    suspend fun updateWish(wish: Wish) {
        wishDao.updateWish(wish)
    }

    suspend fun deleteWish(wish: Wish) {
        wishDao.deleteWish(wish)
    }
}