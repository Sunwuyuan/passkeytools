package passkeytools.wuyuan.dev.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class LogType { CREATE, GET, CLEAR, ERROR }

@Serializable
@Entity(tableName = "request_logs")
data class RequestLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = LogType.CREATE.name,
    val rpId: String = "",
    val sourcePackage: String = "",
    val requestJson: String = "",
    val responseStatus: String = "OK",
    val errorMessage: String = "",
    // Resolved credential ID if applicable (Base64URL)
    val credentialId: String = "",
)

