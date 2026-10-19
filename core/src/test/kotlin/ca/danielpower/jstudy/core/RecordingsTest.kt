package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RecordingsTest {
    private val voices = RecordedVoices(
        "s2.1-pro", "narrator", mapOf("ユキ" to "yuki", "ケンタ" to ""),
        mapOf(VoiceKind.WOMAN to "woman", VoiceKind.MAN to "man", VoiceKind.CHILD to ""),
    )
    private val cast = listOf(
        CastMember("ユキ", "", CastVoice(VoiceKind.WOMAN)),
        CastMember("ケンタ", "", CastVoice(VoiceKind.MAN)),
    ).associateBy { it.name }

    @Test
    fun `names a clip by what's said, in which voice, by which model`() {
        val key = Recordings.key("猫だ。", "yuki", "s2.1-pro")
        assertEquals(key, Recordings.key("猫だ。", "yuki", "s2.1-pro"))
        assertEquals(20, key.length)
        assertNotEquals(key, Recordings.key("猫だ！", "yuki", "s2.1-pro"))
        assertNotEquals(key, Recordings.key("猫だ。", "kenta", "s2.1-pro"))
        assertNotEquals(key, Recordings.key("猫だ。", "yuki", "s1"))
        assertEquals("audio/$key.opus", Recordings.file(key))
    }

    @Test
    fun `records each speaker in their own voice, or their kind's`() {
        assertEquals("narrator", Recordings.voice(null, voices, cast))
        assertEquals("story", Recordings.voice(null, voices, cast, narrator = "story"))
        assertEquals("yuki", Recordings.voice("ユキ", voices, cast))
        assertEquals("man", Recordings.voice("ケンタ", voices, cast))
        assertEquals("man", Recordings.voice("男の人", voices, cast))
        assertEquals(null, Recordings.voice("子供", voices, cast))
    }

    @Test
    fun `records nothing without a model`() {
        val line = NarrationLine(0, 0..1, "猫だ。", listOf(0, 1), null)
        assertEquals(listOf(null), Recordings.keys(listOf(line), voices.copy(model = ""), cast))
        assertEquals(listOf(Recordings.key("猫だ。", "narrator", "s2.1-pro")), Recordings.keys(listOf(line), voices, cast))
    }
}
