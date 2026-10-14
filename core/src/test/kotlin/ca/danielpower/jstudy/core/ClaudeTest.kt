package ca.danielpower.jstudy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaudeTest {
    private val ok = """{"content":[{"type":"text","text":"話"}],"stop_reason":"end_turn"}"""

    @Test
    fun `sends the system prompt, messages and effort, and nothing the server decides`() {
        var sent = ""
        val claude = ClaudeConversation(effort = "low", send = { sent = it; 200 to ok })
        claude.start("sys", "hi")
        val body = Json.parseToJsonElement(sent).jsonObject
        assertEquals(setOf("system", "messages", "effort"), body.keys)
        assertEquals("low", body["effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `starting again clears the history`() {
        val bodies = ArrayList<String>()
        val claude = ClaudeConversation(send = { bodies += it; 200 to ok })
        claude.start("one", "a")
        claude.reply("b")
        claude.start("two", "c")
        val last = Json.parseToJsonElement(bodies.last()).jsonObject
        assertEquals("two", last["system"]!!.jsonPrimitive.content)
        assertEquals(1, last["messages"]!!.jsonArray.size)
    }

    @Test
    fun `joins every text block of a reply`() {
        val reply = """{"content":[{"type":"text","text":"一"},{"type":"thinking","thinking":"x","signature":"s"},{"type":"text","text":"二"}],"stop_reason":"end_turn"}"""
        assertEquals("一二", ClaudeConversation(send = { 200 to reply }).start("s", "m"))
    }

    @Test
    fun `retries when the server is briefly unavailable`() {
        var calls = 0
        val claude = ClaudeConversation(send = { if (++calls == 1) 503 to "" else 200 to ok })
        assertEquals("話", claude.start("s", "m"))
        assertEquals(2, calls)
    }

    @Test
    fun `doesn't retry when a limit is reached, and shows the server's message`() {
        var calls = 0
        val limit = """{"type":"error","error":{"type":"rate_limit_error","message":"You've reached today's story limit. Try again tomorrow."}}"""
        val claude = ClaudeConversation(send = { calls++; 429 to limit })
        val e = assertFailsWith<ClaudeException> { claude.start("s", "m") }
        assertEquals(1, calls)
        assertEquals("You've reached today's story limit. Try again tomorrow.", e.message)
        assertFalse(e.signInNeeded)
    }

    @Test
    fun `a rejected session asks for sign-in again`() {
        val e = assertFailsWith<ClaudeException> { ClaudeConversation(send = { 401 to "" }).start("s", "m") }
        assertTrue(e.signInNeeded)
        assertTrue(e.message!!.contains("Sign in"))
    }

    @Test
    fun `cut off and empty replies are errors`() {
        val cut = ClaudeConversation(send = { 200 to """{"content":[{"type":"text","text":"一"}],"stop_reason":"max_tokens"}""" })
        assertTrue(assertFailsWith<ClaudeException> { cut.start("s", "m") }.message!!.contains("cut off"))
        val empty = ClaudeConversation(send = { 200 to """{"content":[{"type":"text","text":"  "}],"stop_reason":"end_turn"}""" })
        assertTrue(assertFailsWith<ClaudeException> { empty.start("s", "m") }.message!!.contains("empty"))
    }

    @Test
    fun `error messages fall back to the status when the server gives none`() {
        assertEquals("no access", ClaudeConversation.errorMessage(403, """{"error":{"message":"no access"}}"""))
        assertTrue(ClaudeConversation.errorMessage(503, "<html>").contains("busy"))
        assertEquals("The story server returned an error (500).", ClaudeConversation.errorMessage(500, ""))
    }

    @Test
    fun `the story server signs in, reports usage and signs out`() {
        val calls = ArrayList<String>()
        val server = StoryServer("https://stories.example.com/") { method, url, token, body ->
            calls += "$method $url $token"
            when (method to url.substringAfter(".com")) {
                "POST" to "/v1/sessions" -> {
                    assertTrue(body!!.contains("google-token"))
                    200 to """{"token":"t1","expires_at":1,"email":"a@example.com"}"""
                }
                "GET" to "/v1/usage" -> 200 to """{"tokens_last_day":{"used":50000,"limit":200000},"requests_last_hour":{"used":1,"limit":20}}"""
                "DELETE" to "/v1/sessions" -> 204 to ""
                else -> 404 to ""
            }
        }
        assertEquals(StorySession("t1", "a@example.com"), server.signIn("google-token"))
        assertEquals(0.25, server.usage("t1").usedToday)
        server.signOut("t1")
        assertEquals(
            listOf(
                "POST https://stories.example.com/v1/sessions null",
                "GET https://stories.example.com/v1/usage t1",
                "DELETE https://stories.example.com/v1/sessions t1",
            ),
            calls,
        )
    }

    @Test
    fun `story requests go to the server with the session`() {
        var seen: Pair<String, String?>? = null
        val server = StoryServer("https://s.example.com") { _, url, token, _ -> seen = url to token; 200 to ok }
        assertEquals("話", server.conversation("t1").start("s", "m"))
        assertEquals("https://s.example.com/v1/messages" to "t1", seen)
    }

    @Test
    fun `a build without a server address says so`() {
        val e = assertFailsWith<ClaudeException> { StoryServer("").signIn("x") }
        assertTrue(e.message!!.contains("set up"))
    }
}
