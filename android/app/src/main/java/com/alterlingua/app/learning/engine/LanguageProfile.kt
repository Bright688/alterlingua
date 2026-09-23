package com.alterlingua.app.learning.engine

/** The writing system family that decides how text is split into words. */
enum class Script { LATIN, CJK }

/**
 * Everything the engine knows about one language, as data: its function words (by role), elisions, fixed expressions
 * and how its phrases are built. Adding a language means adding a profile, not changing the engine.
 *
 * [functionWordsFollow] is for languages like 日本語 where particles and verb endings come AFTER the content word,
 * unlike French or Español where pronouns, prepositions and articles come before it.
 */
class LanguageProfile(
    val code: String,
    val script: Script,
    functionWords: Map<TokenRole, String>,
    val elisions: Set<String> = emptySet(),
    expressions: Set<String> = emptySet(),
    /** A capitalised word in mid-sentence is probably a name. False for German, which capitalises every noun. */
    val capitalisedIsProperNoun: Boolean = true,
    val functionWordsFollow: Boolean = false,
    /** Hiragana-only tokens of one or two characters are particles and endings (日本語). */
    val shortHiraganaIsFunction: Boolean = false,
) {
    private val roles: Map<String, TokenRole> = buildMap {
        for ((role, words) in functionWords) words.split(' ').filter { it.isNotEmpty() }.forEach { put(it, role) }
    }
    private val expressionSet: Set<String> = expressions

    /** The role of a normalized function word, or null for a content word. */
    fun roleOf(normalized: String): TokenRole? = roles[normalized]

    fun isExpression(normalized: String): Boolean = normalized in expressionSet

    /** Joins tokens the way this language writes them: with spaces, except after an elision apostrophe. */
    fun join(pieces: List<String>): String {
        if (script == Script.CJK) return pieces.joinToString("")
        val out = StringBuilder()
        for (piece in pieces) {
            if (out.isNotEmpty() && !out.endsWith("'")) out.append(' ')
            out.append(piece)
        }
        return out.toString()
    }
}

/** The profiles for the initial languages (CLAUDE.md 6.1). Word lists cover the most common function words. */
object LanguageProfiles {

    val english = LanguageProfile(
        code = "en",
        script = Script.LATIN,
        functionWords = mapOf(
            TokenRole.ARTICLE to "the a an this that these those my your his her its our their some any",
            TokenRole.PRONOUN to "i you he she it we they me him us them i'll you'll he'll she'll we'll they'll i'm you're we're they're i've you've we've they've i'd you'd it's that's there's",
            TokenRole.PREPOSITION to "in on at by for with from to of about before after during until since over under between through into onto without within",
            TokenRole.CONJUNCTION to "and or but so because if when while although than",
            TokenRole.AUXILIARY to "am is are was were be been being have has had do does did will would shall should can could may might must",
            TokenRole.NEGATION to "not no never don't doesn't didn't won't can't isn't aren't wasn't",
            TokenRole.ADVERB to "very too also just still already always here there again really",
        ),
        expressions = setOf("as soon as possible", "by the way", "looking forward to", "no problem", "of course", "see you tomorrow", "let me know", "keep me posted", "thank you very much", "thank you", "good morning"),
    )

    val french = LanguageProfile(
        code = "fr",
        script = Script.LATIN,
        functionWords = mapOf(
            TokenRole.ARTICLE to "le la les l' un une des ce cet cette ces mon ma mes ton ta tes son sa ses notre nos votre vos leur leurs",
            TokenRole.PRONOUN to "je j' tu il elle on nous vous ils elles me m' te t' se s' lui y en moi toi soi eux c' ça ce",
            TokenRole.PREPOSITION to "à au aux de d' du en dans sur sous avant après pendant avec sans pour par chez vers entre depuis jusqu' jusque devant derrière contre",
            TokenRole.CONJUNCTION to "et ou mais donc car ni que qu' si quand comme lorsque puisque parce",
            TokenRole.AUXILIARY to "suis es est sommes êtes sont étais était serai sera ai as a avons avez ont avais avait aurai aura vais vas va allons allez vont irai irez peux peut pouvons pouvez peuvent dois doit devons devez doivent veux veut voulons voulez veulent",
            TokenRole.NEGATION to "ne n' pas plus jamais rien",
            TokenRole.ADVERB to "très trop assez aussi encore déjà toujours ici là",
        ),
        elisions = setOf("l'", "d'", "j'", "n'", "s'", "m'", "t'", "c'", "qu'", "jusqu'"),
        expressions = setOf("je vous tiens au courant", "au courant", "tout à fait", "bien sûr", "à demain", "s'il vous plaît", "s'il te plaît", "de rien", "à bientôt", "bonne journée", "je vous en prie", "en fait", "tout de suite", "pas de problème", "merci beaucoup", "à tout à l'heure"),
    )

    val spanish = LanguageProfile(
        code = "es",
        script = Script.LATIN,
        functionWords = mapOf(
            TokenRole.ARTICLE to "el la los las lo un una unos unas mi mis tu tus su sus nuestro nuestra este esta estos estas ese esa esos esas",
            TokenRole.PRONOUN to "yo tú vos él ella usted nosotros ellos ellas ustedes me te se nos os le les",
            TokenRole.PREPOSITION to "a al de del en con sin por para sobre entre hacia desde hasta antes después durante según tras ante bajo contra",
            TokenRole.CONJUNCTION to "y e o u pero ni que si cuando como porque aunque mientras",
            TokenRole.AUXILIARY to "soy eres es somos son estoy estás está estamos están he has ha hemos han voy vas va vamos van puedo puedes puede podemos pueden debo debe hay",
            TokenRole.NEGATION to "no nunca tampoco nada",
            TokenRole.ADVERB to "muy más menos también ya aún todavía siempre aquí allí",
        ),
        expressions = setOf("por favor", "de nada", "hasta mañana", "hasta luego", "buenos días", "buenas tardes", "por supuesto", "te mantendré informado", "de acuerdo", "no hay problema", "sin embargo", "muchas gracias"),
    )

    val german = LanguageProfile(
        code = "de",
        script = Script.LATIN,
        functionWords = mapOf(
            TokenRole.ARTICLE to "der die das den dem des ein eine einen einem einer eines mein meine meinen meinem meiner dein deine sein seine ihr ihre unser unsere kein keine keinen",
            TokenRole.PRONOUN to "ich du er sie es wir ihr mich dich ihn uns euch ihnen mir dir ihm man",
            TokenRole.PREPOSITION to "in im an am auf aus bei mit nach von vom vor zu zum zur über unter durch für gegen ohne um bis seit während wegen ab",
            TokenRole.CONJUNCTION to "und oder aber denn dass ob wenn als weil obwohl sondern",
            TokenRole.AUXILIARY to "bin bist ist sind seid war waren habe hast hat haben habt hatte werde wirst wird werden kann kannst können muss musst müssen will willst wollen soll sollst sollen darf dürfen möchte möchten",
            TokenRole.NEGATION to "nicht nie nichts",
            TokenRole.ADVERB to "sehr auch noch schon immer hier dort wieder nur",
        ),
        expressions = setOf("auf dem laufenden", "bis morgen", "vielen dank", "bitte schön", "kein problem", "auf wiedersehen", "zum beispiel", "guten morgen", "vor mittag"),
        capitalisedIsProperNoun = false,
    )

    val italian = LanguageProfile(
        code = "it",
        script = Script.LATIN,
        functionWords = mapOf(
            TokenRole.ARTICLE to "il lo la l' i gli le un uno una un'",
            TokenRole.PRONOUN to "io tu lui lei noi voi loro mi ti ci vi si ne c'",
            TokenRole.PREPOSITION to "a ad di da in con su per tra fra al allo alla ai agli alle del dello della dei degli delle dal dallo dalla nel nello nella sul sullo sulla prima dopo senza sotto sopra verso dell' all' nell' sull' dall' d'",
            TokenRole.CONJUNCTION to "e ed o ma però che se quando come perché",
            TokenRole.AUXILIARY to "sono sei è siamo siete ho hai ha abbiamo avete hanno posso puoi può possiamo possono devo deve voglio vuole vado va vanno",
            TokenRole.NEGATION to "non mai niente nulla",
            TokenRole.ADVERB to "molto più meno anche già ancora sempre qui là",
        ),
        elisions = setOf("l'", "un'", "dell'", "all'", "nell'", "sull'", "dall'", "c'", "d'"),
        expressions = setOf("per favore", "a domani", "di niente", "non c'è problema", "buona giornata", "grazie mille", "a presto", "certo che sì"),
    )

    val dutch = LanguageProfile(
        code = "nl",
        script = Script.LATIN,
        functionWords = mapOf(
            TokenRole.ARTICLE to "de het een 't",
            TokenRole.PRONOUN to "ik jij je u hij zij ze wij we jullie hen hun mij me jou ons",
            TokenRole.PREPOSITION to "in op aan met van voor vóór naar bij uit door over onder tot om tegen zonder na tijdens sinds",
            TokenRole.CONJUNCTION to "en of maar dat als omdat want toen terwijl",
            TokenRole.AUXILIARY to "ben bent is zijn was waren heb hebt heeft hebben had zal zult zullen kan kunt kunnen moet moeten wil wilt willen mag mogen ga gaat gaan",
            TokenRole.NEGATION to "niet geen nooit niets",
            TokenRole.ADVERB to "heel erg ook nog al altijd hier daar weer",
        ),
        expressions = setOf("alstublieft", "tot morgen", "geen probleem", "graag gedaan", "goedemorgen", "hartelijk bedankt", "tot ziens", "wat leuk"),
    )

    val chinese = LanguageProfile(
        code = "zh",
        script = Script.CJK,
        functionWords = mapOf(
            TokenRole.PRONOUN to "我 你 他 她 它 我们 你们 他们 她们 您 咱们 大家",
            TokenRole.PARTICLE to "的 了 吗 呢 吧 啊 呀 嘛 么 着 过 哦 啦",
            TokenRole.PREPOSITION to "在 从 向 对 给 把 被 跟 往 到 用 为 关于 除了",
            TokenRole.CONJUNCTION to "和 与 或 或者 但 但是 因为 所以 如果 而 并 而且 虽然 可是",
            TokenRole.AUXILIARY to "是 会 能 要 可以 应该 想 可能 得 将 该",
            TokenRole.NEGATION to "不 没 没有 别 未",
            TokenRole.ARTICLE to "这 那 这个 那个 这些 那些 一 个 些 每",
            TokenRole.ADVERB to "很 也 都 就 还 才 又 再 太 更 最 已经 正在 非常 只 刚",
        ),
        expressions = setOf("没问题", "不客气", "明天见", "谢谢", "对不起", "请问", "没关系", "早上好", "晚安", "你好", "再见", "不好意思", "辛苦了", "没事"),
    )

    val japanese = LanguageProfile(
        code = "ja",
        script = Script.CJK,
        functionWords = mapOf(
            TokenRole.PARTICLE to "は が を に で と の も へ や か ね よ から まで より など って だけ しか ので のに けど",
            TokenRole.AUXILIARY to "し です でした ます ました ません ませんでした でしょう ましょう たい たかった ない なかった だ だった た て いる いた います いました ある あった あります ありました する して した れる られる せる ください",
            TokenRole.PRONOUN to "私 僕 俺 あなた 君 彼 彼女 これ それ あれ ここ そこ あそこ どこ 誰 何",
            TokenRole.CONJUNCTION to "そして しかし でも だから それで ですが また",
            TokenRole.ADVERB to "とても もう まだ すぐ よく たくさん ちょっと",
            TokenRole.ARTICLE to "この その あの",
        ),
        expressions = setOf("よろしくお願いします", "お願いします", "ありがとうございます", "ありがとうございました", "すみません", "大丈夫です", "お疲れ様です", "ごめんなさい", "わかりました", "いただきます", "おはようございます", "こんにちは", "こんばんは", "お待ちください"),
        functionWordsFollow = true,
        shortHiraganaIsFunction = true,
    )

    val all: List<LanguageProfile> = listOf(english, french, spanish, german, italian, dutch, chinese, japanese)
}
