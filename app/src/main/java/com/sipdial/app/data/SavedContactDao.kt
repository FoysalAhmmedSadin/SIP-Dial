package com.sipdial.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sipdial.app.data.models.SavedContact
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedContactDao {

    @Query("SELECT * FROM saved_contacts ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<SavedContact>>

    @Query("SELECT * FROM saved_contacts ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<SavedContact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: SavedContact): Long

    @Delete
    suspend fun delete(contact: SavedContact)

    @Query("SELECT COUNT(*) FROM saved_contacts WHERE category = :category AND number = :number")
    suspend fun countSame(category: String, number: String): Int
}
