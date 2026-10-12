package ca.danielpower.jstudy.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import ca.danielpower.jstudy.core.Kana
import ca.danielpower.jstudy.core.Lexicon
import ca.danielpower.jstudy.core.LexiconMatch
import ca.danielpower.jstudy.core.VocabWord
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
    /** Int.MAX_VALUE for words that aren't on the JLPT lists. */
    val studyOrder: Int,
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

    /**
     * Words in study order that aren't in [exclude]. Levels from [startLevel]
     * up come first (N3 first means N3, N2, N1, then N5 and N4). The second
     * value is the word's place in that order, for sorting its card.
     * With [canRead], words whose kana spelling it rejects are skipped, so
     * new words wait until you can read them.
     */
    fun nextWords(
        exclude: Set<Long>,
        limit: Int,
        startLevel: Int = 5,
        canRead: ((kana: String) -> Boolean)? = null,
    ): List<Pair<Long, Int>> {
        if (limit <= 0) return emptyList()
        val out = ArrayList<Pair<Long, Int>>(limit)
        db.rawQuery(
            """
            SELECT id, study_order + CASE WHEN jlpt > ? THEN $LATER_LEVELS ELSE 0 END AS place, kana
            FROM word WHERE study_order IS NOT NULL ORDER BY place
            """,
            arrayOf(startLevel.toString()),
        ).use { c ->
            while (out.size < limit && c.moveToNext()) {
                val id = c.getLong(0)
                if (id in exclude || (canRead != null && !canRead(c.getString(2)))) continue
                out.add(id to c.getInt(1))
            }
        }
        return out
    }

    /** JLPT words below [startLevel] (easier levels), in study order. */
    fun wordsBelow(startLevel: Int): List<Long> =
        db.rawQuery(
            "SELECT id FROM word WHERE jlpt > ? AND study_order IS NOT NULL ORDER BY study_order",
            arrayOf(startLevel.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }

    /** Word count per JLPT level (5 for N5 down to 1 for N1). */
    fun levelCounts(): Map<Int, Int> =
        db.rawQuery("SELECT jlpt, COUNT(*) FROM word WHERE jlpt > 0 GROUP BY jlpt", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getInt(0), c.getInt(1)) }
        }

    /** JLPT level of each word, for the stats. */
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
            SELECT w.id, w.kanji, w.kana, w.usually_kana, w.furigana IS NOT NULL, w.jlpt, w.study_order,
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
                            gloss = c.getString(7)?.let { strings(it).joinToString("; ") }.orEmpty(),
                            jlpt = c.getInt(5),
                            studyOrder = if (c.isNull(6)) Int.MAX_VALUE else c.getInt(6),
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

    /**
     * The entry for a word spelled [text]. Among homographs, one with a kana
     * spelling matching [reading] wins, then JLPT words, then the more frequent.
     */
    override fun find(text: String, reading: String?): LexiconMatch? {
        data class Candidate(val id: Long, val kana: String, val jlpt: Int, val rank: Int)
        val candidates = db.rawQuery(
            """
            SELECT w.id, w.kana, w.jlpt, COALESCE(w.freq_rank, 1000000000)
            FROM form f JOIN word w ON w.id = f.word_id WHERE f.text = ?
            """,
            arrayOf(text),
        ).use { c ->
            buildList { while (c.moveToNext()) add(Candidate(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3))) }
        }.distinctBy { it.id }
        if (candidates.isEmpty()) return null
        val wanted = reading?.let(Kana::toHiragana)
        val readingMatches = if (wanted == null) emptySet() else candidates.filter { cand ->
            Kana.toHiragana(cand.kana) == wanted || forms(cand.id).any { Kana.isKana(it) && Kana.toHiragana(it) == wanted }
        }.map { it.id }.toSet()
        val best = candidates.minWith(
            compareBy<Candidate>({ it.id !in readingMatches }, { it.jlpt == 0 }, { it.rank }, { it.id }),
        )
        val bestReading = if (best.id in readingMatches) wanted!! else Kana.toHiragana(best.kana)
        return LexiconMatch(best.id, bestReading)
    }

    /** How the story writer should see each word: the spelling cards show, and its reading. */
    fun vocab(ids: Collection<Long>): List<VocabWord> =
        ids.chunked(500).flatMap { chunk ->
            db.rawQuery(
                "SELECT id, kanji, kana, usually_kana, furigana IS NOT NULL FROM word WHERE id IN (${chunk.joinToString(",")})",
                null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val kanji = if (c.isNull(1)) null else c.getString(1)
                        val kana = c.getString(2)
                        val showsKanji = kanji != null && c.getInt(3) == 0 && c.getInt(4) != 0
                        add(VocabWord(c.getLong(0), if (showsKanji) kanji!! else kana, kana))
                    }
                }
            }
        }

    fun level(id: Long): Int =
        db.rawQuery("SELECT jlpt FROM word WHERE id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getInt(0) else 0 }

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

        /** Added to the study order of words before the starting level, so they come last. */
        private const val LATER_LEVELS = 1_000_000
        private val PARENTHESES = Regex("\\([^)]*\\)")
    }
}
