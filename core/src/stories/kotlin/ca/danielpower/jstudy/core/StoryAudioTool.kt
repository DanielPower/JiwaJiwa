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
 * in dollars. The key comes from FISH_API_KEY. Each clip is leveled to the
 * same loudness with ffmpeg, as some voices are much quieter than others.
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
    val bytes = missing.values.sumOf { it.text.toByteArray().size }
    val cost = bytes * StoryAudio.DOLLARS_PER_BYTE
    println("${wanted.size} sentences recorded or to record: ${missing.size} to record (${bytes} bytes, about $%.2f), ${unused.size} clips unused".format(cost))

    if (!record) return
    for (file in unused.values) file.delete()
    if (missing.isEmpty()) return
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
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
        val failed = AtomicInteger()
        val done = AtomicInteger()
        val pool = Executors.newFixedThreadPool(4)
        for ((key, take) in takes) pool.execute {
            val body = buildJsonObject {
                put("text", take.text)
                put("reference_id", take.voice)
                // Uncompressed, as it's leveled and compressed here.
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
            var ok = false
            for (attempt in 1..4) {
                val response = runCatching { client.send(request, HttpResponse.BodyHandlers.ofByteArray()) }.getOrNull()
                if (response != null && response.statusCode() == 200 && response.body().isNotEmpty()) {
                    val wav = File(dir, "$key.wav")
                    wav.writeBytes(response.body())
                    ok = level(wav, File(dir, "$key.${Recordings.EXTENSION}"))
                    if (!ok) System.err.println("${take.page}: couldn't compress \"${take.text}\"")
                    wav.delete()
                    break
                }
                if (response != null && response.statusCode() !in RETRY) {
                    System.err.println("${take.page}: ${response.statusCode()} ${response.body().decodeToString().take(200)}")
                    break
                }
                Thread.sleep(2000L shl attempt)
            }
            if (!ok) failed.incrementAndGet()
            val n = done.incrementAndGet()
            if (n % 50 == 0 || n == takes.size) println("Recorded $n of ${takes.size}")
        }
        pool.shutdown()
        pool.awaitTermination(6, java.util.concurrent.TimeUnit.HOURS)
        return failed.get()
    }

    /** How loud every clip is, in LUFS: Fish's voices range from -13 to -40. */
    const val LOUDNESS = -18

    /**
     * Brings [wav] to [LOUDNESS], measured first so the whole clip is turned
     * up or down evenly, and compresses it into [out]. Written under a
     * temporary name, so a stopped run never leaves half a clip.
     */
    fun level(wav: File, out: File): Boolean {
        val tmp = File(out.parentFile, "${out.name}.tmp")
        // A clip that can't be leveled is still kept as it is: it's been paid for.
        val ok = listOfNotNull(leveling(wav), "anull").any { filter ->
            ffmpeg("-y", "-i", wav.path, "-af", filter, "-ar", "48000", "-ac", "1", "-c:a", "libopus", "-b:a", "${Recordings.BITRATE}", "-f", "ogg", tmp.path) != null
        }
        if (!ok) tmp.delete()
        return ok && tmp.renameTo(out)
    }

    /**
     * The filter that levels [wav]. A clip too short or quiet to measure its
     * loudness (a whispered 「…はい。」) is turned up by its peak instead, to
     * where the peaks of a clip at [LOUDNESS] usually are.
     */
    private fun leveling(wav: File): String? {
        val target = "I=$LOUDNESS:TP=-1.5:LRA=11"
        val measured = ffmpeg("-i", wav.path, "-af", "loudnorm=$target:print_format=json", "-f", "null", "-") ?: return null
        val stats = runCatching {
            Json.parseToJsonElement(measured.substring(measured.lastIndexOf('{'), measured.lastIndexOf('}') + 1)).jsonObject
        }.getOrNull() ?: return null
        fun stat(name: String) = stats[name]?.jsonPrimitive?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }
        val loudness = stat("input_i")
        val peak = stat("input_tp") ?: return null
        if (loudness == null) return "volume=${"%.2f".format(java.util.Locale.ROOT, PEAK - peak)}dB"
        return "loudnorm=$target:linear=true:measured_I=$loudness:measured_TP=$peak" +
            ":measured_LRA=${stat("input_lra") ?: 0.0}:measured_thresh=${stat("input_thresh") ?: (loudness - 10)}:offset=${stat("target_offset") ?: 0.0}"
    }

    /** Where a clip's peaks are, in dB, when it's at [LOUDNESS]. */
    private const val PEAK = -6.0

    /** Runs ffmpeg, giving what it printed, or null when it failed. */
    private fun ffmpeg(vararg args: String): String? {
        val process = ProcessBuilder(listOf("ffmpeg", "-hide_banner", "-nostats") + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return output.takeIf { process.waitFor() == 0 }
    }

    // Rate limits and server errors are worth another try; anything else isn't.
    private val RETRY = setOf(408, 429, 500, 502, 503, 504)
}
