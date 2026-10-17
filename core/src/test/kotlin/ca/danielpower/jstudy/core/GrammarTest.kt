package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrammarTest {
    private val reader = Reader { _, _ -> null }

    /** The grammar points in [text], by id, in order. */
    private fun points(text: String): List<String> =
        reader.words(text).flatMap { it.grammar }.map { it.point.id }.distinct()

    @Test
    fun `every point's own example sentence shows that point`() {
        val missing = Grammar.points.filter { it.id !in points(it.example) }
        assertTrue(missing.isEmpty(), "examples without their point: " + missing.joinToString { "${it.id} ${it.example} -> ${points(it.example)}" })
    }

    @Test
    fun `points have an id, a level and some text`() {
        assertEquals(Grammar.points.size, Grammar.points.map { it.id }.toSet().size)
        for (p in Grammar.points) {
            assertTrue(p.level in 1..5, p.id)
            assertTrue(listOf(p.pattern, p.meaning, p.note, p.example, p.exampleMeaning).none { it.isBlank() }, p.id)
        }
    }

    @Test
    fun `a pattern covers its particles rather than explaining them one by one`() {
        assertEquals(listOf("wo", "te-wa-ikenai"), points("写真を撮ってはいけません。"))
        assertEquals(listOf("ta-koto-ga-aru"), points("行ったことがない。"))
        assertEquals(listOf("nakute-mo-ii"), points("行かなくてもいい。"))
        assertEquals(listOf("naide-kudasai"), points("行かないでください。"))
        assertEquals(listOf("tokoro-ing"), points("読んでいるところです。"))
    }

    @Test
    fun `particles are told apart by how they're used`() {
        assertEquals(listOf("kara-from"), points("駅から歩く。"))
        assertEquals(listOf("kara-because"), points("疲れたから寝る。").filter { it != "ta" })
        assertEquals(listOf("te-kara"), points("食べてから出かける。"))
        assertEquals(listOf("ga"), points("猫がいる。"))
        assertEquals(listOf("ga-but"), points("寒いが行く。"))
        assertEquals(listOf("to"), points("友達と話す。"))
        assertEquals(listOf("to-omou"), points("行くと思う。"))
        assertEquals(listOf("to-if"), points("春になると暖かくなる。").filter { it != "ni" })
    }

    @Test
    fun `before needs a verb in front of it`() {
        assertEquals(listOf("mae-ni"), points("寝る前に"))
        assertEquals(listOf("no"), points("駅の前に").filter { it != "ni" })
    }

    @Test
    fun `a pattern spans the words it's made of`() {
        val words = reader.words("ここで遊んじゃいけない。")
        val uses = words.flatMap { it.grammar }.filter { it.point.id == "te-wa-ikenai" }.distinct()
        assertEquals(1, uses.size)
        val use = uses[0]
        assertEquals("じゃいけない", words.subList(use.first, use.last + 1).joinToString("") { it.surface })
    }

    @Test
    fun `patterns inside a conjugated word are found too`() {
        val w = reader.words("飲んでしまった").single()
        assertEquals(listOf("te-shimau"), w.grammar.map { it.point.id })
        assertEquals(0, w.grammar[0].first)
        assertEquals(0, w.grammar[0].last)
    }
}
