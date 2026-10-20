package ca.danielpower.jstudy.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KanaDrillTest {
    private val day = 20_000L
    private val noon = day * 86_400_000L + 12 * 3_600_000L

    @Test
    fun `splits text into kana units, longest first`() {
        assertEquals(listOf("きょ", "う", "は", "雨", "。"), KanaCourse.segments("きょうは雨。"))
        assertEquals(listOf("ティ", "ー"), KanaCourse.segments("ティー"))
        assertEquals(listOf("ぁ"), KanaCourse.segments("ぁ"))
    }

    @Test
    fun `romaji follows small tsu and the long vowel mark`() {
        assertEquals("kitte", Romaji.of("きって"))
        assertEquals("koohii", Romaji.of("コーヒー"))
        assertEquals("matcha", Romaji.of("まっちゃ"))
        assertEquals("nihon", Romaji.of("にほん"))
        assertEquals("a'", Romaji.of("あっ"))
        assertEquals("tsuini", Romaji.of("ついに"))
        assertEquals(listOf("tsu", "i", "ni"), Romaji.split("ついに").map { it.second })
        assertEquals("wa", Romaji.of("は", particles = listOf(0)))
        assertEquals("ha", Romaji.of("は"))
        assertEquals("niwa", Romaji.of("には", particles = listOf(1)))
        assertNull(Romaji.split("雨").single().second)
    }

    @Test
    fun `a small tsu at the end of a piece doubles the sound after it`() {
        assertEquals("nat", Romaji.of("なっ", next = "た"))
        assertEquals("it", Romaji.of("いっ", next = "ちゃ"))
        assertEquals("a'", Romaji.of("あっ", next = "。"))
        val all = setOf("な", "っ", "た")
        // なった is read なっ and た.
        assertEquals(
            listOf(RubyPiece("な", "na", true), RubyPiece("っ", "t", true), RubyPiece("た", "ta", true)),
            romajiPieces(word("なっ" to null, "た" to null), all),
        )
        // 笑った: the kanji's reading, then っ before た.
        assertEquals(
            listOf(RubyPiece("笑", "わら"), RubyPiece("っ", "t", true), RubyPiece("た", null)),
            romajiPieces(word("笑" to "わら", "っ" to null, "た" to null), setOf("っ")),
        )
        // At the end of a word, the next word's sound: 言っ before ちゃ.
        assertEquals(
            listOf(RubyPiece("言", "い"), RubyPiece("っ", "t", true)),
            romajiPieces(word("言" to "い", "っ" to null), setOf("っ"), next = "ちゃ"),
        )
    }

    @Test
    fun `the particle は is said wa wherever it is in a word, and only there`() {
        val ha = setOf("は")
        assertEquals(
            listOf(RubyPiece("こんばん", null), RubyPiece("は", "wa", true)),
            romajiPieces(word("こんばんは" to null, particles = listOf(4)), ha),
        )
        assertEquals(
            listOf(RubyPiece("実", "じつ"), RubyPiece("は", "wa", true)),
            romajiPieces(word("実" to "じつ", "は" to null, particles = listOf(1)), ha),
        )
        assertEquals(
            listOf(RubyPiece("は", "wa", true), RubyPiece("いけ", null), RubyPiece("ませ", null), RubyPiece("ん", null)),
            romajiPieces(word("は" to null, "いけ" to null, "ませ" to null, "ん" to null, kind = WordKind.GRAMMAR, particles = listOf(0)), ha),
        )
        // 母 is haha, and はず (a grammar word) is hazu.
        assertEquals(listOf(RubyPiece("母", "haha", true)), romajiPieces(word("母" to "はは"), ha))
        assertEquals(listOf(RubyPiece("は", "ha", true), RubyPiece("ず", null)), romajiPieces(word("はず" to null, kind = WordKind.GRAMMAR), ha))
        // A word on its own, as on a card: the greetings that end in the particle.
        assertEquals("konbanwa", Romaji.of("こんばんは"))
        assertEquals("jitsuwa", Romaji.of("じつは"))
        assertEquals("haha", Romaji.of("はは"))
        assertEquals(listOf("ko", "n", "ba", "n", "wa"), Romaji.split("こんばんは").map { it.second })
    }

    @Test
    fun `n before a vowel or y takes an apostrophe`() {
        assertEquals("kon'ya", Romaji.of("こんや"))
        assertEquals("kin'en", Romaji.of("きんえん"))
        assertEquals("hon'ya", Romaji.of("ほんや"))
        assertEquals("annai", Romaji.of("あんない"))
        assertEquals("shinbun", Romaji.of("しんぶん"))
        assertEquals("ten'in", Romaji.of("テンイン"))
        assertEquals(listOf(RubyPiece("今夜", "kon'ya", true)), romajiPieces(word("今夜" to "こんや"), setOf("ん")))
        // The reader shows a sound a kana, so there's nothing to split there.
        assertEquals(listOf("ko", "n", "ya"), Romaji.split("こんや").map { it.second })
    }

    @Test
    fun `teaches the kana the first chapters use most, first`() {
        val order = KanaCourse.order(listOf("ついについに", "たいした", "いい"))
        assertEquals(listOf("い", "つ", "に", "た", "し"), order.take(5))
        // Everything else follows, in chart order.
        assertEquals(KanaCourse.units.size, order.size)
        assertEquals("あ", order[5])
    }

    @Test
    fun `keeps lookalikes in different sets`() {
        val sets = KanaCourse.sets(listOf("さ", "ち", "き", "か", "た", "な", "い"))
        assertEquals(listOf("さ", "か", "た", "い"), sets[0].take(4))
        assertFalse(sets[0].contains("ち"))
        assertFalse(sets[0].contains("き"))
        for (set in KanaCourse.sets(KanaCourse.units.map { it.text })) {
            for (a in set) for (b in set) if (a != b) assertFalse(KanaLooks.alike(a, b), "$a and $b")
        }
    }

    @Test
    fun `unset kana known means both, so older installs don't get kana practice`() {
        assertEquals(KanaKnown.BOTH, KanaKnown.of(null))
        val state = KanaState(KanaKnown.of(null), KanaCourse.units.map { it.text }, emptyMap())
        assertTrue(state.done)
        assertTrue(state.unread.isEmpty())
        assertEquals(0, state.counts(day, noon).due)
    }

    @Test
    fun `hiragana only leaves katakana to learn`() {
        val state = KanaState(KanaKnown.HIRAGANA, KanaCourse.units.map { it.text }, emptyMap())
        assertTrue(state.toLearn.all { KanaCourse.unit(it)!!.script == Script.KATAKANA })
        assertEquals(KanaStage.CLEAR, state.stage("あ"))
        assertEquals(KanaStage.NEW, state.stage("ア"))
    }

    /** Answers every question right, quickly, and returns the progress. */
    private fun drill(session: KanaSession, wrongFirst: Set<String> = emptySet(), millis: Long = 1000): Map<String, KanaProgress> {
        val missed = HashSet<String>()
        while (true) {
            val q = session.next() ?: break
            val miss = q.unit.text in wrongFirst && missed.add(q.unit.text)
            val choice = if (miss) q.options.first { it != q.answer } else q.answer
            session.answer(q, choice, millis, noon)
        }
        return session.practised.associateWith { session.progress(it) }
    }

    private fun none() = KanaState(KanaKnown.NONE, KanaCourse.order(listOf("ついに来た")), emptyMap())

    @Test
    fun `a new set is practised until each is right three times, then looked at again later that day`() {
        val state = none()
        val set = state.nextSet()!!.map { KanaCourse.unit(it)!! }
        assertEquals(5, set.size)
        val session = KanaSession(state, day, noon, set, random = Random(1))
        var asked = 0
        val shown = ArrayList<String>()
        while (true) {
            val q = session.next() ?: break
            shown.add(q.unit.text)
            session.answer(q, q.answer, 1000, noon)
            asked++
        }
        assertEquals(15, asked)
        // Never the same kana twice running, while there are others to ask.
        assertTrue(shown.take(10).zipWithNext().none { (a, b) -> a == b })
        for (unit in set) {
            val p = session.progress(unit.text)
            assertEquals(KanaStage.LEARNING, p.stage)
            assertEquals(day, p.introducedDay)
            assertEquals(noon + KanaRules.LATE_CHECK_MILLIS, p.lateCheckAt)
        }
        val after = state.copy(progress = set.associate { it.text to session.progress(it.text) }, lastSetDay = day)
        assertTrue(after.unread.none { it in set.map(KanaUnit::text) })
        assertEquals(0, after.counts(day, noon).due)
        assertEquals(5, after.counts(day, noon).later)
        assertEquals(5, after.counts(day, noon + KanaRules.LATE_CHECK_MILLIS).check)
    }

    @Test
    fun `a miss in the first sitting means at least two more rights`() {
        val state = none()
        val set = state.nextSet()!!.take(2).map { KanaCourse.unit(it)!! }
        val session = KanaSession(state, day, noon, set, random = Random(2))
        var asked = 0
        val missed = HashSet<String>()
        while (true) {
            val q = session.next() ?: break
            val choice = if (q.unit == set[0] && missed.add(q.unit.text)) q.options.first { it != q.answer } else q.answer
            session.answer(q, choice, 1000, noon)
            asked++
        }
        // Three rights each, and the miss.
        assertEquals(7, asked)
    }

    @Test
    fun `a kana is clear after three days quick and right, and a miss starts the count again`() {
        var state = none()
        val set = state.nextSet()!!.map { KanaCourse.unit(it)!! }
        state = state.copy(progress = drill(KanaSession(state, day, noon, set, random = Random(3))))
        val text = set[0].text
        fun dayOf(d: Long, wrong: Set<String> = emptySet(), millis: Long = 1000) {
            val now = d * 86_400_000L + 12 * 3_600_000L
            val s = KanaSession(state, d, now, emptyList(), random = Random(d))
            val missed = HashSet<String>()
            while (true) {
                val q = s.next() ?: break
                val choice = if (q.unit.text in wrong && missed.add(q.unit.text)) q.options.first { it != q.answer } else q.answer
                s.answer(q, choice, millis, now)
            }
            state = state.copy(progress = state.progress + s.practised.associateWith { s.progress(it) })
        }
        dayOf(day + 1)
        dayOf(day + 2, wrong = setOf(text))
        assertEquals(0, state.progress.getValue(text).passDays)
        // Slow but right doesn't count, and isn't a miss.
        dayOf(day + 3, millis = 5000)
        assertEquals(0, state.progress.getValue(text).passDays)
        dayOf(day + 4)
        dayOf(day + 5)
        assertEquals(KanaStage.LEARNING, state.progress.getValue(text).stage)
        dayOf(day + 6)
        assertEquals(KanaStage.CLEAR, state.progress.getValue(text).stage)
        assertEquals(day + 6 + KanaRules.CHECK_DAYS, state.progress.getValue(text).checkDue)
        // The others had no misses, so they were clear days earlier.
        assertEquals(KanaStage.CLEAR, state.progress.getValue(set[1].text).stage)
        assertTrue(state.done || state.nextSet() != null)
    }

    @Test
    fun `a missed weekly check sends a kana back to learning`() {
        val text = "あ"
        val p = KanaProgress(text, KanaStage.CLEAR, introducedDay = day - 10, passDays = 3, lastDay = day - 7, checkDue = day)
        val state = KanaState(KanaKnown.NONE, KanaCourse.units.map { it.text }, mapOf(text to p))
        assertEquals(listOf(text), state.due(day, noon))
        val s = KanaSession(state, day, noon, emptyList(), random = Random(4))
        val q = s.next()!!
        val r = s.answer(q, q.options.first { it != q.answer }, 1000, noon)
        assertFalse(r.correct)
        assertEquals(KanaStage.LEARNING, r.progress.stage)
        assertNull(r.progress.checkDue)
    }

    @Test
    fun `i know these clears each kana answered quickly`() {
        val state = none()
        val set = state.nextSet()!!.map { KanaCourse.unit(it)!! }
        val session = KanaSession(state, day, noon, set, random = Random(5))
        session.checkKnown()
        val progress = drill(session, wrongFirst = setOf(set[0].text))
        assertEquals(KanaStage.LEARNING, progress.getValue(set[0].text).stage)
        for (unit in set.drop(1)) assertEquals(KanaStage.CLEAR, progress.getValue(unit.text).stage)
    }

    @Test
    fun `wrong answers never sound the same as the right one`() {
        val pool = KanaCourse.units.filter { !it.mark }
        val random = Random(6)
        for (unit in pool) {
            for (listen in listOf(true, false)) {
                val wrong = Distractors.pick(unit, pool, emptyList(), 3, listen, random)
                assertEquals(3, wrong.size)
                assertTrue(wrong.none { it.romaji == unit.romaji }, unit.text)
                assertEquals(3, wrong.map { it.romaji }.distinct().size)
            }
        }
        val ji = KanaCourse.unit("じ")!!
        assertTrue(Distractors.pick(ji, pool, emptyList(), 3, false, random).none { it.text == "ぢ" })
    }

    @Test
    fun `wrong answers lean on lookalikes and past mix-ups`() {
        val pool = KanaCourse.units.filter { !it.mark && it.script == Script.HIRAGANA }
        val me = KanaCourse.unit("め")!!
        val wrong = Distractors.pick(me, pool, listOf("ろ"), 3, false, Random(7)).map { it.text }
        assertEquals("ろ", wrong.first())
        assertTrue("ぬ" in wrong || "の" in wrong || "あ" in wrong)
    }

    @Test
    fun `a mark's wrong answers differ only in what the marks do`() {
        // Each answer is the same sounds, doubled or held in different places: bedo, beedo, beddoo for beddo.
        fun bare(romaji: String) = romaji.replace(Regex("(.)\\1+"), "$1")
        for ((mark, words) in KanaSession.MARK_WORDS) {
            for ((word, options) in words) {
                assertEquals(Romaji.of(word), options.first(), word)
                assertEquals(4, options.distinct().size, word)
                assertEquals(1, options.map(::bare).distinct().size, "$mark: $word $options")
            }
        }
    }

    @Test
    fun `marks are asked in a word`() {
        val state = KanaState(KanaKnown.NONE, listOf("っ") + KanaCourse.units.map { it.text }.filter { it != "っ" }, emptyMap())
        val set = state.nextSet()!!.map { KanaCourse.unit(it)!! }
        assertEquals("っ", set.first().text)
        val q = KanaSession(state, day, noon, set, random = Random(8)).next()!!
        assertEquals("っ", q.unit.text)
        assertTrue(q.prompt.contains("っ"))
        assertEquals(Romaji.of(q.prompt), q.answer)
        assertNotNull(q.options.single { it == q.answer })
    }

    private fun word(vararg ruby: Pair<String, String?>, kind: WordKind = WordKind.WORD, particles: List<Int> = emptyList()) =
        ReadingWord(ruby.joinToString("") { it.first }, "", "", kind, null, emptyList(), ruby.map { Ruby(it.first, it.second) }, particles = particles)

    @Test
    fun `romaji sits over unread kana, and over a kanji's whole reading`() {
        val unread = setOf("つ", "に", "き", "ほ", "ん")
        assertEquals(
            listOf(RubyPiece("つ", "tsu", true), RubyPiece("い", null), RubyPiece("に", "ni", true)),
            romajiPieces(word("ついに" to null), unread),
        )
        assertEquals(
            listOf(RubyPiece("来", "ki", true), RubyPiece("た", null)),
            romajiPieces(word("来" to "き", "た" to null), unread),
        )
        assertEquals(listOf(RubyPiece("日本", "nihon", true)), romajiPieces(word("日本" to "にほん"), unread))
        // Kana you've practised need nothing.
        assertNull(romajiPieces(word("いた" to null), unread))
        assertNull(romajiPieces(word("ついに" to null), emptySet()))
        // The topic particle is said "wa".
        assertEquals(listOf(RubyPiece("は", "wa", true)), romajiPieces(word("は" to null, kind = WordKind.GRAMMAR, particles = listOf(0)), setOf("は")))
    }
}
