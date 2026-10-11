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
import java.net.HttpURLConnection
import java.net.URL

class ClaudeException(message: String) : IOException(message)

/**
 * A conversation with Claude over the Messages API, sent as plain HTTPS
 * requests. Each reply's content blocks go back unchanged in the next
 * request, as the API requires for thinking blocks.
 */
class ClaudeConversation(
    private val apiKey: String,
    private val model: String = MODEL,
    private val effort: String = "high",
    private val send: (body: String) -> Pair<Int, String> = { postMessages(apiKey, it) },
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
            put("model", model)
            put("max_tokens", 16000)
            put("system", system)
            put("messages", JsonArray(messages))
            put("output_config", buildJsonObject { put("effort", effort) })
            // If a safety classifier declines, the API retries on its recommended fallback model.
            put("fallbacks", "default")
        }.toString()

        var attempt = 0
        while (true) {
            val (status, text) = send(body)
            if (status == 200) return read(text)
            val retryable = status == 429 || status == 529 || status >= 500
            if (!retryable || attempt >= RETRY_DELAYS.size) throw ClaudeException(errorMessage(status, text))
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
        const val MODEL = "claude-opus-5-5"
        private const val URL_MESSAGES = "https://api.anthropic.com/v1/messages"
        private val RETRY_DELAYS = longArrayOf(2_000, 6_000)

        fun errorMessage(status: Int, body: String): String {
            val detail = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
            }.getOrNull()
            return when (status) {
                401 -> "The Claude API key was rejected. Check it in Settings."
                403 -> "This Claude API key isn't allowed to do that" + (detail?.let { ": $it" } ?: ".")
                429 -> "Claude is rate limiting this key. Try again in a minute."
                529 -> "Claude is overloaded right now. Try again in a minute."
                else -> "Claude returned an error ($status)" + (detail?.let { ": $it" } ?: ".")
            }
        }

        fun postMessages(apiKey: String, body: String): Pair<Int, String> {
            val connection = URL(URL_MESSAGES).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 30_000
                // A long story with thinking can take a few minutes.
                connection.readTimeout = 600_000
                connection.doOutput = true
                connection.setRequestProperty("content-type", "application/json")
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
                connection.setRequestProperty("anthropic-beta", "server-side-fallback-2026-07-01")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                return status to text
            } catch (e: IOException) {
                throw ClaudeException("Couldn't reach Claude: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                connection.disconnect()
            }
        }
    }
}

