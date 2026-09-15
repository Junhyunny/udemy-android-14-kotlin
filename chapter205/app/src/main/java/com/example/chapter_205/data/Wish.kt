package com.example.chapter_205.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wish-table")
data class Wish(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "title")
    val title: String = "",
    @ColumnInfo(name = "description")
    val description: String = ""
)

object DummyWish {
    val wishList = listOf(
        Wish(title = "Google watch 2", description = "Android Watch"),
        Wish(title = "Oculus Quest 2", description = "Android Watch"),
        Wish(title = "A Sci-fi 2", description = "Android Watch"),
    )
}