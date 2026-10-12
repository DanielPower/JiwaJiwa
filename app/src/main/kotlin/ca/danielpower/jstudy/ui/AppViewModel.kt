package ca.danielpower.jstudy.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.FuriganaMode
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.ImportPlan
import ca.danielpower.jstudy.core.KanaCourse
import ca.danielpower.jstudy.core.KanaKnown
import ca.danielpower.jstudy.core.KanaUnit
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.StudyClock
import ca.danielpower.jstudy.core.StudySession
import ca.danielpower.jstudy.core.StudySettings
import ca.danielpower.jstudy.core.StoryDifficulty
import ca.danielpower.jstudy.core.StoryLength
import ca.danielpower.jstudy.data.BackupException
import ca.danielpower.jstudy.data.Entry
import ca.danielpower.jstudy.data.Reminder
import ca.danielpower.jstudy.data.Stats
import ca.danielpower.jstudy.reminder.DailyReminder
import ca.danielpower.jstudy.data.RenshuuClient
import ca.danielpower.jstudy.data.RenshuuException
import ca.danielpower.jstudy.data.Repository
import ca.danielpower.jstudy.data.SavedStory
import ca.danielpower.jstudy.data.StoryReading
import ca.danielpower.jstudy.data.Summary
import ca.danielpower.jstudy.data.Undo
import ca.danielpower.jstudy.data.Word
import ca.danielpower.jstudy.data.WordDetail
import ca.danielpower.jstudy.data.WordStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    /** The word, or null on a kana card. */
    val word: Word?,
    /** Furigana on the front, from the word's mastery. */
    val furigana: FuriganaMode,
    val mastery: Int,
    /** The kana on a kana card. */
    val kana: KanaUnit? = null,
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
    data class Settings(
        val settings: StudySettings,
        val attribution: String,
        val claudeKey: String,
        val storyVertical: Boolean,
        val startLevel: Int,
        val reminder: Reminder,
        val kanaKnown: KanaKnown,
        /** The result of the last backup export or import. */
        val backupMessage: String? = null,
    ) : Screen

    /** First run, a page at a time: kana, where to start, and the reminder. */
    data class Setup(
        val levelCounts: Map<Int, Int>,
        val startLevel: Int,
        /** Mastery for the words below the starting level; 0 leaves them unstarted. */
        val lowerMastery: Int,
        /** The daily reminder's time, if you chose one. */
        val reminderMinute: Int?,
        val rules: MasteryRules,
        /** Which kana you read already; null until you choose. */
        val kana: KanaKnown? = null,
        val page: SetupPage = SetupPage.WELCOME,
    ) : Screen {
        /**
         * The pages in order. Picking a level only makes sense if you read
         * kana; anyone still learning them starts at N5.
         */
        val pages get() = SetupPage.entries.filter { it != SetupPage.LEVEL || kana == KanaKnown.BOTH }
    }

    data class StatsPage(val stats: Stats) : Screen

    data class Dictionary(
        val query: String,
        /** JLPT levels to show; empty shows all. */
        val levels: Set<Int>,
        val status: StatusFilter,
        val rows: List<DictionaryRow>,
    ) : Screen

    data class WordPage(val detail: WordDetail, val rules: MasteryRules, val clock: StudyClock) : Screen

    data class RenshuuImport(val step: ImportStep) : Screen

    data class Stories(
        val stories: List<SavedStory>,
        val draft: StoryDraft,
        val hasClaudeKey: Boolean,
        /** Words you've started, to warn when there are too few for a story. */
        val knownWords: Int,
        /** What the story writer is doing, while it works. */
        val writing: String?,
        val error: String?,
    ) : Screen

    data class StoryReader(
        val reading: StoryReading,
        /** New words this story just added to the front of the queue, shown once. */
        val queued: Int?,
        /** Set top to bottom, right to left, as Japanese books are. */
        val vertical: Boolean,
    ) : Screen
}

enum class SetupPage { WELCOME, KANA, LEVEL, REMINDER }

/** The story form, kept while you move around the app. */
data class StoryDraft(
    val prompt: String = "",
    val length: StoryLength = StoryLength.SHORT,
    val difficulty: StoryDifficulty = StoryDifficulty.NORMAL,
)

sealed interface ImportStep {
    data class EnterKey(val error: String? = null) : ImportStep
    data class Fetching(val page: Int, val pages: Int) : ImportStep
    data class Preview(val plan: ImportPlan) : ImportStep
    data object Applying : ImportStep
    data class Done(val updated: Int) : ImportStep
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    // Kept outside the database so the theme is right from the first frame.
    private val display = app.getSharedPreferences("display", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == display.getString("theme", null) } ?: ThemeMode.AUTO,
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private lateinit var repo: Repository
    private var session: StudySession? = null
    private var undo: Undo? = null
    private val lock = Mutex()

    /** The JLPT words. Other words join the list when a search finds them or you've started them. */
    private var entries: List<Entry>? = null
    private var entryIds: Set<Long> = emptySet()
    private var statuses: Map<Long, WordStatus> = emptyMap()
    private var lastDictionary: Screen.Dictionary? = null
    private var searchJob: Job? = null
    private var importJob: Job? = null

    private var draft = StoryDraft()
    private var storyJob: Job? = null
    private var storyProgress: String? = null
    private var storyError: String? = null

    init {
        launchIo {
            repo = Repository(getApplication())
            DailyReminder.schedule(getApplication(), repo.reminder())
            if (repo.needsSetup()) {
                _screen.value = Screen.Setup(repo.levelCounts(), 5, 0, null, repo.settings().mastery)
            } else {
                showHome()
            }
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

    /** The system back button: word page to dictionary, import to settings, story to stories, anything else home. */
    fun back() {
        when (_screen.value) {
            is Screen.WordPage -> closeWord()
            is Screen.RenshuuImport -> closeImport()
            is Screen.StoryReader -> openStories()
            is Screen.Setup -> setupBack()
            else -> goHome()
        }
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
        val rules = repo.settings().mastery
        if (card.direction == Direction.KANA) {
            val unit = KanaCourse.unit(card.wordId)
            if (unit == null) {
                s.answered(card.copy(state = CardState.REVIEW), 0)
                return advance()
            }
            _screen.value = Screen.Review(
                current = ReviewCard(card, null, FuriganaMode.OFF, repo.progress(card.wordId).mastery, unit),
                revealed = false,
                furiganaVisible = false,
                counts = counts,
                canUndo = undo != null,
                rules = rules,
            )
            return
        }
        val word = repo.dictionary.word(card.wordId)
        if (word == null) {
            // A word removed from the dictionary in an update; skip it.
            s.answered(card.copy(state = CardState.REVIEW), 0)
            return advance()
        }
        val mastery = repo.progress(card.wordId).mastery
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
        if (it is Screen.Review && it.current.furigana != FuriganaMode.OFF && it.current.word?.showsKanji == true) {
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

    fun openSettings() = launchIo { showSettings(null) }

    private fun showSettings(backupMessage: String?) {
        val attribution = repo.dictionary.meta("attribution").orEmpty()
        _screen.value = Screen.Settings(
            settings = repo.settings(),
            attribution = attribution,
            claudeKey = repo.claudeKey(),
            storyVertical = repo.storyVertical(),
            startLevel = repo.startLevel(),
            reminder = repo.reminder(),
            kanaKnown = repo.kanaKnown(),
            backupMessage = backupMessage,
        )
    }

    fun setStartLevel(level: Int) = launchIo {
        repo.setStartLevel(level)
        _screen.update { if (it is Screen.Settings) it.copy(startLevel = repo.startLevel()) else it }
    }

    fun setKanaKnown(known: KanaKnown) = launchIo {
        repo.setKanaKnown(known)
        _screen.update { if (it is Screen.Settings) it.copy(kanaKnown = repo.kanaKnown()) else it }
    }

    fun setReminder(reminder: Reminder) = launchIo {
        repo.saveReminder(reminder)
        DailyReminder.schedule(getApplication(), reminder)
        _screen.update { if (it is Screen.Settings) it.copy(reminder = reminder) else it }
    }

    // ------------------------------------------------------------ backup

    fun exportBackup(uri: Uri) = launchIo {
        val message = try {
            val out = getApplication<Application>().contentResolver.openOutputStream(uri) ?: error("no stream")
            out.use { repo.exportBackup(it) }
            "Backup saved."
        } catch (e: Exception) {
            "Couldn't save the backup: ${e.message}"
        }
        _screen.update { if (it is Screen.Settings) it.copy(backupMessage = message) else it }
    }

    fun importBackup(uri: Uri) = launchIo {
        val message = try {
            val input = getApplication<Application>().contentResolver.openInputStream(uri) ?: error("no stream")
            input.use { repo.importBackup(it) }
            // Everything cached came from the old progress.
            session = null
            undo = null
            statuses = emptyMap()
            lastDictionary = null
            DailyReminder.schedule(getApplication(), repo.reminder())
            "Backup restored."
        } catch (e: BackupException) {
            e.message
        } catch (e: Exception) {
            "Couldn't restore the backup: ${e.message}"
        }
        showSettings(message)
    }

    // ------------------------------------------------------------ reset

    /** Wipe all progress, settings, stories and keys, and start again at first-run setup. */
    fun resetAll() {
        storyJob?.cancel()
        importJob?.cancel()
        searchJob?.cancel()
        launchIo {
            _screen.value = Screen.Loading
            repo.resetAll()
            session = null
            undo = null
            entries = null
            entryIds = emptySet()
            statuses = emptyMap()
            lastDictionary = null
            draft = StoryDraft()
            storyProgress = null
            storyError = null
            display.edit().clear().commit()
            _theme.value = ThemeMode.AUTO
            DailyReminder.schedule(getApplication(), repo.reminder())
            _screen.value = Screen.Setup(repo.levelCounts(), 5, 0, null, repo.settings().mastery)
        }
    }

    // ------------------------------------------------------------ first run

    fun editSetup(change: (Screen.Setup) -> Screen.Setup) = _screen.update { if (it is Screen.Setup) change(it) else it }

    fun setupNext() = editSetup { s -> s.pages.getOrNull(s.pages.indexOf(s.page) + 1)?.let { s.copy(page = it) } ?: s }

    fun setupBack() = editSetup { s -> s.pages.getOrNull(s.pages.indexOf(s.page) - 1)?.let { s.copy(page = it) } ?: s }

    fun finishSetup() = launchIo {
        val setup = _screen.value as? Screen.Setup ?: return@launchIo
        _screen.value = Screen.Loading
        val kana = setup.kana ?: KanaKnown.BOTH
        // Still learning kana: start at the beginning, whatever was picked before going back.
        val startLevel = if (kana == KanaKnown.BOTH) setup.startLevel else 5
        val lower = setup.lowerMastery.takeIf { startLevel < 5 && it > 0 }
        repo.finishSetup(startLevel, lower, kana, setup.reminderMinute, now())
        DailyReminder.schedule(getApplication(), repo.reminder())
        showHome()
    }

    // ------------------------------------------------------------ stats

    fun openStats() = launchIo { _screen.value = Screen.StatsPage(repo.stats(now())) }

    fun saveClaudeKey(key: String) = launchIo {
        repo.saveClaudeKey(key)
        _screen.update { if (it is Screen.Settings) it.copy(claudeKey = repo.claudeKey()) else it }
    }

    fun saveSettings(settings: StudySettings) = launchIo {
        repo.saveSettings(settings)
        _screen.update { if (it is Screen.Settings) it.copy(settings = settings) else it }
    }

    fun setTheme(mode: ThemeMode) {
        display.edit().putString("theme", mode.name).apply()
        _theme.value = mode
    }

    fun setStoryVertical(vertical: Boolean) = launchIo {
        repo.setStoryVertical(vertical)
        _screen.update {
            when (it) {
                is Screen.StoryReader -> it.copy(vertical = vertical)
                is Screen.Settings -> it.copy(storyVertical = vertical)
                else -> it
            }
        }
    }

    // ------------------------------------------------------------ renshuu import

    fun openImport() {
        _screen.value = Screen.RenshuuImport(ImportStep.EnterKey())
    }

    fun closeImport() {
        importJob?.cancel()
        importJob = null
        openSettings()
    }

    private fun importStep(step: ImportStep) =
        _screen.update { if (it is Screen.RenshuuImport) Screen.RenshuuImport(step) else it }

    /** Download your studied words and show what importing them would change. Nothing is written yet. */
    fun fetchRenshuu(apiKey: String) {
        val key = apiKey.trim()
        if (key.isEmpty()) return
        importJob?.cancel()
        importStep(ImportStep.Fetching(0, 0))
        // Not under the lock: the download can take a while and shouldn't block leaving the screen.
        importJob = viewModelScope.launch {
            try {
                val terms = withContext(Dispatchers.IO) {
                    RenshuuClient(key).studiedVocab { page, pages ->
                        ensureActive()
                        importStep(ImportStep.Fetching(page, pages))
                    }
                }
                lock.withLock {
                    val plan = withContext(Dispatchers.IO) { repo.renshuuPlan(terms) }
                    importStep(ImportStep.Preview(plan))
                }
            } catch (e: RenshuuException) {
                importStep(ImportStep.EnterKey(e.message))
            }
        }
    }

    fun applyImport() = launchIo {
        val step = (_screen.value as? Screen.RenshuuImport)?.step as? ImportStep.Preview ?: return@launchIo
        importStep(ImportStep.Applying)
        repo.importMastery(step.plan.updates, now())
        importStep(ImportStep.Done(step.plan.updates.size))
    }

    // ------------------------------------------------------------ stories

    fun openStories() = launchIo { showStories() }

    private fun showStories() {
        _screen.value = Screen.Stories(
            stories = repo.stories(),
            draft = draft,
            hasClaudeKey = repo.claudeKey().isNotEmpty(),
            knownWords = repo.knownWordCount(),
            writing = storyProgress,
            error = storyError,
        )
    }

    fun editDraft(change: (StoryDraft) -> StoryDraft) {
        draft = change(draft)
        _screen.update { if (it is Screen.Stories) it.copy(draft = draft) else it }
    }

    private fun storyStatus(progress: String?, error: String?) {
        storyProgress = progress
        storyError = error
        _screen.update { if (it is Screen.Stories) it.copy(writing = progress, error = error) else it }
    }

    /** Write a story from the draft. It keeps going if you leave the screen, and opens when done if you're still here. */
    fun writeStory() {
        if (storyJob?.isActive == true) return
        val request = draft
        storyStatus("Getting your words ready", null)
        // Not under the lock: writing takes a while and shouldn't block the rest of the app.
        storyJob = viewModelScope.launch {
            try {
                val (id, queued) = withContext(Dispatchers.IO) {
                    val req = lock.withLock { repo.storyRequest(request.prompt, request.length, request.difficulty) }
                    val id = repo.writeStory(req, now()) { storyStatus(it, null) }
                    id to (repo.readStory(id)?.coverage?.newWords?.count { it.wordId != null } ?: 0)
                }
                storyStatus(null, null)
                draft = draft.copy(prompt = "")
                if (_screen.value is Screen.Stories) {
                    lock.withLock { withContext(Dispatchers.IO) { showStory(id, queued) } }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                storyStatus(null, e.message ?: "Something went wrong writing the story.")
            }
        }
    }

    fun openStory(id: Long) = launchIo {
        _screen.value = Screen.Loading
        showStory(id, null)
    }

    private fun showStory(id: Long, queued: Int?) {
        val reading = repo.readStory(id) ?: return showStories()
        _screen.value = Screen.StoryReader(reading, queued, repo.storyVertical())
    }

    fun deleteStory(id: Long) = launchIo {
        repo.deleteStory(id)
        showStories()
    }

    /** "I know this" on a word in a story: mark it known, as on a new card. */
    fun knowStoryWord(wordId: Long) = launchIo {
        val state = _screen.value as? Screen.StoryReader ?: return@launchIo
        repo.markKnown(wordId, repo.settings().mastery.hideAt, now())
        val reading = repo.readStory(state.reading.story.id) ?: return@launchIo
        _screen.update { if (it is Screen.StoryReader) it.copy(reading = reading) else it }
    }

    // ------------------------------------------------------------ dictionary

    fun openDictionary() = launchIo {
        if (entries == null) {
            entries = repo.dictionary.entries()
            entryIds = entries!!.mapTo(HashSet()) { it.id }
        }
        statuses = repo.wordStatuses()
        val state = Screen.Dictionary("", emptySet(), StatusFilter.ALL, emptyList())
        _screen.value = state.copy(rows = rows(state))
    }

    /** Words matching the query and filters, best match first. */
    private fun rows(state: Screen.Dictionary): List<DictionaryRow> {
        val jlpt = entries ?: return emptyList()
        val ranks = if (state.query.isBlank()) null else repo.dictionary.search(state.query)
        val others = if (ranks == null) {
            statuses.keys.filter { it !in entryIds }
        } else {
            ranks.keys.filter { it !in entryIds }.sortedBy { ranks.getValue(it) }.take(MAX_OTHER_HITS)
        }
        val all = jlpt + repo.dictionary.entries(others)
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

    companion object {
        /** Search hits beyond the JLPT words to list, best first. */
        private const val MAX_OTHER_HITS = 200
    }
}
