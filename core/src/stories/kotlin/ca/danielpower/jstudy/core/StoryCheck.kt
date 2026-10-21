package ca.danielpower.jstudy.core

import java.io.File

/** Something wrong with the stories, on a page (a chapter or grammar page id) or the path as a whole. */
data class Problem(val page: String?, val text: String) {
    override fun toString() = page?.let { "$it: $text" } ?: text
}

/**
 * The stories checked against the real dictionary: every chapter and grammar
 * page has English for each sentence, every counted word is in the
 * dictionary, topics name real grammar points, each story read in order
 * (after the stories it follows) opens chapter by chapter, with its cast
 * speaking only once introduced and no chapter under half the length of
 * those before it, and the solver reaches every chapter from knowing
 * nothing. `:core:stories` prints what it finds, StoryPathTest fails on it,
 * and [chapter] is what a writer reads about the chapter they're writing.
 */
class StoryCheck(private val tools: StoryTools) {
    private val catalog = tools.catalog
    private val reader = tools.reader
    private val lexicon = tools.lexicon

    /** Every word the chapters use. */
    private val storyWords: Set<Long> by lazy { tools.needs.values.flatMap { n -> n.words.mapNotNull { it.wordId } }.toSet() }

    /** Each chapter's own step: read in order in its story, after the stories it follows. */
    val steps = LinkedHashMap<String, StoryTools.Step>()
    val problems = ArrayList<Problem>()

    /** Words the reader may have looked up wrong, by page: entries off the JLPT lists where one on them is written the same. */
    val readings = LinkedHashMap<String, List<String>>()

    /** What the solver can't reach. */
    val stuck: List<String>

    init {
        val sentences by lazy { StorySource.sentences(tools.root) }
        fun problem(page: String?, text: String) { problems += Problem(page, text) }

        fun page(s: PathStory) {
            val text = tools.text.getValue(s.id)
            val count = text.sumOf { sentenceRanges(it).size }
            if (count != s.translations.size) {
                // Name the sentence the reader splits differently from how it's written.
                val split = sentences[s.id].orEmpty().map { it to reader.read(it).sumOf { p -> sentenceRanges(p).size } }.firstOrNull { it.second != 1 }
                problem(s.id, split?.let { (jp, n) -> "「$jp」 reads as $n sentences, with one translation: split it, or rejoin it with its neighbour" }
                    ?: "$count sentences, ${s.translations.size} translations")
            }
            // Grammar the reader can't explain would go untaught and unchecked: a particle or ending
            // with no grammar point, or a form of a word that no point covers and the dictionary
            // doesn't have whole (いらっしゃいませ is a word; いいじゃん needs じゃん).
            val unexplained = text.flatten().filter { it.grammar.isEmpty() && (it.kind == WordKind.GRAMMAR || it.conjugated) }
            if (unexplained.isNotEmpty()) problem(s.id, "no grammar point explains " + unexplained.joinToString { it.surface } + " (add one to Grammar.kt)")
            // A counter after a number (三人) is read as a counter, rightly.
            val doubtful = text.flatMap { p -> p.filterIndexed { i, w -> p.getOrNull(i - 1)?.surface?.all { it in NUMERALS } != true && w.kind == WordKind.WORD } }
                .filter { it.wordId != null && it.surface.any(Kanjis::isKanji) }
                .distinctBy { it.wordId }
                .mapNotNull { w ->
                    lexicon.likelier(w.lemma, w.wordId!!).takeIf { it.isNotEmpty() }?.let { others ->
                        "${w.lemma} is read ${w.reading} (${lexicon.describe(w.wordId)}), not " +
                            others.joinToString(" or ") { "${it.kana} (N${it.jlpt})" }
                    }
                }
            if (doubtful.isNotEmpty()) readings[s.id] = doubtful
        }

        for ((_, c) in catalog.chapters) {
            val s = c.story
            page(s)
            for (w in tools.needs.getValue(s.id).words.filter { it.wordId == null }) problem(s.id, "${w.lemma} isn't in the dictionary")
            if (c.topic != null && catalog.topic(c.topic) == null) problem(s.id, "no grammar topic ${c.topic}")
            // A pinned reading must be needed, and must take: the reader agrees once it's pinned.
            for ((text, reading) in s.readings) {
                // A dictionary form pins its conjugations too (描く pins 描いた).
                fun read(pins: Map<String, String>) = reader.read(s.body, pins, s.names).flatten().filter { it.surface == text || (it.kind == WordKind.WORD && it.lemma == text) }
                val pinned = read(s.readings)
                if (pinned.isEmpty()) problem(s.id, "readings: no word written $text")
                else if (pinned.any { (it.surface == text && it.reading != reading) || it.wordId == null }) problem(s.id, "readings: the dictionary has no $text read $reading")
                else if (read(emptyMap()) == pinned) problem(s.id, "readings: $text is already read $reading")
            }
        }
        for (t in catalog.grammar) {
            page(t.page)
            for (id in t.points.filter { grammarPoint(it) == null }) problem(t.page.id, "no grammar point $id")
            if (catalog.teaching(t.id).isEmpty()) problem(t.page.id, "no chapter teaches it")
            lesson(t)?.let { problem(t.page.id, it) }
            for (run in tools.runs.getValue(Lessons.runsId(t))) {
                for (w in run.filter { it.kind == WordKind.WORD && it.wordId == null }) problem(t.page.id, "${w.lemma} in its notes isn't in the dictionary")
                misread(t, run)?.let { problem(t.page.id, it) }
            }
        }

        // Anyone in a story's cast. Any other speaker is a description (店員, 男の人), fine anywhere.
        val cast = catalog.chapters.flatMap { it.second.meets.map { m -> m.name } }.toSet()
        // Each of the cast has a voice to read their lines in, the same wherever they're met.
        for ((_, c) in catalog.chapters) for (m in c.meets) {
            if (m.voice == null) problem(c.story.id, "${m.name}: give them a \"voice\" (woman, man or child)")
        }
        for ((name, met) in catalog.chapters.flatMap { it.second.meets }.groupBy { it.name }) {
            if (met.mapNotNull { it.voice }.distinct().size > 1) problem(null, "$name has a different voice where they're met again")
        }
        for (name in catalog.voices.cast.keys - cast) problem(null, "voices.json: $name isn't in any story's cast")
        // Each story read in order, after the ones it follows, opens one chapter at a time.
        // A side story opens with nothing new at all. The stories it follows are checked on their own turn.
        for (series in catalog.series) {
            val walked = tools.walk(series) { problem(series.id, "no story or chapter $it") }
            val met = HashSet<String>()
            for ((i, step) in walked.withIndex()) {
                val c = step.chapter
                met += c.meets.map { it.name }
                if (!step.own) continue
                val gap = step.gap
                val id = c.story.id
                steps[id] = step
                val early = Speech.speakers(c.story.body).filterNotNull().filter { it in cast && it !in met }.distinct()
                if (early.isNotEmpty()) problem(id, "${early.joinToString()} ${if (early.size == 1) "speaks" else "speak"} before they're introduced")
                // Chapters grow as the words do; a short one now and then is fine, but not half the length of what came before.
                val recent = walked.subList(maxOf(0, i - 5), i).map { it.needs.running }.sorted()
                if (recent.isNotEmpty() && step.needs.running * 2 < recent[recent.size / 2])
                    problem(id, "${step.needs.running} words, under half the ${recent[recent.size / 2]} of the chapters before it")
                if (gap.words.size > gap.allowance) problem(id, "${gap.words.size} new words, ${gap.allowance} allowed")
                if (gap.grammar.isNotEmpty()) problem(id, "grammar not taught yet: ${gap.grammar}")
                if (series.side && (gap.words.isNotEmpty() || c.topic != null))
                    problem(id, "side story has something new: ${gap.words.map { it.lemma }} ${c.topic.orEmpty()}")
                if (c.plot.isBlank()) problem(id, "no plot line")
                // The topic's example, shown before reading, uses only words you have by then.
                catalog.topic(c.topic)?.example?.let { (jp, _) ->
                    val unknown = StoryPath.words(reader.read(jp, emptyMap(), c.story.names)).filter { it.wordId !in step.words }
                    if (unknown.isNotEmpty()) problem(id, "${c.topic} example has new words ${unknown.map { it.lemma }}")
                }
            }
        }

        val all = catalog.chapters.map { tools.needs.getValue(it.second.story.id) }
        val order = StoryPath.solve(all).toSet()
        stuck = all.indices.filter { it !in order }.map { catalog.chapters[it].second.story.id }
        if (stuck.isNotEmpty()) problem(null, "the solver can't reach ${stuck.joinToString()}")
    }

    /**
     * What's wrong with [topic]'s page as a lesson (the lesson guide in
     * tools/stories/AGENTS.md), or null: a first line from the chapter that
     * teaches it, a short summary, at most three examples and two notes
     * before "More", English before "More" in proportion to that chapter,
     * and a short forms table.
     */
    private fun lesson(topic: GrammarTopic): String? {
        val teaching = catalog.teaching(topic.id)
        val chapter = teaching.firstOrNull() ?: return null
        val budget = lessonBudget(tools.needs.getValue(chapter.second.story.id).running)
        val shown = topic.page.notes.filter { !it.more }
        val words = shown.sumOf { countWords(it.text) }
        val first = topic.example?.first
        return when {
            first != null && teaching.none { (_, c) -> sentences(c.story.id).any { Lessons.sameLine(first, it) } } ->
                "its first line, $first, isn't a line of ${chapter.second.story.id}; open with one of the chapter's own sentences"
            countWords(topic.summary) > SUMMARY_WORDS -> "its summary is ${countWords(topic.summary)} words; keep it to $SUMMARY_WORDS"
            topic.moreAt > 3 -> "${topic.moreAt} examples before More; keep three at most and fold the rest"
            shown.size > 2 -> "${shown.size} notes before More; keep two at most and fold the rest"
            words > budget -> "$words words of notes before More, $budget for a chapter of its length; fold the rest"
            (topic.forms?.rows?.size ?: 0) > 6 -> "${topic.forms!!.rows.size} rows of forms; keep six at most"
            else -> null
        }
    }

    /** Chapter [id]'s sentences, as it's read. */
    private fun sentences(id: String): List<String> = tools.text.getValue(id).flatMap { words ->
        sentenceRanges(words).map { r -> r.joinToString("") { words[it].surface } }
    }

    /**
     * Why a short run of Japanese in [topic]'s notes would mislead whoever taps
     * it, or null: a piece of a word in kana read as a word no story uses
     * (たい as 対, versus; かも as 鴨, a duck), or bare grammar read as another
     * lesson's (られる as the passive). Lone kana and ～ endings are shown as
     * written ([Lessons.fragment]).
     */
    private fun misread(topic: GrammarTopic, run: List<ReadingWord>): String? {
        val text = run.filter { it.kind != WordKind.PUNCTUATION }.joinToString("") { it.surface }
        if (text.length > SHORT_RUN) return null
        val word = run.firstOrNull { it.kind == WordKind.WORD && it.wordId != null && it.wordId !in storyWords }?.takeIf { Kana.isKana(text) }
        val point = run.flatMap { it.grammar }.firstOrNull { it.point.id !in topic.points }?.point
            ?.takeIf { run.none { it.kind == WordKind.WORD || it.kind == WordKind.NAME } }
        val read = word?.let { "the word ${lexicon.describe(it.wordId).substringBefore(' ')}" } ?: point?.let { "${it.pattern}, ${it.meaning}" } ?: return null
        return "$text in its notes reads as $read: write it as ～$text, or as a whole word"
    }

    /** A new word as a writer wants it: 歌 (うた, N5). */
    private fun word(w: StepWord) = w.wordId?.let(lexicon::entry)?.let { e ->
        val level = if (e.jlpt == 0) "not JLPT" else "N${e.jlpt}"
        if (Kana.isKana(w.lemma)) "${w.lemma} ($level)" else "${w.lemma} (${e.kana}, $level)"
    } ?: "${w.lemma} (no entry)"

    /** What a writer needs to know about chapter or grammar page [id]: its new words and grammar, and anything wrong with it. */
    fun chapter(id: String): String = buildString {
        val step = steps[id]
        val page = catalog.chapters.firstOrNull { it.second.story.id == id }?.second?.story ?: catalog.grammar.firstOrNull { it.page.id == id }?.page
            ?: return "No chapter or grammar page $id. Chapters are story/slug (new-housemate/through-the-wall), grammar pages grammar/id."
        appendLine("$id: ${page.title}")
        if (step != null) {
            val gap = step.gap
            appendLine("  ${step.needs.running} words, ${gap.words.size} new of ${gap.allowance} allowed${if (gap.words.isEmpty()) "" else ": " + gap.words.joinToString(", ", transform = ::word)}")
            appendLine("  Grammar: ${step.needs.grammar.sorted().joinToString(" ")}")
            if (gap.grammar.isNotEmpty()) appendLine("  Not taught yet: ${gap.grammar.joinToString(" ") { p -> catalog.topicOf(p)?.let { "$p (topic ${it.id})" } ?: p }}")
            if (id in stuck) appendLine("  The solver can't reach it.")
        }
        val own = problems.filter { it.page == id }
        appendLine(if (own.isEmpty()) "  No problems." else "  Problems:")
        for (p in own) appendLine("    ${p.text}")
        // Off the lists, the reader goes by its tokenizer and how common each entry is, which can miss
        // what the story means (弦 as つる, a bowstring, in a story about guitars).
        val off = tools.text.getValue(id).flatten().filter { it.kind == WordKind.WORD && it.surface.any(Kanjis::isKanji) && it.wordId?.let(lexicon::entry)?.jlpt == 0 }
            .distinctBy { it.wordId }
        if (off.isNotEmpty()) appendLine("  Off the JLPT lists, so check they're read as meant: ${off.joinToString(", ") { "${it.lemma} (${lexicon.entry(it.wordId!!)!!.kana})" }}")
        readings[id]?.let { r ->
            appendLine("  Readings to check (pin one in \"readings\" if it's wrong):")
            for (line in r) appendLine("    $line")
        }
    }

    /** build/reports/story-path.txt: each story's chapters with their new words, and everything wrong. */
    fun report(): String = buildString {
        for (series in catalog.series) {
            for (c in series.chapters) {
                val step = steps[c.story.id] ?: continue
                val gap = step.gap
                appendLine("${c.story.id} ${c.story.title}  words ${gap.words.size}/${gap.allowance}, total ${step.words.size}${c.topic?.let { "; teaches $it" }.orEmpty()}")
                appendLine("    grammar ${step.needs.grammar.sorted()}")
                for (w in gap.words) appendLine("    ${w.lemma} ${w.wordId} ${lexicon.describe(w.wordId)}")
            }
            appendLine()
        }
        // Words met in only one chapter get no reinforcement from reading; aim to bring them back.
        val chaptersWith = HashMap<Long, Int>()
        for (n in tools.needs.values) for (id in n.words.mapNotNull { it.wordId }.distinct()) chaptersWith.merge(id, 1, Int::plus)
        val once = chaptersWith.filterValues { it == 1 }.keys
        appendLine("Words in one chapter only (${once.size} of ${chaptersWith.size}): ${once.joinToString(" ") { lexicon.describe(it).substringBefore('/') }}")
        if (readings.isNotEmpty()) {
            appendLine()
            appendLine("Readings to check:")
            for ((id, r) in readings) for (line in r) appendLine("$id: $line")
        }
        appendLine()
        appendLine("Problems:")
        for (p in problems.distinct()) appendLine(p.toString())
    }

    companion object {
        private const val NUMERALS = "0123456789０１２３４５６７８９一二三四五六七八九十百千万何"

        /** A run of Japanese in a lesson's notes this short is checked for what it reads as. */
        private const val SHORT_RUN = 3

        /** The most a topic's summary may be, in words. */
        const val SUMMARY_WORDS = 35

        /**
         * The most words of notes a lesson shows before "More": short for the
         * first chapters, which are a dozen words long, and up to 90 once
         * chapters reach 120 words.
         */
        fun lessonBudget(chapterWords: Int) = minOf(90, 30 + chapterWords / 2)

        /** Words of English, counting a run of Japanese as one. */
        fun countWords(text: String) = text.split(' ').count { it.any(Char::isLetterOrDigit) }

        /** Reads a chapter named by id or by its file (tools/stories/series/new-housemate/through-the-wall.json). */
        fun pageId(arg: String): String = File(arg).path.replace('\\', '/').substringAfter("tools/stories/series/").removeSuffix(".json")
    }
}
