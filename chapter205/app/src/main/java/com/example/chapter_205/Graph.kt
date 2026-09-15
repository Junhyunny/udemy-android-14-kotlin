package com.example.chapter_205

import android.content.Context
import androidx.room.Room

object Graph {
    lateinit var database: WishDatabase

    // TODO: [todos/kotlin-by-lazy-delegate.md](../../../../../../../../todos/kotlin-by-lazy-delegate.md)
    val wishRepository by lazy {
        WishRepository(database.wishDao())
    }

    // TODO: [todos/android-context-types-and-application-context.md](../../../../../../../../todos/android-context-types-and-application-context.md)
    fun provide(context: Context) {
        database = Room.databaseBuilder(context, WishDatabase::class.java, "wishlist.db").build()
    }
}