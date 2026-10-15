package ca.danielpower.jstudy.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import ca.danielpower.jstudy.core.Answer
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.DailyLimits
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.Fsrs
import ca.danielpower.jstudy.core.ImportPlan
import ca.danielpower.jstudy.core.KanaCourse
import ca.danielpower.jstudy.core.KanaKnown
import ca.danielpower.jstudy.core.KanaUnit
import ca.danielpower.jstudy.core.Kanji
import ca.danielpower.jstudy.core.Kanjis
import ca.danielpower.jstudy.core.LocalWord
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.RenshuuImport
import ca.danielpower.jstudy.core.RenshuuTerm
import ca.danielpower.jstudy.core.Reviewer
import ca.danielpower.jstudy.core.Script
import ca.danielpower.jstudy.core.StudyAhead
import ca.danielpower.jstudy.core.StudyClock
import ca.danielpower.jstudy.core.StudySession
import ca.danielpower.jstudy.core.StudySettings
import ca.danielpower.jstudy.core.WordProgress
import ca.danielpower.jstudy.BuildConfig
import ca.danielpower.jstudy.core.ClaudeException
import ca.danielpower.jstudy.core.Coverage
import ca.danielpower.jstudy.core.Reader
import ca.danielpower.jstudy.core.ReadingWord
import ca.danielpower.jstudy.core.CastMember
import ca.danielpower.jstudy.core.Chapter
import ca.danielpower.jstudy.core.GrammarTopic
import ca.danielpower.jstudy.core.Pace
import ca.danielpower.jstudy.core.PathSeries
import ca.danielpower.jstudy.core.PathStory
import ca.danielpower.jstudy.core.Story
import ca.danielpower.jstudy.core.StepGap
import ca.danielpower.jstudy.core.StepNeeds
import ca.danielpower.jstudy.core.StoryPath
import ca.danielpower.jstudy.core.StoryDifficulty
import ca.danielpower.jstudy.core.StoryLength
import ca.danielpower.jstudy.core.StoryRequest
import ca.danielpower.jstudy.core.StoryServer
import ca.danielpower.jstudy.core.StoryTranslator
import ca.danielpower.jstudy.core.StoryUsage
import ca.danielpower.jstudy.core.StoryWriter
import ca.danielpower.jstudy.core.UiLabel
import ca.danielpower.jstudy.core.UiLabels
import ca.danielpower.jstudy.core.coverage
import ca.danielpower.jstudy.core.newWordBudget
import ca.danielpower.jstudy.core.sentenceRanges
import ca.danielpower.jstudy.core.sentenceTexts
import kotlin.random.Random

data class Summary(
    val newCards: Int,
    val learning: Int,
    val reviews: Int,
    val streak: Int,
    val daysAhead: Int,
    val reviewedToday: Int,
    val correctToday: Int,
    val wordsStarted: Int,
    val wordsTotal: Int,
    val today: Long,
    /** Cards answered per study day over the last year. */
    val answersPerDay: Map<Long, Int>,
    /** Kana learned and still to learn; both 0 when you read all kana. */
    val kanaLearned: Int = 0,
    val kanaTotal: Int = 0,
) {
    val anythingDue get() = newCards + learning + reviews > 0
}

data class WordDetail(
    val word: Word,
    val forms: List<String>,
    /** Part-of-speech names for each sense, in order. */
    val partsOfSpeech: List<String>,
    val cards: List<Card>,
    val mastery: Int,
    val suspended: Boolean,
    val history: List<Pair<Direction, ReviewEntry>>,
    /** The kanji in the word's spelling, in order. */
    val kanji: List<Kanji>,
)

/** A kanji's page: its entry and the words written with it. */
data class KanjiDetail(
    val kanji: Kanji,
    /** Words you've started that are written with it, best known first. */
    val yourWords: List<KanjiWordRow>,
    /** Other common words written with it, in study order. */
    val moreWords: List<Entry>,
    /** The dictionary readings your words use. */
    val readingsMet: Set<String>,
)

/** One of your words on a kanji's page, with the kanji's reading in it (its dictionary form when it has one). */
data class KanjiWordRow(val entry: Entry, val mastery: Int, val reading: String?)

/** Everything needed to take back the last answer. */
data class Undo(
    val cardBefore: Card,
    val progressBefore: WordProgress,
    /** Null when the action wasn't a review (marking a word known). */
    val logId: Long?,
    val productionCardId: Long?,
)

/** All reads and writes go through here; call it off the main thread. */
/** A saved story split into words, with what's needed to show each one. */
data class StoryReading(
    val story: SavedStory,
    /** The title's words, tappable like the story's. */
    val title: List<ReadingWord>,
    val paragraphs: List<List<ReadingWord>>,
    /** Dictionary entries for the story's words. */
    val words: Map<Long, Word>,
    val statuses: Map<Long, WordStatus>,
    val coverage: Coverage,
    /** The sequel, once one is written. */
    val next: Long?,
    /** For a grammar topic's page: its English notes between the examples. */
    val lesson: PathStory? = null,
) {
    /** New words with a dictionary entry, in the order they first appear, with where: the end page lists these. */
    val newWords: List<Pair<Pair<Int, Int>, Word>> by lazy {
        coverage.newWords.mapNotNull { w ->
            val entry = w.wordId?.let { words[it] } ?: return@mapNotNull null
            val p = paragraphs.indexOfFirst { para -> para.any { it.key == w.key } }
            (p to paragraphs[p].indexOfFirst { it.key == w.key }) to entry
        }
    }

    /** The word at [i] in paragraph [p], where paragraph [TITLE] is the title. */
    fun word(p: Int, i: Int): ReadingWord? = (if (p == TITLE) title else paragraphs.getOrNull(p))?.getOrNull(i)

    /** Each paragraph's sentences, the title's first; the translations follow this order. */
    private val sentences = (listOf(title) + paragraphs).map(::sentenceRanges)

    /** The sentence holding word [i] of paragraph [p]. */
    fun sentence(p: Int, i: Int): StorySentence? {
        val k = if (p == TITLE) 0 else p + 1
        val j = sentences.getOrNull(k)?.indexOfFirst { i in it }?.takeIf { it >= 0 } ?: return null
        val translations = story.translations.takeIf { it.size == sentences.sumOf { s -> s.size } }
        return StorySentence(sentences[k][j], translations?.get(sentences.take(k).sumOf { it.size } + j))
    }

    companion object {
        const val TITLE = -1
    }
}

/** What to learn before reading a chapter: its new kana and words, and the grammar topic it brings in. */
data class StepToLearn(val kana: List<KanaUnit>, val words: List<Word>, val topic: GrammarTopic?) {
    val isEmpty get() = kana.isEmpty() && words.isEmpty() && topic == null
}

/** A chapter of a story that comes with the app, and what of it you haven't learned. */
data class ChapterEntry(
    val series: PathSeries,
    val chapter: Chapter,
    val story: SavedStory,
    val gap: StepGap,
    /** The grammar topic it teaches, when you haven't seen it yet. */
    val newTopic: GrammarTopic?,
) {
    val ready get() = gap.ready
    val read get() = story.readAt != null

    /** Open, with no new words, kana or grammar: something new to read with nothing to learn. */
    val nothingNew get() = ready && gap.words.isEmpty() && gap.kana.isEmpty() && newTopic == null
}

/** A story that comes with the app, with each of its chapters. */
data class SeriesEntry(val series: PathSeries, val chapters: List<ChapterEntry>) {
    val readCount get() = chapters.count { it.read }

    /** The first chapter that's open and unread. */
    val next get() = chapters.firstOrNull { it.ready && !it.read }

    /** On the list once something in it is open, or read. */
    val shown get() = chapters.any { it.ready || it.read }

    /** The people you've met in the chapters you've read, in order. */
    val cast: List<CastMember> get() = chapters.filter { it.read }.flatMap { it.chapter.meets }.distinctBy { it.name }

    /** Whether chapters you haven't read introduce anyone else. */
    val moreCast get() = chapters.any { !it.read && it.chapter.meets.isNotEmpty() }
}

/** Why a chapter is locked, and what would open it. */
data class LockInfo(
    val entry: ChapterEntry,
    /** New words with dictionary entries, and how many it allows. */
    val words: List<Word>,
    val kana: List<KanaUnit>,
    /** Grammar it uses that you haven't seen, with the chapters that teach it. */
    val grammar: List<Pair<GrammarTopic, List<ChapterEntry>>>,
    /** Open, unread chapters that have some of the new words, most first, with how many. */
    val sources: List<Pair<ChapterEntry, Int>>,
    /** Reading this story's earlier chapters gives you every one of the new words. */
    val earlierCover: Boolean,
)

/** A grammar topic for the Words tab: whether you've met it, where it's taught, and its page. */
data class GrammarRow(val topic: GrammarTopic, val seen: Boolean, val taughtIn: ChapterEntry?, val pageId: Long)

/** A sentence of a story: its words in their paragraph, and its English if the story has translations. */
/** The story reader's typefaces: two sans serifs and a serif, all bundled under the OFL. */
enum class ReaderFont { SANS, SERIF, ROUNDED }

/** How stories are set: text size in sp, typeface, and whether columns run top to bottom. */
data class ReaderStyle(
    val size: Int = 22,
    val font: ReaderFont = ReaderFont.SANS,
    /** Set top to bottom, right to left, as Japanese books are. */
    val vertical: Boolean = false,
) {
    companion object {
        val SIZES = listOf(18, 22, 26, 30)
    }
}

data class StorySentence(val words: IntRange, val translation: String?)

/** Numbers for the stats screen. */
data class Stats(
    val today: Long,
    /** Reviews due on each of the next 30 days; today includes anything overdue and still learning. */
    val forecast: List<Int>,
    /** Answers and right answers on each of the last 30 days, oldest first. */
    val answers: List<Pair<Int, Int>>,
    /** Right and total answers on cards in review, over the last 30 days and ever. */
    val recall30: Pair<Int, Int>,
    val recallAll: Pair<Int, Int>,
    val levels: List<LevelStats>,
    /** Hiragana and katakana you're learning here; empty when you read them already. */
    val kana: List<LevelStats>,
    val wordsStarted: Int,
    val totalAnswers: Int,
    val studyDays: Int,
)

/** Words of one JLPT level, or kana of one script, by how far along they are. Each count includes the ones after it. */
data class LevelStats(val label: String, val total: Int, val started: Int, val known: Int, val mastered: Int)

/** The daily reminder. There's no default time: [minuteOfDay] is null until you pick one. */
data class Reminder(val enabled: Boolean, val minuteOfDay: Int?) {
    val time: Int? get() = minuteOfDay?.takeIf { enabled }
}

class BackupException(message: String) : Exception(message)

class Repository(context: Context) {
    private val appContext = context.applicationContext
    val dictionary = Dictionary(context)
    private val store = ProgressStore(context)

    /** The story server sign-in. Kept out of the progress database so a backup doesn't carry it. */
    private val secrets = context.getSharedPreferences("secrets", Context.MODE_PRIVATE).also {
        // Stories used to need your own Claude API key; don't leave one lying around.
        it.edit().remove("claude_api_key").apply()
    }
    private val storyServer = StoryServer(BuildConfig.STORY_SERVER_URL)
    private val reader by lazy { Reader(dictionary) }

    /**
     * The words each UI label needs, kept between launches so labels are in
     * the right language from the first frame. Derived, so not in a backup.
     */
    private val labels = context.getSharedPreferences("labels", Context.MODE_PRIVATE)

    // ------------------------------------------------------------ settings

    fun settings(): StudySettings {
        val d = StudySettings()
        fun int(key: String, default: Int) = store.getValue(key)?.toIntOrNull() ?: default
        return runCatching {
            StudySettings(
                newPerDay = int("daily_new", d.newPerDay),
                reviewNudge = int("review_nudge", d.reviewNudge),
                reviewCap = int("review_cap", d.reviewCap),
                rolloverHour = int("rollover_hour", d.rolloverHour),
            )
        }.getOrDefault(d)
    }

    fun saveSettings(s: StudySettings) {
        store.setValues(
            mapOf(
                "daily_new" to s.newPerDay.toString(),
                "review_nudge" to s.reviewNudge.toString(),
                "review_cap" to s.reviewCap.toString(),
                "rollover_hour" to s.rolloverHour.toString(),
            ),
        )
    }

    private fun ahead(): StudyAhead = StudyAhead(
        daysAhead = store.getValue("days_ahead")?.toIntOrNull() ?: 0,
        day = store.getValue("ahead_day")?.toLongOrNull() ?: 0,
        extraDaysToday = store.getValue("ahead_extra")?.toIntOrNull() ?: 0,
    )

    private fun saveAhead(a: StudyAhead) {
        store.setValues(
            mapOf(
                "days_ahead" to a.daysAhead.toString(),
                "ahead_day" to a.day.toString(),
                "ahead_extra" to a.extraDaysToday.toString(),
            ),
        )
    }

    fun clock(settings: StudySettings = settings()) = StudyClock(rolloverHour = settings.rolloverHour)

    // ------------------------------------------------------------ queue

    private fun limits(settings: StudySettings, clock: StudyClock, now: Long): DailyLimits {
        val today = clock.dayOf(now)
        return DailyLimits.of(
            settings = settings,
            ahead = ahead(),
            clock = clock,
            now = now,
            // Production cards fill what today's new words and kana leave of the daily limit.
            newIntroducedToday = store.count("SELECT COUNT(*) FROM card WHERE introduced_day = ?", today),
            reviewsDoneToday = store.count(
                "SELECT COUNT(*) FROM review_log WHERE day = ? AND state_before = ?",
                today,
                CardState.REVIEW.value,
            ),
        )
    }

    fun session(now: Long): StudySession {
        val settings = settings()
        val clock = clock(settings)
        val limits = limits(settings, clock, now)
        val learning = store.cards(
            "state IN (?, ?) AND suspended = 0",
            arrayOf(CardState.LEARNING.value.toString(), CardState.RELEARNING.value.toString()),
            "due_at",
        )
        val reviews = store.cards(
            "state = ? AND due_at < ? AND suspended = 0",
            arrayOf(CardState.REVIEW.value.toString(), limits.reviewDueBefore.toString()),
            "due_at, id",
            limits.reviews,
        )
        // New words and kana come from the story path, not reviews; only unlocked production cards are new here.
        val newWords = store.cards(
            "state = ? AND direction = ? AND suspended = 0",
            arrayOf(CardState.NEW.value.toString(), Direction.PRODUCTION.value.toString()),
            "sort_key, id",
            limits.newCards,
        )
        return StudySession(reviews, newWords, learning, random = Random.Default)
    }

    /**
     * The new words and kana you've started today, against the daily limit,
     * and the reviews due today. "I know these" and imports start nothing,
     * so they don't count.
     */
    fun pace(now: Long): Pace {
        val settings = settings()
        val clock = clock(settings)
        val today = clock.dayOf(now)
        return Pace(
            newToday = store.count(
                "SELECT COUNT(*) FROM card WHERE introduced_day = ? AND direction != ?",
                today,
                Direction.PRODUCTION.value,
            ),
            limit = settings.newPerDay,
            reviewsDue = store.count(
                "SELECT COUNT(*) FROM card WHERE state = ? AND due_at < ? AND suspended = 0",
                CardState.REVIEW.value,
                clock.startOf(today + 1),
            ),
            reviewNudge = settings.reviewNudge,
        )
    }

    fun endOfToday(now: Long): Long {
        val clock = clock()
        return clock.startOf(clock.dayOf(now) + 1)
    }

    // ------------------------------------------------------------ answers

    fun progress(wordId: Long): WordProgress = store.progress(wordId)

    fun answer(card: Card, correct: Boolean, now: Long): Pair<Answer, Undo> {
        val settings = settings()
        val reviewer = Reviewer(Fsrs(), clock(settings))
        val before = store.progress(card.wordId)
        return save(card, before, reviewer.answer(card, before, correct, now, Random.Default), now)
    }

    private fun save(card: Card, before: WordProgress, answer: Answer, now: Long): Pair<Answer, Undo> =
        store.transaction { db ->
            store.updateCard(db, answer.card)
            store.saveProgress(db, answer.progress)
            val logId = store.insertLog(db, answer.log)
            val production = if (answer.unlockedProduction) {
                // Unlocked production cards come before any new word, oldest first.
                store.insertNewCard(db, card.wordId, Direction.PRODUCTION, now / 1000 - PRODUCTION_FIRST)
                    .takeIf { it > 0 }
            } else {
                null
            }
            answer to Undo(card, before, logId, production)
        }

    /** First sight of a word you don't know yet: start learning it, ungraded. */
    fun introduce(card: Card, now: Long): Pair<Card, Undo> {
        val settings = settings()
        val reviewer = Reviewer(Fsrs(), clock(settings))
        val before = store.progress(card.wordId)
        val introduced = reviewer.introduce(card, now)
        store.transaction { db -> store.updateCard(db, introduced) }
        return introduced to Undo(card, before, null, null)
    }

    /** Mark a word you already know at [mastery]. Returns the review interval in days. */
    fun markKnown(card: Card, mastery: Int, now: Long): Pair<Int, Undo> {
        val settings = settings()
        val reviewer = Reviewer(Fsrs(), clock(settings))
        val before = store.progress(card.wordId)
        val known = reviewer.markKnown(card, before, mastery, now, Random.Default)
        return store.transaction { db ->
            store.updateCard(db, known.card)
            store.saveProgress(db, known.progress)
            val production = if (known.unlockedProduction) {
                store.insertNewCard(db, card.wordId, Direction.PRODUCTION, now / 1000 - PRODUCTION_FIRST)
                    .takeIf { it > 0 }
            } else {
                null
            }
            known.intervalDays to Undo(card, before, null, production)
        }
    }

    fun undo(undo: Undo) {
        store.transaction { db ->
            store.updateCard(db, undo.cardBefore)
            store.saveProgress(db, undo.progressBefore)
            undo.logId?.let { store.deleteLog(db, it) }
            undo.productionCardId?.let { store.deleteCard(db, it) }
        }
    }

    fun studyMore(now: Long) {
        val clock = clock()
        saveAhead(ahead().studyMore(clock.dayOf(now)))
    }

    // ------------------------------------------------------------ dictionary

    fun wordStatuses(): Map<Long, WordStatus> = store.wordStatuses()

    fun wordDetail(wordId: Long): WordDetail? {
        val word = dictionary.word(wordId) ?: return null
        val names = dictionary.partOfSpeechNames()
        return WordDetail(
            word = word,
            forms = dictionary.forms(wordId),
            partsOfSpeech = word.senses.map { sense -> sense.partsOfSpeech.joinToString(", ") { names[it] ?: it } },
            cards = store.cards("word_id = ?", arrayOf(wordId.toString()), "direction"),
            mastery = store.progress(wordId).mastery,
            suspended = store.isSuspended(wordId),
            history = store.logs(wordId),
            kanji = kanjiIn(word),
        )
    }

    private fun recognitionCard(wordId: Long): Card? = mainCard(wordId, Direction.RECOGNITION)

    /** The card mastery follows: recognition for a word, the kana card for a kana. */
    private fun mainCard(wordId: Long, direction: Direction): Card? =
        store.cards(
            "word_id = ? AND direction = ?",
            arrayOf(wordId.toString(), direction.value.toString()),
            "id",
        ).firstOrNull()

    /** Put a word at the front of the new-word queue. */
    fun learnNext(wordId: Long, now: Long) {
        store.transaction { db ->
            store.setSuspended(db, wordId, false)
            val card = recognitionCard(wordId)
            if (card == null) {
                store.insertNewCard(db, wordId, Direction.RECOGNITION, now / 1000 - LEARN_NEXT_FIRST)
            } else if (card.state == CardState.NEW) {
                store.setSortKey(db, card.id, now / 1000 - LEARN_NEXT_FIRST)
            }
        }
    }

    /** Mark a word from the dictionary, or a kana, as known at [mastery], starting it if needed. */
    fun markKnown(wordId: Long, mastery: Int, now: Long) {
        val direction = if (KanaCourse.isKanaId(wordId)) Direction.KANA else Direction.RECOGNITION
        store.transaction { db ->
            store.setSuspended(db, wordId, false)
            if (mainCard(wordId, direction) == null) {
                store.insertNewCard(db, wordId, direction, now / 1000 - LEARN_NEXT_FIRST)
            }
        }
        markKnown(mainCard(wordId, direction) ?: return, mastery, now)
    }

    /** Forget a word: its cards, mastery and history. It returns to its place in the study order. */
    fun reset(wordId: Long) {
        store.transaction { db -> store.deleteWord(db, wordId) }
    }

    fun setSuspended(wordId: Long, suspended: Boolean) {
        store.transaction { db ->
            if (suspended && recognitionCard(wordId) == null) {
                // Keeps an unstarted word out of the new-word queue.
                store.insertNewCard(db, wordId, Direction.RECOGNITION, 0, suspended = true)
            }
            store.setSuspended(db, wordId, suspended)
            if (!suspended) {
                // An unstarted word goes back to its usual place in the study order.
                val card = recognitionCard(wordId)
                if (card != null && card.state == CardState.NEW && card.reps == 0) store.deleteCard(db, card.id)
            }
        }
    }

    // ------------------------------------------------------------ kanji

    /** The kanji in a word's spelling, in order; none for a word shown in kana. */
    fun kanjiIn(word: Word): List<Kanji> =
        if (word.showsKanji) dictionary.kanji(Kanjis.inText(word.display)) else emptyList()

    fun kanjiDetail(literal: String): KanjiDetail? {
        val kanji = dictionary.kanji(literal) ?: return null
        // Words you've started and not set aside that are written with it.
        val started = store.wordStatuses().filter { (_, st) -> st.state != CardState.NEW && !st.suspended }
        val mine = dictionary.kanjiSpellings(started.keys).mapNotNull { (id, segments) ->
            Kanjis.inWord(segments).firstOrNull { it.kanji == literal }?.let { id to it }
        }
        val entries = dictionary.entries(mine.map { it.first }).associateBy { it.id }
        val yourWords = mine.mapNotNull { (id, use) ->
            val entry = entries[id] ?: return@mapNotNull null
            val reading = use.reading?.let { Kanjis.dictionaryReading(kanji, it, use.after) ?: it }
            KanjiWordRow(entry, started.getValue(id).mastery, reading)
        }.sortedByDescending { it.mastery }
        val yours = yourWords.mapTo(HashSet()) { it.entry.id }
        return KanjiDetail(
            kanji = kanji,
            yourWords = yourWords,
            moreWords = dictionary.wordsWithKanji(literal, MORE_WORDS + yours.size).filter { it.id !in yours }.take(MORE_WORDS),
            readingsMet = yourWords.mapNotNullTo(HashSet()) { it.reading },
        )
    }

    // ------------------------------------------------------------ renshuu

    /** What importing [terms] would change. */
    fun renshuuPlan(terms: List<RenshuuTerm>): ImportPlan {
        val ids = dictionary.wordIds()
        val local = (store.wordStatuses() + store.kanaStatuses()).mapValues { (_, s) ->
            LocalWord(s.mastery, started = s.state != CardState.NEW, suspended = s.suspended)
        }
        return RenshuuImport.plan(terms, { it in ids }, local)
    }

    /**
     * Mark each word known at its mastery, as "I already know this" does, in
     * one transaction. With [unlockProduction] off, English-to-Japanese cards
     * wait until each word is next answered right, so a big batch doesn't
     * crowd out new words.
     */
    fun importMastery(updates: Map<Long, Int>, now: Long, unlockProduction: Boolean = true) {
        val settings = settings()
        val reviewer = Reviewer(Fsrs(), clock(settings))
        store.transaction { db ->
            for ((wordId, mastery) in updates) {
                // Kana from Renshuu go to kana cards, which stay set aside for a script you've said you read.
                val kana = KanaCourse.unit(wordId)
                val direction = if (kana != null) Direction.KANA else Direction.RECOGNITION
                if (mainCard(wordId, direction) == null) {
                    store.insertNewCard(db, wordId, direction, now / 1000 - LEARN_NEXT_FIRST)
                    if (kana != null && kanaKnown().covers(kana.script)) store.setSuspended(db, wordId, true)
                }
                val card = mainCard(wordId, direction) ?: continue
                val known = reviewer.markKnown(card, store.progress(wordId), mastery, now, Random.Default)
                store.updateCard(db, known.card)
                store.saveProgress(db, if (unlockProduction) known.progress else known.progress.copy(productionUnlocked = false))
                if (known.unlockedProduction && unlockProduction) {
                    store.insertNewCard(db, wordId, Direction.PRODUCTION, now / 1000 - PRODUCTION_FIRST)
                }
            }
        }
    }

    // ------------------------------------------------------------ app language

    /** The label words looked up before, unless the dictionary has changed since. */
    fun cachedLabelWords(): Map<String, List<Long>?> {
        if (labels.getLong(LABELS_VERSION, -1) != dictionary.version) return emptyMap()
        return labels.all.filterKeys { it != LABELS_VERSION }.mapValues { (_, v) ->
            (v as? String)?.takeIf { it.isNotEmpty() }?.split(',')?.map { it.toLong() }
        }
    }

    /** Look up the words in [found] labels, and remember them. */
    fun labelWords(found: Collection<UiLabel>): Map<String, List<Long>?> {
        val words = found.associate { it.key to UiLabels.words(it, reader, dictionary) }
        val edit = labels.edit()
        if (labels.getLong(LABELS_VERSION, -1) != dictionary.version) edit.clear().putLong(LABELS_VERSION, dictionary.version)
        for ((key, ids) in words) edit.putString(key, ids?.joinToString(",").orEmpty())
        edit.apply()
        return words
    }

    /** Words mastered well enough for the app to use them: the mastery where furigana turns off. */
    fun masteredWords(): Set<Long> = store.wordsAtMastery(MasteryRules.OFF_AT)

    // ------------------------------------------------------------ stories

    /** How the story reader sets text, chosen in its own settings menu. */
    fun readerStyle(): ReaderStyle {
        val default = ReaderStyle()
        return ReaderStyle(
            size = store.getValue("story_text_size")?.toIntOrNull()?.takeIf { it in ReaderStyle.SIZES } ?: default.size,
            font = ReaderFont.entries.firstOrNull { it.name == store.getValue("story_font") } ?: default.font,
            vertical = store.getValue("story_vertical") == "1",
        )
    }

    fun setReaderStyle(style: ReaderStyle) {
        store.setValues(
            mapOf(
                "story_text_size" to style.size.toString(),
                "story_font" to style.font.name,
                "story_vertical" to if (style.vertical) "1" else "0",
            ),
        )
    }

    // ------------------------------------------------------------ story server sign-in

    private fun storyToken(): String? = secrets.getString("story_token", null)

    /** The Google account signed in to write stories, or null. */
    fun storyAccount(): String? = storyToken()?.let { secrets.getString("story_email", null).orEmpty() }

    /** Sign in to the story server with a Google ID token from Credential Manager. */
    fun signInToStories(googleIdToken: String) {
        val session = storyServer.signIn(googleIdToken)
        secrets.edit().putString("story_token", session.token).putString("story_email", session.email).apply()
    }

    fun signOutOfStories() {
        val token = storyToken() ?: return
        secrets.edit().remove("story_token").remove("story_email").apply()
        // Ending the session on the server is tidy, not essential; forgetting it here is what signs you out.
        runCatching { storyServer.signOut(token) }
    }

    /** How much of today's story allowance is spent, or null if it can't be fetched. */
    fun storyUsage(): StoryUsage? {
        val token = storyToken() ?: return null
        return try {
            storyServer.usage(token)
        } catch (e: ClaudeException) {
            if (e.signInNeeded) forgetStorySession()
            null
        }
    }

    private fun forgetStorySession() {
        secrets.edit().remove("story_token").remove("story_email").apply()
    }

    fun stories(): List<SavedStory> = store.stories()

    fun story(id: Long): SavedStory? = store.story(id)

    fun deleteStory(id: Long) = store.deleteStory(id)

    /** Words you've started, or suspended to leave alone, count as known while reading. */
    private fun known(statuses: Map<Long, WordStatus>): (Long) -> Boolean = { id ->
        val s = statuses[id]
        s != null && (s.state != CardState.NEW || s.suspended)
    }

    fun knownWordCount(): Int = wordStatuses().values.count { it.state != CardState.NEW }

    /** What to ask for: your started words, the shakiest of them to practise, and the next new words. */
    fun storyRequest(prompt: String, length: StoryLength, difficulty: StoryDifficulty): StoryRequest {
        val statuses = store.wordStatuses()
        val started = statuses.filterValues { it.state != CardState.NEW }
        val known = dictionary.vocab(started.keys)
        val shaky = started.filterValues { !it.suspended && it.mastery < MasteryRules.HIDE_AT }.entries
            .sortedBy { it.value.mastery }.take(PRACTICE_POOL).map { it.key }.shuffled().take(PRACTICE)
        val budget = newWordBudget(length, difficulty)
        val queued = store.cards(
            "state = ? AND direction = ? AND suspended = 0",
            arrayOf(CardState.NEW.value.toString(), Direction.RECOGNITION.value.toString()),
            "sort_key, id",
        ).map { it.wordId }
        val introduceCount = maxOf(1, budget / 2)
        val next = (queued + dictionary.nextWords(statuses.keys, introduceCount, startLevel(), canRead()).map { it.first })
            .distinct().take(introduceCount)
        val frontier = dictionary.nextWords(statuses.keys, 1, startLevel()).firstOrNull()?.first
        return StoryRequest(
            prompt = prompt,
            length = length,
            difficulty = difficulty,
            grammarLevel = frontier?.let { dictionary.level(it) }?.takeIf { it in 1..5 } ?: 1,
            known = known,
            practice = dictionary.vocab(shaky),
            introduce = dictionary.vocab(next).sortedBy { next.indexOf(it.wordId) },
        )
    }

    /**
     * Have Claude write a story and save it. Its new words aren't queued;
     * the reader adds the ones the learner picks. Returns the story's id.
     */
    fun writeStory(request: StoryRequest, now: Long, sequelTo: SavedStory? = null, progress: (String) -> Unit): Long {
        val token = storyToken() ?: throw ClaudeException("Sign in with Google to write stories.", signInNeeded = true)
        val writer = StoryWriter(storyServer.conversation(token), reader)
        val written = try {
            writer.write(request, known(store.wordStatuses()), progress)
        } catch (e: ClaudeException) {
            if (e.signInNeeded) forgetStorySession()
            throw e
        }
        // Every part of a series keeps the first part's title.
        val title = sequelTo?.title ?: written.story.title
        progress("Translating")
        val sentences = sentenceTexts(listOf(reader.read(title).flatten()) + reader.read(written.story.body))
        // The story is worth keeping without its translations if they fail.
        val translations = try {
            StoryTranslator(storyServer.conversation(token, effort = "low")).translate(sentences)
        } catch (e: IOException) {
            null
        }
        progress("Saving")
        val story = SavedStory(
            id = 0,
            createdAt = now,
            title = title,
            body = written.story.body,
            prompt = request.prompt.trim(),
            length = request.length.name,
            difficulty = request.difficulty.name,
            translations = translations.orEmpty(),
        )
        return if (sequelTo == null) store.insertStory(story) else store.insertSequel(sequelTo, story)
    }

    /** What to ask for in a sequel to [story]: the same settings as it, and every part so far. */
    fun sequelRequest(story: SavedStory): StoryRequest {
        val parts = generateSequence(story) { s -> s.previousId?.let { store.story(it) } }.toList().asReversed()
        return storyRequest(
            story.prompt,
            StoryLength.entries.firstOrNull { it.name == story.length } ?: StoryLength.SHORT,
            StoryDifficulty.entries.firstOrNull { it.name == story.difficulty } ?: StoryDifficulty.NORMAL,
        ).copy(previous = parts.map { Story(it.title, it.body) })
    }

    fun markStoryRead(id: Long, now: Long, millis: Long) = store.markStoryRead(id, now, millis)

    fun readStory(id: Long): StoryReading? {
        val story = store.story(id) ?: return null
        // A bundled chapter's title is English, and a grammar page's names its pattern; neither is read.
        val title = if (story.pathId != null) emptyList() else reader.read(story.title).flatten()
        val paragraphs = reader.read(story.body)
        val ids = (title + paragraphs.flatten()).mapNotNull { it.wordId }.toSet()
        val statuses = store.wordStatuses()
        return StoryReading(
            story = story,
            title = title,
            paragraphs = paragraphs,
            words = ids.mapNotNull { dictionary.word(it) }.associateBy { it.id },
            statuses = statuses,
            coverage = coverage(paragraphs, known(statuses)),
            next = store.nextPart(id),
            lesson = story.pathId?.let { id -> catalog.grammar.firstOrNull { it.page.id == id }?.page },
        )
    }

    // ------------------------------------------------------------ story path

    /** The stories and grammar topics that come with the app. */
    private val catalog by lazy {
        StoryPath.parse(appContext.assets.open("stories/path.json").bufferedReader().use { it.readText() })
    }

    /** What each chapter asks of you, by its id. */
    private val needs: Map<String, StepNeeds> by lazy {
        catalog.chapters.associate { (_, c) -> c.story.id to StoryPath.needs(reader, c.story) }
    }
    private var pathSynced = false

    /** Saves the chapters and grammar pages on first use, so they can be opened like any story. */
    private fun savedPath(now: Long): Map<String, SavedStory> {
        if (!pathSynced) {
            val stories = catalog.chapters.map { it.second.story } + catalog.grammar.map { it.page }
            store.syncPathStories(stories.map { p -> SavedStory(0, now, p.title, p.body, "", "", "", p.translations, pathId = p.id) })
            pathSynced = true
        }
        return store.pathStories()
    }

    /** What's left of a step to learn: words and kana you haven't started, and grammar you haven't seen. */
    private fun gaps(): (StepNeeds) -> StepGap {
        val isKnown = known(store.wordStatuses())
        val readable = kanaKnown()
        val kana = store.kanaStatuses()
        val knowsKana = { unit: KanaUnit -> readable.covers(unit.script) || kana[unit.id]?.let { it.state != CardState.NEW || it.suspended } == true }
        val grammar = seenGrammar()
        return { needs -> StoryPath.gap(needs, isKnown, knowsKana, grammar) }
    }

    /** Every story that comes with the app, in order, with each chapter and what you haven't learned of it. */
    fun storyPath(now: Long): List<SeriesEntry> {
        val saved = savedPath(now)
        val gap = gaps()
        val seen = seenGrammar()
        return catalog.series.map { series ->
            SeriesEntry(series, series.chapters.mapNotNull { c ->
                val story = saved[c.story.id] ?: return@mapNotNull null
                val topic = catalog.topic(c.topic)?.takeIf { !seen.containsAll(it.points) }
                ChapterEntry(series, c, story, gap(needs.getValue(c.story.id)), topic)
            })
        }
    }

    /** The story a saved chapter belongs to, and the chapter. */
    fun chapterOf(story: SavedStory, now: Long): ChapterEntry? =
        story.pathId?.let { id -> storyPath(now).flatMap { it.chapters }.firstOrNull { it.chapter.story.id == id } }

    /** The kana and words (with dictionary entries) in a chapter you haven't learned yet, and its new grammar, or null off the path. */
    fun stepToLearn(story: SavedStory, now: Long): StepToLearn? {
        val entry = chapterOf(story, now) ?: return null
        return StepToLearn(entry.gap.kana, entry.gap.words.mapNotNull { w -> w.wordId?.let { dictionary.word(it) } }, entry.newTopic)
    }

    /** Why [chapterId] is locked: what it's missing, and where to pick that up. */
    fun lockInfo(chapterId: String, now: Long): LockInfo? {
        val path = storyPath(now)
        val chapters = path.flatMap { it.chapters }
        val entry = chapters.firstOrNull { it.chapter.story.id == chapterId } ?: return null
        val missing = entry.gap.words.mapNotNull { it.wordId }.toSet()
        val grammar = entry.gap.grammar.mapNotNull { catalog.topicOf(it) }.distinct().map { topic ->
            topic to chapters.filter { it.chapter.topic == topic.id }
        }
        val sources = chapters
            .filter { it.ready && !it.read && it !== entry }
            .map { c -> c to needs.getValue(c.chapter.story.id).words.count { it.wordId in missing } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(3)
        val earlier = entry.series.chapters.takeWhile { it !== entry.chapter }
            .filter { c -> chapters.firstOrNull { it.chapter === c }?.read == false }
            .flatMap { c -> needs.getValue(c.story.id).words.mapNotNull { it.wordId } }
            .toSet()
        return LockInfo(
            entry = entry,
            words = entry.gap.words.mapNotNull { w -> w.wordId?.let { dictionary.word(it) } },
            kana = entry.gap.kana,
            grammar = grammar,
            sources = sources,
            earlierCover = missing.isNotEmpty() && earlier.containsAll(missing),
        )
    }

    /** Every grammar topic, newest you've met first, then the ones you haven't, in the order they come. */
    fun grammarRows(now: Long): List<GrammarRow> {
        val saved = savedPath(now)
        val seen = seenGrammar()
        val chapters = storyPath(now).flatMap { it.chapters }
        val rows = catalog.grammar.mapNotNull { topic ->
            val page = saved[topic.page.id] ?: return@mapNotNull null
            val taughtIn = chapters.filter { it.chapter.topic == topic.id }.let { c -> c.firstOrNull { it.read } ?: c.firstOrNull() }
            GrammarRow(topic, seen.containsAll(topic.points), taughtIn, page.id)
        }
        val (met, unmet) = rows.partition { it.seen }
        return met.reversed() + unmet
    }

    /** The saved story holding [topic]'s page. */
    fun grammarPageId(topic: GrammarTopic, now: Long): Long? = savedPath(now)[topic.page.id]?.id

    /** Grammar points you've been taught, or said you knew. */
    fun seenGrammar(): Set<String> =
        store.getValue("grammar_seen")?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    fun markGrammarSeen(ids: Collection<String>) {
        store.setValues(mapOf("grammar_seen" to (seenGrammar() + ids).sorted().joinToString(",")))
    }

    /**
     * Finishing a path story: each word due for review that you read without
     * looking it up counts as a lighter review than a Right answer ([Reviewer.read]).
     */
    fun readWords(story: SavedStory, lookedUp: Set<Long>, now: Long) {
        val reviewer = Reviewer(Fsrs(), clock())
        val ids = StoryPath.words(reader, story.body).mapNotNull { it.wordId }
        for (id in ids.distinct() - lookedUp) {
            val card = recognitionCard(id) ?: continue
            if (card.state != CardState.REVIEW || card.dueAt > now || store.isSuspended(id)) continue
            val before = store.progress(id)
            save(card, before, reviewer.read(card, before, now, Random.Default), now)
        }
    }

    /** Mastery of each of [ids] you've started. */
    fun masteries(ids: Collection<Long>): Map<Long, Int> {
        val statuses = store.wordStatuses()
        return ids.mapNotNull { id -> statuses[id]?.takeIf { it.state != CardState.NEW }?.let { id to it.mastery } }.toMap()
    }

    /** Start [kana] and [wordIds] as new cards, to learn before reading: kana first, then words, in order. */
    fun cardsToLearn(wordIds: List<Long>, kana: List<KanaUnit>, now: Long): List<Card> {
        store.transaction { db ->
            kana.forEachIndexed { i, unit ->
                store.setSuspended(db, unit.id, false)
                if (mainCard(unit.id, Direction.KANA) == null) store.insertNewCard(db, unit.id, Direction.KANA, i.toLong())
            }
        }
        for (id in wordIds) learnNext(id, now)
        val kanaCards = kana.mapNotNull { mainCard(it.id, Direction.KANA) }
        return (kanaCards + wordIds.mapNotNull { recognitionCard(it) }).filter { it.state == CardState.NEW }
    }

    // ------------------------------------------------------------ kana

    /** Which kana you read without studying them here. Installs from before kana lessons read both. */
    fun kanaKnown(): KanaKnown = KanaKnown.of(store.getValue("kana_known"))

    /**
     * Change which kana you read already. Kana and words queued but not yet
     * shown are dropped, so both queues are rebuilt for what you can read.
     * Kana cards you've started for a script you now read are set aside, so
     * they stop coming up for review, and come back if you change your mind.
     */
    fun setKanaKnown(known: KanaKnown) {
        store.setValues(mapOf("kana_known" to known.value))
        val started = store.wordIdsWithCards(Direction.KANA)
        store.transaction { db ->
            store.deleteUnstartedKana(db)
            store.deleteQueuedNewCards(db)
            for (id in started) {
                val unit = KanaCourse.unit(id) ?: continue
                store.setSuspended(db, id, known.covers(unit.script))
            }
        }
    }

    /**
     * Whether a word's kana spelling can be read with the kana you know, or
     * null when you read all kana.
     */
    private fun canRead(): ((String) -> Boolean)? {
        val known = kanaKnown()
        if (known == KanaKnown.BOTH) return null
        val learned = store.kanaInReview()
        return { kana -> KanaCourse.canRead(kana, known, learned) }
    }

    // ------------------------------------------------------------ first run

    /** The JLPT level new words start from: 5 for N5 down to 1 for N1. */
    fun startLevel(): Int = store.getValue("start_level")?.toIntOrNull()?.coerceIn(1, 5) ?: 5

    /** Start new words from another level. Words already queued in the old order are re-queued. */
    fun setStartLevel(level: Int) {
        store.setValues(mapOf("start_level" to level.coerceIn(1, 5).toString()))
        store.transaction { db -> store.deleteQueuedNewCards(db) }
    }

    /**
     * Mark the words in levels easier than [level] that you haven't started
     * as known at [mastery], as first-run setup does. Words you've started keep their mastery.
     */
    fun markEasierLevelsKnown(level: Int, mastery: Int, now: Long) {
        val started = store.wordStatuses().filterValues { it.state != CardState.NEW || it.suspended }.keys
        val words = dictionary.wordsBelow(level).filter { it !in started }
        importMastery(words.associateWith { mastery }, now, unlockProduction = false)
    }

    /** True on a fresh install, before you've picked a starting level. Existing progress counts as set up. */
    fun needsSetup(): Boolean {
        if (store.getValue("setup_done") == "1") return false
        val started = store.count("SELECT COUNT(*) FROM card WHERE state != ?", CardState.NEW.value) +
            store.count("SELECT COUNT(*) FROM review_log")
        if (started > 0) {
            store.setValues(mapOf("setup_done" to "1"))
            return false
        }
        return true
    }

    fun levelCounts(): Map<Int, Int> = dictionary.levelCounts()

    /**
     * Finish the first run: start new words at [startLevel], and if
     * [lowerMastery] is set, mark every word of the easier levels known at it.
     */
    fun finishSetup(startLevel: Int, lowerMastery: Int?, kana: KanaKnown, reminderMinute: Int?, now: Long) {
        setKanaKnown(kana)
        if (reminderMinute != null) saveReminder(Reminder(enabled = true, minuteOfDay = reminderMinute))
        setStartLevel(startLevel)
        if (lowerMastery != null && lowerMastery > 0) {
            val words = dictionary.wordsBelow(startLevel)
            importMastery(words.associateWith { lowerMastery }, now, unlockProduction = false)
        }
        store.setValues(mapOf("setup_done" to "1"))
    }

    // ------------------------------------------------------------ backup

    /** Write a copy of all your progress, settings and stories. The story sign-in isn't included. */
    fun exportBackup(out: OutputStream) {
        store.checkpoint()
        appContext.getDatabasePath(ProgressStore.NAME).inputStream().use { it.copyTo(out) }
    }

    /** Replace all progress with a backup. The file is checked before anything is replaced. */
    fun importBackup(input: InputStream) {
        val incoming = File(appContext.cacheDir, "import.db")
        try {
            incoming.outputStream().use { input.copyTo(it) }
            checkBackup(incoming)
            store.close()
            val target = appContext.getDatabasePath(ProgressStore.NAME)
            for (suffix in listOf("-wal", "-shm", "-journal")) File(target.path + suffix).delete()
            incoming.copyTo(target, overwrite = true)
        } finally {
            incoming.delete()
        }
        // The next query reopens the database, upgrading an older backup's schema.
        store.count("SELECT COUNT(*) FROM card")
    }

    private fun checkBackup(file: File) {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw BackupException("That file isn't a Jiwa Jiwa backup.")
        }
        db.use {
            val tables = it.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            if (!tables.containsAll(listOf("card", "word_progress", "review_log", "kv"))) {
                throw BackupException("That file isn't a Jiwa Jiwa backup.")
            }
            if (it.version > ProgressStore.VERSION) {
                throw BackupException("That backup is from a newer version of Jiwa Jiwa. Update the app first.")
            }
        }
    }

    // ------------------------------------------------------------ reset

    /**
     * Erase everything you've done here: cards, mastery, history, settings
     * and stories, and sign out of stories. The next query recreates an empty
     * database, so the app starts again at first-run setup.
     */
    fun resetAll() {
        store.close()
        val target = appContext.getDatabasePath(ProgressStore.NAME)
        SQLiteDatabase.deleteDatabase(target)
        signOutOfStories()
        secrets.edit().clear().commit()
        store.count("SELECT COUNT(*) FROM card")
    }

    // ------------------------------------------------------------ reminder

    fun reminder(): Reminder = Reminder(
        enabled = store.getValue("reminder_enabled") == "1",
        minuteOfDay = store.getValue("reminder_minute")?.toIntOrNull()?.coerceIn(0, 24 * 60 - 1),
    )

    fun saveReminder(r: Reminder) {
        store.setValues(
            mapOf(
                "reminder_enabled" to if (r.enabled) "1" else "0",
            ) + (r.minuteOfDay?.let { mapOf("reminder_minute" to it.toString()) } ?: emptyMap()),
        )
    }

    // ------------------------------------------------------------ stats

    fun stats(now: Long): Stats {
        val settings = settings()
        val clock = clock(settings)
        val today = clock.dayOf(now)
        val forecast = IntArray(FORECAST_DAYS)
        for (dueAt in store.reviewDueTimes(clock.startOf(today + FORECAST_DAYS))) {
            forecast[(clock.dayOf(dueAt) - today).toInt().coerceIn(0, FORECAST_DAYS - 1)]++
        }
        forecast[0] += store.count(
            "SELECT COUNT(*) FROM card WHERE state IN (?, ?) AND suspended = 0",
            CardState.LEARNING.value,
            CardState.RELEARNING.value,
        )
        val perDay = store.answersAndRightPerDay(today - (HISTORY_DAYS - 1))
        val answers = (0 until HISTORY_DAYS).map { perDay[today - (HISTORY_DAYS - 1) + it] ?: (0 to 0) }

        val levelOf = dictionary.levels()
        val counts = dictionary.levelCounts()
        val statuses = store.wordStatuses()
        val levels = (5 downTo 1).map { level ->
            val mine = statuses.filter { (id, st) -> levelOf[id] == level && st.state != CardState.NEW && !st.suspended }.values
            LevelStats(
                label = "N$level",
                total = counts[level] ?: 0,
                started = mine.size,
                known = mine.count { it.mastery >= MasteryRules.HIDE_AT },
                mastered = mine.count { it.mastery >= MasteryRules.OFF_AT },
            )
        }
        val kanaStatuses = store.kanaStatuses()
        val toTeach = KanaCourse.toTeach(kanaKnown())
        val kana = listOf(Script.HIRAGANA to "あ", Script.KATAKANA to "ア").mapNotNull { (script, label) ->
            val units = toTeach.filter { it.script == script }
            if (units.isEmpty()) return@mapNotNull null
            val mine = units.mapNotNull { kanaStatuses[it.id] }.filter { it.state != CardState.NEW && !it.suspended }
            LevelStats(
                label = label,
                total = units.size,
                started = mine.size,
                known = mine.count { it.mastery >= MasteryRules.HIDE_AT },
                mastered = mine.count { it.mastery >= MasteryRules.OFF_AT },
            )
        }
        return Stats(
            today = today,
            forecast = forecast.toList(),
            answers = answers,
            recall30 = store.reviewRecall(today - (HISTORY_DAYS - 1)),
            recallAll = store.reviewRecall(Long.MIN_VALUE),
            levels = levels,
            kana = kana,
            wordsStarted = statuses.values.count { it.state != CardState.NEW },
            totalAnswers = store.count("SELECT COUNT(*) FROM review_log"),
            studyDays = store.studyDays().size,
        )
    }

    // ------------------------------------------------------------ summary

    fun summary(now: Long): Summary {
        val settings = settings()
        val clock = clock(settings)
        val today = clock.dayOf(now)
        val limits = limits(settings, clock, now)
        val reviewsDue = store.count(
            "SELECT COUNT(*) FROM card WHERE state = ? AND due_at < ? AND suspended = 0",
            CardState.REVIEW.value,
            limits.reviewDueBefore,
        )
        val started = store.count("SELECT COUNT(*) FROM card WHERE direction = ?", Direction.RECOGNITION.value)
        val total = dictionary.wordCount()
        val known = kanaKnown()
        // New words come from the story path, so only unlocked production cards wait here.
        val newWaiting = store.count(
            "SELECT COUNT(*) FROM card WHERE state = ? AND direction = ? AND suspended = 0",
            CardState.NEW.value,
            Direction.PRODUCTION.value,
        )
        val toTeach = KanaCourse.toTeach(known)
        val learned = store.kanaInReview()
        return Summary(
            newCards = minOf(limits.newCards, newWaiting),
            learning = store.count(
                "SELECT COUNT(*) FROM card WHERE state IN (?, ?) AND suspended = 0",
                CardState.LEARNING.value,
                CardState.RELEARNING.value,
            ),
            reviews = minOf(limits.reviews, reviewsDue),
            streak = streak(store.studyDays(), today),
            daysAhead = ahead().daysAhead,
            reviewedToday = store.count("SELECT COUNT(*) FROM review_log WHERE day = ?", today),
            correctToday = store.count("SELECT COUNT(*) FROM review_log WHERE day = ? AND correct = 1", today),
            wordsStarted = store.count(
                "SELECT COUNT(*) FROM card WHERE direction = ? AND state != ?",
                Direction.RECOGNITION.value,
                CardState.NEW.value,
            ).coerceAtMost(started),
            wordsTotal = total,
            today = today,
            answersPerDay = store.answersPerDay(today - 370),
            kanaLearned = toTeach.count { it.id in learned },
            kanaTotal = toTeach.size,
        )
    }

    companion object {
        /** Started words before Claude writes stories: below this, the bundled stories cover you. */
        const val WRITING_OPENS_AT = 300

        private const val PRODUCTION_FIRST = 10_000_000_000L
        private const val FORECAST_DAYS = 30
        private const val HISTORY_DAYS = 30

        /** Words you haven't started to list on a kanji's page. */
        private const val MORE_WORDS = 12
        private const val LEARN_NEXT_FIRST = 20_000_000_000L
        private const val PRACTICE_POOL = 20
        private const val PRACTICE = 8

        /** Never a label key, which is Japanese text. */
        private const val LABELS_VERSION = "@dictionary"

        /** Consecutive study days ending today, or yesterday if today has no reviews yet. */
        fun streak(daysNewestFirst: List<Long>, today: Long): Int {
            if (daysNewestFirst.isEmpty()) return 0
            var expected = if (daysNewestFirst.first() == today) today else today - 1
            var count = 0
            for (day in daysNewestFirst) {
                if (day != expected) break
                count++
                expected--
            }
            return count
        }
    }
}
