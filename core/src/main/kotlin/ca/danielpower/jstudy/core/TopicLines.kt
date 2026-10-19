package ca.danielpower.jstudy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A sentence of a chapter: words [words] of its paragraph [p]. */
data class TopicLine(val chapter: String, val p: Int, val words: IntRange)

/**
 * Where each grammar topic is used in the chapters, a sentence at a time, so
 * a topic's page can show the lines you've read with it. The story tools
 * work it out (stories/lines.json), so the app doesn't read every chapter.
 */
object TopicLines {
    /** Each topic's lines, in the order the catalog lists the chapters, by topic id. */
    fun of(catalog: StoryCatalog, text: (String) -> List<List<ReadingWord>>): Map<String, List<TopicLine>> {
        val topicOf = catalog.grammar.flatMap { t -> t.points.map { it to t.id } }.toMap()
        val out = LinkedHashMap<String, MutableList<TopicLine>>()
        for ((_, c) in catalog.chapters) {
            text(c.story.id).forEachIndexed { p, words ->
                for (range in sentenceRanges(words)) {
                    val topics = range.flatMap { i -> words[i].grammar.mapNotNull { topicOf[it.point.id] } }.distinct()
                    for (t in topics) out.getOrPut(t) { ArrayList() } += TopicLine(c.story.id, p, range)
                }
            }
        }
        return out
    }

    fun write(lines: Map<String, List<TopicLine>>): String =
        lines.entries.joinToString(",\n", "{\n", "\n}\n") { (topic, list) ->
            " \"$topic\": [" + list.joinToString(",") { "[\"${it.chapter}\",${it.p},${it.words.first},${it.words.last}]" } + "]"
        }

    fun parse(json: String): Map<String, List<TopicLine>> =
        Json.parseToJsonElement(json).jsonObject.mapValues { (_, list) ->
            list.jsonArray.map { e ->
                val a = e.jsonArray
                TopicLine(a[0].jsonPrimitive.content, a[1].jsonPrimitive.int, a[2].jsonPrimitive.int..a[3].jsonPrimitive.int)
            }
        }
}
