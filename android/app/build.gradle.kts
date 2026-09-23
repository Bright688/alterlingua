plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

room {
    // Room writes the database layout to this folder; it is checked in so upgrades can be tested.
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.alterlingua.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.alterlingua.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Where the app finds the backend. Debug builds use the phone's own localhost, which reaches the computer's
    // server through `adb reverse tcp:8000 tcp:8000` (see docs/progress.md). Override with
    // -Palterlingua.translationBaseUrl=... . Release builds use -Palterlingua.releaseBackendUrl (empty until a real server exists).
    val debugBackendUrl = providers.gradleProperty("alterlingua.translationBaseUrl").orElse("http://127.0.0.1:8000").get()
    // For a real server: -Palterlingua.releaseBackendUrl=https://your-server (HTTPS only in release builds) and
    // -Palterlingua.apiToken=... (the server's ALTERLINGUA_API_TOKENS entry). Put them in ~/.gradle/gradle.properties, never in the repository.
    val releaseBackendUrl = providers.gradleProperty("alterlingua.releaseBackendUrl").orElse("").get()
    val apiToken = providers.gradleProperty("alterlingua.apiToken").orElse("").get()

    buildTypes {
        debug {
            buildConfigField("String", "TRANSLATION_BASE_URL", "\"$debugBackendUrl\"")
            buildConfigField("String", "API_TOKEN", "\"$apiToken\"")
            // Debug builds also read AlterLingua's own test notifications (see docs/progress.md). Release builds: WhatsApp only.
            buildConfigField("String", "EXTRA_INCOMING_PACKAGES", "\"com.alterlingua.app\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "TRANSLATION_BASE_URL", "\"$releaseBackendUrl\"")
            buildConfigField("String", "API_TOKEN", "\"$apiToken\"")
            buildConfigField("String", "EXTRA_INCOMING_PACKAGES", "\"\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { test ->
            // Some unit tests read the app's own resources, manifests and sources (localization, privacy and manifest checks).
            // Declaring them as inputs makes Gradle re-run those tests when a file changes instead of reusing an old pass.
            test.inputs.dir("src/main/res").withPropertyName("mainResources")
            test.inputs.dir("src/main/java").withPropertyName("mainSources")
            test.inputs.dir("src/debug").withPropertyName("debugSources")
            test.inputs.file("src/main/AndroidManifest.xml").withPropertyName("mainManifest")
            test.inputs.file("build.gradle.kts").withPropertyName("buildScript")
        }
    }

    androidResources {
        noCompress += "data" // Mozc's data file is read with a length check and copied out; keep it stored as-is
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Mozc (Japanese conversion): generated protobuf classes, the native library and its data file. The two binaries are
    // built by scripts/build-mozc.sh and are not committed (see docs/input-engines.md).
    sourceSets {
        getByName("main") {
            java.directories.add("src/mozc/java")
            jniLibs.directories.add("src/mozc/jniLibs")
            assets.directories.add("src/mozc/assets")
            // librime (Chinese pinyin conversion): its libraries are built by scripts/build-rime.sh and not committed; the small schema files are.
            jniLibs.directories.add("src/rime/jniLibs")
            assets.directories.add("src/rime/assets")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // App shell: single Activity hosting Compose.
    implementation(libs.androidx.activity.compose)

    // UI: Material 3 components and the icons needed by the bottom navigation.
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Permission and system-setting checks (ContextCompat, ActivityCompat, NotificationManagerCompat).
    implementation(libs.androidx.core.ktx)

    // Navigation between the five tabs.
    implementation(libs.androidx.navigation.compose)

    // ViewModel per screen, exposing state to Compose in a lifecycle-aware way.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Saves the user's settings on the phone (Jetpack DataStore, Preferences flavour).
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.protobuf.javalite)
    // Handwriting recognition (Google ML Kit, on the phone; the language model is downloaded once). Not open source: see docs/privacy.md.
    implementation(libs.mlkit.digital.ink)

    // The Personal Language Map database (Room), with generated code from KSP.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    testImplementation(libs.icu4j)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
