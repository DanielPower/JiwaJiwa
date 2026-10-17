package ca.danielpower.jstudy.core

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

// The story tools: what's worked out from the bundled stories with the real
// dictionary, written next to them so neither the app nor anyone writing a
// story has to read every chapter. Not part of the app.
//
//   ./gradlew -PcoreOnly=true :core:stories
//
// writes path.json (tools/stories/build.py), then the index of each chapter's
// words and grammar (stories/needs.json, which the app reads in place of
// reading every chapter) and each story's SUMMARY.md. StoryPathTest checks
// they're up to date. Needs the dictionary built first.

/** Writes the index and summaries for the repository at the one argument. */
fun main(args: Array<String>) {
    StoryTools.open(File(args.single())).use { tools ->
        for ((file, text) in tools.outputs()) file.writeText(text)
        println("Wrote ${tools.needsFile.relativeTo(tools.root)} and ${tools.catalog.series.size} summaries")
    }
}

/** The dictionary the app is built with, read over JDBC, looking words up as the app does. */
class JdbcLexicon(private val conn: Connection) : Lexicon {
    override fun find(text: String, reading: String?): LexiconMatch? =
        bestHomograph(query(HOMOGRAPHS_SQL, text) { Homograph(getLong(1), getString(2), getInt(3), getInt(4)) }, reading) { id ->
            query("SELECT text FROM form WHERE word_id = ?", id.toString()) { getString(1) }
        }

    /** An entry's kana and JLPT level (0 for none). */
    data class Entry(val kana: String, val jlpt: Int)

    fun entry(id: Long): Entry? =
        query("SELECT kana, jlpt FROM word WHERE id = ?", id.toString()) { Entry(getString(1), getInt(2)) }.firstOrNull()

    /** An entry in a line of the StoryPathTest report: 来る/くる N5, with "kana" when it's usually written in kana. */
    fun describe(id: Long?): String = id?.let {
        query("SELECT COALESCE(kanji, kana), kana, jlpt, usually_kana FROM word WHERE id = ?", it.toString()) {
            "${getString(1)}/${getString(2)} N${getInt(3)}${if (getInt(4) == 1) " kana" else ""}"
        }.firstOrNull()
    }.orEmpty()

    private fun <T> query(sql: String, arg: String, row: ResultSet.() -> T): List<T> =
        conn.prepareStatement(sql).use { st ->
            st.setString(1, arg)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.row()) } }
        }
}

class StoryTools(val root: File, private val conn: Connection) : AutoCloseable {
    val lexicon = JdbcLexicon(conn)
    val reader = Reader(lexicon)
    val catalog = StoryPath.parse(File(root, "app/src/main/assets/stories/path.json").readText())
    val needsFile = File(root, "app/src/main/assets/stories/needs.json")

    /** Each chapter's needs, read with the app's own reader and lookup. */
    val needs: Map<String, StepNeeds> by lazy {
        catalog.chapters.associate { (_, c) -> c.story.id to StoryPath.needs(reader, c.story) }
    }

    private val bySeries = catalog.series.associateBy { it.id }
    private val seriesOf = catalog.series.flatMap { s -> s.chapters.map { it.story.id to s } }.toMap()

    /**
     * The chapters read before [series], in order: the stories it follows
     * (`after`), and the ones they follow. [missing] hears of an `after` that
     * names no story or chapter.
     */
    fun before(series: PathSeries, missing: (String) -> Unit = {}): List<Chapter> = series.after.flatMap { id ->
        bySeries[id]?.let { before(it, missing) + it.chapters }
            ?: seriesOf[id]?.let { s -> before(s, missing) + s.chapters.takeWhile { it.story.id != id } + s.chapters.first { it.story.id == id } }
            ?: emptyList<Chapter>().also { missing(id) }
    }.distinct()

    /** A chapter of a story read in order after the ones it follows, with what it adds. */
    data class Step(val chapter: Chapter, val own: Boolean, val needs: StepNeeds, val gap: StepGap, val words: Set<Long>, val grammar: Set<String>)

    /**
     * [series] read in order, after the stories it follows: each chapter, what
     * it has that's new by then, and the words and grammar known after it.
     */
    fun walk(series: PathSeries, missing: (String) -> Unit = {}): List<Step> {
        val words = LinkedHashSet<Long>()
        val grammar = LinkedHashSet<String>()
        return (before(series, missing) + series.chapters).map { c ->
            val n = needs.getValue(c.story.id)
            val gap = StoryPath.gap(n, { it in words }, grammar)
            words += n.words.mapNotNull { it.wordId }
            grammar += n.grammar + n.teaches
            Step(c, c in series.chapters, n, gap, words.toSet(), grammar.toSet())
        }
    }

    /** Each word, as the stories first write it in its dictionary form. */
    private val lemmas: Map<Long, String> by lazy {
        val out = HashMap<Long, String>()
        for (n in needs.values) for (w in n.words) w.wordId?.let { out.putIfAbsent(it, w.lemma) }
        out
    }

    /** What a story's SUMMARY.md says: enough to write its next chapter without reading the rest. */
    fun summary(series: PathSeries): String = buildString {
        fun word(w: StepWord) = w.wordId?.let(lexicon::entry)?.let { e ->
            if (Kana.isKana(w.lemma)) "${w.lemma} (${level(e.jlpt)})" else "${w.lemma} (${e.kana}, ${level(e.jlpt)})"
        } ?: "${w.lemma} (no entry)"
        val steps = walk(series)
        val own = steps.filter { it.own }
        appendLine("# ${series.title}")
        appendLine()
        appendLine("Written by `./gradlew -PcoreOnly=true :core:stories` from the chapter files here. Don't edit it by hand.")
        appendLine()
        appendLine(series.blurb)
        appendLine()
        appendLine("- Id `${series.id}`, ${series.chapters.size} chapter${if (series.chapters.size == 1) "" else "s"}.")
        if (series.after.isNotEmpty()) {
            appendLine("- Follows ${series.after.joinToString { id -> bySeries[id]?.let { "${it.title} (`$id`)" } ?: "`$id`" }}.")
        }
        if (series.side) appendLine("- A side story: it has nothing new to learn, no new words and no grammar topic.")
        // Someone introduced again later keeps their first chapter and takes the later description.
        val met = (before(series) + series.chapters).flatMap { c -> c.meets.map { c to it } }
            .groupBy { it.second.name }.values.map { it.first().first to it.last().second }
        if (met.isNotEmpty()) {
            appendLine()
            appendLine("## Cast")
            appendLine()
            appendLine("Everyone met by the end, in the chapter that introduces them.")
            appendLine()
            for ((c, m) in met) appendLine("- **${m.name}** (`${c.story.id}`): ${m.about}")
        }
        appendLine()
        appendLine("## Chapters")
        for (s in own) {
            val c = s.chapter
            appendLine()
            appendLine("### ${c.number}. ${c.story.title} (`${c.story.id}`)")
            appendLine()
            appendLine(c.plot)
            appendLine()
            val topic = catalog.topic(c.topic)
            appendLine("- ${s.needs.running} words; ${s.gap.words.size} new of ${s.gap.allowance} allowed.")
            if (topic != null) appendLine("- Teaches ${topic.title} (`${topic.id}`).")
            if (s.gap.words.isNotEmpty()) appendLine("- New: ${s.gap.words.joinToString(", ", transform = ::word)}.")
        }
        val last = steps.last()
        appendLine()
        appendLine("## Known at the end")
        appendLine()
        appendLine("Read in order after the stories it follows, a reader knows these by the end of the last chapter.")
        appendLine()
        // In the order they're met.
        val grammar = last.grammar.map { p -> catalog.topicOf(p)?.let { "${it.title} (`${it.id}`)" } ?: "`$p`" }.distinct()
        appendLine("Grammar: ${grammar.joinToString(", ")}.")
        appendLine()
        appendLine("${last.words.size} words: ${last.words.joinToString(" ") { lemmas[it] ?: it.toString() }}")
    }

    /** Every file the tools write, with what goes in it. */
    fun outputs(): Map<File, String> =
        mapOf(needsFile to StoryPath.writeNeeds(needs)) +
            catalog.series.associate { File(root, "tools/stories/series/${it.id}/SUMMARY.md") to summary(it) }

    override fun close() = conn.close()

    companion object {
        fun dictionary(root: File) = File(root, "app/src/main/assets/databases/dictionary.db")

        fun open(root: File): StoryTools {
            val db = dictionary(root)
            check(db.exists()) { "Build the dictionary first: python3 tools/dictionary/build_dictionary.py" }
            return StoryTools(root, DriverManager.getConnection("jdbc:sqlite:${db.path}"))
        }

        private fun level(jlpt: Int) = if (jlpt == 0) "not JLPT" else "N$jlpt"
    }
}
