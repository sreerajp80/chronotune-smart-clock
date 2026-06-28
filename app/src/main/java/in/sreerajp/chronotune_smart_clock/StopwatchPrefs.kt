package `in`.sreerajp.chronotune_smart_clock

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import `in`.sreerajp.chronotune_smart_clock.ui.ChronometerService
import `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Durable, process-death-proof store **and** in-memory hub for the single stopwatch. Both the
 * UI ([ClockViewModel]) and the foreground [ChronometerService] mutate and observe the same
 * [snapshot] flow, so a Pause/Lap/Reset from the notification and from the app are identical.
 *
 * The base is [SystemClock.elapsedRealtime] (monotonic, immune to wall-clock changes). The
 * displayed elapsed time is always *derived* from the stored base, so a freshly restored
 * process recomputes the exact value:
 *
 * - RUNNING: `elapsed = (elapsedRealtime() - baseElapsed) + accumulatedMs`
 * - PAUSED / IDLE: `elapsed = accumulatedMs`
 *
 * Laps are serialized as `number:splitMs:lapMs` triples joined by `;`.
 */
object StopwatchPrefs {
    private const val PREFS = "chronotune_stopwatch_prefs"
    private const val KEY_STATE = "sw_state"
    private const val KEY_BASE = "sw_base_elapsed"
    private const val KEY_ACCUM = "sw_accumulated"
    private const val KEY_LAPS = "sw_laps"

    const val STATE_IDLE = "IDLE"
    const val STATE_RUNNING = "RUNNING"
    const val STATE_PAUSED = "PAUSED"

    data class Lap(val number: Int, val splitTimeMs: Long, val lapTimeMs: Long)

    data class Snapshot(
        val state: String = STATE_IDLE,
        val baseElapsed: Long = 0L,
        val accumulatedMs: Long = 0L,
        val laps: List<Lap> = emptyList()
    ) {
        /** Elapsed time right now, derived from the stored base. */
        fun elapsedNow(nowElapsed: Long = SystemClock.elapsedRealtime()): Long =
            if (state == STATE_RUNNING) (nowElapsed - baseElapsed) + accumulatedMs
            else accumulatedMs
    }

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private var initialized = false

    /** Loads the persisted snapshot into the in-memory hub. Safe to call repeatedly. */
    fun init(context: Context) {
        if (initialized) return
        _snapshot.value = load(context)
        initialized = true
    }

    fun start(context: Context) {
        val s = _snapshot.value
        if (s.state == STATE_RUNNING) return
        commit(context, s.copy(state = STATE_RUNNING, baseElapsed = SystemClock.elapsedRealtime()))
    }

    fun pause(context: Context) {
        val s = _snapshot.value
        if (s.state != STATE_RUNNING) return
        commit(context, s.copy(state = STATE_PAUSED, accumulatedMs = s.elapsedNow(), baseElapsed = 0L))
    }

    fun reset(context: Context) {
        commit(context, Snapshot())
    }

    fun lap(context: Context) {
        val s = _snapshot.value
        if (s.state == STATE_IDLE) return
        val total = s.elapsedNow()
        val number = s.laps.size + 1
        val lastSplit = s.laps.firstOrNull()?.splitTimeMs ?: 0L
        val newLap = Lap(number, total, total - lastSplit)
        commit(context, s.copy(laps = listOf(newLap) + s.laps))
    }

    private fun commit(context: Context, snapshot: Snapshot) {
        _snapshot.value = snapshot
        save(context, snapshot)
        ChronometerService.refresh(context)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): Snapshot {
        val p = prefs(context)
        return Snapshot(
            state = p.getString(KEY_STATE, STATE_IDLE) ?: STATE_IDLE,
            baseElapsed = p.getLong(KEY_BASE, 0L),
            accumulatedMs = p.getLong(KEY_ACCUM, 0L),
            laps = deserializeLaps(p.getString(KEY_LAPS, "") ?: "")
        )
    }

    private fun save(context: Context, snapshot: Snapshot) {
        prefs(context).edit {
            putString(KEY_STATE, snapshot.state)
            putLong(KEY_BASE, snapshot.baseElapsed)
            putLong(KEY_ACCUM, snapshot.accumulatedMs)
            putString(KEY_LAPS, serializeLaps(snapshot.laps))
        }
    }

    private fun serializeLaps(laps: List<Lap>): String =
        laps.joinToString(";") { "${it.number}:${it.splitTimeMs}:${it.lapTimeMs}" }

    private fun deserializeLaps(raw: String): List<Lap> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 3) return@mapNotNull null
            val n = parts[0].toIntOrNull() ?: return@mapNotNull null
            val split = parts[1].toLongOrNull() ?: return@mapNotNull null
            val lap = parts[2].toLongOrNull() ?: return@mapNotNull null
            Lap(n, split, lap)
        }
    }
}
