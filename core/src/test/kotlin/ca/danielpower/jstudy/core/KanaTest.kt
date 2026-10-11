package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KanaTest {
    @Test
    fun `converts hepburn and kunrei romaji`() {
        val cases = mapOf(
            "taberu" to "たべる",
            "shinbun" to "しんぶん",
            "sinbun" to "しんぶん",
            "kitte" to "きって",
            "gakkou" to "がっこう",
            "kyou" to "きょう",
            "chotto" to "ちょっと",
            "tsukue" to "つくえ",
            "konnichiha" to "こんにちは",
            "kon'ya" to "こんや",
            "onna" to "おんな",
            "ja" to "じゃ",
            "jitensha" to "じてんしゃ",
            "matcha" to "まっちゃ",
            "fuyu" to "ふゆ",
            "ko-hi-" to "こーひー",
            "hon" to "ほん",
            "wo" to "を",
        )
        for ((romaji, kana) in cases) assertEquals(kana, Kana.romajiToHiragana(romaji), romaji)
    }

    @Test
    fun `rejects non-romaji`() {
        assertNull(Kana.romajiToHiragana("食べる"))
        assertNull(Kana.romajiToHiragana("qq"))
        assertNull(Kana.romajiToHiragana("to eat"))
    }

    @Test
    fun `search variants cover both kana scripts`() {
        assertEquals(listOf("ko-hi-", "こーひー", "コーヒー"), Kana.searchVariants("ko-hi-"))
        assertEquals(listOf("コーヒー", "こーひー"), Kana.searchVariants("コーヒー"))
        assertTrue("eat" in Kana.searchVariants("eat"))
        assertEquals(listOf("食べる"), Kana.searchVariants("食べる"))
    }
}
