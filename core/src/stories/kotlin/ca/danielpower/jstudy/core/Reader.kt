package ca.danielpower.jstudy.core

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer

/**
 * A dictionary entry matched to a word, and the entry's reading of that
 * spelling in hiragana. [jlpt] is its level (0 for none) and [classes] what
 * kinds of word its senses are, when the dictionary says.
 */
data class LexiconMatch(val wordId: Long, val reading: String, val jlpt: Int = 0, val classes: Set<WordClass> = emptySet())

/** What kind of word a dictionary entry is, as far as telling homographs apart goes. */
enum class WordClass(vararg val codes: String) {
    NOUN("n", "pn", "adj-no", "num", "ctr"),
    VERB("v"),
    I_ADJECTIVE("adj-i", "adj-ix"),
    NA_ADJECTIVE("adj-na"),
    ADVERB("adv", "n-adv", "n-t"),
    INTERJECTION("int"),
    PRENOUN("adj-pn"),
    CONJUNCTION("conj"),
    ;

    companion object {
        /** The classes of the JMdict part-of-speech codes [codes] (n, v5k, adj-i, …). */
        fun of(codes: Collection<String>): Set<WordClass> =
            codes.flatMap { code -> entries.filter { c -> c.codes.any { code == it || (code.startsWith(it) && it in PREFIXES) } } }.toSet()

        private val PREFIXES = setOf("v", "adv", "n")

        /** The class IPADIC gives [m], when it's one a dictionary entry can have. */
        internal fun of(m: Morpheme): WordClass? = when (m.pos) {
            "名詞" -> when (m.pos2) {
                "形容動詞語幹" -> NA_ADJECTIVE
                // Suffixes (さん, 時) and dependent nouns are filed every which way.
                "接尾", "非自立" -> null
                // A noun that works as an adverb: いつか "someday", not 五日.
                "副詞可能" -> ADVERB
                else -> NOUN
            }
            "動詞" -> VERB
            "形容詞" -> I_ADJECTIVE
            "副詞" -> ADVERB
            "感動詞" -> INTERJECTION
            "連体詞" -> PRENOUN
            "接続詞" -> CONJUNCTION
            else -> null
        }
    }
}

/** Finds dictionary entries for the words in a story. */
fun interface Lexicon {
    /**
     * The entry best matching a word spelled [text], or null. [reading] is the
     * word's reading in hiragana when known, to choose between homographs.
     */
    fun find(text: String, reading: String?): LexiconMatch?

    /** As [find], preferring an entry of the kind of word [wordClass] says (五日 is a noun, いつか "someday" an adverb). */
    fun find(text: String, reading: String?, wordClass: WordClass?): LexiconMatch? = find(text, reading)

    /** The verbs spelled [text], for telling a potential form (話せる) from a verb of its own (売れる). See [verbEntries]. */
    fun verbs(text: String): List<VerbEntry> = emptyList()
}

/**
 * A dictionary entry for a verb. [potential] when the dictionary says it's a
 * potential form itself: its first meaning is "to be able to …" (話せる,
 * 読める), or a sense notes it (取れる, 撮れる).
 */
data class VerbEntry(val id: Long, val kana: String, val godan: Boolean, val usuallyKana: Boolean, val potential: Boolean, val jlpt: Int = 0)

/** Every sense of the entries with a spelling (the one argument), for [verbEntries]. */
const val VERBS_SQL =
    "SELECT w.id, w.kana, w.usually_kana, s.pos, s.gloss, s.info, w.jlpt FROM form f JOIN word w ON w.id = f.word_id " +
        "JOIN sense s ON s.word_id = w.id WHERE f.text = ? ORDER BY w.id, s.ord"

/** One row of [VERBS_SQL]: parts of speech and glosses as the JSON arrays the dictionary stores. */
data class SenseRow(val id: Long, val kana: String, val usuallyKana: Boolean, val pos: String, val gloss: String, val info: String?, val jlpt: Int = 0)

/** The verbs among the entries [rows] (from [VERBS_SQL]) describe. */
fun verbEntries(rows: List<SenseRow>): List<VerbEntry> = rows.groupBy { it.id }.values.mapNotNull { senses ->
    val first = senses.first()
    if (!first.pos.contains("\"v")) return@mapNotNull null
    val able = first.gloss.startsWith("[\"to be able to") || first.gloss.startsWith("[\"to be possible to") ||
        senses.any { it.info?.contains("potential") == true }
    VerbEntry(first.id, first.kana, first.pos.contains("\"v5"), first.usuallyKana, able, first.jlpt)
}

/** A dictionary entry with a spelling a word was written in, for [bestHomograph]. [classes] are its senses' kinds of word. */
data class Homograph(val id: Long, val kana: String, val jlpt: Int, val rank: Int, val classes: Set<WordClass> = emptySet())

/**
 * The entries that have a spelling (the one argument), for [bestHomograph]: id, kana, JLPT level, frequency rank,
 * and every sense's parts of speech (JSON arrays joined by |).
 */
const val HOMOGRAPHS_SQL =
    "SELECT w.id, w.kana, w.jlpt, COALESCE(w.freq_rank, 1000000000), (SELECT group_concat(s.pos, '|') FROM sense s WHERE s.word_id = w.id) " +
        "FROM form f JOIN word w ON w.id = f.word_id WHERE f.text = ?"

/**
 * The entry for a word among the [homographs] spelled the way it is. One with
 * a kana spelling matching [reading] wins, then one of the kind of word
 * [wordClass] says (いつか the adverb, not 五日), then JLPT words, then the
 * more frequent. JLPT only breaks ties here: the frequency list ranks
 * spellings, not entries, so a rare homograph shares a common word's rank
 * (右舞 has うまい's), and the JLPT lists name the entry learners mean.
 * [forms] gives an entry's spellings.
 */
fun bestHomograph(homographs: List<Homograph>, reading: String?, wordClass: WordClass? = null, forms: (Long) -> List<String>): LexiconMatch? {
    val candidates = homographs.distinctBy { it.id }
    if (candidates.isEmpty()) return null
    val wanted = reading?.let(Kana::toHiragana)
    val readingMatches = if (wanted == null) emptySet() else candidates.filter { cand ->
        Kana.toHiragana(cand.kana) == wanted || forms(cand.id).any { Kana.isKana(it) && Kana.toHiragana(it) == wanted }
    }.map { it.id }.toSet()
    val best = candidates.minWith(
        compareBy<Homograph>({ it.id !in readingMatches }, { wordClass != null && wordClass !in it.classes }, { it.jlpt == 0 }, { it.rank }, { it.id }),
    )
    return LexiconMatch(best.id, if (best.id in readingMatches) wanted!! else Kana.toHiragana(best.kana), best.jlpt, best.classes)
}

/** Splits Japanese text into words with Kuromoji (IPADIC) and matches them to the dictionary. */
class Reader(private val lexicon: Lexicon) {

    /**
     * Paragraphs of words. Blank lines and single line breaks both start a new paragraph.
     * [readings] pins a word the reader gets wrong to the right one; see [words].
     */
    fun read(text: String, readings: Map<String, String> = emptyMap(), names: Set<String> = emptySet()): List<List<ReadingWord>> =
        paragraphs(text).map { words(Speech.of(it).text, readings, names) }

    /**
     * [readings] maps a word, written as the text writes it, to its reading,
     * for the odd word the reader gets wrong: 方 in あの方 is かた, not the
     * ほう of ～のほうが. A pinned word is looked up by that reading, and
     * left as it was if the dictionary doesn't have it. A pinned dictionary
     * form pins its conjugations too: 描く read かく makes 描いた かいた.
     * [names] are always names, never words: リン the guitarist, not 燐.
     */
    fun words(paragraph: String, readings: Map<String, String> = emptyMap(), names: Set<String> = emptySet()): List<ReadingWord> {
        val tokens = purposeStems(loanwords(Tokens.repair(tokenizer.tokenize(paragraph).map { it.morpheme() }), names))
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
                    out += bareStem(t, tokens.getOrNull(end), end == i + 1) ?: inflected(tokens.subList(i, end), tokens.getOrNull(end))
                    i = end
                }
                t.pos == "名詞" && t.pos2 == "形容動詞語幹" -> {
                    // A na-adjective takes the copula as its conjugation (きれいだった),
                    // but plain だ or な is grammar of its own.
                    val end = chainEnd(tokens, i + 1)
                    val group = tokens.subList(i, end)
                    if (end > i + 1 && labels(group).isNotEmpty()) {
                        out += inflected(group, tokens.getOrNull(end))
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
        val pins = DEFAULT_READINGS + readings
        val said = out.mapIndexed { w, word -> if (saidNan(word, tokens.getOrNull(spans[w].last + 1), tokens.getOrNull(spans[w].last + 2))) word.copy(reading = "なん", ruby = ruby(word.surface, "なん")) else word }
        val read = said.map { w -> if (w.surface in names) w.copy(kind = WordKind.NAME, wordId = null) else pinned(w, pins) }
        return stutters(joinGrammar(withGrammar(read, spans, tokens)))
    }

    /**
     * A stuttered start (ご、ごめん) is the word to come, not a word of its
     * own: it's left as plain text, neither counted nor explained.
     */
    /**
     * Grammar pieces side by side in the same grammar points are one tap:
     * でした, not でし and た, both explaining です / だ. Pieces of different
     * points stay apart (だっ and たら). The word keeps its first piece's
     * dictionary form (です for でした).
     */
    private fun joinGrammar(words: List<ReadingWord>): List<ReadingWord> {
        val runs = ArrayList<MutableList<ReadingWord>>()
        val newIndex = IntArray(words.size)
        words.forEachIndexed { w, word ->
            val last = runs.lastOrNull()?.last()
            if (last != null && word.kind == WordKind.GRAMMAR && last.kind == WordKind.GRAMMAR && word.grammar.isNotEmpty() && word.grammar == last.grammar) {
                runs.last() += word
            } else {
                runs += mutableListOf(word)
            }
            newIndex[w] = runs.size - 1
        }
        if (runs.size == words.size) return words
        return runs.map { run ->
            val first = run.first()
            first.copy(
                surface = run.joinToString("") { it.surface },
                reading = run.joinToString("") { it.reading },
                ruby = run.flatMap { it.ruby },
                particles = run.runningFold(0) { at, w -> at + w.surface.length }.zip(run).flatMap { (at, w) -> w.particles.map { at + it } },
                // Joined, the last word's end is no longer where a point ends inside it.
                grammar = first.grammar.map { it.copy(first = newIndex[it.first], last = newIndex[it.last], to = it.to.takeIf { _ -> runs[newIndex[it.last]].size == 1 }) },
            )
        }
    }

    private fun stutters(words: List<ReadingWord>): List<ReadingWord> = words.mapIndexed { k, w ->
        val next = words.getOrNull(k + 2)
        if (w.kind != WordKind.PUNCTUATION && w.surface.length <= 2 && Kana.isKana(w.surface) && words.getOrNull(k + 1)?.surface == "、" &&
            next != null && next.surface.length > w.surface.length && next.surface.startsWith(w.surface)
        ) {
            ReadingWord(w.surface, w.surface, w.reading, WordKind.PUNCTUATION, null, emptyList(), listOf(Ruby(w.surface, null)))
        } else {
            w
        }
    }

    /**
     * A verb's stem on its own at the end of a clause (終わり。, 休みは) is
     * the noun the dictionary has for it: "the end", not "ending, and…".
     */
    private fun bareStem(t: Morpheme, next: Morpheme?, alone: Boolean): ReadingWord? {
        if (!alone || t.pos != "動詞" || t.form != "連用形") return null
        val ends = next == null || (next.pos == "記号" && next.pos2 != "読点") ||
            (next.pos == "助詞" && next.base in setOf("は", "が", "も", "を", "の")) || (next.pos == "助動詞" && next.base in setOf("だ", "です"))
        if (!ends) return null
        val noun = lexicon.find(t.surface, t.readingOrSurface, WordClass.NOUN)?.takeIf { WordClass.NOUN in it.classes } ?: return null
        return plain(listOf(t), WordKind.WORD, noun.wordId).copy(lemma = t.surface, reading = noun.reading, ruby = ruby(t.surface, noun.reading))
    }

    /**
     * A verb's stem the tokenizer takes for its noun before ～に行く (送りに
     * 行く, "go to see off"): the verb, so ～に行く explains it, unless the
     * noun is a word learners know.
     */
    private fun purposeStems(tokens: List<Morpheme>): List<Morpheme> = tokens.mapIndexed { k, t ->
        val after = tokens.getOrNull(k + 2)
        if (t.pos != "名詞" || t.pos2 != "一般" || tokens.getOrNull(k + 1)?.base != "に" || after?.pos != "動詞" || after.base !in MOVING) return@mapIndexed t
        val u = I_TO_U[t.surface.last()] ?: return@mapIndexed t
        val verb = t.surface.dropLast(1) + u
        // A noun of its own is where you go (祭りに行く); only a rare one is the stem (送りに行く).
        val godan = lexicon.verbs(verb).filter { it.godan }.maxOfOrNull { it.jlpt } ?: return@mapIndexed t
        if (godan == 0 || (lexicon.find(t.surface, t.readingOrSurface, WordClass.NOUN)?.jlpt ?: 0) > 0) return@mapIndexed t
        t.copy(pos = "動詞", pos2 = "自立", pos3 = "*", form = "連用形", type = "五段", base = verb)
    }

    /**
     * Whether [word] is 何 said なん, before [next] and then [after]: before
     * だ and です, の, quoting と, って and なんて, and でも (何ですか, 何の仕事,
     * 何でもない). The tokenizer says なに everywhere but before a counter;
     * 何を, 何が, 何か and 何も are なに. It stays the word 何 (なに), "what",
     * so it's one card, not two: the dictionary's なん is the same word.
     */
    private fun saidNan(word: ReadingWord, next: Morpheme?, after: Morpheme?): Boolean {
        if (word.surface != "何" || word.kind != WordKind.WORD || word.reading != "なに" || next == null) return false
        return when {
            next.pos == "助動詞" -> next.base in setOf("だ", "です")
            next.pos != "助詞" -> false
            next.base == "で" -> after?.base == "も"
            next.base == "と" -> next.pos3 == "引用"
            else -> next.base in setOf("の", "って", "て", "でも", "じゃ")
        }
    }

    /**
     * A loanword newer than the tokenizer, which it sometimes splits into
     * pieces it does know (スマホで、 as スマ・ホ・で, though スマホで見た
     * comes whole): one token, as the tokenizer gives a word it doesn't know,
     * when the dictionary has it. The で after it is then the particle, as
     * the tokenizer reads it after any noun it knows (電話で、), not だ.
     */
    private fun loanwords(tokens: List<Morpheme>, names: Set<String>): List<Morpheme> {
        fun piece(t: Morpheme) = t.pos == "名詞" && t.surface.all { it in 'ァ'..'ヺ' || it == 'ー' } && t.surface !in names
        val out = ArrayList<Morpheme>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            var end = i
            while (end < tokens.size && piece(tokens[end])) end++
            // Only a run with a piece the tokenizer shouldn't have found there (a name, a dependent noun);
            // runs of ordinary nouns are compounds, joined later.
            val stop = (end downTo i + 2).firstOrNull { stop ->
                val run = tokens.subList(i, stop)
                val text = run.joinToString("") { it.surface }
                run.any { it.pos2 == "固有名詞" || it.pos2 == "非自立" || it.unknown } &&
                    lexicon.find(text, Kana.toHiragana(text))?.reading == Kana.toHiragana(text)
            }
            if (stop == null) {
                out += tokens[i++]
                continue
            }
            val text = tokens.subList(i, stop).joinToString("") { it.surface }
            out += Morpheme(text, "名詞", "一般", "*", "*", "*", text, null, unknown = true)
            val next = tokens.getOrNull(stop)
            i = stop
            if (next != null && next.surface == "で" && next.pos == "助動詞") {
                out += Morpheme("で", "助詞", "格助詞", "一般", "*", "*", "で", "で")
                i++
            }
        }
        return out
    }

    /** [word] as [readings] reads it, if it pins it and the dictionary has it with that reading. */
    private fun pinned(word: ReadingWord, readings: Map<String, String>): ReadingWord {
        val reading = readings[word.surface] ?: return conjugationPinned(word, readings)
        val match = (lexicon.find(word.surface, reading) ?: lexicon.find(word.lemma, reading))?.takeIf { it.reading == reading }
            ?: return word
        return word.copy(kind = WordKind.WORD, wordId = match.wordId, reading = reading, ruby = ruby(word.surface, reading))
    }

    /** A conjugated [word] whose dictionary form [readings] pins, read the pinned way: 描いた as かいた for 描く read かく. */
    private fun conjugationPinned(word: ReadingWord, readings: Map<String, String>): ReadingWord {
        if (word.kind != WordKind.WORD || word.lemma == word.surface) return word
        val reading = readings[word.lemma] ?: return word
        val match = lexicon.find(word.lemma, reading)?.takeIf { it.reading == reading } ?: return word
        // The kanji the two share keep the pinned reading; the kana after them are read as written.
        val stem = word.lemma.takeWhile { !Kana.isKana(it.toString()) }
        val lemmaTail = Kana.toHiragana(word.lemma.drop(stem.length))
        val tail = word.surface.removePrefix(stem)
        if (stem.isEmpty() || tail == word.surface || !Kana.isKana(tail) || !reading.endsWith(lemmaTail)) return word
        val surfaceReading = reading.dropLast(lemmaTail.length) + Kana.toHiragana(tail)
        return word.copy(wordId = match.wordId, reading = surfaceReading, ruby = ruby(word.surface, surfaceReading))
    }

    /**
     * Where a point starts in [word], [at] characters in by its tokens: where
     * one token is both the word and its form (話せる, 行け), after what it
     * shares with its dictionary form (話す, 行く), so the word itself isn't
     * counted as the grammar.
     */
    private fun from(word: ReadingWord, at: Int): Int =
        if (at == 0 && word.kind == WordKind.WORD && word.conjugated) word.surface.commonPrefixWith(word.lemma).length else at

    private fun withGrammar(words: List<ReadingWord>, spans: List<IntRange>, tokens: List<Morpheme>): List<ReadingWord> {
        val wordOf = IntArray(tokens.size)
        spans.forEachIndexed { w, span -> for (k in span) wordOf[k] = w }
        // A set phrase or compound read whole (いってきます) has no grammar inside it.
        val whole = words.indices.filter { words[it].kind == WordKind.WORD && words[it].lemma == words[it].surface && spans[it].count() > 1 }.toSet()
        // How far into word [w] its tokens before [until] reach, in characters.
        fun reach(w: Int, until: Int) = (spans[w].first until until).sumOf { tokens[it].surface.length }.coerceAtMost(words[w].surface.length)
        val found = Grammar.find(tokens).map { m ->
            val first = wordOf[m.start]
            val last = wordOf[m.end - 1]
            GrammarUse(m.point, first, last, from = from(words[first], reach(first, m.start)), to = reach(last, m.end).takeIf { it < words[last].surface.length })
        }
            .filterNot { it.first == it.last && it.first in whole }
        // A bare stem is there for what follows it, so it joins that point: 見 in 見に行く, 暑く in 暑くなる.
        val stems = words.indices.filter { words[it].stem }.toSet()
        val joined = found.map { u ->
            // The stem is the word it belongs to, so the point still starts after it.
            if (u.first - 1 in stems && found.none { o -> u.first - 1 in o.first..o.last }) u.copy(first = u.first - 1, from = words[u.first - 1].surface.length) else u
        }
        // The tokenizer takes a う verb's potential for a verb of its own (話せる), so no pattern finds it.
        val potential = grammarPoint("potential")!!
        val uses = (
            joined + words.indices.filter { w ->
                "potential" in words[w].conjugation && joined.none { it.point == potential && w in it.first..it.last }
            }.map { GrammarUse(potential, it, it, from = from(words[it], 0)) }
            ).sortedBy { it.first }
        return words.mapIndexed { w, word ->
            val mine = uses.filter { w in it.first..it.last }
            when {
                // しれない in ～かもしれない is part of the point, not a word to learn.
                mine.any { it.point.id == "kamoshirenai" } && word.lemma in setOf("しれる", "知れる") ->
                    word.copy(kind = WordKind.GRAMMAR, wordId = null, grammar = mine)
                mine.isNotEmpty() -> word.copy(grammar = mine)
                word.conjugated && !word.stem -> phrase(word)
                // A time noun the tokenizer files with grammar but no point explains is a word: 日 in 休みの日, 最後の日です.
                word.kind == WordKind.GRAMMAR && word.surface.any(::isKanji) &&
                    (tokens[spans[w].first].pos3 == "副詞可能" || word.lemma in TIME_NOUNS) ->
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

    private fun inflected(group: List<Morpheme>, after: Morpheme?): ReadingWord {
        val head = group[0]
        val suru = head.isSuruNoun && group.getOrNull(1)?.isSuru == true
        val lemma = if (suru) head.surface else head.base
        val reading = if (suru) head.readingOrSurface else baseReading(head)
        val id = if (suru) {
            lexicon.find(head.surface + "する", reading + "する") ?: lexicon.find(head.surface, reading)
        } else {
            lexicon.find(head.base, reading, WordClass.of(head))
        }?.wordId
        val word = ReadingWord(
            surface = group.joinToString("") { it.surface },
            lemma = if (suru) lemma + "する" else lemma,
            reading = group.joinToString("") { it.readingOrSurface },
            kind = WordKind.WORD,
            wordId = id,
            conjugation = labels(group, after),
            ruby = group.flatMap { ruby(it.surface, it.readingOrSurface) },
            particles = particles(group),
        )
        if (suru || !head.type.startsWith("一段")) return word
        val (verb, verbText) = potential(head.base, reading) ?: return word
        val potential = word.copy(lemma = verbText, wordId = verb.wordId, conjugation = (listOf("potential") + word.conjugation).distinct())
        // Read as that verb's potential: 弾ける as ひける, not はじける.
        val stem = reading.dropLast(1)
        val wanted = verb.reading.dropLast(1) + (U_TO_E[verb.reading.last()] ?: return potential)
        if (stem == wanted || verbText == "来る" || !potential.reading.startsWith(stem)) return potential
        val read = wanted + potential.reading.removePrefix(stem)
        val headRead = wanted + head.readingOrSurface.removePrefix(stem)
        return potential.copy(reading = read, ruby = ruby(head.surface, headRead) + group.drop(1).flatMap { ruby(it.surface, it.readingOrSurface) })
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
        // しれる of ～かもしれない is grammar, not "can know".
        if (base == "しれる" || base == "知れる") return null
        val godan = godanOf(base) ?: return null
        val read = godanOf(reading) ?: return null
        val verbs = lexicon.verbs(godan).filter { it.godan }
        // An entry has one kana of its own, but a pinned reading may be another of its readings (描く read かく).
        val godanReading = DEFAULT_READINGS[godan]?.takeIf { r -> verbs.any { Kana.toHiragana(it.kana) == r } } ?: read
        val verb = verbs.firstOrNull { Kana.toHiragana(it.kana) == godanReading } ?: return null
        val own = lexicon.verbs(base).filter { Kana.toHiragana(it.kana) == reading }
        // A rare verb of its own loses to a common verb's potential: a story's 休めない is "can't rest", not 休める "to rest something".
        val rare = verb.jlpt > 0 && own.all { it.jlpt == 0 || it.jlpt < verb.jlpt - 2 }
        if (!rare && own.any { !it.potential && !(it.usuallyKana && base.any(::isKanji)) }) return null
        return LexiconMatch(verb.id, godanReading) to godan
    }

    private fun content(t: Morpheme): ReadingWord {
        // Numbers written in kanji (三, 百, 何) and counters (時, 円, 人) are words to learn; digits aren't.
        // Katakana the tokenizer doesn't know is a name, unless the dictionary has it: a loanword newer than
        // the tokenizer (スマホ). Cast names are read as names before this. A suffix (さん) is part of a name,
        // but not a na-adjective the tokenizer takes for one: 好き in ゲーム好き？, "like games?", is the word.
        val newWord = t.unknown && Kana.isKana(t.surface) && lexicon.find(t.surface, t.surface) != null
        val name = t.pos2 == "固有名詞" && !newWord || (t.isNumber && t.surface.any(Char::isDigit)) ||
            (t.pos2 == "接尾" && !t.isCounter && t.pos3 != "形容動詞語幹") || (t.unknown && Kana.isKana(t.surface) && !newWord)
        val match = if (t.unknown && name) null else lexicon.find(t.base, t.readingOrSurface, WordClass.of(t))
        // The tokenizer files some everyday words as names (毎日 the newspaper, 日本 the country): a word on the JLPT lists is a word.
        val word = t.pos2 == "固有名詞" && t.surface.any(::isKanji) && (match?.jlpt ?: 0) > 0
        return plain(listOf(t), if (name && !word) WordKind.NAME else WordKind.WORD, match?.wordId)
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
            particles = particles(group),
        )
    }

    /**
     * Where in [group]'s text a は or へ is said "wa" or "e", as the particle
     * is: a token ending in one that the tokenizer pronounces ending in ワ or
     * エ (は on its own, こんばんは, 実は). The particle always ends its token.
     */
    private fun particles(group: List<Morpheme>): List<Int> {
        val out = ArrayList<Int>()
        var at = 0
        for (t in group) {
            at += t.surface.length
            val said = t.pronunciation?.lastOrNull()
            when (t.surface.last()) {
                'は' -> if (said == 'ワ') out += at - 1
                'へ' -> if (said == 'エ') out += at - 1
            }
        }
        return out
    }

    companion object {
        private const val MAX_COMPOUND = 4
        private val CONNECTIVES = setOf("て", "で", "ば", "ながら", "つつ")
        private val CONJUNCTIONS = setOf("でも", "けど", "だけど", "だって")
        /** Nouns the tokenizer sometimes files as dependent (grammar) nouns, though they're words: 日 in 最後の日です. */
        private val TIME_NOUNS = setOf("日")

        /**
         * Spellings the tokenizer reads the rarer way, read the everyday way
         * unless a chapter pins them otherwise (see [words]).
         */
        val DEFAULT_READINGS = mapOf("何時" to "なんじ", "描く" to "かく", "弦" to "げん", "弾く" to "ひく", "日本" to "にほん")

        /** Verbs of going or coming that a purpose (～に行く) goes with. */
        private val MOVING = setOf("行く", "来る", "帰る", "戻る", "出る", "出かける")
        private val I_TO_U = mapOf('い' to 'う', 'き' to 'く', 'し' to 'す', 'ち' to 'つ', 'に' to 'ぬ', 'び' to 'ぶ', 'み' to 'む', 'り' to 'る', 'ぎ' to 'ぐ')

        private val SET_PHRASES = listOf("ただいま", "おかえりなさい", "おかえり", "いってきます", "いってらっしゃい")
        private val CONTRACTIONS = setOf("ちゃう", "じゃう", "ちまう", "とく", "てる", "でる")
        private val U_TO_E by lazy { E_TO_U.entries.associate { it.value to it.key } }
        private val E_TO_U = mapOf('え' to 'う', 'け' to 'く', 'せ' to 'す', 'て' to 'つ', 'ね' to 'ぬ', 'べ' to 'ぶ', 'め' to 'む', 'れ' to 'る', 'げ' to 'ぐ')

        /** The う verb whose potential [text] would be: 話せる to 話す. Null when it can't be one. */
        private fun godanOf(text: String): String? {
            if (text.length < 2 || !text.endsWith("る")) return null
            val u = E_TO_U[text[text.length - 2]] ?: return null
            return text.dropLast(2) + u
        }

        /** Loading the IPADIC dictionary takes a moment, so it happens once, on first use. */
        internal val tokenizer: Tokenizer by lazy { Tokenizer() }

        /**
         * What a conjugated word's attached pieces do, in order and without
         * repeats. [after] is what follows the word, if anything.
         */
        internal fun labels(group: List<Morpheme>, after: Morpheme? = null): List<String> {
            val out = LinkedHashSet<String>()
            val head = group[0]
            val rest = group.drop(1)
            val chain = rest.joinToString("") { it.surface }
            // A bare なきゃ or なくちゃ is "must" where it ends the clause (もう行かなきゃ。),
            // and "if not" before more of it (行かなきゃわからない).
            val unless = CONTRACTED_MUST.any { chain.endsWith(it) } && after != null &&
                after.pos != "記号" && after.pos2 != "終助詞" && after.base !in MUST_AFTER
            if (unless) out += listOf("negative", "conditional (if)")
            if (MUST.any { chain.contains(it) } && !unless) out += "must"
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
                        "う", "よう" -> (if (i == 0) head else rest[i - 1]).let { prev ->
                            when {
                                // だろう and でしょう
                                prev.form.startsWith("未然") && prev.base in setOf("だ", "です") -> {}
                                // An adjective's or a negative's presumptive: 高かろう, 行かなかろう.
                                prev.pos == "形容詞" || (prev.pos == "助動詞" && prev.base in setOf("ない", "たい")) -> out += "probably"
                                else -> out += "volitional (let's)"
                            }
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
        private val CONTRACTED_MUST = listOf("なきゃ", "なくちゃ")
        /** Words after a bare なきゃ that still make it "must": ～なきゃいけない, ～なきゃだめ. */
        private val MUST_AFTER = setOf("いける", "なる", "だめ", "ダメ", "駄目")

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

/** [token] as a [Morpheme], its IPADIC features named. */
internal fun Token.morpheme(): Morpheme {
    val unknown = baseForm == "*"
    return Morpheme(
        surface = surface,
        pos = partOfSpeechLevel1,
        pos2 = partOfSpeechLevel2,
        pos3 = partOfSpeechLevel3,
        form = conjugationForm,
        type = conjugationType,
        base = if (unknown) surface else baseForm,
        reading = reading.takeIf { it != "*" },
        unknown = unknown,
        pronunciation = pronunciation.takeIf { it != "*" },
    )
}
