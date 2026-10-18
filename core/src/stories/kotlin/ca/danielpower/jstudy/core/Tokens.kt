package ca.danielpower.jstudy.core

/**
 * Repairs to tokens the tokenizer gets wrong in everyday speech, before the
 * reader puts them into words. Each is a pattern IPADIC misreads the same way
 * every time, so a story can be written naturally rather than around it.
 */
internal object Tokens {
    fun repair(tokens: List<Morpheme>): List<Morpheme> {
        val out = ArrayList<Morpheme>(tokens.size)
        for (t in tokens) out += word(t)
        return out
    }

    /** A token read as the wrong word. */
    private fun word(t: Morpheme): Morpheme = when {
        // 行っ(た) is 行く, "went", far more often than 行う, "carried out", which the tokenizer sometimes picks.
        t.base == "行う" && t.surface == "行っ" -> t.copy(base = "行く", reading = "いっ", type = "五段・カ行促音便")
        // 後 after の (仕事の後で) is あと; the tokenizer says のち.
        t.surface == "後" && t.pos2 == "非自立" -> t.copy(reading = "あと")
        else -> t
    }
}
