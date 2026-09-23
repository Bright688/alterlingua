package com.alterlingua.app.keyboard

import com.alterlingua.app.learning.KeyboardStyle
import java.util.Locale

/** Which set of keys is showing. */
enum class KeyboardPage { LETTERS, SYMBOLS_1, SYMBOLS_2 }

/** Shift: off, capital for the next letter only, or caps lock. */
enum class ShiftState { OFF, ONCE, CAPS_LOCK }

/** What the keyboard is showing right now. */
data class KeyboardState(
    val page: KeyboardPage = KeyboardPage.LETTERS,
    val shift: ShiftState = ShiftState.OFF,
)

/** The keys that do something other than type a character. HANDWRITING opens the drawing pad (handled by the keyboard service). */
enum class KeyAction { SHIFT, BACKSPACE, ENTER, SPACE, SHOW_LETTERS, SHOW_SYMBOLS, SHOW_MORE_SYMBOLS, SWITCH_KEYBOARD, HANDWRITING }

/** One key. [weight] is its share of the row width; a normal letter key is 1. */
sealed interface KeySpec {
    val weight: Float

    /** True for keys that add or remove text (not shift, page or keyboard switches). */
    val changesText: Boolean
        get() = when (this) {
            is Character -> true
            is Function -> action == KeyAction.SPACE || action == KeyAction.BACKSPACE || action == KeyAction.ENTER
            is Spacer -> false
        }

    /**
     * Types [text]. On the letters page it is shown and typed in capitals while shift is on. [alternates] are the
     * accented forms offered when the key is held (for example é and è on e).
     */
    data class Character(
        val text: String,
        override val weight: Float = 1f,
        val alternates: List<String> = emptyList(),
        /** What the key shows when that differs from what it types (a stroke or a bopomofo symbol on a key that types a letter for the engine). */
        val label: String? = null,
        /** Typed by flicking the key left, up, right or down (the kana keyboard); empty for most keys. */
        val flick: List<String> = emptyList(),
    ) : KeySpec {
        fun typed(state: KeyboardState): String =
            if (state.page == KeyboardPage.LETTERS && state.shift != ShiftState.OFF) capital(text) else text

        companion object {
            /** The capital form. "ß" has no one-letter capital in [String.uppercase] ("SS"), so the capital sharp s is used. */
            fun capital(text: String): String = if (text == "ß") "\u1E9E" else text.uppercase(Locale.ROOT)
        }
    }

    data class Function(val action: KeyAction, override val weight: Float) : KeySpec

    /** An empty gap, used to centre the middle letter row. */
    data class Spacer(override val weight: Float) : KeySpec
}

/**
 * The key layouts. Letters follow the language the user types in (their own language, chosen in onboarding):
 * QWERTY, AZERTY for Français, QWERTZ for Deutsch, and Español with its own ñ key. Accented letters are on long press.
 * The digits are 0-9 in every supported language. Language-specific punctuation (¿ ¡ « » „ “) is on the second
 * symbol page. Every row adds up to a weight of 10.
 *
 * 日本語 has a 12-key kana layout (converted by the Mozc engine, see keyboard/engine) or romaji. 中文 has three: stroke keys (the
 * default), Zhuyin (bopomofo) keys, or pinyin on QWERTY letters; all are converted by the librime engine. The keys of the stroke and
 * Zhuyin layouts show Chinese symbols and type the letters the engine expects.
 */
object KeyboardLayouts {
    private const val ROW_WEIGHT = 10f

    private fun fn(action: KeyAction, weight: Float) = KeySpec.Function(action, weight)

    /** The bottom row. 中文 uses the full-width comma and full stop (，。) beside the space bar. */
    private fun bottomRow(firstKey: KeyAction, language: String = "en") = listOf(
        fn(firstKey, 1.3f),
        fn(KeyAction.SWITCH_KEYBOARD, 1f),
        fn(KeyAction.HANDWRITING, 1f),
        KeySpec.Character(when (language) { "zh" -> "，"; "ja" -> "、"; else -> "," }),
        fn(KeyAction.SPACE, 3.2f),
        KeySpec.Character(if (language == "zh" || language == "ja") "。" else "."),
        fn(KeyAction.ENTER, 1.5f),
    )

    /** Accented forms per base letter, by language. Lowercase; capitals follow shift. */
    private val alternates: Map<String, Map<Char, String>> = mapOf(
        "fr" to mapOf('a' to "àâæ", 'e' to "éèêë", 'i' to "îï", 'o' to "ôœ", 'u' to "ùûü", 'c' to "ç", 'y' to "ÿ"),
        "es" to mapOf('a' to "á", 'e' to "é", 'i' to "í", 'o' to "ó", 'u' to "úü"),
        "it" to mapOf('a' to "àá", 'e' to "èé", 'i' to "ìí", 'o' to "òó", 'u' to "ùú"),
        "nl" to mapOf('a' to "áàäâ", 'e' to "éèëê", 'i' to "íïî", 'o' to "óöô", 'u' to "úüû"),
    )

    /** Letter keys that share the width left over by [fixed] equally. */
    private fun keys(language: String, text: String, fixed: List<KeySpec>): List<KeySpec> {
        val weight = (ROW_WEIGHT - fixed.sumOf { it.weight.toDouble() }.toFloat()) / text.length
        val forms = alternates[language].orEmpty()
        val letters = text.map { KeySpec.Character(it.toString(), weight, forms[it].orEmpty().map(Char::toString)) }
        return letters
    }

    private fun row(language: String, text: String, before: List<KeySpec> = emptyList(), after: List<KeySpec> = emptyList()) =
        before + keys(language, text, before + after) + after

    /** The 12-key kana layout used for 日本語: each key is the first kana of a row; holding it offers the rest of the row, the small forms and voiced forms. */
    private val kanaForms = mapOf(
        "あ" to "あいうえお ぁぃぅぇぉ", "か" to "かきくけこ がぎぐげご", "さ" to "さしすせそ ざじずぜぞ",
        "た" to "たちつてと っ だぢづでど", "な" to "なにぬねの", "は" to "はひふへほ ばびぶべぼ ぱぴぷぺぽ",
        "ま" to "まみむめも", "や" to "やゆよ ゃゅょ", "ら" to "らりるれろ", "わ" to "わをんー ゎ", "、" to "、。？！・…",
    )

    /** Flick directions, in the order left, up, right, down (the row's second to fifth kana). */
    private val kanaFlick = mapOf(
        "あ" to "いうえお", "か" to "きくけこ", "さ" to "しすせそ", "た" to "ちつてと", "な" to "にぬねの", "は" to "ひふへほ",
        "ま" to "みむめも", "や" to "（ゆ）よ", "ら" to "りるれろ", "わ" to "をんー〜", "、" to "。？！…",
    )

    private fun kana(key: String, weight: Float) = KeySpec.Character(
        key, weight,
        alternates = kanaForms.getValue(key).filter { it != ' ' }.map { it.toString() },
        flick = kanaFlick.getValue(key).map { it.toString() },
    )

    private fun kanaRows() = listOf(
        listOf(kana("あ", 2.5f), kana("か", 2.5f), kana("さ", 2.5f), fn(KeyAction.BACKSPACE, 2.5f)),
        listOf(kana("た", 2.5f), kana("な", 2.5f), kana("は", 2.5f), fn(KeyAction.SPACE, 2.5f)),
        listOf(kana("ま", 2.5f), kana("や", 2.5f), kana("ら", 2.5f), fn(KeyAction.ENTER, 2.5f)),
        listOf(fn(KeyAction.SHOW_SYMBOLS, 2f), fn(KeyAction.SWITCH_KEYBOARD, 1.2f), fn(KeyAction.HANDWRITING, 1.3f), kana("わ", 2.5f), kana("、", 1.5f), KeySpec.Character("。", 1.5f)),
    )

    private fun symbolKey(label: String, typed: String, weight: Float) = KeySpec.Character(typed, weight, label = label)

    /** 中文 stroke input: the five strokes (一 丨 丿 丶 乙) are the keys, typed to the engine as h s p n z, plus Chinese punctuation. */
    private fun strokeRows() = listOf(
        listOf(symbolKey("⼀", "h", 2.5f), symbolKey("⼁", "s", 2.5f), symbolKey("⼃", "p", 2.5f), fn(KeyAction.BACKSPACE, 2.5f)),
        listOf(symbolKey("⼂", "n", 2.5f), symbolKey("⼄", "z", 2.5f), KeySpec.Character("，", 2.5f), KeySpec.Character("。", 2.5f)),
        listOf(KeySpec.Character("？", 2.5f), KeySpec.Character("！", 2.5f), KeySpec.Character("、", 2.5f), KeySpec.Character("：", 2.5f)),
        listOf(fn(KeyAction.SHOW_SYMBOLS, 1.8f), fn(KeyAction.SWITCH_KEYBOARD, 1.2f), fn(KeyAction.HANDWRITING, 1.2f), fn(KeyAction.SPACE, 3.3f), fn(KeyAction.ENTER, 2.5f)),
    )

    /** The bopomofo symbols and the standard (Daqian) keys that type them, row by row; the tone marks are left out (see the schema). */
    private val zhuyinKeys = listOf(
        listOf("ㄅ" to "1", "ㄉ" to "2", "ㄓ" to "5", "ㄚ" to "8", "ㄞ" to "9", "ㄢ" to "0", "ㄦ" to "-"),
        listOf("ㄆ" to "q", "ㄊ" to "w", "ㄍ" to "e", "ㄐ" to "r", "ㄔ" to "t", "ㄗ" to "y", "ㄧ" to "u", "ㄛ" to "i", "ㄟ" to "o", "ㄣ" to "p"),
        listOf("ㄇ" to "a", "ㄋ" to "s", "ㄎ" to "d", "ㄑ" to "f", "ㄕ" to "g", "ㄘ" to "h", "ㄨ" to "j", "ㄜ" to "k", "ㄠ" to "l", "ㄤ" to ";"),
        listOf("ㄈ" to "z", "ㄌ" to "x", "ㄏ" to "c", "ㄒ" to "v", "ㄖ" to "b", "ㄙ" to "n", "ㄩ" to "m", "ㄝ" to ",", "ㄡ" to ".", "ㄥ" to "/"),
    )

    private fun zhuyinRows(): List<List<KeySpec>> {
        val letters = zhuyinKeys.map { row -> row.map { (symbol, typed) -> symbolKey(symbol, typed, ROW_WEIGHT / row.size) } }
        return letters + listOf(
            listOf(
                fn(KeyAction.SHOW_SYMBOLS, 1.3f), fn(KeyAction.SWITCH_KEYBOARD, 1f), fn(KeyAction.HANDWRITING, 1f), KeySpec.Character("，", 0.9f),
                fn(KeyAction.SPACE, 3.4f), KeySpec.Character("。", 0.9f), fn(KeyAction.BACKSPACE, 1.5f),
            ),
        )
    }

    private fun lettersFor(language: String, style: KeyboardStyle?): List<List<KeySpec>> {
        if (language == "zh" && style == KeyboardStyle.STROKE) return strokeRows()
        if (language == "zh" && style == KeyboardStyle.ZHUYIN) return zhuyinRows()
        if (language == "ja" && style != KeyboardStyle.ROMAJI) return kanaRows()
        val shift = listOf(fn(KeyAction.SHIFT, 1.5f))
        val back = listOf(fn(KeyAction.BACKSPACE, 1.5f))
        val (top, middle, bottom) = when (language) {
            "fr" -> Triple(row(language, "azertyuiop"), row(language, "qsdfghjklm"), row(language, "wxcvbn'", shift, back))
            "de" -> Triple(row(language, "qwertzuiopü"), row(language, "asdfghjklöä"), row(language, "yxcvbnmß", shift, back))
            "es" -> Triple(row(language, "qwertyuiop"), row(language, "asdfghjklñ"), row(language, "zxcvbnm", shift, back))
            else -> Triple(
                row(language, "qwertyuiop"),
                row(language, "asdfghjkl", listOf(KeySpec.Spacer(0.5f)), listOf(KeySpec.Spacer(0.5f))),
                row(language, "zxcvbnm", shift, back),
            )
        }
        return listOf(top, middle, bottom, bottomRow(KeyAction.SHOW_SYMBOLS, language))
    }

    private fun chars(text: String) = text.map { KeySpec.Character(it.toString()) }

    /** The everyday currency sign on the first symbol page: $ for English, ¥ for 中文 and 日本語, € for the rest. */
    private fun currency(language: String) = when (language) {
        "en" -> "$"
        "zh", "ja" -> "¥"
        else -> "€"
    }

    private fun symbols1(language: String) = listOf(
        chars("1234567890"),
        chars("@#" + currency(language) + "_&-+(/)"),
        // 中文 has its own quotation marks, colon, semicolon, exclamation and question marks (full-width).
        listOf(fn(KeyAction.SHOW_MORE_SYMBOLS, 1.5f)) + chars(if (language == "zh") "、“”：；！？" else "*\"':;!?") + fn(KeyAction.BACKSPACE, 1.5f),
        bottomRow(KeyAction.SHOW_LETTERS, language),
    )

    /** Two punctuation marks that only some languages use, placed at the end of the first row of the second page. */
    private fun extraPunctuation(language: String) = when (language) {
        "es" -> "¿¡"
        "fr" -> "«»"
        "de" -> "„“"
        "ja" -> "「」"
        "zh" -> "《》"
        else -> "¶∆"
    }

    private fun symbols2(language: String) = listOf(
        chars("~`|•√π÷×" + extraPunctuation(language)),
        chars("£¢€¥^°={}\\"),
        listOf(fn(KeyAction.SHOW_SYMBOLS, 1.5f)) + chars("%©®™[]<") + fn(KeyAction.BACKSPACE, 1.5f),
        bottomRow(KeyAction.SHOW_LETTERS, language),
    )

    /**
     * The language's own characters, as a slim row above the keys on every page: accented letters and its own punctuation for the
     * Latin-script languages, and the marks each of 中文 and 日本語 use most. English has none. This is what makes each language's
     * keyboard visibly its own, beyond where the letters sit.
     */
    private val extras = mapOf(
        "fr" to "éèêàùçôîïœ",
        "es" to "áéíóúüñ¿¡€",
        "de" to "€„“‚‘»«–§°",
        "it" to "àèéìòùóá€«",
        "nl" to "éëïóöüáè€’",
        "zh" to "，。？！、：；“”（",
        "ja" to "「」ー〜・…？！（）",
    )

    /** True when the keyboard for [language] in [style] has the extra row of its own characters (the stroke and Zhuyin keyboards carry their own marks). */
    fun hasExtras(language: String, style: KeyboardStyle? = null): Boolean =
        language in extras && !(language == "zh" && resolve(language, style).let { it == KeyboardStyle.STROKE || it == KeyboardStyle.ZHUYIN })

    /** [style] if given, otherwise the language's default style (null for languages with one way of typing). */
    private fun resolve(language: String, style: KeyboardStyle?): KeyboardStyle? =
        style ?: KeyboardStyle.forLanguage(language).firstOrNull()

    private fun extrasRow(language: String): List<KeySpec> {
        val text = extras.getValue(language)
        return text.map { KeySpec.Character(it.toString(), ROW_WEIGHT / text.length) }
    }

    /** The rows of [page] for someone typing in [language] (a language code such as "fr"). Unknown codes use QWERTY. */
    fun rows(page: KeyboardPage, language: String = "en", style: KeyboardStyle? = null): List<List<KeySpec>> {
        val style = resolve(language, style)
        val page = when (page) {
            KeyboardPage.LETTERS -> lettersFor(language, style)
            KeyboardPage.SYMBOLS_1 -> symbols1(language)
            KeyboardPage.SYMBOLS_2 -> symbols2(language)
        }
        return if (hasExtras(language, style)) listOf(extrasRow(language)) + page else page
    }
}
