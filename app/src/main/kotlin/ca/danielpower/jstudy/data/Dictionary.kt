package ca.danielpower.jstudy.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import ca.danielpower.jstudy.core.Kana
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
    val studyOrder: Int,
)

/**
 * The read-only word database built by tools/dictionary and shipped in the
 * APK. It is copied to app storage when the app is installed or updated.
 */
class Dictionary(context: Context) {
    private val db: SQLiteDatabase

    init {
        val target = context.getDatabasePath(NAME)
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
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

    /** Words in study order that aren't in [exclude], starting at study position [from]. */
    fun nextWords(exclude: Set<Long>, limit: Int, from: Int = 0): List<Pair<Long, Int>> {
        if (limit <= 0) return emptyList()
        val out = ArrayList<Pair<Long, Int>>(limit)
        db.rawQuery(
            "SELECT id, study_order FROM word WHERE study_order >= ? ORDER BY study_order",
            arrayOf(from.toString()),
        ).use { c ->
            while (out.size < limit && c.moveToNext()) {
                val id = c.getLong(0)
                if (id !in exclude) out.add(id to c.getInt(1))
            }
        }
        return out
    }

    /** Every word in study order, for the dictionary list. */
    fun entries(): List<Entry> =
        db.rawQuery(
            """
            SELECT w.id, w.kanji, w.kana, w.usually_kana, w.furigana IS NOT NULL, w.jlpt, w.study_order,
                (SELECT gloss FROM sense s WHERE s.word_id = w.id ORDER BY s.ord LIMIT 1)
            FROM word w ORDER BY w.study_order
            """,
            null,
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
                            gloss = c.getString(7)?.let { strings(it).joinToString("; ") }.orEmpty(),
                            jlpt = c.getInt(5),
                            studyOrder = c.getInt(6),
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

    fun wordCount(): Int = db.rawQuery("SELECT COUNT(*) FROM word", null).use { it.moveToFirst(); it.getInt(0) }

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
