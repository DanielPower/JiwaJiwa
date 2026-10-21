package ca.danielpower.jstudy.core

/**
 * Romaji for kana you can't read yet, the way the reader shows it: a sound
 * over each kana, so it can drop away one kana at a time as they're learned.
 */
object Romaji {
    /**
     * [text] in kana units, each with its sound, or null for anything that
     * isn't kana. A small っ takes the next sound's first consonant (きって is
     * ki t te), ー the vowel before it (コーヒー is ko o hi i). A は or へ at
     * one of [particles] (places in [text]) is the particle, said "wa" or "e".
     * [next] is the text straight after, in the same word or the next, so a
     * small っ at the end still doubles what follows (なっ before た is na t).
     */
    fun split(text: String, particles: Collection<Int> = particlesIn(text), next: String? = null): List<Pair<String, String?>> {
        val segments = KanaCourse.segments(text)
        val sounds = segments.map { KanaCourse.unit(it)?.takeIf { u -> !u.mark }?.romaji }.toMutableList()
        var at = 0
        for (i in segments.indices) {
            if (at in particles) {
                when (segments[i]) {
                    "は" -> sounds[i] = "wa"
                    "へ" -> sounds[i] = "e"
                }
            }
            at += segments[i].length
        }
        for (i in segments.indices) {
            when (segments[i]) {
                "っ", "ッ" -> sounds[i] = doubling(if (i < segments.lastIndex) sounds[i + 1] else next?.let(::firstSound))
                "ー" -> sounds[i] = sounds.getOrNull(i - 1)?.lastOrNull { it in VOWELS }?.toString() ?: "-"
            }
        }
        return segments.zip(sounds)
    }

    /**
     * [text] as one word of romaji, with anything that isn't kana left as it
     * is: にほん is nihon. ん before a vowel or y takes an apostrophe, so
     * こんや is kon'ya, not ko-nya. [particles] and [next] are as for [split].
     */
    fun of(text: String, particles: Collection<Int> = particlesIn(text), next: String? = null): String {
        val pieces = split(text, particles, next)
        val out = StringBuilder()
        pieces.forEachIndexed { i, (piece, sound) ->
            out.append(sound ?: piece)
            if (piece == "ん" || piece == "ン") {
                val after = if (i < pieces.lastIndex) pieces[i + 1].second else next?.let(::firstSound)
                if (after != null && (after.first() in VOWELS || after.first() == 'y')) out.append('\'')
            }
        }
        return out.toString()
    }

    /**
     * Where a word written all in kana ends in the topic particle, said "wa":
     * the greetings and set phrases the dictionary has as one word
     * (こんばんは, じつは). Words read in a story know their own particles
     * ([ReadingWord.particles]); this is for a word on its own, as on a card.
     */
    fun particlesIn(text: String): List<Int> = if (text in ENDS_IN_WA) listOf(text.lastIndex) else emptyList()

    private val ENDS_IN_WA = setOf("こんにちは", "こんばんは", "じつは", "では", "それでは", "とは", "または", "あるいは", "もしくは")

    /** The sound [text] starts with, if it starts with kana. */
    private fun firstSound(text: String): String? =
        KanaCourse.segments(text).firstOrNull()?.let { KanaCourse.unit(it) }?.takeIf { !it.mark }?.romaji

    /**
     * A grammar pattern's name read out, as its kana say it: ～た is ta, です / だ
     * desu / da. Its は and へ are particles, wa and e (～ては is tewa), but for
     * はず. Null when it has kanji, which have no reading here.
     */
    fun pattern(name: String): String? {
        if (name.any(Kanjis::isKanji)) return null
        val pieces = split(name.filter { it !in "～〜" })
        return pieces.mapIndexed { i, (piece, sound) ->
            when {
                piece == "は" && pieces.getOrNull(i + 1)?.first != "ず" -> "wa"
                piece == "へ" -> "e"
                else -> sound ?: piece
            }
        }.joinToString("")
    }

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
 * "nihon", never "niほん"). [next] is the text after the word, for a small
 * っ at its very end (言っ before ちゃ is i t).
 */
fun romajiPieces(word: ReadingWord, unread: Set<String>, next: String? = null): List<RubyPiece>? {
    if (unread.isEmpty()) return null
    val needs = word.ruby.any { piece -> KanaCourse.unitsIn(piece.ruby ?: piece.base).any { it.text in unread } }
    if (!needs) return null
    val out = ArrayList<RubyPiece>()
    var at = 0
    word.ruby.forEachIndexed { n, piece ->
        val start = at
        at += piece.base.length
        // What's said after this piece: the rest of the word, else the next word.
        val after = word.ruby.getOrNull(n + 1)?.let { it.ruby ?: it.base } ?: next
        val reading = piece.ruby
        if (reading != null) {
            out += if (KanaCourse.unitsIn(reading).any { it.text in unread }) RubyPiece(piece.base, Romaji.of(reading, emptyList(), after), romaji = true) else RubyPiece(piece.base, reading)
            return@forEachIndexed
        }
        val particles = word.particles.filter { it >= start && it < at }.map { it - start }
        val plain = StringBuilder()
        for ((segment, sound) in Romaji.split(piece.base, particles, after)) {
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
