package com.alterlingua.app.speak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the promises about sharing generated audio that live in the manifest and the FileProvider paths. */
class SpeakManifestTest {
    private fun read(path: String) = listOf("src/main/$path", "app/src/main/$path").map(::File).first { it.exists() }.readText()

    private val manifest = read("AndroidManifest.xml")

    @Test fun theFileProviderIsNotExported_andGrantsPerShareOnly() {
        val provider = Regex("""<provider.*?</provider>""", RegexOption.DOT_MATCHES_ALL).find(manifest)!!.value
        assertTrue(provider.contains("android:exported=\"false\""))
        assertTrue(provider.contains("android:grantUriPermissions=\"true\""))
        assertTrue(provider.contains("androidx.core.content.FileProvider"))
    }

    @Test fun onlyTheGeneratedSpeechFolderCanBeShared() {
        val paths = read("res/xml/spoken_audio_paths.xml")
        val entries = Regex("""<([a-z-]+)\s+name="([^"]+)"\s+path="([^"]+)"""").findAll(paths).map { it.groupValues.drop(1) }.toList()
        assertEquals(listOf(listOf("cache-path", "spoken", "spoken_audio/")), entries)
        assertFalse(paths.contains("root-path"))
        assertFalse(paths.contains("external"))
    }

    @Test fun theVoiceMessageScreenIsOnlyOpenedFromInsideTheApp() {
        val activity = Regex("""<activity\s+android:name="\.speak\.SpeakActivity".*?/>""", RegexOption.DOT_MATCHES_ALL).find(manifest)!!.value
        assertTrue(activity.contains("android:exported=\"false\""))
        assertFalse(activity.contains("intent-filter"))
    }

    @Test fun theGeneratedSpeechFolderMatchesWhereTheAppWritesIt() {
        val wiring = File(listOf("src/main/java/com/alterlingua/app/AppViewModelProvider.kt", "app/src/main/java/com/alterlingua/app/AppViewModelProvider.kt").first { File(it).exists() }).readText()
        assertTrue(wiring.contains("File(app.cacheDir, \"spoken_audio\")"))
    }
}
