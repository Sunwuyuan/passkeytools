package passkeytools.wuyuan.dev.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import passkeytools.wuyuan.dev.model.RequestLogEntry

@Dao
interface RequestLogDao {
    @Query("SELECT * FROM request_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<RequestLogEntry>>

    @Query("SELECT * FROM request_logs WHERE type = :type ORDER BY timestamp DESC")
    fun getByType(type: String): Flow<List<RequestLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: RequestLogEntry): Long

    @Query("DELETE FROM request_logs")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM request_logs")
    fun getCount(): Flow<Int>
}

