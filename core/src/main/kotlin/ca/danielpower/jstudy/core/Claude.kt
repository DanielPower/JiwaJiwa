package ca.danielpower.jstudy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException

/** A story request failed. [signInNeeded] means the story server no longer accepts this sign-in. */
class ClaudeException(message: String, val signInNeeded: Boolean = false) : IOException(message)

/**
 * A conversation with Claude, through the story server (see `server/`),
 * which holds the API key and picks the model. Each reply's content blocks
 * go back unchanged in the next request, as the API requires for thinking
 * blocks.
 */
class ClaudeConversation(
    private val effort: String = "high",
    private val send: (body: String) -> Pair<Int, String>,
) : StoryModel {
    private var system = ""
    private val messages = ArrayList<JsonObject>()

    override fun start(system: String, message: String): String {
        this.system = system
        messages.clear()
        return reply(message)
    }

    override fun reply(message: String): String {
        messages += buildJsonObject {
            put("role", "user")
            put("content", message)
        }
        val body = buildJsonObject {
            put("system", system)
            put("messages", JsonArray(messages))
            put("effort", effort)
        }.toString()

        var attempt = 0
        while (true) {
            val (status, text) = send(body)
            if (status == 200) return read(text)
            // The server already retries Claude's own overloads; these cover it or its proxy being briefly away.
            val retryable = status == 502 || status == 503 || status == 504
            if (!retryable || attempt >= RETRY_DELAYS.size) throw ClaudeException(errorMessage(status, text), signInNeeded = status == 401)
            Thread.sleep(RETRY_DELAYS[attempt++])
        }
    }

    private fun read(text: String): String {
        val response = Json.parseToJsonElement(text).jsonObject
        val content = response["content"]?.jsonArray ?: JsonArray(emptyList())
        when (response["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "refusal" -> throw ClaudeException("Claude declined to write this story. Try a different topic.")
            "max_tokens" -> throw ClaudeException("The story was cut off. Try a shorter length.")
        }
        messages += buildJsonObject {
            put("role", "assistant")
            put("content", content)
        }
        val reply = content.filter { it.type == "text" }.joinToString("") { it.jsonObject["text"]!!.jsonPrimitive.content }
        if (reply.isBlank()) throw ClaudeException("Claude's reply was empty.")
        return reply
    }

    private val JsonElement.type get() = jsonObject["type"]?.jsonPrimitive?.contentOrNull

    companion object {
        private val RETRY_DELAYS = longArrayOf(2_000, 6_000)

        /** The story server's own message, which is written to be shown as is, or a fallback. */
        fun errorMessage(status: Int, body: String): String {
            val message = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
            }.getOrNull()?.takeIf { it.isNotBlank() }
            return message ?: when (status) {
                401 -> "Your sign-in has expired. Sign in again."
                429 -> "You've written a lot of stories lately. Try again later."
                502, 503, 504 -> "The story server is busy right now. Try again in a minute."
                else -> "The story server returned an error ($status)."
            }
        }
    }
}
