package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TopicLinesTest {
    private fun line(chapter: String, p: Int, size: Int = 5, points: List<Int> = listOf(0)) = TopicLine(chapter, p, 0..size, size, points)

    private fun ids(lines: List<TopicLine>) = lines.map { "${it.chapter}:${it.p}" }

    @Test
    fun `takes two lines from a chapter at most, and six in all`() {
        val lines = (1..5).flatMap { c -> (0 until 4).map { line("c$c", it) } }
        val readAt = (1..5).associate { "c$it" to it.toLong() }
        val picked = TopicLines.pick(lines, readAt, points = 1)
        assertEquals(listOf("c5:0", "c5:1", "c4:0", "c4:1", "c3:0", "c3:1"), ids(picked))
    }

    @Test
    fun `leaves out chapters you haven't read`() {
        val lines = listOf(line("read", 0), line("unread", 0), line("read", 1))
        assertEquals(listOf("read:0", "read:1"), ids(TopicLines.pick(lines, mapOf("read" to 1L), points = 1)))
    }

    @Test
    fun `takes the topic's points in turn`() {
        // から and まで: the latest chapters only use から, but まで gets its turn.
        val lines = listOf(
            line("a", 0, points = listOf(1)),
            line("b", 0), line("b", 1), line("c", 0), line("c", 1), line("d", 0), line("d", 1),
        )
        val readAt = mapOf("a" to 1L, "b" to 2L, "c" to 3L, "d" to 4L)
        val picked = TopicLines.pick(lines, readAt, points = 2, shown = 3)
        assertEquals(listOf("d:0", "d:1", "a:0"), ids(picked))
    }

    @Test
    fun `picks short sentences before long ones`() {
        val lines = listOf(line("new", 0, size = 20), line("new", 1, size = 4), line("old", 0, size = 6))
        val readAt = mapOf("new" to 2L, "old" to 1L)
        // Both short ones, then the long one; listed the chapter read last first.
        assertEquals(listOf("new:1", "old:0"), ids(TopicLines.pick(lines, readAt, points = 1, shown = 2)))
        assertEquals(listOf("new:0", "new:1", "old:0"), ids(TopicLines.pick(lines, readAt, points = 1)))
    }

    @Test
    fun `fills up with long sentences when there are too few short ones`() {
        val lines = listOf(line("a", 0, size = 30), line("a", 1, size = 3), line("a", 2, size = 30))
        assertEquals(listOf("a:0", "a:1"), ids(TopicLines.pick(lines, mapOf("a" to 1L), points = 1)))
    }

    @Test
    fun `writes and reads back each line`() {
        val lines = mapOf("kara-made" to listOf(TopicLine("rin-1", 2, 3..9, 6, listOf(0, 1)), TopicLine("rin-2", 0, 0..4, 4, listOf(1))))
        assertEquals(lines, TopicLines.parse(TopicLines.write(lines)))
    }

    @Test
    fun `leaves out the lesson's own example and counts what each line uses`() {
        val reader = Reader { _, _ -> null }
        val topic = GrammarTopic(
            id = "kara-made",
            title = "～から～まで",
            titleEn = "From, to",
            points = listOf("kara-from", "made"),
            summary = "",
            page = PathStory("grammar/kara-made", "～から～まで", "今夜から朝まで？", listOf("From tonight till morning?"), lesson = true),
        )
        val chapter = PathStory("night", "Night", "ユキ|「ケンタ、今夜から朝まで？」\n駅まで歩いた。朝から雨だった。", listOf("", "", ""))
        val catalog = StoryCatalog(
            series = listOf(PathSeries("night", "Night", "", emptyList(), listOf(Chapter(chapter, 1, "kara-made", emptyList())))),
            grammar = listOf(topic),
        )
        val lines = TopicLines.of(catalog) { reader.read(chapter.body) }.getValue("kara-made")
        assertEquals(listOf(1 to listOf(1), 1 to listOf(0)), lines.map { it.p to it.points })
        assertEquals(listOf(3, 4), lines.map { it.size })
    }
}
