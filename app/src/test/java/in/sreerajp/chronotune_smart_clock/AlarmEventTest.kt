package `in`.sreerajp.chronotune_smart_clock

import `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent
import `in`.sreerajp.chronotune_smart_clock.data.findMissedArmRecords
import `in`.sreerajp.chronotune_smart_clock.data.formatShortDuration
import `in`.sreerajp.chronotune_smart_clock.data.looksHalfAsleep
import `in`.sreerajp.chronotune_smart_clock.data.summaryLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the missed-alarm rule and the one-line summaries.
 *
 * The missed rule matters more than it looks: telling someone their alarm failed when it did
 * not would make the whole history worthless, so every case that must NOT be reported has its
 * own test here.
 */
class AlarmEventTest {

    private val due = 1_700_000_000_000L   // some fixed "6 am"
    private val armedAt = due - 8 * 60 * 60 * 1000   // armed the evening before

    private fun armed(alarmId: Int = 1, id: Long = 1, at: Long = armedAt, dueAt: Long = due) =
        AlarmEvent(
            id = id,
            alarmId = alarmId,
            label = "Wake up",
            event = AlarmEvent.SCHEDULED,
            scheduledAt = dueAt,
            actualAt = at
        )

    private fun other(event: String, at: Long, alarmId: Int = 1, dueAt: Long = 0L) =
        AlarmEvent(
            id = 99,
            alarmId = alarmId,
            label = "Wake up",
            event = event,
            scheduledAt = dueAt,
            actualAt = at
        )

    @Test
    fun `an arm record with no ring at all is missed`() {
        val missed = findMissedArmRecords(listOf(armed()), emptyList())
        assertEquals(1, missed.size)
        assertEquals(due, missed.first().scheduledAt)
    }

    @Test
    fun `an alarm that rang on time is not missed`() {
        val missed = findMissedArmRecords(
            listOf(armed()),
            listOf(other(AlarmEvent.FIRED, due + 2_000L))
        )
        assertTrue(missed.isEmpty())
    }

    @Test
    fun `a ring a few minutes late still counts as this firing`() {
        val missed = findMissedArmRecords(
            listOf(armed()),
            listOf(other(AlarmEvent.FIRED, due + 4 * 60 * 1000L))
        )
        assertTrue("a late ring is still a ring", missed.isEmpty())
    }

    @Test
    fun `a ring far outside the window does not count`() {
        val missed = findMissedArmRecords(
            listOf(armed()),
            listOf(other(AlarmEvent.FIRED, due + 3 * 60 * 60 * 1000L))
        )
        assertEquals(1, missed.size)
    }

    @Test
    fun `a deliberately suppressed alarm is not missed`() {
        listOf(
            AlarmEvent.SUPPRESSED_HOLIDAY,
            AlarmEvent.SUPPRESSED_PAUSE,
            AlarmEvent.SUPPRESSED_SKIP
        ).forEach { suppression ->
            val missed = findMissedArmRecords(
                listOf(armed()),
                listOf(other(suppression, due))
            )
            assertTrue("$suppression must not be reported as a failure", missed.isEmpty())
        }
    }

    @Test
    fun `an alarm switched off before it was due is not missed`() {
        val missed = findMissedArmRecords(
            listOf(armed()),
            listOf(other(AlarmEvent.CANCELLED, due - 60 * 60 * 1000L))
        )
        assertTrue(missed.isEmpty())
    }

    @Test
    fun `an alarm re-armed after this record is not missed`() {
        // Editing an alarm replaces its pending firing and writes a fresh arm record. The old
        // record describes a firing that no longer existed, so it must not be reported.
        val first = armed(id = 1, at = armedAt)
        val edited = armed(id = 2, at = armedAt + 60 * 60 * 1000L)
        val missed = findMissedArmRecords(listOf(first, edited), emptyList())
        assertEquals(1, missed.size)
        assertEquals(2L, missed.first().id)
    }

    @Test
    fun `a miss is only reported once`() {
        val missed = findMissedArmRecords(
            listOf(armed()),
            listOf(other(AlarmEvent.MISSED, due + 60_000L, dueAt = due))
        )
        assertTrue(missed.isEmpty())
    }

    @Test
    fun `one alarm's ring does not cover another alarm's miss`() {
        val missed = findMissedArmRecords(
            listOf(armed(alarmId = 1, id = 1), armed(alarmId = 2, id = 2)),
            listOf(other(AlarmEvent.FIRED, due, alarmId = 1))
        )
        assertEquals(1, missed.size)
        assertEquals(2, missed.first().alarmId)
    }

    @Test
    fun `half-asleep flag needs a fast dismiss on a locked phone`() {
        val fastOnLockScreen = AlarmEvent(
            alarmId = 1,
            event = AlarmEvent.DISMISSED,
            ringDurationMs = 4_000L,
            deviceLocked = true
        )
        assertTrue(fastOnLockScreen.looksHalfAsleep())

        // Took a while to turn off: the user was awake.
        assertFalse(fastOnLockScreen.copy(ringDurationMs = 90_000L).looksHalfAsleep())
        // Phone was already unlocked, so the user was up and using it.
        assertFalse(fastOnLockScreen.copy(deviceLocked = false).looksHalfAsleep())
        // Not a dismiss at all.
        assertFalse(fastOnLockScreen.copy(event = AlarmEvent.SNOOZED).looksHalfAsleep())
    }

    @Test
    fun `summary lines describe each event in plain words`() {
        assertEquals("Rang", other(AlarmEvent.FIRED, due, dueAt = due).summaryLine())

        val late = AlarmEvent(
            alarmId = 1,
            event = AlarmEvent.FIRED,
            scheduledAt = due,
            actualAt = due + 5 * 60 * 1000L
        )
        assertTrue(late.summaryLine().contains("late"))

        val snoozed = AlarmEvent(
            alarmId = 1,
            event = AlarmEvent.SNOOZED,
            snoozeIndex = 2,
            snoozeLimit = 3,
            snoozeGapMinutes = 5,
            ringDurationMs = 12_000L
        )
        val snoozeLine = snoozed.summaryLine()
        assertTrue(snoozeLine.contains("2 of 3"))
        assertTrue(snoozeLine.contains("5 min"))
        assertTrue(snoozeLine.contains("12 s"))

        val dismissed = AlarmEvent(
            alarmId = 1,
            event = AlarmEvent.DISMISSED,
            ringDurationMs = 12_000L,
            dismissSource = AlarmEvent.SOURCE_FULL_SCREEN,
            challengeType = "MATH",
            challengeAttempts = 1,
            challengeSolvedMs = 4_000L
        )
        val dismissLine = dismissed.summaryLine()
        assertTrue(dismissLine.contains("on the alarm screen"))
        assertTrue(dismissLine.contains("after 12 s"))
        assertTrue(dismissLine.contains("1 try"))
    }

    @Test
    fun `durations read the way a person would say them`() {
        assertEquals("0 s", formatShortDuration(400L))
        assertEquals("12 s", formatShortDuration(12_400L))
        assertEquals("2 m", formatShortDuration(120_000L))
        assertEquals("2 m 30 s", formatShortDuration(150_000L))
        assertEquals("1 h 5 m", formatShortDuration(3_900_000L))
    }
}
