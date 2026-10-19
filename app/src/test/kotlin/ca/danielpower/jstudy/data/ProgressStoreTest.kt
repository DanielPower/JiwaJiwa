package ca.danielpower.jstudy.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.LogKind
import ca.danielpower.jstudy.core.ReviewLog
import ca.danielpower.jstudy.core.StartSource
import ca.danielpower.jstudy.core.WordProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: ProgressStore

    @Before
    fun open() {
        context.deleteDatabase(ProgressStore.NAME)
        store = ProgressStore(context)
    }

    @After
    fun close() {
        store.close()
    }

    private fun wordIds(direction: Direction) =
        store.cards("direction = ?", arrayOf(direction.value.toString()), "id").map { it.wordId }.toSet()

    private fun log(cardId: Long, day: Long, correct: Boolean, before: CardState = CardState.REVIEW) = ReviewLog(
        cardId = cardId, reviewedAt = day * 86_400_000L, day = day, correct = correct,
        stateBefore = before, stateAfter = CardState.REVIEW, elapsedDays = 3,
        stabilityAfter = 10.0, difficultyAfter = 5.0, dueAt = 0, masteryBefore = 40, masteryAfter = 50,
    )

    @Test
    fun cardsRoundTripEveryField() {
        val id = store.transaction { store.insertNewCard(it, wordId = 7, direction = Direction.RECOGNITION, sortKey = 1) }
        val fresh = store.card(id)!!
        assertEquals(Card(id = id, wordId = 7, direction = Direction.RECOGNITION), fresh)

        val reviewed = fresh.copy(
            state = CardState.RELEARNING, step = 0, stability = 2.5, difficulty = 6.25,
            dueAt = 123_456, lastReviewAt = 100_000, reps = 4, lapses = 1, introducedDay = 20_000, startSource = StartSource.IMPORT,
        )
        store.transaction { store.updateCard(it, reviewed) }
        assertEquals(reviewed, store.card(id))

        // Nulls go back as nulls.
        val cleared = reviewed.copy(step = null, stability = null, difficulty = null, lastReviewAt = null, introducedDay = null, startSource = StartSource.STORY)
        store.transaction { store.updateCard(it, cleared) }
        assertEquals(cleared, store.card(id))
    }

    @Test
    fun aWordHasOneCardPerDirection() {
        store.transaction {
            assertTrue(store.insertNewCard(it, 7, Direction.RECOGNITION, 1) > 0)
            assertEquals(-1L, store.insertNewCard(it, 7, Direction.RECOGNITION, 2))
            assertTrue(store.insertNewCard(it, 7, Direction.PRODUCTION, 3) > 0)
        }
        assertEquals(setOf(7L), wordIds(Direction.RECOGNITION))
        assertEquals(setOf(7L), wordIds(Direction.PRODUCTION))
    }

    @Test
    fun statusesJoinMasteryAndSuspension() {
        store.transaction {
            store.insertNewCard(it, 7, Direction.RECOGNITION, 1)
            store.insertNewCard(it, 8, Direction.RECOGNITION, 2, suspended = true)
            store.saveProgress(it, WordProgress(7, mastery = 55, productionUnlocked = true))
        }
        assertEquals(
            mapOf(7L to WordStatus(CardState.NEW, false, 55), 8L to WordStatus(CardState.NEW, true, 0)),
            store.wordStatuses(),
        )
        assertTrue(store.isSuspended(8))
        store.transaction { store.setSuspended(it, 8, false) }
        assertFalse(store.isSuspended(8))
    }

    @Test
    fun progressDefaultsAndReplaces() {
        assertEquals(WordProgress(7), store.progress(7))
        store.transaction { store.saveProgress(it, WordProgress(7, mastery = 30)) }
        store.transaction { store.saveProgress(it, WordProgress(7, mastery = 60, productionUnlocked = true)) }
        assertEquals(WordProgress(7, mastery = 60, productionUnlocked = true), store.progress(7))
    }

    @Test
    fun deletingAWordRemovesItsCardsProgressAndHistory() {
        val (kept, gone) = store.transaction {
            val kept = store.insertNewCard(it, 1, Direction.RECOGNITION, 1)
            val gone = store.insertNewCard(it, 7, Direction.RECOGNITION, 2)
            store.insertNewCard(it, 7, Direction.PRODUCTION, 3)
            store.saveProgress(it, WordProgress(7, mastery = 50))
            store.insertLog(it, log(kept, 10, true))
            store.insertLog(it, log(gone, 10, true))
            kept to gone
        }
        assertEquals(1, store.logs(7).size)
        store.transaction { store.deleteWord(it, 7) }
        assertNull(store.card(gone))
        assertEquals(emptyList<Any>(), store.logs(7))
        assertEquals(WordProgress(7), store.progress(7))
        assertEquals(1, store.logs(1).size)
        assertEquals(kept, store.card(kept)!!.id)
    }

    @Test
    fun historyCountsPerDay() {
        store.transaction {
            store.insertLog(it, log(1, 10, true))
            store.insertLog(it, log(1, 11, false))
            store.insertLog(it, log(2, 11, true))
            store.insertLog(it, log(3, 11, true, before = CardState.LEARNING))
        }
        assertEquals(mapOf(10L to 1, 11L to 3), store.answersPerDay(0))
        assertEquals(mapOf(11L to 3), store.answersPerDay(11))
        assertEquals(mapOf(10L to (1 to 1), 11L to (3 to 2)), store.answersAndRightPerDay(0))
        // Learning answers don't count toward recall.
        assertEquals(2 to 3, store.reviewRecall(0))
        assertEquals(0 to 0, store.reviewRecall(12))
        assertEquals(listOf(11L, 10L), store.studyDays())
    }

    @Test
    fun reviewDueTimesSkipSuspendedAndLaterCards() {
        store.transaction {
            for ((word, due) in listOf(1L to 100L, 2L to 200L, 3L to 300L)) {
                val id = store.insertNewCard(it, word, Direction.RECOGNITION, word, suspended = word == 2L)
                store.updateCard(it, store.card(id)!!.copy(state = CardState.REVIEW, dueAt = due))
            }
        }
        assertEquals(listOf(100L), store.reviewDueTimes(250))
    }

    @Test
    fun settingsAreStoredAndReplaced() {
        assertNull(store.getValue("new_per_day"))
        store.setValues(mapOf("new_per_day" to "10", "theme" to "dark"))
        store.setValues(mapOf("new_per_day" to "15"))
        assertEquals("15", store.getValue("new_per_day"))
        assertEquals("dark", store.getValue("theme"))
    }

    @Test
    fun finishingAStoryMarksItRead() {
        store.syncPathStories(listOf(SavedStory(0, "path-01", "Cat", "猫だ。", listOf("A cat."))))
        val id = store.pathStories().getValue("path-01").id
        assertNull(store.story(id)!!.readAt)
        store.markStoryRead(id, 500, 120_000)
        assertEquals(500L, store.story(id)!!.readAt)
        assertEquals(120_000L, store.story(id)!!.readMillis)
    }

    @Test
    fun pathStoriesAreSavedOnceAndUpdatedInPlace() {
        fun path(body: String) = SavedStory(0, "path-01", "猫", body, listOf("A cat."))
        store.syncPathStories(listOf(path("猫です。")))
        val saved = store.pathStories().getValue("path-01")
        store.markStoryRead(saved.id, 500, 1_000)
        store.syncPathStories(listOf(path("これは猫です。")))
        val updated = store.pathStories().getValue("path-01")
        assertEquals(saved.id, updated.id)
        assertEquals("これは猫です。", updated.body)
        assertEquals(listOf("A cat."), updated.translations)
        assertEquals(500L, updated.readAt)
    }

    @Test
    fun pathStoriesTheAppNoLongerHasAreRemoved() {
        fun path(id: String) = SavedStory(0, id, "Cat", "猫だ。", listOf("A cat."))
        store.syncPathStories(listOf(path("old-01"), path("new-01")))
        store.syncPathStories(listOf(path("new-01")))
        assertEquals(setOf("new-01"), store.pathStories().keys)
    }

    @Test
    fun upgradingToVersion8KeepsOnlyWhenBundledStoriesWereRead() {
        store.close()
        context.deleteDatabase(ProgressStore.NAME)
        val path = context.getDatabasePath(ProgressStore.NAME).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE story (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, title TEXT NOT NULL, " +
                    "body TEXT NOT NULL, prompt TEXT NOT NULL, length TEXT NOT NULL, difficulty TEXT NOT NULL, " +
                    "translations TEXT NOT NULL DEFAULT '', part INTEGER, previous_id INTEGER, read_at INTEGER, read_ms INTEGER, path_id TEXT)",
            )
            db.execSQL("INSERT INTO story (created_at, title, body, prompt, length, difficulty) VALUES (1, '猫', '一', 'cats', 'SHORT', 'EASY')")
            db.execSQL(
                "INSERT INTO story (created_at, title, body, prompt, length, difficulty, translations, read_at, read_ms, path_id) " +
                    "VALUES (1, 'Cat', '猫だ。', '', '', '', 'A cat.', 2, 3, 'path-01')",
            )
            db.version = 7
        }
        store = ProgressStore(context)
        val story = store.pathStories().values.single()
        assertEquals(SavedStory(story.id, "path-01", "Cat", "猫だ。", listOf("A cat."), readAt = 2, readMillis = 3), story)
    }

    @Test
    fun aFailedTransactionWritesNothing() {
        runCatching {
            store.transaction {
                store.insertNewCard(it, 7, Direction.RECOGNITION, 1)
                error("boom")
            }
        }
        assertEquals(emptySet<Long>(), wordIds(Direction.RECOGNITION))
    }

    @Test
    fun upgradingFromVersion3DerivesMasteryFromStability() {
        store.close()
        context.deleteDatabase(ProgressStore.NAME)
        // A version 3 database with points-based mastery.
        val path = context.getDatabasePath(ProgressStore.NAME).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            ProgressStore(context).onCreate(db)
            db.version = 3
            fun card(word: Long, direction: Direction, stability: Double?) = db.insert(
                "card", null,
                ContentValues().apply {
                    put("word_id", word)
                    put("direction", direction.value)
                    put("state", if (stability == null) CardState.NEW.value else CardState.REVIEW.value)
                    if (stability == null) putNull("stability") else put("stability", stability)
                    put("due_at", 0L)
                    put("reps", 0)
                    put("lapses", 0)
                    put("sort_key", word)
                },
            )
            fun mastery(word: Long, points: Int) = db.insert(
                "word_progress", null,
                ContentValues().apply {
                    put("word_id", word)
                    put("mastery", points)
                    put("production_unlocked", 1)
                },
            )
            card(1, Direction.RECOGNITION, 30.0)
            card(1, Direction.PRODUCTION, 1.0)
            mastery(1, 10)
            card(2, Direction.RECOGNITION, null)
            mastery(2, 80)
        }

        store = ProgressStore(context)
        assertEquals(WordProgress(1, mastery = 70, productionUnlocked = true), store.progress(1))
        assertEquals(WordProgress(2, mastery = 0, productionUnlocked = true), store.progress(2))
    }

    @Test
    fun upgradingToVersion9DropsKanaCardsAndQueuedWords() {
        store.close()
        context.deleteDatabase(ProgressStore.NAME)
        val path = context.getDatabasePath(ProgressStore.NAME).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            ProgressStore(context).onCreate(db)
            db.version = 8
            fun card(word: Long, direction: Int) = db.insert(
                "card", null,
                ContentValues().apply {
                    put("word_id", word)
                    put("direction", direction)
                    put("state", CardState.REVIEW.value)
                    put("due_at", 0L)
                    put("reps", 1)
                    put("lapses", 0)
                    put("sort_key", 0)
                },
            )
            card(7, Direction.RECOGNITION.value)
            card(-1, 2)
            // Waiting unstarted in the old new-word queue.
            db.execSQL(
                "INSERT INTO card (word_id, direction, state, due_at, reps, lapses, sort_key) VALUES (8, 0, 0, 0, 0, 0, 1)",
            )
            db.execSQL("INSERT INTO word_progress (word_id, mastery, production_unlocked) VALUES (7, 50, 0), (-1, 40, 0)")
            db.execSQL("INSERT INTO kv (key, value) VALUES ('kana_known', 'none')")
        }

        store = ProgressStore(context)
        assertEquals(1, store.count("SELECT COUNT(*) FROM card"))
        assertEquals(setOf(7L), wordIds(Direction.RECOGNITION))
        assertEquals(0, store.progress(-1).mastery)
        assertEquals(50, store.progress(7).mastery)
        assertNull(store.getValue("kana_known"))
    }
    @Test
    fun upgradingToVersion10AddsStartSource() {
        store.close()
        context.deleteDatabase(ProgressStore.NAME)
        val path = context.getDatabasePath(ProgressStore.NAME).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            ProgressStore(context).onCreate(db)
            // A version 9 card table, without start_source.
            db.execSQL("DROP TABLE card")
            db.execSQL(
                "CREATE TABLE card (id INTEGER PRIMARY KEY AUTOINCREMENT, word_id INTEGER NOT NULL, direction INTEGER NOT NULL, " +
                    "state INTEGER NOT NULL, step INTEGER, stability REAL, difficulty REAL, due_at INTEGER NOT NULL, " +
                    "last_review_at INTEGER, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, introduced_day INTEGER, " +
                    "sort_key INTEGER NOT NULL, suspended INTEGER NOT NULL DEFAULT 0, UNIQUE (word_id, direction))",
            )
            db.execSQL(
                "INSERT INTO card (word_id, direction, state, due_at, reps, lapses, introduced_day, sort_key) VALUES (7, 0, 2, 0, 1, 0, 20000, 0)",
            )
            db.version = 9
        }

        store = ProgressStore(context)
        val card = store.cards("word_id = ?", arrayOf("7"), "id").single()
        assertEquals(20_000L, card.introducedDay)
        assertEquals(StartSource.STORY, card.startSource)
        // The review log gains its kind column too, and old rows are answers.
        store.transaction { store.insertLog(it, log(card.id, day = 5, correct = true).copy(kind = LogKind.READ)) }
        assertEquals(1, store.count("SELECT COUNT(*) FROM review_log WHERE kind = 1"))
    }

    @Test
    fun upgradingToVersion11IndexesTheLogByCardAndClearsRemovedSettings() {
        store.close()
        context.deleteDatabase(ProgressStore.NAME)
        val path = context.getDatabasePath(ProgressStore.NAME).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            ProgressStore(context).onCreate(db)
            // A version 10 database: no card index on the log, and settings the app no longer has.
            db.execSQL("DROP INDEX review_log_card")
            db.execSQL("INSERT INTO kv (key, value) VALUES ('retention', '0.9'), ('mastery_known', '40'), ('reminder_minute', '480')")
            db.version = 10
        }

        store = ProgressStore(context)
        assertEquals(1, store.count("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'review_log_card'"))
        assertNull(store.getValue("retention"))
        assertNull(store.getValue("mastery_known"))
        assertEquals("480", store.getValue("reminder_minute"))
    }

    @Test
    fun readsInStoriesStayOutOfAnswerStats() {
        val id = store.transaction { store.insertNewCard(it, wordId = 7, direction = Direction.RECOGNITION, sortKey = 1) }
        store.transaction {
            store.insertLog(it, log(id, day = 10, correct = true))
            store.insertLog(it, log(id, day = 10, correct = true).copy(kind = LogKind.READ))
            store.insertLog(it, log(id, day = 11, correct = true).copy(kind = LogKind.READ))
        }
        assertEquals(mapOf(10L to 1), store.answersPerDay(0))
        assertEquals(1 to 1, store.reviewRecall(0))
        assertEquals(listOf(10L), store.studyDays())
        assertEquals(3, store.masteryChanges().size)
        assertEquals("the word page lists answers only", 1, store.logs(7).size)
    }
}
