package com.sipdial.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sipdial.app.data.models.CallRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CallHistoryDao {

    @Query("SELECT * FROM call_history ORDER BY timestamp DESC")
    fun getAllCallRecords(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCallRecords(limit: Int = 200): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_history WHERE recordingPath != '' ORDER BY timestamp DESC")
    fun getRecordedCalls(): Flow<List<CallRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(record: CallRecord)

    @Delete
    suspend fun deleteCallRecord(record: CallRecord)

    @Query("UPDATE call_history SET recordingPath = '' WHERE id = :id")
    suspend fun clearRecordingPath(id: Long)

    @Query("SELECT * FROM call_history ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<CallRecord>

    @Query("SELECT COUNT(*) FROM call_history WHERE timestamp = :timestamp AND remoteAddress = :address")
    suspend fun countSame(timestamp: Long, address: String): Int

    @Query("DELETE FROM call_history")
    suspend fun deleteAllCallRecords()
}
