package ca.danielpower.jstudy.core

import java.security.MessageDigest

/**
 * The Fish Audio voices the stories are recorded in (tools/stories/voices.json):
 * the narrator's (a story can have its own), each of the cast's by name, and
 * one for each kind of voice, for anyone else. A blank id isn't recorded, and
 * the phone's voice reads those lines.
 */
data class RecordedVoices(
    val model: String = "",
    val narrator: String = "",
    val cast: Map<String, String> = emptyMap(),
    val labels: Map<VoiceKind, String> = emptyMap(),
    /** How much to turn each voice up or down, in dB, so they're all as loud (tools/stories/levels.json). */
    val gains: Map<String, Double> = emptyMap(),
)

/**
 * A line's recording: its path (audio/<key>.opus, on the audio server, or
 * in the app's assets when it's [bundled]), and the gain to play it at, in dB.
 */
data class Recording(val file: String, val gain: Float, val bundled: Boolean = false)

/**
 * Recorded sentences: each is a clip on the audio server (audio/), named by a
 * hash of what was recorded, so a sentence is recorded again only when its
 * text, voice or model changes, and the app can find a line's clip from its
 * text alone. Kana's clips come with the app, in its assets' audio/.
 */
object Recordings {
    const val DIR = "audio"
    const val EXTENSION = "opus"
    /** Bits a second: plenty for one voice, so the clips stay small. */
    const val BITRATE = 24000

    /** The clip for [text] said in [voice] by [model]. */
    fun key(text: String, voice: String, model: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$model\n$voice\n$BITRATE\n$text".toByteArray())
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    fun file(key: String) = "$DIR/$key.$EXTENSION"

    /**
     * The Fish voice a line is recorded in: the narrator's for narration (the
     * story's own [narrator], or the stories' usual one), a cast member's
     * own, or else the one for their kind of voice, which is also how someone
     * unnamed (店員, 子供) is recorded. Null when there's none.
     */
    fun voice(speaker: String?, voices: RecordedVoices, cast: Map<String, CastMember>, narrator: String? = null): String? {
        if (speaker == null) return narrator ?: voices.narrator.ifBlank { null }
        voices.cast[speaker]?.ifBlank { null }?.let { return it }
        val kind = cast[speaker]?.voice?.kind ?: Voices.forLabel(speaker).kind
        return voices.labels[kind]?.ifBlank { null }
    }

    /**
     * The voice and model kana's sounds were recorded in, once: the clips come
     * with the app and aren't recorded again, so they keep these even if the
     * stories' narrator changes.
     */
    const val KANA_VOICE = "92c556e1a13e4ac7add3d1a8665c3cb8"
    const val KANA_MODEL = "s2.1-pro"

    /** The clip of a kana's sound for kana practice, said on its own, and the voice it's in. */
    fun kana(text: String): Pair<String, String> = key(text, KANA_VOICE, KANA_MODEL) to KANA_VOICE

    /** Each line's clip, or null where it isn't recorded. */
    fun keys(lines: List<NarrationLine>, voices: RecordedVoices, cast: Map<String, CastMember>, narrator: String? = null): List<String?> =
        clips(lines, voices, cast, narrator).map { it?.first }

    /** Each line's clip and the voice it's in, or null where it isn't recorded. */
    fun clips(lines: List<NarrationLine>, voices: RecordedVoices, cast: Map<String, CastMember>, narrator: String? = null): List<Pair<String, String>?> =
        if (voices.model.isBlank()) lines.map { null }
        else lines.map { line -> voice(line.speaker, voices, cast, narrator)?.let { key(line.text, it, voices.model) to it } }
}
