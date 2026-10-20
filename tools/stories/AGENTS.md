# tools/stories/AGENTS.md

The stories that come with the app, as they're written. The app reads
`stories/path.json` (the text), `needs.json` (each chapter's words and
grammar), `stories/read/` (every chapter split into words, which the
reader shows) and `lines.json` (where each grammar topic is used, for the
lessons' "From your stories"), all put together from here by the story tools (core
`src/stories`) when the app is built; none of them is committed. The app
has no tokenizer of its own: what the check passes is exactly what it shows.

## Layout

| Path | What's in it |
| --- | --- |
| `stories.json` | Every story's id, in the order the Stories tab lists them: the main stories, each following the one before, then side stories and one-shots. Add a new story here. |
| `grammar.json` | The grammar topics (lessons), in Grammar-list order: `id`, `title` (the pattern), `title_en`, `points` (from core `Grammar.kt`), a `summary`, `parts` (Japanese lines with their English, `["来た。", "I'm here."]`, and notes in English, plain strings, between them), and optionally `forms` and `more`. See "Writing a grammar lesson". Chapters name topics by `id`. |
| `series/<id>/story.json` | A story's `title`, `blurb`, the stories it follows (`after`: story ids, or a chapter id such as `share-house/yukis-gig` to mean the story up to that chapter), `side` for a side story, an optional `narrator` (a Fish Audio voice id, when its narration shouldn't be in the usual narrator's voice), and `chapters`: its chapters' slugs in reading order. |
| `levels.json` | Written by the recorder: each Fish voice's gain in dB, so they all play as loud. |
| `voices.json` | The Fish Audio voices the stories are recorded in: `model`, the usual `narrator`, each of the `cast` by name, and `labels`, a voice for each kind (`woman`, `man`, `child`) for unnamed speakers. A blank id isn't recorded; the phone reads those lines. Someone new to the cast gets an entry here. Changing a voice records all of its lines again, which costs money. |
| `series/<id>/<slug>.json` | One chapter each, named by its slug (lowercase words joined by hyphens, usually from its title: `trash-day.json`): `title`, a one-line `plot`, optional `topic` (one grammar topic it teaches), `meets` (people it introduces, `{"name", "about", "voice", "pitch"}`: `voice` is `woman`, `man` or `child`, and the optional `pitch` (about 0.85 to 1.15) sets them apart from others of their kind, since phones have only a few Japanese voices; the same person keeps the same voice wherever they're met) and `readings` (see below), and `paragraphs`, each a list of `[Japanese sentence, English]`, or a note (`{"note": "English…"}`) between two of them. Its id is the story's id and its slug (`share-house/trash-day`), which saved progress uses. To add or move a chapter, add its file and put its slug where it goes in `chapters`; never rename a slug or delete a chapter people may have read. |
| `series/<id>/SUMMARY.md` | Written by the tools, never by hand: the cast so far, each chapter's plot, words and new words, and every word and grammar topic known by the end. |
| `PLAN.md` | The approved plan for the stories being written next: grammar topics in order, and each story's cast and outline. |
| `COVERAGE.md` | Written by the tools: how many of the most common words the stories use, the words left (most common first), and the grammar points left. |

## Conventions

- Titles are English, with Japanese where it fits.
- Speech is casual from the start: Alex and his friends talk the way friends
  do. Staff and strangers are polite, and です/ます come in as their own topic.
- A quote from one of the cast starts with their name and a bar,
  `ユキ|「でしょ？」`, and is shown with the name before it, script style; it
  goes on a line of its own. Someone in the cast speaks only from the
  chapter that introduces them (`meets`) on. Staff and strangers can go
  unnamed, or take a description as their label (`店員|「…」`,
  `男の人|「…」`), which may be used anywhere. Thinking aloud has no name.
  A cast name written in katakana is always read as a name, even where the
  dictionary has a word spelled the same (リン is the guitarist, not 燐).
- English quotes are escaped in JSON: `"\"Morning!\""`.
- When the reader picks the wrong word, pin it in the chapter's `readings`:
  the word as the text writes it, and its reading (`"readings": {"方":
  "ほう"}`). A pinned dictionary form pins its conjugations too. Few
  chapters need one: the reader already reads 何時 なんじ, 描く かく, 弦
  げん, 弾く ひく and 日本 にほん everywhere (`DEFAULT_READINGS` in the
  reader), and あの方 as かた. Check the new words the chapter check lists:
  a wrong reading there means a wrong card. It also lists the kanji words
  off the JLPT lists, the likeliest to be read the wrong way. The check
  fails if a pin isn't needed, or doesn't take.
- Notes are rare: an aid for the odd spot where the reader needs cultural
  context the dialogue doesn't make clear, or an exception in the language
  explained. Most chapters have none. A note goes after the paragraph it's
  about (never first), and shows as a 注 marker at that paragraph's end,
  opening the note in the reader's pane. Notes aren't read for words.

## Writing a chapter

To continue a story you don't need the others: read its `SUMMARY.md`, its
last chapter or two, and the conventions above. The summary's "Known at
the end" is what a reader has by then. Use those words freely and bring
back ones met only once or twice. Pick new words from `COVERAGE.md`, most
common first: the stories teach the words used most in casual Japanese,
whatever their JLPT level, and any word in the dictionary can be used.

Chapters grow as the reader's words do: the allowance grows with length,
and the next few hundred common words need chapters of 150 to 300 words.
A short chapter now and then is fine, but one under half as long as the
chapters before it fails the check.

A chapter's new words must fit its allowance (3% of its running words, and at
least 3), it may teach one grammar topic (and should usually teach none,
see the grammar pace in the root AGENTS.md), and every word should appear in
at least two chapters. Side stories have nothing new at all.

Every particle, ending and changed verb or adjective in a chapter (and on a
grammar page) must belong to a grammar point in `core/Grammar.kt`, or the
check fails with "no grammar point explains …". If the point exists, its
topic must be taught by then; if none does, add the point (with an example
`GrammarTest` checks) and a topic for it in `grammar.json`, or reword the
sentence. If the new point explains a conjugation label, add it to that
label's row in `LABEL_POINTS` too (see core/AGENTS.md). A changed form no point explains but the dictionary lists as one
entry (いらっしゃいませ) is read as that word instead. でも, けど, だけど and
だって opening a sentence are words, not grammar.

The reader reads ～られる on a る verb (食べられる, 来られる) and spoken
食べれる, 来れる as "can" (the `potential` point), and ～れる / ～られる on a
う verb or する as the passive. For a passive on a る verb, reword.

Write naturally: the reader handles everyday speech the tokenizer gets wrong
(the repairs in `Tokens.kt`, each with a case in `ReaderTrapsTest`). If it
misreads something new, fix the reader and add the case rather than
rewording around it. What's left, by design rather than misreading: ない on
its own (ゴミがない, ことはない) is the word 無い; ありがとうございました,
よかった (良い) and the なん of 何ページ or なんで are entries of their own,
so each counts as a new word; 男 in 男の人 is a word (as a speaker label it
isn't counted); and と opening a sentence (…と、思う) reads as "if", so
keep it after the quote. な before a noun (きれいな部屋) and before ん / の
(そうなんだ) is taught with だ / です, so it's fine anywhere. ただいま,
おかえり(なさい), いってきます and いってらっしゃい read as words.

Use a new grammar point where a native speaker would, not in every line:
a chapter that teaches ので still has friends say から, and one that
teaches ～たことがある doesn't use it for a single night remembered.

Sentences split at 。, ！ and ？ only: a line ending in … alone runs into
the next one, and so do several 『…』 lyric lines in one paragraph, which
breaks the sentence and translation count. End it with 。 or give each line
its own paragraph. A side story is held to the length rule too, counting
the main chapters before the one it follows.

Then check it, with the dictionary built (`python3 tools/dictionary/build_dictionary.py`):

    ./gradlew -PcoreOnly=true :core:stories -Pchapter=new-housemate/through-the-wall

That takes a few seconds: it reads every chapter, writes each `SUMMARY.md`
and `COVERAGE.md`,
and prints what you need for the chapter you name (by id or by its file):
its new words with their readings against the allowance, grammar not
taught yet, anything the checks below find, naming the sentence when a
translation doesn't line up, and the kanji words to check are read as
meant. Without `-Pchapter` it prints every problem in the stories.

The checks (`StoryCheck`) are the rules above, and that the solver reaches
every chapter. A new word in one chapter can push another over its
allowance further on, so read the full list before you're done. Before
committing, run the tests too:

    ./gradlew -PcoreOnly=true :core:stories :core:test

`StoryPathTest` fails on any problem and on files out of date; its report
(`core/build/reports/story-path.txt`) lists each chapter's new words.
Commit all of the written files with the chapter.

Those files are worked out from every story, so two branches that each
change a story both change them, and conflict when the second is merged or
rebased. Never resolve them by hand: take either side, rerun
`:core:stories`, and commit what it writes.

## Writing a grammar lesson

Each topic in `grammar.json` is a lesson page, read just before the chapter
that teaches it, and again whenever it's looked up. Every lesson has the
same shape, so a reader always knows where to look:

1. **`summary`**: what the pattern does, in one or two sentences with a
   short example (`勝ったら, if we win`). At most 35 words.
2. **The chapter's line**: the first of `parts` is a line from the chapter
   that teaches it, with its English. Its words should be ones the reader
   has by then.
3. **`forms`**, for a pattern that changes a word: a small table of the
   form the reader knows and the new form, with a few words of English.
   `{"head": ["Verb", "Past"], "rows": [["来る", "来た", "come, came"]]}`,
   at most six rows, using words from the stories. It shows after the first
   line, so the notes don't need to spell the forms out again.
4. **At most two notes**, each a few sentences, and at most three lines in
   all. A note says what the reader needs to read the next chapters: how
   the form is made, and the one distinction that matters most. The notes'
   length grows with the reader: about 30 words in all for the first
   chapters, up to 90 once chapters run past 120 words (`lessonBudget` in
   `StoryCheck`: 30 plus half the teaching chapter's length).
5. **`more`**: everything else, folded under "More on …": the polite or
   formal version, rarer uses, comparisons with patterns they know, slang.
   It's `parts` again (lines and notes), shown after the rest when opened.

The app adds the rest itself: each line's English under it, and, once the
lesson has been read, lines from the reader's own chapters that use it.

Japanese written in notes, the summary and forms is read like the
examples, so it can be tapped: keep it to words a reader can look up, and
put a run of Japanese next to its English (`待って, wait`) rather than
inside a sentence of romaji. A lone kana (た, に) and an ending written
with ～ (～てる, ～った) are shown as written, not read: on their own they'd
read as some other word (たい as 対, versus; かも as 鴨, a duck). Write an
ending that way, or as a whole word (食べたい); the check fails a short run
that reads as a word no story uses or as another lesson's grammar.

Write for a reader of the stories, not a textbook: no grammar terms a
reader hasn't met, and nothing promised for "later". When in doubt, fold
it under `more`.

Changing a line records it again, which costs money; moving a line between
`parts` and `more` doesn't. The check fails a lesson over any of the limits
above, and names the limit.

## Recorded audio

Each sentence is recorded with Fish Audio into `app/src/main/assets/audio`,
one clip a sentence, named by a hash of the model, voice and text. The
"Record story audio" workflow records what's missing on each pull request
that changes the stories and commits the clips to its branch, so a changed
sentence is recorded again and its old clip deleted. Never edit or rename
clips by hand. To see what a change would record and cost, without
spending anything:

    ./gradlew -PcoreOnly=true :core:storyAudio

With `-Precord` and `FISH_API_KEY` set it records too (it needs ffmpeg).
Fish's voices differ a lot in loudness, so each new voice first says the
same short passage, and the gain that brings it to the others' loudness goes
in `levels.json`, one per voice; the app plays all of that voice's clips at
it, so a shout stays louder than a whisper. Clips are kept as Fish made them,
so a gain can be changed by hand without recording again. It stops before
spending more than `-Plimit` dollars (5 unless given). Clips conflict like
the written files above: take either side and let the workflow run again.
