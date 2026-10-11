package ca.danielpower.jstudy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.FuriganaMode
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.StudyClock
import ca.danielpower.jstudy.core.StudySession
import ca.danielpower.jstudy.core.StudySettings
import ca.danielpower.jstudy.data.Entry
import ca.danielpower.jstudy.data.Repository
import ca.danielpower.jstudy.data.Summary
import ca.danielpower.jstudy.data.Undo
import ca.danielpower.jstudy.data.Word
import ca.danielpower.jstudy.data.WordDetail
import ca.danielpower.jstudy.data.WordStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ReviewCard(
    val card: Card,
    val word: Word,
    /** Furigana on the front, from the word's mastery. */
    val furigana: FuriganaMode,
    val mastery: Int,
)

data class ReviewCounts(val new: Int, val learning: Int, val review: Int)

enum class StatusFilter(val label: String) {
    ALL("All"),
    NOT_STARTED("Not started"),
    STUDYING("Studying"),
    SUSPENDED("Suspended"),
    ;

    fun matches(status: WordStatus?): Boolean {
        val started = status != null && status.state != CardState.NEW
        val suspended = status?.suspended == true
        return when (this) {
            ALL -> true
            NOT_STARTED -> !started && !suspended
            STUDYING -> started && !suspended
            SUSPENDED -> suspended
        }
    }
}

data class DictionaryRow(val entry: Entry, val status: WordStatus?)

sealed interface Screen {
    data object Loading : Screen
    data class Home(val summary: Summary) : Screen
    data class Review(
        val current: ReviewCard,
        val revealed: Boolean,
        /** Whether furigana is currently visible on the front. */
        val furiganaVisible: Boolean,
        val counts: ReviewCounts,
        val canUndo: Boolean,
        val rules: MasteryRules,
    ) : Screen

    /** Only learning steps remain and none is due yet. */
    data class Waiting(val dueAt: Long, val counts: ReviewCounts) : Screen
    data class Done(val summary: Summary) : Screen
    data class Settings(val settings: StudySettings, val attribution: String) : Screen

    data class Dictionary(
        val query: String,
        /** JLPT levels to show; empty shows all. */
        val levels: Set<Int>,
        val status: StatusFilter,
        val rows: List<DictionaryRow>,
    ) : Screen

    data class WordPage(val detail: WordDetail, val rules: MasteryRules, val clock: StudyClock) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private lateinit var repo: Repository
    private var session: StudySession? = null
    private var undo: Undo? = null
    private val lock = Mutex()

    private var entries: List<Entry>? = null
    private var statuses: Map<Long, WordStatus> = emptyMap()
    private var lastDictionary: Screen.Dictionary? = null
    private var searchJob: Job? = null

    init {
        launchIo {
            repo = Repository(getApplication())
            showHome()
        }
    }

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch {
            lock.withLock { withContext(Dispatchers.IO) { block() } }
        }
    }

    private fun now() = System.currentTimeMillis()

    private fun showHome() {
        session = null
        undo = null
        _screen.value = Screen.Home(repo.summary(now()))
    }

    fun goHome() = launchIo { showHome() }

    /** The system back button: word page to dictionary, anything else home. */
    fun back() {
        if (_screen.value is Screen.WordPage) closeWord() else goHome()
    }

    fun startReview() = launchIo {
        session = repo.session(now())
        undo = null
        advance()
    }

    fun studyMore() = launchIo {
        repo.studyMore(now())
        session = repo.session(now())
        undo = null
        advance()
    }

    /** Show the next card, or the waiting or done screen. */
    private fun advance() {
        val s = session ?: return showHome()
        val counts = ReviewCounts(s.newRemaining, s.learningRemaining, s.reviewRemaining)
        val card = s.next(now())
        if (card == null) {
            val due = s.nextLearningDue
            _screen.value = if (due != null) Screen.Waiting(due, counts) else Screen.Done(repo.summary(now()))
            return
        }
        val word = repo.dictionary.word(card.wordId)
        if (word == null) {
            // A word removed from the dictionary in an update; skip it.
            s.answered(card.copy(state = CardState.REVIEW), 0)
            return advance()
        }
        val mastery = repo.progress(card.wordId).mastery
        val rules = repo.settings().mastery
        val mode = rules.furigana(mastery)
        _screen.value = Screen.Review(
            current = ReviewCard(card, word, mode, mastery),
            revealed = false,
            furiganaVisible = mode == FuriganaMode.SHOWN,
            counts = counts,
            canUndo = undo != null,
            rules = rules,
        )
    }

    fun reveal() = _screen.update { if (it is Screen.Review) it.copy(revealed = true) else it }

    fun toggleFurigana() = _screen.update {
        if (it is Screen.Review && it.current.furigana != FuriganaMode.OFF && it.current.word.showsKanji) {
            it.copy(furiganaVisible = !it.furiganaVisible)
        } else {
            it
        }
    }

    fun answer(correct: Boolean) = launchIo {
        val state = _screen.value as? Screen.Review ?: return@launchIo
        if (!state.revealed) return@launchIo
        val s = session ?: return@launchIo
        val now = now()
        val (answer, u) = repo.answer(state.current.card, correct, now)
        s.answered(answer.card, repo.endOfToday(now))
        undo = u
        advance()
    }

    /**
     * Answer the first sight of a new word. Mastery 0 starts learning it;
     * anything higher marks it known at that mastery, and the session is
     * rebuilt so the next new word takes its place.
     */
    fun introduce(mastery: Int) = launchIo {
        val state = _screen.value as? Screen.Review ?: return@launchIo
        if (state.current.card.state != CardState.NEW) return@launchIo
        val s = session ?: return@launchIo
        val now = now()
        if (mastery <= 0) {
            val (card, u) = repo.introduce(state.current.card, now)
            s.answered(card, repo.endOfToday(now))
            undo = u
        } else {
            val (_, u) = repo.markKnown(state.current.card, mastery, now)
            session = repo.session(now)
            undo = u
        }
        advance()
    }

    fun undoLast() = launchIo {
        val u = undo ?: return@launchIo
        repo.undo(u)
        if (u.logId == null) {
            // Undoing a first sight: the word is new again, so rebuild the queue around it.
            session = repo.session(now())
        }
        session?.restore(u.cardBefore)
        undo = null
        advance()
    }

    /** Re-check the queue, e.g. when a learning step falls due. */
    fun refresh() = launchIo { if (session != null) advance() }

    fun openSettings() = launchIo {
        val attribution = repo.dictionary.meta("attribution").orEmpty()
        _screen.value = Screen.Settings(repo.settings(), attribution)
    }

    fun saveSettings(settings: StudySettings) = launchIo {
        repo.saveSettings(settings)
        _screen.update { if (it is Screen.Settings) it.copy(settings = settings) else it }
    }

    // ------------------------------------------------------------ dictionary

    fun openDictionary() = launchIo {
        if (entries == null) entries = repo.dictionary.entries()
        statuses = repo.wordStatuses()
        val state = Screen.Dictionary("", emptySet(), StatusFilter.ALL, emptyList())
        _screen.value = state.copy(rows = rows(state))
    }

    /** Words matching the query and filters, best match first. */
    private fun rows(state: Screen.Dictionary): List<DictionaryRow> {
        val all = entries ?: return emptyList()
        val ranks = if (state.query.isBlank()) null else repo.dictionary.search(state.query)
        val matching = all.filter { e ->
            (ranks == null || e.id in ranks) &&
                (state.levels.isEmpty() || e.jlpt in state.levels) &&
                state.status.matches(statuses[e.id])
        }
        // Entries are in study order, and the sort is stable.
        val sorted = if (ranks == null) matching else matching.sortedBy { ranks.getValue(it.id) }
        return sorted.map { DictionaryRow(it, statuses[it.id]) }
    }

    private fun filterDictionary(debounce: Long, change: (Screen.Dictionary) -> Screen.Dictionary) {
        val current = _screen.value as? Screen.Dictionary ?: return
        val next = change(current)
        _screen.value = next
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(debounce)
            val rows = withContext(Dispatchers.IO) { rows(next) }
            _screen.update {
                val same = it is Screen.Dictionary && it.query == next.query && it.levels == next.levels && it.status == next.status
                if (same) (it as Screen.Dictionary).copy(rows = rows) else it
            }
        }
    }

    fun search(query: String) = filterDictionary(150) { it.copy(query = query) }

    fun toggleLevel(level: Int) = filterDictionary(0) {
        it.copy(levels = if (level in it.levels) it.levels - level else it.levels + level)
    }

    fun setStatusFilter(status: StatusFilter) = filterDictionary(0) { it.copy(status = status) }

    fun openWord(wordId: Long) = launchIo {
        (_screen.value as? Screen.Dictionary)?.let { lastDictionary = it }
        showWord(wordId)
    }

    private fun showWord(wordId: Long) {
        val detail = repo.wordDetail(wordId) ?: return
        val settings = repo.settings()
        _screen.value = Screen.WordPage(detail, settings.mastery, repo.clock(settings))
    }

    fun closeWord() = launchIo {
        val state = lastDictionary
        if (state == null) {
            showHome()
            return@launchIo
        }
        statuses = repo.wordStatuses()
        _screen.value = state.copy(rows = rows(state))
    }

    private fun wordAction(action: (wordId: Long) -> Unit) = launchIo {
        val page = _screen.value as? Screen.WordPage ?: return@launchIo
        val wordId = page.detail.word.id
        action(wordId)
        showWord(wordId)
    }

    fun learnNext() = wordAction { repo.learnNext(it, now()) }

    fun markKnown(mastery: Int) = wordAction { repo.markKnown(it, mastery, now()) }

    fun resetWord() = wordAction { repo.reset(it) }

    fun setSuspended(suspended: Boolean) = wordAction { repo.setSuspended(it, suspended) }
}
