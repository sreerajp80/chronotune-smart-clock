package `in`.sreerajp.chronotune_smart_clock.config

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject

class ConfigService {
    companion object {
        private const val ASSET_PATH = "config/app_config.json"
        private const val TAG = "ConfigService"

        fun load(context: Context): AppConfig = try {
            val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            AppConfig.fromJson(JSONObject(text))
        } catch (_: Exception) {
            AppConfig.fallback
        }

        fun loadAndVerify(context: Context): AppConfig {
            val config = load(context)
            try {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0L),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                val versionCode = PackageInfoCompat.getLongVersionCode(info).toString()
                val mismatch = info.versionName != config.version ||
                    versionCode != config.build
                if (mismatch) {
                    Log.d(
                        TAG,
                        "version/build in app_config.json " +
                            "(${config.version}+${config.build}) does not match the build " +
                            "(${info.versionName}+$versionCode).",
                    )
                }
            } catch (_: Exception) {
                // Package info unavailable — ignore.
            }
            return config
        }
    }
}
