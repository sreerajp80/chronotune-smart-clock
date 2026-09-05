package `in`.sreerajp.chronotune_smart_clock.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigServiceTest {

    @Test
    fun fallback_providesDefaultValues() {
        val fallback = AppConfig.fallback
        assertNotNull(fallback)
        assertEquals("ChronoTune Smart Clock", fallback.appName)
        assertEquals("2.10.12", fallback.version)
        assertEquals("2", fallback.build)
        assertEquals("Sreeraj P", fallback.details["Architect"])
    }

    @Test
    fun fromJson_parsesCompleteValidJson() {
        val rawJson = """
            {
              "appName": "Test Clock",
              "description": "A test clock application.",
              "version": "3.0.0",
              "build": "42",
              "details": {
                "Architect": "Test Architect",
                "Author": "Test Author",
                "License": "MIT"
              }
            }
        """.trimIndent()

        val config = AppConfig.fromJson(JSONObject(rawJson))
        assertEquals("Test Clock", config.appName)
        assertEquals("A test clock application.", config.description)
        assertEquals("3.0.0", config.version)
        assertEquals("42", config.build)
        assertEquals("Test Architect", config.details["Architect"])
        assertEquals("Test Author", config.details["Author"])
        assertEquals("MIT", config.details["License"])
    }

    @Test
    fun fromJson_fallsBackOnMissingFieldsGracefully() {
        val rawJson = """
            {
              "appName": "Custom Name"
            }
        """.trimIndent()

        val config = AppConfig.fromJson(JSONObject(rawJson))
        assertEquals("Custom Name", config.appName)
        assertEquals(AppConfig.fallback.description, config.description)
        assertEquals(AppConfig.fallback.version, config.version)
        assertEquals(AppConfig.fallback.build, config.build)
        assertEquals(AppConfig.fallback.details, config.details)
    }

    @Test
    fun fromJson_handlesEmptyJsonWithoutCrashing() {
        val config = AppConfig.fromJson(JSONObject("{}"))
        assertEquals(AppConfig.fallback.appName, config.appName)
        assertEquals(AppConfig.fallback.description, config.description)
        assertEquals(AppConfig.fallback.version, config.version)
        assertEquals(AppConfig.fallback.build, config.build)
    }

    @Test
    fun configService_load_readsFromAssets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = ConfigService.load(context)
        assertNotNull(config)
        assertEquals("ChronoTune Smart Clock", config.appName)
        assertEquals("2.10.12", config.version)
        assertEquals("2", config.build)
        assertEquals("Sreeraj P", config.details["Architect"])
    }
}
