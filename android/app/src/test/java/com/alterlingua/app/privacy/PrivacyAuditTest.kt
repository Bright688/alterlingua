package com.alterlingua.app.privacy

import com.alterlingua.app.learning.engine.Exposure
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningCandidate
import com.alterlingua.app.learning.engine.LearningEvent
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.engine.Usefulness
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.notifications.IncomingMessage
import com.alterlingua.app.notifications.IncomingSources
import com.alterlingua.app.notifications.NotificationSnapshot
import com.alterlingua.app.notifications.SnapshotMessage
import com.alterlingua.app.notifications.TranslatedConversation
import com.alterlingua.app.notifications.TranslatedLine
import com.alterlingua.app.share.VoiceNoteResult
import com.alterlingua.app.speak.SpokenResult
import com.alterlingua.app.translation.Translation
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import com.alterlingua.app.translation.VoiceResult
import com.alterlingua.app.translation.VoiceTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Findings of the privacy and security audit, kept as tests so they cannot come back. */
class PrivacyAuditTest {
    private val secret = "SECRET please wire 4200 euros to account 9917"

    // ---- crash and log safety: private text never appears when an object is printed ----

    @Test fun objectsThatHoldPrivateText_printWithoutIt() {
        val candidate = LearningCandidate(secret, secret, UnitType.PHRASE, "fr", "en", Usefulness(0.5, emptyList()), Exposure(1, 0, 0))
        val printed = listOf(
            TranslationRequest(text = secret, target = "fr"),
            Translation(secret, "en", "fr"),
            TranslationResult.Success(Translation(secret, "en", "fr")),
            VoiceTranslation("en", secret, "fr", secret),
            VoiceResult.Success(VoiceTranslation("en", secret, "fr", secret)),
            SpokenResult("en", secret, Languages.French, secret, File("x.wav"), "audio/wav"),
            VoiceNoteResult(Languages.French, "fr", secret, Languages.English, secret, false, emptyList(), false, false),
            SnapshotMessage("Marie", secret, 1),
            NotificationSnapshot(packageName = "com.whatsapp", key = "k", title = secret, text = secret, bigText = secret, messages = listOf(SnapshotMessage("Marie", secret, 1))),
            IncomingMessage("Marie", secret, 1),
            TranslatedLine("Marie", secret, "fr"),
            TranslatedConversation("k", "Marie", false, listOf(TranslatedLine("Marie", secret, "fr")), null),
            TranslationInteraction(InteractionKind.INCOMING_MESSAGE, secret, "fr"),
            candidate,
            LearningEvent("e", 0, InteractionKind.INCOMING_MESSAGE, "fr", "en", listOf(candidate)),
        ).map { it.toString() }
        printed.forEach { assertFalse(it, it.contains("SECRET") || it.contains("4200") || it.contains("9917")) }
    }

    @Test fun redactingThePrintedFormDoesNotChangeEquality() {
        assertEquals(Translation("a", "en", "fr"), Translation("a", "en", "fr"))
        assertFalse(Translation("a", "en", "fr") == Translation("b", "en", "fr"))
    }

    // ---- the source code holds no logging, no secrets, no plain-HTTP addresses ----

    private val sourceRoot = listOf("src/main/java", "app/src/main/java").map(::File).first { it.exists() }
    private val debugRoot = listOf("src/debug", "app/src/debug").map(::File).first { it.exists() }
    private fun kotlinFiles(root: File) = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun theAppNeverWritesToTheSystemLog() {
        val banned = Regex("""\b(Log\.[dievwt]\(|println\(|print\(|printStackTrace|Timber\.|Logger\.|StrictMode)""")
        val hits = (kotlinFiles(sourceRoot) + kotlinFiles(debugRoot)).filter { banned.containsMatchIn(it.readText()) }.map { it.name }
        assertEquals(emptyList<String>(), hits)
    }

    @Test fun noSecretShapedStringIsInTheAndroidSources() {
        val shapes = listOf(
            Regex("""sk-[A-Za-z0-9]{20,}"""), Regex("""AIza[0-9A-Za-z_\-]{30,}"""), Regex("""AKIA[0-9A-Z]{16}"""),
            Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----"""), Regex("""(?i)(api[_-]?key|secret|password)\s*[=:]\s*["'][^"'\s]{8,}["']"""),
        )
        val hits = kotlinFiles(sourceRoot).filter { f -> shapes.any { it.containsMatchIn(f.readText()) } }.map { it.name }
        assertEquals(emptyList<String>(), hits)
    }

    @Test fun theReleaseBuildHasNoBackendAddressUnlessOneIsGivenAtBuildTime_andNoPlainHttp() {
        val gradle = listOf("build.gradle.kts", "app/build.gradle.kts").map(::File).first { it.exists() }.readText()
        val release = gradle.substringAfter("release {").substringBefore("}")
        // The release address comes only from a build property whose default is empty; nothing is written into the repository.
        assertTrue(release.contains("TRANSLATION_BASE_URL\", \"\\\"\$releaseBackendUrl\\\"\""))
        assertTrue(gradle.contains("\"alterlingua.releaseBackendUrl\").orElse(\"\")"))
        assertTrue(gradle.contains("\"alterlingua.apiToken\").orElse(\"\")"))
        // Plain HTTP appears only in the debug-only default (the phone's own loopback address).
        val httpLines = gradle.lines().filter { it.contains("http://") }
        assertTrue(httpLines.all { it.contains("127.0.0.1") })
        assertTrue(kotlinFiles(sourceRoot).none { it.readText().contains("\"http://") })
    }

    @Test fun plainHttpIsAllowedOnlyForLoopbackAndOnlyInDebugBuilds() {
        val debugConfig = File(debugRoot, "res/xml/network_security_config.xml").readText()
        val domains = Regex("""<domain[^>]*>([^<]+)</domain>""").findAll(debugConfig).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("127.0.0.1", "localhost", "10.0.2.2"), domains)
        val main = File(sourceRoot.parentFile, "AndroidManifest.xml").readText()
        assertFalse(main.contains("usesCleartextTraffic=\"true\""))
        assertFalse(main.contains("networkSecurityConfig"))
    }

    @Test fun onlyTheNecessaryPermissionsAreRequested() {
        val main = File(sourceRoot.parentFile, "AndroidManifest.xml").readText()
        val permissions = Regex("""<uses-permission android:name="([^"]+)"""").findAll(main).map { it.groupValues[1].substringAfterLast('.') }.toSet()
        // No SYSTEM_ALERT_WINDOW: the live chat captions are drawn as an accessibility overlay, which needs no such permission.
        assertEquals(setOf("RECORD_AUDIO", "INTERNET", "ACCESS_NETWORK_STATE", "POST_NOTIFICATIONS"), permissions)
        assertTrue(main.contains("android:allowBackup=\"false\""))
        assertTrue("the microphone is optional for install", main.contains("android.hardware.microphone\" android:required=\"false\""))
    }

    @Test fun everyExportedComponentIsAccountedFor() {
        val main = File(sourceRoot.parentFile, "AndroidManifest.xml").readText()
        val exported = Regex("""<(activity|service|receiver|provider)\s+android:name="([^"]+)"[^>]*?android:exported="true"""", RegexOption.DOT_MATCHES_ALL)
            .findAll(main).map { it.groupValues[2] }.toList()
        // The launcher screen, the audio Share target, the keyboard, the notification listener and the (optional,
        // off-by-default) accessibility service, each bound by the system only.
        assertEquals(
            listOf(".MainActivity", ".share.ShareVoiceActivity", ".keyboard.AlterLinguaKeyboardService", ".notifications.AlterLinguaNotificationListener", ".accessibility.AlterLinguaAccessibilityService"),
            exported,
        )
        assertTrue(main.contains("android.permission.BIND_INPUT_METHOD"))
        assertTrue(main.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"))
        assertTrue(main.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
    }

    @Test fun theAccessibilityServiceIsScopedToExactlyTheKnownChatApps_andNeverClaimsToBeAnAccessibilityTool() {
        // Deliberate (see docs/build-log.md): reading a chat app's own screen live, not only its notifications,
        // needs AccessibilityService. Scope is minimised at the OS level, not only in code: android:packageNames in
        // accessibility_service_config.xml must list exactly the apps IncomingSources itself knows about — nothing
        // more — so this test breaks if the two lists are ever allowed to drift apart. AlterLingua is a translation
        // feature, not an accessibility tool for people with disabilities, and must never claim to be one: claiming
        // isAccessibilityTool="true" without that being true is exactly the kind of deceptive declaration Google
        // Play's Accessibility API policy explicitly penalises with app suspension or developer account termination.
        val config = File(sourceRoot.parentFile, "res/xml/accessibility_service_config.xml").readText()
        assertFalse(config.contains("isAccessibilityTool=\"true\""))
        val configuredPackages = Regex("""android:packageNames="([^"]+)"""").find(config)!!.groupValues[1].split(',').toSet()
        assertEquals(IncomingSources.knownPackages, configuredPackages)
    }

    @Test fun theNotificationListenerReadsOnlyTheMessagingAppsAndTheTranslatedNotificationIsPrivateOnTheLockScreen() {
        val sources = File(sourceRoot, "com/alterlingua/app/notifications/IncomingModels.kt").readText()
        assertTrue(sources.contains("const val WHATSAPP = \"com.whatsapp\""))
        val presenter = File(sourceRoot, "com/alterlingua/app/notifications/TranslatedNotificationPresenter.kt").readText()
        assertTrue(presenter.contains("VISIBILITY_PRIVATE"))
        assertTrue(presenter.contains("setPublicVersion"))
    }

    @Test fun nothingIsPersistedOutsideTheKnownPrivateStores() {
        // Every place the app writes: two private databases, two DataStores, and the audio cache folders. Nothing on shared storage.
        val writers = Regex("""(getExternalFilesDir|externalCacheDir|Environment\.getExternalStorage|MediaStore|openFileOutput|getSharedPreferences|SharedPreferences)""")
        assertEquals(emptyList<String>(), kotlinFiles(sourceRoot).filter { writers.containsMatchIn(it.readText()) }.map { it.name })
    }
}
