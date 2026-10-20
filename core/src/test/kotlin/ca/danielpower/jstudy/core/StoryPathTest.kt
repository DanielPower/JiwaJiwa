package ca.danielpower.jstudy.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryPathTest {
    private val ids = mapOf("猫" to 1L, "犬" to 2L, "魚" to 3L, "好き" to 4L, "食べる" to 5L)
    private val reader = Reader { text, reading -> ids[text]?.let { LexiconMatch(it, reading.orEmpty()) } }

    @Test
    fun `a story counts each word once, and skips grammar and names`() {
        val words = StoryPath.words(reader.read("タマは猫が好きです。猫は魚を食べます。"))
        assertEquals(listOf("猫", "好き", "魚", "食べる"), words.map { it.lemma })
    }

    @Test
    fun `words with no dictionary entry always count as new`() {
        val words = needs("猫はりんごを食べます。").words
        val new = StoryPath.newWords(words) { it == 1L || it == 5L }
        assertEquals(listOf("りんご"), new.map { it.lemma })
    }

    private fun needs(body: String, teaches: List<String> = emptyList()) =
        StoryPath.needs(PathStory("x", "A title in English", body, emptyList(), teaches), reader.read(body))

    @Test
    fun `a step needs its grammar, but not its English title's`() {
        val n = needs("猫と犬です。")
        assertEquals(setOf("to", "desu"), n.grammar)
        assertEquals(setOf("wa", "desu"), needs("猫は犬です。").grammar)
    }

    @Test
    fun `the new word allowance is 3 percent of running words, and at least 3`() {
        fun allowance(running: Int) = StepNeeds(emptyList(), running, emptySet(), emptySet()).allowance
        assertEquals(3, allowance(10))
        assertEquals(3, allowance(133))
        assertEquals(6, allowance(200))
        assertEquals(30, allowance(1000))
    }

    @Test
    fun `a chapter waits for its grammar to be taught, unless it teaches it`() {
        val story = needs("猫は犬です。")
        val none = StoryPath.gap(story, { true }, emptySet())
        assertEquals(listOf("desu", "wa"), none.grammar)
        assertFalse(none.ready)
        assertTrue(StoryPath.gap(story, { true }, setOf("wa", "desu")).ready)
        assertTrue(StoryPath.gap(needs("猫は犬です。", teaches = listOf("wa", "desu")), { true }, emptySet()).ready)
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
    fun `a chapter's topic is new in it on a first reading, in whichever chapter that lists it you reach first`() {
        fun chapter(id: String, n: Int, topic: String?) = Chapter(PathStory(id, id, "", emptyList()), n, topic, emptyList())
        val ta = GrammarTopic("ta", "～た", "Did", listOf("ta"), "", PathStory("grammar/ta", "～た", "来た。", listOf("Came.")))
        val catalog = StoryCatalog(
            series = listOf(PathSeries("s", "S", "", emptyList(), listOf(chapter("s/1", 1, "ta"), chapter("s/2", 2, "ta"), chapter("s/3", 3, null)))),
            grammar = listOf(ta),
        )
        assertEquals(ta, catalog.teaches("s/1", emptySet()))
        assertEquals(ta, catalog.teaches("s/2", emptySet()))
        // Read again, it's nothing new.
        assertNull(catalog.teaches("s/1", setOf("s/1")))
        // Learned in one chapter, it isn't new in the other.
        assertNull(catalog.teaches("s/2", setOf("s/1")))
        assertNull(catalog.teaches("s/3", emptySet()))
        assertNull(catalog.teaches("grammar/ta", emptySet()))
    }

    @Test
    fun `reads the bundled stories file`() {
        val catalog = StoryPath.parse(bundled)
        assertTrue(catalog.series.isNotEmpty())
        val ids = catalog.chapters.map { it.second.story.id } + catalog.grammar.map { it.page.id }
        assertEquals(ids.size, ids.toSet().size, "ids are unique")
        assertEquals(catalog.series.size, catalog.series.map { it.id }.toSet().size, "story ids are unique")
        // A chapter's note follows a paragraph, whose end has its marker.
        for ((_, c) in catalog.chapters) for (note in c.story.notes) {
            assertTrue(note.before in 1..paragraphs(c.story.body).size, "${c.story.id}: note before paragraph ${note.before}")
        }
        assertTrue(catalog.chapters.any { it.second.story.notes.isNotEmpty() }, "chapter notes are read")
    }

    /**
     * Checks the bundled stories against the real dictionary (StoryCheck),
     * and that the files the story tools write are up to date. Needs the
     * dictionary built (python3 tools/dictionary/build_dictionary.py), so
     * it's skipped where it isn't. Writes build/reports/story-path.txt.
     */
    @Test
    fun `every chapter of the bundled stories can be reached from knowing nothing`() {
        val root = File("..")
        assumeTrue(StoryTools.dictionary(root).exists(), "dictionary not built")
        StoryTools.open(root).use { tools ->
            val check = StoryCheck(tools)
            val problems = check.problems.map { it.toString() }.toMutableList()
            // Writers read the summaries in place of every chapter, so they must match the stories.
            for ((file, text) in tools.outputs()) {
                if (!file.exists() || file.readText() != text) problems += "${file.relativeTo(root)} is out of date: run ./gradlew -PcoreOnly=true :core:stories"
            }
            // The app shows the stories as the tools read them.
            for ((file, text) in StoryText.write(tools.text)) assertEquals(tools.text.filterKeys { StoryText.file(it) == file }, StoryText.parse(text), "$file reads back as written")
            assertEquals(tools.needs, StoryPath.parseNeeds(StoryPath.writeNeeds(tools.needs), tools.catalog), "the index reads back as written")

            File("build/reports").mkdirs()
            File("build/reports/story-path.txt").writeText(check.report() + problems.drop(check.problems.size).joinToString("") { "$it\n" })
            assertTrue(problems.isEmpty(), problems.distinct().joinToString("\n"))
        }
    }

    private val bundled = StorySource.pathJson(File(".."))
}
