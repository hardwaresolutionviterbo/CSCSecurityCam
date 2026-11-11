package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DeviceEntity::class, RecordingEntity::class, SettingsEntity::class, UserEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao
    abstract fun recordingDao(): RecordingDao
    abstract fun settingsDao(): SettingsDao
    abstract fun userDao(): UserDao

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

        /** Aggiunge account e proprietario delle telecamere ai database già installati. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `devices` ADD COLUMN `ownerId` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users` (
                        `id` TEXT NOT NULL,
                        `fullName` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `passwordHash` TEXT NOT NULL,
                        `passwordSalt` TEXT NOT NULL,
                        `passwordIterations` INTEGER NOT NULL,
                        `provider` TEXT NOT NULL,
                        `photoPath` TEXT NOT NULL,
                        `plan` TEXT NOT NULL,
                        `planExpiresAtMillis` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
