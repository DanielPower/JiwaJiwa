package ca.danielpower.jstudy.core

/** A grammar pattern the reader can explain, such as ～てしまう or the particle は. */
data class GrammarPoint(
    val id: String,
    /** As textbooks write it, e.g. "～てしまう". */
    val pattern: String,
    /** JLPT level, 5 (N5) to 1 (N1), following the common community lists. */
    val level: Int,
    /** A few words of English. */
    val meaning: String,
    /** How it's used, in a sentence or two. */
    val note: String,
    val example: String,
    val exampleMeaning: String,
    /**
     * False for a point no sentence can be searched for, because the reader
     * reads it as words (三本, いる): only its topic's lesson explains it, and
     * it's learned by reading that.
     */
    val inText: Boolean = true,
)

/**
 * A grammar point found in a paragraph, covering the words [first] to [last]
 * (indexes into the paragraph). Inside them it starts [from] characters into
 * its first word and ends [to] characters into its last (null: at its end),
 * so the たら of 勝ったら is 2 until the end of 勝ったら.
 */
data class GrammarUse(val point: GrammarPoint, val first: Int, val last: Int, val from: Int = 0, val to: Int? = null)

/**
 * Finds grammar points in tokenized text. Each point has one or more token
 * patterns; where matches overlap, one that lies inside a longer match is
 * dropped, so 食べてはいけない shows ～てはいけない rather than the particle は.
 */
internal object Grammar {

    private val ENDINGS = setOf("ない", "ぬ", "ん", "ます", "た", "です", "だ")

    /** One token in a pattern. */
    class Step(val optional: Boolean, val test: (Morpheme) -> Boolean)

    /**
     * A token pattern for [point]. [before], when set, must hold for the token
     * just before the match, and [after] for the token just after it (null at
     * the end).
     */
    class Rule(val point: GrammarPoint, val steps: List<Step>, val before: ((Morpheme) -> Boolean)?, val after: ((Morpheme?) -> Boolean)? = null)

    /** A match over tokens [start] until [end], [core] of them matched by the pattern itself rather than trailing endings. */
    data class Match(val point: GrammarPoint, val start: Int, val end: Int, val core: Int = end - start)

    fun find(tokens: List<Morpheme>): List<Match> {
        val found = ArrayList<Match>()
        for (start in tokens.indices) {
            for (rule in ordered) {
                if (rule.before != null && (start == 0 || !rule.before.invoke(tokens[start - 1]))) continue
                var end = match(rule.steps, 0, tokens, start)
                if (end <= start) continue
                if (rule.after != null && !rule.after.invoke(tokens.getOrNull(end))) continue
                val core = end - start
                // Take in the endings that follow (いけ + ない, つもり + です) so the whole phrase lights up,
                // but not a conditional (たら, なきゃ), which is a point of its own.
                while (end < tokens.size && tokens[end].pos == "助動詞" && tokens[end].base in ENDINGS && !tokens[end].form.startsWith("仮定")) end++
                found += Match(rule.point, start, end, core)
            }
        }
        val distinct = found.distinctBy { Triple(it.point.id, it.start, it.end) }
        // Of two matches over the same tokens, the one whose pattern matched more of them wins: ～てた is ～ている, not ～て with た.
        return distinct.filter { m ->
            distinct.none { o -> o !== m && o.start <= m.start && o.end >= m.end && (o.end - o.start) > (m.end - m.start) }
        }.sortedWith(compareBy({ it.start }, { -it.end }, { -it.core })).distinctBy { it.start to it.end }
    }

    /** Where the match of [steps] from [s] ends, or -1. Optional steps are tried taken first. */
    private fun match(steps: List<Step>, s: Int, tokens: List<Morpheme>, at: Int): Int {
        if (s == steps.size) return at
        val step = steps[s]
        if (at < tokens.size && step.test(tokens[at])) {
            val end = match(steps, s + 1, tokens, at + 1)
            if (end >= 0) return end
        }
        return if (step.optional) match(steps, s + 1, tokens, at) else -1
    }

    // Token tests.

    private fun base(vararg bases: String) = Step(false) { it.base in bases }
    private fun particle(vararg bases: String, pos2: String? = null) =
        Step(false) { it.pos == "助詞" && it.base in bases && (pos2 == null || it.pos2 == pos2) }
    private fun aux(vararg bases: String) = Step(false) { it.pos == "助動詞" && it.base in bases }
    private fun dependentNoun(vararg bases: String) = Step(false) { it.pos == "名詞" && it.pos2 == "非自立" && it.base in bases }
    private fun dependentVerb(vararg bases: String) = Step(false) { it.pos == "動詞" && it.pos2 == "非自立" && it.base in bases }
    private fun Step.optional() = Step(true, test)

    private val te = Step(false) { it.pos == "助詞" && it.pos2 == "接続助詞" && it.base in setOf("て", "で") }
    private val past = Step(false) { it.pos == "助動詞" && it.type == "特殊・タ" && it.form == "基本形" }
    private val good = Step(false) { it.pos == "形容詞" && it.base in setOf("いい", "よい", "良い") }
    private val yoo = Step(false) { it.pos == "名詞" && it.pos3 == "助動詞語幹" && it.base == "よう" }
    private val plainVerb: (Morpheme) -> Boolean = { it.pos == "動詞" && it.form == "基本形" }

    private val rules = ArrayList<Rule>()

    // A guarded rule is the more specific reading of its tokens, so it wins a tie.
    private val ordered by lazy { rules.sortedByDescending { it.before != null } }

    private fun point(
        id: String,
        pattern: String,
        level: Int,
        meaning: String,
        note: String,
        example: String,
        exampleMeaning: String,
        vararg variants: List<Step>,
        before: ((Morpheme) -> Boolean)? = null,
        after: ((Morpheme?) -> Boolean)? = null,
    ) {
        val p = GrammarPoint(id, pattern, level, meaning, note, example, exampleMeaning)
        for (steps in variants) rules += Rule(p, steps, before, after)
    }

    private val lessonOnly = ArrayList<GrammarPoint>()

    /** A point with no token pattern (see [GrammarPoint.inText]). */
    private fun lesson(id: String, pattern: String, level: Int, meaning: String, note: String, example: String, exampleMeaning: String) {
        lessonOnly += GrammarPoint(id, pattern, level, meaning, note, example, exampleMeaning, inText = false)
    }

    /** Another pattern for the point [id], with its own [before]. */
    private fun variant(id: String, steps: List<Step>, before: ((Morpheme) -> Boolean)?, after: ((Morpheme?) -> Boolean)? = null) {
        rules += Rule(rules.first { it.point.id == id }.point, steps, before, after)
    }

    init {
        // Particles.
        point(
            "wa", "は", 5, "topic marker",
            "Marks what the sentence is about: \"as for X…\". Written は but read わ. It often sets up a contrast with something else.",
            "私は学生です。", "I'm a student.",
            listOf(particle("は", pos2 = "係助詞")),
        )
        point(
            "ga", "が", 5, "subject marker",
            "Marks who or what does the action or has the quality. Used for new information and with words like 好き, ある and いる.",
            "猫がいます。", "There's a cat.",
            listOf(particle("が", pos2 = "格助詞")),
        )
        point(
            "ga-but", "～が", 5, "but",
            "After a full clause, が joins it to the next one with \"but\". A little more formal than けど.",
            "高いですが、おいしいです。", "It's expensive, but it's good.",
            listOf(particle("が", pos2 = "接続助詞")),
        )
        // The tokenizer often calls this が a case particle; after a verb, adjective or だ it can only be "but".
        variant("ga-but", listOf(particle("が", pos2 = "格助詞")), before = { it.pos in setOf("動詞", "形容詞", "助動詞") && it.form == "基本形" })
        point(
            "wo", "を", 5, "object marker",
            "Marks the thing an action is done to. Written を but read お. With movement verbs it marks the place you go through (道を歩く).",
            "水を飲みます。", "I drink water.",
            listOf(particle("を")),
        )
        point(
            "ni", "に", 5, "at, to, on (time)",
            "Marks a point: a time (三時に), a destination (学校に行く), where something is (家にいる), or who receives something (友達にあげる). After a na-adjective it makes an adverb (静かに).",
            "七時に起きます。", "I get up at seven.",
            listOf(particle("に", pos2 = "格助詞")),
        )
        point(
            "e", "へ", 5, "toward",
            "Marks a direction or destination. Written へ but read え. Close to に for places you go.",
            "日本へ行きます。", "I'm going to Japan.",
            listOf(particle("へ")),
        )
        point(
            "de", "で", 5, "at, by, with",
            "Marks where an action happens (図書館で読む), the means or tool (バスで行く), or a cause (風邪で休む).",
            "バスで学校に行きます。", "I go to school by bus.",
            listOf(particle("で", pos2 = "格助詞")),
        )
        point(
            "to", "と", 5, "and, with",
            "Between nouns it means \"and\" for a complete list (パンと牛乳). After a person it means \"with\" (友達と話す).",
            "友達と映画を見ました。", "I watched a movie with a friend.",
            listOf(Step(false) { it.pos == "助詞" && it.base == "と" && it.pos2 in setOf("格助詞", "並立助詞") && it.pos3 != "引用" }),
        )
        variant("to", listOf(Step(false) { it.pos == "助詞" && it.base == "と" && it.pos3 == "引用" }), before = { it.pos == "名詞" })
        point(
            "to-quote", "～と", 4, "quoting: \"…\", that…",
            "Marks what was said, thought or heard, before verbs like 言う and 思う.",
            "「おはよう」と言いました。", "I said \"good morning\".",
            listOf(Step(false) { it.pos == "助詞" && it.base == "と" && it.pos3 == "引用" }),
            // Quotes follow a closing 」 or 』 or a plain form; after a bare noun the tokenizer's 引用 is usually "with" (友達といる).
            before = { it.surface in setOf("」", "』") || it.pos in setOf("動詞", "形容詞", "助動詞") || (it.pos == "助詞" && it.pos2 == "終助詞") },
        )
        point(
            "to-if", "～と", 4, "when, whenever, if",
            "After a plain verb, と means that the second part always or naturally follows: \"when A happens, B happens\". Not for requests or plans.",
            "春になると暖かくなります。", "When spring comes, it gets warm.",
            listOf(particle("と", pos2 = "接続助詞")),
        )
        point(
            "mo", "も", 5, "also, too, even",
            "Means \"also\" and takes the place of は, が or を. With a number it stresses how many (三時間も, a whole three hours). After a question word with a negative it means \"no…\": 何も, nothing; 誰も, nobody.",
            "私も行きます。", "I'm going too.",
            listOf(particle("も", pos2 = "係助詞")),
        )
        point(
            "no", "の", 5, "'s, of",
            "Links two nouns: the first describes the second, often as owner (私の本, my book) or kind (日本語の本, a Japanese book). In a description before a noun it can say who does it, as が would: ソラの笑う声, the sound of Sora laughing.",
            "これは私の傘です。", "This is my umbrella.",
            listOf(particle("の", pos2 = "連体化")),
            // The の of ソラの笑う声, standing for が.
            listOf(particle("の", pos2 = "格助詞")),
        )
        point(
            "no-nominal", "～の", 5, "the one, the act of",
            "Turns a verb or adjective into a noun-like phrase (読むのが好き, I like reading), or stands for a thing already mentioned (赤いの, the red one).",
            "本を読むのが好きです。", "I like reading books.",
            listOf(dependentNoun("の")),
            // The の of ～のかな or ～のだ explains (～んだ); this one is followed by a particle (読むのが, 赤いのを) or ends the sentence.
            after = { it == null || it.pos == "記号" || (it.pos == "助詞" && it.base != "か") },
        )
        point(
            "ka", "か", 5, "question, or",
            "At the end of a sentence it makes a question. Between nouns it means \"or\" (コーヒーか紅茶).",
            "これは何ですか。", "What is this?",
            listOf(particle("か")),
        )
        point(
            "kara-from", "から", 5, "from",
            "After a noun, marks a starting point in place or time.",
            "駅から歩きます。", "I'll walk from the station.",
            listOf(particle("から", pos2 = "格助詞")),
        )
        point(
            "kara-because", "～から", 5, "because, so",
            "After a clause, gives the reason for what follows. A bit more personal and direct than ので.",
            "疲れたから寝ます。", "I'm tired, so I'll go to bed.",
            listOf(particle("から", pos2 = "接続助詞")),
        )
        point(
            "made", "まで", 5, "until, as far as",
            "Marks an end point in time or place. Often paired with から: 九時から五時まで.",
            "駅まで歩きました。", "I walked as far as the station.",
            listOf(particle("まで")),
        )
        point(
            "madeni", "～までに", 4, "by (a deadline)",
            "The time by which something must happen. Compare まで, which means the whole time until then.",
            "三時までに来てください。", "Please come by three.",
            listOf(particle("まで"), particle("に")),
        )
        point(
            "yori", "より", 5, "than, from",
            "In comparisons it marks what the other thing is compared against: A は B より… means \"A is more … than B\".",
            "犬より猫が好きです。", "I like cats more than dogs.",
            listOf(particle("より")),
        )
        point(
            "hou-ga", "～のほうが", 5, "the … one (comparing)",
            "Picks out the side of a comparison that is more so: 猫のほうが好き, I like cats better.",
            "バスより電車のほうが速いです。", "The train is faster than the bus.",
            listOf(dependentNoun("ほう", "方"), particle("が")),
        )
        point(
            "ya", "や", 5, "and (among others)",
            "Lists a few examples from a longer set. Compare と, which gives the whole list.",
            "りんごやバナナを買いました。", "I bought apples, bananas and so on.",
            listOf(particle("や", pos2 = "並立助詞")),
        )
        point(
            "ne", "ね", 5, "right?, isn't it",
            "At the end of a sentence it asks for agreement or shares a feeling with the listener.",
            "今日は暑いですね。", "It's hot today, isn't it?",
            listOf(particle("ね", pos2 = "終助詞")),
        )
        point(
            "yo", "よ", 5, "you know, I tell you",
            "At the end of a sentence it tells the listener something they may not know, or adds emphasis.",
            "この本はおもしろいですよ。", "This book is interesting, you know.",
            listOf(particle("よ", pos2 = "終助詞")),
        )
        point(
            "demo", "～でも", 4, "or something; even",
            "After a noun, でも softens a suggestion (コーヒーでも飲む？, want a coffee or something?) or means \"even\" (子供でもわかる, even a kid gets it).",
            "コーヒーでも飲む？", "Want a coffee or something?",
            listOf(particle("でも", pos2 = "副助詞")),
            // Opening a sentence, でも is "but", a word of its own.
            before = { it.pos != "記号" },
        )
        // Casual sentence endings.
        point(
            "na-final", "な / なあ", 4, "…huh (to yourself)",
            "At the end of a sentence, な or a drawn-out なあ is half said to yourself: a feeling or a passing thought (大きいな, it's big, huh). Casual. After a plain verb, a sharp な means \"don't\" (行くな！).",
            "今日は暑いなあ。", "It's hot today, huh.",
            listOf(particle("な", "なあ", pos2 = "終助詞")),
        )
        point(
            "kana", "～かな", 4, "I wonder",
            "か and な together: wondering out loud (行こうかな, maybe I'll go). Casual; かなあ draws it out.",
            "明日は晴れるかな。", "I wonder if it'll be sunny tomorrow.",
            listOf(particle("か"), particle("な", "なあ", pos2 = "終助詞")),
        )
        point(
            "no-question", "～の？", 4, "(casual question)",
            "At the end of a sentence, の said with a rise asks a casual question, often wanting an explanation (どこ行くの？, where are you going?). Said flat, it explains.",
            "どこに行くの？", "Where are you going?",
            listOf(particle("の", pos2 = "終助詞")),
        )
        point(
            "tte", "～って", 4, "(casual quote or topic)",
            "A casual と言う or とは. It quotes what someone said (行くって, he says he's going), or brings up something to ask or talk about (ユキって誰？, who's Yuki?).",
            "ユキって誰？", "Who's Yuki?",
            listOf(particle("って")),
        )
        // だって ending a sentence after a noun is だ + って, "they say it's": 好きだって.
        variant(
            "tte", listOf(particle("だって")), before = { it.pos == "名詞" },
            after = { it == null || it.pos == "記号" || it.pos2 == "終助詞" },
        )
        point(
            "jan", "～じゃん", 3, "…, right?, see?",
            "Casual: points out something the listener should see or agree with (いいじゃん, that's great, isn't it). It comes from じゃないか.",
            "これ、いいじゃん。", "Hey, this is nice.",
            listOf(aux("じゃん")),
        )
        point(
            "kke", "～っけ", 3, "was it…?",
            "Asks about something you've forgotten or are trying to remember: 何だっけ？ What was it again?",
            "名前、何だっけ？", "What was the name again?",
            listOf(particle("っけ", "け", pos2 = "終助詞")),
        )
        point(
            "mon", "～もん", 3, "because (it's just so)",
            "Casual: gives a reason as if that settles it, often a little childish or sulky. Also もの.",
            "だって、眠いもん。", "Well, I'm sleepy.",
            listOf(particle("もん", "もの", pos2 = "終助詞")),
        )
        point(
            "sa", "さ", 3, "you know (filler)",
            "Casual: after a word or at the end of a sentence, さ is a light \"you know\". あのさ is a common way to start talking.",
            "あのさ、明日ひま？", "Hey, are you free tomorrow?",
            listOf(particle("さ", pos2 = "終助詞")),
        )
        point(
            "zo-ze", "ぞ / ぜ", 3, "(strong emphasis)",
            "Rough, casual emphasis at the end of a sentence, mostly from men and in manga: 行くぞ！ Let's go! ぜ is the same, a little friendlier.",
            "よし、行くぞ！", "Right, let's go!",
            listOf(particle("ぞ", "ぜ", pos2 = "終助詞")),
        )
        point(
            "wa-final", "わ", 3, "(soft emphasis)",
            "A light emphasis at the end of a sentence. In Tokyo it sounds feminine; in Kansai anyone says it.",
            "私も行くわ。", "I'll go too.",
            listOf(particle("わ", pos2 = "終助詞")),
        )
        point(
            "filler", "あのー / えーと", 5, "um, er",
            "Hesitation words that buy a moment to think. あのー also gets someone's attention politely.",
            "えーと、駅はどこですか。", "Um, where's the station?",
            listOf(Step(false) { it.pos == "フィラー" }),
        )
        point(
            "dake", "だけ", 5, "only, just",
            "Limits something to just that amount or thing. Works with positive verbs, unlike しか.",
            "少しだけ食べました。", "I ate just a little.",
            listOf(particle("だけ")),
        )
        point(
            "shika", "しか", 4, "nothing but, only",
            "Always used with a negative verb: しか…ない means \"only\", often with a feeling of \"not enough\".",
            "百円しかありません。", "I only have a hundred yen.",
            listOf(particle("しか")),
        )
        point(
            "kurai", "くらい / ぐらい", 4, "about, around; to the point of",
            "After a number it means \"about\". After a phrase it shows extent: 泣きたいくらい, so much I want to cry.",
            "一時間ぐらいかかります。", "It takes about an hour.",
            listOf(particle("くらい", "ぐらい")),
        )
        point(
            "hodo", "ほど", 4, "about; (not) as … as",
            "After a number it means \"about\". In A は B ほど…ない it means \"A is not as … as B\".",
            "今日は昨日ほど寒くないです。", "Today isn't as cold as yesterday.",
            listOf(particle("ほど")),
        )
        point(
            "nado", "など", 4, "and so on, things like",
            "Follows one or more examples to show the list isn't complete. Often paired with や.",
            "本やノートなどを買いました。", "I bought books, notebooks and so on.",
            listOf(particle("など")),
        )
        point(
            "zutsu", "ずつ", 4, "each, at a time",
            "After an amount, says it applies to each one or happens that much at a time.",
            "一人二つずつ取ってください。", "Please take two each.",
            listOf(particle("ずつ")),
        )
        point(
            "bakari", "ばかり", 4, "nothing but, only",
            "Says something is all there is or all someone does, often as a complaint.",
            "弟はゲームばかりしています。", "My little brother does nothing but play games.",
            listOf(particle("ばかり")),
        )
        point(
            "keredo", "～けど / けれど", 4, "but, although",
            "Joins two clauses with \"but\". At the end of a sentence it softens it, leaving something unsaid.",
            "寒いけど、行きます。", "It's cold, but I'll go.",
            listOf(particle("けど", "けれど", "けれども", pos2 = "接続助詞")),
        )
        point(
            "node", "～ので", 4, "because, since",
            "Gives a reason, a little softer and more polite than から. After a noun or na-adjective it becomes なので.",
            "雨なので、行きません。", "It's raining, so I won't go.",
            listOf(particle("ので")),
        )
        point(
            "noni", "～のに", 4, "even though",
            "Says the result is not what you'd expect, often with surprise or disappointment. Before words like 便利 or 必要 it can instead mean \"for, in order to\".",
            "勉強したのに、忘れました。", "Even though I studied, I forgot.",
            listOf(particle("のに")),
            listOf(dependentNoun("の"), particle("に", pos2 = "格助詞")),
        )
        point(
            "shi", "～し", 4, "and what's more",
            "Lists reasons or points, hinting there are more. Often explains a decision.",
            "安いし、おいしいし、よく行きます。", "It's cheap and it's good, so I go often.",
            listOf(particle("し", pos2 = "接続助詞")),
        )
        point(
            "nagara", "～ながら", 4, "while",
            "After a verb stem: doing two things at once by the same person. The main action is the second verb.",
            "音楽を聞きながら勉強します。", "I study while listening to music.",
            listOf(particle("ながら")),
        )
        point(
            "tari", "～たり～たりする", 4, "things like A and B",
            "Gives a few example actions, not a full list or an order. The last たり is followed by する.",
            "週末は本を読んだり、散歩したりします。", "On weekends I do things like read and go for walks.",
            listOf(particle("たり", "だり")),
        )
        point(
            "ka-douka", "～かどうか", 4, "whether or not",
            "Embeds a yes-or-no question in a sentence.",
            "行くかどうか分かりません。", "I don't know whether I'll go.",
            listOf(particle("か"), base("どう"), particle("か")),
            listOf(particle("か"), base("どうか")),
        )
        point(
            "toiu", "～という", 4, "called, named; that",
            "Names something (田中という人, a person called Tanaka) or gives the content of a story or idea.",
            "「こころ」という本を読みました。", "I read a book called Kokoro.",
            listOf(particle("という")),
            listOf(Step(false) { it.pos == "助詞" && it.base == "と" && it.pos3 == "引用" }, Step(false) { it.base in setOf("いう", "言う") && it.form == "基本形" }),
        )
        point(
            "nitsuite", "～について", 4, "about, concerning",
            "Marks the topic of talking, thinking, writing and so on.",
            "日本の歴史について話しました。", "We talked about Japanese history.",
            listOf(particle("について", "につき")),
        )
        point(
            "niyotte", "～によって", 3, "by; depending on",
            "Marks who does it in a passive sentence (by), the means, or what something varies with (人によって違う, it depends on the person).",
            "答えは人によって違います。", "The answer depends on the person.",
            listOf(particle("によって", "により", "による")),
        )
        point(
            "toshite", "～として", 3, "as (a role)",
            "Says in what role or capacity: 先生として, as a teacher.",
            "通訳として働いています。", "I work as an interpreter.",
            listOf(particle("として")),
        )
        point(
            "nitotte", "～にとって", 3, "for, from the point of view of",
            "Says whose view a judgement comes from.",
            "私にとって家族が一番大切です。", "For me, family matters most.",
            listOf(particle("にとって")),
        )

        // The copula and sentence endings.
        point(
            "desu", "です / だ", 5, "is",
            "Ends a sentence that says what something is: \"X is Y\". です is polite and だ is plain. After an i-adjective, です only adds politeness.",
            "これは本です。", "This is a book.",
            // Not なら (if), the な after a na-adjective, でしょう / だろう (a guess) or
            // the で that links to what follows, which are other forms of だ.
            listOf(
                Step(false) {
                    it.pos == "助動詞" && it.type in setOf("特殊・デス", "特殊・ダ") && it.form !in setOf("仮定形", "体言接続", "未然形") &&
                        !(it.type == "特殊・ダ" && it.form == "連用形")
                },
            ),
            // Not the でし in ～ませんでした, which is part of the verb's polite past.
            before = { it.base != "ん" },
        )
        point(
            "na", "～な", 5, "(before a noun)",
            "だ becomes な before a noun: a na-adjective like きれい describes with it (きれいな部屋, a beautiful room). A noun or na-adjective also takes it before ん or の (学生なんだ, 好きなの？).",
            "きれいな部屋だね。", "It's a beautiful room, huh.",
            listOf(Step(false) { it.pos == "助動詞" && it.type == "特殊・ダ" && it.form == "体言接続" }),
        )
        point(
            "deshou", "～でしょう / だろう", 5, "probably; right?",
            "A guess (明日は雨でしょう, it'll probably rain tomorrow). Said with a rise, でしょ? looks for agreement: \"right?\". だろう is the plain form.",
            "明日は雨でしょう。", "It'll probably rain tomorrow.",
            listOf(Step(false) { it.pos == "助動詞" && it.type in setOf("特殊・デス", "特殊・ダ") && it.form == "未然形" }, aux("う").optional()),
        )
        point(
            "janai", "～じゃない / ではない", 5, "is not",
            "The negative of だ and です after a noun or na-adjective. ではない and ではありません are more formal than じゃない.",
            "私は先生じゃないです。", "I'm not a teacher.",
            listOf(particle("じゃ", pos2 = "副助詞"), Step(false) { it.base == "ない" || it.base == "ある" }),
            listOf(base("で"), particle("は"), Step(false) { it.base == "ない" || it.base == "ある" }),
        )
        point(
            "nai", "～ない", 5, "not (plain)",
            "The plain negative of a verb, as friends say it: 食べる, 食べない.",
            "肉は食べない。", "I don't eat meat.",
            // Not なきゃ, which is ～なければ.
            listOf(Step(false) { it.pos == "助動詞" && it.type == "特殊・ナイ" && !it.form.startsWith("仮定縮約") }),
        )
        point(
            "ta", "～た", 5, "did (plain past)",
            "The plain past of a verb or adjective, as friends say it: 食べる, 食べた. After some verbs it's だ (読んだ).",
            "もう食べた。", "I already ate.",
            // Not たら, which is its own point.
            listOf(Step(false) { it.pos == "助動詞" && it.type == "特殊・タ" && it.form != "仮定形" }),
        )
        point(
            "tai", "～たい", 5, "want to",
            "On a verb's stem: you want to do it. It changes like an i-adjective: 食べたい, 食べたくない, 食べたかった.",
            "ラーメン食べたい。", "I want ramen.",
            listOf(Step(false) { it.pos == "助動詞" && it.type == "特殊・タイ" }),
        )
        point(
            "masu", "～ます", 5, "polite verb ending",
            "The polite form of a verb, used with strangers, staff and at work: 食べます, 食べません, 食べました.",
            "これ、お願いします。", "This one, please.",
            // Not set phrases learned whole: いらっしゃいませ, ありがとうございます.
            listOf(Step(false) { it.pos == "助動詞" && it.type == "特殊・マス" && !it.form.startsWith("命令") }),
            before = { it.base != "ござる" },
        )
        point(
            "masenka", "～ませんか", 5, "won't you…?, shall we…?",
            "A polite invitation or suggestion. It can also just be a negative question.",
            "一緒に行きませんか。", "Would you like to go together?",
            listOf(Step(false) { it.base == "ます" && it.form.startsWith("未然") }, aux("ん"), particle("か")),
        )
        point(
            "mashouka", "～ましょうか", 5, "shall I…?, shall we…?",
            "Offers to do something or suggests doing it together.",
            "窓を開けましょうか。", "Shall I open the window?",
            listOf(Step(false) { it.base == "ます" && it.form.startsWith("未然ウ") }, aux("う"), particle("か")),
        )
        point(
            "mashou", "～ましょう", 5, "let's",
            "Suggests doing something together, politely. With friends it's the volitional form (行こう).",
            "一緒に行きましょう。", "Let's go together.",
            listOf(Step(false) { it.base == "ます" && it.form.startsWith("未然ウ") }, aux("う")),
        )
        point(
            "volitional", "～よう / ～おう", 4, "let's (casual); I'll",
            "The volitional form: a casual \"let's\" between friends (行こう), or what you've decided to do. ～ましょう is the polite version.",
            "一緒に行こう。", "Let's go together.",
            listOf(aux("う", "よう")),
            before = { it.pos == "動詞" },
        )
        point(
            "nara", "～なら", 4, "if (it's the case that)",
            "Takes up a topic or condition someone raised and says what follows from it: \"if it's X, then…\".",
            "日曜日なら、時間があります。", "If it's Sunday, I have time.",
            listOf(Step(false) { it.pos == "助動詞" && it.base == "だ" && it.surface == "なら" }),
        )

        // Te-form patterns.
        point(
            "te", "～て", 5, "and; (casual) please",
            "The て form links actions in order (起きて、食べる) and describes with adjectives (安くて、うまい). At the end of a sentence it's a casual request, as friends say it (待って, wait). Patterns like ～てください build on it.",
            "ラーメンを食べて、ビールを飲む。", "I eat ramen and drink a beer.",
            listOf(te),
            // The で after a noun or na-adjective (静かで), which links the same way.
            listOf(Step(false) { it.pos == "助動詞" && it.type == "特殊・ダ" && it.form == "連用形" && it.surface == "で" }),
        )
        point(
            "te-kudasai", "～てください", 5, "please do",
            "A polite request: the te-form plus ください.",
            "ここに名前を書いてください。", "Please write your name here.",
            listOf(te, dependentVerb("くださる")),
        )
        point(
            "naide-kudasai", "～ないでください", 5, "please don't",
            "A polite request not to do something: the ない form plus でください.",
            "ここで写真を撮らないでください。", "Please don't take photos here.",
            listOf(aux("ない"), te, dependentVerb("くださる")),
        )
        point(
            "te-iru", "～ている", 5, "is doing; is in a state",
            "With action verbs it means the action is going on (食べている, is eating). With change verbs it means the result holds (結婚している, is married). It also covers habits.",
            "今、本を読んでいます。", "I'm reading a book now.",
            listOf(te, dependentVerb("いる", "おる")),
            listOf(dependentVerb("てる", "でる")),
            // ～てた: in speech the い drops, and the tokenizer sees て and た alone.
            listOf(te, Step(false) { it.pos == "助動詞" && it.type == "特殊・タ" }),
        )
        point(
            "te-mo-ii", "～てもいい", 5, "may, it's okay to",
            "Gives or asks for permission. As a question (～てもいいですか) it asks \"may I?\".",
            "ここに座ってもいいですか。", "May I sit here?",
            listOf(te, particle("も"), good),
        )
        point(
            "te-wa-ikenai", "～てはいけない", 5, "must not",
            "Says something isn't allowed. In speech it often shrinks to ～ちゃいけない or ～じゃいけない, and among friends to ～ちゃだめ.",
            "ここで泳いではいけません。", "You must not swim here.",
            listOf(te, particle("は"), dependentVerb("いける", "なる")),
            listOf(particle("ちゃ", "じゃ", pos2 = "接続助詞"), dependentVerb("いける", "なる")),
            listOf(te, particle("は"), base("だめ", "駄目")),
            listOf(particle("ちゃ", "じゃ", pos2 = "接続助詞"), base("だめ", "駄目")),
        )
        point(
            "te-kara", "～てから", 5, "after doing",
            "Says one action happens after another is finished.",
            "手を洗ってから食べます。", "I'll eat after washing my hands.",
            listOf(te, particle("から")),
        )
        point(
            "te-shimau", "～てしまう", 4, "do completely; do by mistake",
            "Says an action is fully done, or happened with regret or by accident. In speech it shrinks to ～ちゃう or ～じゃう.",
            "宿題を忘れてしまいました。", "I forgot my homework (oops).",
            listOf(te, dependentVerb("しまう")),
            // The tokenizer sometimes takes ちゃう for a verb of its own (帰っちゃう).
            listOf(Step(false) { it.pos == "動詞" && it.base in setOf("ちゃう", "じゃう", "ちまう") }),
        )
        point(
            "te-miru", "～てみる", 4, "try doing",
            "Do something to see what it's like.",
            "この服を着てみてもいいですか。", "May I try this on?",
            listOf(te, dependentVerb("みる")),
        )
        point(
            "te-oku", "～ておく", 4, "do in advance",
            "Do something now to be ready later, or leave something as it is. In speech it shrinks to ～とく.",
            "旅行の前にホテルを予約しておきます。", "I'll book a hotel before the trip.",
            listOf(te, dependentVerb("おく")),
            listOf(dependentVerb("とく")),
        )
        point(
            "te-aru", "～てある", 4, "has been done (and stays so)",
            "Describes a state someone left on purpose. The thing usually takes が.",
            "窓が開けてあります。", "The window has been left open.",
            listOf(te, dependentVerb("ある")),
        )
        point(
            "te-kuru", "～てくる", 4, "come to; start to; go and come back",
            "Change coming up to now (寒くなってきた, it's got cold), or doing something and coming back (買ってくる).",
            "だんだん寒くなってきました。", "It's been getting colder.",
            listOf(te, dependentVerb("くる", "来る")),
            // Not ～ないで来る, "come without doing".
            before = { it.base != "ない" },
        )
        point(
            "te-iku", "～ていく", 4, "go on; do and go",
            "Change continuing from now on, or doing something as you go (持っていく, take along).",
            "これから人口が減っていきます。", "The population will keep falling from now on.",
            listOf(te, dependentVerb("いく", "行く")),
        )
        point(
            "te-kureru", "～てくれる", 4, "do for me",
            "Someone does something for the speaker or their side. ～てくださる is the polite form.",
            "母がケーキを作ってくれました。", "My mother made me a cake.",
            listOf(te, dependentVerb("くれる")),
        )
        point(
            "te-morau", "～てもらう", 4, "have someone do",
            "The speaker receives a favour: someone does something for them. ～ていただく is the humble form.",
            "友達に写真を撮ってもらいました。", "I had a friend take a photo.",
            listOf(te, dependentVerb("もらう", "いただく")),
        )
        point(
            "te-ageru", "～てあげる", 4, "do for someone",
            "Doing something for someone else. Can sound condescending when said to the person directly.",
            "妹に本を読んであげました。", "I read a book to my little sister.",
            listOf(te, dependentVerb("あげる", "やる")),
        )
        point(
            "te-kuremasenka", "～てくれませんか", 4, "could you…?",
            "A polite request, softer than ～てください. ～てもらえませんか is similar.",
            "もう少しゆっくり話してくれませんか。", "Could you speak a little more slowly?",
            listOf(te, dependentVerb("くれる", "くださる", "もらえる", "いただける"), Step(false) { it.base == "ます" && it.form.startsWith("未然") }, aux("ん"), particle("か")),
        )
        point(
            "te-hoshii", "～てほしい", 4, "want someone to",
            "Says what you want someone else to do.",
            "早く来てほしいです。", "I want you to come soon.",
            listOf(te, Step(false) { it.pos == "形容詞" && it.base == "ほしい" }),
        )
        point(
            "te-mo", "～ても", 4, "even if, even though",
            "Says the second part holds whatever the first part is.",
            "雨が降っても行きます。", "I'll go even if it rains.",
            listOf(te, particle("も", pos2 = "係助詞")),
        )

        // Verbs that change form.
        point(
            "ku-adverb", "～く / ～に", 5, "-ly (adverb)",
            "An i-adjective swaps い for く to say how something is done: 早い, fast; 早く起きる, get up early. いい becomes よく. A na-adjective takes に instead: 静かに歩く, walk quietly.",
            "早く起きた。", "I got up early.",
            listOf(Step(false) { it.pos == "形容詞" && it.form == "連用テ接続" }),
            // Not the く of くて, くない and くなる, which belong to those patterns.
            after = { next -> next == null || next.base !in setOf("て", "ない", "ある", "なる", "は", "も") },
        )
        // The に of a na-adjective, or of ～そう, used as an adverb: 静かに, 楽しそうに.
        variant("ku-adverb", listOf(particle("に", pos2 = "副詞化")), before = null)
        point(
            "imperative", "～ろ / ～え", 4, "do it! (command)",
            "The plain command: 食べろ, 行け. Blunt: coaches, manga and close friends use it. ～て and ～なさい are softer.",
            "早く起きろ！", "Get up already!",
            // ください, なさい and いらっしゃい are commands too, but have points or entries of their own.
            listOf(Step(false) { it.pos == "動詞" && it.form.startsWith("命令") && it.base !in setOf("くださる", "下さる", "なさる", "いらっしゃる") }),
        )
        point(
            "potential", "～える / ～られる", 4, "can, be able to",
            "The potential form: what you can do. う verbs change their last sound to the え row plus る (話す, 話せる; 行く, 行ける), and る verbs take られる (食べられる), often shortened to れる in speech (食べれる). It changes like a る verb: 行けない, 行けた.",
            "納豆、食べられる？", "Can you eat natto?",
            // う verbs' potentials are words of their own to the tokenizer (話せる); Reader finds those.
            listOf(Step(false) { it.pos == "動詞" && it.pos2 == "接尾" && it.base in setOf("れる", "られる") }),
            before = { it.pos == "動詞" && it.pos2 != "接尾" && (it.type.startsWith("一段") || it.type.startsWith("カ変")) },
        )
        point(
            "passive", "～れる / ～られる", 4, "is done (passive)",
            "The passive: something is done to the subject, often with に for who did it (先生に言われた, I was told by the teacher).",
            "先生に名前を呼ばれました。", "My name was called by the teacher.",
            listOf(Step(false) { it.pos == "動詞" && it.pos2 == "接尾" && it.base in setOf("れる", "られる") }),
            before = { !(it.pos == "動詞" && it.pos2 != "接尾" && (it.type.startsWith("一段") || it.type.startsWith("カ変"))) },
        )
        point(
            "causative", "～せる / ～させる", 4, "make or let someone do",
            "The causative: someone makes or lets someone else do something, with に or を for who does it.",
            "母は弟に野菜を食べさせました。", "Mom made my little brother eat his vegetables.",
            listOf(Step(false) { it.pos == "動詞" && it.pos2 == "接尾" && it.base in setOf("せる", "させる") }),
        )

        // Negatives and obligation.
        point(
            "nakereba", "～なければならない", 4, "must, have to",
            "Literally \"if not, it won't do\". Also ～なくてはいけない, and in speech ～なきゃ or ～なくちゃ.",
            "明日早く起きなければなりません。", "I have to get up early tomorrow.",
            listOf(Step(false) { it.base == "ない" && it.form == "仮定形" }, particle("ば"), dependentVerb("なる", "いける")),
            listOf(Step(false) { it.base == "ない" && it.form == "連用テ接続" }, te, particle("は"), dependentVerb("なる", "いける")),
            listOf(Step(false) { it.pos == "助動詞" && it.base == "ない" && it.form.startsWith("仮定縮約") }, dependentVerb("なる", "いける").optional()),
            listOf(Step(false) { it.base == "ない" && it.form == "連用テ接続" }, particle("ちゃ"), dependentVerb("なる", "いける").optional()),
        )
        point(
            "nakute-mo-ii", "～なくてもいい", 4, "don't have to",
            "Says something isn't necessary.",
            "明日は来なくてもいいです。", "You don't have to come tomorrow.",
            listOf(Step(false) { it.base == "ない" && it.form == "連用テ接続" }, te, particle("も"), good),
        )
        point(
            "naide", "～ないで", 4, "without doing",
            "Do the next thing without doing this one, or instead of it.",
            "朝ご飯を食べないで出かけました。", "I went out without eating breakfast.",
            listOf(aux("ない"), Step(false) { it.pos == "助詞" && it.base == "で" && it.pos2 == "接続助詞" }),
        )

        // Conditionals.
        point(
            "ba", "～ば", 4, "if",
            "A condition: if A, then B. Often for general truths or what's needed for a result.",
            "安ければ買います。", "If it's cheap, I'll buy it.",
            listOf(particle("ば", pos2 = "接続助詞")),
        )
        point(
            "tara", "～たら", 4, "if, when, after",
            "If or once A happens, then B. The most flexible way to say \"if\", and the usual one in speech.",
            "雨が降ったら、行きません。", "If it rains, I won't go.",
            listOf(Step(false) { it.pos == "助動詞" && it.type == "特殊・タ" && it.form == "仮定形" }),
        )

        point(
            "ni-iku", "～に行く / ～に来る", 5, "go to do, come to do",
            "After a verb stem: the purpose of going or coming (見に行く, go to see).",
            "公園に桜を見に行きます。", "I'm going to the park to see the cherry blossoms.",
            listOf(particle("に", pos2 = "格助詞"), base("行く", "いく", "来る", "くる", "帰る", "かえる", "出かける", "でかける")),
            before = { it.pos == "動詞" && it.form == "連用形" },
        )
        point(
            "naru", "～くなる / ～になる", 5, "become",
            "Change: an i-adjective takes く (寒くなる, get cold), and a noun or na-adjective takes に (先生になる, become a teacher).",
            "春になりました。", "It's spring now.",
            listOf(particle("に"), base("なる", "成る")),
        )
        variant("naru", listOf(base("なる", "成る")), before = { it.pos == "形容詞" && it.form == "連用テ接続" })

        // Verb stem patterns.
        point(
            "sugiru", "～すぎる", 4, "too much",
            "After a verb stem or adjective stem: more than is good.",
            "昨日は食べすぎました。", "I ate too much yesterday.",
            listOf(Step(false) { it.pos == "動詞" && it.pos2 in setOf("非自立", "接尾") && it.base in setOf("すぎる", "過ぎる") }),
        )
        point(
            "hajimeru", "～始める / ～出す", 4, "start to",
            "After a verb stem: the action begins. ～出す suggests it starts suddenly.",
            "雨が降り始めました。", "It started to rain.",
            listOf(dependentVerb("始める", "はじめる", "出す", "だす")),
        )
        point(
            "owaru", "～終わる", 4, "finish doing",
            "After a verb stem: the action is finished.",
            "この本はもう読み終わりました。", "I've finished reading this book.",
            listOf(dependentVerb("終わる", "おわる", "終える")),
        )
        point(
            "tsuzukeru", "～続ける", 4, "keep doing",
            "After a verb stem: the action carries on.",
            "三時間歩き続けました。", "I kept walking for three hours.",
            listOf(dependentVerb("続ける", "つづける")),
        )
        point(
            "yasui", "～やすい / ～にくい", 4, "easy to / hard to",
            "After a verb stem they make an adjective: 読みやすい, easy to read; 読みにくい, hard to read.",
            "このペンは書きやすいです。", "This pen is easy to write with.",
            listOf(Step(false) { it.pos == "形容詞" && it.pos2 == "非自立" && it.base in setOf("やすい", "にくい", "づらい") }),
        )
        point(
            "nasai", "～なさい", 4, "do (an order)",
            "A firm instruction, as from a parent or teacher, or in test questions.",
            "早く寝なさい。", "Go to bed now.",
            listOf(dependentVerb("なさる")),
        )

        // Noun-based patterns.
        point(
            "ta-koto-ga-aru", "～たことがある", 4, "have done before",
            "Talks about experience: something has happened at least once. ～たことがない means never.",
            "日本に行ったことがあります。", "I've been to Japan.",
            listOf(past, dependentNoun("こと"), particle("が", "は", "も").optional(), Step(false) { it.base == "ある" || (it.pos == "形容詞" && it.base == "ない") }),
        )
        point(
            "koto-ga-aru", "～ことがある", 4, "sometimes",
            "After a plain present verb: it happens now and then.",
            "たまに遅れることがあります。", "I'm sometimes late.",
            listOf(dependentNoun("こと"), particle("が", "も"), base("ある")),
            before = plainVerb,
        )
        point(
            "koto-ga-dekiru", "～ことができる", 4, "can, be able to",
            "After a plain verb: ability or possibility. A bit more formal than the potential form.",
            "私は泳ぐことができます。", "I can swim.",
            listOf(dependentNoun("こと"), particle("が", "は", "も").optional(), base("できる", "出来る")),
        )
        point(
            "koto-ni-suru", "～ことにする", 4, "decide to",
            "Your own decision. ～ことにしている means it's a rule you keep.",
            "毎日歩くことにしました。", "I've decided to walk every day.",
            listOf(dependentNoun("こと"), particle("に"), base("する")),
        )
        point(
            "koto-ni-naru", "～ことになる", 4, "it's been decided that",
            "A decision made by others or by circumstances. ～ことになっている means it's a rule.",
            "来月、大阪に行くことになりました。", "It's been decided that I'll go to Osaka next month.",
            listOf(dependentNoun("こと"), particle("に"), base("なる")),
        )
        point(
            "hou-ga-ii", "～ほうがいい", 4, "had better, should",
            "Advice. Usually after the past form (行ったほうがいい) for doing, and after ない for not doing.",
            "早く寝たほうがいいですよ。", "You'd better go to bed early.",
            listOf(dependentNoun("ほう", "方"), particle("が"), good),
        )
        point(
            "tsumori", "～つもり", 4, "intend to, plan to",
            "After a plain verb: what you mean to do. ～ないつもり is intending not to.",
            "来年日本に行くつもりです。", "I plan to go to Japan next year.",
            listOf(dependentNoun("つもり")),
        )
        point(
            "hazu", "～はず", 4, "should, is expected to",
            "What the speaker expects to be true from what they know. ～はずがない means it can't be.",
            "彼はもう着いたはずです。", "He should have arrived by now.",
            listOf(dependentNoun("はず", "筈")),
        )
        point(
            "tame", "～ため(に)", 4, "for, in order to; because of",
            "After a verb or \"noun + の\": a purpose (in order to). After a past form or a state it can give a cause.",
            "日本語を勉強するために日本に来ました。", "I came to Japan to study Japanese.",
            listOf(dependentNoun("ため", "為"), particle("に").optional()),
        )
        point(
            "toki", "～とき", 5, "when",
            "After a verb, adjective or \"noun + の\": the time when something happens (子供のとき, when I was a child).",
            "暇なとき、マンガを読みます。", "When I have free time, I read manga.",
            listOf(Step(false) { it.pos == "名詞" && it.pos2 == "非自立" && it.base in setOf("とき", "時") }),
        )
        point(
            "n-desu", "～んです / ～のだ", 4, "explaining, asking why",
            "の or ん before だ or です explains or asks for an explanation: どうしたんですか, what's wrong?. It sounds softer and more involved than a bare statement. ～んじゃない？ suggests: isn't it that…?",
            "どうして来なかったんですか。", "Why didn't you come?",
            listOf(Step(false) { it.pos == "名詞" && it.pos2 == "非自立" && it.base in setOf("の", "ん") }, Step(false) { it.pos == "助動詞" && it.type in setOf("特殊・ダ", "特殊・デス") }),
            listOf(Step(false) { it.pos == "名詞" && it.pos2 == "非自立" && it.base in setOf("の", "ん") }, particle("か")),
            // ～んじゃない: "isn't it that…?".
            listOf(Step(false) { it.pos == "名詞" && it.pos2 == "非自立" && it.base in setOf("の", "ん") }, particle("じゃ")),
        )
        // ～んだって, "I hear that…": the だって is ～って's (だ + って), the ん this point's.
        variant(
            "n-desu", listOf(Step(false) { it.pos == "名詞" && it.pos2 == "非自立" && it.base in setOf("の", "ん") }), before = null,
            after = { it?.pos == "助詞" && it.base == "だって" },
        )
        point(
            "mae-ni", "～前に", 5, "before doing",
            "After a plain present verb: before that happens. After \"noun + の\" it can mean \"in front of\" instead.",
            "寝る前に歯を磨きます。", "I brush my teeth before bed.",
            listOf(base("前"), particle("に")),
            before = plainVerb,
        )
        point(
            "ato-de", "～た後で", 5, "after doing",
            "After a past-form verb: once that's done.",
            "食べた後で散歩します。", "I'll take a walk after eating.",
            listOf(past, base("後", "あと"), particle("で", "に").optional()),
        )
        // After a noun: 仕事の後で.
        variant("ato-de", listOf(base("後", "あと"), particle("で", "に").optional()), before = { it.pos == "助詞" && it.base == "の" })
        point(
            "uchi-ni", "～うちに", 3, "while (still)",
            "Do it while a state lasts, before it changes: 若いうちに, while you're young.",
            "温かいうちに食べてください。", "Please eat it while it's warm.",
            listOf(dependentNoun("うち"), particle("に")),
        )
        point(
            "tokoro-ing", "～ているところ", 4, "in the middle of doing",
            "Right in the middle of an action.",
            "今、ご飯を食べているところです。", "I'm in the middle of eating.",
            listOf(te, dependentVerb("いる"), dependentNoun("ところ")),
        )
        point(
            "tokoro-just", "～たところ", 4, "just did",
            "The action has just finished.",
            "今、家に着いたところです。", "I've just got home.",
            listOf(past, dependentNoun("ところ")),
        )
        point(
            "tokoro-about", "～るところ", 4, "about to",
            "After a plain present verb: just about to do it.",
            "今から出かけるところです。", "I'm just about to go out.",
            listOf(dependentNoun("ところ")),
            before = plainVerb,
        )
        point(
            "ta-bakari", "～たばかり", 4, "just did (recently)",
            "Something happened only a short while ago, in the speaker's eyes. Unlike ～たところ, it can be days or months.",
            "先月日本に来たばかりです。", "I only came to Japan last month.",
            listOf(past, particle("ばかり")),
        )

        // Guesses, hearsay and likeness.
        point(
            "kamoshirenai", "～かもしれない", 4, "might, maybe",
            "A guess the speaker is about half sure of. ～かもしれません is the polite form, and in speech it's often just ～かも.",
            "明日は雨が降るかもしれません。", "It might rain tomorrow.",
            listOf(particle("かも"), base("しれる", "知れる")),
            listOf(particle("か"), particle("も"), base("しれる", "知れる")),
            listOf(particle("かも")),
        )
        point(
            "to-omou", "～と思う", 4, "I think",
            "Gives an opinion or guess. ～と思っている is used for others' thoughts or a long-held view.",
            "明日は晴れると思います。", "I think it'll be sunny tomorrow.",
            listOf(Step(false) { it.pos == "助詞" && it.base == "と" && it.pos3 == "引用" }, base("思う", "おもう")),
        )
        point(
            "you-to-omou", "～ようと思う", 4, "I'm thinking of doing",
            "The volitional form plus と思う: a plan you're considering.",
            "週末に映画を見ようと思います。", "I'm thinking of seeing a movie this weekend.",
            listOf(aux("う", "よう"), Step(false) { it.pos == "助詞" && it.base == "と" && it.pos3 == "引用" }, base("思う", "おもう")),
        )
        point(
            "you-ni-naru", "～ようになる", 4, "come to, become able to",
            "A gradual change in ability or habit.",
            "日本語が話せるようになりました。", "I've become able to speak Japanese.",
            listOf(yoo, particle("に"), base("なる")),
        )
        point(
            "you-ni-suru", "～ようにする", 4, "make a point of, try to",
            "An effort to make something a habit. ～ないようにする is trying not to.",
            "毎日野菜を食べるようにしています。", "I make sure to eat vegetables every day.",
            listOf(yoo, particle("に"), base("する")),
        )
        point(
            "you", "～よう / ～みたい", 4, "seems, looks like; like",
            "A guess from what you see or know (来ないようだ, it seems he isn't coming), or a likeness (猫のような, like a cat). みたい is the casual version.",
            "誰もいないようです。", "It seems nobody's here.",
            listOf(yoo),
            listOf(Step(false) { it.pos == "名詞" && it.base == "みたい" }),
        )
        point(
            "sou-looks", "～そう", 4, "looks like, seems about to",
            "After a verb stem or adjective stem: how something looks to you (おいしそう, looks tasty), or that it's about to happen (降りそう).",
            "このケーキはおいしそうですね。", "This cake looks good.",
            listOf(Step(false) { it.pos == "名詞" && it.base == "そう" && it.pos2 == "接尾" }),
        )
        point(
            "sou-hearsay", "～そうだ", 4, "I hear that",
            "After a plain form: passing on something you heard or read.",
            "天気予報によると、明日は雨だそうです。", "According to the forecast, it'll rain tomorrow.",
            listOf(Step(false) { it.pos == "名詞" && it.base == "そう" && it.pos2 == "特殊" }),
        )
        point(
            "rashii", "～らしい", 4, "apparently, seems",
            "A conclusion from what you've heard or seen. After a noun it can also mean \"typical of\" (男らしい).",
            "彼は会社を辞めたらしいです。", "Apparently he quit his job.",
            listOf(aux("らしい")),
            listOf(Step(false) { it.pos == "形容詞" && it.pos2 == "接尾" && it.base == "らしい" }),
        )

        // Taught by a lesson only: the reader reads these as words.
        lesson(
            "counters", "一つ / 一人 / 一回", 5, "counting",
            "A number takes a counter for the kind of thing counted: つ for things, 人 for people, 回 for times, 本 for long things like bottles. Some numbers change their sound with a counter: 一人 is ひとり, 三本 is さんぼん.",
            "ラーメンを三つ買った。", "We bought three bowls of ramen.",
        )
    }

    /** Every point, for listing and tests. */
    val points: List<GrammarPoint> by lazy { rules.map { it.point }.distinctBy { it.id } + lessonOnly }

    val byId: Map<String, GrammarPoint> by lazy { points.associateBy { it.id } }
}

/** The grammar point with [id], such as "wa" for the particle は. */
fun grammarPoint(id: String): GrammarPoint? = Grammar.byId[id]

/** Every grammar point the reader knows, in the order they're defined. */
fun grammarPoints(): List<GrammarPoint> = Grammar.points

/** The grammar points that explain each conjugation label a word can have (see [ReadingWord.conjugation]). */
internal val LABEL_POINTS = mapOf(
    "past" to setOf("ta", "ta-koto-ga-aru", "ta-bakari", "tokoro-just", "ato-de"),
    "negative" to setOf("nai", "janai", "naide", "naide-kudasai", "nakute-mo-ii"),
    "polite" to setOf("masu", "masenka", "mashou", "mashouka", "desu", "deshou"),
    "want to" to setOf("tai"),
    "volitional (let's)" to setOf("volitional", "mashou", "mashouka", "you-to-omou"),
    "probably" to setOf("deshou"),
    "seems" to setOf("rashii"),
    "conditional (if/when)" to setOf("tara"),
    "conditional (if)" to setOf("ba"),
    "while" to setOf("nagara"),
    "things like" to setOf("tari"),
    "must" to setOf("nakereba"),
    "causative" to setOf("causative"),
    "passive" to setOf("passive"),
    "potential" to setOf("potential"),
    "imperative" to setOf("imperative", "nasai"),
    "ongoing or resulting state" to setOf("te-iru", "tokoro-ing"),
    "ongoing (humble)" to setOf("te-iru"),
    "has been done" to setOf("te-aru"),
    "in advance" to setOf("te-oku"),
    "completely or regrettably" to setOf("te-shimau"),
    "try" to setOf("te-miru"),
    "toward now" to setOf("te-kuru"),
    "from now on" to setOf("te-iku"),
    "get someone to" to setOf("te-morau"),
    "get someone to (humble)" to setOf("te-morau"),
    "for me" to setOf("te-kureru", "te-kuremasenka"),
    "please" to setOf("te-kudasai", "naide-kudasai"),
    "for someone" to setOf("te-ageru"),
    "want someone to" to setOf("te-hoshii"),
    "easy to" to setOf("yasui"),
    "hard to" to setOf("yasui"),
    "looks like" to setOf("sou-looks"),
)

/** Labels any of a word's points explain: a stem or a te-form is there for the point it's part of (見 in 見に行く, 使って in 使ってもいい). */
private val PART_OF_A_POINT = setOf("stem", "stem (continuing)", "adverb (-ly)", "te-form")

/**
 * The word's conjugation labels that none of its grammar points explains:
 * "past" in 切れちゃった, whose one point is ～てしまう (it takes in the た),
 * or in 来ました, whose point is ～ます.
 */
fun ReadingWord.unexplainedConjugation(): List<String> {
    if (grammar.isEmpty()) return conjugation
    val ids = grammar.map { it.point.id }.toSet()
    return conjugation.filter { label -> label !in PART_OF_A_POINT && LABEL_POINTS[label].orEmpty().none { it in ids } }
}
