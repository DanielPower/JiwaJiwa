package ca.danielpower.jstudy.core

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorySourceTest {
    /** A tools/stories tree with one story, "trip", whose story.json lists [listed] and which has a file for each of [files]. */
    private fun root(listed: List<String>, files: List<String> = listed, chapter: String = """{"title": "T", "plot": "P", "paragraphs": [[["来た。", "I'm here."]]]}"""): File {
        val root = Files.createTempDirectory("stories").toFile()
        val dir = File(root, "tools/stories/series/trip").apply { mkdirs() }
        File(root, "tools/stories/stories.json").writeText("""["trip"]""")
        File(root, "tools/stories/grammar.json").writeText("[]")
        File(dir, "story.json").writeText("""{"title": "Trip", "blurb": "B", "chapters": [${listed.joinToString { "\"$it\"" }}]}""")
        for (f in files) File(dir, "$f.json").writeText(chapter)
        return root
    }

    private fun chapterIds(root: File) =
        StoryPath.parse(StorySource.pathJson(root)).chapters.map { it.second.story.id }

    @Test
    fun `chapters take their ids from their slugs, in the order story json lists them`() {
        assertEquals(listOf("trip/the-airport", "trip/arrival"), chapterIds(root(listOf("the-airport", "arrival"))))
    }

    @Test
    fun `a chapter file story json doesn't list, or a listed one with no file, is an error`() {
        assertFailsWith<IllegalArgumentException> { StorySource.pathJson(root(listOf("a"), files = listOf("a", "b"))) }
        assertFailsWith<IllegalArgumentException> { StorySource.pathJson(root(listOf("a", "b"), files = listOf("a"))) }
        assertFailsWith<IllegalArgumentException> { StorySource.pathJson(root(listOf("Not A Slug"))) }
    }

    @Test
    fun `a note sits before the paragraph after it and never opens a chapter`() {
        val chapter = """{"title": "T", "plot": "P", "paragraphs": [[["来た。", "I'm here."]], {"note": "N"}, [["駅だ。", "The station."]]]}"""
        val notes = StoryPath.parse(StorySource.pathJson(root(listOf("a"), chapter = chapter))).chapters.single().second.story.notes
        assertEquals(listOf(LessonNote(1, "N")), notes)
        val first = """{"title": "T", "plot": "P", "paragraphs": [{"note": "N"}, [["来た。", "I'm here."]]]}"""
        val error = assertFailsWith<IllegalArgumentException> { StorySource.pathJson(root(listOf("a"), chapter = first)) }
        assertTrue("note" in error.message.orEmpty())
    }

    @Test
    fun `a chapter's pinned readings reach the app`() {
        val chapter = """{"title": "T", "plot": "P", "readings": {"方": "かた"}, "paragraphs": [[["あの方だ。", "It's that person."]]]}"""
        val story = StoryPath.parse(StorySource.pathJson(root(listOf("a"), chapter = chapter))).chapters.single().second.story
        assertEquals(mapOf("方" to "かた"), story.readings)
    }
}
