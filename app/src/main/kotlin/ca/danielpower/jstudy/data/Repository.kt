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
import ca.danielpower.jstudy.core.ClaudeConversation
import ca.danielpower.jstudy.core.Coverage
import ca.danielpower.jstudy.core.Reader
import ca.danielpower.jstudy.core.ReadingWord
import ca.danielpower.jstudy.core.StoryDifficulty
import ca.danielpower.jstudy.core.StoryLength
import ca.danielpower.jstudy.core.StoryRequest
import ca.danielpower.jstudy.core.StoryTranslator
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
)

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
) {
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

    /** Kept out of the progress database so a backup doesn't carry it. */
    private val secrets = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)
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
                newPerDay = int("new_per_day", d.newPerDay),
                newKanaPerDay = int("new_kana_per_day", d.newKanaPerDay),
                reviewCap = int("review_cap", d.reviewCap),
                rolloverHour = int("rollover_hour", d.rolloverHour),
            )
        }.getOrDefault(d)
    }

    fun saveSettings(s: StudySettings) {
        store.setValues(
            mapOf(
                "new_per_day" to s.newPerDay.toString(),
                "new_kana_per_day" to s.newKanaPerDay.toString(),
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
            newIntroducedToday = store.count(
                "SELECT COUNT(*) FROM card WHERE introduced_day = ? AND direction != ?",
                today,
                Direction.KANA.value,
            ),
            newKanaIntroducedToday = store.count(
                "SELECT COUNT(*) FROM card WHERE introduced_day = ? AND direction = ?",
                today,
                Direction.KANA.value,
            ),
            reviewsDoneToday = store.count(
                "SELECT COUNT(*) FROM review_log WHERE day = ? AND state_before = ?",
                today,
                CardState.REVIEW.value,
            ),
        )
    }

    /**
     * Make sure at least [count] new word cards exist, taking the next words
     * in study order that you can read.
     */
    private fun ensureNewCards(count: Int) {
        val existing = newWordCards()
        if (existing >= count) return
        val started = store.wordIdsWithCards(Direction.RECOGNITION)
        val words = dictionary.nextWords(started, count - existing, startLevel(), canRead())
        store.transaction { db ->
            for ((wordId, order) in words) store.insertNewCard(db, wordId, Direction.RECOGNITION, order.toLong())
        }
    }

    private fun newWordCards() = store.count(
        "SELECT COUNT(*) FROM card WHERE state = ? AND direction != ? AND suspended = 0",
        CardState.NEW.value,
        Direction.KANA.value,
    )

    private fun newKanaCards() = store.count(
        "SELECT COUNT(*) FROM card WHERE state = ? AND direction = ? AND suspended = 0",
        CardState.NEW.value,
        Direction.KANA.value,
    )

    /** Make sure at least [count] new kana cards exist, taking the next kana in teaching order. */
    private fun ensureNewKana(count: Int) {
        val existing = newKanaCards()
        if (existing >= count) return
        val started = store.wordIdsWithCards(Direction.KANA)
        val next = KanaCourse.toTeach(kanaKnown()).withIndex().filter { it.value.id !in started }.take(count - existing)
        store.transaction { db ->
            for ((order, unit) in next) store.insertNewCard(db, unit.id, Direction.KANA, order.toLong())
        }
    }

    fun session(now: Long): StudySession {
        val settings = settings()
        val clock = clock(settings)
        val limits = limits(settings, clock, now)
        ensureNewCards(limits.newCards)
        if (kanaKnown() != KanaKnown.BOTH) ensureNewKana(limits.newKana)
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
        val newKana = store.cards(
            "state = ? AND direction = ? AND suspended = 0",
            arrayOf(CardState.NEW.value.toString(), Direction.KANA.value.toString()),
            "sort_key, id",
            limits.newKana,
        )
        val newWords = store.cards(
            "state = ? AND direction != ? AND suspended = 0",
            arrayOf(CardState.NEW.value.toString(), Direction.KANA.value.toString()),
            "sort_key, id",
            limits.newCards,
        )
        return StudySession(reviews, newKana + newWords, learning, random = Random.Default)
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
        val answer = reviewer.answer(card, before, correct, now, Random.Default)
        return store.transaction { db ->
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

    /** Mark a word from the dictionary as known at [mastery], starting it if needed. */
    fun markKnown(wordId: Long, mastery: Int, now: Long) {
        store.transaction { db ->
            store.setSuspended(db, wordId, false)
            if (recognitionCard(wordId) == null) {
                store.insertNewCard(db, wordId, Direction.RECOGNITION, now / 1000 - LEARN_NEXT_FIRST)
            }
        }
        markKnown(recognitionCard(wordId) ?: return, mastery, now)
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

    /** Whether stories are set vertically, top to bottom and right to left. */
    fun storyVertical(): Boolean = store.getValue("story_vertical") == "1"

    fun setStoryVertical(vertical: Boolean) {
        store.setValues(mapOf("story_vertical" to if (vertical) "1" else "0"))
    }

    fun claudeKey(): String = secrets.getString("claude_api_key", null).orEmpty()

    fun saveClaudeKey(key: String) {
        secrets.edit().putString("claude_api_key", key.trim()).apply()
    }

    fun stories(): List<SavedStory> = store.stories()

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
    fun writeStory(request: StoryRequest, now: Long, progress: (String) -> Unit): Long {
        val key = claudeKey()
        check(key.isNotEmpty()) { "Add a Claude API key in Settings first." }
        val writer = StoryWriter(ClaudeConversation(key), reader)
        val written = writer.write(request, known(store.wordStatuses()), progress)
        progress("Translating")
        val sentences = sentenceTexts(listOf(reader.read(written.story.title).flatten()) + reader.read(written.story.body))
        // The story is worth keeping without its translations if they fail.
        val translations = try {
            StoryTranslator(ClaudeConversation(key, effort = "low")).translate(sentences)
        } catch (e: IOException) {
            null
        }
        progress("Saving")
        val id = store.insertStory(
            SavedStory(
                id = 0,
                createdAt = now,
                title = written.story.title,
                body = written.story.body,
                prompt = request.prompt.trim(),
                length = request.length.name,
                difficulty = request.difficulty.name,
                translations = translations.orEmpty(),
            ),
        )
        return id
    }

    fun readStory(id: Long): StoryReading? {
        val story = store.story(id) ?: return null
        val title = reader.read(story.title).flatten()
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
        )
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

    /** Write a copy of all your progress, settings and stories. The Claude API key isn't included. */
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
            throw BackupException("That file isn't a jstudy backup.")
        }
        db.use {
            val tables = it.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            if (!tables.containsAll(listOf("card", "word_progress", "review_log", "kv"))) {
                throw BackupException("That file isn't a jstudy backup.")
            }
            if (it.version > ProgressStore.VERSION) {
                throw BackupException("That backup is from a newer version of jstudy. Update the app first.")
            }
        }
    }

    // ------------------------------------------------------------ reset

    /**
     * Erase everything you've done here: cards, mastery, history, settings,
     * stories and the Claude API key. The next query recreates an empty
     * database, so the app starts again at first-run setup.
     */
    fun resetAll() {
        store.close()
        val target = appContext.getDatabasePath(ProgressStore.NAME)
        SQLiteDatabase.deleteDatabase(target)
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
        val unstarted = total - store.count(
            "SELECT COUNT(*) FROM card WHERE direction = ? AND (state != ? OR suspended = 1)",
            Direction.RECOGNITION.value,
            CardState.NEW.value,
        )
        val known = kanaKnown()
        val newWaiting = if (known == KanaKnown.BOTH) {
            store.count(
                "SELECT COUNT(*) FROM card WHERE state = ? AND direction = ? AND suspended = 0",
                CardState.NEW.value,
                Direction.PRODUCTION.value,
            ) + unstarted
        } else {
            // Only words you can read are waiting, so queue them to count them.
            ensureNewCards(limits.newCards)
            newWordCards()
        }
        val kanaWaiting = if (known == KanaKnown.BOTH) {
            0
        } else {
            ensureNewKana(limits.newKana)
            newKanaCards()
        }
        val toTeach = KanaCourse.toTeach(known)
        val learned = store.kanaInReview()
        return Summary(
            newCards = minOf(limits.newCards, newWaiting) + minOf(limits.newKana, kanaWaiting),
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
        private const val PRODUCTION_FIRST = 10_000_000_000L
        private const val FORECAST_DAYS = 30
        private const val HISTORY_DAYS = 30
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
