# tools/dictionary/AGENTS.md

Builds `app/src/main/assets/databases/dictionary.db` from pinned upstream
sources, using only the Python standard library.

```sh
python3 tools/dictionary/build_dictionary.py          # build, rewrite build_report.md
python3 tools/dictionary/build_dictionary.py --check  # rebuild, compare with the recorded digest
python3 -m unittest discover tools/dictionary
```

- Sources and checksums are pinned in `sources.json`; downloads are cached in
  `.cache/` (gitignored, cached in CI).
- The DB is not committed (it's about 85 MB). `build_report.md` is, and
  records the content digest plus every spelling the build changed and
  anything it couldn't resolve. CI fails if a build changes the report, so
  commit the regenerated report with any change to the build or sources.
- Hand corrections go in `overrides.csv`, not in code. A `jlpt` row also
  puts a word the lists leave out on them (母, which they only have as
  お母さん).
- `SCHEMA_VERSION` must be bumped when the table layout changes, and the
  app's queries in `app/.../data/Dictionary.kt` updated to match.
- Sources: JMdict and KANJIDIC2 via jmdict-simplified, JmdictFurigana,
  Jonathan Waller's JLPT lists as linked to JMdict by yomitan-jlpt-vocab,
  JLPT kanji levels from kanji-data, KanjiVG stroke paths, and Jiten's global
  frequency list. Jiten's download isn't versioned, so a snapshot is committed
  in `data/` (xz-compressed CSV, `path` instead of `url` in sources.json).
  Its readings are compared in hiragana, because Jiten writes some in
  katakana. English glosses only. Every JMdict entry is included; non-JLPT
  words get level 0 and no study order.
- Every source must allow redistribution in the Play Store app, and the
  database is CC BY-SA 4.0, so new sources need a compatible licence. The app
  shows each source's `description`, `license` and `homepage` in Settings.
  The EDRDG licence requires regular JMdict updates: `update_sources.py` pins
  the newest JMdict, KANJIDIC2 and JmdictFurigana releases, and
  `.github/workflows/update-dictionary.yml` runs it monthly and opens a PR.
