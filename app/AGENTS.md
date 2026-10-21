# app/AGENTS.md

The Android app: Kotlin, Jetpack Compose, minSdk 26, target/compile SDK 36.
It can't be built in cloud agent containers (Google's Maven hosts are
blocked there), so CI is the build check. Keep logic in `core` where you can.

## Structure

| Path | What's in it |
| --- | --- |
| `MainActivity.kt` | Entry point; theme and the screen switch. |
| `ui/AppViewModel.kt` | The single view model. Navigation is a `Screen` sealed interface held in state, not a nav library. |
| `ui/*Screen.kt`, `ui/Screens.kt` | Compose screens: home, review, done, dictionary and word page, settings, setup, stats, stories, Renshuu import. |
| `ui/Washi.kt`, `ui/Theme.kt` | The Washi design system (see below). |
| `ui/KanjiScreen.kt` | The kanji page, stroke order drawn from KanjiVG paths, and the kanji links used on word pages and cards. |
| `ui/VerticalStory.kt` | Custom vertical (tategaki) layout; Compose has no vertical writing mode. Line breaking lives in core `VerticalText`. |
| `data/Repository.kt` | Everything the UI asks for. Owns settings, sessions, undo, backup, reset, stories, stats. |
| `data/ProgressStore.kt` | The user's SQLite DB (`progress.db`): cards, word progress, review log, a `kv` settings table, stories. |
| `data/Dictionary.kt` | The read-only dictionary DB, copied from assets on install or update. |
| `data/RenshuuClient.kt` | Renshuu API client (`/list/all/vocab`, 50 per page). |
| `audio/StoryVoice.kt` | Reads a story a line at a time: each line's recording (assets `audio/`, core `Recordings`) with ExoPlayer, or where it has none, the phone's text-to-speech in the speaker's voice (core `Voices`). The lines are core `Narration`. |
| `reminder/DailyReminder.kt` | Inexact daily alarm and notification. |

## Storage

- Plain `SQLiteOpenHelper`, not Room (avoids KSP and prepackaged-DB schema
  validation). Settings live in the progress DB's `kv` table, not DataStore,
  so a backup carries them.
- `ProgressStore.VERSION` is 13 (11 dropped old settings, 12 added the
  `kana` and `kana_log` tables, 13 cleared more old settings). The `story`
  table's `read_ms` is no longer written but stays, since Android 8's SQLite
  can't drop a column. Schema changes need an `onUpgrade` step, and
  backups from older versions must still restore (they're upgraded when
  reopened). A backup from a newer version is refused.
- SharedPreferences hold only what must not go in a backup or must load
  before the DB: `display` (theme, read on
  the first frame) and `dictionary` (asset copy bookkeeping).
- The dictionary DB (`assets/databases/dictionary.db`, about 85 MB, all of
  JMdict) is built by `tools/dictionary` and isn't committed. JLPT words
  have level 1–5 and a `study_order`; others have level 0 and no order.
  `levelLabel` shows them as "Other".

## Design: Washi

Warm unbleached paper `#F6ECDA`, brown-black ink `#2A1F17`, seal red
`#B5381C`; dark mode is a tea-brown `#1E1711` with cream text `#F1E5CF` and
red `#E8704F`. Keep every text colour at 4.5:1 or better on its background. No dynamic colour. Zen Kaku Gothic New (sans serif) is the font
for Japanese, headings and UI. The story reader also offers Zen Old Mincho
(serif) and Zen Maru Gothic (rounded) for story text only, in their regular
weight alone (a note's bold 注 is in the gothic); all three are OFL,
with licences in `assets/licenses`. Use the shared components
in `ui/Washi.kt` (`PrimaryButton`, `SealButton`, `SecondaryButton`,
`WashiChip`, `LevelSeal`, bottom nav, line icons) for new screens rather than
stock Material ones. New/learning/review counts use 新/習/復.

Colour roles (Daniel, 2026-10-21): one job per colour, so a new thing takes
the colour of what it means.

| Colour | Where it comes from | Means |
| --- | --- | --- |
| Vermilion | `primary` | The main action, the selected thing or "this one"; a word you haven't started (and 新); stamps, seals and the streak |
| Indigo | `secondary` | Quieter links and actions; grammar, conjugation included |
| Ochre | `tertiary` | Seen (mastery under 40) or still learning (習, kana you're learning) |
| Verdigris | `MaterialTheme.washi.verdigris` | Known (40 to 69) or due for review (復, Coming up, the Stats forecast) |
| Pine | `MaterialTheme.washi.pine` | Mastered (70 and up), Right, finished |
| Plum | `MaterialTheme.washi.plum` | Notes only (注) |
| Crimson | `error` | Wrong, errors, anything that deletes progress |
| Ink | `onSurface`, `onSurfaceVariant` | Everything that only needs reading: labels, numerals, Cancel, warnings |

In the reader, a tapped word keeps its own colour and the tint marks it,
romaji takes its word's colour, and the line under the word being read
aloud is ink. Review cards have a topper, indigo for Japanese → English
and vermilion for English → Japanese, with the card's 新/習/復 stage.
Mastery is coloured by stage everywhere (`masteryColor`), the review
card's bar too, with no extra labels or legend. Right and Wrong never
rest on colour alone: they carry ○ and ×, or their names.

## UI text

UI text is plain English strings. Design marks such as the 新/習/復
counters and the 済/読了 seals stay Japanese.

## Tests

`src/test/kotlin`, JUnit 4. Tests that need Android (SQLite, `Context`) use
`@RunWith(RobolectricTestRunner::class)`; Robolectric is pinned to SDK 34 in
`src/test/resources/robolectric.properties`. `ProgressStoreTest` covers the
DB layer including its migrations (3 → 4, 7 → 8, 8 → 9, 9 → 10, 10 → 11, 11 → 12, 12 → 13); add a migration test with
any schema change. Run with `./gradlew :app:testDebugUnitTest` (CI only in
cloud containers).

## Debug builds

The debug build type has app id `com.crispyumbrella.jiwajiwa.debug`, so a PR's APK
installs next to the real app without touching its progress. Its name is "Jiwa Jiwa
Debug" (`app/src/debug/res/values/strings.xml`) so the two can be told apart.
