package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "model_history")
data class ModelHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meshId: String,
    val meshName: String,
    val category: String,
    val vertexCount: Int,
    val faceCount: Int,
    val displayMode: String,
    val timestamp: Long = System.currentTimeMillis()
)
