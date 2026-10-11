# jstudy

An Android app for learning Japanese vocabulary. It ships with every word on
the JLPT N5 to N1 lists, reviews them Anki-style (you grade yourself Wrong or
Right), schedules them with FSRS, and fades out furigana as each word's
mastery grows.

Every push to `main` builds a release APK and publishes it on the
[Releases](../../releases) page.

## Layout

| Path | What it is |
| --- | --- |
| `app/` | The Android app (Kotlin, Jetpack Compose) |
| `core/` | Pure-Kotlin study logic: FSRS, the daily queue, mastery and furigana rules |
| `tools/dictionary/` | Builds `app/src/main/assets/databases/dictionary.db` from pinned sources |
| `tools/fsrs/` | Generates the FSRS reference vectors the core tests replay |

## Building

```sh
./gradlew :core:test :app:assembleDebug
```

Without the Android SDK, the core can be built and tested on its own:

```sh
./gradlew -PcoreOnly=true :core:test
```

## Dictionary

```sh
python3 tools/dictionary/build_dictionary.py           # rebuild the database and build_report.md
python3 tools/dictionary/build_dictionary.py --check   # confirm the committed database is current
python3 -m unittest discover tools/dictionary
```

Sources and checksums are pinned in `tools/dictionary/sources.json`. Hand
corrections go in `tools/dictionary/overrides.csv`. `build_report.md` lists
every spelling the build changed and anything it could not resolve.

Data comes from JMdict (EDRDG, CC BY-SA 4.0) via jmdict-simplified,
JmdictFurigana (CC BY-SA 4.0), Jonathan Waller's JLPT lists (CC BY) as linked
to JMdict by Stephen Kraus's yomitan-jlpt-vocab (CC BY-SA 4.0), and the JPDB
frequency list compiled by Kuuuube.

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
