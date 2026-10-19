package ca.danielpower.jstudy.audio

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import ca.danielpower.jstudy.core.PhoneVoice
import ca.danielpower.jstudy.core.Recording
import ca.danielpower.jstudy.core.SpokenVoice
import ca.danielpower.jstudy.core.Voices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.pow

/** A line to say, in a voice: its recording when it has one, else the phone's voice. */
data class SpokenLine(val text: String, val voice: SpokenVoice, val clip: Recording? = null)

/**
 * Reads a story aloud a line at a time, so each line can have its speaker's
 * voice and playback can start at any of them: the line's recording when it
 * has one, else the phone's text-to-speech.
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
        /** Where the engine is in the line's text, when it says (worked out from the time for a recording). */
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

    // Plays the recordings; made the first time one plays.
    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var booster: LoudnessEnhancer? = null
    // The line the player is on, and the run it belongs to.
    private var playerLine: Pair<Int, Int>? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) playerLine?.let { (r, i) -> done(r, i) }
        }

        override fun onPlayerError(error: PlaybackException) {
            // A recording that won't play is read by the phone instead.
            val (r, i) = playerLine ?: return
            if (r == run) synthesize(i)
        }
    }

    // While a recording plays, where it's got to in the line, so the word being said can be underlined.
    private val tick = object : Runnable {
        override fun run() {
            val p = player ?: return
            val (r, i) = playerLine ?: return
            if (r != run || _state.value.line != i) return
            if (p.duration > 0) {
                val length = lines[i].text.length
                val offset = (p.currentPosition * length / p.duration).toInt().coerceIn(0, length - 1)
                if (_state.value.offset != offset) _state.update { it.copy(offset = offset) }
            }
            main.postDelayed(this, 100)
        }
    }

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
                done(r, i)
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

    /** Line [i] of run [r] has been said: on to the next, if it's still reading. */
    private fun done(r: Int, i: Int) {
        if (r != run || !_state.value.playing) return
        if (i + 1 < lines.size) speak(i + 1)
        else _state.update { it.copy(playing = false, line = null, offset = null) }
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
        val made = lines(_state.value.voices)
        // With no Japanese voice on the phone, only recordings can be read.
        if (_state.value.missing && made.all { it.clip == null }) return@whenReady
        if (from !in made.indices) return@whenReady
        this.lines = made
        this.rate = rate
        quiet()
        _state.update { it.copy(key = key, playing = true) }
        speak(from)
    }

    private fun speak(i: Int) {
        val clip = lines[i].clip
        _state.update { it.copy(line = i, offset = null) }
        if (clip != null) record(i, clip) else synthesize(i)
    }

    /** Line [i] from its recording, at its voice's gain. */
    @OptIn(UnstableApi::class)
    private fun record(i: Int, clip: Recording) {
        val p = player ?: ExoPlayer.Builder(appContext).build().also {
            it.addListener(playerListener)
            // Its own audio session, for the booster to turn up quiet voices in.
            val session = (appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
            it.audioSessionId = session
            booster = runCatching { LoudnessEnhancer(session) }.getOrNull()
            player = it
        }
        tts.stop()
        playerLine = run to i
        // The player can only turn a clip down, so a quiet voice is turned up by the booster, which also stops it clipping.
        val boost = booster
        if (clip.gain > 0 && boost != null) {
            p.volume = 1f
            boost.setTargetGain((clip.gain * 100).toInt())
            boost.enabled = true
        } else {
            boost?.enabled = false
            p.volume = 10f.pow(clip.gain.coerceAtMost(0f) / 20)
        }
        p.setMediaItem(MediaItem.fromUri("asset:///${clip.file}"))
        p.setPlaybackSpeed(rate)
        p.prepare()
        p.play()
        main.removeCallbacks(tick)
        main.post(tick)
    }

    /** Line [i] in the phone's voice; skipped when the phone has none. */
    private fun synthesize(i: Int) {
        val line = lines[i]
        playerLine = null
        player?.stop()
        if (_state.value.missing) {
            val r = run
            main.post { done(r, i) }
            return
        }
        line.voice.name?.let(byName::get)?.let { tts.voice = it }
        tts.setPitch(line.voice.pitch)
        tts.setSpeechRate(rate)
        tts.speak(line.text, TextToSpeech.QUEUE_FLUSH, null, "line:$run:$i")
    }

    /** Stops whichever is speaking. */
    private fun quiet() {
        run++
        playerLine = null
        main.removeCallbacks(tick)
        player?.stop()
        tts.stop()
    }

    /** Stop reading, keeping the line it stopped at to carry on from. */
    fun pause() {
        quiet()
        _state.update { it.copy(playing = false, offset = null) }
    }

    /** Stop reading and forget where it was. */
    fun stop() {
        quiet()
        _state.update { it.copy(playing = false, key = null, line = null, offset = null) }
    }

    /** Forget where a paused reading stopped, so the next one starts afresh. */
    fun forgetPlace() {
        if (!_state.value.playing) _state.update { it.copy(line = null, offset = null) }
    }

    /** Say [text] on its own, as a word card's speaker button does, stopping any reading. */
    fun say(text: String, rate: Float, voiceFor: (List<PhoneVoice>) -> SpokenVoice) = whenReady {
        if (_state.value.missing) return@whenReady
        if (_state.value.playing) pause() else quiet()
        val voice = voiceFor(_state.value.voices)
        voice.name?.let(byName::get)?.let { tts.voice = it }
        tts.setPitch(voice.pitch)
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word:$run")
    }

    fun shutdown() {
        main.removeCallbacksAndMessages(null)
        booster?.release()
        booster = null
        player?.release()
        player = null
        tts.stop()
        tts.shutdown()
    }
}
