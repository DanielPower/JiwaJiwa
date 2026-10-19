package ca.danielpower.jstudy.core

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.system.exitProcess

/**
 * Records the stories' sentences with Fish Audio into the app's assets
 * (app/src/main/assets/audio), for the repository at the first argument.
 * Only sentences without a clip are recorded, since each costs money, and
 * clips no sentence uses any more are deleted. Without "record" it only says
 * what it would do. It stops before spending more than the second argument,
 * in dollars. The key comes from FISH_API_KEY. Fish's voices differ a lot
 * in loudness, so each voice is measured once on the same passage, and the
 * gain that evens them out goes in tools/stories/levels.json for the app to
 * play every clip in that voice at: a shout stays louder than a whisper, and
 * a gain can be changed without recording again. Needs ffmpeg.
 */
fun main(args: Array<String>) {
    val root = File(args[0])
    val limit = args.getOrNull(1)?.toDoubleOrNull() ?: 5.0
    val record = "record" in args
    val dir = File(root, "app/src/main/assets/${Recordings.DIR}")

    val (model, wanted) = StoryTools.open(root).use { tools -> tools.catalog.voices.model to StoryAudio.wanted(tools) }
    val have = dir.listFiles { f -> f.name.endsWith(".${Recordings.EXTENSION}") }.orEmpty().associateBy { it.name.substringBefore('.') }
    val missing = wanted.filterKeys { it !in have }
    val unused = have.filterKeys { it !in wanted }
    val levels = File(root, "tools/stories/levels.json")
    val gains = StoryAudio.readGains(levels, model).toMutableMap()
    val unmeasured = wanted.values.map { it.voice }.distinct().filter { it !in gains }
    val bytes = missing.values.sumOf { it.text.toByteArray().size } + unmeasured.size * StoryAudio.REFERENCE.toByteArray().size
    val cost = bytes * StoryAudio.DOLLARS_PER_BYTE
    println(
        "${wanted.size} sentences recorded or to record: ${missing.size} to record and ${unmeasured.size} voices to measure " +
            "(${bytes} bytes, about $%.2f), ${unused.size} clips unused".format(cost),
    )

    if (!record) return
    for (file in unused.values) file.delete()
    if (missing.isEmpty() && unmeasured.isEmpty()) return
    val key = System.getenv("FISH_API_KEY").orEmpty()
    if (key.isBlank()) {
        println("No FISH_API_KEY, so nothing is recorded.")
        return
    }
    if (cost > limit) {
        System.err.println("Recording would cost about $%.2f, over the $%.2f limit. Run it by hand with a higher limit to go ahead.".format(cost, limit))
        exitProcess(1)
    }
    dir.mkdirs()
    for (voice in unmeasured) {
        val gain = StoryAudio.measure(voice, model, key, dir) ?: continue
        println("Measured $voice: ${if (gain >= 0) "+" else ""}$gain dB")
        gains[voice] = gain
        StoryAudio.writeGains(levels, model, gains)
    }
    // The clips that were recorded are kept and committed whatever else failed, as they're paid for.
    // The rest are tried again on the next run.
    val failed = StoryAudio.record(missing, model, key, dir)
    if (failed > 0) println("::warning::$failed sentences couldn't be recorded; the next run tries them again.")
}

object StoryAudio {
    /** Fish Audio's price: $15 for a million bytes of text (UTF-8, so three a kana or kanji). */
    const val DOLLARS_PER_BYTE = 15.0 / 1_000_000

    /** A sentence to record: what to say, and in which voice. */
    data class Take(val text: String, val voice: String, val page: String)

    /** Every sentence the stories and grammar pages read aloud, by its clip's key. */
    fun wanted(tools: StoryTools): Map<String, Take> {
        val catalog = tools.catalog
        val cast = catalog.cast.associateBy { it.name }
        // Each page, with its story's narrator: grammar pages have the usual one.
        val pages = catalog.chapters.map { (s, c) -> c.story to s.narrator } + catalog.grammar.map { it.page to null }
        val out = LinkedHashMap<String, Take>()
        for ((page, narrator) in pages) {
            val lines = Narration.lines(tools.text.getValue(page.id), Speech.speakers(page.body))
            val keys = Recordings.keys(lines, catalog.voices, cast, narrator)
            lines.forEachIndexed { i, line ->
                val key = keys[i] ?: return@forEachIndexed
                out.getOrPut(key) { Take(line.text, Recordings.voice(line.speaker, catalog.voices, cast, narrator)!!, page.id) }
            }
        }
        return out
    }

    /** Records [takes] into [dir], a few at a time. Returns how many failed. */
    fun record(takes: Map<String, Take>, model: String, apiKey: String, dir: File): Int {
        val failed = AtomicInteger()
        val done = AtomicInteger()
        val pool = Executors.newFixedThreadPool(4)
        for ((key, take) in takes) pool.execute {
            val audio = speak(take.text, take.voice, model, apiKey) { System.err.println("${take.page}: $it") }
            var ok = false
            if (audio != null) {
                val wav = File(dir, "$key.wav")
                wav.writeBytes(audio)
                ok = compress(wav, File(dir, "$key.${Recordings.EXTENSION}"))
                if (!ok) System.err.println("${take.page}: couldn't compress \"${take.text}\"")
                wav.delete()
            }
            if (!ok) failed.incrementAndGet()
            val n = done.incrementAndGet()
            if (n % 50 == 0 || n == takes.size) println("Recorded $n of ${takes.size}")
        }
        pool.shutdown()
        pool.awaitTermination(6, java.util.concurrent.TimeUnit.HOURS)
        return failed.get()
    }

    /** [text] said in [voice], as uncompressed audio (compressed here, once), or null when Fish wouldn't. */
    fun speak(text: String, voice: String, model: String, apiKey: String, problem: (String) -> Unit): ByteArray? {
        val body = buildJsonObject {
            put("text", text)
            put("reference_id", voice)
            put("format", "wav")
            put("language", "ja")
        }.toString()
        val request = HttpRequest.newBuilder(URI("https://api.fish.audio/v1/tts"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("model", model)
            .timeout(Duration.ofMinutes(2))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        for (attempt in 1..4) {
            val response = runCatching { client.send(request, HttpResponse.BodyHandlers.ofByteArray()) }.getOrNull()
            if (response != null && response.statusCode() == 200 && response.body().isNotEmpty()) return response.body()
            if (response != null && response.statusCode() !in RETRY) {
                problem("${response.statusCode()} ${response.body().decodeToString().take(200)}")
                return null
            }
            Thread.sleep(2000L shl attempt)
        }
        problem("no answer from Fish")
        return null
    }

    /** Each voice's gain in dB, as measured for [model]; none when they were measured for another. */
    fun readGains(file: File, model: String): Map<String, Double> {
        if (!file.exists()) return emptyMap()
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        if (root["model"]?.jsonPrimitive?.content != model) return emptyMap()
        return root["gains"]?.jsonObject.orEmpty().mapValues { it.value.jsonPrimitive.double }
    }

    fun writeGains(file: File, model: String, gains: Map<String, Double>) {
        val json = buildJsonObject {
            put("model", model)
            put("gains", buildJsonObject { for ((voice, gain) in gains.toSortedMap()) put(voice, gain) })
        }
        file.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json) + "\n")
    }

    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

    /** How loud the app plays each voice, in LUFS: Fish's voices range from -13 to -40. */
    const val LOUDNESS = -18.0

    /**
     * What each voice is measured on: everyday narration and talk, so a voice
     * is turned up or down by how loud it usually is, and a shout in it stays
     * louder than a whisper.
     */
    const val REFERENCE = "今日は朝から雨が降っていた。駅まで歩いて、コーヒーを買った。「おはよう。今日も寒いね。」「うん、でも午後は晴れるって。」"

    /**
     * The gain that brings [voice] to [LOUDNESS], in dB, from [REFERENCE] said in it. Null when it couldn't be measured.
     */
    fun measure(voice: String, model: String, apiKey: String, dir: File): Double? {
        val audio = speak(REFERENCE, voice, model, apiKey) { System.err.println("measuring $voice: $it") } ?: return null
        val wav = File(dir, "$voice.reference.wav")
        wav.writeBytes(audio)
        val loudness = loudness(wav)
        wav.delete()
        return loudness?.let { Math.round((LOUDNESS - it) * 10) / 10.0 }
    }

    /** [wav]'s loudness, in LUFS. */
    fun loudness(wav: File): Double? {
        val printed = ffmpeg("-i", wav.path, "-af", "ebur128", "-f", "null", "-") ?: return null
        return Regex("""I:\s+(-?[0-9.]+) LUFS""").findAll(printed).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /** [wav] compressed into [out], under a temporary name so a stopped run never leaves half a clip. */
    fun compress(wav: File, out: File): Boolean {
        val tmp = File(out.parentFile, "${out.name}.tmp")
        val ok = ffmpeg("-y", "-i", wav.path, "-ar", "48000", "-ac", "1", "-c:a", "libopus", "-b:a", "${Recordings.BITRATE}", "-f", "ogg", tmp.path) != null
        if (!ok) tmp.delete()
        return ok && tmp.renameTo(out)
    }

    /** Runs ffmpeg, giving what it printed, or null when it failed. */
    private fun ffmpeg(vararg args: String): String? {
        val process = ProcessBuilder(listOf("ffmpeg", "-hide_banner", "-nostats") + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return output.takeIf { process.waitFor() == 0 }
    }

    // Rate limits and server errors are worth another try; anything else isn't.
    private val RETRY = setOf(408, 429, 500, 502, 503, 504)
}
