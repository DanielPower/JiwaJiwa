package ca.danielpower.jstudy.core

/** The kind of voice someone speaks in. */
enum class VoiceKind { WOMAN, MAN, CHILD }

/**
 * How someone in a story sounds: a kind of voice, and a pitch against that
 * voice's own (1 leaves it as it is), so two people sharing a voice can be
 * told apart.
 */
data class CastVoice(val kind: VoiceKind, val pitch: Float = 1f) {
    companion object {
        /** A cast member's voice as a chapter's `meets` writes it: `"voice": "woman"`, `"pitch": 1.1`. */
        fun parse(kind: String, pitch: Float?): CastVoice? {
            val k = VoiceKind.entries.firstOrNull { it.name.equals(kind, ignoreCase = true) } ?: return null
            return CastVoice(k, pitch ?: 1f)
        }
    }
}

/** One of the phone's Japanese voices, and what kind it is when its name tells. */
data class PhoneVoice(val name: String, val kind: VoiceKind?)

/** What to speak a line in: a phone voice by name (the engine's default when null), at a pitch. */
data class SpokenVoice(val name: String?, val pitch: Float)

/**
 * Who speaks in which of the phone's voices. Phones have only a few Japanese
 * voices, so the cast take turns at the ones of their kind, kept off the
 * narrator's where there's another, and their pitches tell apart those who
 * share one.
 */
object Voices {
    /**
     * What kind a phone voice is, from its name, where the engine is one we
     * know: Google's (ja-jp-x-jab-local) and Samsung's (SMTf00 is a woman's).
     */
    fun kindOf(name: String): VoiceKind? {
        val n = name.lowercase()
        GOOGLE.entries.firstOrNull { n.startsWith(it.key) }?.let { return it.value }
        return when {
            "smtf" in n -> VoiceKind.WOMAN
            "smtm" in n -> VoiceKind.MAN
            else -> null
        }
    }

    // Google's Japanese voices, as its voice settings name them.
    private val GOOGLE = mapOf(
        "ja-jp-x-htm" to VoiceKind.WOMAN,
        "ja-jp-x-jab" to VoiceKind.WOMAN,
        "ja-jp-x-jac" to VoiceKind.MAN,
        "ja-jp-x-jad" to VoiceKind.MAN,
    )

    /** A name for each voice in a menu: Woman 1, Man 2, or Voice 3 when its kind isn't known. */
    fun labels(voices: List<PhoneVoice>): List<String> {
        val counts = HashMap<VoiceKind?, Int>()
        return voices.map { v ->
            val n = counts.merge(v.kind, 1, Int::plus)!!
            when (v.kind) {
                VoiceKind.WOMAN -> "Woman $n"
                VoiceKind.MAN -> "Man $n"
                else -> "Voice ${voices.indexOf(v) + 1}"
            }
        }
    }

    /**
     * The voice for someone who isn't in the cast, by the description that
     * labels their line: 子供 sounds like a child everywhere, 男の人 like a man.
     * Anyone else (店員, 友達) gets a man's or a woman's voice by their label,
     * the same every time.
     */
    fun forLabel(label: String): CastVoice = when {
        CHILD.any { it in label } -> CastVoice(VoiceKind.CHILD)
        WOMEN.any { it in label } -> CastVoice(VoiceKind.WOMAN)
        MEN.any { it in label } -> CastVoice(VoiceKind.MAN)
        else -> CastVoice(if (label.sumOf { it.code } % 2 == 0) VoiceKind.WOMAN else VoiceKind.MAN)
    }

    private val CHILD = listOf("子供", "子ども", "男の子", "女の子")
    private val WOMEN = listOf("女", "母", "姉", "妹", "おばさん", "おばあさん", "娘")
    private val MEN = listOf("男", "父", "兄", "弟", "おじさん", "おじいさん", "息子")

    /**
     * Each speaker's voice, for [speakers] in the order given (the cast in
     * the order they're met, so the voices stay put as stories are added),
     * with [narrator] reading the rest.
     */
    fun assign(voices: List<PhoneVoice>, narrator: String?, speakers: List<Pair<String, CastVoice>>): Map<String, SpokenVoice> {
        val turns = HashMap<VoiceKind, Int>()
        // How many already speak in each voice at each pitch, so a second at the same pitch is moved off it.
        val taken = HashMap<Pair<String?, Float>, Int>()
        return speakers.associate { (name, voice) ->
            val pool = pool(voices, voice.kind, narrator)
            val turn = turns.merge(voice.kind, 1, Int::plus)!! - 1
            val phone = pool.getOrNull(turn % pool.size.coerceAtLeast(1))
            var pitch = base(voice.kind, phone?.kind) * voice.pitch
            val shared = taken.merge(phone?.name to pitch, 1, Int::plus)!! - 1
            if (shared > 0) pitch *= 1f + SPREAD * ((shared + 1) / 2) * if (shared % 2 == 1) 1 else -1
            name to SpokenVoice(phone?.name, pitch.coerceIn(0.5f, 2f))
        }
    }

    /** The narrator's voice: the one chosen, while the phone still has it, at its own pitch. */
    fun narrator(voices: List<PhoneVoice>, chosen: String?): SpokenVoice =
        SpokenVoice(chosen?.takeIf { c -> voices.any { it.name == c } } ?: voices.firstOrNull()?.name, 1f)

    // The voices a kind can speak in: its own kind's, else those whose kind isn't known, else any.
    private fun pool(voices: List<PhoneVoice>, kind: VoiceKind, narrator: String?): List<PhoneVoice> {
        val own = if (kind == VoiceKind.CHILD) VoiceKind.WOMAN else kind
        val pool = voices.filter { it.kind == own }.ifEmpty { voices.filter { it.kind == null } }.ifEmpty { voices }
        return pool.filter { it.name != narrator }.ifEmpty { pool }
    }

    // A kind's pitch in a voice: as it is in its own kind's, raised for a child, and
    // moved towards the kind in a voice whose kind isn't known or doesn't match.
    private fun base(kind: VoiceKind, voice: VoiceKind?): Float = when {
        kind == VoiceKind.CHILD -> if (voice == VoiceKind.WOMAN) 1.3f else 1.4f
        voice == kind -> 1f
        kind == VoiceKind.WOMAN -> 1.15f
        else -> 0.85f
    }

    /** How far apart two speakers sharing a voice and pitch are moved. */
    private const val SPREAD = 0.08f
}
