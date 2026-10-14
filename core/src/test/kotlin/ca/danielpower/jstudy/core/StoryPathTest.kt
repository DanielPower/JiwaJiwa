package ca.danielpower.jstudy.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    @Test
    fun `a path is readable when each story adds at most a few words`() {
        val path = listOf("猫です。", "犬は魚が好きです。", "猫は魚を食べます。").map { StoryPath.words(reader, story(it)) }
        assertNull(StoryPath.firstDeadEnd(path))
        val jump = listOf("猫です。", "犬は魚が好きです。猫は食べます。").map { StoryPath.words(reader, story(it)) }
        assertEquals(1, StoryPath.firstDeadEnd(jump))
    }

    @Test
    fun `reads the bundled stories file`() {
        val stories = StoryPath.parse(bundled.readText())
        assertTrue(stories.isNotEmpty())
        assertEquals(stories.size, stories.map { it.id }.toSet().size, "story ids are unique")
    }

    /**
     * Checks the bundled path against the real dictionary: every story has
     * English for each sentence, every counted word is in the dictionary, and
     * reading in order never meets more than [StoryPath.MAX_NEW] new words.
     * Needs the dictionary built (python3 tools/dictionary/build_dictionary.py),
     * so it's skipped where it isn't. Writes build/reports/story-path.txt.
     */
    @Test
    fun `the bundled path can be read from the first story to the last`() {
        val db = File("../app/src/main/assets/databases/dictionary.db")
        assumeTrue(db.exists(), "dictionary not built")
        DriverManager.getConnection("jdbc:sqlite:${db.path}").use { conn ->
            val lexicon = DictionaryLexicon(conn)
            val reader = Reader(lexicon)
            val stories = StoryPath.parse(bundled.readText())
            val words = stories.map { StoryPath.words(reader, it) }
            val learned = HashSet<String>()
            val report = StringBuilder()
            val problems = ArrayList<String>()
            stories.forEachIndexed { i, s ->
                val sentences = (listOf(reader.read(s.title).flatten()) + reader.read(s.body)).sumOf { sentenceRanges(it).size }
                if (sentences != s.translations.size) problems += "${s.id}: $sentences sentences, ${s.translations.size} translations"
                val new = words[i].filter { it.key !in learned }
                for (w in words[i].filter { it.wordId == null }) problems += "${s.id}: ${w.lemma} isn't in the dictionary"
                if (!StoryPath.ready(new.size)) problems += "${s.id}: ${new.size} new words"
                learned += words[i].map { it.key }
                report.appendLine("${s.id} ${s.title}  new ${new.size}, total ${learned.size}")
                for (w in new) report.appendLine("    ${w.lemma} (${w.reading}) ${w.wordId} ${lexicon.describe(w.wordId)}")
            }
            File("build/reports").mkdirs()
            File("build/reports/story-path.txt").writeText(report.toString() + problems.joinToString("\n", "\nProblems:\n"))
            assertTrue(problems.isEmpty(), problems.joinToString("\n"))
            assertNull(StoryPath.firstDeadEnd(words))
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
