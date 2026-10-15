package ca.danielpower.jstudy.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.ReviewLog
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
            dueAt = 123_456, lastReviewAt = 100_000, reps = 4, lapses = 1, introducedDay = 20_000,
        )
        store.transaction { store.updateCard(it, reviewed) }
        assertEquals(reviewed, store.card(id))

        // Nulls go back as nulls.
        val cleared = reviewed.copy(step = null, stability = null, difficulty = null, lastReviewAt = null, introducedDay = null)
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
        assertEquals(setOf(7L), store.wordIdsWithCards(Direction.RECOGNITION))
        assertEquals(setOf(7L), store.wordIdsWithCards(Direction.PRODUCTION))
        assertEquals(emptySet<Long>(), store.wordIdsWithCards(Direction.KANA))
    }

    @Test
    fun statusesJoinMasteryAndSuspension() {
        store.transaction {
            store.insertNewCard(it, 7, Direction.RECOGNITION, 1)
            store.insertNewCard(it, 8, Direction.RECOGNITION, 2, suspended = true)
            store.insertNewCard(it, -1, Direction.KANA, 3)
            store.saveProgress(it, WordProgress(7, mastery = 55, productionUnlocked = true))
        }
        assertEquals(
            mapOf(7L to WordStatus(CardState.NEW, false, 55), 8L to WordStatus(CardState.NEW, true, 0)),
            store.wordStatuses(),
        )
        assertEquals(mapOf(-1L to WordStatus(CardState.NEW, false, 0)), store.kanaStatuses())
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
        store.transaction { store.deleteProgress(it, 7) }
        assertEquals(WordProgress(7), store.progress(7))
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
    fun rebuildingTheQueueKeepsStartedSuspendedAndLearnNextCards() {
        val ids = store.transaction {
            val queued = store.insertNewCard(it, 1, Direction.RECOGNITION, 1)
            val learnNext = store.insertNewCard(it, 2, Direction.RECOGNITION, -1)
            val suspended = store.insertNewCard(it, 3, Direction.RECOGNITION, 2, suspended = true)
            val started = store.insertNewCard(it, 4, Direction.RECOGNITION, 3)
            store.updateCard(it, store.card(started)!!.copy(state = CardState.LEARNING, reps = 1))
            val kana = store.insertNewCard(it, -1, Direction.KANA, 4)
            listOf(queued, learnNext, suspended, started, kana)
        }
        store.transaction { store.deleteQueuedNewCards(it) }
        assertNull(store.card(ids[0]))
        for (id in ids.drop(1)) assertEquals(id, store.card(id)?.id)

        store.transaction { store.deleteUnstartedKana(it) }
        assertNull(store.card(ids[4]))
    }

    @Test
    fun kanaInReviewListsOnlyFinishedKana() {
        store.transaction {
            val done = store.insertNewCard(it, -1, Direction.KANA, 1)
            store.updateCard(it, store.card(done)!!.copy(state = CardState.REVIEW))
            val learning = store.insertNewCard(it, -2, Direction.KANA, 2)
            store.updateCard(it, store.card(learning)!!.copy(state = CardState.LEARNING))
        }
        assertEquals(setOf(-1L), store.kanaInReview())
    }

    @Test
    fun settingsAreStoredAndReplaced() {
        assertNull(store.getValue("new_per_day"))
        store.setValues(mapOf("new_per_day" to "10", "kana_known" to "none"))
        store.setValues(mapOf("new_per_day" to "15"))
        assertEquals("15", store.getValue("new_per_day"))
        assertEquals("none", store.getValue("kana_known"))
    }

    @Test
    fun storiesAreListedNewestFirst() {
        val old = store.insertStory(SavedStory(0, 100, "古い", "本文", "", "SHORT", "EASY"))
        val new = store.insertStory(SavedStory(0, 200, "新しい", "本文", "cats", "LONG", "HARD", listOf("New", "Text")))
        assertEquals(listOf(new, old), store.stories().map { it.id })
        assertEquals(SavedStory(new, 200, "新しい", "本文", "cats", "LONG", "HARD", listOf("New", "Text")), store.story(new))
        assertEquals(emptyList<String>(), store.story(old)!!.translations)
        store.deleteStory(new)
        assertNull(store.story(new))
        assertEquals(listOf(old), store.stories().map { it.id })
    }

    @Test
    fun aSequelNumbersBothPartsAndLinksBack() {
        val first = store.insertStory(SavedStory(0, 100, "猫", "一", "", "SHORT", "EASY"))
        val second = store.insertSequel(store.story(first)!!, SavedStory(0, 200, "猫", "二", "", "SHORT", "EASY"))
        val third = store.insertSequel(store.story(second)!!, SavedStory(0, 300, "猫", "三", "", "SHORT", "EASY"))
        assertEquals(1, store.story(first)!!.part)
        assertEquals(2, store.story(second)!!.part)
        assertEquals(3, store.story(third)!!.part)
        assertEquals(second, store.story(third)!!.previousId)
        assertEquals(second, store.nextPart(first))
        assertNull(store.nextPart(third))
    }

    @Test
    fun finishingAStoryMarksItRead() {
        val id = store.insertStory(SavedStory(0, 100, "猫", "一", "", "SHORT", "EASY"))
        assertNull(store.story(id)!!.readAt)
        store.markStoryRead(id, 500, 120_000)
        assertEquals(500L, store.story(id)!!.readAt)
        assertEquals(120_000L, store.story(id)!!.readMillis)
    }

    @Test
    fun pathStoriesAreSavedOnceUpdatedInPlaceAndKeptApart() {
        fun path(body: String) = SavedStory(0, 100, "猫", body, "", "", "", listOf("Cat", "A cat."), pathId = "path-01")
        val mine = store.insertStory(SavedStory(0, 50, "犬", "犬です。", "", "SHORT", "EASY"))
        store.syncPathStories(listOf(path("猫です。")))
        val saved = store.pathStories().getValue("path-01")
        store.markStoryRead(saved.id, 500, 1_000)
        store.syncPathStories(listOf(path("これは猫です。")))
        val updated = store.pathStories().getValue("path-01")
        assertEquals(saved.id, updated.id)
        assertEquals("これは猫です。", updated.body)
        assertEquals(500L, updated.readAt)
        assertEquals(listOf(mine), store.stories().map { it.id })
        store.deleteStory(updated.id)
        assertEquals(1, store.pathStories().size)
    }

    @Test
    fun pathStoriesTheAppNoLongerHasAreRemoved() {
        fun path(id: String) = SavedStory(0, 100, "Cat", "猫だ。", "", "", "", listOf("A cat."), pathId = id)
        store.syncPathStories(listOf(path("old-01"), path("new-01")))
        store.syncPathStories(listOf(path("new-01")))
        assertEquals(setOf("new-01"), store.pathStories().keys)
    }

    @Test
    fun upgradingFromVersion5KeepsStories() {
        store.close()
        context.deleteDatabase(ProgressStore.NAME)
        val path = context.getDatabasePath(ProgressStore.NAME).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE story (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, title TEXT NOT NULL, " +
                    "body TEXT NOT NULL, prompt TEXT NOT NULL, length TEXT NOT NULL, difficulty TEXT NOT NULL, translations TEXT NOT NULL DEFAULT '')",
            )
            db.execSQL("INSERT INTO story (created_at, title, body, prompt, length, difficulty) VALUES (1, '猫', '一', '', 'SHORT', 'EASY')")
            db.version = 5
        }
        store = ProgressStore(context)
        val story = store.stories().single()
        assertEquals("猫", story.title)
        assertNull(story.part)
        store.markStoryRead(story.id, 2, 3)
        assertEquals(2L, store.story(story.id)!!.readAt)
    }

    @Test
    fun aFailedTransactionWritesNothing() {
        runCatching {
            store.transaction {
                store.insertNewCard(it, 7, Direction.RECOGNITION, 1)
                error("boom")
            }
        }
        assertEquals(emptySet<Long>(), store.wordIdsWithCards(Direction.RECOGNITION))
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
}
