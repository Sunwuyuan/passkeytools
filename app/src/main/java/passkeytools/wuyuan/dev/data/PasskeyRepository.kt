package passkeytools.wuyuan.dev.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import passkeytools.wuyuan.dev.model.PasskeyEntity
import passkeytools.wuyuan.dev.model.RequestLogEntry

class PasskeyRepository(context: Context) {
    private val db = PasskeyDatabase.getInstance(context)
    private val passkeyDao = db.passkeyDao()
    private val logDao = db.requestLogDao()

    // ── Passkeys ──────────────────────────────────────────────────────────────
    fun getAllPasskeys(): Flow<List<PasskeyEntity>> = passkeyDao.getAll()
    fun getPasskeysByRpId(rpId: String): Flow<List<PasskeyEntity>> = passkeyDao.getByRpId(rpId)
    fun getPasskeyCount(): Flow<Int> = passkeyDao.getCount()

    suspend fun getPasskeyByCredentialId(credentialId: String): PasskeyEntity? =
        passkeyDao.getByCredentialId(credentialId)

    suspend fun getPasskeysByRpIdSync(rpId: String): List<PasskeyEntity> =
        passkeyDao.getByRpIdSync(rpId)

    suspend fun getPasskeysByCredentialIds(ids: List<String>): List<PasskeyEntity> =
        passkeyDao.getByCredentialIds(ids)

    suspend fun getAllPasskeysSync(): List<PasskeyEntity> = passkeyDao.getAllSync()

    suspend fun savePasskey(passkey: PasskeyEntity) = passkeyDao.insert(passkey)
    suspend fun savePasskeys(passkeys: List<PasskeyEntity>) = passkeyDao.insertAll(passkeys)
    suspend fun updatePasskey(passkey: PasskeyEntity) = passkeyDao.update(passkey)
    suspend fun deletePasskey(passkey: PasskeyEntity) = passkeyDao.delete(passkey)
    suspend fun deletePasskeyById(credentialId: String) = passkeyDao.deleteById(credentialId)
    suspend fun deleteAllPasskeys() = passkeyDao.deleteAll()

    suspend fun incrementCounter(credentialId: String): PasskeyEntity? {
        val pk = passkeyDao.getByCredentialId(credentialId) ?: return null
        val updated = pk.copy(
            counter = pk.counter + 1,
            lastUsedAt = System.currentTimeMillis()
        )
        passkeyDao.update(updated)
        return updated
    }

    // ── Logs ──────────────────────────────────────────────────────────────────
    fun getAllLogs(): Flow<List<RequestLogEntry>> = logDao.getAll()
    fun getLogsByType(type: String): Flow<List<RequestLogEntry>> = logDao.getByType(type)
    fun getLogCount(): Flow<Int> = logDao.getCount()

    suspend fun insertLog(log: RequestLogEntry): Long = logDao.insert(log)
    suspend fun clearLogs() = logDao.clearAll()
}

