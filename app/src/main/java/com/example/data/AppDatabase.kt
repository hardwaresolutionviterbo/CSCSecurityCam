package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DeviceEntity::class, RecordingEntity::class, SettingsEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao
    abstract fun recordingDao(): RecordingDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        private const val DATABASE_NAME = "csc-securitycam.db"

        @Volatile
        private var instance: AppDatabase? = null

        /** Aggiunge le tabelle delle registrazioni e delle impostazioni ai database già installati. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recordings` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `timestamp` TEXT NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `isMotionEvent` INTEGER NOT NULL,
                        `isSnapshot` INTEGER NOT NULL,
                        `cameraName` TEXT NOT NULL,
                        `uri` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `settings` (
                        `id` INTEGER NOT NULL,
                        `videoQuality` TEXT NOT NULL,
                        `audioMode` TEXT NOT NULL,
                        `motionDetectionEnabled` INTEGER NOT NULL,
                        `motionSensitivity` REAL NOT NULL,
                        `autoRecordingOnlyMotion` INTEGER NOT NULL,
                        `notificationsEnabled` INTEGER NOT NULL,
                        `remoteAccessWebRtc` INTEGER NOT NULL,
                        `nightVisionHardware` INTEGER NOT NULL,
                        `port` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
