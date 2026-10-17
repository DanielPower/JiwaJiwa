"""Helpers for writing the bundled stories. See AGENTS.md here for the layout.

A story is a folder in series/ with a story.py (its title and blurb) and one
file per chapter, 01.py, 02.py and on. A chapter is a list of paragraphs, each
a list of (Japanese sentence, English). It may teach one grammar topic, shown
before it's read, and introduce people. Chapter ids are the story id and the
file's number, so keep chapters in place once people have read them.

A quote from one of the cast starts with their name and a bar, ユキ|「でしょ？」, and
is shown with the name before it, script style. Unnamed staff and strangers, and
thinking aloud, have no name.

Speech is casual from the start: Alex and his friends talk the way friends
do. Staff and strangers are polite, and です/ます come in as their own topic.
"""
import importlib.util
import os
import re


def topic(id, title, title_en, points, summary, parts):
    """A grammar topic. Its first Japanese line is the example shown with the summary before the chapter."""
    lines, en, notes = [], [], []
    for p in parts:
        if isinstance(p, str):
            notes.append({"before": len(lines), "text": p})
        else:
            lines.append(p[0]); en.append(p[1])
    return {"id": id, "title": title, "title_en": title_en, "points": points, "summary": summary,
            "body": "\n".join(lines), "translations": en, "notes": notes}


def chapter(title, *paras, topic=None, meets=()):
    return {"title": title, "paras": paras, "topic": topic, "meets": [{"name": n, "about": a} for n, a in meets]}


def series(title, blurb, after=(), side=False):
    """A story. after names stories, or chapters to mean the story up to that one. A side
    story uses only what those teach: something new to read with no new words."""
    return {"title": title, "blurb": blurb, "after": list(after), "side": side}


def lines(paras):
    """A paragraph's quotes marked with a speaker (ユキ|「…」) each go on a line of their own."""
    out = []
    for p in paras:
        run = []
        for s, e in p:
            if "|" in s:
                if run: out.append(run); run = []
                out.append([(s, e)])
            else:
                run.append((s, e))
        if run: out.append(run)
    return out


def _module(path):
    spec = importlib.util.spec_from_file_location(os.path.splitext(os.path.basename(path))[0], path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def load(folder):
    """The story in [folder], as it goes in path.json."""
    id = os.path.basename(folder)
    meta = _module(os.path.join(folder, "story.py")).STORY
    files = sorted(f for f in os.listdir(folder) if re.fullmatch(r"\d{2}\.py", f))
    if [int(f[:2]) for f in files] != list(range(1, len(files) + 1)):
        raise ValueError(f"{id}: chapters must be numbered 01.py, 02.py, … with no gaps: {files}")
    out = []
    for f in files:
        module = _module(os.path.join(folder, f))
        c = module.CHAPTER
        plot = (module.__doc__ or "").strip()
        if not plot:
            raise ValueError(f"{id}/{f}: start the file with a one-line plot in a docstring")
        ch = {
            "id": f"{id}-{f[:2]}",
            "title": c["title"],
            "plot": plot,
            "body": "\n\n".join("".join(s for s, _ in p) for p in lines(c["paras"])),
            "translations": [e for p in c["paras"] for _, e in p],
        }
        if c["topic"]: ch["topic"] = c["topic"]
        if c["meets"]: ch["meets"] = c["meets"]
        out.append(ch)
    s = {"id": id, "title": meta["title"], "blurb": meta["blurb"], "after": meta["after"], "chapters": out}
    if meta["side"]: s["side"] = True
    return s
