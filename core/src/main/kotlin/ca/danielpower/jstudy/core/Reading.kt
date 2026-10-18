package ca.danielpower.jstudy.core

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

/**
 * One Kuromoji token with its IPADIC features named. [Reader] repairs the
 * odd token the tokenizer gets wrong, so a morpheme can also be made up.
 */
internal class Morpheme(
    val surface: String,
    val pos: String,
    val pos2: String,
    val pos3: String,
    val form: String,
    val type: String,
    val base: String,
    reading: String?,
    val unknown: Boolean = false,
) {
    private val reading: String? = reading?.let(Kana::toHiragana)

    /** The reading in hiragana, or the surface if the tokenizer has none. */
    val readingOrSurface: String get() = reading ?: Kana.toHiragana(surface)

    val isInflectingHead get() = (pos == "動詞" || pos == "形容詞") && pos2 == "自立"
    val isSuruNoun get() = pos == "名詞" && pos2 == "サ変接続"
    val isSuru get() = pos == "動詞" && base == "する"
    val isNumber get() = pos == "名詞" && pos2 == "数"
    /** A counter after a number: 時, 円, 人, つ. */
    val isCounter get() = pos == "名詞" && pos2 == "接尾" && pos3 == "助数詞"

    fun copy(
        surface: String = this.surface,
        pos: String = this.pos,
        pos2: String = this.pos2,
        pos3: String = this.pos3,
        form: String = this.form,
        type: String = this.type,
        base: String = this.base,
        reading: String? = this.reading,
    ) = Morpheme(surface, pos, pos2, pos3, form, type, base, reading, unknown)

    override fun toString() = "$surface($pos,$pos2,$pos3,$form,$type,$base,$readingOrSurface)"
}
