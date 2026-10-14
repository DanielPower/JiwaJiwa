package ca.danielpower.jstudy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A sign-in to the story server. */
data class StorySession(val token: String, val email: String)

/** How much of today's story allowance is spent, from 0 to 1. */
data class StoryUsage(val usedToday: Double)

/**
 * The story server (`server/` in this repo): it swaps a Google sign-in for
 * a session, then writes stories through Claude for that session, within
 * each account's limits.
 */
class StoryServer(
    private val baseUrl: String,
    private val http: (method: String, url: String, token: String?, body: String?) -> Pair<Int, String> = ::request,
) {
    /** Swap a Google ID token for a session. */
    fun signIn(googleIdToken: String): StorySession {
        val body = buildJsonObject { put("id_token", googleIdToken) }.toString()
        val json = call("POST", "/v1/sessions", null, body)
        return StorySession(json["token"]!!.jsonPrimitive.content, json["email"]!!.jsonPrimitive.content)
    }

    fun signOut(token: String) {
        call("DELETE", "/v1/sessions", token, null)
    }

    fun usage(token: String): StoryUsage {
        val tokens = call("GET", "/v1/usage", token, null)["tokens_last_day"]!!.jsonObject
        val limit = tokens["limit"]!!.jsonPrimitive.long
        val used = tokens["used"]!!.jsonPrimitive.long
        return StoryUsage(if (limit > 0) (used.toDouble() / limit).coerceIn(0.0, 1.0) else 1.0)
    }

    /** A conversation with Claude for this session. */
    fun conversation(token: String, effort: String = "high"): ClaudeConversation =
        ClaudeConversation(effort) { body -> http("POST", url("/v1/messages"), token, body) }

    private fun call(method: String, path: String, token: String?, body: String?) = run {
        val (status, text) = http(method, url(path), token, body)
        if (status !in 200..299) throw ClaudeException(ClaudeConversation.errorMessage(status, text), signInNeeded = status == 401)
        if (text.isBlank()) buildJsonObject {} else Json.parseToJsonElement(text).jsonObject
    }

    private fun url(path: String): String {
        if (baseUrl.isBlank()) throw ClaudeException("Stories aren't set up in this build of the app.")
        return baseUrl.trimEnd('/') + path
    }

    companion object {
        fun request(method: String, url: String, token: String?, body: String?): Pair<Int, String> {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 30_000
                // A long story with thinking can take a few minutes.
                connection.readTimeout = 600_000
                token?.let { connection.setRequestProperty("authorization", "Bearer $it") }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("content-type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                return status to text
            } catch (e: IOException) {
                throw ClaudeException("Couldn't reach the story server. Check your connection.")
            } finally {
                connection.disconnect()
            }
        }
    }
}
