package `in`.sreerajp.chronotune_smart_clock

import `in`.sreerajp.chronotune_smart_clock.data.AlarmIds
import `in`.sreerajp.chronotune_smart_clock.data.TimerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the AlarmManager id map.
 *
 * Two ids that collide share a PendingIntent request code and a notification id, so arming one
 * silently cancels the other — which the user experiences as an alarm that simply never rang.
 * These tests pin down the spacing that keeps that from happening.
 */
class AlarmIdsTest {

    /** Realistic alarm / timer row ids: Room starts at 1 and this app will never see huge ones. */
    private val rowIds = listOf(1, 2, 5, 17, 99, 500, 1_000, 9_999)

    @Test
    fun `snooze ring stays in its own space no matter how long the chain is`() {
        rowIds.forEach { alarmId ->
            var ringId = alarmId
            // Ten snoozes in a row: each one derives from the ORIGINAL alarm, so the id must
            // not climb. Before the fix each snooze added 50000 to the previous ring, and the
            // fourth snooze of alarm N landed exactly on timer N's ring id.
            repeat(10) {
                ringId = AlarmIds.snoozeRing(AlarmIds.baseAlarmId(ringId))
                assertEquals(
                    "snooze chain must reuse one slot",
                    alarmId + AlarmIds.SNOOZE_RING_OFFSET,
                    ringId
                )
                assertTrue(ringId < AlarmIds.SNOOZE_ACTION_OFFSET)
            }
        }
    }

    @Test
    fun `a snooze ring never collides with a timer ring`() {
        val timerRings = rowIds.map { AlarmIds.timerRing(it) }.toSet()
        rowIds.forEach { alarmId ->
            val snooze = AlarmIds.snoozeRing(alarmId)
            assertFalse("snooze $snooze collides with a timer ring", snooze in timerRings)
        }
    }

    @Test
    fun `every id space stays clear of every other for realistic row ids`() {
        val seen = mutableMapOf<Int, String>()
        rowIds.forEach { id ->
            val ring = AlarmIds.timerRing(id)
            val candidates = listOf(
                id to "alarm",
                AlarmIds.musicRing(id) to "music",
                ring to "timerRing",
                AlarmIds.snoozeRing(id) to "snoozeRing",
                AlarmIds.snoozeAction(id) to "snoozeAction",
                AlarmIds.addMinuteAction(ring) to "addMinute",
                AlarmIds.dismissAction(ring) to "dismiss"
            )
            candidates.forEach { (value, name) ->
                val clash = seen.put(value, name)
                assertEquals("$name id $value already used by $clash", null, clash)
            }
        }
        assertFalse(
            "the watchdog code must not be reachable by any row id",
            seen.containsKey(AlarmIds.WATCHDOG_REQUEST_CODE)
        )
    }

    @Test
    fun `base alarm id maps a snooze ring back to its alarm`() {
        rowIds.forEach { alarmId ->
            assertEquals(alarmId, AlarmIds.baseAlarmId(AlarmIds.snoozeRing(alarmId)))
            assertTrue(AlarmIds.isSnoozeRing(AlarmIds.snoozeRing(alarmId)))
            // A plain scheduled firing is its own base and is not a snooze.
            assertEquals(alarmId, AlarmIds.baseAlarmId(alarmId))
            assertFalse(AlarmIds.isSnoozeRing(alarmId))
            // A timer ring must not be mistaken for a snooze.
            assertFalse(AlarmIds.isSnoozeRing(AlarmIds.timerRing(alarmId)))
        }
    }

    @Test
    fun `timer ring offset keeps its historical value`() {
        // Pending intents armed by older builds still use this number; changing it would
        // orphan every timer already waiting on a device that upgrades.
        assertEquals(200_000, AlarmIds.TIMER_RING_OFFSET)
        assertEquals(TimerItem.RING_ID_OFFSET, AlarmIds.TIMER_RING_OFFSET)
        assertNotEquals(AlarmIds.TIMER_RING_OFFSET, AlarmIds.SNOOZE_RING_OFFSET)
    }
}
