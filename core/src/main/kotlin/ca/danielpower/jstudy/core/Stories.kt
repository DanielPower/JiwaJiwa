package ca.danielpower.jstudy.core

enum class StoryLength(val label: UiLabel, val characters: Int) {
    SHORT(UiLabel("Short", "短い"), 300),
    MEDIUM(UiLabel("Medium", "普通"), 800),
    LONG(UiLabel("Long", "長い"), 2000),
}

/** How many of a story's running words should already be known. */
enum class StoryDifficulty(val label: UiLabel, val knownShare: Double) {
    EASY(UiLabel("Easy", "易しい"), 0.99),
    NORMAL(UiLabel("Normal", "普通"), 0.97),
    HARD(UiLabel("Hard", "難しい"), 0.95),
}

/**
 * How many different new words a story may use. The share of known running
 * words (99%, 97%, 95%) is turned into a count of distinct words, so a new
 * word can repeat as often as the story needs without making it harder.
 */
fun newWordBudget(length: StoryLength, difficulty: StoryDifficulty): Int = when (length) {
    StoryLength.SHORT -> when (difficulty) {
        StoryDifficulty.EASY -> 1
        StoryDifficulty.NORMAL -> 2
        StoryDifficulty.HARD -> 4
    }
    StoryLength.MEDIUM -> when (difficulty) {
        StoryDifficulty.EASY -> 3
        StoryDifficulty.NORMAL -> 6
        StoryDifficulty.HARD -> 9
    }
    StoryLength.LONG -> when (difficulty) {
        StoryDifficulty.EASY -> 6
        StoryDifficulty.NORMAL -> 10
        StoryDifficulty.HARD -> 15
    }
}

/** A word as listed to the story writer: how it's written and read. */
data class VocabWord(val wordId: Long, val written: String, val reading: String) {
    val label get() = if (written == reading) written else "$written($reading)"
}

data class StoryRequest(
    /** What the story should be about; may be blank. */
    val prompt: String,
    val length: StoryLength,
    val difficulty: StoryDifficulty,
    /** The hardest JLPT level whose grammar the story may use (5 is the easiest). */
    val grammarLevel: Int,
    /** Every word the learner has started. */
    val known: List<VocabWord>,
    /** Started words that aren't solid yet; the story should use some of them. */
    val practice: List<VocabWord>,
    /** The next new words to learn; the story should introduce them. */
    val introduce: List<VocabWord>,
) {
    val budget get() = newWordBudget(length, difficulty)
}

/** How a story's words measure up against what the learner knows. */
data class Coverage(
    /** Counted running words (names, numbers, grammar and punctuation aside). */
    val words: Int,
    val knownWords: Int,
    /** Distinct new words in order of first appearance. */
    val newWords: List<ReadingWord>,
) {
    val knownShare get() = if (words == 0) 1.0 else knownWords.toDouble() / words
}

/** [isKnown] says whether a dictionary word counts as known; words with no entry never do. */
fun coverage(paragraphs: List<List<ReadingWord>>, isKnown: (Long) -> Boolean): Coverage {
    var words = 0
    var known = 0
    val new = LinkedHashMap<String, ReadingWord>()
    for (w in paragraphs.flatten()) {
        if (w.kind != WordKind.WORD) continue
        words++
        val id = w.wordId
        if (id != null && isKnown(id)) {
            known++
        } else {
            new.putIfAbsent(w.key, w)
        }
    }
    return Coverage(words, known, new.values.toList())
}

data class Story(val title: String, val body: String)

/** The first line is the title; the rest, with blank lines between paragraphs, is the story. */
fun parseStory(text: String): Story {
    val lines = text.trim().lines()
    val title = lines.firstOrNull().orEmpty().trim().removePrefix("#").trim().removeSurrounding("「", "」")
    val body = lines.drop(1).joinToString("\n").trim()
    return if (body.isEmpty()) Story("", title) else Story(title, body)
}

object StoryPrompts {
    val system = """
        You write short stories in Japanese for someone learning the language, as a graded reader.
        The learner gives you the words they know. Write so that almost every word is one of them,
        so the story is easy and pleasant to read, and teach a few new words through context.

        Rules:
        - Use words from the known list freely. Particles, conjugations, auxiliaries, numbers, counters,
          and the names of people and places are always allowed.
        - Use every word in the "introduce" list, in a way that makes its meaning clear from context,
          and use each more than once if it fits naturally.
        - Use some of the "practice" words.
        - Apart from those, use at most the number of other words the request allows. When you need a
          word that isn't listed, prefer a simpler way to say it with known words.
        - Keep to the grammar the request names.
        - Write natural Japanese, spelling each listed word the way it is listed (kanji or kana).
        - Give the story a beginning, a middle and an end. Short paragraphs.

        Reply with the title on the first line, a blank line, and then the story, with a blank line
        between paragraphs. Write nothing else: no English, no readings in brackets, no notes.
    """.trimIndent()

    fun request(r: StoryRequest): String = buildString {
        appendLine("Write a story of about ${r.length.characters} Japanese characters.")
        if (r.prompt.isNotBlank()) appendLine("What it should be about: ${r.prompt.trim()}")
        appendLine("Grammar: JLPT N${r.grammarLevel} or easier.")
        val others = (r.budget - r.introduce.size).coerceAtLeast(0)
        appendLine("Other words allowed beyond the lists: $others.")
        appendLine()
        appendLine("Introduce: " + r.introduce.joinToString("、") { it.label }.ifEmpty { "(none)" })
        appendLine("Practice: " + r.practice.joinToString("、") { it.label }.ifEmpty { "(none)" })
        appendLine("Known words: " + r.known.joinToString("、") { it.written }.ifEmpty { "(none yet)" })
    }

    fun rewrite(extra: List<ReadingWord>, allowed: Int): String = buildString {
        append("These words in the story aren't on my lists: ")
        append(extra.joinToString("、") { it.lemma })
        appendLine(".")
        append("Only $allowed other ${if (allowed == 1) "word is" else "words are"} allowed. ")
        appendLine(
            "Rewrite the whole story using known words or simpler phrasing in their place, keep the " +
                "introduced words, and reply in the same format.",
        )
    }
}

/** Something that can write a story: a conversation with Claude, or a fake in tests. */
interface StoryModel {
    /** Start a conversation with [system] and [message], returning the reply text. */
    fun start(system: String, message: String): String

    /** Continue the same conversation. */
    fun reply(message: String): String
}

data class WrittenStory(val story: Story, val coverage: Coverage, val rewrites: Int)

/**
 * Writes a story and checks every word against what the learner knows. When
 * the story uses more new words than its budget, it asks for a rewrite, up to
 * [maxRewrites] times, and keeps the version with the fewest extra words.
 */
class StoryWriter(
    private val model: StoryModel,
    private val reader: Reader,
    private val maxRewrites: Int = 2,
) {
    fun write(
        request: StoryRequest,
        isKnown: (Long) -> Boolean,
        progress: (String) -> Unit = {},
    ): WrittenStory {
        val targets = request.introduce.map { it.wordId }.toSet()
        progress("Writing")
        var text = model.start(StoryPrompts.system, StoryPrompts.request(request))
        var best: WrittenStory? = null
        for (attempt in 0..maxRewrites) {
            progress("Checking the words")
            val story = parseStory(text)
            val cov = coverage(reader.read(story.body), isKnown)
            val candidate = WrittenStory(story, cov, attempt)
            if (best == null || cov.newWords.size < best.coverage.newWords.size) best = candidate
            val extra = cov.newWords.filter { it.wordId !in targets }
            val allowed = (request.budget - targets.size).coerceAtLeast(0)
            if (cov.newWords.size <= request.budget || extra.size <= allowed || attempt == maxRewrites) break
            progress(if (maxRewrites > 1) "Rewriting (${attempt + 1} of $maxRewrites)" else "Rewriting")
            text = model.reply(StoryPrompts.rewrite(extra, allowed))
        }
        return best!!
    }
}
