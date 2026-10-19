package ca.danielpower.jstudy.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import ca.danielpower.jstudy.core.Answer
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.DailyLimits
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.Fsrs
import ca.danielpower.jstudy.core.ImportPlan
import ca.danielpower.jstudy.core.Kanji
import ca.danielpower.jstudy.core.Kanjis
import ca.danielpower.jstudy.core.LocalWord
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.ReadLevel
import ca.danielpower.jstudy.core.RenshuuImport
import ca.danielpower.jstudy.core.Speech
import ca.danielpower.jstudy.core.RenshuuTerm
import ca.danielpower.jstudy.core.Reviewer
import ca.danielpower.jstudy.core.StartSource
import ca.danielpower.jstudy.core.StudyAhead
import ca.danielpower.jstudy.core.StudyClock
import ca.danielpower.jstudy.core.StudySession
import ca.danielpower.jstudy.core.StudySettings
import ca.danielpower.jstudy.core.WordProgress
import ca.danielpower.jstudy.core.Coverage
import ca.danielpower.jstudy.core.ReadingWord
import ca.danielpower.jstudy.core.CastMember
import ca.danielpower.jstudy.core.CastVoice
import ca.danielpower.jstudy.core.Narration
import ca.danielpower.jstudy.core.NarrationLine
import ca.danielpower.jstudy.core.Recording
import ca.danielpower.jstudy.core.Recordings
import ca.danielpower.jstudy.core.Chapter
import ca.danielpower.jstudy.core.GrammarTopic
import ca.danielpower.jstudy.core.Candidate
import ca.danielpower.jstudy.core.NextUp
import ca.danielpower.jstudy.core.Pace
import ca.danielpower.jstudy.core.Recent
import ca.danielpower.jstudy.core.Streak
import ca.danielpower.jstudy.core.Suggest
import ca.danielpower.jstudy.core.WordDay
import ca.danielpower.jstudy.core.WordHistory
import ca.danielpower.jstudy.core.PathSeries
import ca.danielpower.jstudy.core.PathStory
import ca.danielpower.jstudy.core.StepGap
import ca.danielpower.jstudy.core.StepNeeds
import ca.danielpower.jstudy.core.StoryPath
import ca.danielpower.jstudy.core.StoryText
import ca.danielpower.jstudy.core.coverage
import ca.danielpower.jstudy.core.sentenceRanges
import kotlin.random.Random

data class Summary(
    val newCards: Int,
    val learning: Int,
    val reviews: Int,
    val streak: Int,
    val daysAhead: Int,
    val reviewedToday: Int,
    val correctToday: Int,
    val today: Long,
    /** Cards answered per study day over the last year. */
    val answersPerDay: Map<Long, Int>,
    /** Study days a chapter was finished on. */
    val readDays: Set<Long>,
    /** Reviews due on each of the next week's days, tomorrow first. */
    val comingUp: List<Int>,
) {
    val anythingDue get() = newCards + learning + reviews > 0
}

/** Everything Home shows: what to do next, and how far you've come. */
data class Dashboard(
    val today: Long,
    val next: NextUp<ChapterEntry>,
    val pace: Pace,
    /** Study days with a review answered or a chapter finished. */
    val activeDays: Set<Long>,
    val streak: Int,
    /** Words seen, known and mastered at the end of each day, up to today. */
    val words: List<WordDay>,
    /** Words started and grammar learned in the last week, newest first ([Recent.pick]). */
    val recent: List<RecentItem>,
)

/** A word or grammar topic on Home's Recent row. */
sealed interface RecentItem {
    data class NewWord(val word: Word) : RecentItem
    data class NewGrammar(val topic: GrammarTopic, val pageId: Long) : RecentItem
}

/** Where you left a chapter: its page and how many it has, in the reader style the pages were laid out with. */
data class ReaderPlace(val storyId: Long, val page: Int, val pages: Int, val style: String)

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
    /** Sentences with it from chapters you've read, the last read first. */
    val stories: List<StoryUse> = emptyList(),
)

/** A sentence from a chapter you've read, for a word's page: where it is, its words, and how to hear it. */
data class StoryUse(
    val series: PathSeries,
    val chapter: Chapter,
    val storyId: Long,
    /** The word's place in the chapter, for the reader to open at. */
    val paragraph: Int,
    val index: Int,
    val words: List<ReadingWord>,
    /** Which of [words] are the word. */
    val marked: Set<Int>,
    val speaker: String?,
    val translation: String?,
    /** The sentence as it's read aloud, and its recording if it has one. */
    val line: NarrationLine?,
    val clip: Recording?,
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
    val logId: Long,
    val productionCardId: Long?,
)

/** A saved story split into words, with what's needed to show each one. Its English title isn't read. */
data class StoryReading(
    val story: SavedStory,
    val paragraphs: List<List<ReadingWord>>,
    /** Who says each paragraph, when it's a line of dialogue. */
    val speakers: List<String?> = paragraphs.map { null },
    /** Dictionary entries for the story's words. */
    val words: Map<Long, Word>,
    val statuses: Map<Long, WordStatus>,
    val coverage: Coverage,
    /** For a grammar topic's page: its English notes between the examples. */
    val lesson: PathStory? = null,
    /** A chapter's English notes, by the paragraph whose end has their marker. */
    val notes: Map<Int, String> = emptyMap(),
    /** The saved page of the grammar topic each grammar point belongs to, by point id. */
    val lessons: Map<String, Long> = emptyMap(),
    /** Each line of [narration]'s recording, or null for the phone to read it. */
    val clips: List<Recording?> = emptyList(),
) {
    /** The text as sentences to read aloud. */
    val narration: List<NarrationLine> by lazy { Narration.lines(paragraphs, speakers) }

    /** The word at [i] in paragraph [p]. */
    fun word(p: Int, i: Int): ReadingWord? = paragraphs.getOrNull(p)?.getOrNull(i)

    /** Each paragraph's sentences; the translations follow this order. */
    private val sentences = paragraphs.map(::sentenceRanges)

    /** The sentence holding word [i] of paragraph [p]. */
    fun sentence(p: Int, i: Int): StorySentence? {
        val j = sentences.getOrNull(p)?.indexOfFirst { i in it }?.takeIf { it >= 0 } ?: return null
        val translations = story.translations.takeIf { it.size == sentences.sumOf { s -> s.size } }
        return StorySentence(sentences[p][j], translations?.get(sentences.take(p).sumOf { it.size } + j))
    }
}

/** What a chapter brings that you haven't learned: how many new words, and the grammar topic it brings in. */
data class StepToLearn(val newWords: Int, val topic: GrammarTopic?)

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

    /** Open, with no new words or grammar: something new to read with nothing to learn. */
    val nothingNew get() = ready && gap.words.isEmpty() && newTopic == null
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
    /** Grammar it uses that you haven't seen, with the chapters that teach it. */
    val grammar: List<Pair<GrammarTopic, List<ChapterEntry>>>,
    /** Open, unread chapters that have some of the new words, most first, with how many. */
    val sources: List<Pair<ChapterEntry, Int>>,
    /** Reading this story's earlier chapters gives you every one of the new words. */
    val earlierCover: Boolean,
)

/** A grammar topic for the Dictionary tab's Grammar list: whether you've met it, where it's taught, and its page. */
data class GrammarRow(val topic: GrammarTopic, val seen: Boolean, val taughtIn: ChapterEntry?, val pageId: Long)

/** The story reader's typefaces: two sans serifs and a serif, all bundled under the OFL. */
enum class ReaderFont { SANS, SERIF, ROUNDED }

/** How stories are set: text size in sp, typeface, and whether columns run top to bottom. */
data class ReaderStyle(
    val size: Int = 26,
    val font: ReaderFont = ReaderFont.SANS,
    /** Set top to bottom, right to left, as Japanese books are. */
    val vertical: Boolean = false,
) {
    companion object {
        val SIZES = listOf(22, 26, 30, 34)
    }
}

/** How stories are read aloud: the speed. */
data class SpeechSettings(val rate: Float = 1f) {
    companion object {
        val RATES = listOf(0.6f, 0.8f, 1f, 1.2f)
    }
}

/** A sentence of a story: its words in their paragraph, and its English if the story has translations. */
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
    val wordsStarted: Int,
    val totalAnswers: Int,
    val studyDays: Int,
)

/** Words of one JLPT level by how far along they are. Each count includes the ones after it. */
data class LevelStats(val label: String, val total: Int, val started: Int, val known: Int, val mastered: Int)

/** The daily reminder. There's no default time: [minuteOfDay] is null until you pick one. */
data class Reminder(val enabled: Boolean, val minuteOfDay: Int?) {
    val time: Int? get() = minuteOfDay?.takeIf { enabled }
}

class BackupException(message: String) : Exception(message)

/**
 * Your progress and the dictionary. There's one per process ([get]), so the
 * app and the reminder receivers never copy the dictionary at the same time
 * or open the progress database twice.
 */
class Repository private constructor(context: Context) {
    private val appContext = context.applicationContext
    val dictionary = Dictionary(context)
    private val store = ProgressStore(context)

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
            // Production cards fill what today's new words leave of the daily limit.
            newIntroducedToday = store.count("SELECT COUNT(*) FROM card WHERE introduced_day = ? AND start_source = 0", today),
            reviewsDoneToday = store.count(
                "SELECT COUNT(*) FROM review_log WHERE day = ? AND state_before = ? AND kind = 0",
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
            "state = ? AND $REVIEW_DUE AND suspended = 0",
            arrayOf(CardState.REVIEW.value.toString(), *reviewDueArgs(limits).map(Long::toString).toTypedArray()),
            "due_at, id",
            limits.reviews,
        )
        // New words come from the story path, not reviews; only unlocked production cards are new here.
        val newWords = store.cards(
            "state = ? AND direction = ? AND suspended = 0",
            arrayOf(CardState.NEW.value.toString(), Direction.PRODUCTION.value.toString()),
            "sort_key, id",
            limits.newCards,
        )
        return StudySession(reviews, newWords, learning, random = Random.Default)
    }

    /**
     * The new words you've started today, against the daily limit,
     * and the cards a review session would show now. Words marked known,
     * imported or filled in by level don't count against the limit.
     */
    fun pace(now: Long): Pace {
        val settings = settings()
        val clock = clock(settings)
        val today = clock.dayOf(now)
        return Pace(
            newToday = store.count(
                "SELECT COUNT(*) FROM card WHERE introduced_day = ? AND start_source = 0 AND direction != ?",
                today,
                Direction.PRODUCTION.value,
            ),
            limit = settings.newPerDay,
            reviewsDue = queue(settings, clock, now).total,
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
                store.insertNewCard(db, card.wordId, Direction.PRODUCTION, now / 1000)
                    .takeIf { it > 0 }
            } else {
                null
            }
            answer to Undo(card, before, logId, production)
        }

    /** First sight of a word you don't know yet: start learning it, ungraded. */
    private fun introduce(card: Card, now: Long) {
        val reviewer = Reviewer(Fsrs(), clock(settings()))
        store.transaction { db -> store.updateCard(db, reviewer.introduce(card, now)) }
    }

    /** Mark a word you already know at [mastery]. */
    private fun markKnown(card: Card, mastery: Int, now: Long) {
        val settings = settings()
        val reviewer = Reviewer(Fsrs(), clock(settings))
        val before = store.progress(card.wordId)
        val known = reviewer.markKnown(card, before, mastery, now, Random.Default)
        store.transaction { db ->
            store.updateCard(db, known.card)
            store.saveProgress(db, known.progress)
            store.insertLog(db, known.log)
            if (known.unlockedProduction) {
                store.insertNewCard(db, card.wordId, Direction.PRODUCTION, now / 1000)
            }
        }
    }

    fun undo(undo: Undo) {
        store.transaction { db ->
            store.updateCard(db, undo.cardBefore)
            store.saveProgress(db, undo.progressBefore)
            store.deleteLog(db, undo.logId)
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
            stories = storyUses(wordId),
        )
    }

    /** Up to [limit] sentences with [wordId], one from each chapter you've read that has it, the last read first. */
    private fun storyUses(wordId: Long, limit: Int = 3): List<StoryUse> {
        val saved = savedPath()
        return catalog.chapters
            .mapNotNull { (series, chapter) ->
                val story = saved[chapter.story.id]?.takeIf { it.readAt != null } ?: return@mapNotNull null
                val has = needs[chapter.story.id]?.words?.any { it.wordId == wordId } == true
                if (has) Triple(series, chapter, story) else null
            }
            .sortedByDescending { it.third.readAt }
            .asSequence()
            .mapNotNull { (series, chapter, story) -> storyUse(series, chapter, story, wordId) }
            .take(limit)
            .toList()
    }

    /** The first sentence of [story] with [wordId]. */
    private fun storyUse(series: PathSeries, chapter: Chapter, story: SavedStory, wordId: Long): StoryUse? {
        val paragraphs = paragraphs(story)
        val p = paragraphs.indexOfFirst { words -> words.any { it.wordId == wordId } }.takeIf { it >= 0 } ?: return null
        val i = paragraphs[p].indexOfFirst { it.wordId == wordId }
        // Only its text is needed, not its words' entries or progress.
        val reading = StoryReading(story, paragraphs, Speech.speakers(story.body), emptyMap(), emptyMap(), coverage(paragraphs) { true })
        val sentence = reading.sentence(p, i) ?: return null
        val line = Narration.lineOf(reading.narration, p, i)
        return StoryUse(
            series = series,
            chapter = chapter,
            storyId = story.id,
            paragraph = p,
            index = i,
            words = paragraphs[p].slice(sentence.words),
            marked = sentence.words.filter { paragraphs[p][it].wordId == wordId }.map { it - sentence.words.first }.toSet(),
            speaker = reading.speakers.getOrNull(p),
            translation = sentence.translation,
            line = line?.let(reading.narration::get),
            clip = line?.let { clips(story, reading.narration).getOrNull(it) },
        )
    }

    /** The card mastery follows. */
    private fun recognitionCard(wordId: Long): Card? =
        store.cards(
            "word_id = ? AND direction = ?",
            arrayOf(wordId.toString(), Direction.RECOGNITION.value.toString()),
            "id",
        ).firstOrNull()

    /** Mark a word from the dictionary as known at [mastery], starting it if needed. */
    fun markKnown(wordId: Long, mastery: Int, now: Long) {
        store.transaction { db ->
            store.setSuspended(db, wordId, false)
            if (recognitionCard(wordId) == null) {
                store.insertNewCard(db, wordId, Direction.RECOGNITION, 0)
            }
        }
        markKnown(recognitionCard(wordId) ?: return, mastery, now)
    }

    /** Forget a word: its cards, mastery and history. */
    /** Whether [card] is still as stored and its word isn't suspended, e.g. after a look at the word's page mid-review. */
    fun unchanged(card: Card): Boolean = !store.isSuspended(card.wordId) && store.card(card.id) == card

    fun reset(wordId: Long) {
        store.transaction { db -> store.deleteWord(db, wordId) }
    }

    fun setSuspended(wordId: Long, suspended: Boolean) {
        store.transaction { db ->
            if (suspended && recognitionCard(wordId) == null) {
                // Suspension lives on the card, so an unstarted word gets one.
                store.insertNewCard(db, wordId, Direction.RECOGNITION, 0, suspended = true)
            }
            store.setSuspended(db, wordId, suspended)
            if (!suspended) {
                // An unstarted word goes back to having no card.
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
        val local = store.wordStatuses().mapValues { (_, s) ->
            LocalWord(s.mastery, started = s.state != CardState.NEW, suspended = s.suspended)
        }
        return RenshuuImport.plan(terms, { it in ids }, local)
    }

    /** Mark each word known at its mastery, as "I know this" does, in one transaction. */
    fun importMastery(updates: Map<Long, Int>, now: Long) {
        val settings = settings()
        val reviewer = Reviewer(Fsrs(), clock(settings))
        store.transaction { db ->
            for ((wordId, mastery) in updates) {
                if (recognitionCard(wordId) == null) {
                    store.insertNewCard(db, wordId, Direction.RECOGNITION, 0)
                }
                val card = recognitionCard(wordId) ?: continue
                val known = reviewer.markKnown(card, store.progress(wordId), mastery, now, Random.Default, StartSource.IMPORT)
                store.updateCard(db, known.card)
                store.saveProgress(db, known.progress)
                store.insertLog(db, known.log)
                if (known.unlockedProduction) {
                    store.insertNewCard(db, wordId, Direction.PRODUCTION, now / 1000)
                }
            }
        }
    }

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

    fun speechSettings(): SpeechSettings {
        val default = SpeechSettings()
        return SpeechSettings(
            rate = store.getValue("speech_rate")?.toFloatOrNull()?.takeIf { it in SpeechSettings.RATES } ?: default.rate,
        )
    }

    fun setSpeechSettings(settings: SpeechSettings) {
        store.setValues(mapOf("speech_rate" to settings.rate.toString()))
    }

    /** The stories' cast with their voices, in the order the stories meet them, so each keeps theirs as stories are added. */
    fun castVoices(): List<Pair<String, CastVoice>> = catalog.cast.mapNotNull { m -> m.voice?.let { m.name to it } }

    fun story(id: Long): SavedStory? = store.story(id)

    /** Words you've started, or suspended to leave alone, count as known while reading. */
    private fun known(statuses: Map<Long, WordStatus>): (Long) -> Boolean = { id ->
        val s = statuses[id]
        s != null && (s.state != CardState.NEW || s.suspended)
    }

    fun markStoryRead(id: Long, now: Long, millis: Long) = store.markStoryRead(id, now, millis)

    fun readStory(id: Long): StoryReading? {
        val story = store.story(id) ?: return null
        val paragraphs = paragraphs(story)
        val ids = paragraphs.flatten().mapNotNull { it.wordId }.toSet()
        val statuses = store.wordStatuses()
        return StoryReading(
            story = story,
            paragraphs = paragraphs,
            speakers = Speech.speakers(story.body),
            words = ids.mapNotNull { dictionary.word(it) }.associateBy { it.id },
            statuses = statuses,
            coverage = coverage(paragraphs, known(statuses)),
            lesson = topicOfPage(story)?.page,
            notes = notes(story),
            lessons = lessons(),
        ).let { it.copy(clips = clips(story, it.narration)) }
    }

    /** The recordings that come with the app, by file name. */
    private val recorded: Set<String> by lazy { appContext.assets.list(Recordings.DIR).orEmpty().toSet() }

    /**
     * Each of [lines]' recording, when it's been recorded: in its story's
     * narrator's voice, and each of the cast's own, played at its voice's gain.
     */
    private fun clips(story: SavedStory, lines: List<NarrationLine>): List<Recording?> {
        val narrator = catalog.series.firstOrNull { s -> s.chapters.any { it.story.id == story.pathId } }?.narrator
        val voices = catalog.voices
        return Recordings.clips(lines, voices, catalog.cast.associateBy { it.name }, narrator).map { clip ->
            clip?.takeIf { (key, _) -> "$key.${Recordings.EXTENSION}" in recorded }
                ?.let { (key, voice) -> Recording(Recordings.file(key), voices.gains[voice]?.toFloat() ?: 0f) }
        }
    }

    /** The bundled chapter [story] was saved from, if it's one. */
    private fun chapterOf(story: SavedStory): PathStory? =
        catalog.chapters.firstOrNull { it.second.story.id == story.pathId }?.second?.story

    /** A chapter's notes, each shown from the end of the paragraph before it; two after one paragraph share its marker. */
    private fun notes(story: SavedStory): Map<Int, String> =
        chapterOf(story)?.notes.orEmpty()
            .groupBy({ it.before - 1 }, { it.text })
            .mapValues { it.value.joinToString("\n\n") }

    /** The bundled pages split into words as the story tools read them, a story's file at a time (core [StoryText]). */
    private val texts = HashMap<String, Map<String, List<List<ReadingWord>>>>()

    /** [story]'s paragraphs of words. */
    private fun paragraphs(story: SavedStory): List<List<ReadingWord>> {
        val file = StoryText.file(story.pathId)
        val pages = synchronized(texts) {
            texts.getOrPut(file) { StoryText.parse(appContext.assets.open("stories/read/$file").bufferedReader().use { it.readText() }) }
        }
        return pages[story.pathId].orEmpty()
    }

    /** Each grammar point's topic page, saved when the path was. */
    private fun lessons(): Map<String, Long> {
        val saved = store.pathStories().mapValues { it.value.id }
        return catalog.grammar.flatMap { t -> saved[t.page.id]?.let { id -> t.points.map { it to id } }.orEmpty() }.toMap()
    }

    /** The grammar topic whose page [story] is, if it's one. */
    fun topicOfPage(story: SavedStory): GrammarTopic? = catalog.grammar.firstOrNull { it.page.id == story.pathId }

    // ------------------------------------------------------------ story path

    /** The stories and grammar topics that come with the app. */
    private val catalog by lazy {
        StoryPath.parse(appContext.assets.open("stories/path.json").bufferedReader().use { it.readText() })
    }

    /**
     * What each chapter asks of you, by its id. The story tools work it out
     * when the stories change (core StoryTools), so the app doesn't read every chapter.
     */
    private val needs: Map<String, StepNeeds> by lazy {
        StoryPath.parseNeeds(appContext.assets.open("stories/needs.json").bufferedReader().use { it.readText() }, catalog)
    }
    private var pathSynced = false

    /** Saves the chapters and grammar pages on first use, so they can be opened like any story. */
    private fun savedPath(): Map<String, SavedStory> {
        if (!pathSynced) {
            val stories = catalog.chapters.map { it.second.story } + catalog.grammar.map { it.page }
            store.syncPathStories(stories.map { p -> SavedStory(0, p.id, p.title, p.body, p.translations) })
            pathSynced = true
        }
        return store.pathStories()
    }

    /** What's left of a step to learn: words you haven't started, and grammar you haven't seen. */
    private fun gaps(): (StepNeeds) -> StepGap {
        val isKnown = known(store.wordStatuses())
        val grammar = seenGrammar()
        return { needs -> StoryPath.gap(needs, isKnown, grammar) }
    }

    /** Every story that comes with the app, in order, with each chapter and what you haven't learned of it. */
    fun storyPath(): List<SeriesEntry> {
        val saved = savedPath()
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
    fun chapterEntry(story: SavedStory): ChapterEntry? =
        storyPath().flatMap { it.chapters }.firstOrNull { it.chapter.story.id == story.pathId }

    /** How many words in a chapter you haven't learned yet, and its new grammar, or null off the path. */
    fun stepToLearn(story: SavedStory): StepToLearn? {
        val entry = chapterEntry(story) ?: return null
        return StepToLearn(entry.gap.words.count { it.wordId != null }, entry.newTopic)
    }

    /** Why [chapterId] is locked: what it's missing, and where to pick that up. */
    fun lockInfo(chapterId: String): LockInfo? {
        val path = storyPath()
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
            grammar = grammar,
            sources = sources,
            earlierCover = missing.isNotEmpty() && earlier.containsAll(missing),
        )
    }

    /** Every grammar topic, newest you've met first, then the ones you haven't, in the order they come. */
    fun grammarRows(): List<GrammarRow> {
        val saved = savedPath()
        val seen = seenGrammar()
        val chapters = storyPath().flatMap { it.chapters }
        val rows = catalog.grammar.mapNotNull { topic ->
            val page = saved[topic.page.id] ?: return@mapNotNull null
            val taughtIn = chapters.filter { it.chapter.topic == topic.id }.let { c -> c.firstOrNull { it.read } ?: c.firstOrNull() }
            GrammarRow(topic, seen.containsAll(topic.points), taughtIn, page.id)
        }
        val (met, unmet) = rows.partition { it.seen }
        return met.reversed() + unmet
    }

    /** The saved story holding [topic]'s page. */
    fun grammarPageId(topic: GrammarTopic): Long? = savedPath()[topic.page.id]?.id

    /** Grammar points you've been taught, or said you knew. */
    fun seenGrammar(): Set<String> =
        store.getValue("grammar_seen")?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    /** Marks [topic]'s points seen, and the study day it was learned, unless it already has one. */
    fun learnGrammar(topic: GrammarTopic, now: Long) {
        val days = grammarDays().toMutableMap()
        days.putIfAbsent(topic.id, clock().dayOf(now))
        store.setValues(
            mapOf(
                "grammar_seen" to (seenGrammar() + topic.points).sorted().joinToString(","),
                "grammar_days" to days.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" },
            ),
        )
    }

    /**
     * The study day each grammar topic was learned, by topic id. Topics
     * learned before the app kept these have none.
     */
    private fun grammarDays(): Map<String, Long> =
        store.getValue("grammar_days")?.split(',').orEmpty().mapNotNull { entry ->
            val (id, day) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            day.toLongOrNull()?.let { id to it }
        }.toMap()

    /**
     * Finishing a path story: each word due for review that you read without
     * looking it up counts as a lighter review than a Right answer ([Reviewer.read]).
     */
    fun readWords(story: SavedStory, lookedUp: Set<Long>, now: Long) {
        val reviewer = Reviewer(Fsrs(), clock())
        val ids = StoryPath.words(paragraphs(story)).mapNotNull { it.wordId }
        for (id in ids.distinct() - lookedUp) {
            val card = recognitionCard(id) ?: continue
            if (!reviewer.readCounts(card, now) || store.isSuspended(id)) continue
            val before = store.progress(id)
            save(card, before, reviewer.read(card, before, now, Random.Default), now)
        }
    }

    /** Finishing a story: each of [ids] you said you forgot gets a Wrong review. */
    fun forgetWords(ids: Set<Long>, now: Long) {
        for (id in ids) {
            val card = recognitionCard(id) ?: continue
            if (card.state != CardState.REVIEW || store.isSuspended(id)) continue
            answer(card, false, now)
        }
    }

    /**
     * Finishing a story: its words you haven't started, or said you know,
     * start as cards, as though just shown. Returns their ids.
     */
    /**
     * Finish a chapter in one transaction: mark it read, credit the words read
     * and forgotten, and start its new words. Returns the words started. Done
     * apart, a failure part way left a chapter read whose words never started.
     */
    fun finishChapter(story: SavedStory, now: Long, millis: Long, lookedUp: Set<Long>, forgotten: Set<Long>): List<Long> =
        store.transaction {
            markStoryRead(story.id, now, millis)
            if (readerPlace()?.storyId == story.id) clearReaderPlace()
            readWords(story, lookedUp, now)
            forgetWords(forgotten, now)
            startStoryWords(story, now)
        }

    fun startStoryWords(story: SavedStory, now: Long): List<Long> {
        val isKnown = known(store.wordStatuses())
        val ids = StoryPath.words(paragraphs(story)).mapNotNull { it.wordId }.distinct()
            .filter { !isKnown(it) && dictionary.word(it) != null }
        for (id in ids) start(id, now)
        return ids
    }

    /**
     * A word a story just started that you already know: known now, still
     * started today, but no longer counted against today's new words.
     */
    fun knowStartedWord(wordId: Long, now: Long) = markKnown(wordId, MasteryRules.HIDE_AT, now)

    /** Undo [knowStartedWord]: new again, as the story started it. */
    fun unknowStartedWord(wordId: Long, now: Long) {
        reset(wordId)
        start(wordId, now)
    }

    private fun start(wordId: Long, now: Long) {
        store.transaction { db -> if (recognitionCard(wordId) == null) store.insertNewCard(db, wordId, Direction.RECOGNITION, 0) }
        recognitionCard(wordId)?.takeIf { it.state == CardState.NEW }?.let { introduce(it, now) }
    }

    /** Mastery of each of [ids] you've started. */
    fun masteries(ids: Collection<Long>): Map<Long, Int> {
        val statuses = store.wordStatuses()
        return ids.mapNotNull { id -> statuses[id]?.takeIf { it.state != CardState.NEW }?.let { id to it.mastery } }.toMap()
    }

    // ------------------------------------------------------------ first run

    /** The JLPT level you can already read: 5 for N5 down to 1 for N1, or [ReadLevel.NONE]. */
    fun readLevel(): Int = store.getValue("read_level")?.toIntOrNull()?.takeIf { it in 1..5 } ?: ReadLevel.NONE

    /**
     * Read at [level] from now on. Raising it fills in its words and every
     * easier level's ([ReadLevel]); lowering it changes nothing else.
     */
    fun setReadLevel(level: Int, now: Long) {
        val before = readLevel()
        store.setValues(mapOf("read_level" to level.toString()))
        val raised = level != ReadLevel.NONE && (before == ReadLevel.NONE || level < before)
        if (raised) fillReadLevel(level, now)
    }

    /**
     * Start the words of [level] and easier as known, each at its
     * [ReadLevel] stability. Words you've studied keep their progress where
     * it's higher, and suspended words are left alone. English-to-Japanese
     * cards wait until each word is next answered right, so the batch
     * doesn't crowd reviews with new cards.
     */
    private fun fillReadLevel(level: Int, now: Long) {
        val statuses = store.wordStatuses()
        val fill = dictionary.levels().mapNotNull { (id, jlpt) ->
            val days = ReadLevel.stabilityDays(level, jlpt) ?: return@mapNotNull null
            val status = statuses[id]
            val skip = status != null && (status.suspended || status.mastery >= MasteryRules.fromStability(days.toDouble()))
            if (skip) null else id to days
        }
        val reviewer = Reviewer(Fsrs(), clock())
        store.transaction { db ->
            for ((id, days) in fill) {
                if (recognitionCard(id) == null) store.insertNewCard(db, id, Direction.RECOGNITION, 0)
                val card = recognitionCard(id) ?: continue
                val before = store.progress(id)
                val within = ReadLevel.firstDueWithin(days, fill.size)
                val known = reviewer.markKnownFor(card, before, days, within, now, Random.Default)
                store.updateCard(db, known.card)
                store.saveProgress(db, known.progress.copy(productionUnlocked = before.productionUnlocked))
                store.insertLog(db, known.log)
            }
        }
    }

    /** True on a fresh install, before you've been through setup. Existing progress counts as set up. */
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

    /** Finish the first run: the level you read at, filled in, and the reminder. */
    fun finishSetup(readLevel: Int, vertical: Boolean, reminderMinute: Int?, now: Long) {
        if (reminderMinute != null) saveReminder(Reminder(enabled = true, minuteOfDay = reminderMinute))
        setReaderStyle(readerStyle().copy(vertical = vertical))
        setReadLevel(readLevel, now)
        store.setValues(mapOf("setup_done" to "1"))
    }

    // ------------------------------------------------------------ backup

    /** Write a copy of all your progress, settings and stories. */
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
        // The backup's chapters may be older than the app's.
        pathSynced = false
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
     * and stories read. The next query recreates an empty database, so the
     * app starts again at first-run setup.
     */
    fun resetAll() {
        store.close()
        val target = appContext.getDatabasePath(ProgressStore.NAME)
        SQLiteDatabase.deleteDatabase(target)
        pathSynced = false
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
        val forecast = forecast(clock, today, FORECAST_DAYS)
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
        return Stats(
            today = today,
            forecast = forecast,
            answers = answers,
            recall30 = store.reviewRecall(today - (HISTORY_DAYS - 1)),
            recallAll = store.reviewRecall(Long.MIN_VALUE),
            levels = levels,
            wordsStarted = statuses.values.count { it.state != CardState.NEW },
            totalAnswers = store.count("SELECT COUNT(*) FROM review_log WHERE kind = 0"),
            studyDays = store.studyDays().size,
        )
    }

    // ------------------------------------------------------------ home

    fun dashboard(now: Long): Dashboard {
        val clock = clock()
        val today = clock.dayOf(now)
        val active = activeDays(clock)
        val started = store.startedWords()
        val firstDays = started.associate { w -> w.wordId to w.introducedDay }
        val firsts = listOfNotNull(firstDays.values.filterNotNull().minOrNull(), active.minOrNull())
        val from = minOf(firsts.minOrNull() ?: today, today - MIN_GRAPH_DAYS + 1)
        val words = WordHistory.days(firstDays, started.associate { it.wordId to it.mastery }, store.masteryChanges(), from, today)
        val since = today - RECENT_DAYS
        // Imported and level-filled words count in the graph, but aren't the week's new words.
        val newWords = started
            .filter { it.source.recent && it.introducedDay != null && it.introducedDay > since }
            .sortedByDescending { it.introducedDay }
            .mapNotNull { w -> dictionary.word(w.wordId)?.let { RecentItem.NewWord(it) to w.introducedDay!! } }
        val rows = grammarRows()
        val learned = grammarDays()
        val newGrammar = rows.mapNotNull { row ->
            learned[row.topic.id]?.takeIf { row.seen && it > since }?.let { RecentItem.NewGrammar(row.topic, row.pageId) to it }
        }
        val recent = Recent.pick<RecentItem>(newWords, newGrammar, Int.MAX_VALUE).first
        return Dashboard(
            today = today,
            next = nextUp(now),
            pace = pace(now),
            activeDays = active,
            streak = Streak.current(active, today),
            words = words,
            recent = recent,
        )
    }

    /** Study days with a review answered or a chapter finished. */
    private fun activeDays(clock: StudyClock): Set<Long> = (store.studyDays() + store.storyReadTimes().map(clock::dayOf)).toSet()

    /** What Home suggests: see [Suggest.next]. */
    private fun nextUp(now: Long): NextUp<ChapterEntry> {
        val path = storyPath()
        val chapters = path.flatMap { it.chapters }
        val place = readerPlace()
        val open = chapters.filter { it.ready && !it.read }
        val resumed = open.firstOrNull { it.story.id == place?.storyId }
        val lastSeries = chapters.filter { it.read }.maxByOrNull { it.story.readAt ?: 0 }?.series?.id
        // The chapter you left part way, else the next in the story you read last, else the first open on the path.
        val current = resumed
            ?: path.firstOrNull { it.series.id == lastSeries }?.next
            ?: path.firstOrNull { !it.series.side && it.next != null }?.next
            ?: open.firstOrNull()
        val side = open.firstOrNull { it.nothingNew && it.story.id != current?.story?.id }
        fun candidate(entry: ChapterEntry) = Candidate(
            entry,
            newWords = entry.gap.words.size,
            teachesGrammar = entry.newTopic != null,
            resumePage = place?.takeIf { it.storyId == entry.story.id }?.page ?: 0,
            pages = place?.takeIf { it.storyId == entry.story.id }?.pages ?: 0,
        )
        val pace = pace(now)
        return Suggest.next(
            pace,
            due = pace.reviewsDue,
            current = current?.let(::candidate),
            side = side?.let(::candidate),
            anyRead = chapters.any { it.read },
        )
    }

    fun readerPlace(): ReaderPlace? {
        val parts = store.getValue("reader_place")?.split(':', limit = 4) ?: return null
        if (parts.size < 4) return null
        return ReaderPlace(
            parts[0].toLongOrNull() ?: return null,
            parts[1].toIntOrNull() ?: return null,
            parts[2].toIntOrNull() ?: return null,
            parts[3],
        )
    }

    fun saveReaderPlace(place: ReaderPlace) = store.setValues(mapOf("reader_place" to "${place.storyId}:${place.page}:${place.pages}:${place.style}"))

    fun clearReaderPlace() = store.deleteValue("reader_place")

    // ------------------------------------------------------------ summary

    /** What a review session would show now: the one count behind the Review badge, the Read tab and Home. */
    private data class Queue(val newCards: Int, val learning: Int, val reviews: Int) {
        val total get() = newCards + learning + reviews
    }

    private fun queue(settings: StudySettings, clock: StudyClock, now: Long): Queue {
        val limits = limits(settings, clock, now)
        val reviewsDue = store.count(
            "SELECT COUNT(*) FROM card WHERE state = ? AND $REVIEW_DUE AND suspended = 0",
            CardState.REVIEW.value,
            *reviewDueArgs(limits).toTypedArray(),
        )
        // New words come from the story path, so only unlocked production cards wait here.
        val newWaiting = store.count(
            "SELECT COUNT(*) FROM card WHERE state = ? AND direction = ? AND suspended = 0",
            CardState.NEW.value,
            Direction.PRODUCTION.value,
        )
        return Queue(
            newCards = minOf(limits.newCards, newWaiting),
            learning = store.count(
                "SELECT COUNT(*) FROM card WHERE state IN (?, ?) AND suspended = 0",
                CardState.LEARNING.value,
                CardState.RELEARNING.value,
            ),
            reviews = minOf(limits.reviews, reviewsDue),
        )
    }

    /** Reviews due on each of the [days] days from today; today includes anything overdue and still learning. */
    private fun forecast(clock: StudyClock, today: Long, days: Int): List<Int> {
        val forecast = IntArray(days)
        for (dueAt in store.reviewDueTimes(clock.startOf(today + days))) {
            forecast[(clock.dayOf(dueAt) - today).toInt().coerceIn(0, days - 1)]++
        }
        forecast[0] += store.count(
            "SELECT COUNT(*) FROM card WHERE state IN (?, ?) AND suspended = 0",
            CardState.LEARNING.value,
            CardState.RELEARNING.value,
        )
        return forecast.toList()
    }

    /** Cards waiting now, as the Review tab's badge shows them. */
    fun dueCount(now: Long): Int {
        val settings = settings()
        return queue(settings, clock(settings), now).total
    }

    fun summary(now: Long): Summary {
        val settings = settings()
        val clock = clock(settings)
        val today = clock.dayOf(now)
        val queue = queue(settings, clock, now)
        return Summary(
            newCards = queue.newCards,
            learning = queue.learning,
            reviews = queue.reviews,
            streak = Streak.current(activeDays(clock), today),
            daysAhead = ahead().daysAhead,
            reviewedToday = store.count("SELECT COUNT(*) FROM review_log WHERE day = ? AND kind = 0", today),
            correctToday = store.count("SELECT COUNT(*) FROM review_log WHERE day = ? AND correct = 1 AND kind = 0", today),
            today = today,
            answersPerDay = store.answersPerDay(today - 370),
            readDays = store.storyReadTimes().map(clock::dayOf).toSet(),
            comingUp = forecast(clock, today, 8).drop(1),
        )
    }

    /** The arguments for [REVIEW_DUE], in order. */
    private fun reviewDueArgs(limits: DailyLimits) =
        listOf(limits.endOfToday, limits.reviewDueBefore, limits.answeredSince)

    companion object {
        /** DailyLimits.isDue in SQL. */
        private const val REVIEW_DUE =
            "(due_at < ? OR (due_at < ? AND (last_review_at IS NULL OR last_review_at < ?)))"

        private const val FORECAST_DAYS = 30
        private const val HISTORY_DAYS = 30

        /** The words graph covers at least this many days, so a new start isn't a single point. */
        private const val MIN_GRAPH_DAYS = 7

        /** Words started within this many days are new on Home. */
        private const val RECENT_DAYS = 7

        /** Words you haven't started to list on a kanji's page. */
        private const val MORE_WORDS = 12

        @Volatile private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}
