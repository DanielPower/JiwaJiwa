package ca.danielpower.jstudy.core

import java.time.LocalDateTime
import java.time.ZoneId
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
    fun `a grammar pattern's name reads out as its kana say it`() {
        assertEquals("ta", Romaji.pattern("～た"))
        assertEquals("desu / da", Romaji.pattern("です / だ"))
        assertEquals("wa", Romaji.pattern("は"))
        assertEquals("temoii / tewaikenai", Romaji.pattern("～てもいい / ～てはいけない"))
        assertEquals("hazu", Romaji.pattern("～はず"))
        assertEquals("anoo / eeto", Romaji.pattern("あのー / えーと"))
        // Kanji have no reading here.
        assertNull(Romaji.pattern("～と思う"))
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
    fun `teaches the chart a row at a time, the voiced rows after the plain ones`() {
        val sets = KanaCourse.sets
        // Every kana once, in the chart's order.
        assertEquals(KanaCourse.units.map { it.text }, sets.flatMap { it.kana })
        assertEquals(47, sets.size)
        assertEquals(listOf("あ", "い", "う", "え", "お"), sets[0].kana)
        assertEquals(listOf("か", "き", "く", "け", "こ"), sets[1].kana)
        assertEquals(listOf("わ", "を", "ん"), sets[9].kana)
        assertEquals(listOf("が", "ぎ", "ぐ", "げ", "ご"), sets[10].kana)
        assertEquals(listOf("きゃ", "きゅ", "きょ", "しゃ", "しゅ", "しょ"), sets[15].kana)
        assertEquals(listOf("ア", "イ", "ウ", "エ", "オ"), sets[22].kana)
        // A voiced or combined kana comes in a set after the one it's built on.
        val setOf = sets.withIndex().flatMap { (i, set) -> set.kana.map { it to i } }.toMap()
        for (text in setOf.keys) {
            val base = KanaCourse.base(text) ?: continue
            assertTrue(setOf.getValue(base) < setOf.getValue(text), "$base before $text")
        }
        // Each new idea comes after its lesson, once.
        assertEquals(KanaLesson.entries.toList(), sets.mapNotNull { it.lesson })
        assertEquals(KanaLesson.VOWELS, sets[0].lesson)
        assertEquals(KanaLesson.DAKUTEN, sets[10].lesson)
        assertEquals(KanaLesson.KATAKANA, sets[22].lesson)
        assertNull(sets[32].lesson)
    }

    @Test
    fun `a voiced or combined kana's intro says how it's built`() {
        assertEquals("フ with ゛ · fu → bu", KanaCourse.built("ブ"))
        assertEquals("は with ゜ · ha → pa", KanaCourse.built("ぱ"))
        assertEquals("き + small ゃ · kya", KanaCourse.built("きゃ"))
        assertEquals("フ + small ァ · fa", KanaCourse.built("ファ"))
        assertNull(KanaCourse.built("か"))
        assertNull(KanaCourse.built("っ"))
        assertNull(KanaCourse.base("ー"))
    }

    @Test
    fun `a set's intro names its row and the kana that bend it`() {
        val lines = KanaCourse.sets.map { KanaCourse.introLine(it) }
        assertEquals("The five vowels. Tap one to hear it.", lines[0])
        assertEquals("The k row: k + each vowel. Tap one to hear it.", lines[1])
        assertEquals("The s row: s + each vowel, except し, said shi. Tap one to hear it.", lines[2])
        assertEquals("The t row: t + each vowel, except ち, said chi, and つ, said tsu. Tap one to hear it.", lines[3])
        assertEquals("The y row: y + a, u and o. Tap one to hear it.", lines[7])
        assertEquals("The w row, which keeps only わ and を, then ん. Tap one to hear it.", lines[9])
        assertEquals("The k row with ゛: k becomes g. Tap one to hear it.", lines[10])
        assertEquals("The t row with ゛: t becomes d, except ぢ, said ji, and づ, said zu. Tap one to hear it.", lines[12])
        assertEquals("The h row with ゛: h becomes b. Tap one to hear it.", lines[13])
        assertEquals("The h row with ゜: h becomes p. Tap one to hear it.", lines[14])
        assertEquals("き and し with small ゃ ゅ ょ. Tap one to hear it.", lines[15])
        assertEquals("ぴ with small ゃ ゅ ょ. Tap one to hear it.", lines[20])
        assertEquals("Small っ.", lines[21])
        assertEquals("The h row: h + each vowel, except フ, said fu. Tap one to hear it.", lines[27])
        assertEquals("Small ッ and the dash ー.", lines[43])
        assertEquals("Sounds from other languages. Tap one to hear it.", lines[44])
    }

    @Test
    fun `the worst lookalikes are told apart on the later one's set, and practice mixes them in`() {
        val setOf = KanaCourse.sets.withIndex().flatMap { (i, set) -> set.kana.map { it to i } }.toMap()
        val notes = KanaCourse.sets.flatMap { KanaLooks.notesFor(it.kana) }
        assertEquals(15, notes.size)
        for (note in notes) {
            for (like in note.like) {
                assertTrue(setOf.getValue(like) <= setOf.getValue(note.kana), "$like by ${note.kana}")
                assertTrue(KanaLooks.alike(note.kana, like), "${note.kana} and $like")
            }
        }
        // ぬ is in the n row, め in the m row.
        assertEquals(listOf("ぬ"), KanaLooks.notesFor(KanaCourse.sets[6].kana).single().like)
        assertEquals(listOf("り", "れ", "ろ"), KanaLooks.notesFor(KanaCourse.sets[8].kana).map { it.kana })
        assertTrue(KanaLooks.notesFor(KanaCourse.sets[1].kana).isEmpty())
    }

    @Test
    fun `a set's lesson comes first until one of its kana is practised`() {
        assertEquals(KanaLesson.VOWELS, none().nextLesson())
        val started = none().copy(progress = mapOf("い" to KanaProgress("い", KanaStage.LEARNING, introducedDay = day)))
        assertEquals(listOf("あ", "う", "え", "お"), started.nextSet())
        assertNull(started.nextLesson())
        // A set without one.
        val vowels = KanaCourse.sets[0].kana.associateWith { KanaProgress(it, KanaStage.LEARNING, introducedDay = day) }
        assertNull(none().copy(progress = vowels).nextLesson())
        assertEquals(KanaLesson.KATAKANA, KanaState(KanaKnown.HIRAGANA, emptyMap()).nextLesson())
    }

    @Test
    fun `classic lookalikes and voicing pairs are alike`() {
        for ((a, b) in listOf("ツ" to "ソ", "シ" to "ン", "シ" to "ツ", "ぬ" to "ね", "ば" to "ぱ", "ブ" to "プ", "は" to "ば", "しゃ" to "じゃ", "きゅ" to "きょ", "ティ" to "ディ")) {
            assertTrue(KanaLooks.alike(a, b), "$a and $b")
            assertTrue(KanaLooks.alike(b, a), "$b and $a")
        }
        assertTrue(KanaLooks.alike("か", "が"))
        assertFalse(KanaLooks.alike("あ", "ア"))
        assertFalse(KanaLooks.alike("ば", "ば"))
        assertFalse(KanaLooks.alike("が", "ぎ"))
        assertFalse(KanaLooks.alike("きゃ", "きゅ"))
    }

    @Test
    fun `unset kana known means both, so older installs don't get kana practice`() {
        assertEquals(KanaKnown.BOTH, KanaKnown.of(null))
        val state = KanaState(KanaKnown.of(null), emptyMap())
        assertTrue(state.done)
        assertTrue(state.unread.isEmpty())
        assertEquals(0, state.counts(day, noon).due)
    }

    @Test
    fun `hiragana only leaves katakana to learn`() {
        val state = KanaState(KanaKnown.HIRAGANA, emptyMap())
        assertTrue(state.toLearn.all { KanaCourse.unit(it)!!.script == Script.KATAKANA })
        // Starting with the katakana lesson.
        assertEquals(KanaLesson.KATAKANA, state.sets.first().lesson)
        assertEquals(1, state.setNumber("ア"))
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

    private fun none() = KanaState(KanaKnown.NONE, emptyMap())

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
    fun `a missed weekly check costs a kana one day`() {
        val text = "あ"
        val p = KanaProgress(text, KanaStage.CLEAR, introducedDay = day - 17, passDays = 3, lastDay = day - 7, checkDue = day, checks = 2)
        var state = KanaState(KanaKnown.NONE, mapOf(text to p))
        assertEquals(listOf(text), state.due(day, noon))
        val s = KanaSession(state, day, noon, emptyList(), random = Random(4))
        val q = s.next()!!
        val r = s.answer(q, q.options.first { it != q.answer }, 1000, noon)
        assertFalse(r.correct)
        assertEquals(KanaStage.LEARNING, r.progress.stage)
        assertNull(r.progress.checkDue)
        assertEquals(KanaRules.CLEAR_DAYS - 1, r.progress.passDays)
        // The next day, quick and right: clear again, and its weekly checks carry on from the third.
        state = state.copy(progress = mapOf(text to r.progress))
        val next = day + 1
        val at = noon + 86_400_000L
        assertEquals(listOf(text), state.due(next, at))
        val again = KanaSession(state, next, at, emptyList(), random = Random(5))
        val q2 = again.next()!!
        val r2 = again.answer(q2, q2.answer, 1000, at)
        assertEquals(KanaStage.CLEAR, r2.progress.stage)
        assertEquals(next + KanaRules.CHECK_DAYS, r2.progress.checkDue)
        assertEquals(2, r2.progress.checks)
    }

    @Test
    fun `the second look never falls in the night, and kana learned before the rollover wait three hours`() {
        val zone = ZoneId.of("America/St_Johns")
        val clock = StudyClock(zone)
        fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        // In the afternoon: three hours on, the same day.
        assertEquals(at("2026-10-20T17:00"), KanaRules.lateCheckAt(at("2026-10-20T14:00"), clock))
        // At 10pm it would fall after midnight, so it's the next study day's check.
        assertEquals(at("2026-10-21T04:00"), KanaRules.lateCheckAt(at("2026-10-20T22:00"), clock))
        // At 3:30am, just before the rollover: not before 6:30.
        assertEquals(at("2026-10-21T06:30"), KanaRules.lateCheckAt(at("2026-10-21T03:30"), clock))

        fun learnAt(time: String): KanaState {
            val now = at(time)
            val day = clock.dayOf(now)
            val state = none()
            val set = state.nextSet()!!.map { KanaCourse.unit(it)!! }
            val s = KanaSession(state, day, now, set, random = Random(9), lateCheck = { KanaRules.lateCheckAt(it, clock) })
            while (true) {
                val q = s.next() ?: break
                s.answer(q, q.answer, 1000, now)
            }
            return state.copy(progress = s.practised.associateWith { s.progress(it) }, lastSetDay = day)
        }
        fun counts(state: KanaState, time: String): KanaCounts {
            val now = at(time)
            val day = clock.dayOf(now)
            return state.counts(day, now, clock.startOf(day + 1))
        }

        val afternoon = learnAt("2026-10-20T14:00")
        assertEquals(5, counts(afternoon, "2026-10-20T15:00").later)
        assertEquals(5, counts(afternoon, "2026-10-20T17:00").check)

        // At 10pm: nothing later that night, and the next day's check from the rollover.
        val evening = learnAt("2026-10-20T22:00")
        assertEquals(0, counts(evening, "2026-10-20T22:30").later)
        assertEquals(0, counts(evening, "2026-10-21T02:00").check)
        assertEquals(5, counts(evening, "2026-10-21T04:01").check)

        // At 3:30am: not due just after the rollover, half an hour on, but by 6:30.
        val night = learnAt("2026-10-21T03:30")
        assertEquals(0, counts(night, "2026-10-21T03:45").later)
        assertEquals(0, counts(night, "2026-10-21T04:05").check)
        assertEquals(5, counts(night, "2026-10-21T06:31").check)
        // And that check counts a day's pass.
        val now = at("2026-10-21T06:31")
        val s = KanaSession(night, clock.dayOf(now), now, emptyList(), random = Random(10))
        while (true) {
            val q = s.next() ?: break
            s.answer(q, q.answer, 1000, now)
        }
        for (text in s.practised) {
            assertEquals(1, s.progress(text).passDays, text)
            assertNull(s.progress(text).lateCheckAt, text)
        }
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
        val q = KanaSession(none(), day, noon, listOf(KanaCourse.unit("っ")!!), random = Random(8)).next()!!
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
