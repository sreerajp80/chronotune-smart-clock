import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  id("org.jetbrains.kotlin.android")
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  // alias(libs.plugins.roborazzi)
}

val appConfigFile = file("src/main/assets/config/app_config.json")
fun parseAppConfigVersion(): Pair<String, Int> {
  if (!appConfigFile.exists()) return "1.0.0" to 1
  val text = appConfigFile.readText()
  val versionMatch = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(text)
  val buildMatch = Regex("\"build\"\\s*:\\s*\"?(\\d+)\"?").find(text)
  val version = versionMatch?.groupValues?.get(1) ?: "1.0.0"
  val build = buildMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
  return version to build
}
val (appVersionName, appVersionCode) = parseAppConfigVersion()

android {
  namespace = "in.sreerajp.chronotune_smart_clock"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "in.sreerajp.chronotune_smart_clock"
    minSdk = 24
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
  }
  val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
  }
  fun secret(key: String, legacyKey: String? = null): String? =
    keystoreProps.getProperty(key)
      ?: System.getenv(key)
      ?: (if (legacyKey != null) System.getenv(legacyKey) else null)
      ?: keystoreProps.getProperty(legacyKey ?: key)
      ?: localProps.getProperty(key)
      ?: (if (legacyKey != null) localProps.getProperty(legacyKey) else null)

  signingConfigs {
    create("appKeystore") {
      val rawStoreFile = secret("storeFile", "KEYSTORE_PATH") ?: "keystore.jks"
      val f = File(rawStoreFile)
      storeFile = if (f.isAbsolute) f else rootProject.file(rawStoreFile)
      storePassword = secret("storePassword", "STORE_PASSWORD")
      keyAlias = secret("keyAlias", "KEY_ALIAS") ?: "chronotune-smart-clock"
      keyPassword = secret("keyPassword", "KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("appKeystore")
    }
    debug {
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlin {
    compilerOptions {
      jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  // implementation(libs.androidx.compose.ui.tooling.preview)  // no @Preview in the app
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)  // tabs are a plain index in MainActivity
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.play.services.location)
  // The app makes no network calls at all, so the whole Retrofit/OkHttp/Moshi stack is off.
  // app_config.json is read with the platform's own org.json.JSONObject.
  // implementation(libs.converter.moshi)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  // No Compose UI tests and no screenshot tests yet.
  // testImplementation(libs.androidx.compose.ui.test.junit4)
  // testImplementation(libs.roborazzi)
  // testImplementation(libs.roborazzi.compose)
  // testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  // androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  // androidTestImplementation(libs.androidx.espresso.core)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // debugImplementation(libs.androidx.compose.ui.test.manifest)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
