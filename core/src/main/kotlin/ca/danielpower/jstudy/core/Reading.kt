package ca.danielpower.jstudy.core

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer

/** A dictionary entry matched to a word, and the entry's reading of that spelling in hiragana. */
data class LexiconMatch(val wordId: Long, val reading: String)

/** Finds dictionary entries for the words in a story. */
fun interface Lexicon {
    /**
     * The entry best matching a word spelled [text], or null. [reading] is the
     * word's reading in hiragana when known, to choose between homographs.
     */
    fun find(text: String, reading: String?): LexiconMatch?

    /** The verbs spelled [text], for telling a potential form (話せる) from a verb of its own (売れる). See [verbEntries]. */
    fun verbs(text: String): List<VerbEntry> = emptyList()
}

/**
 * A dictionary entry for a verb. [potential] when the dictionary says it's a
 * potential form itself: its first meaning is "to be able to …" (話せる,
 * 読める), or a sense notes it (取れる, 撮れる).
 */
data class VerbEntry(val id: Long, val kana: String, val godan: Boolean, val usuallyKana: Boolean, val potential: Boolean)

/** Every sense of the entries with a spelling (the one argument), for [verbEntries]. */
const val VERBS_SQL =
    "SELECT w.id, w.kana, w.usually_kana, s.pos, s.gloss, s.info FROM form f JOIN word w ON w.id = f.word_id " +
        "JOIN sense s ON s.word_id = w.id WHERE f.text = ? ORDER BY w.id, s.ord"

/** One row of [VERBS_SQL]: parts of speech and glosses as the JSON arrays the dictionary stores. */
data class SenseRow(val id: Long, val kana: String, val usuallyKana: Boolean, val pos: String, val gloss: String, val info: String?)

/** The verbs among the entries [rows] (from [VERBS_SQL]) describe. The app and the story tools both use this. */
fun verbEntries(rows: List<SenseRow>): List<VerbEntry> = rows.groupBy { it.id }.values.mapNotNull { senses ->
    val first = senses.first()
    if (!first.pos.contains("\"v")) return@mapNotNull null
    val able = first.gloss.startsWith("[\"to be able to") || first.gloss.startsWith("[\"to be possible to") ||
        senses.any { it.info?.contains("potential") == true }
    VerbEntry(first.id, first.kana, first.pos.contains("\"v5"), first.usuallyKana, able)
}

/** A dictionary entry with a spelling a word was written in, for [bestHomograph]. */
data class Homograph(val id: Long, val kana: String, val jlpt: Int, val rank: Int)

/** The entries that have a spelling (the one argument), for [bestHomograph]: id, kana, JLPT level, frequency rank. */
const val HOMOGRAPHS_SQL =
    "SELECT w.id, w.kana, w.jlpt, COALESCE(w.freq_rank, 1000000000) FROM form f JOIN word w ON w.id = f.word_id WHERE f.text = ?"

/**
 * The entry for a word among the [homographs] spelled the way it is. One with
 * a kana spelling matching [reading] wins, then JLPT words, then the more
 * frequent. JLPT only breaks ties here: the frequency list ranks spellings,
 * not entries, so a rare homograph shares a common word's rank (右舞 has
 * うまい's), and the JLPT lists name the entry learners mean. [forms] gives
 * an entry's spellings. The app and the story tools both look words up this
 * way, so a story's index matches what the app reads.
 */
fun bestHomograph(homographs: List<Homograph>, reading: String?, forms: (Long) -> List<String>): LexiconMatch? {
    val candidates = homographs.distinctBy { it.id }
    if (candidates.isEmpty()) return null
    val wanted = reading?.let(Kana::toHiragana)
    val readingMatches = if (wanted == null) emptySet() else candidates.filter { cand ->
        Kana.toHiragana(cand.kana) == wanted || forms(cand.id).any { Kana.isKana(it) && Kana.toHiragana(it) == wanted }
    }.map { it.id }.toSet()
    val best = candidates.minWith(compareBy<Homograph>({ it.id !in readingMatches }, { it.jlpt == 0 }, { it.rank }, { it.id }))
    return LexiconMatch(best.id, if (best.id in readingMatches) wanted!! else Kana.toHiragana(best.kana))
}

/** One piece of a written word and the reading above it (null for kana). */
data class Ruby(val base: String, val ruby: String?)

enum class WordKind {
    /** A content word: counted as known or new, and tappable. */
    WORD,

    /** A content word that isn't vocabulary to learn: a name, or a number in digits. Tappable, never counted. */
    NAME,

    /** A particle, auxiliary or other grammar on its own. */
    GRAMMAR,
    PUNCTUATION,
}

/**
 * A word as it appears in a story: a dictionary word with any conjugation
 * attached, so 食べさせられた is one word whose dictionary form is 食べる.
 */
data class ReadingWord(
    val surface: String,
    /** The dictionary form, e.g. 食べる for 食べさせられた. */
    val lemma: String,
    /** The reading of [surface] in hiragana. */
    val reading: String,
    val kind: WordKind,
    val wordId: Long?,
    /** What the conjugation does, e.g. ["causative", "passive", "past"]. Empty when not conjugated. */
    val conjugation: List<String>,
    val ruby: List<Ruby>,
    /** Grammar points this word is part of, in the order they appear. */
    val grammar: List<GrammarUse> = emptyList(),
) {
    val conjugated get() = conjugation.isNotEmpty()

    /** A verb or adjective stem on its own, waiting for what follows it: 見 in 見に行く, 暑く in 暑くなる. */
    val stem get() = conjugation.singleOrNull() in setOf("stem (continuing)", "stem", "adverb (-ly)")

    /** Identifies the word for counting: its dictionary entry, or its dictionary form if it has none. */
    val key: String get() = wordId?.toString() ?: lemma
}

/** A story's paragraphs as written: blank lines and single line breaks both start a new one. */
fun paragraphs(text: String): List<String> = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * A paragraph, and who says it when it's a line of dialogue. A story marks
 * the speaker with their name and a bar: ユキ|「でしょ？」. The name isn't
 * part of the text, so it's never read as a word.
 */
data class Speech(val speaker: String?, val text: String) {
    /** As a script prints it, the name straight before the quote: ユキ「でしょ？」. */
    override fun toString() = (speaker ?: "") + text

    companion object {
        fun of(paragraph: String): Speech {
            val bar = paragraph.indexOf('|')
            if (bar <= 0) return Speech(null, paragraph)
            return Speech(paragraph.substring(0, bar).trim(), paragraph.substring(bar + 1).trim())
        }

        /** A story's opening paragraph, for a preview, with its speaker as a script prints it. */
        fun firstLine(text: String): String = paragraphs(text).firstOrNull()?.let { of(it).toString() }.orEmpty()

        /** Each paragraph's speaker, or null for narration. */
        fun speakers(text: String): List<String?> = paragraphs(text).map { of(it).speaker }

        /**
         * For each paragraph, the widest speaker name in its run of dialogue:
         * the paragraphs around it with speakers, unbroken by narration. A
         * run's quotes all start in line, and a long name only widens its own
         * run. Null for narration.
         */
        fun columns(speakers: List<String?>, width: (String) -> Float): List<Float?> {
            val out = MutableList<Float?>(speakers.size) { null }
            var start = 0
            while (start < speakers.size) {
                if (speakers[start] == null) {
                    start++
                    continue
                }
                var end = start
                while (end < speakers.size && speakers[end] != null) end++
                val widest = (start until end).maxOf { width(speakers[it]!!) }
                for (i in start until end) out[i] = widest
                start = end
            }
            return out
        }
    }
}

/** Splits Japanese text into words with Kuromoji (IPADIC) and matches them to the dictionary. */
class Reader(private val lexicon: Lexicon) {

    /**
     * Paragraphs of words. Blank lines and single line breaks both start a new paragraph.
     * [readings] pins a word the reader gets wrong to the right one; see [words].
     */
    fun read(text: String, readings: Map<String, String> = emptyMap()): List<List<ReadingWord>> =
        paragraphs(text).map { words(Speech.of(it).text, readings) }

    /**
     * [readings] maps a word, written as the text writes it, to its reading,
     * for the odd word the reader gets wrong: 方 in あの方 is かた, not the
     * ほう of ～のほうが. A pinned word is looked up by that reading, and
     * left as it was if the dictionary doesn't have it.
     */
    fun words(paragraph: String, readings: Map<String, String> = emptyMap()): List<ReadingWord> {
        val tokens = tokenizer.tokenize(paragraph).map(::Morpheme)
        val out = ArrayList<ReadingWord>()
        // The tokens each word was made from, for placing grammar points.
        val spans = ArrayList<IntRange>()
        var i = 0
        while (i < tokens.size) {
            val from = i
            val t = tokens[i]
            val compound = compound(tokens, i) ?: counted(tokens, i) ?: greeting(tokens, i) ?: setPhrase(tokens, i)
            when {
                compound != null -> {
                    out += compound.first
                    i = compound.second
                }
                t.pos == "記号" || t.surface.none(Char::isLetterOrDigit) -> {
                    out += plain(listOf(t), WordKind.PUNCTUATION, null)
                    i++
                }
                t.isInflectingHead || comesWithout(tokens, i) || (t.isSuruNoun && tokens.getOrNull(i + 1)?.isSuru == true) -> {
                    val end = chainEnd(tokens, i + if (t.isSuruNoun) 2 else 1)
                    out += inflected(tokens.subList(i, end))
                    i = end
                }
                t.pos == "名詞" && t.pos2 == "形容動詞語幹" -> {
                    // A na-adjective takes the copula as its conjugation (きれいだった),
                    // but plain だ or な is grammar of its own.
                    val end = chainEnd(tokens, i + 1)
                    val group = tokens.subList(i, end)
                    if (end > i + 1 && labels(group).isNotEmpty()) {
                        out += inflected(group)
                        i = end
                    } else {
                        out += content(t)
                        i++
                    }
                }
                // でも, けど and だって opening a sentence join it to the one before ("But, …"):
                // a word, though the tokenizer sometimes takes them for particles after ！.
                t.pos == "助詞" && t.base in CONJUNCTIONS && (i == 0 || tokens[i - 1].pos == "記号") -> {
                    out += content(t)
                    i++
                }
                t.pos == "助詞" || t.pos == "助動詞" || (t.pos == "動詞" && t.pos2 == "非自立") ||
                    (t.pos == "名詞" && t.pos2 == "非自立") || t.pos == "フィラー" -> {
                    out += plain(listOf(t), WordKind.GRAMMAR, null)
                    i++
                }
                else -> {
                    out += content(t)
                    i++
                }
            }
            spans += from until i
        }
        return withGrammar(if (readings.isEmpty()) out else out.map { pinned(it, readings) }, spans, tokens)
    }

    /** [word] as [readings] reads it, if it pins it and the dictionary has it with that reading. */
    private fun pinned(word: ReadingWord, readings: Map<String, String>): ReadingWord {
        val reading = readings[word.surface] ?: return word
        val match = (lexicon.find(word.surface, reading) ?: lexicon.find(word.lemma, reading))?.takeIf { it.reading == reading }
            ?: return word
        return word.copy(kind = WordKind.WORD, wordId = match.wordId, reading = reading, ruby = ruby(word.surface, reading))
    }

    private fun withGrammar(words: List<ReadingWord>, spans: List<IntRange>, tokens: List<Morpheme>): List<ReadingWord> {
        val wordOf = IntArray(tokens.size)
        spans.forEachIndexed { w, span -> for (k in span) wordOf[k] = w }
        // A set phrase or compound read whole (いってきます) has no grammar inside it.
        val whole = words.indices.filter { words[it].kind == WordKind.WORD && words[it].lemma == words[it].surface && spans[it].count() > 1 }.toSet()
        val found = Grammar.find(tokens).map { GrammarUse(it.point, wordOf[it.start], wordOf[it.end - 1]) }
            .filterNot { it.first == it.last && it.first in whole }
        // A bare stem is there for what follows it, so it joins that point: 見 in 見に行く, 暑く in 暑くなる.
        val stems = words.indices.filter { words[it].stem }.toSet()
        val joined = found.map { u ->
            if (u.first - 1 in stems && found.none { o -> u.first - 1 in o.first..o.last }) u.copy(first = u.first - 1) else u
        }
        // The tokenizer takes a う verb's potential for a verb of its own (話せる), so no pattern finds it.
        val potential = grammarPoint("potential")!!
        val uses = (
            joined + words.indices.filter { w ->
                "potential" in words[w].conjugation && joined.none { it.point == potential && w in it.first..it.last }
            }.map { GrammarUse(potential, it, it) }
            ).sortedBy { it.first }
        return words.mapIndexed { w, word ->
            val mine = uses.filter { w in it.first..it.last }
            when {
                mine.isNotEmpty() -> word.copy(grammar = mine)
                word.conjugated && !word.stem -> phrase(word)
                // A time noun the tokenizer files with grammar but no point explains is a word: 日 in 休みの日.
                word.kind == WordKind.GRAMMAR && word.surface.any(::isKanji) && tokens[spans[w].first].pos3 == "副詞可能" ->
                    lexicon.find(word.lemma, word.reading)?.let { word.copy(kind = WordKind.WORD, wordId = it.wordId) } ?: word
                else -> word
            }
        }
    }

    /**
     * A conjugated word no grammar point explains, as a set phrase when the
     * dictionary has it whole: いらっしゃいませ is "welcome", not a form of
     * いらっしゃる.
     */
    private fun phrase(word: ReadingWord): ReadingWord {
        val match = lexicon.find(word.surface, word.reading) ?: return word
        return word.copy(lemma = word.surface, wordId = match.wordId, reading = match.reading, conjugation = emptyList(), ruby = ruby(word.surface, match.reading))
    }

    /** An interjection and the polite ending the dictionary has with it as one phrase: ありがとうございます. */
    private fun greeting(tokens: List<Morpheme>, start: Int): Pair<ReadingWord, Int>? {
        if (tokens[start].pos != "感動詞") return null
        var end = start + 1
        while (end < tokens.size && tokens[end].pos == "助動詞") end++
        for (stop in end downTo start + 2) {
            val group = tokens.subList(start, stop)
            val text = group.joinToString("") { it.surface }
            val match = lexicon.find(text, group.joinToString("") { it.readingOrSurface }) ?: continue
            return plain(group, WordKind.WORD, match.wordId).copy(reading = match.reading, ruby = ruby(text, match.reading)) to stop
        }
        return null
    }

    /**
     * An everyday set phrase the tokenizer splits or misreads, as its
     * dictionary entry: ただいま (not ただ + いま before ！), いってきます (not a
     * form of 言う).
     */
    private fun setPhrase(tokens: List<Morpheme>, start: Int): Pair<ReadingWord, Int>? {
        for (phrase in SET_PHRASES) {
            var end = start
            var text = ""
            while (end < tokens.size && text.length < phrase.length) text += tokens[end++].surface
            if (text != phrase || end == start) continue
            val match = lexicon.find(phrase, Kana.toHiragana(phrase)) ?: continue
            return plain(tokens.subList(start, end), WordKind.WORD, match.wordId).copy(reading = match.reading, ruby = ruby(phrase, match.reading)) to end
        }
        return null
    }

    /** A run of nouns the dictionary knows as one word (図書館員, 魔導士), longest first. */
    private fun compound(tokens: List<Morpheme>, start: Int): Pair<ReadingWord, Int>? {
        val first = tokens[start]
        if (!(first.pos == "名詞" || first.pos == "接頭詞") || first.pos2 == "数") return null
        var end = start
        while (end < tokens.size && end - start < MAX_COMPOUND &&
            (tokens[end].pos == "名詞" || (end == start && tokens[end].pos == "接頭詞")) &&
            tokens[end].pos2 != "数" && tokens[end].pos2 != "非自立"
        ) {
            end++
        }
        for (stop in end downTo start + 2) {
            val group = tokens.subList(start, stop)
            val text = group.joinToString("") { it.surface }
            val reading = group.joinToString("") { it.readingOrSurface }
            val match = lexicon.find(text, reading) ?: continue
            // The tokenizer reads unfamiliar compounds piece by piece (魔導士 as ま・しるべ・し),
            // so the dictionary's reading wins.
            return plain(group, WordKind.WORD, match.wordId).copy(reading = match.reading, ruby = ruby(text, match.reading)) to stop
        }
        return null
    }

    /**
     * A number with its counter that's a word of its own: a dictionary word
     * whose reading isn't its pieces read in turn (一つ, 二人, 一回, 六本,
     * 二十歳). Others (三時, 三人, 五百円) are read a piece at a time, so you
     * learn 三 and 時 rather than every pair.
     */
    private fun counted(tokens: List<Morpheme>, start: Int): Pair<ReadingWord, Int>? {
        if (!tokens[start].isNumber || tokens.getOrNull(start - 1)?.isNumber == true) return null
        var end = start
        while (end < tokens.size && tokens[end].isNumber) end++
        if (tokens.getOrNull(end)?.isCounter == true) end++
        if (end == start + 1) return null
        val group = tokens.subList(start, end)
        val text = group.joinToString("") { it.surface }
        if (text.any(Char::isDigit)) return null
        // The tokenizer reads each piece on its own (二人 as に・にん), which is
        // exactly what these words don't do, so the dictionary's reading wins.
        val pieces = Kana.toHiragana(group.joinToString("") { it.readingOrSurface })
        val match = lexicon.find(text, null)?.takeIf { it.reading != pieces } ?: return null
        return plain(group, WordKind.WORD, match.wordId).copy(reading = match.reading, ruby = ruby(text, match.reading)) to end
    }

    /** 来る after ～ないで, "come without doing": a verb of its own, though the tokenizer takes it for ～てくる's. */
    private fun comesWithout(tokens: List<Morpheme>, i: Int): Boolean =
        tokens[i].pos == "動詞" && tokens[i].base in setOf("くる", "来る") && tokens.getOrNull(i - 1)?.base == "で" &&
            tokens.getOrNull(i - 2)?.base == "ない"

    /** Where the conjugation attached to a verb or adjective at [from] - 1 ends. */
    private fun chainEnd(tokens: List<Morpheme>, from: Int): Int {
        var i = from
        while (i < tokens.size) {
            val t = tokens[i]
            val prev = tokens[i - 1]
            val attaches = when {
                t.pos == "助動詞" -> true
                t.pos == "動詞" && t.pos2 == "接尾" -> true
                t.pos == "助詞" && t.pos2 == "接続助詞" && t.base in CONNECTIVES -> true
                t.pos == "助詞" && t.pos2 == "並立助詞" && t.base == "たり" -> true
                comesWithout(tokens, i) -> false
                t.pos == "動詞" && t.pos2 == "非自立" ->
                    prev.base in setOf("て", "で", "ば") || t.base in CONTRACTIONS
                // The tokenizer sometimes takes ちゃう for a verb of its own (帰っちゃう).
                t.pos == "動詞" && t.base in CONTRACTIONS -> prev.form == "連用タ接続"
                t.pos == "形容詞" && t.pos2 == "非自立" -> true
                t.pos == "名詞" && t.base == "そう" && (t.pos3 == "助動詞語幹" || t.pos2 == "接尾") ->
                    prev.form.startsWith("連用") || prev.form == "ガル接続"
                else -> false
            }
            if (!attaches) break
            i++
        }
        return i
    }

    private fun inflected(group: List<Morpheme>): ReadingWord {
        val head = group[0]
        val suru = head.isSuruNoun && group.getOrNull(1)?.isSuru == true
        val lemma = if (suru) head.surface else head.base
        val reading = if (suru) head.readingOrSurface else baseReading(head)
        val id = if (suru) {
            lexicon.find(head.surface + "する", reading + "する") ?: lexicon.find(head.surface, reading)
        } else {
            lexicon.find(head.base, reading)
        }?.wordId
        val word = ReadingWord(
            surface = group.joinToString("") { it.surface },
            lemma = if (suru) lemma + "する" else lemma,
            reading = group.joinToString("") { it.readingOrSurface },
            kind = WordKind.WORD,
            wordId = id,
            conjugation = labels(group),
            ruby = group.flatMap { ruby(it.surface, it.readingOrSurface) },
        )
        if (suru || !head.type.startsWith("一段")) return word
        val (verb, verbText) = potential(head.base, reading) ?: return word
        return word.copy(lemma = verbText, wordId = verb.wordId, conjugation = (listOf("potential") + word.conjugation).distinct())
    }

    /**
     * The う verb an ichidan verb [base], read [reading], is the potential form
     * of, with its spelling: 話す for 話せる, since the tokenizer takes 話せる
     * and 行ける for verbs of their own. Null when there's no such verb, or
     * when the dictionary has [base] as a verb with a meaning of its own (売れる
     * sells well, 開ける opens). A verb usually written in kana but written
     * here in kanji is the potential: 行ける is "can go", not いける "to be good at".
     */
    private fun potential(base: String, reading: String): Pair<LexiconMatch, String>? {
        // 来れる, the spoken potential of 来る ("can come"), the one irregular verb with one.
        if (base == "来れる" || base == "これる") return lexicon.find("来る", "くる")?.let { it to "来る" }
        val godan = godanOf(base) ?: return null
        val godanReading = godanOf(reading) ?: return null
        val verb = lexicon.verbs(godan).firstOrNull { it.godan && Kana.toHiragana(it.kana) == godanReading } ?: return null
        val own = lexicon.verbs(base).filter { Kana.toHiragana(it.kana) == reading }
        if (own.any { !it.potential && !(it.usuallyKana && base.any(::isKanji)) }) return null
        return LexiconMatch(verb.id, godanReading) to godan
    }

    private fun content(t: Morpheme): ReadingWord {
        // Numbers written in kanji (三, 百, 何) and counters (時, 円, 人) are words to learn; digits aren't.
        val name = t.pos2 == "固有名詞" || (t.isNumber && t.surface.any(Char::isDigit)) ||
            (t.pos2 == "接尾" && !t.isCounter) || (t.unknown && Kana.isKana(t.surface))
        val id = if (t.unknown && name) null else lexicon.find(t.base, t.readingOrSurface)?.wordId
        return plain(listOf(t), if (name) WordKind.NAME else WordKind.WORD, id)
    }

    private fun plain(group: List<Morpheme>, kind: WordKind, id: Long?): ReadingWord {
        val surface = group.joinToString("") { it.surface }
        return ReadingWord(
            surface = surface,
            lemma = if (group.size == 1) group[0].base else surface,
            reading = group.joinToString("") { it.readingOrSurface },
            kind = kind,
            wordId = id,
            conjugation = emptyList(),
            ruby = if (kind == WordKind.PUNCTUATION) listOf(Ruby(surface, null)) else group.flatMap { ruby(it.surface, it.readingOrSurface) },
        )
    }

    companion object {
        private const val MAX_COMPOUND = 4
        private val CONNECTIVES = setOf("て", "で", "ば", "ながら", "つつ")
        private val CONJUNCTIONS = setOf("でも", "けど", "だけど", "だって")
        private val SET_PHRASES = listOf("ただいま", "おかえりなさい", "おかえり", "いってきます", "いってらっしゃい")
        private val CONTRACTIONS = setOf("ちゃう", "じゃう", "ちまう", "とく", "てる", "でる")
        private val E_TO_U = mapOf('え' to 'う', 'け' to 'く', 'せ' to 'す', 'て' to 'つ', 'ね' to 'ぬ', 'べ' to 'ぶ', 'め' to 'む', 'れ' to 'る', 'げ' to 'ぐ')

        /** The う verb whose potential [text] would be: 話せる to 話す. Null when it can't be one. */
        private fun godanOf(text: String): String? {
            if (text.length < 2 || !text.endsWith("る")) return null
            val u = E_TO_U[text[text.length - 2]] ?: return null
            return text.dropLast(2) + u
        }

        /** Loading the IPADIC dictionary takes a moment, so it happens once, on first use. */
        val tokenizer: Tokenizer by lazy { Tokenizer() }

        /** What a conjugated word's attached pieces do, in order and without repeats. */
        internal fun labels(group: List<Morpheme>): List<String> {
            val out = LinkedHashSet<String>()
            val head = group[0]
            val rest = group.drop(1)
            val chain = rest.joinToString("") { it.surface }
            if (MUST.any { chain.contains(it) }) out += "must"
            for ((i, t) in rest.withIndex()) {
                val next = rest.getOrNull(i + 1)
                when {
                    "must" in out && t.base in setOf("ない", "ば", "なる", "いける", "ぬ", "ん", "ちゃ", "て", "は") &&
                        MUST.any { chain.contains(it) } -> {}
                    t.pos == "動詞" && t.pos2 == "接尾" -> when (t.base) {
                        "させる", "せる", "す", "さす" -> out += "causative"
                        // On る verbs (and 来る) it's "can": 食べられる, and 食べれる in speech.
                        "れる", "られる" -> (if (i == 0) head else rest[i - 1]).let { prev ->
                            out += if (prev.pos2 != "接尾" && (prev.type.startsWith("一段") || prev.type.startsWith("カ変"))) "potential" else "passive"
                        }
                    }
                    t.pos == "助動詞" -> when (t.base) {
                        "ます" -> out += "polite"
                        "ない", "ぬ", "ん" -> out += "negative"
                        "た", "だ" -> when {
                            // The copula だ (of na-adjectives) only matters in だろう; its past comes from た.
                            t.type != "特殊・タ" -> if (t.form.startsWith("未然") && next?.base == "う") out += "probably"
                            t.form == "仮定形" -> out += "conditional (if/when)"
                            else -> out += "past"
                        }
                        "たい" -> out += "want to"
                        "う", "よう" -> if (rest.getOrNull(i - 1)?.form?.startsWith("未然") == true && rest.getOrNull(i - 1)?.base in setOf("だ", "です")) {
                            // だろう and でしょう
                        } else {
                            out += "volitional (let's)"
                        }
                        "まい" -> out += "negative volitional"
                        "です" -> {
                            out += "polite"
                            if (t.form.startsWith("未然") && next?.base == "う") out += "probably"
                        }
                        "らしい" -> out += "seems"
                    }
                    t.pos == "助詞" -> when (t.base) {
                        "て", "で" -> when {
                            next == null -> out += "te-form"
                            // ～てた, ～ていた with the い dropped.
                            next.pos == "助動詞" && next.type == "特殊・タ" -> out += "ongoing or resulting state"
                        }
                        "ば" -> out += "conditional (if)"
                        "ながら" -> out += "while"
                        "つつ" -> out += "while"
                        "たり" -> out += "things like"
                    }
                    t.pos == "動詞" && (t.pos2 == "非自立" || t.base in CONTRACTIONS) -> {
                        val meaning = AUXILIARY_VERBS[t.base]
                        if (meaning != null) out += meaning
                        if (t.form.startsWith("命令") && t.base != "くださる") out += "imperative"
                    }
                    t.pos == "形容詞" && t.pos2 == "非自立" -> when (t.base) {
                        "やすい" -> out += "easy to"
                        "にくい", "づらい" -> out += "hard to"
                        "ほしい" -> out += "want someone to"
                    }
                    t.pos == "名詞" && t.base == "そう" -> out += "looks like"
                }
            }
            if (head.form.startsWith("命令")) out.add("imperative")
            if (rest.isEmpty()) {
                when {
                    head.pos == "形容詞" && head.form == "連用テ接続" -> out += "adverb (-ly)"
                    head.pos == "形容詞" && head.form == "ガル接続" -> out += "stem"
                    head.pos == "動詞" && head.form == "連用形" -> out += "stem (continuing)"
                }
            }
            return out.toList()
        }

        private val MUST = listOf("なければなら", "なければいけ", "なくてはなら", "なくてはいけ", "なきゃ", "なくちゃ")

        private val AUXILIARY_VERBS = mapOf(
            "いる" to "ongoing or resulting state",
            "おる" to "ongoing (humble)",
            "ある" to "has been done",
            "おく" to "in advance",
            "とく" to "in advance",
            "しまう" to "completely or regrettably",
            "ちゃう" to "completely or regrettably",
            "じゃう" to "completely or regrettably",
            "ちまう" to "completely or regrettably",
            "みる" to "try",
            "くる" to "toward now",
            "いく" to "from now on",
            "もらう" to "get someone to",
            "いただく" to "get someone to (humble)",
            "くれる" to "for me",
            "くださる" to "please",
            "あげる" to "for someone",
            "やる" to "for someone",
            "てる" to "ongoing or resulting state",
            "でる" to "ongoing or resulting state",
        )

        /**
         * The reading of a conjugated word's dictionary form, from the reading of
         * its conjugated stem: 読ん (よん) and 読む give よむ. Only used to choose
         * between homographs, so an occasional miss (来る) does no harm.
         */
        internal fun baseReading(t: Morpheme): String {
            val reading = t.readingOrSurface
            if (t.surface == t.base) return reading
            val common = t.surface.commonPrefixWith(t.base).length
            val surfaceTail = t.surface.length - common
            if (surfaceTail > reading.length) return reading
            return reading.dropLast(surfaceTail) + Kana.toHiragana(t.base.substring(common))
        }

        /**
         * Furigana for one written piece: kana in the spelling is matched against
         * the reading so only the kanji get a reading above them (食べ -> 食[た]べ).
         */
        fun ruby(surface: String, reading: String): List<Ruby> {
            if (!surface.any(::isKanji)) return listOf(Ruby(surface, null))
            val runs = ArrayList<Pair<String, Boolean>>()
            for (c in surface) {
                val kanji = isKanji(c)
                if (runs.isNotEmpty() && runs.last().second == kanji) {
                    runs[runs.size - 1] = runs.last().first + c to kanji
                } else {
                    runs += c.toString() to kanji
                }
            }
            val pattern = runs.joinToString("", "^", "$") { (text, kanji) ->
                if (kanji) "(.+?)" else "(" + Regex.escape(Kana.toHiragana(text)) + ")"
            }
            val match = Regex(pattern).find(Kana.toHiragana(reading)) ?: return listOf(Ruby(surface, reading))
            return runs.mapIndexed { i, (text, kanji) -> Ruby(text, if (kanji) match.groupValues[i + 1] else null) }
        }

        private fun isKanji(c: Char) = c in '一'..'鿿' || c in '㐀'..'䶿' || c == '々'
    }
}

/** One Kuromoji token with its IPADIC features named. */
internal class Morpheme(token: Token) {
    val surface: String = token.surface
    val pos: String = token.partOfSpeechLevel1
    val pos2: String = token.partOfSpeechLevel2
    val pos3: String = token.partOfSpeechLevel3
    val form: String = token.conjugationForm
    val type: String = token.conjugationType
    val unknown: Boolean = token.baseForm == "*"
    val base: String = if (unknown) surface else token.baseForm
    private val reading: String? = token.reading.takeIf { it != "*" }?.let(Kana::toHiragana)

    /** The reading in hiragana, or the surface if the tokenizer has none. */
    val readingOrSurface: String get() = reading ?: Kana.toHiragana(surface)

    val isInflectingHead get() = (pos == "動詞" || pos == "形容詞") && pos2 == "自立"
    val isSuruNoun get() = pos == "名詞" && pos2 == "サ変接続"
    val isSuru get() = pos == "動詞" && base == "する"
    val isNumber get() = pos == "名詞" && pos2 == "数"
    /** A counter after a number: 時, 円, 人, つ. */
    val isCounter get() = pos == "名詞" && pos2 == "接尾" && pos3 == "助数詞"
}
