package com.example.chapter_205

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.chapter_205.data.Wish

@Database(
    entities = [Wish::class],
    version = 1,
    exportSchema = false
)
abstract class WishDatabase : RoomDatabase() {
    abstract fun wishDao(): WishDao
}