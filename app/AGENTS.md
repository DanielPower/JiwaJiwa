# app/AGENTS.md

The Android app: Kotlin, Jetpack Compose, minSdk 26, target/compile SDK 35.
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
| `reminder/DailyReminder.kt` | Inexact daily alarm and notification. |

## Storage

- Plain `SQLiteOpenHelper`, not Room (avoids KSP and prepackaged-DB schema
  validation). Settings live in the progress DB's `kv` table, not DataStore,
  so a backup carries them.
- `ProgressStore.VERSION` is 6. Schema changes need an `onUpgrade` step, and
  backups from older versions must still restore (they're upgraded when
  reopened). A backup from a newer version is refused.
- SharedPreferences hold only what must not go in a backup or must load
  before the DB: `secrets` (the Claude API key), `display` (theme, read on
  the first frame), `dictionary` (asset copy bookkeeping).
- The dictionary DB (`assets/databases/dictionary.db`, about 85 MB, all of
  JMdict) is built by `tools/dictionary` and isn't committed. JLPT words
  have level 1–5 and a `study_order`; others have level 0 and no order.
  `levelLabel` shows them as "Other".

## Design: Washi

Warm unbleached paper `#F6ECDA`, brown-black ink `#2A1F17`, seal red
`#B5381C`; dark mode is a tea-brown `#1E1711` with cream text `#F1E5CF` and
red `#E8704F`. Keep every text colour at 4.5:1 or better on its background. No dynamic colour. Zen Kaku Gothic New (sans serif) is the font
for Japanese, headings and UI. The story reader also offers Zen Old Mincho
(serif) and Zen Maru Gothic (rounded) for story text only; all three are OFL,
with licences in `assets/licenses`. Use the shared components
in `ui/Washi.kt` (`PrimaryButton`, `SealButton`, `SecondaryButton`,
`WashiChip`, `LevelSeal`, bottom nav, line icons) for new screens rather than
stock Material ones. New/learning/review counts use 新/習/復.

Colour cues (Daniel, 2026-10-13): review cards have a topper, indigo for
Japanese → English and vermilion for English → Japanese, with the card's
新/習/復 stage. Mastery outside the review page is coloured by furigana stage
(`masteryColor`: gold below 40, indigo to 69, green from 70), with no extra
labels or legend. The review page's mastery bar stays ink.

## UI text

Write UI labels through `ui("English", "日本語")` (`ui/Labels.kt`), or a
`UiLabel` for text kept outside a composable (enums, lists). A label shows in
Japanese once you've mastered every word in it. Its words are found by
reading the Japanese the first time it's shown, off the main thread, and
kept in the `labels` SharedPreferences (cleared when the app updates) so
later launches start in the right language. Pass the dictionary spellings
after the Japanese (`ui("Vertical", "縦書き", "縦", "書く")`) only where the
automatic split goes wrong or picks a word outside the JLPT lists; a label
with a word or loanword the dictionary lacks stays English. Prefer Japanese
built from JLPT words, which you'll actually study. Explanatory sentences
and text with numbers stay plain English.

## Tests

`src/test/kotlin`, JUnit 4. Tests that need Android (SQLite, `Context`) use
`@RunWith(RobolectricTestRunner::class)`; Robolectric is pinned to SDK 34 in
`src/test/resources/robolectric.properties`. `ProgressStoreTest` covers the
DB layer including the version 3 → 4 migration; add a migration test with
any schema change. Run with `./gradlew :app:testDebugUnitTest` (CI only in
cloud containers).

## Debug builds

The debug build type has app id `com.crispyumbrella.jiwajiwa.debug`, so a PR's APK
installs next to the real app without touching its progress.
