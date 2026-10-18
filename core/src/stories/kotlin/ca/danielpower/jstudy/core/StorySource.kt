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

    /**
     * A story: story.json lists its chapters' slugs in reading order, and each
     * is a file of that name. A chapter's id is the story's id and its slug
     * (share-house/trash-day), so chapters can be added or moved anywhere
     * without changing the ids saved progress uses.
     */
    private fun story(folder: File): JsonObject {
        val id = folder.name
        require(id.matches(SLUG) && id != "grammar") { "$id: a story's id is lowercase words joined by hyphens, and not \"grammar\"" }
        val meta = read(File(folder, "story.json")).jsonObject
        val slugs = meta["chapters"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        require(slugs.isNotEmpty()) { "$id: story.json lists no \"chapters\"" }
        for (slug in slugs) require(slug.matches(SLUG)) { "$id: chapter \"$slug\" should be lowercase words joined by hyphens" }
        require(slugs.size == slugs.toSet().size) { "$id: a chapter is listed twice: ${slugs.groupBy { it }.filter { it.value.size > 1 }.keys}" }
        val files = folder.listFiles { f -> f.name.endsWith(".json") && f.name != "story.json" }.orEmpty().map { it.name.removeSuffix(".json") }.toSet()
        require(files == slugs.toSet()) { "$id: story.json and the chapter files disagree: listed only ${slugs - files}, files only ${files - slugs.toSet()}" }
        return buildJsonObject {
            put("id", id)
            put("title", meta.text("title"))
            put("blurb", meta.text("blurb"))
            put("after", meta["after"] ?: JsonArray(emptyList()))
            putJsonArray("chapters") {
                for (slug in slugs) add(chapter("$id/$slug", read(File(folder, "$slug.json")).jsonObject))
            }
            if (meta["side"]?.jsonPrimitive?.boolean == true) put("side", true)
        }
    }

    /** A chapter: its paragraphs are lists of sentences with their English, and notes (`{"note": …}`) between them. */
    private fun chapter(id: String, c: JsonObject): JsonObject {
        require(c["plot"]?.jsonPrimitive?.content.orEmpty().isNotBlank()) { "$id: give it a one-line \"plot\"" }
        val paragraphs = ArrayList<List<Pair<String, String>>>()
        val notes = buildJsonArray {
            for (block in c.getValue("paragraphs").jsonArray) {
                if (block is JsonObject) {
                    // Shown from a marker after the paragraph before it, so it can't open the chapter.
                    require(paragraphs.isNotEmpty()) { "$id: a note goes after the paragraph it's about, not first" }
                    addJsonObject { put("before", paragraphs.size); put("text", block.text("note")) }
                } else {
                    paragraphs += lines(listOf(block.jsonArray.map { pair(it) }))
                }
            }
        }
        return buildJsonObject {
            put("id", id)
            put("title", c.text("title"))
            put("plot", c.text("plot"))
            put("body", paragraphs.joinToString("\n\n") { p -> p.joinToString("") { it.first } })
            putJsonArray("translations") { for (p in paragraphs) for ((_, en) in p) add(en) }
            if (notes.isNotEmpty()) put("notes", notes)
            c["readings"]?.let { put("readings", it) }
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

    /**
     * Each page's Japanese sentences as written beside their English, by id
     * (arrival/narita, grammar/wa), so a check can name the one sentence the
     * reader splits in two.
     */
    fun sentences(root: File): Map<String, List<String>> {
        val dir = dir(root)
        val out = LinkedHashMap<String, List<String>>()
        for (folder in File(dir, "series").listFiles { f -> f.isDirectory }.orEmpty()) {
            for (file in folder.listFiles { f -> f.name.endsWith(".json") && f.name != "story.json" }.orEmpty()) {
                out["${folder.name}/${file.name.removeSuffix(".json")}"] = read(file).jsonObject.getValue("paragraphs").jsonArray
                    .filterIsInstance<JsonArray>().flatMap { p -> p.map { pair(it).first } }
            }
        }
        for (t in read(File(dir, "grammar.json")).jsonArray) {
            out["grammar/${t.jsonObject.text("id")}"] = t.jsonObject.getValue("parts").jsonArray.filterIsInstance<JsonArray>().map { pair(it).first }
        }
        return out
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

    /** Story ids and chapter slugs: lowercase words joined by hyphens. */
    private val SLUG = Regex("[a-z0-9]+(-[a-z0-9]+)*")

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = " " }
}
