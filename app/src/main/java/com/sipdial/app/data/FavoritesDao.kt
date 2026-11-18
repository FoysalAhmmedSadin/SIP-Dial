package com.sipdial.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sipdial.app.data.models.FavoriteContact
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    @Query("SELECT * FROM favorite_contacts ORDER BY sortOrder ASC, addedAt DESC")
    fun getAll(): Flow<List<FavoriteContact>>

    @Query("SELECT * FROM favorite_contacts ORDER BY sortOrder ASC, addedAt DESC")
    suspend fun getAllOnce(): List<FavoriteContact>

    @Query("SELECT COUNT(*) FROM favorite_contacts WHERE normalizedNumber = :normalized")
    suspend fun countByNormalized(normalized: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteContact): Long

    @Delete
    suspend fun delete(favorite: FavoriteContact)

    @Query("DELETE FROM favorite_contacts WHERE normalizedNumber = :normalized")
    suspend fun deleteByNormalized(normalized: String)

    @Query("UPDATE favorite_contacts SET preferredRoute = :route WHERE id = :id")
    suspend fun updatePreferredRoute(id: Long, route: String)

    @Query("DELETE FROM favorite_contacts")
    suspend fun deleteAll()
}
