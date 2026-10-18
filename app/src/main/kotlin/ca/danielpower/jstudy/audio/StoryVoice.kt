package ca.danielpower.jstudy.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import ca.danielpower.jstudy.core.PhoneVoice
import ca.danielpower.jstudy.core.SpokenVoice
import ca.danielpower.jstudy.core.Voices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** A line to say, in a voice. */
data class SpokenLine(val text: String, val voice: SpokenVoice)

/**
 * The phone's text-to-speech, reading a story a line at a time, so each line
 * can have its speaker's voice and playback can start at any of them.
 */
class StoryVoice(context: Context) {
    data class State(
        /** The engine has started. */
        val ready: Boolean = false,
        /** Started, but with no Japanese voice to speak in. */
        val missing: Boolean = false,
        val voices: List<PhoneVoice> = emptyList(),
        /** What's being read, by whoever asked: a story's id. */
        val key: Long? = null,
        /** The line being read, or the one reading stopped at. */
        val line: Int? = null,
        val playing: Boolean = false,
        /** Where the engine is in the line's text, when it says. */
        val offset: Int? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var byName: Map<String, Voice> = emptyMap()
    private var lines: List<SpokenLine> = emptyList()
    private var rate = 1f
    // Counts each start, so callbacks from lines already stopped are ignored.
    private var run = 0
    // Asked for before the engine had started.
    private var pending: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> main.post { started(status) } }

    private fun started(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _state.update { it.copy(ready = true, missing = true) }
            return
        }
        val language = tts.setLanguage(Locale.JAPAN)
        // Installed Japanese voices, one of each: a voice on the phone over the same one online.
        val japanese = tts.voices.orEmpty()
            .filter { it.locale.language == "ja" && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            .groupBy { it.name.removeSuffix("-local").removeSuffix("-network") }
            .map { (_, same) -> same.firstOrNull { !it.isNetworkConnectionRequired } ?: same.first() }
            .sortedBy { it.name }
        byName = japanese.associateBy { it.name }
        val missing = language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED
        _state.update { it.copy(ready = true, missing = missing, voices = japanese.map { v -> PhoneVoice(v.name, Voices.kindOf(v.name)) }) }
        tts.setOnUtteranceProgressListener(listener)
        pending?.invoke()
        pending = null
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {}

        override fun onDone(utteranceId: String) {
            main.post {
                val (r, i) = parse(utteranceId) ?: return@post
                if (r != run || !_state.value.playing) return@post
                if (i + 1 < lines.size) speak(i + 1)
                else _state.update { it.copy(playing = false, line = null, offset = null) }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            main.post {
                val (r, _) = parse(utteranceId) ?: return@post
                if (r == run) _state.update { it.copy(playing = false, offset = null) }
            }
        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            main.post {
                val (r, i) = parse(utteranceId) ?: return@post
                if (r == run && _state.value.line == i) _state.update { it.copy(offset = start) }
            }
        }
    }

    private fun parse(id: String): Pair<Int, Int>? {
        val parts = id.split(':')
        if (parts.size != 3 || parts[0] != "line") return null
        return parts[1].toInt() to parts[2].toInt()
    }

    private fun whenReady(block: () -> Unit) {
        if (_state.value.ready) block() else pending = block
    }

    /** Read the [lines] made for the phone's voices, for [key], from line [from] on. */
    fun play(key: Long, from: Int, rate: Float, lines: (List<PhoneVoice>) -> List<SpokenLine>) = whenReady {
        if (_state.value.missing) return@whenReady
        val made = lines(_state.value.voices)
        if (from !in made.indices) return@whenReady
        this.lines = made
        this.rate = rate
        run++
        _state.update { it.copy(key = key, playing = true) }
        speak(from)
    }

    private fun speak(i: Int) {
        val line = lines[i]
        line.voice.name?.let(byName::get)?.let { tts.voice = it }
        tts.setPitch(line.voice.pitch)
        tts.setSpeechRate(rate)
        _state.update { it.copy(line = i, offset = null) }
        tts.speak(line.text, TextToSpeech.QUEUE_FLUSH, null, "line:$run:$i")
    }

    /** Stop reading, keeping the line it stopped at to carry on from. */
    fun pause() {
        run++
        tts.stop()
        _state.update { it.copy(playing = false, offset = null) }
    }

    /** Stop reading and forget where it was. */
    fun stop() {
        run++
        tts.stop()
        _state.update { it.copy(playing = false, key = null, line = null, offset = null) }
    }

    /** Forget where a paused reading stopped, so the next one starts afresh. */
    fun forgetPlace() {
        if (!_state.value.playing) _state.update { it.copy(line = null, offset = null) }
    }

    /** Say [text] on its own, as a word card's speaker button does, stopping any reading. */
    fun say(text: String, rate: Float, voiceFor: (List<PhoneVoice>) -> SpokenVoice) = whenReady {
        if (_state.value.missing) return@whenReady
        if (_state.value.playing) pause()
        run++
        val voice = voiceFor(_state.value.voices)
        voice.name?.let(byName::get)?.let { tts.voice = it }
        tts.setPitch(voice.pitch)
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word:$run")
    }

    fun shutdown() {
        main.removeCallbacksAndMessages(null)
        tts.stop()
        tts.shutdown()
    }
}
