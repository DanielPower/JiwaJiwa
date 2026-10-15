package ca.danielpower.jstudy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** English in a grammar topic's page, shown before Japanese line [before] (after the last line when it's the line count). */
data class LessonNote(val before: Int, val text: String)

/**
 * Japanese that comes with the app: a chapter of a story, or a grammar
 * topic's page. Its [title] is English (or, for a grammar topic, the
 * pattern) and isn't read; only the [body] counts.
 */
data class PathStory(
    /** Stable across app updates, so a read chapter stays read. */
    val id: String,
    val title: String,
    val body: String,
    /** English for each sentence of the body, in order. */
    val translations: List<String>,
    /** The grammar points it teaches (ids from [grammarPoint]): a chapter's topic, before you read it. */
    val teaches: List<String> = emptyList(),
    /** For a grammar topic's page, its English explanations between the Japanese lines. */
    val notes: List<LessonNote> = emptyList(),
    /** A grammar topic's page, rather than a chapter. */
    val lesson: Boolean = false,
)

/** Someone in a story, as the chapter that introduces them describes them. */
data class CastMember(val name: String, val about: String)

/** One chapter of a story: [number] counts from 1. */
data class Chapter(
    val story: PathStory,
    val number: Int,
    /** The grammar topic it introduces before you read it, if any. */
    val topic: String?,
    /** The people you meet in it. */
    val meets: List<CastMember>,
)

/**
 * A story that comes with the app, as one or more chapters. Each chapter
 * opens on its own once its words, kana and grammar are within reach.
 * Read in order, after the stories in [after], every chapter is.
 */
data class PathSeries(
    val id: String,
    /** English, with Japanese where it fits. */
    val title: String,
    val blurb: String,
    val after: List<String>,
    val chapters: List<Chapter>,
) {
    /** A one-chapter story opens straight to its chapter, with no chapter list. */
    val single get() = chapters.size == 1
}

/**
 * Grammar taught before the chapter that first needs it: one or more
 * related points (から and まで), a line or two on them, and a page to look
 * back at with the examples and fuller notes.
 */
data class GrammarTopic(
    val id: String,
    /** The pattern, as textbooks write it ("～て (ください)"). */
    val title: String,
    /** A few words of English ("Asking, linking"). */
    val titleEn: String,
    val points: List<String>,
    /** What it does, in a sentence or two. */
    val summary: String,
    /** Its full page: the examples, with notes between them. */
    val page: PathStory,
) {
    /** The first example and its English, shown with the summary. */
    val example: Pair<String, String>? get() = page.body.lineSequence().firstOrNull()?.let { it to page.translations.first() }
}

/** Every story and grammar topic that comes with the app. */
data class StoryCatalog(val series: List<PathSeries>, val grammar: List<GrammarTopic>) {
    val chapters: List<Pair<PathSeries, Chapter>> get() = series.flatMap { s -> s.chapters.map { s to it } }

    fun topic(id: String?): GrammarTopic? = grammar.firstOrNull { it.id == id }

    /** The topic a grammar point belongs to. */
    fun topicOf(point: String): GrammarTopic? = grammar.firstOrNull { point in it.points }

    /** The chapters that introduce [topic], in order. */
    fun teaching(topic: String): List<Pair<PathSeries, Chapter>> = chapters.filter { it.second.topic == topic }
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
 * The bundled stories, whose chapters introduce new words, kana and grammar
 * in place of the study queue. A chapter opens once it has few enough new
 * words and kana and uses only grammar you've seen or it teaches. A chapter
 * teaches one grammar topic at most, before you read it.
 */
object StoryPath {
    const val MIN_NEW = 3
    const val MAX_NEW = 10
    const val MAX_KANA = 6

    fun parse(json: String): StoryCatalog {
        val root = Json.parseToJsonElement(json).jsonObject
        fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
        fun JsonObject.texts(key: String) = this[key]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val grammar = root["grammar"]?.jsonArray.orEmpty().map { e ->
            val o = e.jsonObject
            val id = o.text("id")
            GrammarTopic(
                id = id,
                title = o.text("title"),
                titleEn = o.text("title_en"),
                points = o.texts("points"),
                summary = o.text("summary"),
                page = PathStory(
                    id = "grammar/$id",
                    title = o.text("title"),
                    body = o.text("body"),
                    translations = o.texts("translations"),
                    notes = o["notes"]?.jsonArray.orEmpty().map { n ->
                        val note = n.jsonObject
                        LessonNote(note.getValue("before").jsonPrimitive.int, note.text("text"))
                    },
                    lesson = true,
                ),
            )
        }
        val topics = grammar.associateBy { it.id }
        val series = root.getValue("series").jsonArray.map { e ->
            val o = e.jsonObject
            PathSeries(
                id = o.text("id"),
                title = o.text("title"),
                blurb = o["blurb"]?.jsonPrimitive?.content.orEmpty(),
                after = o.texts("after"),
                chapters = o.getValue("chapters").jsonArray.mapIndexed { i, c ->
                    val ch = c.jsonObject
                    val topic = ch["topic"]?.jsonPrimitive?.content
                    Chapter(
                        story = PathStory(
                            id = ch.text("id"),
                            title = ch.text("title"),
                            body = ch.text("body"),
                            translations = ch.texts("translations"),
                            teaches = topic?.let { topics[it]?.points }.orEmpty(),
                        ),
                        number = i + 1,
                        topic = topic,
                        meets = ch["meets"]?.jsonArray.orEmpty().map { m ->
                            val member = m.jsonObject
                            CastMember(member.text("name"), member.text("about"))
                        },
                    )
                },
            )
        }
        return StoryCatalog(series, grammar)
    }

    /** The words a story's body counts, each once, in the order they first appear. */
    fun words(reader: Reader, body: String): List<ReadingWord> =
        reader.read(body).flatten().filter { it.kind == WordKind.WORD }.distinctBy { it.key }

    fun needs(reader: Reader, story: PathStory): StepNeeds {
        val all = reader.read(story.body).flatten()
        val counted = all.filter { it.kind == WordKind.WORD }
        // Furigana is read where there is some; a grammar page's English can quote kana too.
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
