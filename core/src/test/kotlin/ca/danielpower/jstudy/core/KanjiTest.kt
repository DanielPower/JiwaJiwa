package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KanjiTest {
    private fun kanji(literal: String, on: List<String>, kun: List<String>) =
        Kanji(literal, listOf("x"), on, kun, 1, null, 5, emptyList())

    // Readings as KANJIDIC2 has them.
    private val sei = kanji("生", listOf("セイ", "ショウ"), listOf("い.きる", "い.かす", "う.まれる", "うま.れる", "う.む", "なま", "なま-", "き"))
    private val gaku = kanji("学", listOf("ガク"), listOf("まな.ぶ"))
    private val kawa = kanji("川", listOf("セン"), listOf("かわ"))
    private val uke = kanji("受", listOf("ジュ"), listOf("う.ける", "-う.け", "う.かる"))
    private val hiku = kanji("引", listOf("イン"), listOf("ひ.く", "-ひ.き", "ひ.き-", "ひ.ける"))
    private val nichi = kanji("日", listOf("ニチ", "ジツ"), listOf("ひ", "-び", "-か"))

    @Test
    fun `finds kanji and the reading each takes from furigana`() {
        val uses = Kanjis.inWord(listOf("生" to "い", "きる" to null))
        assertEquals(listOf(KanjiInWord("生", "い", "きる")), uses)
        // A reading over several kanji belongs to none of them alone.
        assertEquals(
            listOf(KanjiInWord("今", null, ""), KanjiInWord("日", null, "")),
            Kanjis.inWord(listOf("今日" to "きょう")),
        )
        assertEquals(listOf("学", "生"), Kanjis.inText("学生々"))
    }

    @Test
    fun `matches on'yomi and kun'yomi`() {
        assertEquals("セイ", Kanjis.dictionaryReading(sei, "せい"))
        assertEquals("ショウ", Kanjis.dictionaryReading(sei, "しょう"))
        assertEquals("なま", Kanjis.dictionaryReading(sei, "なま"))
        assertEquals("ニチ", Kanjis.dictionaryReading(nichi, "にち"))
        assertNull(Kanjis.dictionaryReading(sei, "ほし"))
    }

    @Test
    fun `okurigana after the kanji picks the kun'yomi`() {
        assertEquals("い.きる", Kanjis.dictionaryReading(sei, "い", "きる"))
        assertEquals("い.かす", Kanjis.dictionaryReading(sei, "い", "かす"))
        // Conjugated.
        assertEquals("い.きる", Kanjis.dictionaryReading(sei, "い", "きて"))
        assertEquals("う.まれる", Kanjis.dictionaryReading(sei, "う", "まれる"))
        assertEquals("う.む", Kanjis.dictionaryReading(sei, "う", "む"))
    }

    @Test
    fun `allows sound changes inside compounds`() {
        assertEquals("かわ", Kanjis.dictionaryReading(kawa, "がわ"))
        assertEquals("ガク", Kanjis.dictionaryReading(gaku, "がっ"))
        // KANJIDIC lists び as a suffix reading of its own, and an exact match wins.
        assertEquals("-び", Kanjis.dictionaryReading(nichi, "び"))
        assertEquals("ひ", Kanjis.dictionaryReading(kanji("火", listOf("カ"), listOf("ひ")), "び"))
    }

    @Test
    fun `matches a verb stem written under the kanji`() {
        assertEquals("う.ける", Kanjis.dictionaryReading(uke, "うけ", "付"))
        assertEquals("ひ.く", Kanjis.dictionaryReading(hiku, "ひき", "出し"))
        assertEquals("ひ.く", Kanjis.dictionaryReading(hiku, "ひっ", "越す"))
    }
}
