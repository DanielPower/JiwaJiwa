# tools/stories/AGENTS.md

The stories that come with the app, as source. The app reads
`app/src/main/assets/stories/path.json` (the text) and `needs.json` (each
chapter's words and grammar), both written from here.

## Layout

| Path | What's in it |
| --- | --- |
| `build.py` | Writes path.json. `STORIES` lists every story in the order the Stories tab shows them; add a new one there. |
| `story.py` | The helpers chapter files use (`chapter`, `series`, `topic`), and the writing conventions. |
| `grammar.py` | The grammar topics, in Grammar-list order. Chapters name them by id. |
| `series/<id>/story.py` | A story's title, blurb, the stories it follows (`after`) and whether it's a side story. |
| `series/<id>/01.py`, `02.py`, … | One chapter each. The file starts with a one-line plot in a docstring. The number is part of the chapter's id, which saved progress uses: never renumber, reorder or delete a chapter people may have read. |
| `series/<id>/SUMMARY.md` | Written by the tools, never by hand: the cast so far, each chapter's plot, words and new words, and every word and grammar topic known by the end. |

## Writing a chapter

To continue a story you don't need the others: read its `SUMMARY.md`, its
last chapter or two, and `story.py` for the conventions. The summary's
"Known at the end" is what a reader has by then. Use those words freely and
bring back ones met only once or twice.

A chapter's new words must fit its allowance (3% of its running words, from
3 to 10), it may teach one grammar topic (and should usually teach none,
see the grammar pace in the root AGENTS.md), and every word should appear in
at least two chapters. Side stories have nothing new at all.

Then run, with the dictionary built (`python3 tools/dictionary/build_dictionary.py`):

    ./gradlew -PcoreOnly=true :core:stories :core:test

`:core:stories` runs `build.py`, then the story tools (core `src/stories`)
read every chapter with the app's reader and dictionary lookup and write
`needs.json` and each `SUMMARY.md`. `StoryPathTest` checks the rules above,
that the solver reaches every chapter, and that path.json, needs.json and
the summaries are up to date; its report (`core/build/reports/story-path.txt`)
lists each chapter's new words. Commit all of them with the chapter.
