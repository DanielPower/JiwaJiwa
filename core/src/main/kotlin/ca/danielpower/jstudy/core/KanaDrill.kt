package ca.danielpower.jstudy.core

import kotlin.random.Random

/**
 * Where a kana is. New: not practised yet, so stories show its romaji.
 * Learning: practised, checked once a day until it's quick and right on
 * [KanaRules.CLEAR_DAYS] different days. Clear: learned, and kept fresh by
 * reading, with a weekly check for the first month.
 */
enum class KanaStage(val value: Int) {
    NEW(0),
    LEARNING(1),
    CLEAR(2),
    ;

    companion object {
        fun of(value: Int) = entries.firstOrNull { it.value == value } ?: NEW
    }
}

/** Your progress with one kana, as saved. */
data class KanaProgress(
    val text: String,
    val stage: KanaStage = KanaStage.NEW,
    /** The study day it was first practised. */
    val introducedDay: Long? = null,
    /** Days it's been quick and right since its last miss, not counting the day it was introduced. */
    val passDays: Int = 0,
    /** The study day it was last asked. */
    val lastDay: Long? = null,
    /** When the day it's introduced, a second look falls due. Null once it's had it. */
    val lateCheckAt: Long? = null,
    /** Once clear, the study day of its next weekly check, or null once the checks are over. */
    val checkDue: Long? = null,
    val checks: Int = 0,
    /** Kana it's been taken for, most recent first. */
    val confusions: List<String> = emptyList(),
)

object KanaRules {
    /** Right answers in the sitting a kana is introduced, spread out. */
    const val FIRST_RIGHTS = 3

    /** After a miss there, at least this many more. */
    const val AFTER_MISS = 2

    /** Quick and right on this many days makes a kana clear. */
    const val CLEAR_DAYS = 3

    /** A right answer quicker than this counts towards clear. */
    const val QUICK_MILLIS = 3000L

    /** The second look the day a kana is introduced, this long after. */
    const val LATE_CHECK_MILLIS = 3 * 60 * 60 * 1000L

    /** Clear kana are checked every week, this many times. */
    const val CHECK_DAYS = 7
    const val CHECKS = 4

    /** Questions between two asks of the same kana. */
    const val GAP = 3

    const val OPTIONS = 4
    const val CONFUSIONS = 4
}

/** How many kana a session would ask, for Home, the Review tab and the badge. */
data class KanaCounts(
    /** Kana a new set would bring in now. */
    val new: Int,
    /** Kana to check today. */
    val check: Int,
    val clear: Int,
    val total: Int,
    /** Kana introduced today whose second look isn't due yet. */
    val later: Int = 0,
    /** Kana practised but not clear yet. */
    val learning: Int = 0,
) {
    val due get() = new + check

    /** Roughly how long practising them takes: a new kana is asked three times, a few seconds each. */
    val minutes get() = maxOf(1, ((new * KanaRules.FIRST_RIGHTS + check) * 4 + 59) / 60)
}

/**
 * Where you are with kana: what you read already ([known]), the order the
 * stories need the rest in, and your progress with each.
 */
data class KanaState(
    val known: KanaKnown,
    val order: List<String>,
    val progress: Map<String, KanaProgress>,
    /** The study day a new set was last brought in by itself; "5 more" brings in more. */
    val lastSetDay: Long? = null,
) {
    /** The kana to learn, in order: everything you didn't read already. */
    val toLearn: List<String> by lazy {
        order.filter { text -> KanaCourse.unit(text)?.let { !known.covers(it.script) } == true }
    }

    val sets: List<List<String>> by lazy { KanaCourse.sets(toLearn) }

    fun stage(text: String): KanaStage {
        val unit = KanaCourse.unit(text) ?: return KanaStage.CLEAR
        if (known.covers(unit.script)) return KanaStage.CLEAR
        return progress[text]?.stage ?: KanaStage.NEW
    }

    /** Every kana is clear: nothing to practise, no romaji, and vertical text is back. */
    val done: Boolean get() = toLearn.all { stage(it) == KanaStage.CLEAR }

    /** Kana you've never practised, whose romaji stories show. */
    val unread: Set<String> by lazy { toLearn.filter { stage(it) == KanaStage.NEW }.toSet() }

    /** Kana that aren't clear yet, whose romaji a word's pane shows. */
    val unclear: Set<String> by lazy { toLearn.filter { stage(it) != KanaStage.CLEAR }.toSet() }

    /** The next set's kana not practised yet. */
    fun nextSet(): List<String>? = sets.firstOrNull { set -> set.any { stage(it) == KanaStage.NEW } }?.filter { stage(it) == KanaStage.NEW }

    /** Which set [text] is in, counting from 1. */
    fun setNumber(text: String): Int = sets.indexOfFirst { text in it } + 1

    fun isDue(text: String, today: Long, now: Long): Boolean {
        val p = progress[text] ?: return false
        if (KanaCourse.unit(text)?.let { known.covers(it.script) } != false) return false
        return when (p.stage) {
            KanaStage.NEW -> false
            KanaStage.LEARNING -> if (p.introducedDay == today) p.lateCheckAt != null && p.lateCheckAt <= now else (p.lastDay ?: Long.MIN_VALUE) < today
            KanaStage.CLEAR -> p.checkDue != null && p.checkDue <= today
        }
    }

    fun due(today: Long, now: Long): List<String> = toLearn.filter { isDue(it, today, now) }

    fun counts(today: Long, now: Long): KanaCounts = KanaCounts(
        new = if (lastSetDay == today) 0 else nextSet()?.size ?: 0,
        check = due(today, now).size,
        clear = toLearn.count { stage(it) == KanaStage.CLEAR },
        total = toLearn.size,
        later = toLearn.count { t -> progress[t]?.let { it.introducedDay == today && it.lateCheckAt != null && it.lateCheckAt > now } == true },
        learning = toLearn.count { stage(it) == KanaStage.LEARNING },
    )
}

/**
 * A kana question with four answers: see a kana and pick its sound, or
 * ([listen]) hear a sound and pick its kana. A mark (っ, ー) is asked in a
 * word, for the word's sound.
 */
data class KanaQuestion(
    val unit: KanaUnit,
    val listen: Boolean,
    /** What's shown: the kana, its sound in romaji, or a word with the mark. */
    val prompt: String,
    val options: List<String>,
    val answer: String,
    /** The kana each option stands for, where it's one. */
    val optionUnits: List<String?>,
    /** Whether this is the first time the kana is shown, just after its set's intro. */
    val first: Boolean = false,
)

data class KanaResult(
    val correct: Boolean,
    val quick: Boolean,
    val progress: KanaProgress,
    /** The kana the wrong answer stands for, to show beside the right one. */
    val picked: String?,
)

/**
 * One sitting of kana practice: today's checks, and maybe a new set,
 * practised until each of the set is right [KanaRules.FIRST_RIGHTS] times.
 * Each answer's progress is handed back to be saved straight away, so
 * leaving part way keeps what was done.
 */
class KanaSession(
    state: KanaState,
    private val today: Long,
    now: Long,
    /** The new set to learn, if any, shown first. */
    val newSet: List<KanaUnit>,
    /** Whether a kana has a sound to play, for asking it by sound. */
    private val canListen: (String) -> Boolean = { true },
    private val random: Random = Random.Default,
) {
    private enum class Kind { NEW, KNOW_CHECK, DUE, LATE, CHECK }

    private class Item(var kind: Kind, var needed: Int, var asked: Int = 0)

    private val known = state.known
    private val progress = HashMap(state.progress)
    private val items = LinkedHashMap<String, Item>()
    private val queue = ArrayList<String>()

    init {
        for (unit in newSet) items[unit.text] = Item(Kind.NEW, KanaRules.FIRST_RIGHTS)
        val due = state.due(today, now).filter { it !in items }.shuffled(random)
        for (text in due) {
            val p = progress.getValue(text)
            val kind = when {
                p.stage == KanaStage.CLEAR -> Kind.CHECK
                p.introducedDay == today -> Kind.LATE
                else -> Kind.DUE
            }
            items[text] = Item(kind, 1)
        }
        queue.addAll(newSet.map { it.text })
        queue.addAll(due)
    }

    /** Check the new set rather than learn it: each one quick and right is clear straight away. */
    fun checkKnown() {
        for (unit in newSet) items[unit.text]?.takeIf { it.asked == 0 }?.kind = Kind.KNOW_CHECK
    }

    val newRemaining: Int get() = items.count { (_, item) -> item.needed > 0 && item.kind in NEW_KINDS }
    val checkRemaining: Int get() = items.count { (_, item) -> item.needed > 0 && item.kind !in NEW_KINDS }
    val answered: Int get() = items.values.sumOf { it.asked }

    /** Right answers still needed, about how many questions are left. */
    val asksLeft: Int get() = items.values.sumOf { maxOf(0, it.needed) }
    val isEmpty: Boolean get() = queue.isEmpty()

    /** The kana this session brought in or checked. */
    val practised: List<String> get() = items.filterValues { it.asked > 0 }.keys.toList()

    fun progress(text: String): KanaProgress = progress[text] ?: KanaProgress(text)

    /** The next question, or null when the session's done. */
    fun next(): KanaQuestion? {
        val text = queue.firstOrNull() ?: return null
        val unit = KanaCourse.unit(text)!!
        val item = items.getValue(text)
        if (unit.mark) return markQuestion(unit, item.asked == 0)
        val firstSight = item.kind in NEW_KINDS && item.asked == 0
        val listen = !firstSight && canListen(text) && random.nextInt(4) == 0
        // Early on there may be too few kana met to choose from: then any will do.
        val pool = pool().takeIf { it.size >= KanaRules.OPTIONS } ?: KanaCourse.units.filter { !it.mark }
        val wrong = Distractors.pick(unit, pool, progress(text).confusions, KanaRules.OPTIONS - 1, listen, random)
        val choices = (wrong + unit).shuffled(random)
        return if (listen) {
            KanaQuestion(unit, true, unit.romaji, choices.map { it.text }, unit.text, choices.map { it.text }, firstSight)
        } else {
            KanaQuestion(unit, false, unit.text, choices.map { it.romaji }, unit.romaji, choices.map { it.text }, firstSight)
        }
    }

    private fun markQuestion(unit: KanaUnit, first: Boolean): KanaQuestion {
        val (word, options) = MARK_WORDS.getValue(unit.text).random(random)
        val shuffled = options.shuffled(random)
        return KanaQuestion(unit, false, word, shuffled, options.first(), shuffled.map { null }, first)
    }

    /** Kana you've met: the ones to draw wrong answers from. */
    private fun pool(): List<KanaUnit> = KanaCourse.units.filter { u ->
        !u.mark && (known.covers(u.script) || (progress[u.text]?.stage ?: KanaStage.NEW) != KanaStage.NEW || u.text in items)
    }

    /** Answer [question] with [choice], [millis] after it was shown. */
    fun answer(question: KanaQuestion, choice: String, millis: Long, now: Long): KanaResult {
        val text = question.unit.text
        val item = items.getValue(text)
        val correct = choice == question.answer
        val quick = correct && millis < KanaRules.QUICK_MILLIS
        val picked = if (correct) null else question.optionUnits.getOrNull(question.options.indexOf(choice))
        var p = progress(text)
        if (picked != null) p = p.copy(confusions = (listOf(picked) + p.confusions.filter { it != picked }).take(KanaRules.CONFUSIONS))
        val firstAsk = item.asked == 0
        item.asked++

        when (item.kind) {
            Kind.KNOW_CHECK -> {
                if (quick) {
                    p = p.copy(stage = KanaStage.CLEAR, introducedDay = today, lastDay = today, checkDue = today + KanaRules.CHECK_DAYS, checks = 0, lateCheckAt = null)
                    item.needed = 0
                } else {
                    if (correct) p = p.copy(stage = KanaStage.LEARNING, introducedDay = today, lastDay = today)
                    item.kind = Kind.NEW
                    item.needed = if (correct) KanaRules.AFTER_MISS else KanaRules.FIRST_RIGHTS
                }
            }
            Kind.NEW -> {
                if (correct) {
                    if (p.stage == KanaStage.NEW) p = p.copy(stage = KanaStage.LEARNING, introducedDay = today)
                    item.needed--
                    p = p.copy(lastDay = today)
                    if (item.needed <= 0) p = p.copy(lateCheckAt = now + KanaRules.LATE_CHECK_MILLIS)
                } else {
                    item.needed = maxOf(item.needed, KanaRules.AFTER_MISS)
                }
            }
            Kind.DUE -> {
                if (firstAsk) {
                    p = when {
                        quick -> {
                            val days = p.passDays + 1
                            if (days >= KanaRules.CLEAR_DAYS) {
                                p.copy(stage = KanaStage.CLEAR, passDays = days, checkDue = today + KanaRules.CHECK_DAYS, checks = 0)
                            } else {
                                p.copy(passDays = days)
                            }
                        }
                        correct -> p
                        else -> p.copy(passDays = 0)
                    }.copy(lastDay = today)
                }
                item.needed = if (correct) item.needed - 1 else maxOf(item.needed, 1)
            }
            Kind.LATE -> {
                if (firstAsk) p = p.copy(lateCheckAt = null, lastDay = today)
                item.needed = if (correct) item.needed - 1 else maxOf(item.needed, 1)
            }
            Kind.CHECK -> {
                if (firstAsk) {
                    p = when {
                        quick -> (p.checks + 1).let { n -> p.copy(checks = n, checkDue = if (n >= KanaRules.CHECKS) null else today + KanaRules.CHECK_DAYS) }
                        correct -> p.copy(checkDue = today + 3)
                        else -> p.copy(stage = KanaStage.LEARNING, passDays = 0, checkDue = null)
                    }.copy(lastDay = today)
                }
                item.needed = if (correct) item.needed - 1 else maxOf(item.needed, 1)
            }
        }
        progress[text] = p

        queue.removeAt(0)
        if (item.needed > 0) queue.add(minOf(KanaRules.GAP, queue.size), text)
        return KanaResult(correct, quick, p, picked)
    }

    companion object {
        private val NEW_KINDS = setOf(Kind.NEW, Kind.KNOW_CHECK)

        /**
         * Words that show a mark at work, each with its sound first and three
         * near misses. The misses differ only in what the marks do (a sound
         * doubled or held), never in a kana, which may not have been met yet.
         */
        val MARK_WORDS: Map<String, List<Pair<String, List<String>>>> = mapOf(
            "っ" to listOf(
                "きって" to listOf("kitte", "kite", "kiite", "kittee"),
                "ちょっと" to listOf("chotto", "choto", "chooto", "chottoo"),
                "もっと" to listOf("motto", "moto", "mooto", "mottoo"),
                "ずっと" to listOf("zutto", "zuto", "zuuto", "zuttoo"),
                "やった" to listOf("yatta", "yata", "yaata", "yattaa"),
            ),
            "ッ" to listOf(
                "ベッド" to listOf("beddo", "bedo", "beedo", "beddoo"),
                "カップ" to listOf("kappu", "kapu", "kaapu", "kappuu"),
                "ネット" to listOf("netto", "neto", "neeto", "nettoo"),
                "ロック" to listOf("rokku", "roku", "rooku", "rokkuu"),
                "チケット" to listOf("chiketto", "chiketo", "chikeeto", "chikettoo"),
            ),
            "ー" to listOf(
                "ラーメン" to listOf("raamen", "ramen", "rameen", "raameen"),
                "コーヒー" to listOf("koohii", "kohi", "koohi", "kohii"),
                "ゲーム" to listOf("geemu", "gemu", "gemuu", "geemuu"),
                "ケーキ" to listOf("keeki", "keki", "kekii", "keekii"),
                "スーパー" to listOf("suupaa", "supa", "suupa", "supaa"),
            ),
        )
    }
}

/**
 * Wrong answers worth thinking about: kana you've mixed this one up with,
 * then ones that look like it (with or without its voicing marks), then
 * ones that sound like it, then any you've met. Never one that sounds the
 * same (じ and ぢ, お and を, か and カ), since it would be right too.
 */
object Distractors {
    fun pick(unit: KanaUnit, pool: List<KanaUnit>, confusions: List<String>, count: Int, listen: Boolean, random: Random): List<KanaUnit> {
        val scored = pool
            .filter { it.text != unit.text && it.romaji != unit.romaji }
            .map { it to score(unit, it, confusions, listen) + random.nextDouble() * 10 }
            .sortedByDescending { it.second }
        val out = ArrayList<KanaUnit>()
        for ((candidate, _) in scored) {
            if (out.size >= count) break
            if (out.none { it.romaji == candidate.romaji }) out.add(candidate)
        }
        return out
    }

    private fun score(unit: KanaUnit, other: KanaUnit, confusions: List<String>, listen: Boolean): Double {
        var s = 0.0
        if (other.text in confusions) s += 100
        if (KanaLooks.alike(unit.text, other.text)) s += 50
        if (soundsAlike(unit.romaji, other.romaji)) s += 20
        // Picking by sound, a kana from the other script would give itself away.
        if (other.script == unit.script) s += if (listen) 30 else 5
        if (other.text.length == unit.text.length) s += 5
        return s
    }

    /** Sounds that share a consonant or a vowel and are easy to mix up: shi, chi, tsu, su; ru, ro, ri. */
    fun soundsAlike(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val (ca, va) = split(a)
        val (cb, vb) = split(b)
        return (ca == cb && ca.isNotEmpty()) || (va == vb && SIBILANTS.contains(ca) && SIBILANTS.contains(cb))
    }

    private fun split(r: String): Pair<String, String> {
        val v = r.takeLastWhile { it in "aiueo" }
        return r.dropLast(v.length) to v
    }

    private val SIBILANTS = setOf("s", "sh", "ch", "ts", "z", "j", "t")
}
