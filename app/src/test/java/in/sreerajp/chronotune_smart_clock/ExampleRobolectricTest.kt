package `in`.sreerajp.chronotune_smart_clock

import `in`.sreerajp.chronotune_smart_clock.R
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The emulated Android SDK is set once for all tests in src/test/resources/robolectric.properties.
@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Clock", appName)
  }
}
