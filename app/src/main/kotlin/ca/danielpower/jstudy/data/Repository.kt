package ca.danielpower.jstudy.data

import android.content.Context
import ca.danielpower.jstudy.core.Answer
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.DailyLimits
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.Fsrs
import ca.danielpower.jstudy.core.ImportPlan
import ca.danielpower.jstudy.core.LocalWord
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.RenshuuImport
import ca.danielpower.jstudy.core.RenshuuTerm
import ca.danielpower.jstudy.core.Reviewer
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
import ca.danielpower.jstudy.core.StoryWriter
import ca.danielpower.jstudy.core.coverage
import ca.danielpower.jstudy.core.newWordBudget
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
    val paragraphs: List<List<ReadingWord>>,
    /** Dictionary entries for the story's words. */
    val words: Map<Long, Word>,
    val statuses: Map<Long, WordStatus>,
    val coverage: Coverage,
    val rules: MasteryRules,
)

class Repository(context: Context) {
    val dictionary = Dictionary(context)
    private val store = ProgressStore(context)

    /** Kept out of the progress database so a backup doesn't carry it. */
    private val secrets = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)
    private val reader by lazy { Reader(dictionary) }

    // ------------------------------------------------------------ settings

    fun settings(): StudySettings {
        val d = StudySettings()
        val m = d.mastery
        fun int(key: String, default: Int) = store.getValue(key)?.toIntOrNull() ?: default
        return runCatching {
            StudySettings(
                newPerDay = int("new_per_day", d.newPerDay),
                reviewCap = int("review_cap", d.reviewCap),
                desiredRetention = store.getValue("retention")?.toDoubleOrNull() ?: d.desiredRetention,
                rolloverHour = int("rollover_hour", d.rolloverHour),
                mastery = MasteryRules(
                    gainOnRight = int("mastery_gain", m.gainOnRight),
                    lossOnWrong = int("mastery_loss", m.lossOnWrong),
                    hideAt = int("mastery_hide_at", m.hideAt),
                    offAt = int("mastery_off_at", m.offAt),
                    productionAt = int("production_at", m.productionAt),
                ),
            )
        }.getOrDefault(d)
    }

    fun saveSettings(s: StudySettings) {
        store.setValues(
            mapOf(
                "new_per_day" to s.newPerDay.toString(),
                "review_cap" to s.reviewCap.toString(),
                "retention" to s.desiredRetention.toString(),
                "rollover_hour" to s.rolloverHour.toString(),
                "mastery_gain" to s.mastery.gainOnRight.toString(),
                "mastery_loss" to s.mastery.lossOnWrong.toString(),
                "mastery_hide_at" to s.mastery.hideAt.toString(),
                "mastery_off_at" to s.mastery.offAt.toString(),
                "production_at" to s.mastery.productionAt.toString(),
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
            newIntroducedToday = store.count("SELECT COUNT(*) FROM card WHERE introduced_day = ?", today),
            reviewsDoneToday = store.count(
                "SELECT COUNT(*) FROM review_log WHERE day = ? AND state_before = ?",
                today,
                CardState.REVIEW.value,
            ),
        )
    }

    /** Make sure at least [count] new cards exist, taking the next words in study order. */
    private fun ensureNewCards(count: Int) {
        val existing = store.count("SELECT COUNT(*) FROM card WHERE state = ? AND suspended = 0", CardState.NEW.value)
        if (existing >= count) return
        val started = store.wordIdsWithCards(Direction.RECOGNITION)
        val words = dictionary.nextWords(started, count - existing)
        store.transaction { db ->
            for ((wordId, order) in words) store.insertNewCard(db, wordId, Direction.RECOGNITION, order.toLong())
        }
    }

    fun session(now: Long): StudySession {
        val settings = settings()
        val clock = clock(settings)
        val limits = limits(settings, clock, now)
        ensureNewCards(limits.newCards)
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
        val newCards = store.cards(
            "state = ? AND suspended = 0",
            arrayOf(CardState.NEW.value.toString()),
            "sort_key, id",
            limits.newCards,
        )
        return StudySession(reviews, newCards, learning)
    }

    fun endOfToday(now: Long): Long {
        val clock = clock()
        return clock.startOf(clock.dayOf(now) + 1)
    }

    // ------------------------------------------------------------ answers

    fun progress(wordId: Long): WordProgress = store.progress(wordId)

    fun answer(card: Card, correct: Boolean, now: Long): Pair<Answer, Undo> {
        val settings = settings()
        val reviewer = Reviewer(Fsrs(desiredRetention = settings.desiredRetention), settings.mastery, clock(settings))
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
        val reviewer = Reviewer(Fsrs(desiredRetention = settings.desiredRetention), settings.mastery, clock(settings))
        val before = store.progress(card.wordId)
        val introduced = reviewer.introduce(card, now)
        store.transaction { db -> store.updateCard(db, introduced) }
        return introduced to Undo(card, before, null, null)
    }

    /** Mark a word you already know at [mastery]. Returns the review interval in days. */
    fun markKnown(card: Card, mastery: Int, now: Long): Pair<Int, Undo> {
        val settings = settings()
        val reviewer = Reviewer(Fsrs(desiredRetention = settings.desiredRetention), settings.mastery, clock(settings))
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

    private fun recognitionCard(wordId: Long): Card? =
        store.cards(
            "word_id = ? AND direction = ?",
            arrayOf(wordId.toString(), Direction.RECOGNITION.value.toString()),
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
        val local = store.wordStatuses().mapValues { (_, s) ->
            LocalWord(s.mastery, started = s.state != CardState.NEW, suspended = s.suspended)
        }
        return RenshuuImport.plan(terms, { it in ids }, local)
    }

    /** Mark each word known at its mastery, as "I already know this" does, in one transaction. */
    fun importMastery(updates: Map<Long, Int>, now: Long) {
        val settings = settings()
        val reviewer = Reviewer(Fsrs(desiredRetention = settings.desiredRetention), settings.mastery, clock(settings))
        store.transaction { db ->
            for ((wordId, mastery) in updates) {
                if (recognitionCard(wordId) == null) {
                    store.insertNewCard(db, wordId, Direction.RECOGNITION, now / 1000 - LEARN_NEXT_FIRST)
                }
                val card = recognitionCard(wordId) ?: continue
                val known = reviewer.markKnown(card, store.progress(wordId), mastery, now, Random.Default)
                store.updateCard(db, known.card)
                store.saveProgress(db, known.progress)
                if (known.unlockedProduction) {
                    store.insertNewCard(db, wordId, Direction.PRODUCTION, now / 1000 - PRODUCTION_FIRST)
                }
            }
        }
    }

    // ------------------------------------------------------------ stories

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
        val rules = settings().mastery
        val shaky = started.filterValues { !it.suspended && it.mastery < rules.hideAt }.entries
            .sortedBy { it.value.mastery }.take(PRACTICE_POOL).map { it.key }.shuffled().take(PRACTICE)
        val budget = newWordBudget(length, difficulty)
        val queued = store.cards(
            "state = ? AND direction = ? AND suspended = 0",
            arrayOf(CardState.NEW.value.toString(), Direction.RECOGNITION.value.toString()),
            "sort_key, id",
        ).map { it.wordId }
        val introduceCount = maxOf(1, budget / 2)
        val next = (queued + dictionary.nextWords(statuses.keys, introduceCount).map { it.first })
            .distinct().take(introduceCount)
        val frontier = dictionary.nextWords(statuses.keys, 1).firstOrNull()?.first
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
     * Have Claude write a story, save it, and put its new words at the front
     * of the new-word queue in the order they appear. Returns the story's id.
     */
    fun writeStory(request: StoryRequest, now: Long, progress: (String) -> Unit): Long {
        val key = claudeKey()
        check(key.isNotEmpty()) { "Add a Claude API key in Settings first." }
        val writer = StoryWriter(ClaudeConversation(key), reader)
        val written = writer.write(request, known(store.wordStatuses()), progress)
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
            ),
        )
        queueFirst(written.coverage.newWords.mapNotNull { it.wordId }, now)
        return id
    }

    /** Put words at the front of the new-word queue, keeping their order. */
    private fun queueFirst(wordIds: List<Long>, now: Long) {
        val statuses = store.wordStatuses()
        store.transaction { db ->
            for ((i, wordId) in wordIds.withIndex()) {
                val status = statuses[wordId]
                if (status != null && (status.state != CardState.NEW || status.suspended)) continue
                val sortKey = now / 1000 - LEARN_NEXT_FIRST + i
                val card = recognitionCard(wordId)
                if (card == null) {
                    store.insertNewCard(db, wordId, Direction.RECOGNITION, sortKey)
                } else {
                    store.setSortKey(db, card.id, sortKey)
                }
            }
        }
    }

    fun readStory(id: Long): StoryReading? {
        val story = store.story(id) ?: return null
        val paragraphs = reader.read(story.body)
        val ids = paragraphs.flatten().mapNotNull { it.wordId }.toSet()
        val statuses = store.wordStatuses()
        return StoryReading(
            story = story,
            paragraphs = paragraphs,
            words = ids.mapNotNull { dictionary.word(it) }.associateBy { it.id },
            statuses = statuses,
            coverage = coverage(paragraphs, known(statuses)),
            rules = settings().mastery,
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
        val newWaiting = store.count(
            "SELECT COUNT(*) FROM card WHERE state = ? AND direction = ? AND suspended = 0",
            CardState.NEW.value,
            Direction.PRODUCTION.value,
        ) + unstarted
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
        )
    }

    companion object {
        private const val PRODUCTION_FIRST = 10_000_000_000L
        private const val LEARN_NEXT_FIRST = 20_000_000_000L
        private const val PRACTICE_POOL = 20
        private const val PRACTICE = 8

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
