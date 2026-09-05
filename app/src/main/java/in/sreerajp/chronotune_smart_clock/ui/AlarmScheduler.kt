package `in`.sreerajp.chronotune_smart_clock.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.AlarmIds
import `in`.sreerajp.chronotune_smart_clock.data.MusicSchedule
import `in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry
import `in`.sreerajp.chronotune_smart_clock.data.TimerItem
import `in`.sreerajp.chronotune_smart_clock.data.nextTriggerTime

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Arms [alarm] at its next valid trigger time.
     *
     * [snoozeCount] is how many times this ring has already been snoozed. It is only non-zero
     * when re-arming a snoozed alarm; a normal scheduled firing always starts back at 0, which
     * is how the snooze allowance resets each day without any stored counter.
     *
     * [baseAlarmId] is the id of the alarm row this firing belongs to. It differs from
     * [Alarm.id] only for a snoozed re-ring, whose id lives in the snooze space; carrying it
     * keeps a whole morning attributable to one alarm.
     */
    fun scheduleAlarm(alarm: Alarm, snoozeCount: Int = 0, baseAlarmId: Int = alarm.id) {
        if (!alarm.isEnabled) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ID", alarm.id)
            putExtra("BASE_ID", baseAlarmId)
            putExtra("TYPE", "ALARM")
            putExtra("LABEL", alarm.label.ifBlank { "Alarm Ringing" })
            putExtra("TONE", alarm.customToneName)
            putExtra("URI", alarm.customToneUri)
            putExtra("VOLUME", alarm.volume)
            putExtra("VIBRATE", alarm.isVibrate)
            putExtra("SNOOZE_MIN", alarm.snoozeMinutes)
            // Carry the dismiss-challenge config so the ringing screen can enforce it.
            putExtra("CHALLENGE", alarm.dismissChallenge)
            putExtra("CHALLENGE_DIFFICULTY", alarm.challengeDifficulty)
            putExtra("CHALLENGE_COUNT", alarm.challengeCount)
            // Carry the per-alarm auto-silence length so the ringing service can stop itself.
            putExtra("AUTO_SILENCE_MIN", alarm.autoSilenceMinutes)
            // Carry repeat-day info + time so the receiver can re-arm the next occurrence after firing.
            putExtra("DAYS", alarm.daysOfWeek)
            putExtra("HOUR", alarm.hour)
            putExtra("MINUTE", alarm.minute)
            // Carry the pause window so the receiver can re-arm pause-aware after firing.
            putExtra("PAUSE_START", alarm.pauseStartMillis)
            putExtra("PAUSE_END", alarm.pauseEndMillis)
            // Carry the skip-next day so the receiver keeps skipping it when it re-arms.
            putExtra("SKIP_EPOCH_DAY", alarm.skipNextEpochDay)
            // Carry the holiday mode; the day list itself can't ride in an Intent, so the
            // receiver re-reads it from SpecialDayRegistry when it re-arms.
            putExtra("HOLIDAY_MODE", alarm.holidayMode)
            // Carry the snooze limit + style so the ringing UI and the snooze receiver can
            // enforce them without a database read.
            putExtra("MAX_SNOOZE_COUNT", alarm.maxSnoozeCount)
            putExtra("SNOOZE_MODE", alarm.snoozeMode)
            putExtra("SNOOZE_COUNT", snoozeCount)
            // Carried so the receiver can keep the start date on a re-arm, and can tell a
            // dated one-shot (which must switch itself off after ringing) from a plain one.
            putExtra("START_EPOCH_DAY", alarm.startEpochDay)
        }

        val calendar = nextTriggerTime(
            alarm.hour, alarm.minute, alarm.getRepeatDaysList(),
            alarm.pauseStartMillis, alarm.pauseEndMillis, alarm.skipNextEpochDay,
            alarm.holidayMode, SpecialDayRegistry::kindOf, alarm.startEpochDay
        )

        // The time this firing is meant to happen, carried so the receiver can report how late
        // the OS actually delivered it. Added before the PendingIntent is built, because
        // FLAG_UPDATE_CURRENT copies the extras at creation time — a later putExtra on the
        // same Intent object would never reach the receiver.
        intent.putExtra("SCHEDULED_AT", calendar.timeInMillis)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setAlarmClock is the only AlarmManager API that grants the "user-facing alarm
        // clock" privilege. On Android 12+ this is what lets the resulting broadcast
        // post a full-screen intent that actually takes over the screen and bypass
        // Doze/battery restrictions — setExactAndAllowWhileIdle does NOT.
        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                // Show-intent is what the system uses to open the alarm's edit UI from the
                // status bar "next alarm" chip. We point it at the main app so the user can
                // jump to the alarms screen.
                val showIntent = Intent(context, `in`.sreerajp.chronotune_smart_clock.MainActivity::class.java)
                val showPending = PendingIntent.getActivity(
                    context,
                    alarm.id,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val info = AlarmManager.AlarmClockInfo(calendar.timeInMillis, showPending)
                alarmManager.setAlarmClock(info, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Successfully scheduled alarm ${alarm.id} for ${calendar.time}")
            recordArmed(baseAlarmId, alarm.label, calendar.timeInMillis, snoozeCount)
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Permission lack for exact scheduling, falling back: ${e.message}")
            // Deliberately setAndAllowWhileIdle and not plain set(): an inexact alarm can be
            // deferred for hours in Doze, and — worse — a broadcast delivered by set() carries
            // no temporary allow-list, so the receiver's startForegroundService would be
            // refused and the ring would be lost with no sound at all.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            recordArmed(baseAlarmId, alarm.label, calendar.timeInMillis, snoozeCount)
        }
    }

    /**
     * Notes in the history that this alarm is now armed for [triggerAt].
     *
     * This row is what makes a missed alarm detectable at all: if the time passes with no
     * matching ring recorded against the same alarm, nothing rang.
     *
     * A snoozed re-ring is not recorded here — the SNOOZED row already says when it is due,
     * and a second arm row per snooze would just be noise.
     */
    private fun recordArmed(alarmId: Int, label: String, triggerAt: Long, snoozeCount: Int) {
        if (snoozeCount > 0) return
        `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog.record(
            context,
            `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent(
                alarmId = alarmId,
                type = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.TYPE_ALARM,
                label = label,
                event = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.SCHEDULED,
                scheduledAt = triggerAt
            )
        )
    }

    /**
     * Cancels the alarm's own pending firing *and* any snoozed re-ring still waiting for it.
     *
     * Cancelling only the base id used to leave a snooze armed, so an alarm switched off
     * between a snooze and its re-ring would still go off once more.
     */
    fun cancelAlarm(alarm: Alarm) {
        cancelByRequestCode(alarm.id)
        cancelByRequestCode(AlarmIds.snoozeRing(alarm.id))
        `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog.record(
            context,
            `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent(
                alarmId = alarm.id,
                type = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.TYPE_ALARM,
                label = alarm.label,
                event = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.CANCELLED
            )
        )
    }

    /** Cancels a pending broadcast by request code, if one exists. */
    private fun cancelByRequestCode(requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * True when a pending broadcast with [requestCode] still exists.
     *
     * FLAG_NO_CREATE returns null once the OS has thrown the alarm away — which happens on a
     * force-stop, an OEM battery clean-up, or app hibernation. This is what the start-up and
     * watchdog re-arms use to find alarms that have gone missing, so they only rebuild the
     * ones that are actually gone instead of churning every pending intent on the device.
     */
    fun isArmed(requestCode: Int): Boolean =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null

    /**
     * Drops any snooze left pending by a pre-fix build, whose chain grew id+50000, id+100000,
     * id+150000... Nothing arms these any more; this only clears what an upgrade inherited.
     */
    fun cancelLegacySnoozes(alarmId: Int) {
        AlarmIds.LEGACY_SNOOZE_OFFSETS.forEach { offset ->
            try {
                cancelByRequestCode(alarmId + offset)
            } catch (_: Exception) { /* best effort */ }
        }
    }

    /**
     * Arms the periodic watchdog that repairs alarms the OS has silently dropped.
     *
     * Inexact on purpose: it only needs to happen roughly every few hours, and an inexact
     * while-idle alarm is far cheaper on the battery than an alarm-clock one. [WatchdogReceiver]
     * re-arms it each time it fires, so this is called once at start-up and after boot.
     */
    fun scheduleWatchdog() {
        val intent = Intent(context, WatchdogReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            AlarmIds.WATCHDOG_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fireAt = System.currentTimeMillis() +
            `in`.sreerajp.chronotune_smart_clock.AppPrefs.WATCHDOG_INTERVAL_MS
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
            Log.d("AlarmScheduler", "Watchdog armed for $fireAt")
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Could not arm watchdog: ${e.message}")
        }
    }

    fun scheduleMusic(schedule: MusicSchedule) {
        if (!schedule.isEnabled) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ID", schedule.id)
            putExtra("TYPE", "MUSIC")
            putExtra("LABEL", schedule.label.ifBlank { "Scheduled Music" })
            putExtra("TONE", schedule.musicTrackName)
            putExtra("URI", schedule.customFileUri)
            putExtra("VOLUME", schedule.volume)
            putExtra("DURATION_MIN", schedule.durationMinutes)
            putExtra("DAYS", schedule.daysOfWeek)
            putExtra("HOUR", schedule.hour)
            putExtra("MINUTE", schedule.minute)
        }

        val calendar = nextTriggerTime(schedule.hour, schedule.minute, schedule.getRepeatDaysList())
        intent.putExtra("SCHEDULED_AT", calendar.timeInMillis)

        // Offset the request code to keep it unique from alarm IDs (see AlarmIds).
        val ringId = AlarmIds.musicRing(schedule.id)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ringId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Music used to be armed with setAndAllowWhileIdle only, which Doze rate-limits to
            // roughly one firing every nine minutes — a schedule could start late or be skipped
            // entirely. Use the same privileged alarm-clock path as alarms when we are allowed
            // to, and keep while-idle as the fallback.
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                val showIntent = Intent(context, `in`.sreerajp.chronotune_smart_clock.MainActivity::class.java)
                val showPending = PendingIntent.getActivity(
                    context,
                    ringId,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val info = AlarmManager.AlarmClockInfo(calendar.timeInMillis, showPending)
                alarmManager.setAlarmClock(info, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Successfully scheduled music ${schedule.id} for ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Music exact scheduling denied, falling back: ${e.message}")
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } catch (e2: Exception) {
                Log.e("AlarmScheduler", "Failed scheduling music: ${e2.message}")
            }
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Failed scheduling music: ${e.message}")
        }
    }

    /**
     * Schedules a one-shot countdown timer to ring at [TimerItem.fireAtWallClock] using the
     * same privileged setAlarmClock path as alarms, so it rings even when the app is
     * backgrounded / Dozed / killed. The ring is handled by the shared [AlarmReceiver] ->
     * [AlarmService] stack (TYPE = "TIMER"), using the timer's chosen tone/volume.
     */
    fun scheduleTimer(timer: TimerItem) {
        if (timer.fireAtWallClock <= 0L) return

        val ringId = AlarmIds.timerRing(timer.id)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ID", ringId)
            putExtra("BASE_ID", ringId)
            putExtra("TIMER_ID", timer.id)
            putExtra("TYPE", "TIMER")
            putExtra("SCHEDULED_AT", timer.fireAtWallClock)
            putExtra("LABEL", timer.label.ifBlank { "Timer Finished" })
            putExtra("TONE", timer.toneName)
            putExtra("URI", timer.toneUri)
            putExtra("VOLUME", timer.volume)
            // Timers use the global default auto-silence length (no per-timer editor for this).
            putExtra(
                "AUTO_SILENCE_MIN",
                `in`.sreerajp.chronotune_smart_clock.AppPrefs.getDefaultAutoSilenceMinutes(context)
            )
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ringId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                val showIntent = Intent(context, `in`.sreerajp.chronotune_smart_clock.MainActivity::class.java)
                val showPending = PendingIntent.getActivity(
                    context,
                    ringId,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val info = AlarmManager.AlarmClockInfo(timer.fireAtWallClock, showPending)
                alarmManager.setAlarmClock(info, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timer.fireAtWallClock,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Scheduled timer ${timer.id} for ${timer.fireAtWallClock}")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Timer exact scheduling denied, falling back: ${e.message}")
            // While-idle rather than plain set(), for the same reason as scheduleAlarm.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timer.fireAtWallClock,
                pendingIntent
            )
        }
    }

    fun cancelTimer(timer: TimerItem) {
        cancelByRequestCode(AlarmIds.timerRing(timer.id))
    }

    fun cancelMusic(schedule: MusicSchedule) {
        cancelByRequestCode(AlarmIds.musicRing(schedule.id))
    }
}
