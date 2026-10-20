package ca.danielpower.jstudy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * The bundled stories as the story tools read them: each page's paragraphs
 * of words, so the app shows exactly what the stories were checked against,
 * and doesn't need a tokenizer of its own. A story's chapters share one file
 * (stories/read/arrival.json), and the grammar pages theirs (grammar.json).
 */
object StoryText {
    /** The file, under stories/read, that has page [id] (arrival/narita, or grammar/wa). */
    fun file(id: String) = id.substringBefore('/') + ".json"

    /** Each file's text, for [pages] by id, in the order given. */
    fun write(pages: Map<String, List<List<ReadingWord>>>): Map<String, String> =
        pages.entries.groupBy { file(it.key) }.mapValues { (_, entries) ->
            buildString {
                append("{\n")
                for ((n, e) in entries.withIndex()) {
                    append(" ").append(JsonPrimitive(e.key)).append(": [\n")
                    for ((p, words) in e.value.withIndex()) {
                        append("  ").append(compact.encodeToString(JsonElement.serializer(), JsonArray(words.map(::word))))
                        append(if (p < e.value.size - 1) ",\n" else "\n")
                    }
                    append(" ]").append(if (n < entries.size - 1) ",\n" else "\n")
                }
                append("}\n")
            }
        }

    /** The pages in one file [write] wrote, by id. */
    fun parse(json: String): Map<String, List<List<ReadingWord>>> =
        Json.parseToJsonElement(json).jsonObject.mapValues { (_, paragraphs) ->
            paragraphs.jsonArray.map { p -> p.jsonArray.map { word(it.jsonObject) } }
        }

    private val compact = Json { prettyPrint = false }

    // A word's fields, leaving out what's as written: its dictionary form, its reading, its furigana.
    private fun word(w: ReadingWord): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        out["s"] = JsonPrimitive(w.surface)
        out["k"] = JsonPrimitive(KINDS.getValue(w.kind))
        if (w.lemma != w.surface) out["l"] = JsonPrimitive(w.lemma)
        if (w.reading != Kana.toHiragana(w.surface)) out["r"] = JsonPrimitive(w.reading)
        if (w.wordId != null) out["id"] = JsonPrimitive(w.wordId)
        if (w.conjugation.isNotEmpty()) out["c"] = JsonArray(w.conjugation.map(::JsonPrimitive))
        if (w.ruby != listOf(Ruby(w.surface, null))) out["ruby"] = JsonArray(w.ruby.map { JsonArray(listOf(JsonPrimitive(it.base), it.ruby?.let(::JsonPrimitive) ?: JsonNull)) })
        if (w.grammar.isNotEmpty()) out["g"] = JsonArray(w.grammar.map { g ->
            // Where it starts and ends inside its words, when not at their edges.
            val inside = when {
                g.to != null -> listOf(g.from, g.to)
                g.from != 0 -> listOf(g.from)
                else -> emptyList()
            }
            JsonArray(listOf(JsonPrimitive(g.point.id)) + (listOf(g.first, g.last) + inside).map { JsonPrimitive(it) })
        })
        if (w.form != null) out["f"] = JsonPrimitive(w.form)
        return JsonObject(out)
    }

    private fun word(o: JsonObject): ReadingWord {
        val surface = o.getValue("s").jsonPrimitive.content
        return ReadingWord(
            surface = surface,
            lemma = o["l"]?.jsonPrimitive?.content ?: surface,
            reading = o["r"]?.jsonPrimitive?.content ?: Kana.toHiragana(surface),
            kind = BY_CODE.getValue(o.getValue("k").jsonPrimitive.content),
            wordId = o["id"]?.jsonPrimitive?.long,
            conjugation = o["c"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
            ruby = o["ruby"]?.jsonArray?.map { r ->
                val (base, ruby) = r.jsonArray
                Ruby(base.jsonPrimitive.content, (ruby as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content)
            } ?: listOf(Ruby(surface, null)),
            grammar = o["g"]?.jsonArray?.map { g ->
                val parts = g.jsonArray.map { it.jsonPrimitive }
                val (id, first, last) = parts
                GrammarUse(
                    grammarPoint(id.content) ?: error("no grammar point ${id.content}"),
                    first.int,
                    last.int,
                    from = parts.getOrNull(3)?.int ?: 0,
                    to = parts.getOrNull(4)?.int,
                )
            }.orEmpty(),
            form = o["f"]?.jsonPrimitive?.content,
        )
    }

    private val KINDS = mapOf(WordKind.WORD to "w", WordKind.NAME to "n", WordKind.GRAMMAR to "g", WordKind.PUNCTUATION to "p")
    private val BY_CODE = KINDS.entries.associate { it.value to it.key }
}
