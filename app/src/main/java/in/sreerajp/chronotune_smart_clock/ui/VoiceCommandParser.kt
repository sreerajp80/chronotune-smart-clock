package `in`.sreerajp.chronotune_smart_clock.ui

import java.util.Calendar

/**
 * What the user asked for out loud, once the spoken words have been understood.
 *
 * Days follow the app-wide convention used by [in.sreerajp.chronotune_smart_clock.data.Scheduled]:
 * 1 = Monday ... 7 = Sunday, and an empty list means "ring once".
 */
sealed class VoiceCommand {
    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String = "",
        val days: List<Int> = emptyList()
    ) : VoiceCommand()

    data class SetTimer(val durationMs: Long, val label: String = "") : VoiceCommand()

    data object ShowAlarms : VoiceCommand()
    data object ShowTimers : VoiceCommand()
    data object DismissAlarm : VoiceCommand()
    data class SnoozeAlarm(val minutes: Int? = null) : VoiceCommand()

    /** Nothing usable was found. [text] is what we heard, so the UI can show it back. */
    data class Unknown(val text: String) : VoiceCommand()
}

/**
 * Turns a spoken sentence into a [VoiceCommand]. Works for English and Malayalam.
 *
 * Deliberately plain Kotlin — no Android classes — so the whole thing is unit testable and
 * runs entirely offline. Matching is substring/regex based rather than a real grammar: speech
 * recognisers return loose, unpunctuated text, so being forgiving beats being precise.
 */
object VoiceCommandParser {

    // ---------------------------------------------------------------- entry point

    fun parse(spoken: String, now: Calendar = Calendar.getInstance()): VoiceCommand {
        val text = normalize(spoken)
        if (text.isBlank()) return VoiceCommand.Unknown(spoken)

        // Control words first: they are specific, and "stop the alarm" must not be
        // mistaken for a request to create one.
        parseControl(text)?.let { return it }

        // A timer is anything with a duration and a timer word, or a bare "for N minutes".
        if (looksLikeTimer(text)) {
            parseDuration(text)?.let { return VoiceCommand.SetTimer(it, extractLabel(text)) }
        }

        parseTimeOfDay(text, now)?.let { (hour, minute) ->
            return VoiceCommand.SetAlarm(hour, minute, extractLabel(text), parseDays(text))
        }

        // "show my alarms" with no time at all.
        if (containsAny(text, SHOW_WORDS) && containsAny(text, ALARM_WORDS)) {
            return VoiceCommand.ShowAlarms
        }
        if (containsAny(text, SHOW_WORDS) && containsAny(text, TIMER_WORDS)) {
            return VoiceCommand.ShowTimers
        }

        return VoiceCommand.Unknown(spoken.trim())
    }

    // ---------------------------------------------------------------- normalising

    /** Malayalam digit ൦ (U+0D66); the ten digits are contiguous from there. */
    private const val ML_ZERO = '൦'

    /**
     * Vowel signs and the chandrakkala are combining marks, not letters. Dropping them would
     * turn "രാവിലെ" into "രവല", so they must be kept alongside the letters themselves.
     */
    private val MARK_CATEGORIES = setOf(
        CharCategory.NON_SPACING_MARK,
        CharCategory.COMBINING_SPACING_MARK,
        CharCategory.ENCLOSING_MARK
    )

    /**
     * Lowercases, turns Malayalam numerals into ASCII ones, and drops the punctuation that
     * recognisers sprinkle in. Colons survive because they carry "7:30".
     */
    internal fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch in ML_ZERO..(ML_ZERO + 9) -> sb.append(('0' + (ch - ML_ZERO)))
                ch.isLetterOrDigit() || ch.category in MARK_CATEGORIES ||
                    ch == ':' || ch == '\'' -> sb.append(ch.lowercaseChar())

                else -> sb.append(' ')
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    // ---------------------------------------------------------------- vocabulary

    private val ALARM_WORDS = listOf("alarm", "wake me", "wake up", "അലാറം", "അലാം", "ഉണർത്ത")
    private val TIMER_WORDS = listOf("timer", "countdown", "ടൈമർ", "ടയമർ")
    private val SHOW_WORDS = listOf("show", "list", "open", "what are my", "കാണിക്ക", "തുറക്ക")
    private val DISMISS_WORDS =
        listOf("stop", "dismiss", "turn off", "switch off", "cancel the alarm", "silence",
            "നിർത്ത", "ഓഫ് ചെയ്യ", "ഓഫാക്ക", "കെടുത്ത")
    private val SNOOZE_WORDS = listOf("snooze", "five more minutes", "സ്നൂസ്", "കുറച്ചു കഴിഞ്ഞ")

    /** Malayalam hour words, as stems so inflected forms (ഏഴിന്, ഏഴുമണി) still match. */
    private val ML_NUMBERS: List<Pair<String, Int>> = listOf(
        // Longest first — പതിനൊന്ന് must win over ഒന്ന്.
        "പന്ത്രണ്ട" to 12, "പതിനൊന്ന" to 11, "പതിനഞ്ച" to 15, "പതിന" to 10,
        "അമ്പത" to 50, "നാൽപ്പത" to 40, "നാല്പത" to 40, "മുപ്പത" to 30, "ഇരുപത" to 20,
        "ഒൻപത" to 9, "ഒമ്പത" to 9, "പത്ത" to 10, "എട്ട" to 8, "ഏഴ" to 7, "ആറ" to 6,
        "അഞ്ച" to 5, "നാല" to 4, "മൂന്ന" to 3, "രണ്ട" to 2, "ഒന്ന" to 1, "ഒരു" to 1
    )

    private val EN_NUMBERS: List<Pair<String, Int>> = listOf(
        "twenty five" to 25, "twenty" to 20, "thirty" to 30, "forty five" to 45,
        "forty" to 40, "fifty" to 50, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "ten" to 10, "nine" to 9, "eight" to 8,
        "seven" to 7, "six" to 6, "five" to 5, "four" to 4, "three" to 3, "two" to 2,
        "one" to 1, "an" to 1, "a" to 1
    )

    /** Malayalam half-past forms: ഏഴര = 7:30. Built from the hour stems. */
    private val ML_HALF_HOURS: List<Pair<String, Int>> = listOf(
        "പന്ത്രണ്ടര" to 12, "പതിനൊന്നര" to 11, "പത്തര" to 10, "ഒമ്പതര" to 9, "ഒൻപതര" to 9,
        "എട്ടര" to 8, "ഏഴര" to 7, "ആറര" to 6, "അഞ്ചര" to 5, "നാലര" to 4, "മൂന്നര" to 3,
        "രണ്ടര" to 2, "ഒന്നര" to 1
    )

    private val AM_WORDS = listOf("am", "a m", "morning", "രാവിലെ", "പുലർച്ചെ", "കാലത്ത")
    private val PM_WORDS =
        listOf("pm", "p m", "evening", "afternoon", "tonight", "night",
            "വൈകിട്ട", "വൈകുന്നേരം", "ഉച്ചയ്ക്ക", "ഉച്ചക്ക", "രാത്രി", "സന്ധ്യ")
    /** Night words that mean "after midnight" rather than "evening" for small hours. */
    private val NIGHT_WORDS = listOf("night", "tonight", "രാത്രി")

    private val DAY_WORDS: List<Pair<String, Int>> = listOf(
        "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4,
        "friday" to 5, "saturday" to 6, "sunday" to 7,
        "തിങ്കൾ" to 1, "ചൊവ്വ" to 2, "ബുധൻ" to 3, "വ്യാഴം" to 4,
        "വെള്ളി" to 5, "ശനി" to 6, "ഞായർ" to 7, "ഞായറ" to 7
    )

    // ---------------------------------------------------------------- control words

    private fun parseControl(text: String): VoiceCommand? {
        val mentionsAlarm = containsAny(text, ALARM_WORDS)
        if (containsAny(text, SNOOZE_WORDS)) {
            return VoiceCommand.SnoozeAlarm(Regex("(\\d{1,2})\\s*(minutes?|mins?|മിനിറ്റ)")
                .find(text)?.groupValues?.get(1)?.toIntOrNull())
        }
        if (mentionsAlarm && containsAny(text, DISMISS_WORDS)) return VoiceCommand.DismissAlarm
        return null
    }

    // ---------------------------------------------------------------- timers

    private fun looksLikeTimer(text: String): Boolean {
        if (containsAny(text, TIMER_WORDS)) return true
        // "wake me in 20 minutes" / "20 മിനിറ്റ് കഴിഞ്ഞ്" — a relative delay, not a clock time.
        val relative = Regex("\\b(in|after)\\b").containsMatchIn(text) ||
            containsAny(text, listOf("കഴിഞ്ഞ", "ശേഷം"))
        return relative && hasDurationUnit(text)
    }

    private fun hasDurationUnit(text: String): Boolean =
        containsAny(text, listOf("hour", "hr", "minute", "min", "second", "sec",
            "മണിക്കൂർ", "മിനിറ്റ", "സെക്കൻഡ", "സെക്കന്റ"))

    /** Adds up every "<number> <unit>" pair it finds: "1 hour 30 minutes" = 90 min. */
    internal fun parseDuration(text: String): Long? {
        var total = 0L
        var found = false

        // Checked before the units below, because "half an hour" would otherwise read the
        // "an" as the number one and come out as a full hour.
        if (Regex("half\\s+(an\\s+)?hour").containsMatchIn(text) || text.contains("അര മണിക്കൂർ")) {
            return 30 * 60_000L
        }

        val units = listOf(
            Triple(listOf("hours", "hour", "hrs", "hr", "മണിക്കൂർ"), 3_600_000L, "h"),
            Triple(listOf("minutes", "minute", "mins", "min", "മിനിറ്റ്", "മിനിറ്റ"), 60_000L, "m"),
            Triple(listOf("seconds", "second", "secs", "sec", "സെക്കൻഡ", "സെക്കന്റ"), 1_000L, "s")
        )

        for ((words, millis, _) in units) {
            for (word in words) {
                val idx = text.indexOf(word)
                if (idx < 0) continue
                val amount = numberBefore(text, idx) ?: continue
                total += amount * millis
                found = true
                break // one hit per unit is enough
            }
        }

        // Bare "timer for 10" — assume minutes, the usual meaning.
        if (!found) {
            val bare = Regex("(?:for|of|ടൈമർ)\\s*(\\d{1,3})\\b").find(text)
                ?: Regex("^(\\d{1,3})\\b").find(text)
            val n = bare?.groupValues?.get(1)?.toIntOrNull()
            if (n != null && n > 0) return n * 60_000L
            return null
        }
        return if (total > 0L) total else null
    }

    /** Reads the number sitting just before [index] — digits or a spelled-out word. */
    private fun numberBefore(text: String, index: Int): Long? {
        val head = text.substring(0, index).trimEnd()
        Regex("(\\d{1,4})$").find(head)?.let { return it.groupValues[1].toLongOrNull() }
        for ((word, value) in EN_NUMBERS) {
            if (head.endsWith(" $word") || head == word) return value.toLong()
        }
        for ((stem, value) in ML_NUMBERS) {
            // Malayalam glues the number to the unit ("പത്തുമിനിറ്റ്"), so allow a loose tail.
            if (head.endsWith(stem) || Regex("$stem\\S{0,3}$").containsMatchIn(head)) {
                return value.toLong()
            }
        }
        return null
    }

    // ---------------------------------------------------------------- clock times

    /** Returns hour (0-23) and minute, or null when the sentence names no time. */
    internal fun parseTimeOfDay(text: String, now: Calendar): Pair<Int, Int>? {
        val hasAm = containsAny(text, AM_WORDS)
        val hasPm = containsAny(text, PM_WORDS)

        // ---- Malayalam half-past forms: ഏഴര
        for ((word, hour) in ML_HALF_HOURS) {
            if (text.contains(word)) return applyMeridiem(hour, 30, hasAm, hasPm, text, now)
        }

        // ---- "7:30", "07:30 pm"
        Regex("\\b(\\d{1,2}):(\\d{2})\\b").find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) {
                return if (h > 12 || (h == 0)) h to min
                else applyMeridiem(h, min, hasAm, hasPm, text, now)
            }
        }

        // ---- "quarter past six", "half past five", "quarter to seven"
        Regex("(quarter|half)\\s+(past|to)\\s+([a-z]+|\\d{1,2})").find(text)?.let { m ->
            val amount = if (m.groupValues[1] == "quarter") 15 else 30
            val base = wordOrDigitToNumber(m.groupValues[3]) ?: return@let
            return if (m.groupValues[2] == "past") {
                applyMeridiem(base, amount, hasAm, hasPm, text, now)
            } else {
                val h = if (base == 1) 12 else base - 1
                applyMeridiem(h, 60 - amount, hasAm, hasPm, text, now)
            }
        }

        // ---- "seven thirty", "7 30"
        Regex("\\b(\\d{1,2})\\s+(\\d{2})\\b").find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 1..12 && min in 0..59) return applyMeridiem(h, min, hasAm, hasPm, text, now)
        }

        // ---- Malayalam "ഏഴു മണിക്ക്" / "7 മണിക്ക്", plus an optional minute part
        if (text.contains("മണി")) {
            val hour = numberBefore(text, text.indexOf("മണി"))?.toInt()
            if (hour != null && hour in 1..23) {
                val min = Regex("(\\d{1,2})\\s*മിനിറ്റ").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                return if (hour > 12) hour to min
                else applyMeridiem(hour, min, hasAm, hasPm, text, now)
            }
        }

        // ---- "7 o'clock", "7 am", "at 7", "for seven"
        val digit = Regex("\\b(\\d{1,2})\\s*(o'?\\s*clock|am|pm)?\\b").find(text)
        val explicit = Regex("\\b(?:at|for|by|to)\\s+(\\d{1,2})\\b").find(text)
        val h = (explicit ?: digit)?.groupValues?.get(1)?.toIntOrNull()
        if (h != null && h in 0..23 && (hasAm || hasPm || mentionsClock(text))) {
            return if (h > 12 || h == 0) h to 0 else applyMeridiem(h, 0, hasAm, hasPm, text, now)
        }

        // ---- spelled-out hour: "set an alarm for seven"
        if (mentionsClock(text)) {
            for ((stem, value) in ML_NUMBERS) {
                if (value in 1..12 && text.contains(stem)) {
                    return applyMeridiem(value, 0, hasAm, hasPm, text, now)
                }
            }
            for ((word, value) in EN_NUMBERS) {
                if (value in 1..12 && word.length > 2 &&
                    Regex("\\b$word\\b").containsMatchIn(text)
                ) return applyMeridiem(value, 0, hasAm, hasPm, text, now)
            }
        }
        return null
    }

    /** True when the sentence is clearly about setting/naming a clock time. */
    private fun mentionsClock(text: String): Boolean =
        containsAny(text, ALARM_WORDS) ||
            containsAny(text, AM_WORDS) || containsAny(text, PM_WORDS) ||
            containsAny(text, listOf("o'clock", "oclock", "മണി", "at ", "set "))

    /**
     * Decides AM vs PM.
     *
     * When the speaker said "am"/"pm" (or a Malayalam equivalent) we obey it. When they did
     * not — "set an alarm for 7" — we pick whichever of 7:00 and 19:00 comes soonest, which
     * is what people mean in practice: asking at 10 pm gets you 7 am.
     */
    private fun applyMeridiem(
        rawHour: Int,
        minute: Int,
        hasAm: Boolean,
        hasPm: Boolean,
        text: String,
        now: Calendar
    ): Pair<Int, Int> {
        val h12 = rawHour % 12
        return when {
            hasAm && !hasPm -> h12 to minute
            hasPm && !hasAm -> {
                // "1 at night" means 01:00, but "9 at night" means 21:00.
                val night = containsAny(text, NIGHT_WORDS)
                if (night && h12 in 1..4) h12 to minute else (h12 + 12) to minute
            }
            else -> nextUpcoming(h12, minute, now) to minute
        }
    }

    /** Of h and h+12, the one that arrives sooner from [now]. */
    private fun nextUpcoming(h12: Int, minute: Int, now: Calendar): Int {
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        fun until(hour: Int): Int {
            val diff = (hour * 60 + minute) - nowMinutes
            return if (diff <= 0) diff + 24 * 60 else diff
        }
        return if (until(h12) <= until(h12 + 12)) h12 else h12 + 12
    }

    private fun wordOrDigitToNumber(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        return EN_NUMBERS.firstOrNull { it.first == token }?.second
    }

    // ---------------------------------------------------------------- repeat days

    internal fun parseDays(text: String): List<Int> {
        if (containsAny(text, listOf("every day", "everyday", "daily", "all days",
                "ദിവസവും", "എന്നും"))
        ) return listOf(1, 2, 3, 4, 5, 6, 7)

        if (containsAny(text, listOf("weekday", "week days", "working days",
                "പ്രവൃത്തിദിവസ", "പ്രവർത്തിദിവസ"))
        ) return listOf(1, 2, 3, 4, 5)

        if (containsAny(text, listOf("weekend", "week end", "വാരാന്ത്യ"))) return listOf(6, 7)

        val days = DAY_WORDS.filter { text.contains(it.first) }.map { it.second }.distinct().sorted()
        return days
    }

    // ---------------------------------------------------------------- label

    /** Pulls out an explicit name: "set an alarm for 7 called gym". */
    internal fun extractLabel(text: String): String {
        val m = Regex("\\b(?:called|labell?ed|named|for my|എന്ന പേരിൽ)\\s+(.{1,40})$").find(text)
        return m?.groupValues?.get(1)?.trim().orEmpty()
    }

    // ---------------------------------------------------------------- util

    private fun containsAny(text: String, needles: List<String>): Boolean =
        needles.any { text.contains(it) }
}
