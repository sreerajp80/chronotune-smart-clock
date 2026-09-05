package `in`.sreerajp.chronotune_smart_clock.config

import org.json.JSONObject

/**
 * Typed values for the About screen, loaded from `assets/config/app_config.json`.
 * Changing About content is a config edit, not a code change.
 */
data class AppConfig(
    val appName: String,
    val description: String,
    val version: String,
    val build: String,
    val details: Map<String, String> = emptyMap(),
) {
    companion object {
        /** Safe built-in value used when the config file is missing or malformed. */
        val fallback = AppConfig(
            appName = "ChronoTune Smart Clock",
            description = "A feature-rich Android clock app featuring multiple location clocks, custom tone alarms, a stopwatch, a timer, and an automated music scheduler.",
            version = "2.10.12",
            build = "2",
            details = mapOf(
                "Architect" to "Sreeraj P",
                "Author" to "Gemini 3.5 Flash and Claude Opus 4.8",
                "License" to "All libraries used are open source."
            ),
        )

        fun fromJson(json: JSONObject): AppConfig {
            fun str(key: String, default: String): String =
                json.optString(key, default)

            fun parseStringMap(key: String): Map<String, String> {
                val raw = json.optJSONObject(key) ?: return fallback.details
                val out = mutableMapOf<String, String>()
                for (k in raw.keys()) {
                    val v = raw.opt(k)
                    if (v is String) out[k] = v
                }
                return out
            }

            return AppConfig(
                appName = str("appName", fallback.appName),
                description = str("description", fallback.description),
                version = str("version", fallback.version),
                build = str("build", fallback.build),
                details = parseStringMap("details"),
            )
        }
    }
}
