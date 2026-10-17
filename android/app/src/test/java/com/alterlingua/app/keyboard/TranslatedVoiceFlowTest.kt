package com.alterlingua.app.keyboard

import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.entitlements.TRIAL_TTS_SHARES_PER_MONTH
import com.alterlingua.app.entitlements.TtsShareUsageService
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.share.Speaker
import com.alterlingua.app.speak.SpokenAudioFiles
import com.alterlingua.app.testing.FakeUserSettingsRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class FakeSpeaker(var succeeds: Boolean = true) : Speaker {
    val synthesized = mutableListOf<Pair<String, String>>()
    override suspend fun canSpeak(language: Language) = true
    override fun speak(text: String, language: Language, onDone: () -> Unit) = false
    override suspend fun synthesizeToFile(text: String, language: Language, file: File): Boolean {
        synthesized += text to language.code
        if (succeeds) file.writeText("audio")
        return succeeds
    }
    override fun stop() = Unit
    override fun shutdown() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class TranslatedVoiceFlowTest {
    @get:Rule val folder = TemporaryFolder()

    private val speaker = FakeSpeaker()
    private val shared = mutableListOf<Pair<File, String>>()
    private val files by lazy { SpokenAudioFiles(folder.newFolder("spoken_audio")) }

    private fun settings(tier: Tier = Tier.PREMIUM) = FakeUserSettingsRepository(UserSettings(tier = tier))

    private fun flow(prefs: FakeUserSettingsRepository = settings(), scope: kotlinx.coroutines.CoroutineScope) = TranslatedVoiceFlow(
        speaker = speaker,
        files = files,
        entitlements = EntitlementService(prefs),
        usage = TtsShareUsageService(prefs, EntitlementService(prefs)),
        share = { file, type -> shared += file to type },
        scope = scope,
    )

    @Test
    fun aPremiumUser_getsTheVoiceAndItIsShared() = runTest {
        val f = flow(scope = backgroundScope)
        f.speakAndShare("Tu viens demain ?", Languages.French)
        runCurrent()
        assertEquals(listOf("Tu viens demain ?" to "fr"), speaker.synthesized)
        assertEquals(1, shared.size)
        assertEquals(SpokenAudioFiles.AUDIO_TYPE, shared.single().second)
        assertTrue(shared.single().first.exists())
        assertEquals(TranslatedVoiceUiState.Idle, f.state)
    }

    @Test
    fun aFreeUser_underTheTrial_getsTheVoice() = runTest {
        val f = flow(settings(Tier.FREE), backgroundScope)
        f.speakAndShare("hello", Languages.French)
        runCurrent()
        assertEquals(1, shared.size)
    }

    @Test
    fun aFreeUser_atTheMonthlyLimit_isStopped_andNothingIsSynthesized() = runTest {
        val prefs = FakeUserSettingsRepository(UserSettings(ttsShareMonth = currentMonth(), ttsShareUsedThisMonth = TRIAL_TTS_SHARES_PER_MONTH))
        val f = flow(prefs, backgroundScope)
        f.speakAndShare("hello", Languages.French)
        runCurrent()
        assertEquals(TranslatedVoiceUiState.LimitReached(TRIAL_TTS_SHARES_PER_MONTH), f.state)
        assertTrue(speaker.synthesized.isEmpty())
        assertTrue(shared.isEmpty())
    }

    @Test
    fun aStandardUser_isAlsoStoppedAtTheLimit() = runTest {
        val prefs = FakeUserSettingsRepository(UserSettings(tier = Tier.STANDARD, ttsShareMonth = currentMonth(), ttsShareUsedThisMonth = TRIAL_TTS_SHARES_PER_MONTH))
        val f = flow(prefs, backgroundScope)
        f.speakAndShare("hello", Languages.French)
        runCurrent()
        assertTrue(f.state is TranslatedVoiceUiState.LimitReached)
    }

    @Test
    fun aPremiumUser_isNeverStopped_evenFarPastTheTrialCount() = runTest {
        val prefs = FakeUserSettingsRepository(UserSettings(tier = Tier.PREMIUM, ttsShareMonth = currentMonth(), ttsShareUsedThisMonth = 10_000))
        val f = flow(prefs, backgroundScope)
        f.speakAndShare("hello", Languages.French)
        runCurrent()
        assertEquals(1, shared.size)
    }

    @Test
    fun recordingUse_isSkippedWhenSynthesisFails() = runTest {
        speaker.succeeds = false
        val prefs = settings(Tier.FREE)
        val f = flow(prefs, backgroundScope)
        f.speakAndShare("hello", Languages.French)
        runCurrent()
        assertEquals(TranslatedVoiceUiState.Failed, f.state)
        assertEquals(0, prefs.current.ttsShareUsedThisMonth)
        assertTrue(shared.isEmpty())
    }

    @Test
    fun aFailedSynthesis_leavesNoFileBehind() = runTest {
        speaker.succeeds = false
        val f = flow(scope = backgroundScope)
        f.speakAndShare("hello", Languages.French)
        runCurrent()
        assertEquals(0, folder.root.resolve("spoken_audio").listFiles()?.size ?: 0)
    }

    @Test
    fun dismiss_returnsToIdle() = runTest {
        val prefs = FakeUserSettingsRepository(UserSettings(ttsShareMonth = currentMonth(), ttsShareUsedThisMonth = TRIAL_TTS_SHARES_PER_MONTH))
        val f = flow(prefs, backgroundScope)
        f.speakAndShare("hello", Languages.French)
        runCurrent()
        assertTrue(f.state is TranslatedVoiceUiState.LimitReached)
        f.dismiss()
        assertEquals(TranslatedVoiceUiState.Idle, f.state)
    }

    @Test
    fun aSecondTapWhileWorking_isIgnored() = runTest {
        val f = flow(scope = backgroundScope)
        f.speakAndShare("hello", Languages.French)
        f.speakAndShare("hello", Languages.French)
        runCurrent()
        assertEquals(1, speaker.synthesized.size)
    }

    private fun currentMonth(): String = java.time.YearMonth.now().toString()
}
