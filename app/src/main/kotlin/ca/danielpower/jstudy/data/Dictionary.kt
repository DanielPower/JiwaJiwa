package ca.danielpower.jstudy.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import ca.danielpower.jstudy.core.HOMOGRAPHS_SQL
import ca.danielpower.jstudy.core.Homograph
import ca.danielpower.jstudy.core.Kana
import ca.danielpower.jstudy.core.Kanji
import ca.danielpower.jstudy.core.Lexicon
import ca.danielpower.jstudy.core.LexiconMatch
import ca.danielpower.jstudy.core.SenseRow
import ca.danielpower.jstudy.core.VERBS_SQL
import ca.danielpower.jstudy.core.VerbEntry
import ca.danielpower.jstudy.core.bestHomograph
import ca.danielpower.jstudy.core.verbEntries
import org.json.JSONArray
import java.io.File

/** One piece of a spelling: base text and the reading above it (null for kana). */
data class FuriganaSegment(val base: String, val ruby: String?)

data class Sense(val partsOfSpeech: List<String>, val glosses: List<String>, val info: String?)

data class Word(
    val id: Long,
    val kanji: String?,
    val kana: String,
    val usuallyKana: Boolean,
    val furigana: List<FuriganaSegment>?,
    /** 5 to 1, or 0 for a word that isn't on the JLPT lists. */
    val jlpt: Int,
    val senses: List<Sense>,
) {
    /** Shown in kanji, with furigana available. */
    val showsKanji get() = kanji != null && !usuallyKana && furigana != null

    /** The spelling shown on cards. */
    val display get() = if (showsKanji) kanji!! else kana
}

/** A dictionary list row. */
data class Entry(
    val id: Long,
    /** The spelling shown on cards. */
    val display: String,
    /** The reading for a kanji spelling, or the occasional kanji for a kana word. */
    val secondary: String?,
    val gloss: String,
    val jlpt: Int,
)

/** "N5" to "N1", or "Other" for a word that isn't on the JLPT lists. */
fun levelLabel(jlpt: Int) = if (jlpt in 1..5) "N$jlpt" else "Other"

/**
 * The read-only word database built by tools/dictionary and shipped in the
 * APK. It is copied to app storage when the app is installed or updated.
 *
 * It holds every JMdict entry. The JLPT words have a level and a place in
 * the study order; the rest (level 0) are there to look up while reading.
 */
class Dictionary(context: Context) : Lexicon {
    private val db: SQLiteDatabase

    /** Changes whenever the app, and so possibly the dictionary, is updated. */
    val version: Long = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

    init {
        val target = context.getDatabasePath(NAME)
        val installedAt = version
        val prefs = context.getSharedPreferences("dictionary", Context.MODE_PRIVATE)
        if (!target.exists() || prefs.getLong("copied_for", -1) != installedAt) {
            copyFromAssets(context, target)
            prefs.edit().putLong("copied_for", installedAt).apply()
        }
        db = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun copyFromAssets(context: Context, target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        context.assets.open("databases/$NAME").use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "could not install the dictionary" }
        }
    }

    fun word(id: Long): Word? {
        val word = db.rawQuery(
            "SELECT id, kanji, kana, usually_kana, furigana, jlpt FROM word WHERE id = ?",
            arrayOf(id.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return null
            Word(
                id = c.getLong(0),
                kanji = if (c.isNull(1)) null else c.getString(1),
                kana = c.getString(2),
                usuallyKana = c.getInt(3) != 0,
                furigana = if (c.isNull(4)) null else parseFurigana(c.getString(4)),
                jlpt = c.getInt(5),
                senses = emptyList(),
            )
        }
        val senses = db.rawQuery(
            "SELECT pos, gloss, info FROM sense WHERE word_id = ? ORDER BY ord",
            arrayOf(id.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Sense(
                            partsOfSpeech = strings(c.getString(0)),
                            glosses = strings(c.getString(1)),
                            info = if (c.isNull(2)) null else c.getString(2),
                        ),
                    )
                }
            }
        }
        return word.copy(senses = senses)
    }

    /** Word count per JLPT level (5 for N5 down to 1 for N1). */
    fun levelCounts(): Map<Int, Int> =
        db.rawQuery("SELECT jlpt, COUNT(*) FROM word WHERE jlpt > 0 GROUP BY jlpt", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getInt(0), c.getInt(1)) }
        }

    /** JLPT level of each word. */
    fun levels(): Map<Long, Int> =
        db.rawQuery("SELECT id, jlpt FROM word WHERE jlpt > 0", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) }
        }

    /** Every JLPT word in study order, for the dictionary list. */
    fun entries(): List<Entry> = entryRows("w.jlpt > 0", emptyArray())

    /** List rows for particular words, e.g. search hits that aren't on the JLPT lists. */
    fun entries(ids: Collection<Long>): List<Entry> =
        ids.chunked(500).flatMap { chunk -> entryRows("w.id IN (${chunk.joinToString(",")})", emptyArray()) }

    private fun entryRows(where: String, args: Array<String>): List<Entry> =
        db.rawQuery(
            """
            SELECT w.id, w.kanji, w.kana, w.usually_kana, w.furigana IS NOT NULL, w.jlpt,
                (SELECT gloss FROM sense s WHERE s.word_id = w.id ORDER BY s.ord LIMIT 1)
            FROM word w WHERE $where ORDER BY w.study_order IS NULL, w.study_order, w.id
            """,
            args,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val kanji = if (c.isNull(1)) null else c.getString(1)
                    val kana = c.getString(2)
                    val showsKanji = kanji != null && c.getInt(3) == 0 && c.getInt(4) != 0
                    add(
                        Entry(
                            id = c.getLong(0),
                            display = if (showsKanji) kanji!! else kana,
                            secondary = if (showsKanji) kana else kanji,
                            gloss = c.getString(6)?.let { strings(it).joinToString("; ") }.orEmpty(),
                            jlpt = c.getInt(5),
                        ),
                    )
                }
            }
        }

    /**
     * Words matching [query] with a rank for each, lower is better: an exact
     * spelling, then a spelling that starts with it, then one containing it,
     * then English meanings. Romaji is matched as hiragana and katakana too.
     */
    fun search(query: String): Map<Long, Int> {
        val q = query.trim()
        if (q.isEmpty()) return emptyMap()
        val ranks = HashMap<Long, Int>()
        fun hit(id: Long, rank: Int) {
            ranks.merge(id, rank) { a, b -> minOf(a, b) }
        }
        for (variant in Kana.searchVariants(q)) {
            db.rawQuery(
                "SELECT word_id, text FROM form WHERE text LIKE ? ESCAPE '\\'",
                arrayOf("%" + escapeLike(variant) + "%"),
            ).use { c ->
                while (c.moveToNext()) {
                    val text = c.getString(1)
                    hit(c.getLong(0), if (text == variant) 0 else if (text.startsWith(variant)) 1 else 2)
                }
            }
        }
        if (q.any { it in 'a'..'z' || it in 'A'..'Z' }) {
            val english = q.lowercase()
            val word = Regex("(^|[^a-z])" + Regex.escape(english) + "($|[^a-z])")
            db.rawQuery(
                "SELECT word_id, ord, gloss FROM sense WHERE gloss LIKE ? ESCAPE '\\'",
                arrayOf("%" + escapeLike(english) + "%"),
            ).use { c ->
                while (c.moveToNext()) {
                    val first = c.getInt(1) == 0
                    for (gloss in strings(c.getString(2))) {
                        val g = gloss.lowercase().replace(PARENTHESES, "").trim()
                        val rank = when {
                            g == english || g == "to $english" -> if (first) 3 else 4
                            word.containsMatchIn(g) -> if (first) 5 else 6
                            english in g -> 7
                            else -> continue
                        }
                        hit(c.getLong(0), rank)
                    }
                }
            }
        }
        return ranks
    }

    /** Every spelling of a word, kanji first. */
    fun forms(id: Long): List<String> =
        db.rawQuery("SELECT text FROM form WHERE word_id = ? ORDER BY is_kana, ord", arrayOf(id.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    private fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    fun wordIds(): Set<Long> =
        db.rawQuery("SELECT id FROM word", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getLong(0)) }
        }

    /** Words on the JLPT lists. */
    fun wordCount(): Int = db.rawQuery("SELECT COUNT(*) FROM word WHERE jlpt > 0", null).use { it.moveToFirst(); it.getInt(0) }

    /** The entry for a word spelled [text] (core [bestHomograph]). */
    override fun find(text: String, reading: String?): LexiconMatch? {
        val homographs = db.rawQuery(HOMOGRAPHS_SQL, arrayOf(text)).use { c ->
            buildList { while (c.moveToNext()) add(Homograph(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3))) }
        }
        return bestHomograph(homographs, reading) { forms(it) }
    }

    override fun verbs(text: String): List<VerbEntry> = verbEntries(
        db.rawQuery(VERBS_SQL, arrayOf(text)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(SenseRow(c.getLong(0), c.getString(1), c.getInt(2) == 1, c.getString(3), c.getString(4), if (c.isNull(5)) null else c.getString(5)))
                }
            }
        },
    )

    /** A kanji's entry, or null for a character KANJIDIC2 doesn't have. */
    fun kanji(literal: String): Kanji? = kanjiRows("literal = ?", arrayOf(literal)).firstOrNull()

    /** Entries for several kanji, in the order asked for. */
    fun kanji(literals: List<String>): List<Kanji> {
        if (literals.isEmpty()) return emptyList()
        val found = kanjiRows("literal IN (${literals.joinToString(",") { "?" }})", literals.toTypedArray())
            .associateBy { it.literal }
        return literals.mapNotNull { found[it] }
    }

    private fun kanjiRows(where: String, args: Array<String>): List<Kanji> =
        db.rawQuery(
            "SELECT literal, meanings, onyomi, kunyomi, strokes, grade, jlpt, stroke_paths FROM kanji WHERE $where",
            args,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Kanji(
                            literal = c.getString(0),
                            meanings = strings(c.getString(1)),
                            onyomi = strings(c.getString(2)),
                            kunyomi = strings(c.getString(3)),
                            strokeCount = c.getInt(4),
                            grade = if (c.isNull(5)) null else c.getInt(5),
                            jlpt = c.getInt(6),
                            strokes = if (c.isNull(7)) emptyList() else strings(c.getString(7)),
                        ),
                    )
                }
            }
        }

    /** Furigana for the words among [ids] that are shown in kanji. */
    fun kanjiSpellings(ids: Collection<Long>): Map<Long, List<Pair<String, String?>>> =
        ids.chunked(500).flatMap { chunk ->
            db.rawQuery(
                "SELECT id, furigana FROM word WHERE id IN (${chunk.joinToString(",")}) AND usually_kana = 0 " +
                    "AND kanji IS NOT NULL AND furigana IS NOT NULL",
                null,
            ).use { c ->
                buildList { while (c.moveToNext()) add(c.getLong(0) to parseFurigana(c.getString(1)).map { it.base to it.ruby }) }
            }
        }.toMap()

    /**
     * Words shown with [kanji] in their spelling: JLPT words in study order,
     * then the rest by frequency. It reads the whole word table, so call it
     * for one page, not a list.
     */
    fun wordsWithKanji(kanji: String, limit: Int): List<Entry> {
        val ids = db.rawQuery(
            """
            SELECT id FROM word
            WHERE kanji LIKE ? ESCAPE '\' AND usually_kana = 0 AND furigana IS NOT NULL
            ORDER BY study_order IS NULL, study_order, freq_rank IS NULL, freq_rank, id LIMIT ?
            """,
            arrayOf("%" + escapeLike(kanji) + "%", limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
        val rows = entries(ids).associateBy { it.id }
        return ids.mapNotNull { rows[it] }
    }

    fun partOfSpeechNames(): Map<String, String> =
        db.rawQuery("SELECT code, description FROM tag", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }

    fun meta(key: String): String? =
        db.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun strings(json: String): List<String> {
        val array = JSONArray(json)
        return List(array.length()) { array.getString(it) }
    }

    private fun parseFurigana(json: String): List<FuriganaSegment> {
        val array = JSONArray(json)
        return List(array.length()) {
            val seg = array.getJSONArray(it)
            FuriganaSegment(seg.getString(0), if (seg.isNull(1)) null else seg.getString(1))
        }
    }

    companion object {
        const val NAME = "dictionary.db"

        private val PARENTHESES = Regex("\\([^)]*\\)")
    }
}
