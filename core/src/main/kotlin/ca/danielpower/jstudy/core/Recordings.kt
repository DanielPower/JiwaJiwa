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
)

/**
 * Recorded sentences: each is a clip in the app's assets (audio/), named by a
 * hash of what was recorded, so a sentence is recorded again only when its
 * text, voice or model changes, and clips no sentence uses can be told apart.
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

    /** Each line's clip, or null where it isn't recorded. */
    fun keys(lines: List<NarrationLine>, voices: RecordedVoices, cast: Map<String, CastMember>, narrator: String? = null): List<String?> =
        if (voices.model.isBlank()) lines.map { null }
        else lines.map { line -> voice(line.speaker, voices, cast, narrator)?.let { key(line.text, it, voices.model) } }
}
