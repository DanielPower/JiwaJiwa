package ca.danielpower.jstudy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A story that comes with the app: one step on the path from the first words up. */
data class PathStory(
    /** Stable across app updates, so a read story stays read. */
    val id: String,
    val title: String,
    val body: String,
    /** English for each sentence of the title and body, in order. */
    val translations: List<String>,
)

/**
 * The bundled stories, which introduce new words in place of the study
 * queue. A story shows once it has at most [MAX_NEW] words you haven't
 * learned, and the stories are written so that reading them in order always
 * works: each has at most that many words beyond the ones before it.
 */
object StoryPath {
    const val MAX_NEW = 3

    fun parse(json: String): List<PathStory> =
        Json.parseToJsonElement(json).jsonObject.getValue("stories").jsonArray.map { e ->
            val o = e.jsonObject
            PathStory(
                id = o.getValue("id").jsonPrimitive.content,
                title = o.getValue("title").jsonPrimitive.content,
                body = o.getValue("body").jsonPrimitive.content,
                translations = o["translations"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
            )
        }

    /** The words a story counts, title and body, each once, in the order they first appear. */
    fun words(reader: Reader, story: PathStory): List<ReadingWord> =
        (reader.read(story.title) + reader.read(story.body)).flatten()
            .filter { it.kind == WordKind.WORD }
            .distinctBy { it.key }

    /** Of a story's [words], the ones still to learn. A word with no dictionary entry can't be learned, so it always counts. */
    fun newWords(words: List<ReadingWord>, isKnown: (Long) -> Boolean): List<ReadingWord> =
        words.filter { w -> w.wordId?.let(isKnown) != true }

    fun ready(newWords: Int) = newWords <= MAX_NEW

    /**
     * Reads the stories in order, learning each one's words, and returns the
     * index of the first that has too many new words by then, or null if the
     * whole path can be read. Words are identified by [ReadingWord.key].
     */
    fun firstDeadEnd(stories: List<List<ReadingWord>>): Int? {
        val learned = HashSet<String>()
        stories.forEachIndexed { i, words ->
            val new = words.filter { it.wordId == null || it.key !in learned }
            if (!ready(new.size)) return i
            learned += words.map { it.key }
        }
        return null
    }
}
