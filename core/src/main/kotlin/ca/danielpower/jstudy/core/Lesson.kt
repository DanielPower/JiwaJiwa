package ca.danielpower.jstudy.core

/** One row of a grammar topic's forms table: a form you know, the new form made from it, and a few words of English. */
data class FormRow(val from: String, val to: String, val english: String)

/** A grammar topic's forms table, shown after its first example: column headings for [FormRow.from] and [FormRow.to], and the rows. */
data class LessonForms(val head: List<String>, val rows: List<FormRow>)

/**
 * A piece of a grammar page's English: plain text, or Japanese in it, read
 * like the examples so it can be tapped. A lone kana or a written ending
 * (た, ～てる) is plain text: read on its own it would be some other word.
 */
sealed interface LessonPiece {
    data class English(val text: String) : LessonPiece

    /** Japanese, read as the page's run number [run] (see [Lessons.runs]). */
    data class Japanese(val text: String, val run: Int) : LessonPiece
}

/** What a grammar page shows, in order. [more] is true for what's folded under "More on …". */
sealed interface LessonBlock {
    val more: Boolean

    /** The topic's summary, first. */
    data class Summary(val pieces: List<LessonPiece>) : LessonBlock {
        override val more get() = false
    }

    data class Note(val pieces: List<LessonPiece>, override val more: Boolean) : LessonBlock

    /** Example [p], a paragraph of the page's body, with its English. */
    data class Line(val p: Int, override val more: Boolean) : LessonBlock

    /** The forms table, after the first example; each cell's Japanese is one run. */
    data class Forms(val head: List<String>, val rows: List<Triple<LessonPiece.Japanese, LessonPiece.Japanese, String>>) : LessonBlock {
        override val more get() = false
    }
}

/**
 * How a grammar topic's page is laid out: the summary, then its examples with
 * the notes between them and the forms table after the first, then what's
 * folded under "More". Japanese in the English is numbered in the order shown,
 * and the story tools read each of those runs ([runs]) as the app shows them.
 */
object Lessons {
    fun blocks(topic: GrammarTopic): List<LessonBlock> {
        var run = 0
        fun pieces(text: String): List<LessonPiece> = split(text).map { (japanese, part) ->
            if (japanese && !fragment(part)) LessonPiece.Japanese(part, run++) else LessonPiece.English(part)
        }
        val page = topic.page
        val lines = paragraphs(page.body).size
        val notes = page.notes.groupBy { it.before }
        return buildList {
            add(LessonBlock.Summary(pieces(topic.summary)))
            for (p in 0..lines) {
                for (note in notes[p].orEmpty()) add(LessonBlock.Note(pieces(note.text), note.more))
                if (p == lines) break
                add(LessonBlock.Line(p, p >= topic.moreAt))
                val forms = topic.forms
                if (p == 0 && forms != null) {
                    add(LessonBlock.Forms(forms.head, forms.rows.map { r ->
                        Triple(LessonPiece.Japanese(r.from, run++), LessonPiece.Japanese(r.to, run++), r.english)
                    }))
                }
            }
        }
    }

    /** Every run of Japanese on [topic]'s page outside its examples, numbered as [blocks] numbers them. */
    fun runs(topic: GrammarTopic): List<String> = blocks(topic).flatMap { block ->
        when (block) {
            is LessonBlock.Summary -> block.pieces
            is LessonBlock.Note -> block.pieces
            is LessonBlock.Forms -> block.rows.flatMap { listOf(it.first, it.second) }
            is LessonBlock.Line -> emptyList()
        }.filterIsInstance<LessonPiece.Japanese>().map { it.text }
    }

    /**
     * Whether a run of Japanese in a page's English is a piece of a word, shown
     * as it's written rather than read for words: a lone kana (た, に, だ), or
     * an ending written with ～ (～てる, ～った). Read on their own they'd be
     * some unrelated word (たい as 対, versus) or another lesson's grammar.
     */
    fun fragment(run: String): Boolean {
        if (run.first() in "～〜") return true
        val text = run.filter { it !in MARKS }
        return text.length == 1 && Kana.isKana(text)
    }

    /** A line as a lesson and a chapter can both write it: without its speaker, the 「」 around it, or spaces. */
    fun lineKey(text: String): String = Speech.of(text).text.filter { it !in "「」 　" }

    /**
     * Whether a lesson's [line] is a chapter's [sentence]: the same, or its
     * end after a pause, with a name or a lead-in left off (雨、降ってるよ！
     * for アレックス、雨、降ってるよ！; 私、大丈夫かな。 for ソラ…私、大丈夫かな。).
     */
    fun sameLine(line: String, sentence: String): Boolean {
        val a = lineKey(line)
        val b = lineKey(sentence)
        return a == b || (b.length > a.length && b.endsWith(a) && b[b.length - a.length - 1] in PAUSES)
    }

    /** The id the story tools give [topic]'s runs, read, beside its page (grammar/tara/runs). */
    fun runsId(topic: GrammarTopic) = topic.page.id + "/runs"

    /**
     * English cut into its runs of Japanese and the rest, each with whether
     * it's Japanese: "add ら: 勝ったら, if" is English, ら, English, 勝ったら,
     * English. A run is kana or kanji, with any Japanese punctuation or ～ in it.
     */
    fun split(text: String): List<Pair<Boolean, String>> {
        val out = ArrayList<Pair<Boolean, String>>()
        var last = 0
        for (m in JAPANESE.findAll(text)) {
            if (m.range.first > last) out += false to text.substring(last, m.range.first)
            out += true to m.value
            last = m.range.last + 1
        }
        if (last < text.length) out += false to text.substring(last)
        return out
    }

    private const val MARKS = "～〜「」『』、。！？…"
    private const val PAUSES = "、。！？…"

    // Kana, kanji and the marks that go with them; a run needs at least one kana or kanji.
    private val JAPANESE = Regex("[～〜「」『』、。！？…ー]*[\\u3040-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF々][\\u3040-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF々～〜「」『』、。！？…ー]*")
}
