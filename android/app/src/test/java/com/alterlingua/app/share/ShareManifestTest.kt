package com.alterlingua.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the promises about the Share target that live in AndroidManifest.xml. */
class ShareManifestTest {
    private val manifest: String = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        .map(::File).first { it.exists() }.readText()

    private val activity: String = Regex("""<activity\s+android:name="\.share\.ShareVoiceActivity".*?</activity>""", RegexOption.DOT_MATCHES_ALL)
        .find(manifest)?.value ?: error("ShareVoiceActivity is not declared")

    @Test fun theShareTargetReceivesOnlySingleAudioItems() {
        val actions = Regex("""<action android:name="([^"]+)"""").findAll(activity).map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.intent.action.SEND"), actions)
        val types = Regex("""android:mimeType="([^"]+)"""").findAll(activity).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("audio/*", "application/ogg"), types)
    }

    @Test fun noStoragePermissionIsRequested_soNothingBeyondTheSharedItemCanBeRead() {
        for (permission in listOf("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "READ_MEDIA_AUDIO", "QUERY_ALL_PACKAGES")) {
            assertFalse(permission, manifest.contains(permission))
        }
    }

    @Test fun theShareScreenIsNotAnAccessibilityOrNotificationRoute() {
        assertFalse(activity.contains("BIND_ACCESSIBILITY_SERVICE"))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }
}
