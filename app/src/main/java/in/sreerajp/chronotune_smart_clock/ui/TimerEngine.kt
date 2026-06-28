package `in`.sreerajp.chronotune_smart_clock.ui

import android.content.Context
import android.os.SystemClock
import `in`.sreerajp.chronotune_smart_clock.data.TimerItem
import `in`.sreerajp.chronotune_smart_clock.data.repository.ClockRepository

/**
 * Single source of truth for the timer state machine, shared by [ClockViewModel] (UI-driven
 * actions), [ChronometerService] (notification controls) and [AlarmReceiver] (firing). Keeping
 * the transitions here means a cold-process notification action behaves identically to a tap in
 * the app.
 *
 * Every mutating call rewrites the persisted [TimerItem], (re)arms or cancels the AlarmManager
 * ring through [AlarmScheduler], and asks [ChronometerService] to refresh its live notifications.
 */
object TimerEngine {

    /** Creates a brand-new RUNNING timer counting down [durationMs]. Returns the new id. */
    suspend fun addAndStart(
        repo: ClockRepository,
        context: Context,
        durationMs: Long,
        label: String,
        toneName: String,
        toneUri: String,
        volume: Float
    ): Int {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val timer = TimerItem(
            label = label,
            totalDurationMs = durationMs,
            remainingMs = durationMs,
            endAtElapsed = nowElapsed + durationMs,
            fireAtWallClock = nowWall + durationMs,
            state = TimerItem.STATE_RUNNING,
            toneName = toneName,
            toneUri = toneUri,
            volume = volume,
            createdAt = nowWall
        )
        val id = repo.insertTimer(timer).toInt()
        val saved = timer.copy(id = id)
        AlarmScheduler(context).scheduleTimer(saved)
        ChronometerService.refresh(context)
        return id
    }

    suspend fun pause(repo: ClockRepository, context: Context, id: Int) {
        val timer = repo.getTimerById(id) ?: return
        if (timer.state != TimerItem.STATE_RUNNING) return
        val remaining = (timer.endAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val updated = timer.copy(
            state = TimerItem.STATE_PAUSED,
            remainingMs = remaining,
            endAtElapsed = 0L,
            fireAtWallClock = 0L
        )
        AlarmScheduler(context).cancelTimer(timer)
        repo.updateTimer(updated)
        ChronometerService.refresh(context)
    }

    suspend fun resume(repo: ClockRepository, context: Context, id: Int) {
        val timer = repo.getTimerById(id) ?: return
        if (timer.state != TimerItem.STATE_PAUSED) return
        val remaining = timer.remainingMs.coerceAtLeast(0L)
        if (remaining <= 0L) {
            markFinished(repo, context, id)
            return
        }
        val updated = timer.copy(
            state = TimerItem.STATE_RUNNING,
            endAtElapsed = SystemClock.elapsedRealtime() + remaining,
            fireAtWallClock = System.currentTimeMillis() + remaining
        )
        repo.updateTimer(updated)
        AlarmScheduler(context).scheduleTimer(updated)
        ChronometerService.refresh(context)
    }

    /** Adds one minute. Stretches [TimerItem.totalDurationMs] too so the progress ring stays sensible. */
    suspend fun addMinute(repo: ClockRepository, context: Context, id: Int) {
        val timer = repo.getTimerById(id) ?: return
        val extra = 60_000L
        when (timer.state) {
            TimerItem.STATE_RUNNING -> {
                val updated = timer.copy(
                    totalDurationMs = timer.totalDurationMs + extra,
                    endAtElapsed = timer.endAtElapsed + extra,
                    fireAtWallClock = timer.fireAtWallClock + extra
                )
                repo.updateTimer(updated)
                AlarmScheduler(context).scheduleTimer(updated)
                ChronometerService.refresh(context)
            }
            TimerItem.STATE_PAUSED, TimerItem.STATE_FINISHED -> {
                // From a finished timer, +1 min effectively restarts it for a minute.
                val base = if (timer.state == TimerItem.STATE_FINISHED) 0L else timer.remainingMs
                val updated = timer.copy(
                    state = TimerItem.STATE_PAUSED,
                    totalDurationMs = timer.totalDurationMs + extra,
                    remainingMs = base + extra
                )
                repo.updateTimer(updated)
                ChronometerService.refresh(context)
            }
        }
    }

    /** Marks a fired timer as FINISHED (its ring is handled separately by the alarm path). */
    suspend fun markFinished(repo: ClockRepository, context: Context, id: Int) {
        val timer = repo.getTimerById(id) ?: return
        val updated = timer.copy(
            state = TimerItem.STATE_FINISHED,
            remainingMs = 0L,
            endAtElapsed = 0L,
            fireAtWallClock = 0L
        )
        repo.updateTimer(updated)
        ChronometerService.refresh(context)
    }

    /** Cancels (and deletes) a timer: tears down its alarm and removes it from the list. */
    suspend fun cancel(repo: ClockRepository, context: Context, id: Int) {
        val timer = repo.getTimerById(id) ?: return
        AlarmScheduler(context).cancelTimer(timer)
        repo.deleteTimer(timer)
        ChronometerService.refresh(context)
    }

    /** Re-arms RUNNING timers after a reboot (elapsedRealtime reset) from their RTC target. */
    suspend fun rescheduleAllAfterBoot(repo: ClockRepository, context: Context) {
        val scheduler = AlarmScheduler(context)
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        repo.getAllTimersOnce().forEach { timer ->
            if (timer.state != TimerItem.STATE_RUNNING) return@forEach
            val remaining = (timer.fireAtWallClock - nowWall)
            if (remaining <= 0L) {
                // Should already have fired while powered off — mark finished, don't ring late.
                repo.updateTimer(
                    timer.copy(
                        state = TimerItem.STATE_FINISHED,
                        remainingMs = 0L,
                        endAtElapsed = 0L,
                        fireAtWallClock = 0L
                    )
                )
            } else {
                // Re-anchor the elapsedRealtime base (it reset on reboot) and re-arm the alarm.
                val updated = timer.copy(endAtElapsed = nowElapsed + remaining)
                repo.updateTimer(updated)
                scheduler.scheduleTimer(updated)
            }
        }
        ChronometerService.refresh(context)
    }
}
