package ca.danielpower.jstudy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A sentence of a chapter: words [words] of its paragraph [p], [size] of
 * them words rather than punctuation, using its topic's points [points]
 * (by their place in the topic's list).
 */
data class TopicLine(val chapter: String, val p: Int, val words: IntRange, val size: Int = words.count(), val points: List<Int> = listOf(0))

/**
 * Where each grammar topic is used in the chapters, a sentence at a time, so
 * a topic's page can show the lines you've read with it. The story tools
 * work it out (stories/lines.json), so the app doesn't read every chapter.
 */
object TopicLines {
    /** The most lines a topic's page shows, and the most from any one chapter. */
    const val SHOWN = 6
    const val PER_CHAPTER = 2

    /** A sentence this long or shorter, in words, is picked before longer ones. */
    const val SHORT = 12

    /**
     * Each topic's lines, in the order the catalog lists the chapters, by
     * topic id. A sentence that's one of the topic's own examples
     * ([Lessons.sameLine]) is left out: its page already shows it.
     */
    fun of(catalog: StoryCatalog, text: (String) -> List<List<ReadingWord>>): Map<String, List<TopicLine>> {
        val topicOf = catalog.grammar.flatMap { t -> t.points.map { it to t } }.toMap()
        val examples = catalog.grammar.associate { t -> t.id to paragraphs(t.page.body) }
        val out = LinkedHashMap<String, MutableList<TopicLine>>()
        for ((_, c) in catalog.chapters) {
            text(c.story.id).forEachIndexed { p, words ->
                for (range in sentenceRanges(words)) {
                    val used = range.flatMap { i -> words[i].grammar.map { it.point.id } }.distinct()
                    val sentence = range.joinToString("") { words[it].surface }
                    val size = range.count { words[it].kind != WordKind.PUNCTUATION }
                    for (t in used.mapNotNull { topicOf[it] }.distinct()) {
                        if (examples.getValue(t.id).any { Lessons.sameLine(it, sentence) }) continue
                        val points = t.points.indices.filter { t.points[it] in used }
                        out.getOrPut(t.id) { ArrayList() } += TopicLine(c.story.id, p, range, size, points)
                    }
                }
            }
        }
        return out
    }

    /**
     * The lines a topic's page shows from [lines], its lines in catalog
     * order: only from chapters you've read ([readAt], when each was read),
     * at most [perChapter] from any one and [shown] in all. The picks take
     * the topic's [points] in turn, its first first, so each part of it
     * shows; short sentences come before long ones, and within each, the
     * chapter read last first. They're listed the chapter read last first.
     */
    fun pick(lines: List<TopicLine>, readAt: Map<String, Long>, points: Int, shown: Int = SHOWN, perChapter: Int = PER_CHAPTER): List<TopicLine> {
        val latest = lines.filter { it.chapter in readAt }.sortedByDescending { readAt.getValue(it.chapter) }
        val picked = LinkedHashSet<TopicLine>()
        val fromChapter = HashMap<String, Int>()
        for (short in listOf(true, false)) {
            val queues = (0 until maxOf(points, 1)).map { n -> latest.filter { (it.size <= SHORT) == short && n in it.points }.toMutableList() }
            while (picked.size < shown) {
                var took = false
                for (queue in queues) {
                    if (picked.size >= shown) break
                    queue.removeAll { it in picked || (fromChapter[it.chapter] ?: 0) >= perChapter }
                    val line = queue.removeFirstOrNull() ?: continue
                    picked += line
                    fromChapter.merge(line.chapter, 1, Int::plus)
                    took = true
                }
                if (!took) break
            }
        }
        val order = latest.withIndex().associate { (i, line) -> line to i }
        return picked.sortedBy { order.getValue(it) }
    }

    fun write(lines: Map<String, List<TopicLine>>): String =
        lines.entries.joinToString(",\n", "{\n", "\n}\n") { (topic, list) ->
            " \"$topic\": [" + list.joinToString(",") { "[\"${it.chapter}\",${it.p},${it.words.first},${it.words.last},${it.size},${it.points.joinToString(",", "[", "]")}]" } + "]"
        }

    fun parse(json: String): Map<String, List<TopicLine>> =
        Json.parseToJsonElement(json).jsonObject.mapValues { (_, list) ->
            list.jsonArray.map { e ->
                val a = e.jsonArray
                val words = a[2].jsonPrimitive.int..a[3].jsonPrimitive.int
                TopicLine(a[0].jsonPrimitive.content, a[1].jsonPrimitive.int, words, a[4].jsonPrimitive.int, a[5].jsonArray.map { it.jsonPrimitive.int })
            }
        }
}
