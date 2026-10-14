# AGENTS.md

Notes for coding agents working on jstudy. Read this first, then the
AGENTS.md in the module you're changing (`core/`, `app/`, `tools/dictionary/`).

## What this is

jstudy is Daniel's Android app for learning Japanese vocabulary, JLPT N5 to
N1. It's Anki-style (you grade yourself Wrong or Right, FSRS schedules the
card) with a friendlier, Renshuu-like feel. It's built for Daniel's own use
first; publishing on Google Play may come later.

Guiding rules from Daniel:

- **Personal use first.** Never trade quality for publishability (for
  example, the dictionary keeps the best community sources even where their
  licences are awkward). Groundwork that makes publishing easier later is
  welcome.
- **Go ahead on best guesses.** Pick a reasonable default, say which, and
  keep going. Save questions for the end.
- Don't re-open the decisions below without a reason; they were made on
  purpose.

## Layout

| Path | What it is |
| --- | --- |
| `core/` | Pure-Kotlin (JVM) study logic: FSRS, cards and mastery, the daily queue, kana course, kanji readings, romaji, story budgets, Kuromoji word splitting, the story server client (Claude conversations, sign-in), Renshuu import planning. No Android imports. |
| `app/` | The Android app: Jetpack Compose UI, SQLite storage, reminder, backup. |
| `tools/dictionary/` | Python (stdlib only) build of the bundled dictionary DB from pinned sources. |
| `tools/fsrs/` | Generates `core/src/test/resources/fsrs_vectors.tsv` from py-fsrs. |
| `server/` | The story server (Python, FastAPI, SQLite, Docker Compose): Google sign-in, per-account rate limits, and the Claude API key, so the app never holds one. See `server/README.md`. |
| `.github/workflows/` | CI (see below). |

Put logic in `core` whenever it doesn't need Android, so it can be tested
anywhere. `app` should mostly be wiring, storage and UI.

## Building and testing

```sh
./gradlew -PcoreOnly=true :core:test      # core only, no Android SDK needed
./gradlew :app:testDebugUnitTest          # app JVM tests (Robolectric), needs the SDK
python3 -m unittest discover tools/dictionary
python3 tools/dictionary/build_dictionary.py   # needed before assembling an APK
./gradlew :app:assembleDebug
```

- Cloud agent containers usually can't reach `dl.google.com` or
  `maven.google.com`, so the Android app can't be built or tested there. Run
  the core tests locally and let CI build the app. `-PcoreOnly=true` drops
  `:app` from `settings.gradle.kts` for this.
- Maven Central sometimes answers 429 from those containers; retry, with
  `--max-workers=1` if it keeps happening.
- Tests use JUnit 5 + `kotlin.test` in core, JUnit 4 + Robolectric in app
  (pinned to SDK 34 in `app/src/test/resources/robolectric.properties`,
  because Robolectric needs Java 21 for SDK 35 and CI uses Java 17).
- Core test names are backtick sentences describing behaviour; follow that.

## CI

| Workflow | When | What |
| --- | --- | --- |
| `test.yml` (Tests) | every PR, every push to main | core tests, app unit tests, dictionary tests, as separate checks |
| `check.yml` (Check pull request) | every PR | builds the dictionary and the release + debug APKs; attaches the debug APK (app id `com.crispyumbrella.jiwajiwa.debug`, installs next to the real app as "Jiwa Jiwa Debug") and comments on the PR with a link to it |
| `build.yml` | pushes to branches other than main | test and build without releasing |
| `release.yml` | every push to main | tests, builds, signs and publishes a GitHub release `v0.1.<run number>` |
| `server.yml` (Story server) | PRs and pushes to main touching `server/` | server tests and image build; on main, pushes `ghcr.io/danielpower/jiwajiwa-story-server` (`latest` and `sha-…`) |
| `dictionary.yml` | pushes touching `tools/dictionary/` | rebuilds and checks the digest |

Every push to main ships a release, so main must always be releasable.
Release signing comes from repository secrets (see the README); never put
keys, passwords or API keys in the repo. Daniel may open-source it later.

## Working in the repo

- Several agents work here at once. Fetch and rebase on `origin/main` before
  each push.
- Open a pull request for feature work rather than pushing to main. Daniel
  sometimes okays a direct push for a small tweak; ask or follow his word.
- Commit messages are a short imperative summary of what the user sees
  ("Teach hiragana and katakana before the words that need them").
- Comments explain why, briefly, in plain sentences. Match the surrounding
  density; don't narrate code.
- User-facing text is plain and friendly, sentence case, no jargon ("Right" /
  "Wrong", not "Good" / "Again").

## Product decisions (don't re-ask)

Study and scheduling:

- Two grade buttons only: **Wrong** (red) and **Right** (green), mapped to
  FSRS Again and Good. FSRS-6, 90% target retention.
- Study days roll over at 4am local time (`StudyClock`).
- **Mastery** (0–100) is derived from the recognition card's FSRS stability:
  7 days = 40, 30 days = 70, 90 days = 100 (`MasteryRules.fromStability`).
  It replaced an older +10/−30 points score (progress DB version 4 migrates).
- Furigana: shown below mastery 40, hidden with a tap-to-show from 40 to 69,
  off from 70. The answer side always shows it.
- Recognition (Japanese → English) comes first. The production card
  (English → Japanese) unlocks when mastery reaches 40. Production reviews
  schedule their own card but don't change mastery.
- Target retention and the mastery thresholds (furigana and production)
  are fixed constants (`MasteryRules`), not settings, so users aren't
  overwhelmed with choices. Don't add them back unless Daniel asks.
- New words go N5 → N1, by frequency within a level. Within a sitting,
  reviews and the day's new words are shuffled; new kana keep their order. A new word's first
  showing offers Continue (main action) or a quiet "I know this".
- "Mark known" lets you set the word's mastery; it gets a review schedule to
  match, first due on a random day in that interval.
- "Study more" pulls every schedule one day earlier (a `daysAhead` counter)
  and adds another day's worth of new cards for today.
- v1 card back is minimal: word, reading, English meanings. No example
  sentences, TTS or pitch accent yet.

Kana:

- Taught as flashcards, introduced by the story path as stories need them
  (see Story path below). No quiz or lesson screen.
- A new word only enters once every kana in its kana spelling has a kana card
  in review. There's no override.
- No romaji on vocabulary cards.

Other features (all shipped):

- Dictionary search and word pages; non-JLPT words show as "Other".
- Kanji pages, not kanji cards (Daniel, 2026-10-13): meanings, on and kun
  readings (the ones your words use in red), animated stroke order
  (KanjiVG), JLPT level, your words written with it and more words to
  learn. Linked from the word page, the card's answer side and dictionary
  search. Kanji have no mastery or stats of their own; Daniel saw no use
  for them without kanji cards.
- First-run setup: start level, kana known, optional reminder time. Both
  can change later in Settings: marking a script known sets its started
  kana cards aside (suspended) until you change it back, and moving the
  start level past N5 asks, as setup does, whether unstarted easier words
  wait or are marked Known or Mastered.
- Daily reminder has **no default time**; the user picks one. Don't request
  notification permission until they turn it on.
- Manual backup/restore only (`jiwajiwa-<date>.jstudy`, a copy of the progress
  DB). No automatic backups. The story sign-in isn't included.
- Settings has a reset button that erases all user data.
- Stats page (heatmap, recall, forecast, per-level progress) is a link on the
  home screen, not a tab.
- Renshuu import: takes the higher of the two masteries per word, skips
  suspended words, never stores the Renshuu key. Renshuu's `edict_ent` is
  the JMdict id, which is jstudy's word id. Renshuu's kana entries have no
  `edict_ent`; they're matched by text to kana cards. Its other entries
  without one (する verbs, phrases, names) are skipped on purpose.
- AI graded-reader stories: the user signs in with Google (Credential
  Manager) and the app sends its Claude conversations to the story server
  (`server/`, https://api.jiwajiwa.app), which holds the API key, picks the
  model and limits each account (Daniel, 2026-10-14). There's no API key
  setting. The server URL and the Google web client ID are the
  `storyServerUrl` and `googleWebClientId` Gradle properties. Short/Medium/Long, Easy/Normal/Hard sets the share of known
  words; unknown words beyond the budget trigger a rewrite. Tapping a word
  shows its meaning (and conjugation, when conjugated), plus any grammar
  point it's part of (`core/Grammar.kt`); particles and endings are tappable
  for their grammar alone, and title words too. The meaning opens in a
  pane fixed at the top of the reader, always visible with the story below
  it so it never covers the text (as in Yomu Yomu), not a popover. Long-pressing a word shows its sentence's English,
  written by Claude with the story (one extra request per story). Story
  words are never queued automatically: an unknown word's card offers
  "Add to reviews" (front of the new-word queue) as the main action and a
  quieter "I know this". Stories show a page at a time, swiping between
  pages (no scrolling); horizontal pages are top-aligned, vertical pages
  are centred and turn right to left. After the last page comes an end page:
  reaching it marks the story read (timed from opening it), shows the
  read's stats (characters, share of words known, time, characters a
  minute) and lists its new words with Add and "Add all to reviews". It
  also offers "Write a sequel": Claude gets every part so far and the same
  length and difficulty, the sequel keeps the first part's title, and parts
  show as "Title: Part n" (the first becomes Part 1 when its sequel is
  saved). Once a sequel exists the button reads "Read the next part". A gear at the top right of the
  reader opens its settings in the pane: text size (four steps), font (Zen
  Kaku Gothic New by default, Zen Old Mincho serif, Zen Maru Gothic rounded)
  and horizontal or vertical (tategaki) text. They live only there, not in
  Settings, and fit the pane without scrolling.
  Both layouts follow Japanese line-break rules (`core/Kinsoku.kt`,
  `VerticalText.kt`, `HorizontalText.kt`).
- The app's own text turns Japanese as you learn: each label shows in
  English until every word in its Japanese is mastered (the furigana-off
  mastery, 70), then in Japanese. Labels are written as
  `ui("English", "日本語")`; the words are found by reading the Japanese with
  the story reader, so there's no word list to maintain. Explanations and
  text with numbers in it stay English for now.
- Visual design is "Washi" (paper, ink, vermilion seal) with light, dark and
  auto themes. See `app/AGENTS.md`.

The app is named **Jiwa Jiwa** (app id `com.crispyumbrella.jiwajiwa`);
"jstudy" survives only in the repo, code packages and backup file extension.
Google Play holds the app signing key and the CI key is the upload key; see
`PRIVACY.md` and "Google Play" in the README. The first Play release goes to internal
testing.

Every bundled data source allows redistribution: word frequency comes from
Jiten (CC BY-SA 4.0), not JPDB, which has no licence. The dictionary database
is CC BY-SA 4.0, and the EDRDG licence requires regular JMdict updates
(`.github/workflows/update-dictionary.yml`). New data sources must have a
licence that allows this.

Story path (an experiment on its own branch, Daniel, 2026-10-14):

- New words, kana and grammar come from the path instead of reviews.
  Reviews and the home count no longer introduce words or kana; only
  unlocked production cards are new there.
- The path (`app/src/main/assets/stories/path.json`, written by
  `tools/stories/path.py`) is
  44 stories and 10 lessons, from one sentence up to roughly 100 N5 words.
  They're saved in the `story` table with a `path_id`, can't be deleted, and
  need no sign-in. The list names a story by its English title and a lesson
  by its Japanese one (the grammar it teaches).
- A step shows on the Stories tab once it's within reach (`StoryPath.gap`):
  new words at most 3% of its running words, from 3 to 10; at most 6 new
  kana (furigana counts, and kana you said you read never do); and no
  grammar point you haven't seen. Hiragana and katakana are separate units.
- Lessons are the only way grammar comes in. A lesson is a path entry with
  `teaches` (grammar point ids) and English `notes` between its Japanese
  lines. It opens in the reader as one scrolling page, with the same tap and
  hold pane. "Done" or the quiet "I know this already" marks its points
  seen (a list in the `grammar_seen` value, no cards). Stories have no
  grammar allowance.
- `StoryPathTest` checks the path against the built dictionary (skipped when
  it isn't built): read in order every step is within reach, and
  `StoryPath.solve` reaches every step from knowing nothing, kana included.
  Readiness only grows as you learn, so that also means no reading order can
  get stuck. Rerun it after editing a story or lesson.
- Words: **seen** (has a card, mastery below 40), **known** (40, where
  furigana hides), **mastered** (70).
- Opening a step with new kana or words lists them first; "Learn these"
  shows each as a new card once (kana first), then it opens. "I know these
  already" marks them known.
- At the end of a path story or lesson, each word due for review that you
  read without tapping counts as an FSRS Hard review (`Reviewer.read`): less
  than a Right answer, since context helps. The end page shows how many
  words were seen, moved to known and moved to mastered, and for a lesson,
  the grammar it opened up.

Not decided or not built yet: example sentences, audio.
