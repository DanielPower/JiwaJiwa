package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NarrationTest {
    private fun w(surface: String, reading: String = surface, kind: WordKind = WordKind.WORD, lemma: String = surface) =
        ReadingWord(surface, lemma, reading, kind, null, emptyList(), listOf(Ruby(surface, null)))

    private fun p(vararg surfaces: String) = surfaces.map { if (it.all { c -> c in "。！？「」…、" }) w(it, kind = WordKind.PUNCTUATION) else w(it) }

    @Test
    fun `reads each sentence as a line, with its speaker`() {
        val lines = Narration.lines(listOf(p("朝", "だ", "。", "コーヒー", "だ", "。"), p("「", "うん", "！", "」")), listOf(null, "ユキ"))
        assertEquals(listOf("朝だ。", "コーヒーだ。", "「うん！」"), lines.map { it.text })
        assertEquals(listOf(null, null, "ユキ"), lines.map { it.speaker })
        assertEquals(3..5, lines[1].words)
    }

    @Test
    fun `skips a sentence with nothing to say`() {
        val lines = Narration.lines(listOf(p("「", "…", "」"), p("猫", "。")), listOf("ユキ", null))
        assertEquals(listOf("猫。"), lines.map { it.text })
        assertEquals(0, Narration.lineOf(lines, 0, 1))
    }

    @Test
    fun `turns the engine's place in a line back into a word`() {
        val line = Narration.lines(listOf(p("コーヒー", "、", "飲む", "？")), listOf(null)).single()
        assertEquals(0, line.wordAt(2))
        assertEquals(2, line.wordAt(5))
        assertEquals(3, line.wordAt(7))
    }

    @Test
    fun `gives the engine the reading of words it might misread`() {
        assertEquals("きょう", Narration.spoken(w("今日", "きょう")))
        assertEquals("たなか", Narration.spoken(w("田中", "たなか", WordKind.NAME)))
        assertEquals("猫", Narration.spoken(w("猫", "ねこ")))
        assertEquals("なにか", Narration.spoken(w("何か", "なにか", lemma = "何")))
    }

    @Test
    fun `finds the line holding a word`() {
        val lines = Narration.lines(listOf(p("朝", "だ", "。", "猫", "だ", "。")), listOf(null))
        assertEquals(1, Narration.lineOf(lines, 0, 4))
    }
}

class VoicesTest {
    private val google = listOf("ja-jp-x-htm-local", "ja-jp-x-jab-local", "ja-jp-x-jac-local", "ja-jp-x-jad-local")
        .map { PhoneVoice(it, Voices.kindOf(it)) }

    @Test
    fun `knows Google's and Samsung's voices`() {
        assertEquals(VoiceKind.WOMAN, Voices.kindOf("ja-jp-x-jab-network"))
        assertEquals(VoiceKind.MAN, Voices.kindOf("ja-jp-x-jad-local"))
        assertEquals(VoiceKind.WOMAN, Voices.kindOf("ja-JP-SMTf00"))
        assertEquals(null, Voices.kindOf("something-else"))
    }

    @Test
    fun `names voices by kind`() {
        assertEquals(listOf("Woman 1", "Woman 2", "Man 1", "Man 2"), Voices.labels(google))
        assertEquals(listOf("Voice 1", "Voice 2"), Voices.labels(listOf(PhoneVoice("a", null), PhoneVoice("b", null))))
    }

    @Test
    fun `gives the cast voices of their kind, off the narrator's`() {
        val voices = Voices.assign(
            google, narrator = "ja-jp-x-htm-local",
            listOf("ユキ" to CastVoice(VoiceKind.WOMAN), "アレックス" to CastVoice(VoiceKind.MAN), "ケンタ" to CastVoice(VoiceKind.MAN)),
        )
        assertEquals("ja-jp-x-jab-local", voices.getValue("ユキ").name)
        assertEquals("ja-jp-x-jac-local", voices.getValue("アレックス").name)
        assertEquals("ja-jp-x-jad-local", voices.getValue("ケンタ").name)
    }

    @Test
    fun `tells apart two who share a voice and pitch`() {
        val voices = Voices.assign(
            google, narrator = null,
            listOf("a" to CastVoice(VoiceKind.WOMAN), "b" to CastVoice(VoiceKind.WOMAN), "c" to CastVoice(VoiceKind.WOMAN)),
        )
        assertEquals(voices.getValue("a").name, voices.getValue("c").name)
        assertNotEquals(voices.getValue("a").pitch, voices.getValue("c").pitch)
    }

    @Test
    fun `raises a child's pitch, and a woman's in a voice of unknown kind`() {
        val unknown = listOf(PhoneVoice("x", null))
        val voices = Voices.assign(unknown, null, listOf("子供" to Voices.forLabel("子供"), "女の人" to Voices.forLabel("女の人"), "男の人" to Voices.forLabel("男の人")))
        assert(voices.getValue("子供").pitch > voices.getValue("女の人").pitch)
        assert(voices.getValue("女の人").pitch > voices.getValue("男の人").pitch)
    }

    @Test
    fun `voices someone unnamed by their label`() {
        assertEquals(VoiceKind.CHILD, Voices.forLabel("子供").kind)
        assertEquals(VoiceKind.WOMAN, Voices.forLabel("ミホの母").kind)
        assertEquals(VoiceKind.MAN, Voices.forLabel("男の人").kind)
        assertEquals(Voices.forLabel("店員"), Voices.forLabel("店員"))
    }

    @Test
    fun `keeps the narrator's choice while the phone has it`() {
        assertEquals("ja-jp-x-jab-local", Voices.narrator(google, "ja-jp-x-jab-local").name)
        assertEquals("ja-jp-x-htm-local", Voices.narrator(google, "gone").name)
        assertEquals(null, Voices.narrator(emptyList(), null).name)
    }
}
