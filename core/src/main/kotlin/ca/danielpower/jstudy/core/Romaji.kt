package ca.danielpower.jstudy.core

/**
 * Romaji for kana you can't read yet, the way the reader shows it: a sound
 * over each kana, so it can drop away one kana at a time as they're learned.
 */
object Romaji {
    /**
     * [text] in kana units, each with its sound, or null for anything that
     * isn't kana. A small っ takes the next sound's first consonant (きって is
     * ki t te), ー the vowel before it (コーヒー is ko o hi i). In a particle
     * ([particle]), a final は is said "wa" and へ "e".
     */
    fun split(text: String, particle: Boolean = false): List<Pair<String, String?>> {
        val segments = KanaCourse.segments(text)
        val sounds = segments.map { KanaCourse.unit(it)?.takeIf { u -> !u.mark }?.romaji }.toMutableList()
        for (i in segments.indices) {
            when (segments[i]) {
                "っ", "ッ" -> sounds[i] = doubling(sounds.getOrNull(i + 1))
                "ー" -> sounds[i] = sounds.getOrNull(i - 1)?.lastOrNull { it in VOWELS }?.toString() ?: "-"
            }
        }
        if (particle) {
            when (segments.lastOrNull()) {
                "は" -> sounds[segments.lastIndex] = "wa"
                "へ" -> sounds[segments.lastIndex] = "e"
            }
        }
        return segments.zip(sounds)
    }

    /** [text] as one word of romaji, with anything that isn't kana left as it is: にほん is nihon. */
    fun of(text: String, particle: Boolean = false): String = split(text, particle).joinToString("") { (piece, sound) -> sound ?: piece }

    /** The consonant a small っ doubles before [next]: t for ch, nothing at the end of a word (an apostrophe shows the stop). */
    private fun doubling(next: String?): String = when {
        next == null || next.first() in VOWELS -> "'"
        next.startsWith("ch") -> "t"
        else -> next.first().toString()
    }

    private const val VOWELS = "aiueo"
}

/** A piece of a word as the reader draws it: its text, and the furigana or ([romaji]) romaji above it. */
data class RubyPiece(val base: String, val ruby: String?, val romaji: Boolean = false)

/**
 * [word]'s pieces with romaji over the kana in [unread] (kana not practised
 * yet), or null when it has none. Kana get a sound each; furigana over a
 * kanji turns to romaji as a whole if any of its kana is unread (日本 is
 * "nihon", never "niほん").
 */
fun romajiPieces(word: ReadingWord, unread: Set<String>): List<RubyPiece>? {
    if (unread.isEmpty()) return null
    val needs = word.ruby.any { piece -> KanaCourse.unitsIn(piece.ruby ?: piece.base).any { it.text in unread } }
    if (!needs) return null
    val out = ArrayList<RubyPiece>()
    word.ruby.forEachIndexed { n, piece ->
        val reading = piece.ruby
        if (reading != null) {
            out += if (KanaCourse.unitsIn(reading).any { it.text in unread }) RubyPiece(piece.base, Romaji.of(reading), romaji = true) else RubyPiece(piece.base, reading)
            return@forEachIndexed
        }
        val particle = word.kind == WordKind.GRAMMAR && n == word.ruby.lastIndex
        val plain = StringBuilder()
        for ((segment, sound) in Romaji.split(piece.base, particle)) {
            if (sound != null && segment in unread) {
                if (plain.isNotEmpty()) out += RubyPiece(plain.toString(), null).also { plain.clear() }
                out += RubyPiece(segment, sound, romaji = true)
            } else {
                plain.append(segment)
            }
        }
        if (plain.isNotEmpty()) out += RubyPiece(plain.toString(), null)
    }
    return out
}
