package passkeytools.wuyuan.dev.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import passkeytools.wuyuan.dev.model.PasskeyEntity

@Dao
interface PasskeyDao {
    @Query("SELECT * FROM passkeys ORDER BY lastUsedAt DESC")
    fun getAll(): Flow<List<PasskeyEntity>>

    @Query("SELECT * FROM passkeys WHERE rpId = :rpId ORDER BY lastUsedAt DESC")
    fun getByRpId(rpId: String): Flow<List<PasskeyEntity>>

    @Query("SELECT * FROM passkeys WHERE credentialId = :credentialId LIMIT 1")
    suspend fun getByCredentialId(credentialId: String): PasskeyEntity?

    @Query("SELECT * FROM passkeys WHERE rpId = :rpId ORDER BY lastUsedAt DESC")
    suspend fun getByRpIdSync(rpId: String): List<PasskeyEntity>

    @Query("SELECT * FROM passkeys WHERE credentialId IN (:credentialIds)")
    suspend fun getByCredentialIds(credentialIds: List<String>): List<PasskeyEntity>

    @Query("SELECT * FROM passkeys ORDER BY lastUsedAt DESC")
    suspend fun getAllSync(): List<PasskeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(passkey: PasskeyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(passkeys: List<PasskeyEntity>)

    @Update
    suspend fun update(passkey: PasskeyEntity)

    @Delete
    suspend fun delete(passkey: PasskeyEntity)

    @Query("DELETE FROM passkeys WHERE credentialId = :credentialId")
    suspend fun deleteById(credentialId: String)

    @Query("DELETE FROM passkeys")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM passkeys")
    fun getCount(): Flow<Int>
}

