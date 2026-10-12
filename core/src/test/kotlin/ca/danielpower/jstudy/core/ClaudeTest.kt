package ca.danielpower.jstudy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClaudeTest {
    private val ok = """{"content":[{"type":"text","text":"話"}],"stop_reason":"end_turn"}"""

    @Test
    fun `sends the model, effort and token limit`() {
        var sent = ""
        val claude = ClaudeConversation("key", model = "m", effort = "low", send = { sent = it; 200 to ok })
        claude.start("sys", "hi")
        val body = Json.parseToJsonElement(sent).jsonObject
        assertEquals("m", body["model"]!!.jsonPrimitive.content)
        assertEquals("low", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals(16000, body["max_tokens"]!!.jsonPrimitive.int)
    }

    @Test
    fun `starting again clears the history`() {
        val bodies = ArrayList<String>()
        val claude = ClaudeConversation("key", send = { bodies += it; 200 to ok })
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
        assertEquals("一二", ClaudeConversation("key", send = { 200 to reply }).start("s", "m"))
    }

    @Test
    fun `retries when Claude is overloaded`() {
        var calls = 0
        val claude = ClaudeConversation("key", send = { if (++calls == 1) 529 to "" else 200 to ok })
        assertEquals("話", claude.start("s", "m"))
        assertEquals(2, calls)
    }

    @Test
    fun `doesn't retry a request Claude rejects`() {
        var calls = 0
        val claude = ClaudeConversation("key", send = { calls++; 400 to """{"error":{"message":"bad request"}}""" })
        val e = assertFailsWith<ClaudeException> { claude.start("s", "m") }
        assertEquals(1, calls)
        assertEquals("Claude returned an error (400): bad request", e.message)
    }

    @Test
    fun `cut off and empty replies are errors`() {
        val cut = ClaudeConversation("key", send = { 200 to """{"content":[{"type":"text","text":"一"}],"stop_reason":"max_tokens"}""" })
        assertTrue(assertFailsWith<ClaudeException> { cut.start("s", "m") }.message!!.contains("cut off"))
        val empty = ClaudeConversation("key", send = { 200 to """{"content":[{"type":"text","text":"  "}],"stop_reason":"end_turn"}""" })
        assertTrue(assertFailsWith<ClaudeException> { empty.start("s", "m") }.message!!.contains("empty"))
    }

    @Test
    fun `error messages name the problem`() {
        assertEquals("This Claude API key isn't allowed to do that: no access", ClaudeConversation.errorMessage(403, """{"error":{"message":"no access"}}"""))
        assertEquals("This Claude API key isn't allowed to do that.", ClaudeConversation.errorMessage(403, "not json"))
        assertTrue(ClaudeConversation.errorMessage(429, "").contains("rate limiting"))
        assertTrue(ClaudeConversation.errorMessage(529, "").contains("overloaded"))
        assertEquals("Claude returned an error (500).", ClaudeConversation.errorMessage(500, ""))
    }
}
