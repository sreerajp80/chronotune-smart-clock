package `in`.sreerajp.chronotune_smart_clock

import `in`.sreerajp.chronotune_smart_clock.ui.VoiceCommand
import `in`.sreerajp.chronotune_smart_clock.ui.VoiceCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Covers the spoken phrases the app promises to understand, in English and Malayalam.
 *
 * Every test pins "now" so the ambiguous-hour rule ("set an alarm for 7" means whichever
 * 7 comes first) is deterministic.
 */
class VoiceCommandParserTest {

    private fun at(hour: Int, minute: Int = 0): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JULY)
            set(Calendar.DAY_OF_MONTH, 19)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

    private fun alarm(text: String, now: Calendar = at(9)): VoiceCommand.SetAlarm {
        val result = VoiceCommandParser.parse(text, now)
        assertTrue("expected an alarm from \"$text\" but got $result", result is VoiceCommand.SetAlarm)
        return result as VoiceCommand.SetAlarm
    }

    private fun timer(text: String, now: Calendar = at(9)): VoiceCommand.SetTimer {
        val result = VoiceCommandParser.parse(text, now)
        assertTrue("expected a timer from \"$text\" but got $result", result is VoiceCommand.SetTimer)
        return result as VoiceCommand.SetTimer
    }

    // ------------------------------------------------------------------ English alarms

    @Test
    fun `bare hour picks the soonest matching time`() {
        // At 9 am, "7" means 7 pm — 7 am has already gone by.
        assertEquals(19, alarm("set an alarm for 7", at(9)).hour)
        // At 10 pm, the same words mean 7 am.
        assertEquals(7, alarm("set an alarm for 7", at(22)).hour)
    }

    @Test
    fun `explicit am and pm are obeyed`() {
        alarm("set an alarm for 7 am").let {
            assertEquals(7, it.hour); assertEquals(0, it.minute)
        }
        assertEquals(19, alarm("set an alarm for 7 pm").hour)
        assertEquals(6, alarm("wake me up at 6:30 am").hour)
        assertEquals(30, alarm("wake me up at 6:30 am").minute)
    }

    @Test
    fun `night hours are read the way people mean them`() {
        assertEquals(21, alarm("set an alarm for 9 at night").hour)
        // Small hours "at night" are after midnight, not in the evening.
        assertEquals(2, alarm("set an alarm for 2 at night").hour)
    }

    @Test
    fun `spelled out and past forms`() {
        assertEquals(0, alarm("set an alarm for seven o'clock").minute)
        alarm("set an alarm for quarter past six am").let {
            assertEquals(6, it.hour); assertEquals(15, it.minute)
        }
        alarm("set an alarm for half past five am").let {
            assertEquals(5, it.hour); assertEquals(30, it.minute)
        }
        alarm("set an alarm for quarter to seven am").let {
            assertEquals(6, it.hour); assertEquals(45, it.minute)
        }
    }

    @Test
    fun `24 hour times pass through untouched`() {
        alarm("set an alarm for 19:45").let {
            assertEquals(19, it.hour); assertEquals(45, it.minute)
        }
    }

    @Test
    fun `repeat days are picked up`() {
        assertEquals(listOf(1, 2, 3, 4, 5), alarm("set an alarm for 7 am on weekdays").days)
        assertEquals(listOf(6, 7), alarm("set an alarm for 9 am on weekends").days)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), alarm("set an alarm for 7 am every day").days)
        assertEquals(listOf(1, 5), alarm("set an alarm for 7 am on monday and friday").days)
        assertEquals(emptyList<Int>(), alarm("set an alarm for 7 am").days)
    }

    @Test
    fun `label is taken from an explicit name`() {
        assertEquals("gym", alarm("set an alarm for 6 am called gym").label)
        assertEquals("", alarm("set an alarm for 6 am").label)
    }

    // ------------------------------------------------------------------ English timers

    @Test
    fun `timer durations add up`() {
        assertEquals(10 * 60_000L, timer("set a timer for 10 minutes").durationMs)
        assertEquals(90 * 60_000L, timer("set a timer for 1 hour 30 minutes").durationMs)
        assertEquals(45_000L, timer("timer for 45 seconds").durationMs)
        assertEquals(30 * 60_000L, timer("set a timer for half an hour").durationMs)
        assertEquals(5 * 60_000L, timer("set a timer for five minutes").durationMs)
    }

    @Test
    fun `bare timer number means minutes`() {
        assertEquals(10 * 60_000L, timer("timer for 10").durationMs)
    }

    @Test
    fun `a relative delay is a timer not an alarm`() {
        assertEquals(20 * 60_000L, timer("wake me in 20 minutes").durationMs)
    }

    // ------------------------------------------------------------------ control words

    @Test
    fun `stop and snooze are understood`() {
        assertEquals(VoiceCommand.DismissAlarm, VoiceCommandParser.parse("stop the alarm", at(7)))
        assertEquals(VoiceCommand.DismissAlarm, VoiceCommandParser.parse("turn off the alarm", at(7)))
        assertEquals(VoiceCommand.SnoozeAlarm(null), VoiceCommandParser.parse("snooze", at(7)))
        assertEquals(
            VoiceCommand.SnoozeAlarm(10),
            VoiceCommandParser.parse("snooze for 10 minutes", at(7))
        )
    }

    @Test
    fun `show alarms without a time`() {
        assertEquals(VoiceCommand.ShowAlarms, VoiceCommandParser.parse("show my alarms", at(9)))
    }

    // ------------------------------------------------------------------ Malayalam

    @Test
    fun `malayalam morning alarm`() {
        // "Set an alarm for 7 in the morning"
        alarm("രാവിലെ 7 മണിക്ക് അലാറം വെക്കൂ").let {
            assertEquals(7, it.hour); assertEquals(0, it.minute)
        }
    }

    @Test
    fun `malayalam evening alarm goes to pm`() {
        assertEquals(18, alarm("വൈകിട്ട് 6 മണിക്ക് അലാറം വെക്കൂ").hour)
    }

    @Test
    fun `malayalam number words`() {
        // "Set an alarm for seven in the morning" using the word ഏഴ്.
        assertEquals(7, alarm("രാവിലെ ഏഴു മണിക്ക് അലാറം വെക്കൂ").hour)
        assertEquals(9, alarm("രാവിലെ ഒമ്പതു മണിക്ക് അലാറം വെക്കൂ").hour)
    }

    @Test
    fun `malayalam numerals are converted`() {
        // ൭ is the Malayalam digit seven.
        assertEquals(7, alarm("രാവിലെ ൭ മണിക്ക് അലാറം വെക്കൂ").hour)
    }

    @Test
    fun `malayalam half past form`() {
        // ഏഴര = half past seven.
        alarm("രാവിലെ ഏഴരയ്ക്ക് അലാറം വെക്കൂ").let {
            assertEquals(7, it.hour); assertEquals(30, it.minute)
        }
    }

    @Test
    fun `malayalam timer`() {
        assertEquals(10 * 60_000L, timer("10 മിനിറ്റ് ടൈമർ വെക്കൂ").durationMs)
        assertEquals(60 * 60_000L, timer("1 മണിക്കൂർ ടൈമർ വെക്കൂ").durationMs)
    }

    @Test
    fun `malayalam repeat days`() {
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7),
            alarm("ദിവസവും രാവിലെ 6 മണിക്ക് അലാറം വെക്കൂ").days
        )
        assertEquals(listOf(1), alarm("തിങ്കൾ രാവിലെ 6 മണിക്ക് അലാറം വെക്കൂ").days)
    }

    @Test
    fun `malayalam stop`() {
        assertEquals(
            VoiceCommand.DismissAlarm,
            VoiceCommandParser.parse("അലാറം നിർത്തൂ", at(7))
        )
    }

    // ------------------------------------------------------------------ nonsense

    @Test
    fun `unrelated speech is reported back unchanged`() {
        val result = VoiceCommandParser.parse("what is the weather like", at(9))
        assertTrue(result is VoiceCommand.Unknown)
        assertEquals("what is the weather like", (result as VoiceCommand.Unknown).text)
    }
}
