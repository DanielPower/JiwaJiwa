package ca.danielpower.jstudy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.FuriganaMode
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.StudySession
import ca.danielpower.jstudy.core.StudySettings
import ca.danielpower.jstudy.data.Repository
import ca.danielpower.jstudy.data.Summary
import ca.danielpower.jstudy.data.Undo
import ca.danielpower.jstudy.data.Word
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
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private lateinit var repo: Repository
    private var session: StudySession? = null
    private var undo: Undo? = null
    private val lock = Mutex()

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
}
