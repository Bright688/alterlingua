package com.alterlingua.app.capturetest

import android.media.AudioAttributes

/** What Android reported was playing while the capture ran, by kind only (never which app or what was said). */
data class CaptureResult(
    val appLabel: String,
    val summary: CaptureSummary,
    /** Audio usage codes seen playing (AudioAttributes.USAGE_*). */
    val usagesSeen: Set<Int>,
    /** Capture policy codes seen on those players (1 = anyone, 2 = system only, 3 = nobody). */
    val policiesSeen: Set<Int>,
    /** The user ended the projection (from the status bar or Quick Settings) before the time was up. */
    val stoppedBySystem: Boolean,
)

/** Turns a capture result into a plain sentence that says what it means for voice notes in that app. */
object CaptureExplainer {
    /** The only usages another app is allowed to capture (Android's documentation for playback capture). */
    private val capturable = setOf(
        AudioAttributes.USAGE_UNKNOWN,
        AudioAttributes.USAGE_MEDIA,
        AudioAttributes.USAGE_GAME,
    )

    fun usageName(usage: Int): String = when (usage) {
        AudioAttributes.USAGE_UNKNOWN -> "UNKNOWN"
        AudioAttributes.USAGE_MEDIA -> "MEDIA"
        AudioAttributes.USAGE_VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        3 -> "VOICE_COMMUNICATION_SIGNALLING"
        AudioAttributes.USAGE_ALARM -> "ALARM"
        AudioAttributes.USAGE_NOTIFICATION -> "NOTIFICATION"
        6 -> "NOTIFICATION_RINGTONE"
        7 -> "NOTIFICATION_COMMUNICATION_REQUEST"
        11 -> "ASSISTANCE_ACCESSIBILITY"
        12 -> "ASSISTANCE_NAVIGATION_GUIDANCE"
        13 -> "ASSISTANCE_SONIFICATION"
        14 -> "GAME"
        16 -> "ASSISTANT"
        else -> "USAGE_$usage"
    }

    fun isCapturable(usage: Int): Boolean = usage in capturable

    fun policyName(policy: Int): String = when (policy) {
        1 -> "ALLOW_CAPTURE_BY_ALL"
        2 -> "ALLOW_CAPTURE_BY_SYSTEM (other apps cannot capture)"
        3 -> "ALLOW_CAPTURE_BY_NONE (nobody can capture)"
        else -> "policy $policy"
    }

    fun explain(result: CaptureResult): String {
        val s = result.summary
        val heard = "${s.secondsWithSound} of ${s.seconds} seconds had sound, loudest ${"%.0f".format(s.peakDb)} dBFS"
        val stopped = if (result.stoppedBySystem) " Note: the capture was stopped early from the system's status bar or Quick Settings." else ""
        return when (s.verdict) {
            Verdict.HEARD -> "Captured: sound from ${result.appLabel} came through ($heard). For this app a voice note can be recorded by playing it.$stopped"
            Verdict.FAINT -> "Only a moment of sound came through ($heard). Either a very short note played, or the capture is being partly blocked. Try again and let a voice note play for its full length.$stopped"
            Verdict.SILENT -> silentReason(result, heard) + stopped
        }
    }

    private fun silentReason(result: CaptureResult, heard: String): String {
        val name = result.appLabel
        val blockedUsages = result.usagesSeen.filterNot(::isCapturable)
        return when {
            result.usagesSeen.isEmpty() ->
                "Nothing was captured ($heard) and Android reported no playback at all. Either no voice note was playing while the test ran, " +
                    "or Android hides that playback from other apps. Start the test, then play a voice note in $name straight away."
            result.policiesSeen.any { it == 2 || it == 3 } ->
                "Nothing was captured ($heard): the playing app has told Android that other apps may not capture its sound " +
                    "(${result.policiesSeen.filter { it == 2 || it == 3 }.joinToString { policyName(it) }}). Capturing voice notes from $name will not work."
            blockedUsages.isNotEmpty() && result.usagesSeen.none(::isCapturable) ->
                "Nothing was captured ($heard): playback was reported as ${blockedUsages.joinToString { usageName(it) }}, " +
                    "a sound type Android does not let other apps capture. Capturing voice notes from $name will not work."
            else ->
                "Nothing was captured ($heard) although capturable playback (${result.usagesSeen.joinToString { usageName(it) }}) was reported. " +
                    "The app may block capture in another way, this phone's Android build may restrict it, or the wrong app was chosen."
        }
    }
}
