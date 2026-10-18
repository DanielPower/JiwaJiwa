# Jiwa Jiwa

An Android app for learning Japanese by reading. Its graded stories bring in
new words and grammar a few at a time; the words you meet are reviewed
Anki-style (you grade yourself Wrong or Right), scheduled with FSRS, and lose
their furigana as each word's mastery grows.

Every push to `main` builds a release APK and publishes it on the
[Releases](../../releases) page.

## Layout

| Path | What it is |
| --- | --- |
| `app/` | The Android app (Kotlin, Jetpack Compose) |
| `core/` | Pure-Kotlin study logic: FSRS, the review session, mastery and furigana rules, and the story path (stories split into words at build time with Kuromoji, conjugation notes, grammar) |
| `tools/dictionary/` | Builds `app/src/main/assets/databases/dictionary.db` from pinned sources |
| `tools/stories/` | The bundled stories and grammar topics, as they're written (see its AGENTS.md) |
| `tools/fsrs/` | Generates the FSRS reference vectors the core tests replay |

## Building

The dictionary database isn't committed, so build it first:

```sh
python3 tools/dictionary/build_dictionary.py
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

Pushes to other branches run the same build in CI without publishing a release.
Every pull request and every push to `main` also runs the unit tests as
separate checks (`.github/workflows/test.yml`).

Without the Android SDK, the core can be built and tested on its own:

```sh
./gradlew -PcoreOnly=true :core:test
```

## Dictionary

```sh
python3 tools/dictionary/build_dictionary.py           # build the database and rewrite build_report.md
python3 tools/dictionary/build_dictionary.py --check   # confirm the build matches the digest in build_report.md
python3 -m unittest discover tools/dictionary
```

The database holds every JMdict entry (about 218,000) and every KANJIDIC2
kanji, about 85 MB in all. JLPT words have a level; the rest have level 0
(shown as "Other"). Every entry can turn up in a story or a search. Because
of its size the database is built in CI rather than committed;
`build_report.md` records its content digest.

Sources and checksums are pinned in `tools/dictionary/sources.json`. Hand
corrections go in `tools/dictionary/overrides.csv`. `build_report.md` lists
every spelling the build changed and anything it could not resolve.

Data comes from JMdict (EDRDG, CC BY-SA 4.0) via jmdict-simplified,
JmdictFurigana (CC BY-SA 4.0), Jonathan Waller's JLPT lists (CC BY) as linked
to JMdict by Stephen Kraus's yomitan-jlpt-vocab (CC BY-SA 4.0), Jiten's
frequency list (jiten.moe, CC BY-SA 4.0), KANJIDIC2 (EDRDG, CC BY-SA 4.0), JLPT
kanji levels via David Gouveia's kanji-data (MIT), and KanjiVG stroke order
(Ulrich Apel, CC BY-SA 3.0). The built dictionary database is an adaptation
of these and is licensed under CC BY-SA 4.0. A monthly workflow
(`update-dictionary.yml`) opens a PR pinning the newest JMdict, KANJIDIC2 and
JmdictFurigana releases, as the EDRDG licence requires regular updates.

## Release signing

Releases are signed with the key in the `SIGNING_KEYSTORE_BASE64` and
`SIGNING_PASSWORD` repository secrets (alias `jstudy`, with the same password
for the store and the key). To make a new key locally:

```sh
keytool -genkeypair -keystore release.jks -alias jstudy -keyalg RSA -keysize 4096 \
  -validity 36500 -dname "CN=jstudy"
base64 -w0 release.jks   # the value for SIGNING_KEYSTORE_BASE64
```

Without the secrets, releases are signed with a throwaway debug key and each
update needs a reinstall.

## Google Play

Every release also builds `jiwajiwa-<version>.aab`, the bundle Google Play takes,
and attaches it to the GitHub release. The first bundle has to be uploaded by
hand in Play Console. After that, setting the `PLAY_SERVICE_ACCOUNT_JSON`
repository secret (a Google Cloud service account key with release access in
Play Console) makes each release go to the internal testing track, with the
commit message as its release notes. Set the `PLAY_RELEASE_STATUS` repository
variable to `draft` while the app is still a draft in Play Console.

Google Play holds the app signing key. The key in the signing secrets is the
upload key, so APKs from GitHub releases can't update an install from Play,
or the other way round.

The privacy policy is [PRIVACY.md](PRIVACY.md).
