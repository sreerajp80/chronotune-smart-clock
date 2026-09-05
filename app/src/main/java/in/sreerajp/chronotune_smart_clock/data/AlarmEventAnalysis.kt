package `in`.sreerajp.chronotune_smart_clock.data

/**
 * Pure reasoning over the alarm event log: working out which alarms never rang, and turning a
 * row into the one line the history screen shows.
 *
 * Kept free of Android types on purpose, so the rules can be tested directly.
 */

/** How far either side of the due time a ring still counts as "this firing". */
const val MISSED_TOLERANCE_MS: Long = 5 * 60_000L

/**
 * Picks out the arm records that never turned into a ring.
 *
 * [armRecords] are [AlarmEvent.SCHEDULED] rows whose [AlarmEvent.scheduledAt] is already in the
 * past; [others] is everything else logged for those alarms. An arm record is only reported as
 * missed when none of these is true:
 *
 * - something rang, or was deliberately suppressed, near the due time,
 * - the alarm was switched off between being armed and falling due,
 * - the alarm was re-armed after this record (an edit replaces the pending firing, so the old
 *   record was superseded rather than missed),
 * - the miss has already been recorded once.
 *
 * Each of those exclusions exists to stop a false alarm: telling the user their alarm failed
 * when it did not would make the whole history useless.
 */
fun findMissedArmRecords(
    armRecords: List<AlarmEvent>,
    others: List<AlarmEvent>,
    toleranceMs: Long = MISSED_TOLERANCE_MS
): List<AlarmEvent> {
    if (armRecords.isEmpty()) return emptyList()
    val byAlarm = others.groupBy { it.alarmId }

    return armRecords.filter { armed ->
        val due = armed.scheduledAt
        val related = byAlarm[armed.alarmId].orEmpty()

        val rang = related.any {
            it.event in AlarmEvent.RANG_OR_HANDLED &&
                it.event != AlarmEvent.CANCELLED &&
                it.event != AlarmEvent.MISSED &&
                kotlin.math.abs(it.actualAt - due) <= toleranceMs
        }
        if (rang) return@filter false

        // Switched off after it was armed but before it was due.
        val cancelled = related.any {
            it.event == AlarmEvent.CANCELLED &&
                it.actualAt > armed.actualAt &&
                it.actualAt <= due + toleranceMs
        }
        if (cancelled) return@filter false

        // Re-armed in the meantime: editing an alarm replaces its pending firing, so this
        // record no longer describes anything that was still due.
        val superseded = armRecords.any {
            it.alarmId == armed.alarmId &&
                it.id != armed.id &&
                it.actualAt > armed.actualAt &&
                it.actualAt <= due + toleranceMs
        }
        if (superseded) return@filter false

        // Already reported.
        val alreadyLogged = related.any {
            it.event == AlarmEvent.MISSED &&
                kotlin.math.abs(it.scheduledAt - due) <= toleranceMs
        }
        !alreadyLogged
    }
}

/**
 * How late the ring was, in milliseconds, or null when the event carries no due time. A large
 * value is the fingerprint of Doze deferring an inexact alarm.
 */
fun AlarmEvent.delayMs(): Long? {
    if (scheduledAt <= 0L || actualAt <= 0L) return null
    return (actualAt - scheduledAt).coerceAtLeast(0L)
}

/** "3 s", "2 m 10 s", "1 h 5 m" — short, for a list row. */
fun formatShortDuration(ms: Long): String {
    if (ms < 1000L) return "0 s"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> if (minutes > 0) "$hours h $minutes m" else "$hours h"
        minutes > 0 -> if (seconds > 0) "$minutes m $seconds s" else "$minutes m"
        else -> "$seconds s"
    }
}

/**
 * The single line the history list shows for an event. Deliberately plain language: this is
 * read at breakfast by someone trying to work out what happened at six in the morning.
 */
fun AlarmEvent.summaryLine(): String = when (event) {
    AlarmEvent.SCHEDULED -> "Set to ring"
    AlarmEvent.CANCELLED -> "Turned off, so it will not ring"
    AlarmEvent.FIRED -> {
        val late = delayMs()
        if (late != null && late >= 60_000L) "Rang ${formatShortDuration(late)} late" else "Rang"
    }
    AlarmEvent.SUPPRESSED_PAUSE -> "Did not ring — alarm is paused"
    AlarmEvent.SUPPRESSED_SKIP -> "Did not ring — this one was skipped"
    AlarmEvent.SUPPRESSED_HOLIDAY -> "Did not ring — holiday"
    AlarmEvent.RING_FAILED -> "Tried to ring but the phone blocked it"
    AlarmEvent.QUEUED -> "Waited for another alarm to finish"
    AlarmEvent.SNOOZED -> buildString {
        append("Snoozed")
        if (snoozeIndex > 0) {
            append(" (")
            append(snoozeIndex)
            if (snoozeLimit > 0) append(" of $snoozeLimit")
            append(")")
        }
        if (snoozeGapMinutes > 0) append(", $snoozeGapMinutes min")
        if (ringDurationMs > 0) append(" — after ${formatShortDuration(ringDurationMs)}")
    }
    AlarmEvent.DISMISSED -> buildString {
        append("Dismissed")
        append(
            when (dismissSource) {
                AlarmEvent.SOURCE_FULL_SCREEN -> " on the alarm screen"
                AlarmEvent.SOURCE_NOTIFICATION -> " from the notification"
                else -> ""
            }
        )
        if (ringDurationMs > 0) append(" after ${formatShortDuration(ringDurationMs)}")
        if (challengeType != "NONE" && challengeAttempts > 0) {
            append(" — $challengeType challenge, $challengeAttempts")
            append(if (challengeAttempts == 1) " try" else " tries")
            if (challengeSolvedMs > 0) append(", ${formatShortDuration(challengeSolvedMs)}")
        }
    }
    AlarmEvent.AUTO_SILENCED -> buildString {
        append("Stopped by itself")
        if (ringDurationMs > 0) append(" after ${formatShortDuration(ringDurationMs)}")
        append(" — nobody turned it off")
    }
    AlarmEvent.MISSED -> buildString {
        append("Did not ring")
        val reasons = mutableListOf<String>()
        if (dozeIdle) reasons.add("phone was in deep sleep")
        if (!exactAllowed) reasons.add("exact alarms were not allowed")
        if (reasons.isNotEmpty()) append(" — ${reasons.joinToString(", ")}")
    }
    AlarmEvent.RESCHEDULED_BOOT -> "Alarms restored after the phone restarted"
    AlarmEvent.RESCHEDULED_WATCHDOG -> "Alarms restored by the background check"
    else -> event
}

/**
 * Whether a "did you sleep through this?" flag is warranted: the alarm rang and was turned off
 * within seconds, without the phone ever being unlocked.
 *
 * The screen is obviously on at the moment of a dismiss — the user just tapped something — so
 * the signal is the keyguard, which is still locked when the alarm screen is dismissed over the
 * lock screen. This is a judgement call rather than a fact, so the UI phrases it as a question.
 */
fun AlarmEvent.looksHalfAsleep(): Boolean =
    event == AlarmEvent.DISMISSED &&
        ringDurationMs in 1 until HALF_ASLEEP_RING_MS &&
        deviceLocked

/** A dismiss faster than this, straight off the lock screen, is worth a second look. */
const val HALF_ASLEEP_RING_MS: Long = 15_000L
