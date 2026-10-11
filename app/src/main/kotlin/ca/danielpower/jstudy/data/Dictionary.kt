package ca.danielpower.jstudy.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
    }
}
