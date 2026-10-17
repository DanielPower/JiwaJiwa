package ca.danielpower.jstudy.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/**
 * The stories as they're written, in tools/stories (see its AGENTS.md), put
 * together into the app's path.json. A chapter keeps each sentence beside its
 * English; the app wants the text joined, a quote from one of the cast on a
 * line of its own, and the English in a list.
 */
object StorySource {
    fun dir(root: File) = File(root, "tools/stories")

    /** path.json as it should be for the stories in [root]'s tools/stories. */
    fun pathJson(root: File): String {
        val dir = dir(root)
        val ids = read(File(dir, "stories.json")).jsonArray.map { it.jsonPrimitive.content }
        val folders = File(dir, "series").listFiles { f -> f.isDirectory }.orEmpty().map { it.name }.toSet()
        require(folders == ids.toSet()) {
            "tools/stories/stories.json and series/ disagree: listed only ${ids - folders}, folders only ${folders - ids.toSet()}"
        }
        val path = buildJsonObject {
            putJsonArray("series") { for (id in ids) add(story(File(dir, "series/$id"))) }
            putJsonArray("grammar") { for (t in read(File(dir, "grammar.json")).jsonArray) add(topic(t.jsonObject)) }
        }
        return pretty.encodeToString(JsonElement.serializer(), path) + "\n"
    }

    private fun story(folder: File): JsonObject {
        val id = folder.name
        val meta = read(File(folder, "story.json")).jsonObject
        val files = folder.listFiles { f -> f.name.matches(Regex("\\d{2}\\.json")) }.orEmpty().map { it.name }.sorted()
        require(files.map { it.take(2).toInt() } == (1..files.size).toList()) {
            "$id: chapters must be numbered 01.json, 02.json, … with no gaps: $files"
        }
        return buildJsonObject {
            put("id", id)
            put("title", meta.text("title"))
            put("blurb", meta.text("blurb"))
            put("after", meta["after"] ?: JsonArray(emptyList()))
            putJsonArray("chapters") {
                for (f in files) add(chapter("$id-${f.take(2)}", read(File(folder, f)).jsonObject))
            }
            if (meta["side"]?.jsonPrimitive?.boolean == true) put("side", true)
        }
    }

    private fun chapter(id: String, c: JsonObject): JsonObject {
        val paragraphs = c.getValue("paragraphs").jsonArray.map { p -> p.jsonArray.map { pair(it) } }
        require(c["plot"]?.jsonPrimitive?.content.orEmpty().isNotBlank()) { "$id: give it a one-line \"plot\"" }
        return buildJsonObject {
            put("id", id)
            put("title", c.text("title"))
            put("plot", c.text("plot"))
            put("body", lines(paragraphs).joinToString("\n\n") { p -> p.joinToString("") { it.first } })
            putJsonArray("translations") { for (p in paragraphs) for ((_, en) in p) add(en) }
            c["topic"]?.let { put("topic", it) }
            c["meets"]?.let { put("meets", it) }
        }
    }

    /** A paragraph's quotes marked with a speaker (ユキ|「…」) each go on a line of their own. */
    private fun lines(paragraphs: List<List<Pair<String, String>>>): List<List<Pair<String, String>>> = buildList {
        for (p in paragraphs) {
            var run = ArrayList<Pair<String, String>>()
            for (s in p) {
                if ('|' in s.first) {
                    if (run.isNotEmpty()) add(run).also { run = ArrayList() }
                    add(listOf(s))
                } else {
                    run += s
                }
            }
            if (run.isNotEmpty()) add(run)
        }
    }

    /** A grammar topic: its parts are Japanese lines with their English, and notes (plain strings) between them. */
    private fun topic(t: JsonObject): JsonObject {
        val lines = ArrayList<Pair<String, String>>()
        return buildJsonObject {
            for (key in listOf("id", "title", "title_en", "points", "summary")) put(key, t.getValue(key))
            val notes = buildJsonArray {
                for (part in t.getValue("parts").jsonArray) {
                    if (part is JsonPrimitive) addJsonObject { put("before", lines.size); put("text", part.content) }
                    else lines += pair(part)
                }
            }
            put("body", lines.joinToString("\n") { it.first })
            putJsonArray("translations") { for ((_, en) in lines) add(en) }
            put("notes", notes)
        }
    }

    private fun pair(e: JsonElement): Pair<String, String> {
        val (jp, en) = e.jsonArray.map { it.jsonPrimitive.content }.also { require(it.size == 2) { "expected [Japanese, English]: $e" } }
        return jp to en
    }

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    private fun read(file: File): JsonElement = try {
        Json.parseToJsonElement(file.readText())
    } catch (e: Exception) {
        throw IllegalArgumentException("${file.path}: ${e.message}", e)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = " " }
}
