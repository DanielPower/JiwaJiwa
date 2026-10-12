package ca.danielpower.jstudy.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.ReviewLog
import ca.danielpower.jstudy.core.WordProgress

data class WordStatus(val state: CardState, val suspended: Boolean, val mastery: Int)

data class ReviewEntry(val at: Long, val correct: Boolean, val masteryAfter: Int)

/** A story written for you, as saved. */
data class SavedStory(
    val id: Long,
    val createdAt: Long,
    val title: String,
    val body: String,
    val prompt: String,
    val length: String,
    val difficulty: String,
)

/** Your cards, mastery and review history. This is what a backup saves. */
class ProgressStore(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE card (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id INTEGER NOT NULL,
                direction INTEGER NOT NULL,
                state INTEGER NOT NULL,
                step INTEGER,
                stability REAL,
                difficulty REAL,
                due_at INTEGER NOT NULL,
                last_review_at INTEGER,
                reps INTEGER NOT NULL,
                lapses INTEGER NOT NULL,
                introduced_day INTEGER,
                -- Unused since mastery is derived from stability (version 4).
                last_mastery_day INTEGER,
                sort_key INTEGER NOT NULL,
                suspended INTEGER NOT NULL DEFAULT 0,
                UNIQUE (word_id, direction)
            )
            """,
        )
        db.execSQL("CREATE INDEX card_state_due ON card (state, due_at)")
        db.execSQL("CREATE INDEX card_introduced ON card (introduced_day)")
        db.execSQL(
            """
            CREATE TABLE word_progress (
                word_id INTEGER PRIMARY KEY,
                mastery INTEGER NOT NULL,
                production_unlocked INTEGER NOT NULL
            )
            """,
        )
        db.execSQL(
            """
            CREATE TABLE review_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                card_id INTEGER NOT NULL,
                reviewed_at INTEGER NOT NULL,
                day INTEGER NOT NULL,
                correct INTEGER NOT NULL,
                state_before INTEGER NOT NULL,
                state_after INTEGER NOT NULL,
                elapsed_days INTEGER,
                stability REAL NOT NULL,
                difficulty REAL NOT NULL,
                due_at INTEGER NOT NULL,
                mastery_before INTEGER NOT NULL,
                mastery_after INTEGER NOT NULL
            )
            """,
        )
        db.execSQL("CREATE INDEX review_log_day ON review_log (day)")
        db.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        createStories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE card ADD COLUMN suspended INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 3) createStories(db)
        if (oldVersion < 4) deriveMastery(db)
    }

    /** Replace the old points-based mastery with the value from each word's own card. */
    private fun deriveMastery(db: SQLiteDatabase) {
        db.rawQuery(
            "SELECT word_id, stability FROM card WHERE direction != ?",
            arrayOf(Direction.PRODUCTION.value.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val stability = if (c.isNull(1)) null else c.getDouble(1)
                db.update(
                    "word_progress",
                    ContentValues().apply { put("mastery", MasteryRules.fromStability(stability)) },
                    "word_id = ?",
                    arrayOf(c.getLong(0).toString()),
                )
            }
        }
    }

    private fun createStories(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE story (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                prompt TEXT NOT NULL,
                length TEXT NOT NULL,
                difficulty TEXT NOT NULL
            )
            """,
        )
    }

    // ------------------------------------------------------------ stories

    fun insertStory(story: SavedStory): Long {
        val values = ContentValues().apply {
            put("created_at", story.createdAt)
            put("title", story.title)
            put("body", story.body)
            put("prompt", story.prompt)
            put("length", story.length)
            put("difficulty", story.difficulty)
        }
        return writableDatabase.insert("story", null, values)
    }

    /** Newest first. */
    fun stories(): List<SavedStory> = storyRows(null, null)

    fun story(id: Long): SavedStory? = storyRows("id = ?", arrayOf(id.toString())).firstOrNull()

    fun deleteStory(id: Long) {
        writableDatabase.delete("story", "id = ?", arrayOf(id.toString()))
    }

    private fun storyRows(where: String?, args: Array<String>?): List<SavedStory> =
        readableDatabase.rawQuery(
            "SELECT id, created_at, title, body, prompt, length, difficulty FROM story" +
                (where?.let { " WHERE $it" } ?: "") + " ORDER BY created_at DESC",
            args,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(SavedStory(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6)))
                }
            }
        }

    // ------------------------------------------------------------ cards

    fun cards(where: String, args: Array<String>, orderBy: String, limit: Int? = null): List<Card> =
        readableDatabase.rawQuery(
            "SELECT $CARD_COLUMNS FROM card WHERE $where ORDER BY $orderBy" + (limit?.let { " LIMIT $it" } ?: ""),
            args,
        ).use { c -> buildList { while (c.moveToNext()) add(card(c)) } }

    fun card(id: Long): Card? = cards("id = ?", arrayOf(id.toString()), "id").firstOrNull()

    fun wordIdsWithCards(direction: Direction): Set<Long> =
        readableDatabase.rawQuery(
            "SELECT word_id FROM card WHERE direction = ?",
            arrayOf(direction.value.toString()),
        ).use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }

    fun insertNewCard(
        db: SQLiteDatabase,
        wordId: Long,
        direction: Direction,
        sortKey: Long,
        suspended: Boolean = false,
    ): Long {
        val values = ContentValues().apply {
            put("suspended", if (suspended) 1 else 0)
            put("word_id", wordId)
            put("direction", direction.value)
            put("state", CardState.NEW.value)
            put("due_at", 0L)
            put("reps", 0)
            put("lapses", 0)
            put("sort_key", sortKey)
        }
        return db.insertWithOnConflict("card", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun updateCard(db: SQLiteDatabase, card: Card) {
        val values = ContentValues().apply {
            put("state", card.state.value)
            putNullable("step", card.step)
            putNullable("stability", card.stability)
            putNullable("difficulty", card.difficulty)
            put("due_at", card.dueAt)
            putNullable("last_review_at", card.lastReviewAt)
            put("reps", card.reps)
            put("lapses", card.lapses)
            putNullable("introduced_day", card.introducedDay)
        }
        db.update("card", values, "id = ?", arrayOf(card.id.toString()))
    }

    fun setSortKey(db: SQLiteDatabase, id: Long, sortKey: Long) {
        db.update("card", ContentValues().apply { put("sort_key", sortKey) }, "id = ?", arrayOf(id.toString()))
    }

    fun setSuspended(db: SQLiteDatabase, wordId: Long, suspended: Boolean) {
        val values = ContentValues().apply { put("suspended", if (suspended) 1 else 0) }
        db.update("card", values, "word_id = ?", arrayOf(wordId.toString()))
    }

    fun isSuspended(wordId: Long): Boolean =
        count("SELECT COUNT(*) FROM card WHERE word_id = ? AND suspended = 1", wordId) > 0

    /** Recognition state, suspension and mastery for every word with a card. */
    fun wordStatuses(): Map<Long, WordStatus> = statuses(Direction.RECOGNITION)

    /** The same for kana, keyed by kana unit id. */
    fun kanaStatuses(): Map<Long, WordStatus> = statuses(Direction.KANA)

    private fun statuses(direction: Direction): Map<Long, WordStatus> =
        readableDatabase.rawQuery(
            """
            SELECT c.word_id, c.state, c.suspended, COALESCE(p.mastery, 0)
            FROM card c LEFT JOIN word_progress p ON p.word_id = c.word_id
            WHERE c.direction = ?
            """,
            arrayOf(direction.value.toString()),
        ).use { c ->
            buildMap {
                while (c.moveToNext()) {
                    put(c.getLong(0), WordStatus(CardState.of(c.getInt(1)), c.getInt(2) != 0, c.getInt(3)))
                }
            }
        }

    /** Removes every card, the mastery and the history of a word. */
    fun deleteWord(db: SQLiteDatabase, wordId: Long) {
        val id = arrayOf(wordId.toString())
        db.delete("review_log", "card_id IN (SELECT id FROM card WHERE word_id = ?)", id)
        db.delete("card", "word_id = ?", id)
        db.delete("word_progress", "word_id = ?", id)
    }

    fun logs(wordId: Long): List<Pair<Direction, ReviewEntry>> =
        readableDatabase.rawQuery(
            """
            SELECT c.direction, l.reviewed_at, l.correct, l.mastery_after
            FROM review_log l JOIN card c ON c.id = l.card_id
            WHERE c.word_id = ? ORDER BY l.reviewed_at DESC
            """,
            arrayOf(wordId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Direction.of(c.getInt(0)) to ReviewEntry(c.getLong(1), c.getInt(2) != 0, c.getInt(3)))
                }
            }
        }

    fun deleteCard(db: SQLiteDatabase, id: Long) {
        db.delete("card", "id = ?", arrayOf(id.toString()))
    }

    fun count(sql: String, vararg args: Any): Int =
        readableDatabase.rawQuery(sql, args.map { it.toString() }.toTypedArray()).use {
            it.moveToFirst()
            it.getInt(0)
        }

    // ------------------------------------------------------------ mastery

    fun progress(wordId: Long): WordProgress =
        readableDatabase.rawQuery(
            "SELECT mastery, production_unlocked FROM word_progress WHERE word_id = ?",
            arrayOf(wordId.toString()),
        ).use { c ->
            if (c.moveToFirst()) WordProgress(wordId, c.getInt(0), c.getInt(1) != 0) else WordProgress(wordId)
        }

    /** The words (not kana) at [mastery] or higher. */
    fun wordsAtMastery(mastery: Int): Set<Long> =
        readableDatabase.rawQuery(
            "SELECT word_id FROM word_progress WHERE mastery >= ? AND word_id > 0",
            arrayOf(mastery.toString()),
        ).use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }

    fun saveProgress(db: SQLiteDatabase, progress: WordProgress) {
        val values = ContentValues().apply {
            put("word_id", progress.wordId)
            put("mastery", progress.mastery)
            put("production_unlocked", if (progress.productionUnlocked) 1 else 0)
        }
        db.insertWithOnConflict("word_progress", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteProgress(db: SQLiteDatabase, wordId: Long) {
        db.delete("word_progress", "word_id = ?", arrayOf(wordId.toString()))
    }

    // ------------------------------------------------------------ history

    fun insertLog(db: SQLiteDatabase, log: ReviewLog): Long {
        val values = ContentValues().apply {
            put("card_id", log.cardId)
            put("reviewed_at", log.reviewedAt)
            put("day", log.day)
            put("correct", if (log.correct) 1 else 0)
            put("state_before", log.stateBefore.value)
            put("state_after", log.stateAfter.value)
            putNullable("elapsed_days", log.elapsedDays)
            put("stability", log.stabilityAfter)
            put("difficulty", log.difficultyAfter)
            put("due_at", log.dueAt)
            put("mastery_before", log.masteryBefore)
            put("mastery_after", log.masteryAfter)
        }
        return db.insert("review_log", null, values)
    }

    fun deleteLog(db: SQLiteDatabase, id: Long) {
        db.delete("review_log", "id = ?", arrayOf(id.toString()))
    }

    /** Answers per study day, from [fromDay] on. */
    fun answersPerDay(fromDay: Long): Map<Long, Int> =
        readableDatabase.rawQuery(
            "SELECT day, COUNT(*) FROM review_log WHERE day >= ? GROUP BY day",
            arrayOf(fromDay.toString()),
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) } }

    /** Answers and right answers per study day, from [fromDay] on. */
    fun answersAndRightPerDay(fromDay: Long): Map<Long, Pair<Int, Int>> =
        readableDatabase.rawQuery(
            "SELECT day, COUNT(*), SUM(correct) FROM review_log WHERE day >= ? GROUP BY day",
            arrayOf(fromDay.toString()),
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1) to c.getInt(2)) } }

    /** Right answers and all answers on cards that were in review (not still learning), from [fromDay] on. */
    fun reviewRecall(fromDay: Long): Pair<Int, Int> =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(correct), 0), COUNT(*) FROM review_log WHERE state_before = ? AND day >= ?",
            arrayOf(CardState.REVIEW.value.toString(), fromDay.toString()),
        ).use { c ->
            c.moveToFirst()
            c.getInt(0) to c.getInt(1)
        }

    /** When each review card not suspended is due, for those due before [before]. */
    fun reviewDueTimes(before: Long): List<Long> =
        readableDatabase.rawQuery(
            "SELECT due_at FROM card WHERE state = ? AND suspended = 0 AND due_at < ?",
            arrayOf(CardState.REVIEW.value.toString(), before.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }

    /**
     * Drop unstarted cards that were queued in study order, so the queue is
     * rebuilt in a new order. Words queued with Learn next, or suspended, stay.
     */
    fun deleteQueuedNewCards(db: SQLiteDatabase) {
        db.delete(
            "card",
            "state = ? AND direction = ? AND reps = 0 AND suspended = 0 AND sort_key >= 0",
            arrayOf(CardState.NEW.value.toString(), Direction.RECOGNITION.value.toString()),
        )
    }

    /** Kana units whose cards have finished learning, so their kana count as readable. */
    fun kanaInReview(): Set<Long> =
        readableDatabase.rawQuery(
            "SELECT word_id FROM card WHERE direction = ? AND state = ?",
            arrayOf(Direction.KANA.value.toString(), CardState.REVIEW.value.toString()),
        ).use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }

    /** Drop kana cards that haven't been shown yet, so the kana queue is rebuilt. */
    fun deleteUnstartedKana(db: SQLiteDatabase) {
        db.delete(
            "card",
            "state = ? AND direction = ? AND reps = 0",
            arrayOf(CardState.NEW.value.toString(), Direction.KANA.value.toString()),
        )
    }

    // ------------------------------------------------------------ backup

    /** Fold any write-ahead log into the main file, so the file alone is a complete copy. */
    fun checkpoint() {
        writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
    }

    /** Study days with at least one review, newest first. */
    fun studyDays(): List<Long> =
        readableDatabase.rawQuery("SELECT DISTINCT day FROM review_log ORDER BY day DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }

    // ------------------------------------------------------------ settings

    fun getValue(key: String): String? =
        readableDatabase.rawQuery("SELECT value FROM kv WHERE key = ?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun setValues(values: Map<String, String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((key, value) in values) {
                db.insertWithOnConflict(
                    "kv",
                    null,
                    ContentValues().apply {
                        put("key", key)
                        put("value", value)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    private fun card(c: Cursor) = Card(
        id = c.getLong(0),
        wordId = c.getLong(1),
        direction = Direction.of(c.getInt(2)),
        state = CardState.of(c.getInt(3)),
        step = if (c.isNull(4)) null else c.getInt(4),
        stability = if (c.isNull(5)) null else c.getDouble(5),
        difficulty = if (c.isNull(6)) null else c.getDouble(6),
        dueAt = c.getLong(7),
        lastReviewAt = if (c.isNull(8)) null else c.getLong(8),
        reps = c.getInt(9),
        lapses = c.getInt(10),
        introducedDay = if (c.isNull(11)) null else c.getLong(11),
    )

    private fun ContentValues.putNullable(key: String, value: Any?) {
        when (value) {
            null -> putNull(key)
            is Int -> put(key, value)
            is Long -> put(key, value)
            is Double -> put(key, value)
            else -> error("unsupported type for $key")
        }
    }

    companion object {
        const val NAME = "progress.db"
        const val VERSION = 4
        private const val CARD_COLUMNS =
            "id, word_id, direction, state, step, stability, difficulty, due_at, last_review_at, reps, lapses, introduced_day"
    }
}
