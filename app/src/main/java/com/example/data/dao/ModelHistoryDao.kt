package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.ModelHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelHistoryDao {
    @Query("SELECT * FROM model_history ORDER BY timestamp DESC LIMIT 40")
    fun getRecentHistory(): Flow<List<ModelHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: ModelHistoryEntity)

    @Query("DELETE FROM model_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM model_history")
    suspend fun clearAll()
}
