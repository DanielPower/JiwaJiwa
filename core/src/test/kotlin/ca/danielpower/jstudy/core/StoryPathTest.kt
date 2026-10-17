package ca.danielpower.jstudy.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryPathTest {
    private val ids = mapOf("猫" to 1L, "犬" to 2L, "魚" to 3L, "好き" to 4L, "食べる" to 5L)
    private val reader = Reader { text, reading -> ids[text]?.let { LexiconMatch(it, reading.orEmpty()) } }

    @Test
    fun `a story counts each word once, and skips grammar and names`() {
        val words = StoryPath.words(reader, "タマは猫が好きです。猫は魚を食べます。")
        assertEquals(listOf("猫", "好き", "魚", "食べる"), words.map { it.lemma })
    }

    @Test
    fun `words with no dictionary entry always count as new`() {
        val words = StoryPath.words(reader, "猫はりんごを食べます。")
        val new = StoryPath.newWords(words) { it == 1L || it == 5L }
        assertEquals(listOf("りんご"), new.map { it.lemma })
    }

    private fun needs(body: String, teaches: List<String> = emptyList()) =
        StoryPath.needs(reader, PathStory("x", "A title in English", body, emptyList(), teaches))

    @Test
    fun `a step needs the kana of its words and furigana, and its grammar, but not its English title`() {
        val n = needs("猫と犬です。")
        assertEquals(setOf("ね", "こ", "と", "い", "ぬ", "で", "す"), n.kana.map { it.text }.toSet())
        assertEquals(setOf("to", "desu"), n.grammar)
        assertEquals(setOf("wa", "desu"), needs("猫は犬です。").grammar)
    }

    @Test
    fun `the new word allowance is 3 percent of running words, from 3 to 10`() {
        fun allowance(running: Int) = StepNeeds(emptyList(), running, emptySet(), emptySet(), emptySet()).allowance
        assertEquals(3, allowance(10))
        assertEquals(3, allowance(133))
        assertEquals(6, allowance(200))
        assertEquals(10, allowance(1000))
    }

    @Test
    fun `a chapter waits for its grammar to be taught, unless it teaches it`() {
        val story = needs("猫は犬です。")
        val none = StoryPath.gap(story, { true }, { true }, emptySet())
        assertEquals(listOf("desu", "wa"), none.grammar)
        assertFalse(none.ready)
        assertTrue(StoryPath.gap(story, { true }, { true }, setOf("wa", "desu")).ready)
        assertTrue(StoryPath.gap(needs("猫は犬です。", teaches = listOf("wa", "desu")), { true }, { true }, emptySet()).ready)
    }

    @Test
    fun `a step waits while it has more than 6 new kana`() {
        val story = needs("猫です。")
        assertTrue(StoryPath.gap(story, { true }, { false }, setOf("desu")).ready)
        val more = StoryPath.gap(needs("犬、魚です。"), { true }, { false }, emptySet())
        assertEquals(setOf("い", "ぬ", "さ", "か", "な", "で", "す"), more.kana.map { it.text }.toSet())
        assertFalse(more.ready)
    }

    @Test
    fun `the solver reads whatever is open until nothing is, and finds what can't be reached`() {
        val lesson = needs("猫は猫です。", teaches = listOf("wa", "desu"))
        val story = needs("犬は魚です。")
        val far = needs("犬は魚と好きです。")
        assertEquals(listOf(1, 0), StoryPath.solve(listOf(story, lesson)))
        assertEquals(listOf(0, 1), StoryPath.solve(listOf(lesson, story, far)))
    }

    @Test
    fun `reads the bundled stories file`() {
        val catalog = StoryPath.parse(bundled.readText())
        assertTrue(catalog.series.isNotEmpty())
        val ids = catalog.chapters.map { it.second.story.id } + catalog.grammar.map { it.page.id }
        assertEquals(ids.size, ids.toSet().size, "ids are unique")
        assertEquals(catalog.series.size, catalog.series.map { it.id }.toSet().size, "story ids are unique")
    }

    /**
     * Checks the bundled stories against the real dictionary: every chapter
     * and grammar page has English for each sentence, every counted word is
     * in the dictionary, topics name real grammar points, each story read in
     * order (after the stories it follows) opens chapter by chapter, and the
     * solver reaches every chapter from knowing nothing, kana included.
     * Needs the dictionary built (python3 tools/dictionary/build_dictionary.py),
     * so it's skipped where it isn't. Writes build/reports/story-path.txt.
     */
    @Test
    fun `every chapter of the bundled stories can be reached from knowing nothing`() {
        val db = File("../app/src/main/assets/databases/dictionary.db")
        assumeTrue(db.exists(), "dictionary not built")
        DriverManager.getConnection("jdbc:sqlite:${db.path}").use { conn ->
            val lexicon = DictionaryLexicon(conn)
            val reader = Reader(lexicon)
            val catalog = StoryPath.parse(bundled.readText())
            val needs = catalog.chapters.associate { (_, c) -> c.story.id to StoryPath.needs(reader, c.story) }
            val report = StringBuilder()
            val problems = ArrayList<String>()

            fun sentences(story: PathStory) = reader.read(story.body).sumOf { sentenceRanges(it).size }
            for ((_, c) in catalog.chapters) {
                val s = c.story
                if (sentences(s) != s.translations.size) problems += "${s.id}: ${sentences(s)} sentences, ${s.translations.size} translations"
                for (w in needs.getValue(s.id).words.filter { it.wordId == null }) problems += "${s.id}: ${w.lemma} isn't in the dictionary"
                if (c.topic != null && catalog.topic(c.topic) == null) problems += "${s.id}: no grammar topic ${c.topic}"
            }
            for (t in catalog.grammar) {
                if (sentences(t.page) != t.page.translations.size) problems += "${t.id}: ${sentences(t.page)} sentences, ${t.page.translations.size} translations"
                for (id in t.points.filter { grammarPoint(it) == null }) problems += "${t.id}: no grammar point $id"
                if (catalog.teaching(t.id).isEmpty()) problems += "${t.id}: no chapter teaches it"
            }

            // Each story read in order, after the ones it follows, opens one chapter at a time.
            // A side story opens with nothing new at all.
            val bySeries = catalog.series.associateBy { it.id }
            val seriesOf = catalog.series.flatMap { s -> s.chapters.map { it.story.id to s } }.toMap()
            fun before(series: PathSeries): List<Chapter> = series.after.flatMap { id ->
                bySeries[id]?.let { before(it) + it.chapters }
                    ?: seriesOf[id]?.let { s -> before(s) + s.chapters.takeWhile { it.story.id != id } + s.chapters.first { it.story.id == id } }
                    ?: emptyList<Chapter>().also { problems += "${series.id}: no story or chapter $id" }
            }.distinct()
            for (series in catalog.series) {
                val words = HashSet<Long>()
                val kana = HashSet<KanaUnit>()
                val grammar = HashSet<String>()
                for (c in before(series) + series.chapters) {
                    val own = c in series.chapters
                    val n = needs.getValue(c.story.id)
                    val gap = StoryPath.gap(n, { it in words }, { it in kana }, grammar)
                    // The stories this one follows are checked on their own turn.
                    if (own) {
                        val id = c.story.id
                        if (gap.words.size > gap.allowance) problems += "$id: ${gap.words.size} new words, ${gap.allowance} allowed"
                        if (gap.kana.size > StoryPath.MAX_KANA) problems += "$id: ${gap.kana.size} new kana"
                        if (gap.grammar.isNotEmpty()) problems += "$id: grammar not taught yet: ${gap.grammar}"
                        if (series.side && (gap.words.isNotEmpty() || gap.kana.isNotEmpty() || c.topic != null))
                            problems += "$id: side story has something new: ${gap.words.map { it.lemma }} ${gap.kana.map { it.text }} ${c.topic.orEmpty()}"
                    }
                    words += n.words.mapNotNull { it.wordId }
                    kana += n.kana
                    grammar += n.grammar + n.teaches
                    if (!own) continue
                    // The topic's example, shown before reading, uses only words you have by then.
                    catalog.topic(c.topic)?.example?.let { (jp, _) ->
                        val unknown = StoryPath.words(reader, jp).filter { it.wordId !in words }
                        if (unknown.isNotEmpty()) problems += "${c.story.id}: ${c.topic} example has new words ${unknown.map { it.lemma }}"
                    }
                    report.appendLine("${c.story.id} ${c.story.title}  words ${gap.words.size}/${gap.allowance}, total ${words.size}; kana ${gap.kana.size}, total ${kana.size}${c.topic?.let { "; teaches $it" }.orEmpty()}")
                    if (gap.kana.isNotEmpty()) report.appendLine("    kana ${gap.kana.joinToString(" ") { it.text }}")
                    report.appendLine("    grammar ${n.grammar.sorted()}")
                    for (w in gap.words) report.appendLine("    ${w.lemma} (${w.reading}) ${w.wordId} ${lexicon.describe(w.wordId)}")
                }
                report.appendLine()
            }

            val all = catalog.chapters.map { needs.getValue(it.second.story.id) }
            val order = StoryPath.solve(all)
            val stuck = all.indices - order.toSet()
            if (stuck.isNotEmpty()) problems += "the solver can't reach ${stuck.map { catalog.chapters[it].second.story.id }}"
            // Words met in only one chapter get no reinforcement from reading; aim to bring them back.
            val chaptersWith = HashMap<Long, Int>()
            for (n in all) for (id in n.words.mapNotNull { it.wordId }.distinct()) chaptersWith.merge(id, 1, Int::plus)
            val once = chaptersWith.filterValues { it == 1 }.keys
            report.appendLine("Words in one chapter only (${once.size} of ${chaptersWith.size}): ${once.joinToString(" ") { lexicon.describe(it).substringBefore('/') }}")
            val kana = all.flatMap { it.kana }.toSet()
            report.appendLine("Kana never needed: ${KanaCourse.units.filter { it !in kana }.joinToString(" ") { it.text }}")
            File("build/reports").mkdirs()
            File("build/reports/story-path.txt").writeText(report.toString() + problems.distinct().joinToString("\n", "\nProblems:\n"))
            assertTrue(problems.isEmpty(), problems.distinct().joinToString("\n"))
        }
    }

    private val bundled = File("../app/src/main/assets/stories/path.json")

    /** The app's dictionary lookup (data/Dictionary.find), over JDBC. */
    private class DictionaryLexicon(private val conn: Connection) : Lexicon {
        override fun find(text: String, reading: String?): LexiconMatch? {
            data class Candidate(val id: Long, val kana: String, val jlpt: Int, val rank: Int)
            val candidates = query(
                "SELECT w.id, w.kana, w.jlpt, COALESCE(w.freq_rank, 1000000000) FROM form f JOIN word w ON w.id = f.word_id WHERE f.text = ?",
                text,
            ) { Candidate(getLong(1), getString(2), getInt(3), getInt(4)) }.distinctBy { it.id }
            if (candidates.isEmpty()) return null
            val wanted = reading?.let(Kana::toHiragana)
            val readingMatches = if (wanted == null) emptySet() else candidates.filter { cand ->
                Kana.toHiragana(cand.kana) == wanted ||
                    query("SELECT text FROM form WHERE word_id = ?", cand.id.toString()) { getString(1) }
                        .any { Kana.isKana(it) && Kana.toHiragana(it) == wanted }
            }.map { it.id }.toSet()
            val best = candidates.minWith(compareBy<Candidate>({ it.id !in readingMatches }, { it.jlpt == 0 }, { it.rank }, { it.id }))
            return LexiconMatch(best.id, if (best.id in readingMatches) wanted!! else Kana.toHiragana(best.kana))
        }

        fun describe(id: Long?): String = id?.let {
            query("SELECT COALESCE(kanji, kana), kana, jlpt, usually_kana FROM word WHERE id = ?", it.toString()) {
                "${getString(1)}/${getString(2)} N${getInt(3)}${if (getInt(4) == 1) " kana" else ""}"
            }.firstOrNull()
        }.orEmpty()

        private fun <T> query(sql: String, arg: String, row: java.sql.ResultSet.() -> T): List<T> =
            conn.prepareStatement(sql).use { st ->
                st.setString(1, arg)
                st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.row()) } }
            }
    }
}
