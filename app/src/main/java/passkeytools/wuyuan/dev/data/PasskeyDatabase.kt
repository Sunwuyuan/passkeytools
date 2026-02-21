package passkeytools.wuyuan.dev.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import passkeytools.wuyuan.dev.model.PasskeyEntity
import passkeytools.wuyuan.dev.model.RequestLogEntry

@Database(
    entities = [PasskeyEntity::class, RequestLogEntry::class],
    version = 1,
    exportSchema = false
)
abstract class PasskeyDatabase : RoomDatabase() {
    abstract fun passkeyDao(): PasskeyDao
    abstract fun requestLogDao(): RequestLogDao

    companion object {
        @Volatile private var INSTANCE: PasskeyDatabase? = null

        fun getInstance(context: Context): PasskeyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PasskeyDatabase::class.java,
                    "passkey_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

