package com.alterlingua.app.capturetest

import android.media.AudioAttributes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureExplainerTest {

    private fun summary(verdict: Verdict, sound: Int = 0, seconds: Int = 20, peak: Double = -96.0) = CaptureSummary(seconds, sound, peak, verdict)

    private fun result(
        verdict: Verdict,
        usages: Set<Int> = emptySet(),
        policies: Set<Int> = emptySet(),
        stopped: Boolean = false,
    ) = CaptureResult("Telegram", summary(verdict, sound = if (verdict == Verdict.HEARD) 6 else 0, peak = if (verdict == Verdict.HEARD) -12.0 else -96.0), usages, policies, stopped)

    @Test
    fun onlyMediaGameAndUnknownPlaybackCanBeCaptured() {
        assertTrue(CaptureExplainer.isCapturable(AudioAttributes.USAGE_MEDIA))
        assertTrue(CaptureExplainer.isCapturable(AudioAttributes.USAGE_UNKNOWN))
        assertTrue(CaptureExplainer.isCapturable(14))
        assertFalse(CaptureExplainer.isCapturable(AudioAttributes.USAGE_VOICE_COMMUNICATION))
        assertFalse(CaptureExplainer.isCapturable(AudioAttributes.USAGE_NOTIFICATION))
        assertFalse(CaptureExplainer.isCapturable(AudioAttributes.USAGE_ALARM))
    }

    @Test
    fun aHeardCapture_saysItWorks_andNamesTheApp() {
        val text = CaptureExplainer.explain(result(Verdict.HEARD, usages = setOf(AudioAttributes.USAGE_MEDIA)))
        assertTrue(text, text.startsWith("Captured"))
        assertTrue(text.contains("Telegram"))
        assertTrue(text.contains("6 of 20 seconds"))
    }

    @Test
    fun silenceWithNoPlaybackReported_saysNothingWasPlaying_orHidden() {
        val text = CaptureExplainer.explain(result(Verdict.SILENT))
        assertTrue(text, text.contains("no playback at all"))
    }

    @Test
    fun silenceWithVoiceCommunicationPlayback_saysThatKindOfSoundCannotBeCaptured() {
        val text = CaptureExplainer.explain(result(Verdict.SILENT, usages = setOf(AudioAttributes.USAGE_VOICE_COMMUNICATION), policies = setOf(1)))
        assertTrue(text, text.contains("VOICE_COMMUNICATION") && text.contains("will not work"))
    }

    @Test
    fun silenceWhereTheAppOptedOut_saysSo() {
        val byNone = CaptureExplainer.explain(result(Verdict.SILENT, usages = setOf(AudioAttributes.USAGE_MEDIA), policies = setOf(3)))
        assertTrue(byNone, byNone.contains("ALLOW_CAPTURE_BY_NONE") && byNone.contains("will not work"))
        val bySystem = CaptureExplainer.explain(result(Verdict.SILENT, usages = setOf(AudioAttributes.USAGE_MEDIA), policies = setOf(2)))
        assertTrue(bySystem, bySystem.contains("ALLOW_CAPTURE_BY_SYSTEM"))
    }

    @Test
    fun silenceDespiteCapturablePlayback_pointsAtTheOtherPossibleCauses() {
        val text = CaptureExplainer.explain(result(Verdict.SILENT, usages = setOf(AudioAttributes.USAGE_MEDIA), policies = setOf(1)))
        assertTrue(text, text.contains("although capturable playback") && text.contains("wrong app"))
    }

    @Test
    fun aMixOfCapturableAndNotCapturablePlayback_isNotBlamedOnTheSoundType() {
        val text = CaptureExplainer.explain(
            result(Verdict.SILENT, usages = setOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_NOTIFICATION), policies = setOf(1)),
        )
        assertTrue(text, text.contains("although capturable playback"))
    }

    @Test
    fun aFaintResult_saysToTryAgainForTheWholeNote() {
        val text = CaptureExplainer.explain(CaptureResult("Signal", summary(Verdict.FAINT, sound = 1, peak = -30.0), emptySet(), emptySet(), false))
        assertTrue(text, text.contains("Only a moment of sound") && text.contains("Try again"))
    }

    @Test
    fun aCaptureStoppedFromTheStatusBar_saysSo() {
        val text = CaptureExplainer.explain(result(Verdict.SILENT, stopped = true))
        assertTrue(text, text.contains("stopped early"))
    }

    @Test
    fun namesAreReadable() {
        assertTrue(CaptureExplainer.usageName(AudioAttributes.USAGE_MEDIA) == "MEDIA")
        assertTrue(CaptureExplainer.usageName(14) == "GAME")
        assertTrue(CaptureExplainer.usageName(999) == "USAGE_999")
        assertTrue(CaptureExplainer.policyName(1) == "ALLOW_CAPTURE_BY_ALL")
    }
}
