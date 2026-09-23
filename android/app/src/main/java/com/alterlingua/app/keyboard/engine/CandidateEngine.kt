package com.alterlingua.app.keyboard.engine

/** What the user has typed so far and the words the engine offers for it (for example "nihao" and 你好, 你, 尼). */
data class Composition(
    /** What to show as typed (for pinyin, the syllables split: "ni hao"). */
    val preedit: String = "",
    val candidates: List<String> = emptyList(),
    /** What was actually typed ("nihao"); this is what Enter keeps. Defaults to [preedit]. */
    val typed: String = preedit,
) {
    val isEmpty: Boolean get() = preedit.isEmpty() && typed.isEmpty()
}

/** The result of choosing a candidate: text to put in the field, and what is still being typed (if the engine converted only part). */
data class Choice(val committed: String, val remaining: Composition = Composition())

/**
 * A conversion engine for languages typed through an editor (pinyin for 中文, kana for 日本語). Everything runs on the phone:
 * implementations must not log or send what is typed. The real engines (Mozc, librime) sit behind this so the keyboard,
 * and its tests, do not depend on native code.
 */
interface CandidateEngine {
    /** Adds typed input (a letter, or a kana) to the composition and returns the new state. */
    fun append(input: String): Composition

    /** Removes the last typed input. */
    fun deleteLast(): Composition

    /** Picks candidate [index] of the current composition. */
    fun choose(index: Int): Choice

    /** Forgets the current composition. */
    fun reset()
}
