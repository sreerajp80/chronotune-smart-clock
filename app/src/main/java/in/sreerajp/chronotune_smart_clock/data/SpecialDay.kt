package `in`.sreerajp.chronotune_smart_clock.data

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

/**
 * A single calendar day the user has marked as meaningful for alarm scheduling.
 *
 * One shared list serves every alarm — an alarm does not own its holidays, it only chooses
 * (via [Alarm.holidayMode]) whether to pay attention to them.
 *
 * [epochDay] is the primary key, using the same UTC-midnight basis as
 * [Alarm.localCalendarToEpochDay], so marking the same date twice replaces the earlier row
 * instead of duplicating it.
 */
@Entity(tableName = "special_days")
data class SpecialDay(
    @PrimaryKey val epochDay: Long,
    val name: String = "",
    // KIND_HOLIDAY | KIND_WORKING_DAY
    val kind: String = KIND_HOLIDAY,
    // Where the row came from. Only MANUAL is produced today; the column exists so a future
    // "import from device calendar" feature can replace its own rows without touching the
    // days the user typed in by hand.
    val source: String = SOURCE_MANUAL
) {
    /**
     * The day as a local Calendar at midnight, for display/formatting. The stored value is
     * UTC-midnight based, so we read the date fields back in UTC and re-apply them locally —
     * that way the shown date always matches the date the user picked, in any timezone.
     */
    fun toCalendar(): Calendar {
        val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = epochDay * Alarm.MILLIS_PER_DAY
        }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }
    }

    companion object {
        const val KIND_HOLIDAY = "HOLIDAY"
        const val KIND_WORKING_DAY = "WORKING_DAY"
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_CALENDAR = "CALENDAR"
    }
}

/**
 * In-memory view of the [SpecialDay] table, used by the alarm scheduling path.
 *
 * Scheduling runs inside broadcast receivers (boot, timezone change, alarm re-arm) where a
 * blocking Room read is not safe, and the re-arm path rebuilds an alarm from intent extras —
 * a whole day list cannot ride in an Intent. So the map is loaded once and read synchronously.
 *
 * If it has not loaded yet the map is empty, which means "no holidays known" and degrades to
 * the historical behaviour: an alarm may ring on a holiday, but is never silently lost.
 */
object SpecialDayRegistry {
    @Volatile
    private var kinds: Map<Long, String> = emptyMap()

    /** The kind for a day, or null when the day is not marked. */
    fun kindOf(epochDay: Long): String? = kinds[epochDay]

    /** True once the map has been populated at least once (used only for logging/diagnostics). */
    @Volatile
    var isLoaded: Boolean = false
        private set

    /** Replaces the map from an already-loaded list (used by the ViewModel's flow collector). */
    fun set(days: List<SpecialDay>) {
        kinds = days.associate { it.epochDay to it.kind }
        isLoaded = true
    }

    /** Reloads the map straight from the database. Safe to call from a coroutine. */
    suspend fun refresh(context: Context) {
        val dao = AppDatabase.getDatabase(context.applicationContext).specialDayDao()
        set(dao.getAllOnce())
    }
}
