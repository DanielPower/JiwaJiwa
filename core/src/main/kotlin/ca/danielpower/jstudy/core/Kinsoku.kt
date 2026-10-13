package ca.danielpower.jstudy.core

/** The usual Japanese line-breaking rules (kinsoku shori), shared by both writing directions. */
internal object Kinsoku {
    /** May hang past the end of a full vertical column (burasage). */
    val HANGING = setOf("、", "。", "，", "．")

    /** Never start a line: closing brackets and punctuation belong with the word before them. */
    val CLOSING = setOf("、", "。", "，", "．", "」", "』", "）", "】", "〕", "！", "？", "!", "?", "…", "‥")

    /** Never end a line. */
    val OPENING = setOf("「", "『", "（", "【", "〔")
}
