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

    private fun story(body: String, title: String = "猫") = PathStory("x", title, body, emptyList())

    @Test
    fun `a story counts each word once, title included, and skips grammar and names`() {
        val words = StoryPath.words(reader, story("タマは猫が好きです。猫は魚を食べます。", title = "犬"))
        assertEquals(listOf("犬", "猫", "好き", "魚", "食べる"), words.map { it.lemma })
    }

    @Test
    fun `words with no dictionary entry always count as new`() {
        val words = StoryPath.words(reader, story("猫はりんごを食べます。"))
        val new = StoryPath.newWords(words) { it == 1L || it == 5L }
        assertEquals(listOf("りんご"), new.map { it.lemma })
    }

    private fun needs(body: String, title: String = "猫", teaches: List<String> = emptyList()) =
        StoryPath.needs(reader, PathStory("x", title, body, emptyList(), teaches))

    @Test
    fun `a step needs the kana of its words and furigana, and its grammar`() {
        val n = needs("犬です。", title = "猫")
        assertEquals(setOf("ね", "こ", "い", "ぬ", "で", "す"), n.kana.map { it.text }.toSet())
        assertEquals(emptySet(), n.grammar)
        assertEquals(setOf("wa"), needs("猫は犬です。").grammar)
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
    fun `a story waits for its grammar to be taught, and a lesson teaches its own`() {
        val story = needs("猫は犬です。")
        val none = StoryPath.gap(story, { true }, { true }, emptySet())
        assertEquals(listOf("wa"), none.grammar)
        assertFalse(none.ready)
        assertTrue(StoryPath.gap(story, { true }, { true }, setOf("wa")).ready)
        assertTrue(StoryPath.gap(needs("猫は犬です。", teaches = listOf("wa")), { true }, { true }, emptySet()).ready)
    }

    @Test
    fun `a step waits while it has more than 6 new kana`() {
        val story = needs("猫です。")
        assertTrue(StoryPath.gap(story, { true }, { false }, emptySet()).ready)
        val more = StoryPath.gap(needs("犬、魚です。", title = ""), { true }, { false }, emptySet())
        assertEquals(setOf("い", "ぬ", "さ", "か", "な", "で", "す"), more.kana.map { it.text }.toSet())
        assertFalse(more.ready)
    }

    @Test
    fun `the solver reads whatever is open until nothing is, and finds what can't be reached`() {
        val lesson = needs("猫は猫です。", teaches = listOf("wa"))
        val story = needs("犬は魚です。")
        val far = needs("犬は魚と好きです。")
        assertEquals(listOf(1, 0), StoryPath.solve(listOf(story, lesson)))
        assertEquals(listOf(0, 1), StoryPath.solve(listOf(lesson, story, far)))
    }

    @Test
    fun `reads the bundled stories file`() {
        val stories = StoryPath.parse(bundled.readText())
        assertTrue(stories.isNotEmpty())
        assertEquals(stories.size, stories.map { it.id }.toSet().size, "story ids are unique")
    }

    /**
     * Checks the bundled path against the real dictionary: every story has
     * English for each sentence, every counted word is in the dictionary,
     * lessons teach real grammar points, read in order each step opens
     * once the ones before it are read, and the solver reaches every step
     * from knowing nothing, kana included. Needs the dictionary built
     * (python3 tools/dictionary/build_dictionary.py), so it's skipped where
     * it isn't. Writes build/reports/story-path.txt.
     */
    @Test
    fun `every step on the bundled path can be reached from knowing nothing`() {
        val db = File("../app/src/main/assets/databases/dictionary.db")
        assumeTrue(db.exists(), "dictionary not built")
        DriverManager.getConnection("jdbc:sqlite:${db.path}").use { conn ->
            val lexicon = DictionaryLexicon(conn)
            val reader = Reader(lexicon)
            val stories = StoryPath.parse(bundled.readText())
            val needs = stories.map { StoryPath.needs(reader, it) }
            val words = HashSet<Long>()
            val kana = HashSet<KanaUnit>()
            val grammar = HashSet<String>()
            val report = StringBuilder()
            val problems = ArrayList<String>()
            stories.forEachIndexed { i, s ->
                val sentences = (listOf(reader.read(s.title).flatten()) + reader.read(s.body)).sumOf { sentenceRanges(it).size }
                if (sentences != s.translations.size) problems += "${s.id}: $sentences sentences, ${s.translations.size} translations"
                for (w in needs[i].words.filter { it.wordId == null }) problems += "${s.id}: ${w.lemma} isn't in the dictionary"
                for (id in s.teaches.filter { grammarPoint(it) == null }) problems += "${s.id}: no grammar point $id"
                val gap = StoryPath.gap(needs[i], { it in words }, { it in kana }, grammar)
                if (gap.words.size > gap.allowance) problems += "${s.id}: ${gap.words.size} new words, ${gap.allowance} allowed"
                if (gap.kana.size > StoryPath.MAX_KANA) problems += "${s.id}: ${gap.kana.size} new kana"
                if (gap.grammar.isNotEmpty()) problems += "${s.id}: grammar not taught yet: ${gap.grammar}"
                words += needs[i].words.mapNotNull { it.wordId }
                kana += needs[i].kana
                grammar += needs[i].grammar + needs[i].teaches
                val kind = if (s.isLesson) "LESSON " else ""
                report.appendLine("${s.id} $kind${s.title}  words ${gap.words.size}/${gap.allowance}, total ${words.size}; kana ${gap.kana.size}, total ${kana.size}")
                if (gap.kana.isNotEmpty()) report.appendLine("    kana ${gap.kana.joinToString(" ") { it.text }}")
                report.appendLine("    grammar ${needs[i].grammar.sorted()}${if (s.isLesson) " teaches ${s.teaches}" else ""}")
                for (w in gap.words) report.appendLine("    ${w.lemma} (${w.reading}) ${w.wordId} ${lexicon.describe(w.wordId)}")
            }
            val order = StoryPath.solve(needs)
            val stuck = stories.indices - order.toSet()
            if (stuck.isNotEmpty()) problems += "the solver can't reach ${stuck.map { stories[it].id }}"
            report.appendLine("\nKana never needed: ${KanaCourse.units.filter { it !in kana }.joinToString(" ") { it.text }}")
            File("build/reports").mkdirs()
            File("build/reports/story-path.txt").writeText(report.toString() + problems.joinToString("\n", "\nProblems:\n"))
            assertTrue(problems.isEmpty(), problems.joinToString("\n"))
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
