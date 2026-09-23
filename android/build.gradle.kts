// Top-level build file. Module configuration lives in app/build.gradle.kts.
// Kotlin support is built into AGP 9; no separate kotlin-android plugin is applied.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
}
