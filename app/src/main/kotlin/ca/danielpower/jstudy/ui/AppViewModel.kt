package ca.danielpower.jstudy.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.FuriganaMode
import ca.danielpower.jstudy.core.GrammarTopic
import ca.danielpower.jstudy.core.ImportPlan
import ca.danielpower.jstudy.core.KanaCourse
import ca.danielpower.jstudy.core.KanaKnown
import ca.danielpower.jstudy.core.KanaQuestion
import ca.danielpower.jstudy.core.KanaResult
import ca.danielpower.jstudy.core.KanaSession
import ca.danielpower.jstudy.core.KanaState
import ca.danielpower.jstudy.core.KanaUnit
import ca.danielpower.jstudy.core.Romaji
import ca.danielpower.jstudy.core.Kanji
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.NarrationLine
import ca.danielpower.jstudy.core.Pace
import ca.danielpower.jstudy.core.PhoneVoice
import ca.danielpower.jstudy.core.ReadLevel
import ca.danielpower.jstudy.core.Recording
import ca.danielpower.jstudy.core.StudyClock
import ca.danielpower.jstudy.core.StudySession
import ca.danielpower.jstudy.core.StudySettings
import ca.danielpower.jstudy.data.BackupException
import ca.danielpower.jstudy.data.ChapterEntry
import ca.danielpower.jstudy.data.Dashboard
import ca.danielpower.jstudy.data.Entry
import ca.danielpower.jstudy.data.GrammarRow
import ca.danielpower.jstudy.data.KanaExample
import ca.danielpower.jstudy.data.KanjiDetail
import ca.danielpower.jstudy.data.LockInfo
import ca.danielpower.jstudy.data.ReaderPlace
import ca.danielpower.jstudy.data.ReaderStyle
import ca.danielpower.jstudy.data.SpeechSettings
import ca.danielpower.jstudy.audio.SpokenLine
import ca.danielpower.jstudy.audio.StoryVoice
import ca.danielpower.jstudy.core.Voices
import ca.danielpower.jstudy.data.Reminder
import ca.danielpower.jstudy.data.Stats
import ca.danielpower.jstudy.reminder.DailyReminder
import ca.danielpower.jstudy.data.RenshuuClient
import ca.danielpower.jstudy.data.RenshuuException
import ca.danielpower.jstudy.data.Repository
import ca.danielpower.jstudy.data.SavedStory
import ca.danielpower.jstudy.data.SeriesEntry
import ca.danielpower.jstudy.data.StepToLearn
import ca.danielpower.jstudy.data.StoryReading
import ca.danielpower.jstudy.data.StoryUse
import ca.danielpower.jstudy.data.Summary
import ca.danielpower.jstudy.data.Undo
import ca.danielpower.jstudy.data.Word
import ca.danielpower.jstudy.data.WordDetail
import ca.danielpower.jstudy.data.WordStatus
import java.util.concurrent.ConcurrentHashMap
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
    val word: Word,
    /** Furigana on the front, from the word's mastery. */
    val furigana: FuriganaMode,
    val mastery: Int,
    /** The kanji in the word's spelling, linked from the answer side. */
    val kanji: List<Kanji> = emptyList(),
    /** The reading in romaji, while a kana in it isn't clear yet: on the answer side. */
    val romaji: String? = null,
    /** And on the front too, while a kana in it hasn't been practised at all. */
    val romajiOnFront: Boolean = false,
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
    data class Home(val dashboard: Dashboard) : Screen

    /** The Review tab: today's cards, study days, and the way into a session. */
    data class ReviewTab(val summary: Summary) : Screen
    data class Review(
        val current: ReviewCard,
        val revealed: Boolean,
        /** Whether furigana is currently visible on the front. */
        val furiganaVisible: Boolean,
        val counts: ReviewCounts,
        val canUndo: Boolean,
    ) : Screen

    /** Only learning steps remain and none is due yet. */
    data class Waiting(val dueAt: Long, val counts: ReviewCounts) : Screen
    data class Done(val summary: Summary) : Screen
    data class Settings(
        val settings: StudySettings,
        val attribution: String,
        /** The JLPT level you can already read, or [ReadLevel.NONE]. */
        val readLevel: Int,
        val levelCounts: Map<Int, Int>,
        val reminder: Reminder,
        /** The result of the last backup export or import. */
        val backupMessage: String? = null,
        /** When a backup was last saved, if ever. */
        val lastBackup: Long? = null,
        /** Which kana you read already. */
        val kana: KanaKnown = KanaKnown.BOTH,
    ) : Screen

    /** First run, a page at a time: the kana and level you can already read, reading direction, and the reminder. */
    data class Setup(
        val levelCounts: Map<Int, Int>,
        /** Which kana you can read already. */
        val kana: KanaKnown = KanaKnown.NONE,
        val readLevel: Int = ReadLevel.NONE,
        /** Whether stories are set vertically, as in the reader's settings menu. */
        val vertical: Boolean = false,
        /** The daily reminder's time, if you chose one. */
        val reminderMinute: Int?,
        val page: SetupPage = SetupPage.WELCOME,
    ) : Screen {
        /** The pages this setup shows: the reading direction only for someone who reads both kana, as vertical text waits until then. */
        val pages: List<SetupPage> get() = SetupPage.entries.filter { it != SetupPage.DIRECTION || kana == KanaKnown.BOTH }
    }

    /** Kana practice: a new set's intro, its questions, then how you did. */
    data class Kana(
        val step: KanaStep,
        /** New kana and kana to check still to get right, as the 新 and 習 counts show. */
        val newLeft: Int = 0,
        val checkLeft: Int = 0,
        /** How far through the sitting, from 0 to 1. */
        val progress: Float = 0f,
    ) : Screen

    data class StatsPage(val stats: Stats) : Screen

    data class Dictionary(
        val query: String,
        /** JLPT levels to show; empty shows all. */
        val levels: Set<Int>,
        val status: StatusFilter,
        val rows: List<DictionaryRow>,
        /** The grammar topics, while the Grammar tab is showing in place of the words. */
        val grammar: List<GrammarRow>? = null,
        /** The kana chart, while the Kana tab is showing. */
        val kana: KanaChart? = null,
    ) : Screen

    data class WordPage(val detail: WordDetail, val clock: StudyClock) : Screen

    data class KanjiPage(val detail: KanjiDetail) : Screen

    data class RenshuuImport(val step: ImportStep) : Screen

    data class Stories(
        /** The stories that come with the app, in order, ready or not. */
        val path: List<SeriesEntry>,
        /** Today's new words against the daily limit, and reviews waiting. */
        val pace: Pace? = null,
        /** Open chapters you haven't read that have nothing new to learn. */
        val nothingNew: List<ChapterEntry> = emptyList(),
        /** Where you left a chapter part way, for its story's bar. */
        val place: ReaderPlace? = null,
        /** Why a story under Coming up is locked, once you tap it. */
        val lock: LockInfo? = null,
    ) : Screen

    /** A story with more than one chapter: its chapters, and why one is locked once you tap it. */
    data class Series(val entry: SeriesEntry, val lock: LockInfo? = null) : Screen

    data class StoryReader(
        val reading: StoryReading,
        val style: ReaderStyle,
        /** For a chapter, where it sits in its story. */
        val chapter: ChapterEntry? = null,
        /** For a chapter, what to read next: the story's next open chapter, or the next story on the path. */
        val next: NextStep? = null,
        /** For a path story, once read: what reading it did for your words. */
        val impact: ReadImpact? = null,
        /** For a path story, once read: today's pace, and something with nothing new to read next. */
        val pace: Pace? = null,
        val rest: NextStep? = null,
        /** For a grammar page opened before a chapter: that chapter, to read once it's done. */
        val then: NextStep? = null,
        /** The page to open on, when coming back from a grammar page. */
        val startPage: Int = 0,
        /** Or a word to open on the page of, with its sentence shown: from a word's page. */
        val startAt: Pair<Int, Int>? = null,
        /** Words you said you forgot: each gets a Wrong review when you finish. */
        val forgotten: Set<Long> = emptySet(),
        /** Words the finished story started that you said you already know. */
        val knewNew: Set<Long> = emptySet(),
        /** Vertical text just came back, with every kana clear: the reader's gear marks it until its menu is opened. */
        val verticalNew: Boolean = false,
    ) : Screen
}

/** What kana practice shows. */
sealed interface KanaStep {
    /** A new set: each kana, its sound, and a word from the stories with it in, by kana. */
    data class Intro(val units: List<KanaUnit>, val examples: Map<String, KanaExample>, val set: Int, val sets: Int) : KanaStep

    /**
     * A question, shown at [shownAt], about a kana from this sitting's new set
     * or not ([fresh]); once answered, what you [picked] and how it went.
     */
    data class Ask(val question: KanaQuestion, val shownAt: Long, val fresh: Boolean, val picked: String? = null, val result: KanaResult? = null) : KanaStep

    /**
     * The end: how many kana were new and how many checked, the chart, how
     * many of today's new kana have their second look later, whether another
     * set is ready, and the word cards waiting next when Start studying began it.
     */
    data class Done(val learned: Int, val checked: Int, val chart: KanaChart, val later: Int, val more: Boolean, val cardsNext: Int) : KanaStep
}

/** The kana chart: every kana's stage, and today, to pick out the ones met today. */
data class KanaChart(val state: KanaState, val today: Long)

/** Past today's limit, a story that would add [adding] new words, before it opens. */
data class LimitPrompt(
    val storyId: Long,
    val title: String,
    /** "Chapter 2", for a story with more than one. */
    val chapter: String?,
    val adding: Int,
    val newToday: Int,
    /** Whether anything with nothing new is open to read instead. */
    val nothingNewOpen: Boolean,
)

/**
 * What reading a path story did: the words you first saw for it, and the
 * ones that crossed into known (mastery 40, where furigana hides) or
 * mastered (70, where it's gone).
 */
data class ReadImpact(
    val seen: List<Word>,
    val known: List<Word>,
    val mastered: List<Word>,
    /** The grammar topic it teaches, learned on its page before the chapter; only the first time it's read. */
    val topic: GrammarTopic? = null,
    /** The words you tapped while reading it, the new ones aside, in story order. */
    val lookedUp: List<Word> = emptyList(),
)

/** The next chapter to read, and how to name it on the button. */
data class NextStep(val storyId: Long, val label: String)

enum class SetupPage { WELCOME, KANA, LEVEL, DIRECTION, REMINDER }

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

    /** Shown over the screen you tapped a story on. */
    private val _limit = MutableStateFlow<LimitPrompt?>(null)
    val limit: StateFlow<LimitPrompt?> = _limit.asStateFlow()

    /** Cards a review session would show now, for the Review tab's badge. */
    private val _due = MutableStateFlow(0)
    val due: StateFlow<Int> = _due.asStateFlow()

    /** How stories are read aloud. */
    private val _speech = MutableStateFlow(SpeechSettings())
    val speech: StateFlow<SpeechSettings> = _speech.asStateFlow()

    // The phone's speech engine, started the first time something is read aloud or a reader opens.
    private var voiceEngine: StoryVoice? = null
    private val voice: StoryVoice get() = voiceEngine ?: StoryVoice(getApplication()).also { voiceEngine = it }
    val voiceState: StateFlow<StoryVoice.State> get() = voice.state

    private lateinit var repo: Repository
    private var session: StudySession? = null
    // Where Back goes from a grammar page: the screen it was opened from.
    private var beforeGrammar: Screen? = null
    // The page the open story is on, set from the main thread.
    @Volatile private var readerPage = 0
    private var undo: Undo? = null
    private val lock = Mutex()

    /** The JLPT words. Other words join the list when a search finds them or you've started them. */
    private var entries: List<Entry>? = null
    private var entryIds: Set<Long> = emptySet()
    private var statuses: Map<Long, WordStatus> = emptyMap()
    private var lastDictionary: Screen.Dictionary? = null

    /** Where Back goes from kanji pages and the words opened from them, most recent last. */
    private val trail = ArrayDeque<Screen>()
    private var searchJob: Job? = null
    private var importJob: Job? = null

    // When the open story was opened, to time the reading, and whether its end has been reached.
    private var readingSince = 0L
    private var readingFinished = false
    // For the open path story: your mastery when it opened, and the words you
    // looked up, which reading doesn't credit.
    private var masteryAtOpen: Map<Long, Int> = emptyMap()
    // Tapped on the main thread, read when the story ends.
    private val lookedUpWords: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    // The chapter end page a review was started from, for closing it.
    private var reviewFrom: Screen.StoryReader? = null
    // Kana practice: the sitting, whether word cards follow it, and the tab it was started from.
    private var kanaSession: KanaSession? = null
    private var kanaThenCards = false
    private var kanaFromReviewTab = false
    // The kana not clear yet as the review session began, for its cards' romaji.
    private var reviewKana: KanaState? = null

    init {
        launchIo {
            repo = Repository.get(getApplication())
            _speech.value = repo.speechSettings()
            DailyReminder.schedule(getApplication(), repo.reminder())
            if (repo.needsSetup()) {
                _screen.value = Screen.Setup(repo.levelCounts(), reminderMinute = null)
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

    /** Recount the Review tab's badge. Most screens do this on the way in. */
    private fun refreshBadge() {
        _due.value = repo.dueCount(now())
    }


    private fun showHome() {
        reviewFrom = null
        refreshBadge()
        trail.clear()
        session = null
        undo = null
        _screen.value = Screen.Home(repo.dashboard(now()))
    }

    fun goHome() = launchIo { showHome() }

    /** Leave a review: back to the chapter end page it started from, else Home. */
    fun closeReview() = launchIo {
        val from = reviewFrom ?: return@launchIo showHome()
        reviewFrom = null
        session = null
        undo = null
        refreshBadge()
        _screen.value = from.copy(pace = from.pace?.let { repo.pace(now()) })
    }

    fun openReviewTab() = launchIo { showReviewTab() }

    private fun showReviewTab() {
        refreshBadge()
        trail.clear()
        session = null
        undo = null
        _screen.value = Screen.ReviewTab(repo.summary(now()))
    }

    /**
     * The system back button: word page to where it was opened, import to
     * settings, story to stories, stats to the Review tab, a review to the
     * chapter end page it started from, anything else home.
     */
    fun back() {
        when (_screen.value) {
            is Screen.Review, is Screen.Waiting, is Screen.Done -> closeReview()
            is Screen.WordPage -> closeWord()
            is Screen.KanjiPage -> goBack()
            is Screen.RenshuuImport -> closeImport()
            is Screen.StoryReader, is Screen.Series -> storyBack()
            is Screen.Setup -> setupBack()
            is Screen.Kana -> closeKana()
            is Screen.StatsPage -> openReviewTab()
            else -> goHome()
        }
    }

    fun startReview() = launchIo { beginReview() }

    private fun beginReview() {
        // From a chapter's end page, closing the review comes back to it.
        reviewFrom = (_screen.value as? Screen.StoryReader)?.takeIf { it.impact != null }
        session = repo.session(now())
        reviewKana = repo.kanaState()
        undo = null
        advance()
    }

    fun studyMore() = launchIo {
        repo.studyMore(now())
        session = repo.session(now())
        reviewKana = repo.kanaState()
        undo = null
        advance()
    }

    /** Show the next card, or the waiting or done screen. */
    private fun advance() {
        val s = session ?: return showHome()
        refreshBadge()
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
            s.drop(card.id)
            return advance()
        }
        val mastery = repo.progress(card.wordId).mastery
        val mode = MasteryRules.furigana(mastery)
        val kana = KanaCourse.unitsIn(word.kana).map { it.text }
        val unclear = reviewKana?.unclear.orEmpty()
        _screen.value = Screen.Review(
            current = ReviewCard(
                card, word, mode, mastery, kanji = repo.kanjiIn(word),
                romaji = Romaji.of(word.kana).takeIf { kana.any { it in unclear } },
                romajiOnFront = reviewKana?.unread.orEmpty().let { unread -> kana.any { it in unread } },
            ),
            revealed = false,
            furiganaVisible = mode == FuriganaMode.SHOWN,
            counts = counts,
            canUndo = undo != null,
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

    fun undoLast() = launchIo {
        val u = undo ?: return@launchIo
        repo.undo(u)
        session?.restore(u.cardBefore)
        undo = null
        advance()
    }

    /** Re-check the queue, e.g. when a learning step falls due. */
    fun refresh() = launchIo { if (session != null) advance() }

    fun openSettings() = launchIo {
        trail.clear()
        showSettings(null)
    }

    private fun showSettings(backupMessage: String?) {
        refreshBadge()
        val attribution = repo.dictionary.meta("attribution").orEmpty()
        _screen.value = Screen.Settings(
            settings = repo.settings(),
            attribution = attribution,
            readLevel = repo.readLevel(),
            levelCounts = repo.levelCounts(),
            reminder = repo.reminder(),
            backupMessage = backupMessage,
            lastBackup = repo.lastBackup(),
            kana = repo.kanaKnown(),
        )
    }

    /** Which kana you read already, from Settings: ones you read are never practised, and the rest are. */
    fun setKanaKnown(known: KanaKnown) = launchIo {
        repo.setKanaKnown(known)
        refreshBadge()
        _screen.update { if (it is Screen.Settings) it.copy(kana = known) else it }
    }

    /** Read at [level] from now on; raising it fills in that level's words and the easier ones. */
    fun setReadLevel(level: Int) = launchIo {
        repo.setReadLevel(level, now())
        refreshBadge()
        _screen.update { if (it is Screen.Settings) it.copy(readLevel = repo.readLevel()) else it }
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
            out.use { repo.exportBackup(it, now()) }
            "Backup saved."
        } catch (e: Exception) {
            "Couldn't save the backup: ${e.message}"
        }
        _screen.update { if (it is Screen.Settings) it.copy(backupMessage = message, lastBackup = repo.lastBackup()) else it }
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

    /** Wipe all progress, settings and stories read, and start again at first-run setup. */
    fun resetAll() {
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
            display.edit().clear().commit()
            _theme.value = ThemeMode.AUTO
            DailyReminder.schedule(getApplication(), repo.reminder())
            refreshBadge()
            _screen.value = Screen.Setup(repo.levelCounts(), reminderMinute = null)
        }
    }

    // ------------------------------------------------------------ first run

    fun editSetup(change: (Screen.Setup) -> Screen.Setup) = _screen.update { if (it is Screen.Setup) change(it) else it }

    fun setupNext() = editSetup { s -> s.pages.getOrNull(s.pages.indexOf(s.page) + 1)?.let { s.copy(page = it) } ?: s }

    fun setupBack() = editSetup { s -> s.pages.getOrNull(s.pages.indexOf(s.page) - 1)?.let { s.copy(page = it) } ?: s }

    fun finishSetup() = launchIo {
        val setup = _screen.value as? Screen.Setup ?: return@launchIo
        _screen.value = Screen.Loading
        repo.finishSetup(setup.readLevel, setup.kana, setup.vertical, setup.reminderMinute, now())
        DailyReminder.schedule(getApplication(), repo.reminder())
        showHome()
    }

    // ------------------------------------------------------------ kana

    /** Start studying, on the Review tab: any kana due first, then the word cards. */
    fun startStudy() = launchIo {
        if ((repo.kanaCounts(now())?.due ?: 0) > 0) beginKana(thenCards = true) else beginReview()
    }

    /** Kana practice on its own, from Home's Next card. */
    fun startKana() = launchIo { beginKana(thenCards = false) }

    /** "5 more" at the end of kana practice: the next set, now. */
    fun moreKana() = launchIo { beginKana(thenCards = kanaThenCards, more = true) }

    private fun beginKana(thenCards: Boolean, more: Boolean = false) {
        if (!more) kanaFromReviewTab = _screen.value is Screen.ReviewTab
        kanaThenCards = thenCards
        // Until the phone's speech engine says, assume it has a Japanese voice; it's started here, for the sounds.
        val phone = voiceEngine?.state?.value
        repo.hasJapaneseVoice = phone == null || !phone.ready || !phone.missing
        if (voiceEngine == null) viewModelScope.launch { voice.state }
        val s = repo.kanaSession(now(), more)
        kanaSession = s
        if (s.newSet.isEmpty()) return askKana()
        val state = repo.kanaState()
        val texts = s.newSet.map { it.text }
        _screen.value = Screen.Kana(
            KanaStep.Intro(s.newSet, repo.kanaExamples(texts), state.setNumber(texts.first()), state.sets.size),
            s.newRemaining,
            s.checkRemaining,
        )
    }

    /** On from a new set's intro; with [know], "I know these": each is asked once, and quick and right is clear. */
    fun practiseKana(know: Boolean = false) = launchIo {
        if (know) kanaSession?.checkKnown()
        askKana()
    }

    private fun askKana() {
        val s = kanaSession ?: return showHome()
        val q = s.next() ?: return kanaDone()
        val fresh = s.newSet.any { it.text == q.unit.text }
        _screen.value = Screen.Kana(KanaStep.Ask(q, now(), fresh), s.newRemaining, s.checkRemaining, kanaProgress(s))
        // Asked by sound, the sound plays as the question opens.
        if (q.listen) sayKana(q.unit.text)
    }

    private fun kanaProgress(s: KanaSession) = s.answered.toFloat() / (s.answered + s.asksLeft).coerceAtLeast(1)

    /** Answer the open kana question with [choice]: timed from when it opened, and saved straight away. */
    fun answerKana(choice: String) = launchIo {
        val state = _screen.value as? Screen.Kana ?: return@launchIo
        val ask = state.step as? KanaStep.Ask ?: return@launchIo
        if (ask.result != null) return@launchIo
        val s = kanaSession ?: return@launchIo
        val at = now()
        val millis = at - ask.shownAt
        val result = s.answer(ask.question, choice, millis, at)
        repo.saveKana(result, at, millis)
        if (ask.fresh) repo.kanaSetStarted(at)
        _screen.value = Screen.Kana(ask.copy(picked = choice, result = result), s.newRemaining, s.checkRemaining, kanaProgress(s))
        if (result.correct) sayKanaAnswer(ask.question)
    }

    /** On from an answered question: a right one goes by itself, a wrong one on a tap. */
    fun nextKana() = launchIo {
        val ask = (_screen.value as? Screen.Kana)?.step as? KanaStep.Ask ?: return@launchIo
        if (ask.result != null) askKana()
    }

    private fun kanaDone() {
        val s = kanaSession ?: return showHome()
        val at = now()
        val state = repo.kanaState()
        val today = repo.clock().dayOf(at)
        val counts = state.counts(today, at)
        val brought = s.newSet.map { it.text }.toSet()
        val practised = s.practised
        _screen.value = Screen.Kana(
            KanaStep.Done(
                learned = practised.count { it in brought },
                checked = practised.count { it !in brought },
                chart = KanaChart(state, today),
                later = counts.later,
                more = state.nextSet() != null,
                cardsNext = if (kanaThenCards) repo.cardsDue(at) else 0,
            ),
            progress = 1f,
        )
    }

    /** After kana practice started from Start studying: on to the word cards. */
    fun kanaThenReview() = launchIo {
        kanaSession = null
        beginReview()
    }

    /** Leave kana practice: what's answered is saved already. */
    fun closeKana() = launchIo {
        kanaSession = null
        if (kanaFromReviewTab) showReviewTab() else showHome()
    }

    /** A kana's sound: its recording, else the phone's voice. */
    fun sayKana(text: String) {
        viewModelScope.launch {
            val clip = withContext(Dispatchers.IO) { repo.kanaClip(text) }
            val rate = _speech.value.rate
            if (clip == null) {
                voice.say(text, rate) { voices -> Voices.narrator(voices, null) }
            } else {
                voice.play(KANA_KEY, 0, rate, single = true) { voices -> listOf(SpokenLine(text, Voices.narrator(voices, null), clip)) }
            }
        }
    }

    /** The sound of a right answer: the kana's, or for a mark, the word it's in. */
    private fun sayKanaAnswer(question: KanaQuestion) {
        if (question.unit.mark) viewModelScope.launch { voice.say(question.prompt, _speech.value.rate) { Voices.narrator(it, null) } } else sayKana(question.unit.text)
    }

    // ------------------------------------------------------------ stats

    fun openStats() = launchIo { _screen.value = Screen.StatsPage(repo.stats(now())) }

    fun saveSettings(settings: StudySettings) = launchIo {
        repo.saveSettings(settings)
        _screen.update { if (it is Screen.Settings) it.copy(settings = settings) else it }
    }

    fun setTheme(mode: ThemeMode) {
        display.edit().putString("theme", mode.name).apply()
        _theme.value = mode
    }

    fun setReaderStyle(style: ReaderStyle) {
        _screen.update { if (it is Screen.StoryReader) it.copy(style = style) else it }
        launchIo { repo.setReaderStyle(style) }
    }

    /** The reader's settings menu opened: vertical text is no longer new. */
    fun readerMenuOpened() {
        val state = _screen.value as? Screen.StoryReader ?: return
        if (!state.verticalNew) return
        _screen.update { if (it is Screen.StoryReader) it.copy(verticalNew = false) else it }
        launchIo { repo.markVerticalSeen() }
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

    fun openStories() = launchIo {
        trail.clear()
        showStories()
    }

    private fun showStories() {
        val path = repo.storyPath()
        _screen.value = Screen.Stories(
            path = path,
            pace = repo.pace(now()),
            nothingNew = nothingNew(path),
            place = repo.readerPlace(),
        )
    }

    /** Open chapters you haven't read with nothing new to learn, story order. */
    private fun nothingNew(path: List<SeriesEntry>) = path.flatMap { it.chapters }.filter { it.nothingNew && !it.read }

    /** A story from the path: one chapter opens straight away, more show their chapters. */
    fun openPathStory(entry: SeriesEntry) {
        if (entry.series.single) entry.chapters.firstOrNull()?.let { openStory(it.story.id) } else openSeries(entry.series.id)
    }

    fun openSeries(id: String) = launchIo { showSeries(id) }

    private fun showSeries(id: String) {
        val entry = repo.storyPath().firstOrNull { it.series.id == id } ?: return showStories()
        _screen.value = Screen.Series(entry)
    }

    /** Tapping a locked chapter, or a story under Coming up: what it's missing, and where to find it. */
    fun showLock(chapterId: String) = launchIo {
        val lock = repo.lockInfo(chapterId) ?: return@launchIo
        _screen.update {
            when (it) {
                is Screen.Series -> it.copy(lock = lock)
                is Screen.Stories -> it.copy(lock = lock)
                else -> it
            }
        }
    }

    fun closeLock() = _screen.update {
        when (it) {
            is Screen.Series -> it.copy(lock = null)
            is Screen.Stories -> it.copy(lock = null)
            else -> it
        }
    }

    /** Back from a story screen: a grammar page to where it was opened, a chapter to its story's list, the rest to the stories. */
    private fun storyBack() = launchIo {
        val screen = _screen.value
        val grammarFrom = beforeGrammar
        val chapter = when (screen) {
            // A grammar page before a chapter goes back to its chapters.
            is Screen.StoryReader -> if (screen.reading.lesson == null) screen.chapter else screen.then?.let { repo.story(it.storyId) }?.let { repo.chapterEntry(it) }
            else -> null
        }
        when {
            screen is Screen.Series && screen.lock != null -> closeLock()
            screen is Screen.StoryReader && screen.reading.lesson != null && grammarFrom != null -> {
                beforeGrammar = null
                if (grammarFrom is Screen.Dictionary) _screen.value = grammarFrom.copy(grammar = repo.grammarRows()) else returnTo(grammarFrom)
            }
            // A chapter opened from a word's page goes back to it.
            screen is Screen.StoryReader && trail.lastOrNull() is Screen.WordPage -> returnTo(trail.removeLastOrNull())
            chapter != null && !chapter.series.single -> showSeries(chapter.series.id)
            else -> showStories()
        }
    }

    /** The back arrow on story screens. */
    fun storyBackPressed() = storyBack()

    /**
     * Open a story. A chapter's new grammar's page comes first, then the
     * chapter; its new words you learn by reading.
     * Past today's limit, a chapter that adds more asks first, unless [anyway].
     */
    fun openStory(id: Long, anyway: Boolean = false) = launchIo {
        _limit.value = null
        val story = repo.story(id) ?: return@launchIo showStories()
        val learn = repo.stepToLearn(story)
        val adding = learn?.newWords ?: 0
        val pace = repo.pace(now())
        if (!anyway && pace.over(adding)) {
            val chapter = repo.chapterEntry(story)
            _limit.value = LimitPrompt(
                id, story.title, chapter?.takeIf { !it.series.single }?.let { "Chapter ${it.chapter.number}" },
                adding, pace.newToday, nothingNew(repo.storyPath()).isNotEmpty(),
            )
            return@launchIo
        }
        continueChapter(story, learn)
    }

    /** On towards reading [story]: its new grammar's page, then the story. */
    private fun continueChapter(story: SavedStory, learn: StepToLearn?) {
        val page = learn?.topic?.let { repo.grammarPageId(it) } ?: return showStory(story.id)
        val chapter = repo.chapterEntry(story)
        val name = if (chapter != null && !chapter.series.single) "chapter ${chapter.chapter.number}: ${chapter.chapter.story.title}" else story.title
        beforeGrammar = null
        showStory(page, then = NextStep(story.id, "Read $name"))
    }

    /** The button at the end of a grammar page before a chapter: the grammar counts as seen, and the chapter opens. */
    fun readAfterLesson() = launchIo {
        val state = _screen.value as? Screen.StoryReader ?: return@launchIo
        val then = state.then ?: return@launchIo
        repo.topicOfPage(state.reading.story)?.let { repo.learnGrammar(it, now()) }
        showStory(then.storyId)
    }

    /** Past today's limit, before a chapter: the stories with nothing new, or reviews if none are open. */
    fun readNothingNew() {
        val prompt = _limit.value
        _limit.value = null
        if (prompt?.nothingNewOpen == false) startReview() else openStories()
    }

    fun closeLimit() {
        _limit.value = null
    }

    /**
     * A grammar topic's page, from the Grammar list or a grammar point in a
     * story; Back returns there, to the same page of the story. From one
     * grammar page to another, Back skips the first.
     */
    fun openGrammar(pageId: Long) = launchIo {
        val screen = _screen.value
        if (screen !is Screen.StoryReader || screen.reading.lesson == null) {
            beforeGrammar = if (screen is Screen.StoryReader) screen.copy(startPage = readerPage, startAt = null) else screen
        }
        showStory(pageId)
    }

    /** The page the open story has settled on; for an unfinished chapter, kept so Home can come back to it. */
    fun readerAt(page: Int, pages: Int) {
        readerPage = page
        val state = _screen.value as? Screen.StoryReader ?: return
        if (state.reading.lesson != null || state.impact != null || state.reading.story.readAt != null) return
        val place = ReaderPlace(state.reading.story.id, page, pages, state.style.toString())
        viewModelScope.launch(Dispatchers.IO) { repo.saveReaderPlace(place) }
    }

    /** A sentence on a word's page: its chapter opens on that page with the sentence shown, and Back comes back to the word. */
    fun openStoryAt(use: StoryUse) = launchIo {
        val page = _screen.value as? Screen.WordPage ?: return@launchIo
        trail.addLast(page)
        showStory(use.storyId, startAt = use.paragraph to use.index)
    }

    /** Open a story, or a grammar page; a grammar page leaves the story it was opened from as it was. */
    private fun showStory(id: Long, then: NextStep? = null, startAt: Pair<Int, Int>? = null) {
        val reading = repo.readStory(id) ?: return showStories()
        val style = repo.readerStyle()
        var startPage = 0
        if (reading.lesson == null) {
            readingSince = now()
            readingFinished = false
            masteryAtOpen = repo.masteries(reading.words.keys)
            lookedUpWords.clear()
            // Back where you left it, unless the pages have been laid out differently since.
            startPage = repo.readerPlace()?.takeIf { it.storyId == id && it.style == style.toString() }?.page ?: 0
            readerPage = startPage
        }
        val chapter = repo.chapterEntry(reading.story)
        _screen.value = Screen.StoryReader(
            reading, style, chapter = chapter, next = chapter?.let(::nextStep), then = then, startPage = startPage, startAt = startAt,
            verticalNew = repo.verticalNew(),
        )
    }

    /**
     * After [chapter]: the chapter that follows it, read or not, when it's open; else its story's next
     * open, unread chapter, or else the next story on the path that has one.
     */
    private fun nextStep(chapter: ChapterEntry): NextStep? {
        val path = repo.storyPath()
        val here = path.firstOrNull { it.series.id == chapter.series.id }
        val following = here?.chapters?.let { c -> c.getOrNull(c.indexOfFirst { it.story.id == chapter.story.id } + 1) }
        val sameStory = following?.takeIf { it.ready || it.read }
            ?: here?.chapters?.let { c -> c.drop(c.indexOfFirst { it.story.id == chapter.story.id } + 1) + c }
                ?.firstOrNull { it.ready && !it.read && it.story.id != chapter.story.id }
        if (sameStory != null) return NextStep(sameStory.story.id, "Next: ${sameStory.chapter.number}. ${sameStory.chapter.story.title}")
        val other = path.firstOrNull { it.series.id != chapter.series.id && it.next != null } ?: return null
        val next = other.next!!
        return NextStep(next.story.id, "Next story: ${other.series.title}")
    }

    /** The finish stamp at the end of the text marks the story read, timed from when it was opened, and shows the summary. */
    fun finishStory() = launchIo {
        val state = _screen.value as? Screen.StoryReader ?: return@launchIo
        if (readingFinished) return@launchIo
        readingFinished = true
        val at = now()
        val story = state.reading.story
        val lookedUp = lookedUpWords.toSet()
        val started = repo.finishChapter(story, at, at - readingSince, lookedUp, state.forgotten)
        // The words read and started change what's due.
        refreshBadge()
        val after = repo.masteries(state.reading.words.keys)
        fun crossed(line: Int) = after.filter { (id, m) -> (masteryAtOpen[id] ?: 0) < line && m >= line }.keys
        val words = state.reading.words
        val impact = ReadImpact(
            seen = started.mapNotNull { words[it] },
            known = crossed(MasteryRules.HIDE_AT).mapNotNull { words[it] },
            mastered = crossed(MasteryRules.OFF_AT).mapNotNull { words[it] },
            // Worked out from what's saved, so leaving the chapter and coming back keeps it.
            topic = state.reading.teaches?.takeIf { story.readAt == null && repo.seenGrammar().containsAll(it.points) },
            lookedUp = state.reading.paragraphs.flatten().mapNotNull { it.wordId }.distinct()
                .filter { it in lookedUp && it !in started }.mapNotNull { words[it] },
        )
        val rest = nothingNew(repo.storyPath()).firstOrNull { it.story.id != story.id }?.let {
            val name = if (it.series.single) it.series.title else "${it.series.title} ${it.chapter.number}"
            NextStep(it.story.id, "Next: $name · nothing new")
        }
        val pace = repo.pace(at)
        _screen.update { if (it is Screen.StoryReader) it.copy(impact = impact, pace = pace, rest = rest) else it }
        refreshStory()
    }

    /** "Read it again" on a story's end page: it opens again on its first page. */
    fun readAgain() = launchIo {
        val state = _screen.value as? Screen.StoryReader ?: return@launchIo
        showStory(state.reading.story.id)
    }

    /** "I know this" on a word the finished story started, or its Undo: it stops counting against today's new words. */
    fun toggleKnewNew(wordId: Long) = launchIo {
        val state = _screen.value as? Screen.StoryReader ?: return@launchIo
        if (state.impact == null) return@launchIo
        val at = now()
        val knew = wordId in state.knewNew
        if (knew) repo.unknowStartedWord(wordId, at) else repo.knowStartedWord(wordId, at)
        val pace = repo.pace(at)
        _screen.update {
            if (it !is Screen.StoryReader) it
            else it.copy(knewNew = if (knew) it.knewNew - wordId else it.knewNew + wordId, pace = pace)
        }
    }

    /** "I forgot this" on a word in the open story, or its Undo. */
    fun toggleForgot(wordId: Long) {
        _screen.update {
            if (it !is Screen.StoryReader) it
            else it.copy(forgotten = if (wordId in it.forgotten) it.forgotten - wordId else it.forgotten + wordId)
        }
    }

    /** A word tapped in the open story: you looked it up, so reading it doesn't count as recalling it. */
    fun lookedUp(wordId: Long) {
        lookedUpWords += wordId
    }

    // ------------------------------------------------------------ reading aloud

    /** The open story read aloud from line [from] of its narration, or only that line when [single]. */
    fun play(from: Int, single: Boolean = false) {
        val state = _screen.value as? Screen.StoryReader ?: return
        val reading = state.reading
        voice.play(reading.story.id, from, _speech.value.rate, single, spokenLines(reading.narration, reading.speakers, reading.clips))
    }

    /** Only the open story's sentence at line [line], as the sentence card's play button does. */
    fun playLine(line: Int) = play(line, single = true)

    /** A sentence on a word's page, from its recording, or the phone's voice for its speaker. */
    fun playStoryUse(use: StoryUse) {
        val line = use.line ?: return
        voice.play(null, 0, _speech.value.rate, single = true, spokenLines(listOf(line), listOf(use.speaker), listOf(use.clip)))
    }

    /**
     * [lines] to read aloud: from their recordings, or where a line has none,
     * the phone's voices, each of the cast in their own and the rest in the phone's first.
     */
    private fun spokenLines(lines: List<NarrationLine>, speakers: List<String?>, clips: List<Recording?>): (List<PhoneVoice>) -> List<SpokenLine> {
        val cast = repo.castVoices()
        return { voices ->
            val narrator = Voices.narrator(voices, null)
            val named = cast.map { it.first }.toSet()
            val others = speakers.filterNotNull().distinct().filter { it !in named }.map { it to Voices.forLabel(it) }
            val voiceOf = Voices.assign(voices, narrator.name, cast + others)
            lines.mapIndexed { i, line -> SpokenLine(line.text, line.speaker?.let(voiceOf::get) ?: narrator, clips.getOrNull(i)) }
        }
    }

    /** A grammar page's line [n] from your stories, on its own: its recording, or its speaker's phone voice. */
    fun playStoryLine(n: Int) {
        val state = _screen.value as? Screen.StoryReader ?: return
        val line = state.reading.fromStories.getOrNull(n) ?: return
        val cast = repo.castVoices()
        // Not the page's own narration, so the reader marks no line of it as playing.
        voice.play(STORY_LINE_KEY, 0, _speech.value.rate) { voices ->
            val narrator = Voices.narrator(voices, null)
            val spoken = line.speaker?.let { speaker ->
                val others = if (cast.any { it.first == speaker }) emptyList() else listOf(speaker to Voices.forLabel(speaker))
                Voices.assign(voices, narrator.name, cast + others)[speaker]
            }
            listOf(SpokenLine(line.text, spoken ?: narrator, line.clip))
        }
    }

    fun pausePlayback() = voice.pause()

    /** Leaving the reader stops it reading. */
    fun stopPlayback() {
        voiceEngine?.stop()
    }

    /** A new page, while not reading: the next play starts from it, not where reading last stopped. */
    fun forgetPlaybackPlace() {
        voiceEngine?.forgetPlace()
    }

    fun setSpeechRate(rate: Float) {
        setSpeech(_speech.value.copy(rate = rate))
        // Carries on at the new speed from the line it's on.
        val state = voice.state.value
        if (state.playing) state.line?.let(::play)
    }

    private fun setSpeech(settings: SpeechSettings) {
        _speech.value = settings
        launchIo { repo.setSpeechSettings(settings) }
    }

    /** A word's speaker button: its reading, in the phone's voice. */
    fun sayWord(kana: String) {
        voice.say(kana, _speech.value.rate) { voices -> Voices.narrator(voices, null) }
    }

    override fun onCleared() {
        voiceEngine?.shutdown()
    }

    private fun refreshStory() {
        val state = _screen.value as? Screen.StoryReader ?: return
        val reading = repo.readStory(state.reading.story.id) ?: return
        val chapter = repo.chapterEntry(reading.story)
        val next = chapter?.let(::nextStep)
        _screen.update { if (it is Screen.StoryReader) it.copy(reading = reading, chapter = chapter, next = next) else it }
    }

    // ------------------------------------------------------------ dictionary

    /** The Dictionary tab, filtered to [status], or with its Grammar list showing when [grammar]. */
    fun openDictionary(status: StatusFilter = StatusFilter.ALL, grammar: Boolean = false, kana: Boolean = false) = launchIo {
        trail.clear()
        if (entries == null) {
            entries = repo.dictionary.entries()
            entryIds = entries!!.mapTo(HashSet()) { it.id }
        }
        statuses = repo.wordStatuses()
        val state = Screen.Dictionary("", emptySet(), status, emptyList(), if (grammar) repo.grammarRows() else null, if (kana) kanaChart() else null)
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

    /** The Dictionary tab's Grammar list in place of the words, or back to the words. */
    fun showGrammar(show: Boolean) = launchIo {
        val rows = if (show) repo.grammarRows() else null
        _screen.update { if (it is Screen.Dictionary) it.copy(grammar = rows, kana = null) else it }
    }

    /** The Dictionary tab's Kana chart in place of the words, or back to the words. */
    fun showKana(show: Boolean) = launchIo {
        val chart = if (show) kanaChart() else null
        _screen.update { if (it is Screen.Dictionary) it.copy(kana = chart, grammar = null) else it }
    }

    private fun kanaChart() = KanaChart(repo.kanaState(), repo.clock().dayOf(now()))

    fun search(query: String) = filterDictionary(150) { it.copy(query = query) }

    fun toggleLevel(level: Int) = filterDictionary(0) {
        it.copy(levels = if (level in it.levels) it.levels - level else it.levels + level)
    }

    fun setStatusFilter(status: StatusFilter) = filterDictionary(0) { it.copy(status = status) }

    fun openWord(wordId: Long) = launchIo {
        when (val current = _screen.value) {
            is Screen.Dictionary -> lastDictionary = current
            // A story comes back on the page you were reading.
            is Screen.StoryReader -> trail.addLast(current.copy(startPage = readerPage, startAt = null))
            // Anywhere else (Home's Recent words, a kanji page) is where Back returns.
            else -> trail.addLast(current)
        }
        showWord(wordId)
    }

    private fun showWord(wordId: Long) {
        refreshBadge()
        val detail = repo.wordDetail(wordId) ?: return
        val settings = repo.settings()
        _screen.value = Screen.WordPage(detail, repo.clock(settings))
    }

    fun closeWord() = launchIo {
        trail.removeLastOrNull()?.let { return@launchIo returnTo(it) }
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

    // ------------------------------------------------------------ kanji

    /** Open a kanji's page; Back returns to the screen it was opened from. */
    fun openKanji(literal: String) = launchIo {
        val detail = repo.kanjiDetail(literal) ?: return@launchIo
        trail.addLast(_screen.value)
        _screen.value = Screen.KanjiPage(detail)
    }

    fun goBack() = launchIo { returnTo(trail.removeLastOrNull()) }

    /** Show a screen from the trail again, reloading the ones whose progress may have changed meanwhile. */
    private fun returnTo(screen: Screen?) {
        when (screen) {
            null, is Screen.Home -> showHome()
            is Screen.WordPage -> showWord(screen.detail.word.id)
            is Screen.KanjiPage -> repo.kanjiDetail(screen.detail.kanji.literal)?.let { _screen.value = Screen.KanjiPage(it) }
            is Screen.StatsPage -> _screen.value = Screen.StatsPage(repo.stats(now()))
            is Screen.Review -> resumeReview(screen)
            else -> _screen.value = screen
        }
    }

    /** Back from a word's page to the card it was opened from, unless the word was reset or suspended there. */
    private fun resumeReview(state: Screen.Review) {
        val s = session ?: return showHome()
        if (repo.unchanged(state.current.card)) {
            _screen.value = state
            return
        }
        s.drop(state.current.card.id)
        undo = null
        advance()
    }

    fun resetWord() = wordAction { repo.reset(it) }

    fun setSuspended(suspended: Boolean) = wordAction { repo.setSuspended(it, suspended) }

    companion object {
        /** Search hits beyond the JLPT words to list, best first. */
        private const val MAX_OTHER_HITS = 200

        /** What's read when a grammar page plays a line from your stories: no story's id. */
        private const val STORY_LINE_KEY = -1L

        /** What's played for a kana's sound. */
        private const val KANA_KEY = -2L
    }
}
