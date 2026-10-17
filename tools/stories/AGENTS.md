# tools/stories/AGENTS.md

The stories that come with the app, as they're written. The app reads
`app/src/main/assets/stories/path.json` (the text) and `needs.json` (each
chapter's words and grammar), both put together from here by the story
tools (core `src/stories`).

## Layout

| Path | What's in it |
| --- | --- |
| `stories.json` | Every story's id, in the order the Stories tab lists them: the main stories, each following the one before, then side stories and one-shots. Add a new story here. |
| `grammar.json` | The grammar topics, in Grammar-list order. Each has `parts`: Japanese lines with their English (`["来た。", "I'm here."]`), and notes in English (plain strings) between them. The first line is the example shown before the chapter that teaches it. Chapters name topics by `id`. |
| `series/<id>/story.json` | A story's `title`, `blurb`, the stories it follows (`after`: story ids, or a chapter id to mean the story up to that chapter) and `side` for a side story. |
| `series/<id>/01.json`, `02.json`, … | One chapter each: `title`, a one-line `plot`, optional `topic` (one grammar topic it teaches) and `meets` (people it introduces, `{"name", "about"}`), and `paragraphs`, each a list of `[Japanese sentence, English]`, or a note (`{"note": "English…"}`) between two of them. The number is part of the chapter's id, which saved progress uses: never renumber, reorder or delete a chapter people may have read. |
| `series/<id>/SUMMARY.md` | Written by the tools, never by hand: the cast so far, each chapter's plot, words and new words, and every word and grammar topic known by the end. |

## Conventions

- Titles are English, with Japanese where it fits.
- Speech is casual from the start: Alex and his friends talk the way friends
  do. Staff and strangers are polite, and です/ます come in as their own topic.
- A quote from one of the cast starts with their name and a bar,
  `ユキ|「でしょ？」`, and is shown with the name before it, script style; it
  goes on a line of its own. Unnamed staff and strangers, and thinking
  aloud, have no name.
- English quotes are escaped in JSON: `"\"Morning!\""`.
- Notes are rare: an aid for the odd spot where the reader needs cultural
  context the dialogue doesn't make clear, or an exception in the language
  explained. Most chapters have none. A note goes after the paragraph it's
  about (never first), and shows as a 注 marker at that paragraph's end,
  opening the note in the reader's pane. Notes aren't read for words.

## Writing a chapter

To continue a story you don't need the others: read its `SUMMARY.md`, its
last chapter or two, and the conventions above. The summary's "Known at
the end" is what a reader has by then. Use those words freely and bring
back ones met only once or twice.

A chapter's new words must fit its allowance (3% of its running words, from
3 to 10), it may teach one grammar topic (and should usually teach none,
see the grammar pace in the root AGENTS.md), and every word should appear in
at least two chapters. Side stories have nothing new at all.

Then run, with the dictionary built (`python3 tools/dictionary/build_dictionary.py`):

    ./gradlew -PcoreOnly=true :core:stories :core:test

`:core:stories` puts path.json together, reads every chapter with the app's
reader and dictionary lookup, and writes `needs.json` and each `SUMMARY.md`.
`StoryPathTest` checks the rules above, that the solver reaches every
chapter, and that path.json, needs.json and the summaries are up to date;
its report (`core/build/reports/story-path.txt`) lists each chapter's new
words. Commit all of them with the chapter.
