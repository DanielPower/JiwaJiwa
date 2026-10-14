package ca.danielpower.jstudy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StoriesTest {
    private val ids = mapOf("猫" to 1L, "本" to 2L, "読む" to 3L, "好き" to 4L, "犬" to 5L, "魚" to 6L, "鳥" to 7L, "食べる" to 8L)
    private val reader = Reader { text, reading -> ids[text]?.let { LexiconMatch(it, reading.orEmpty()) } }
    private val known = setOf(1L, 2L, 3L, 4L)

    private fun request(difficulty: StoryDifficulty = StoryDifficulty.EASY, introduce: List<VocabWord> = emptyList()) =
        StoryRequest("cats", StoryLength.SHORT, difficulty, 5, listOf(VocabWord(1, "猫", "ねこ")), emptyList(), introduce)

    @Test
    fun coverageCountsDistinctNewWords() {
        val cov = coverage(reader.read("猫は犬が好きです。犬と魚を食べる。タロウは本を読む。"), known::contains)
        assertEquals(listOf("犬", "魚", "食べる"), cov.newWords.map { it.lemma })
        // 猫 犬 好き 犬 魚 食べる 本 読む; タロウ is a name.
        assertEquals(8, cov.words)
        assertEquals(4, cov.knownWords)
    }

    @Test
    fun parsesTitleAndBody() {
        assertEquals(Story("猫の本", "猫は本が好きです。\n\n本を読む。"), parseStory("「猫の本」\n\n猫は本が好きです。\n\n本を読む。\n"))
        assertEquals(Story("", "猫は本が好きです。"), parseStory("猫は本が好きです。"))
    }

    @Test
    fun budgetsGrowWithLengthAndDifficulty() {
        assertEquals(1, newWordBudget(StoryLength.SHORT, StoryDifficulty.EASY))
        assertEquals(15, newWordBudget(StoryLength.LONG, StoryDifficulty.HARD))
        for (l in StoryLength.entries) {
            assertTrue(newWordBudget(l, StoryDifficulty.EASY) < newWordBudget(l, StoryDifficulty.NORMAL))
            assertTrue(newWordBudget(l, StoryDifficulty.NORMAL) < newWordBudget(l, StoryDifficulty.HARD))
        }
    }

    private class FakeModel(val replies: List<String>) : StoryModel {
        val sent = ArrayList<String>()
        override fun start(system: String, message: String) = reply(message)
        override fun reply(message: String): String {
            sent += message
            return replies[sent.size - 1]
        }
    }

    @Test
    fun rewritesWhenOverBudgetAndKeepsTheBest() {
        val model = FakeModel(listOf("題\n\n猫は犬と魚と鳥が好き。", "題\n\n猫は犬と魚が好き。", "題\n\n猫は魚と鳥が好き。"))
        val written = StoryWriter(model, reader).write(request(), known::contains)
        assertEquals(3, model.sent.size)
        assertTrue(model.sent[1].contains("犬、魚、鳥"))
        assertEquals("猫は犬と魚が好き。", written.story.body)
        assertEquals(1, written.rewrites)
    }

    @Test
    fun targetWordsDoNotTriggerRewrites() {
        val model = FakeModel(listOf("題\n\n猫は鳥が好き。"))
        val written = StoryWriter(model, reader).write(request(introduce = listOf(VocabWord(7, "鳥", "とり"))), known::contains)
        assertEquals(1, model.sent.size)
        assertEquals(listOf(7L), written.coverage.newWords.map { it.wordId })
    }

    @Test
    fun requestListsTheWords() {
        val text = StoryPrompts.request(request(StoryDifficulty.HARD, listOf(VocabWord(7, "鳥", "とり"))))
        assertTrue(text.contains("Introduce: 鳥(とり)"))
        assertTrue(text.contains("Other words allowed beyond the lists: 3."))
        assertTrue(text.contains("Known words: 猫"))
        assertTrue(text.contains("JLPT N5"))
    }

    @Test
    fun aSequelRequestCarriesTheStorySoFar() {
        val parts = listOf(Story("猫の本", "猫は本が好き。"), Story("猫の本", "猫は本を読む。"))
        val text = StoryPrompts.request(request().copy(previous = parts))
        assertTrue(text.contains("next part"))
        assertTrue(text.indexOf("Part 1: 猫の本\n\n猫は本が好き。") < text.indexOf("Part 2: 猫の本\n\n猫は本を読む。"))
        assertTrue(!StoryPrompts.request(request()).contains("story so far"))
    }

    @Test
    fun partsAreTitledWithTheirNumber() {
        assertEquals("猫の本: Part 2", partTitle("猫の本", 2))
        assertEquals("猫の本", partTitle("猫の本", null))
    }

    @Test
    fun readStatsCountCharactersButNotPunctuation() {
        val paragraphs = reader.read("猫は本が好き。「犬だ」")
        val stats = readStats(paragraphs, coverage(paragraphs, known::contains), millis = 60_000)
        assertEquals(8, stats.characters)
        assertEquals(8, stats.charactersPerMinute)
        assertEquals(3, stats.knownWords)
        assertEquals(4, stats.words)
        assertEquals(null, readStats(paragraphs, coverage(paragraphs, known::contains), millis = 5_000).charactersPerMinute)
    }

    @Test
    fun claudeConversationSendsHistoryBack() {
        val bodies = ArrayList<String>()
        val replies = ArrayDeque(
            listOf(
                """{"content":[{"type":"thinking","thinking":"","signature":"s"},{"type":"text","text":"一"}],"stop_reason":"end_turn"}""",
                """{"content":[{"type":"text","text":"二"}],"stop_reason":"end_turn"}""",
            ),
        )
        val claude = ClaudeConversation(send = { body -> bodies += body; 200 to replies.removeFirst() })
        assertEquals("一", claude.start("sys", "hello"))
        assertEquals("二", claude.reply("again"))
        val second = Json.parseToJsonElement(bodies[1]).jsonObject
        assertEquals("sys", second["system"]!!.jsonPrimitive.content)
        val messages = second["messages"]!!.jsonArray
        assertEquals(listOf("user", "assistant", "user"), messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        // The thinking block goes back unchanged.
        assertEquals("s", messages[1].jsonObject["content"]!!.jsonArray[0].jsonObject["signature"]!!.jsonPrimitive.content)
    }

    @Test
    fun claudeErrorsAreReadable() {
        val claude = ClaudeConversation(send = { 401 to """{"type":"error","error":{"type":"authentication_error","message":"Your sign-in has expired. Sign in again."}}""" })
        val e = assertFailsWith<ClaudeException> { claude.start("s", "m") }
        assertTrue(e.message!!.contains("Sign in again"))
        val refusing = ClaudeConversation(send = { 200 to """{"content":[],"stop_reason":"refusal"}""" })
        assertFailsWith<ClaudeException> { refusing.start("s", "m") }
    }
}
