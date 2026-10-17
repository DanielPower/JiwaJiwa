package ca.danielpower.jstudy.core

/**
 * A word you've studied on Renshuu. Renshuu gives each word its JMdict entry
 * number, which is jstudy's word id.
 */
data class RenshuuTerm(val jmdictId: Long?, val mastery: Int)

/** A word's standing in jstudy before an import. */
data class LocalWord(val mastery: Int, val started: Boolean, val suspended: Boolean)

data class ImportPlan(
    /** Word id to mastery, for each word that should take Renshuu's mastery. */
    val updates: Map<Long, Int>,
    /** Words Renshuu returned. */
    val renshuuWords: Int,
    /** Of those, words that aren't in jstudy's dictionary (any JMdict word counts, JLPT or not). */
    val notInJstudy: Int,
    /** Updates for words jstudy hasn't started. */
    val newWords: Int,
    /** Updates that raise the mastery of a word jstudy has started. */
    val raised: Int,
    /** Words where jstudy's mastery is already as high, or Renshuu's is 0. */
    val unchanged: Int,
    /** Words you've suspended in jstudy, which an import leaves alone. */
    val suspended: Int,
)

object RenshuuImport {
    /**
     * Each word jstudy has takes the higher of the two masteries. A word
     * Renshuu lists more than once counts at its highest mastery.
     */
    fun plan(terms: List<RenshuuTerm>, inJstudy: (Long) -> Boolean, local: Map<Long, LocalWord>): ImportPlan {
        val best = mutableMapOf<Long, Int>()
        var notInJstudy = 0
        for (term in terms) {
            val id = term.jmdictId?.takeIf(inJstudy)
            if (id == null) {
                notInJstudy++
                continue
            }
            val mastery = term.mastery.coerceIn(0, MasteryRules.MAX)
            best[id] = maxOf(best[id] ?: 0, mastery)
        }
        val updates = mutableMapOf<Long, Int>()
        var newWords = 0
        var raised = 0
        var unchanged = 0
        var suspended = 0
        for ((id, mastery) in best) {
            val mine = local[id]
            when {
                mine?.suspended == true -> suspended++
                mastery <= (mine?.mastery ?: 0) -> unchanged++
                else -> {
                    updates[id] = mastery
                    if (mine?.started == true) raised++ else newWords++
                }
            }
        }
        return ImportPlan(updates, terms.size, notInJstudy, newWords, raised, unchanged, suspended)
    }
}
