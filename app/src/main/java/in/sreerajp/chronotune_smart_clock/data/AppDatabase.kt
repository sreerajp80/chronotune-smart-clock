package `in`.sreerajp.chronotune_smart_clock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Alarm::class, WorldClock::class, MusicSchedule::class, TimerItem::class,
        TimerPreset::class, SpecialDay::class, AlarmEvent::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun alarmEventDao(): AlarmEventDao
    abstract fun worldClockDao(): WorldClockDao
    abstract fun musicScheduleDao(): MusicScheduleDao
    abstract fun timerDao(): TimerDao
    abstract fun timerPresetDao(): TimerPresetDao
    abstract fun specialDayDao(): SpecialDayDao

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

        // v3 -> v4: add the per-alarm dismiss-challenge columns (Math / Phrase / Memory
        // wake-up tasks). Defaults keep every existing alarm on plain tap-to-dismiss.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN dismissChallenge TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE alarms ADD COLUMN challengeDifficulty TEXT NOT NULL DEFAULT 'EASY'")
                db.execSQL("ALTER TABLE alarms ADD COLUMN challengeCount INTEGER NOT NULL DEFAULT 1")
            }
        }

        // v4 -> v5: add the per-alarm auto-silence column. Default 0 = "Never" (ring until
        // dismissed), so every existing alarm keeps today's behavior after upgrade.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN autoSilenceMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v5 -> v6: add the per-alarm skip-next-occurrence column. Default 0 = "not skipping",
        // so every existing alarm keeps today's behavior after upgrade.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN skipNextEpochDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v6 -> v7: holiday / work-day awareness. Adds the shared `special_days` table and the
        // per-alarm holidayMode column. Default 'ALL_DAYS' means every existing alarm ignores
        // the day list, so behavior after upgrade is unchanged until the user opts in.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `special_days` (
                        `epochDay` INTEGER NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL DEFAULT '',
                        `kind` TEXT NOT NULL DEFAULT 'HOLIDAY',
                        `source` TEXT NOT NULL DEFAULT 'MANUAL'
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE alarms ADD COLUMN holidayMode TEXT NOT NULL DEFAULT 'ALL_DAYS'")
            }
        }

        // v7 -> v8: snooze limit + progressive snooze. Defaults (0 = unlimited, FIXED gap)
        // keep every existing alarm snoozing exactly as it does today.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN maxSnoozeCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarms ADD COLUMN snoozeMode TEXT NOT NULL DEFAULT 'FIXED'")
            }
        }

        // v8 -> v9: per-alarm start date (and future-dated one-time alarms, which are the same
        // column read differently). Default 0 = no start date, so existing alarms are unchanged.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN startEpochDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v9 -> v10: the alarm event log. Purely additive — a new table and its two indexes,
        // with no existing table touched, so an upgrade cannot lose anything.
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Copied verbatim from the CREATE TABLE Room generates for AlarmEvent, so an
                // upgraded database is byte-for-byte what a fresh install produces. Room checks
                // this on open and refuses to start if the two differ.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alarm_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alarmId` INTEGER NOT NULL, `type` TEXT NOT NULL, `label` TEXT NOT NULL, `event` TEXT NOT NULL, `scheduledAt` INTEGER NOT NULL, `actualAt` INTEGER NOT NULL, `ringDurationMs` INTEGER NOT NULL, `dismissSource` TEXT NOT NULL, `challengeType` TEXT NOT NULL, `challengeDifficulty` TEXT NOT NULL, `challengeRounds` INTEGER NOT NULL, `challengeAttempts` INTEGER NOT NULL, `challengeSolvedMs` INTEGER NOT NULL, `snoozeIndex` INTEGER NOT NULL, `snoozeGapMinutes` INTEGER NOT NULL, `snoozeMode` TEXT NOT NULL, `snoozeLimit` INTEGER NOT NULL, `nextRingAt` INTEGER NOT NULL, `screenOn` INTEGER NOT NULL, `deviceLocked` INTEGER NOT NULL, `dozeIdle` INTEGER NOT NULL, `exactAllowed` INTEGER NOT NULL, `detail` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_alarm_events_alarmId` ON `alarm_events` (`alarmId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_alarm_events_actualAt` ON `alarm_events` (`actualAt`)")
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
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                    MIGRATION_9_10
                )
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
