package com.example.chapter_205

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.chapter_205.data.Wish
import kotlinx.coroutines.flow.Flow

// TODO: [todos/android-room-dao-code-generation.md](../../../../../../../../todos/android-room-dao-code-generation.md)
@Dao
abstract class WishDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun addWish(wish: Wish)

    // TODO: [todos/kotlin-flow-concepts-and-suspend.md](../../../../../../../../todos/kotlin-flow-concepts-and-suspend.md)
    @Query("select * from `wish-table`")
    abstract fun getAll(): Flow<List<Wish>>

    @Update
    abstract fun updateWish(wish: Wish)

    @Delete
    abstract fun deleteWish(wish: Wish)

    @Query("select * from `wish-table` where id=:id")
    abstract fun getWishById(id: Long): Flow<Wish>
}