package ca.danielpower.jstudy.core

/** Romaji to kana conversion and kana normalisation for dictionary search. */
object Kana {
    private val table: Map<String, String> = buildMap {
        val rows = listOf(
            "" to listOf("あ", "い", "う", "え", "お"),
            "k" to listOf("か", "き", "く", "け", "こ"),
            "g" to listOf("が", "ぎ", "ぐ", "げ", "ご"),
            "s" to listOf("さ", "し", "す", "せ", "そ"),
            "z" to listOf("ざ", "じ", "ず", "ぜ", "ぞ"),
            "t" to listOf("た", "ち", "つ", "て", "と"),
            "d" to listOf("だ", "ぢ", "づ", "で", "ど"),
            "n" to listOf("な", "に", "ぬ", "ね", "の"),
            "h" to listOf("は", "ひ", "ふ", "へ", "ほ"),
            "b" to listOf("ば", "び", "ぶ", "べ", "ぼ"),
            "p" to listOf("ぱ", "ぴ", "ぷ", "ぺ", "ぽ"),
            "m" to listOf("ま", "み", "む", "め", "も"),
            "y" to listOf("や", "い", "ゆ", "いぇ", "よ"),
            "r" to listOf("ら", "り", "る", "れ", "ろ"),
            "w" to listOf("わ", "うぃ", "う", "うぇ", "を"),
            "v" to listOf("ゔぁ", "ゔぃ", "ゔ", "ゔぇ", "ゔぉ"),
        )
        val vowels = "aiueo"
        for ((consonant, kana) in rows) {
            for (i in vowels.indices) put(consonant + vowels[i], kana[i])
        }
        // Hepburn spellings and other common variants.
        put("shi", "し"); put("chi", "ち"); put("tsu", "つ"); put("fu", "ふ"); put("ji", "じ")
        put("fa", "ふぁ"); put("fi", "ふぃ"); put("fe", "ふぇ"); put("fo", "ふぉ")
        put("ti", "ち"); put("tu", "つ"); put("si", "し"); put("zi", "じ"); put("hu", "ふ"); put("di", "ぢ"); put("du", "づ")
        put("je", "じぇ"); put("che", "ちぇ"); put("she", "しぇ")
        put("thi", "てぃ"); put("dhi", "でぃ")
        val small = mapOf('a' to "ゃ", 'u' to "ゅ", 'o' to "ょ")
        val yoon = mapOf(
            "ky" to "き", "gy" to "ぎ", "sy" to "し", "sh" to "し", "zy" to "じ", "j" to "じ", "jy" to "じ",
            "ty" to "ち", "ch" to "ち", "cy" to "ち", "dy" to "ぢ", "ny" to "に", "hy" to "ひ", "by" to "び",
            "py" to "ぴ", "my" to "み", "ry" to "り",
        )
        for ((prefix, base) in yoon) for ((v, s) in small) put(prefix + v, base + s)
        put("nn", "ん"); put("n'", "ん")
        put("xa", "ぁ"); put("xi", "ぃ"); put("xu", "ぅ"); put("xe", "ぇ"); put("xo", "ぉ")
        put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ"); put("xtu", "っ"); put("xtsu", "っ")
        put("-", "ー")
    }

    private val maxKey = table.keys.maxOf { it.length }

    /** Hiragana for a romaji string, or null if it isn't romaji. */
    fun romajiToHiragana(input: String): String? {
        val s = input.lowercase().trim()
        if (s.isEmpty() || s.any { !(it in 'a'..'z' || it == '-' || it == '\'') }) return null
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            // Doubled consonant: っ (but "nn" is ん).
            if (i + 1 < s.length && c == s[i + 1] && c !in "aiueon-'") {
                out.append('っ')
                i++
                continue
            }
            // "nn" before a vowel is ん plus an n-syllable, as in "konnichiha" and "onna".
            if (c == 'n' && i + 2 < s.length && s[i + 1] == 'n' && s[i + 2] in "aiueoy") {
                out.append('ん')
                i++
                continue
            }
            // "tch" as in "matcha".
            if (c == 't' && s.startsWith("tch", i)) {
                out.append('っ')
                i++
                continue
            }
            var matched = false
            for (len in minOf(maxKey, s.length - i) downTo 1) {
                val kana = table[s.substring(i, i + len)] ?: continue
                out.append(kana)
                i += len
                matched = true
                break
            }
            if (matched) continue
            // A lone n before a consonant or at the end is ん.
            if (c == 'n') {
                out.append('ん')
                i++
                continue
            }
            return null
        }
        return out.toString()
    }

    fun isKana(s: String): Boolean = s.isNotEmpty() && s.all { it in 'ぁ'..'ゖ' || it in 'ァ'..'ヺ' || it == 'ー' }

    fun toKatakana(s: String): String = buildString {
        for (c in s) append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
    }

    fun toHiragana(s: String): String = buildString {
        for (c in s) append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
    }

    /**
     * Spellings to look up for a search query: the query itself, and for
     * romaji or kana input, its hiragana and katakana forms.
     */
    fun searchVariants(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val hiragana = romajiToHiragana(q) ?: toHiragana(q).takeIf(::isKana) ?: return listOf(q)
        return listOf(q, hiragana, toKatakana(hiragana)).distinct()
    }
}
