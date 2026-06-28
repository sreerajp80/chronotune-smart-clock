package `in`.sreerajp.chronotune_smart_clock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Alarm::class, WorldClock::class, MusicSchedule::class, TimerItem::class, TimerPreset::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun worldClockDao(): WorldClockDao
    abstract fun musicScheduleDao(): MusicScheduleDao
    abstract fun timerDao(): TimerDao
    abstract fun timerPresetDao(): TimerPresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v1 -> v2: add the alarm pause-window columns. A real migration preserves existing
        // alarms / world-clocks / music-schedules (destructive fallback below is just a backstop).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN pauseStartMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarms ADD COLUMN pauseEndMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v2 -> v3: add the multi-timer + named-preset tables (Persist Stopwatch & Timer /
        // real-alarm timer feature). Seeds a few starter presets so the UI isn't empty.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `timers` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `label` TEXT NOT NULL DEFAULT '',
                        `totalDurationMs` INTEGER NOT NULL,
                        `remainingMs` INTEGER NOT NULL,
                        `endAtElapsed` INTEGER NOT NULL DEFAULT 0,
                        `fireAtWallClock` INTEGER NOT NULL DEFAULT 0,
                        `state` TEXT NOT NULL DEFAULT 'IDLE',
                        `toneName` TEXT NOT NULL DEFAULT 'Cosmic Shimmer',
                        `toneUri` TEXT NOT NULL DEFAULT '',
                        `volume` REAL NOT NULL DEFAULT 0.8,
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `timer_presets` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `label` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `toneName` TEXT NOT NULL DEFAULT 'Cosmic Shimmer',
                        `toneUri` TEXT NOT NULL DEFAULT '',
                        `volume` REAL NOT NULL DEFAULT 0.8,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                seedPresets(db)
            }
        }

        // Shared starter presets used by the migration and the destructive-fallback callback.
        private fun seedPresets(db: SupportSQLiteDatabase) {
            fun insert(label: String, minutes: Long, order: Int) {
                db.execSQL(
                    "INSERT INTO `timer_presets` (`label`, `durationMs`, `toneName`, `toneUri`, `volume`, `sortOrder`) " +
                        "VALUES (?, ?, 'Cosmic Shimmer', '', 0.8, ?)",
                    arrayOf<Any>(label, minutes * 60_000L, order)
                )
            }
            insert("Tea", 3, 0)
            insert("Power Nap", 20, 1)
            insert("Workout", 25, 2)
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clock_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    // When the DB is created fresh (first install or after a destructive
                    // fallback), seed the same starter presets the migration would have added.
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        seedPresets(db)
                    }
                })
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
