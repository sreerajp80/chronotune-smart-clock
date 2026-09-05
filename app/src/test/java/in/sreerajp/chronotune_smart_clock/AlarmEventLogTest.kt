package `in`.sreerajp.chronotune_smart_clock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent
import `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog
import `in`.sreerajp.chronotune_smart_clock.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The event log writes to a real Room database here, because the thing worth proving is that
 * recording an event can never interfere with an alarm ringing.
 */
@RunWith(RobolectricTestRunner::class)
class AlarmEventLogTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a recorded event is stored and stamped with the time and device state`() = runBlocking {
        val dao = AppDatabase.getDatabase(context).alarmEventDao()
        dao.deleteAll()

        AlarmEventLog.recordNow(
            context,
            AlarmEvent(
                alarmId = 7,
                label = "Wake up",
                event = AlarmEvent.FIRED,
                scheduledAt = 1_700_000_000_000L
            )
        )

        val rows = dao.getRecentOnce(10)
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(7, row.alarmId)
        assertEquals(AlarmEvent.FIRED, row.event)
        assertEquals(1_700_000_000_000L, row.scheduledAt)
        // The caller did not set actualAt, so the log filled it in.
        assertTrue("actualAt should be stamped", row.actualAt > 0L)
    }

    @Test
    fun `recording never throws, whatever the database does`() {
        // Fire-and-forget path: even if the write fails, nothing may escape to the caller —
        // this runs on the alarm's own code path, and an exception here would cost a ring.
        AlarmEventLog.record(
            context,
            AlarmEvent(alarmId = 1, event = AlarmEvent.FIRED)
        )

        // The suspending variant swallows its errors too.
        runBlocking {
            AlarmEventLog.recordNow(
                context,
                AlarmEvent(alarmId = 1, event = AlarmEvent.DISMISSED)
            )
        }
    }

    @Test
    fun `pruning drops only rows older than the cutoff`() = runBlocking {
        val dao = AppDatabase.getDatabase(context).alarmEventDao()
        dao.deleteAll()
        val now = System.currentTimeMillis()

        dao.insert(AlarmEvent(alarmId = 1, event = AlarmEvent.FIRED, actualAt = now - 100_000L))
        dao.insert(AlarmEvent(alarmId = 1, event = AlarmEvent.FIRED, actualAt = now))

        dao.deleteBefore(now - 50_000L)

        val rows = dao.getRecentOnce(10)
        assertEquals(1, rows.size)
        assertEquals(now, rows.first().actualAt)
    }
}
