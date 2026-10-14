package ca.danielpower.jstudy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** English in a lesson, shown before Japanese line [before] (after the last line when it's the line count). */
data class LessonNote(val before: Int, val text: String)

/** A story or lesson that comes with the app: one step on the path from the first words up. */
data class PathStory(
    /** Stable across app updates, so a read story stays read. */
    val id: String,
    val title: String,
    val body: String,
    /** English for each sentence of the title and body, in order. */
    val translations: List<String>,
    /** For a lesson, the grammar points it teaches (ids from [grammarPoint]). Empty for a story. */
    val teaches: List<String> = emptyList(),
    /** For a lesson, its English explanations between the Japanese lines. */
    val notes: List<LessonNote> = emptyList(),
) {
    val isLesson get() = teaches.isNotEmpty()
}

/** What a step asks of you: the words, kana and grammar its Japanese uses. */
data class StepNeeds(
    /** Each counted word once, in the order they first appear. */
    val words: List<ReadingWord>,
    /** How many counted words there are, repeats included. */
    val running: Int,
    /** Every kana it takes to read it, furigana included. */
    val kana: Set<KanaUnit>,
    /** The grammar points its Japanese uses. */
    val grammar: Set<String>,
    /** The grammar points it teaches, for a lesson. */
    val teaches: Set<String>,
) {
    /** How many new words it may have: 3% of its running words, from [StoryPath.MIN_NEW] to [StoryPath.MAX_NEW]. */
    val allowance get() = (running * 3 / 100).coerceIn(StoryPath.MIN_NEW, StoryPath.MAX_NEW)
}

/** What a step's [needs] leave to learn, for someone who knows some of it. */
data class StepGap(val words: List<ReadingWord>, val kana: List<KanaUnit>, val grammar: List<String>, val allowance: Int) {
    /** Open to read: few enough new words and kana, and no grammar that hasn't been taught. */
    val ready get() = words.size <= allowance && kana.size <= StoryPath.MAX_KANA && grammar.isEmpty()
}

/**
 * The bundled stories and lessons, which introduce new words, kana and
 * grammar in place of the study queue. A step opens once it has few enough
 * new words and kana and uses only grammar you've seen. Only lessons bring
 * in grammar, so a story's grammar must all be taught first.
 */
object StoryPath {
    const val MIN_NEW = 3
    const val MAX_NEW = 10
    const val MAX_KANA = 6

    fun parse(json: String): List<PathStory> =
        Json.parseToJsonElement(json).jsonObject.getValue("stories").jsonArray.map { e ->
            val o = e.jsonObject
            PathStory(
                id = o.getValue("id").jsonPrimitive.content,
                title = o.getValue("title").jsonPrimitive.content,
                body = o.getValue("body").jsonPrimitive.content,
                translations = o["translations"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                teaches = o["teaches"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                notes = o["notes"]?.jsonArray?.map { n ->
                    val note = n.jsonObject
                    LessonNote(note.getValue("before").jsonPrimitive.int, note.getValue("text").jsonPrimitive.content)
                }.orEmpty(),
            )
        }

    /** The words a story counts, title and body, each once, in the order they first appear. */
    fun words(reader: Reader, story: PathStory): List<ReadingWord> =
        counted(reader, story).distinctBy { it.key }

    private fun counted(reader: Reader, story: PathStory) =
        (reader.read(story.title) + reader.read(story.body)).flatten().filter { it.kind == WordKind.WORD }

    fun needs(reader: Reader, story: PathStory): StepNeeds {
        val all = (reader.read(story.title) + reader.read(story.body)).flatten()
        val counted = all.filter { it.kind == WordKind.WORD }
        // Furigana is read where there is some; the lesson's English can quote kana too.
        val text = all.flatMap { w -> w.ruby.map { it.ruby ?: it.base } } + story.notes.map { it.text }
        return StepNeeds(
            words = counted.distinctBy { it.key },
            running = counted.size,
            kana = text.flatMap(KanaCourse::unitsIn).toSet(),
            grammar = all.flatMap { w -> w.grammar.map { it.point.id } }.toSet(),
            teaches = story.teaches.toSet(),
        )
    }

    /** Of a story's [words], the ones still to learn. A word with no dictionary entry can't be learned, so it always counts. */
    fun newWords(words: List<ReadingWord>, isKnown: (Long) -> Boolean): List<ReadingWord> =
        words.filter { w -> w.wordId?.let(isKnown) != true }

    /** What's left of [needs] to learn, given the words, kana and grammar you know. */
    fun gap(needs: StepNeeds, isKnown: (Long) -> Boolean, knowsKana: (KanaUnit) -> Boolean, seenGrammar: Set<String>) = StepGap(
        words = newWords(needs.words, isKnown),
        kana = KanaCourse.units.filter { it in needs.kana && !knowsKana(it) },
        grammar = (needs.grammar - needs.teaches - seenGrammar).sorted(),
        allowance = needs.allowance,
    )

    /**
     * Reads every step that's open, learning what it teaches, until none is
     * left, starting from no words, no kana and no grammar. Returns the
     * indexes read, in order; any step missing can never be reached.
     *
     * Reading a step only adds to what you know, and knowing more never
     * closes a step, so this finds everything reachable. It also means no
     * order can get stuck: whatever you've read, the first step of this
     * order you haven't is open to you.
     */
    fun solve(steps: List<StepNeeds>): List<Int> {
        val words = HashSet<String>()
        val kana = HashSet<KanaUnit>()
        val grammar = HashSet<String>()
        val order = ArrayList<Int>()
        val left = steps.indices.toMutableList()
        while (true) {
            val i = left.firstOrNull { i ->
                val s = steps[i]
                s.words.count { it.wordId == null || it.key !in words } <= s.allowance &&
                    s.kana.count { it !in kana } <= MAX_KANA &&
                    grammar.containsAll(s.grammar - s.teaches)
            } ?: return order
            left.remove(i)
            order += i
            words += steps[i].words.map { it.key }
            kana += steps[i].kana
            grammar += steps[i].grammar + steps[i].teaches
        }
    }
}
