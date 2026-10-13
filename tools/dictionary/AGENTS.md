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
- Hand corrections go in `overrides.csv`, not in code.
- `SCHEMA_VERSION` must be bumped when the table layout changes, and the
  app's queries in `app/.../data/Dictionary.kt` updated to match.
- Sources: JMdict and KANJIDIC2 via jmdict-simplified, JmdictFurigana,
  Jonathan Waller's JLPT lists as linked to JMdict by yomitan-jlpt-vocab,
  JLPT kanji levels from kanji-data, KanjiVG stroke paths, and the JPDB
  frequency list (used instead of BCCWJ, whose site is unreachable from CI
  containers). English glosses only. Every JMdict entry is included; non-JLPT
  words get level 0 and no study order.
- Quality wins over licensing convenience here. The JPDB list's licence is an
  open question for a Play Store release.
