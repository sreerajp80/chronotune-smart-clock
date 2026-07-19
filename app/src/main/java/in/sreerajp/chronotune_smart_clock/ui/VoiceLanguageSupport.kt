package `in`.sreerajp.chronotune_smart_clock.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.net.toUri

/**
 * Finds out whether the phone's speech app can actually understand a given language.
 *
 * Malayalam speech-to-text only works when the user has the Malayalam voice model installed.
 * Without this check the app would silently hear English words and set the wrong alarm, so we
 * ask the recogniser up front and tell the user plainly.
 */
object VoiceLanguageSupport {

    enum class Support {
        /** The recogniser lists this language. */
        SUPPORTED,

        /** The recogniser answered, and this language was not in its list. */
        NOT_SUPPORTED,

        /**
         * We could not find out. Several speech apps ignore the language-details broadcast.
         * The UI must word its message as "may not" rather than stating it as fact.
         */
        UNKNOWN
    }

    /** Cached per language tag for the session; cleared by [refresh] when Settings reopens. */
    private val cache = mutableMapOf<String, Support>()

    /** True when some app on the device can do speech recognition at all. */
    fun isRecognitionAvailable(context: Context): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /** Drops the cached answers so a newly installed voice model is noticed. */
    fun refresh() = cache.clear()

    /**
     * Asks the speech app which languages it has, then reports on [languageTag] (e.g. "ml").
     *
     * The answer arrives through an ordered broadcast, so the result comes back in [onResult]
     * on the main thread rather than as a return value.
     */
    fun check(context: Context, languageTag: String, onResult: (Support) -> Unit) {
        val key = languageTag.lowercase()
        cache[key]?.let { onResult(it); return }

        if (!isRecognitionAvailable(context)) {
            cache[key] = Support.NOT_SUPPORTED
            onResult(Support.NOT_SUPPORTED)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS)
        // Aim the broadcast at the app that handles speech, when there is one.
        val handler = context.packageManager
            .queryBroadcastReceivers(intent, 0)
            .firstOrNull()?.activityInfo?.packageName
        if (handler != null) intent.setPackage(handler)

        try {
            context.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, received: Intent?) {
                        val extras: Bundle? = getResultExtras(true)
                        val languages = buildList {
                            extras?.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                                ?.let { addAll(it) }
                            extras?.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE)
                                ?.let { add(it) }
                        }
                        val support = when {
                            languages.isEmpty() -> Support.UNKNOWN
                            languages.any { it.replace('_', '-').lowercase().startsWith(key) } ->
                                Support.SUPPORTED
                            else -> Support.NOT_SUPPORTED
                        }
                        cache[key] = support
                        onResult(support)
                    }
                },
                null,
                android.app.Activity.RESULT_OK,
                null,
                null
            )
        } catch (_: Exception) {
            cache[key] = Support.UNKNOWN
            onResult(Support.UNKNOWN)
        }
    }

    /**
     * Opens the system screen where voice languages are downloaded. Falls back to the app
     * details page of the speech app, and then to nothing, since not every phone has either.
     */
    fun openVoiceInputSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                return try {
                    context.startActivity(intent); true
                } catch (_: Exception) {
                    false
                }
            }
        }
        // Last resort: the speech app's own settings page.
        val speechPackage = context.packageManager
            .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
            .firstOrNull()?.activityInfo?.packageName ?: return false
        return try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData("package:$speechPackage".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
