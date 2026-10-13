#!/usr/bin/env python3
"""Build the app's read-only dictionary database from pinned upstream sources.

Usage:
    python3 tools/dictionary/build_dictionary.py            # download (cached), build, write report
    python3 tools/dictionary/build_dictionary.py --check    # rebuild and compare with the digest in the report

The database holds every JMdict entry and kanji, about 85 MB, so it is built in CI and
not committed. build_report.md records its content digest instead.

Sources and their checksums are pinned in sources.json. Hand corrections go in
overrides.csv. Only the Python standard library is used.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sqlite3
import sys
import tempfile
import urllib.request
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
SOURCES = HERE / "sources.json"
OVERRIDES = HERE / "overrides.csv"
REPORT = HERE / "build_report.md"
OUTPUT = REPO / "app" / "src" / "main" / "assets" / "databases" / "dictionary.db"
CACHE = HERE / ".cache"

# Bump when the table layout changes; the app's queries must match.
SCHEMA_VERSION = 3

# Kanji and kana forms that are rare, outdated, irregular or search-only.
BAD_KANJI_TAGS = {"rK", "sK", "oK", "iK"}
BAD_KANA_TAGS = {"rk", "sk", "ok", "ik"}

UNRANKED = 10**9

SCHEMA = """
CREATE TABLE word (
    id INTEGER NOT NULL PRIMARY KEY,
    kanji TEXT,
    kana TEXT NOT NULL,
    usually_kana INTEGER NOT NULL,
    furigana TEXT,
    jlpt INTEGER NOT NULL,
    freq_rank INTEGER,
    study_order INTEGER
);
CREATE UNIQUE INDEX index_word_study_order ON word (study_order);
CREATE INDEX index_word_jlpt ON word (jlpt);

CREATE TABLE form (
    word_id INTEGER NOT NULL,
    ord INTEGER NOT NULL,
    text TEXT NOT NULL,
    is_kana INTEGER NOT NULL,
    PRIMARY KEY (word_id, ord)
);
CREATE INDEX index_form_text ON form (text);

CREATE TABLE sense (
    word_id INTEGER NOT NULL,
    ord INTEGER NOT NULL,
    pos TEXT NOT NULL,
    misc TEXT NOT NULL,
    gloss TEXT NOT NULL,
    info TEXT,
    PRIMARY KEY (word_id, ord)
);

CREATE TABLE kanji (
    literal TEXT NOT NULL PRIMARY KEY,
    meanings TEXT NOT NULL,
    onyomi TEXT NOT NULL,
    kunyomi TEXT NOT NULL,
    strokes INTEGER NOT NULL,
    grade INTEGER,
    jlpt INTEGER NOT NULL,
    freq_rank INTEGER,
    stroke_paths TEXT
);
CREATE INDEX index_kanji_jlpt ON kanji (jlpt);

CREATE TABLE tag (
    code TEXT NOT NULL PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE meta (
    key TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);
"""


# ---------------------------------------------------------------- sources


def fetch(url: str, sha256: str, cache: Path) -> Path:
    """Download url into the cache (once) and verify its checksum."""
    cache.mkdir(parents=True, exist_ok=True)
    target = cache / sha256
    if not target.exists():
        print(f"downloading {url}", file=sys.stderr)
        tmp = target.with_suffix(".part")
        with urllib.request.urlopen(url) as response, open(tmp, "wb") as out:
            while chunk := response.read(1 << 20):
                out.write(chunk)
        tmp.rename(target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    if digest != sha256:
        target.unlink()
        raise SystemExit(f"checksum mismatch for {url}: got {digest}")
    return target


def load_sources(cache: Path) -> dict:
    spec = json.loads(SOURCES.read_text(encoding="utf-8"))
    jm = spec["jmdict"]
    with zipfile.ZipFile(fetch(jm["url"], jm["sha256"], cache)) as z:
        jmdict = json.loads(z.read(jm["zip_member"]).decode("utf-8"))
    fu = spec["furigana"]
    furigana = json.loads(fetch(fu["url"], fu["sha256"], cache).read_text(encoding="utf-8-sig"))
    fr = spec["frequency"]
    frequency = fetch(fr["url"], fr["sha256"], cache).read_text(encoding="utf-8")
    jlpt = {
        int(level): fetch(s["url"], s["sha256"], cache).read_text(encoding="utf-8")
        for level, s in spec["jlpt"]["levels"].items()
    }
    spellings = []
    for s in spec["jlpt"]["spellings"]:
        spellings += json.loads(fetch(s["url"], s["sha256"], cache).read_text(encoding="utf-8"))
    kd = spec["kanjidic"]
    with zipfile.ZipFile(fetch(kd["url"], kd["sha256"], cache)) as z:
        kanjidic = json.loads(z.read(kd["zip_member"]).decode("utf-8"))
    kj = spec["kanji_jlpt"]
    kanji_jlpt = json.loads(fetch(kj["url"], kj["sha256"], cache).read_text(encoding="utf-8"))
    kv = spec["kanjivg"]
    with zipfile.ZipFile(fetch(kv["url"], kv["sha256"], cache)) as z:
        strokes = parse_kanjivg({name: z.read(name).decode("utf-8") for name in z.namelist() if name.endswith(".svg")})
    return {
        "spec": spec,
        "kanjidic": kanjidic,
        "kanji_jlpt": kanji_jlpt,
        "strokes": strokes,
        "jmdict": jmdict,
        "furigana": furigana,
        "frequency": frequency,
        "jlpt": jlpt,
        "spellings": spellings,
    }


# ---------------------------------------------------------------- parsing


@dataclass
class JlptRow:
    seq: str
    kana: str
    kanji: str
    level: int


def parse_jlpt(texts: dict[int, str]) -> tuple[list[JlptRow], list[JlptRow]]:
    """Return (rows linked to JMdict, rows without a JMdict id). Easiest level first."""
    linked, unlinked = [], []
    for level in sorted(texts, reverse=True):
        for r in csv.DictReader(texts[level].splitlines()):
            row = JlptRow(r["jmdict_seq"].strip(), r["kana"].strip(), r["kanji"].strip(), level)
            (linked if row.seq else unlinked).append(row)
    return linked, unlinked


def parse_frequency(text: str) -> dict[tuple[str, str], int]:
    """(term, reading) -> best (lowest) JPDB rank."""
    ranks: dict[tuple[str, str], int] = {}
    for r in csv.DictReader(text.splitlines(), delimiter="\t"):
        try:
            rank = int(r["frequency"])
        except (TypeError, ValueError):
            continue
        key = (r["term"], r["reading"])
        if rank < ranks.get(key, UNRANKED):
            ranks[key] = rank
    return ranks


def parse_furigana(entries: list[dict]) -> dict[tuple[str, str], list[list[str | None]]]:
    """(text, reading) -> [[base, ruby or None], ...]."""
    return {
        (e["text"], e["reading"]): [[seg["ruby"], seg.get("rt")] for seg in e["furigana"]]
        for e in entries
    }


def parse_spellings(bank: list[list]) -> dict[str, list[tuple[str, int]]]:
    """reading -> [(curated spelling, level), ...] from the Yomitan JLPT tag bank."""
    out: dict[str, list[tuple[str, int]]] = defaultdict(list)
    for term, _, data in bank:
        out[data["reading"]].append((term, int(data["frequency"]["displayValue"][1:])))
    return out


def parse_overrides(path: Path) -> dict[str, dict[str, str]]:
    """jmdict_seq -> {field: value}. Fields: kanji, kana, jlpt, usually_kana, exclude."""
    if not path.exists():
        return {}
    out: dict[str, dict[str, str]] = defaultdict(dict)
    with open(path, encoding="utf-8") as f:
        for r in csv.DictReader(line for line in f if not line.startswith("#")):
            out[r["jmdict_seq"].strip()][r["field"].strip()] = r["value"].strip()
    return out


# The main file for each character is named by its code point alone; the
# others (kanji/04e00-Kaisho.svg) are variant forms.
KANJIVG_MAIN = re.compile(r"kanji/([0-9a-f]{5})\.svg")
KANJIVG_PATH = re.compile(r'<path\b[^>]*?\sd="([^"]+)"')


def parse_kanjivg(files: dict[str, str]) -> dict[str, list[str]]:
    """character -> SVG path data for each stroke, in stroke order, on a 109 x 109 grid."""
    out = {}
    for name, svg in files.items():
        m = KANJIVG_MAIN.fullmatch(name)
        if m:
            out[chr(int(m.group(1), 16))] = KANJIVG_PATH.findall(svg)
    return out


@dataclass
class Kanji:
    literal: str
    meanings: list[str]
    onyomi: list[str]
    kunyomi: list[str]
    strokes: int
    grade: int | None
    jlpt: int
    freq_rank: int | None
    stroke_paths: list[str] | None


def parse_kanji(kanjidic: dict, levels: dict[str, dict], strokes: dict[str, list[str]]) -> list[Kanji]:
    """Every KANJIDIC2 character, with its JLPT level (0 when it isn't on the lists) and strokes."""
    out = []
    for c in kanjidic["characters"]:
        literal = c["literal"]
        rm = c.get("readingMeaning") or {"groups": []}
        readings = [r for g in rm["groups"] for r in g["readings"]]
        meanings = [m["value"] for g in rm["groups"] for m in g["meanings"] if m["lang"] == "en"]
        level = (levels.get(literal) or {}).get("jlpt_new")
        paths = strokes.get(literal)
        out.append(
            Kanji(
                literal=literal,
                meanings=meanings,
                onyomi=[r["value"] for r in readings if r["type"] == "ja_on"],
                kunyomi=[r["value"] for r in readings if r["type"] == "ja_kun"],
                strokes=c["misc"]["strokeCounts"][0],
                grade=c["misc"].get("grade"),
                jlpt=int(level) if level else 0,
                freq_rank=c["misc"].get("frequency"),
                stroke_paths=paths or None,
            )
        )
    out.sort(key=lambda k: k.literal)
    return out


# ---------------------------------------------------------------- word selection


def has_kanji(text: str) -> bool:
    """True when text contains a CJK ideograph (so 〇 and ＯＫ do not count)."""
    return any("\u4e00" <= c <= "\u9fff" or "\u3400" <= c <= "\u4dbf" or c == "々" for c in text)


def is_common(entry: dict, kanji_text: str) -> bool:
    return any(k["text"] == kanji_text and k["common"] for k in entry["kanji"])


def kana_applies(kana: dict, kanji_text: str) -> bool:
    applies = kana.get("appliesToKanji", ["*"])
    return "*" in applies or kanji_text in applies


def valid_kanji(entry: dict) -> list[dict]:
    return [k for k in entry["kanji"] if not BAD_KANJI_TAGS & set(k["tags"])]


def valid_kana(entry: dict) -> list[dict]:
    return [k for k in entry["kana"] if not BAD_KANA_TAGS & set(k["tags"])] or entry["kana"]


def readings_for(entry: dict, kanji_text: str) -> list[str]:
    return [k["text"] for k in valid_kana(entry) if kana_applies(k, kanji_text)]


def senses_for(entry: dict, kanji: str | None, kana: str) -> list[dict]:
    """Senses that apply to this spelling and reading, in JMdict order."""
    out = []
    for s in entry["sense"]:
        k_ok = kanji is None or "*" in s["appliesToKanji"] or kanji in s["appliesToKanji"]
        r_ok = "*" in s["appliesToKana"] or kana in s["appliesToKana"]
        if k_ok and r_ok:
            out.append(s)
    return out or entry["sense"]


@dataclass
class Word:
    id: int
    kanji: str | None
    kana: str
    usually_kana: bool
    jlpt: int
    freq_rank: int | None
    senses: list[dict]
    forms: list[tuple[str, bool]]
    notes: list[str] = field(default_factory=list)


def select_word(
    entry: dict,
    rows: list[JlptRow],
    curated: dict[str, list[tuple[str, int]]],
    ranks: dict[tuple[str, str], int],
) -> Word:
    """Pick the display spelling and reading for one JMdict entry.

    rows are every JLPT row linked to this entry, easiest level first. The
    easiest row decides the level, the reading, and whether the word is
    written in kana (Waller lists kana-only words without a kanji column).
    For kanji words, the spelling comes from Stephen Kraus's curated list,
    which replaces Waller's rare spellings with common ones and writes some
    words in kana; without a match, Waller's spelling is kept when JMdict
    considers it valid. JMdict's "usually kana" flag is not used: it is set
    on many words learners meet in kanji (分かる, 眼鏡). A kana word keeps its
    usual kanji spelling, if any, for the word page.
    """
    first = rows[0]
    notes: list[str] = []
    kanas = [k["text"] for k in valid_kana(entry)]
    kanji_forms = [k["text"] for k in valid_kanji(entry)]
    all_kanji = [k["text"] for k in entry["kanji"]]

    kana = first.kana
    if kana not in kanas:
        other = next((r.kana for r in rows if r.kana in kanas), None)
        notes.append(f"reading {kana} not in JMdict, using {other or kanas[0]}")
        kana = other or kanas[0]

    def rank(text: str) -> int:
        return ranks.get((text, kana), UNRANKED)

    usable = [k for k in kanji_forms if has_kanji(k) and kana in readings_for(entry, k)]
    waller = first.kanji
    kanji: str | None = None
    usually_kana = True
    if waller:
        spellable = [k for k in all_kanji if kana in [r["text"] for r in entry["kana"] if kana_applies(r, k)]]
        options = [
            (t, lvl) for t, lvl in curated.get(kana, []) if (t in spellable and has_kanji(t)) or t == kana
        ]
        options.sort(key=lambda o: (o[1] != first.level, o[0] != waller))
        if options:
            chosen = options[0][0]
        elif waller in usable:
            chosen = waller
        else:
            chosen = kana
            notes.append(f"list spelling {waller} is not a valid JMdict spelling for {kana}")
        if chosen != kana:
            kanji = chosen
            usually_kana = False
            if chosen != waller:
                notes.append(f"spelling {waller} -> {chosen}")
        else:
            if usable:
                kanji = min(usable, key=lambda k: (k != waller, rank(k)))
            notes.append(f"listed as {waller}, curated list writes it in kana")
    elif usable:
        kanji = min(usable, key=lambda k: (not next(f["common"] for f in entry["kanji"] if f["text"] == k), rank(k)))

    senses = senses_for(entry, kanji, kana)
    # The JLPT lists write many easy words in kana only because their kanji
    # are above that level. Furigana covers that here, so a word is shown in
    # kanji whenever JMdict has a common kanji spelling and doesn't mark the
    # word as usually written in kana.
    if usually_kana and kanji and is_common(entry, kanji) and "uk" not in senses[0]["misc"]:
        usually_kana = False
        notes.append(f"shown in kanji as {kanji} (common spelling, not usually kana)")

    # Kana-only spellings collide with particles and homophones (歯 は vs は),
    # so kanji words are ranked by their kanji spellings.
    ranked = usable if (kanji and not usually_kana) else [kana]
    word_rank = min([rank(t) for t in ranked] or [UNRANKED])
    if word_rank == UNRANKED:
        word_rank = min([rank(t) for t in all_kanji + kanas] or [UNRANKED])

    forms: list[tuple[str, bool]] = []
    shown = [(kana, True), (kanji, False)] if usually_kana or not kanji else [(kanji, False), (kana, True)]
    for item in shown + [(k, False) for k in kanji_forms] + [(k, True) for k in kanas]:
        if item[0] and item not in forms:
            forms.append(item)

    return Word(
        id=int(entry["id"]),
        kanji=kanji,
        kana=kana,
        usually_kana=usually_kana,
        jlpt=first.level,
        freq_rank=None if word_rank == UNRANKED else word_rank,
        senses=senses,
        forms=forms,
        notes=notes,
    )


def apply_override(word: Word, entry: dict, fields: dict[str, str]) -> Word | None:
    if fields.get("exclude", "").lower() in ("1", "true", "yes"):
        return None
    if "kanji" in fields:
        word.kanji = fields["kanji"] or None
    if "kana" in fields:
        word.kana = fields["kana"]
    if "jlpt" in fields:
        word.jlpt = int(fields["jlpt"])
    if "usually_kana" in fields:
        word.usually_kana = fields["usually_kana"].lower() in ("1", "true", "yes")
    word.senses = senses_for(entry, word.kanji, word.kana)
    word.notes.append("override: " + ", ".join(f"{k}={v}" for k, v in sorted(fields.items())))
    return word


def select_other(entry: dict, ranks: dict[tuple[str, str], int]) -> Word:
    """A word that isn't on the JLPT lists: only looked up while reading, or studied on request.

    Its level is 0 and it has no place in the study order. Every spelling is
    kept, rare ones included, so the reader can match whatever a story uses.
    """
    usable = [k for k in valid_kanji(entry) if has_kanji(k["text"])]
    common = [k for k in usable if k["common"]]
    kanji = (common or usable or [None])[0]
    kanji_text = kanji["text"] if kanji else None
    kanas = valid_kana(entry)
    applying = [k for k in kanas if kanji_text is None or kana_applies(k, kanji_text)] or kanas
    kana = next((k for k in applying if k["common"]), applying[0])["text"]
    senses = senses_for(entry, kanji_text, kana)
    usually_kana = kanji_text is None or "uk" in senses[0]["misc"]

    forms: list[tuple[str, bool]] = []
    shown = [(kana, True), (kanji_text, False)] if usually_kana else [(kanji_text, False), (kana, True)]
    for item in shown + [(k["text"], False) for k in entry["kanji"]] + [(k["text"], True) for k in entry["kana"]]:
        if item[0] and item not in forms:
            forms.append(item)
    rank = min([ranks.get((t, kana), UNRANKED) for t, _ in forms] or [UNRANKED])
    return Word(
        id=int(entry["id"]),
        kanji=kanji_text,
        kana=kana,
        usually_kana=usually_kana,
        jlpt=0,
        freq_rank=None if rank == UNRANKED else rank,
        senses=senses,
        forms=forms,
    )


def segments(word: Word, furigana: dict) -> tuple[list | None, bool]:
    """Furigana segments for the kanji spelling, and whether they came from JmdictFurigana."""
    if not word.kanji:
        return None, True
    found = furigana.get((word.kanji, word.kana))
    if found:
        return found, True
    return [[word.kanji, word.kana]], False


# ---------------------------------------------------------------- build


@dataclass
class Build:
    words: list[Word]
    # JMdict entries that aren't on the JLPT lists, in id order.
    others: list[Word]
    furigana: dict[int, list | None]
    missing_furigana: list[Word]
    unlinked: list[JlptRow]
    collapsed: dict[int, list[JlptRow]]
    tags: dict[str, str]
    meta: dict[str, str]
    kanji: list[Kanji] = field(default_factory=list)


def build(src: dict, overrides: dict[str, dict[str, str]]) -> Build:
    jmdict = src["jmdict"]
    entries = {e["id"]: e for e in jmdict["words"]}
    ranks = parse_frequency(src["frequency"])
    furigana = parse_furigana(src["furigana"])
    linked, unlinked = parse_jlpt(src["jlpt"])
    curated = parse_spellings(src["spellings"])

    by_seq: dict[str, list[JlptRow]] = defaultdict(list)
    for row in linked:
        by_seq[row.seq].append(row)

    words: list[Word] = []
    for seq, rows in by_seq.items():
        entry = entries.get(seq)
        if entry is None:
            raise SystemExit(f"JMdict entry {seq} ({rows[0].kanji or rows[0].kana}) not found; pin a matching JMdict release")
        word = select_word(entry, rows, curated, ranks)
        if seq in overrides:
            word = apply_override(word, entry, overrides[seq])
            if word is None:
                continue
        words.append(word)

    words.sort(key=lambda w: (-w.jlpt, w.freq_rank or UNRANKED, w.id))

    listed = {w.id for w in words}
    others = sorted(
        (select_other(e, ranks) for e in jmdict["words"] if int(e["id"]) not in listed),
        key=lambda w: w.id,
    )

    seg_by_id: dict[int, list | None] = {}
    missing: list[Word] = []
    for w in words:
        seg, ok = segments(w, furigana)
        seg_by_id[w.id] = seg
        if not ok:
            missing.append(w)
    for w in others:
        seg_by_id[w.id] = segments(w, furigana)[0]

    kanji = parse_kanji(src["kanjidic"], src["kanji_jlpt"], src["strokes"])

    spec = src["spec"]
    meta = {
        "schema_version": str(SCHEMA_VERSION),
        "jmdict_version": f"{jmdict['version']} ({jmdict['dictDate']})",
        "kanjidic_version": f"{src['kanjidic']['databaseVersion']} ({src['kanjidic']['dictDate']})",
        "attribution": "; ".join(
            f"{s['description']} ({s['license']}, {s['homepage']})" for s in spec.values()
        ),
    }
    return Build(
        words=words,
        others=others,
        furigana=seg_by_id,
        missing_furigana=missing,
        unlinked=unlinked,
        collapsed={int(seq): rows for seq, rows in by_seq.items() if len(rows) > 1},
        tags=jmdict["tags"],
        meta=meta,
        kanji=kanji,
    )


def write_db(result: Build, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        path.unlink()
    db = sqlite3.connect(path)
    try:
        db.executescript(SCHEMA)
        used_tags: set[str] = set()
        orders: list[int | None] = list(range(len(result.words))) + [None] * len(result.others)
        for order, w in zip(orders, result.words + result.others):
            seg = result.furigana[w.id]
            db.execute(
                "INSERT INTO word VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    w.id,
                    w.kanji,
                    w.kana,
                    int(w.usually_kana),
                    json.dumps(seg, ensure_ascii=False) if seg else None,
                    w.jlpt,
                    w.freq_rank,
                    order,
                ),
            )
            for i, (text, is_kana) in enumerate(w.forms):
                db.execute("INSERT INTO form VALUES (?, ?, ?, ?)", (w.id, i, text, int(is_kana)))
            for i, s in enumerate(w.senses):
                used_tags.update(s["partOfSpeech"])
                used_tags.update(s["misc"])
                db.execute(
                    "INSERT INTO sense VALUES (?, ?, ?, ?, ?, ?)",
                    (
                        w.id,
                        i,
                        json.dumps(s["partOfSpeech"]),
                        json.dumps(s["misc"]),
                        json.dumps([g["text"] for g in s["gloss"]], ensure_ascii=False),
                        "; ".join(s["info"]) or None,
                    ),
                )
        for k in result.kanji:
            db.execute(
                "INSERT INTO kanji VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    k.literal,
                    json.dumps(k.meanings, ensure_ascii=False),
                    json.dumps(k.onyomi, ensure_ascii=False),
                    json.dumps(k.kunyomi, ensure_ascii=False),
                    k.strokes,
                    k.grade,
                    k.jlpt,
                    k.freq_rank,
                    json.dumps(k.stroke_paths) if k.stroke_paths else None,
                ),
            )
        for code in sorted(used_tags):
            db.execute("INSERT INTO tag VALUES (?, ?)", (code, result.tags.get(code, code)))
        for key, value in sorted(result.meta.items()):
            db.execute("INSERT INTO meta VALUES (?, ?)", (key, value))
        db.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")
        db.commit()
        db.execute("VACUUM")
    finally:
        db.close()


def content_digest(path: Path) -> str:
    """Hash of every row in a stable order, independent of SQLite file layout."""
    h = hashlib.sha256()
    db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
        for t in tables:
            h.update(t.encode())
            for row in db.execute(f"SELECT * FROM {t} ORDER BY 1, 2"):
                h.update(json.dumps(row, ensure_ascii=False).encode())
    finally:
        db.close()
    return h.hexdigest()


# ---------------------------------------------------------------- report


def reported_digest(path: Path) -> str | None:
    if not path.exists():
        return None
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("Content digest: `"):
            return line.split("`")[1]
    return None


def write_report(result: Build, digest: str, path: Path) -> None:
    words = result.words
    by_level = Counter(w.jlpt for w in words)
    lines = [
        "# Dictionary build report",
        "",
        "Generated by `build_dictionary.py`. Do not edit by hand.",
        "",
        f"Content digest: `{digest}`",
        "",
        "| Level | Words | Shown in kanji | Shown in kana | No frequency rank |",
        "| --- | --- | --- | --- | --- |",
    ]
    for level in sorted(by_level, reverse=True):
        ws = [w for w in words if w.jlpt == level]
        kanji = sum(1 for w in ws if w.kanji and not w.usually_kana)
        lines.append(
            f"| N{level} | {len(ws)} | {kanji} | {len(ws) - kanji} | {sum(1 for w in ws if w.freq_rank is None)} |"
        )
    kanji_total = sum(1 for w in words if w.kanji and not w.usually_kana)
    lines.append(
        f"| All | {len(words)} | {kanji_total} | {len(words) - kanji_total} | {sum(1 for w in words if w.freq_rank is None)} |"
    )
    others = result.others
    other_kanji = sum(1 for w in others if w.kanji and not w.usually_kana)
    lines += [
        "",
        f"Other JMdict words, for lookups while reading: {len(others)}"
        f" ({other_kanji} shown in kanji, {sum(1 for w in others if w.freq_rank is not None)} with a frequency rank).",
    ]

    kanji_levels = Counter(k.jlpt for k in result.kanji)
    lines += [
        "",
        f"Kanji: {len(result.kanji)} from KANJIDIC2, {sum(1 for k in result.kanji if k.stroke_paths)} with KanjiVG strokes.",
        "",
        "| Level | Kanji | Without strokes |",
        "| --- | --- | --- |",
    ]
    for level in (5, 4, 3, 2, 1):
        ks = [k for k in result.kanji if k.jlpt == level]
        missing = [k.literal for k in ks if not k.stroke_paths]
        lines.append(f"| N{level} | {kanji_levels[level]} | {', '.join(missing) or 'None'} |")

    def word_label(w: Word) -> str:
        return f"{w.kanji}【{w.kana}】" if w.kanji else w.kana

    lines += [
        "",
        f"## JLPT rows without a JMdict link ({len(result.unlinked)}, dropped)",
        "",
        "These are single-kanji entries in Waller's N1 list rather than words.",
        "",
        ", ".join(f"{r.kanji or r.kana}【{r.kana}】" for r in result.unlinked) or "None",
        "",
        f"## Entries listed more than once ({len(result.collapsed)}, kept at the easiest level)",
        "",
        "JMdict treats these as one word, so they become one card. The other spellings stay searchable.",
        "",
        "| JMdict id | Rows (level: spelling) |",
        "| --- | --- |",
    ]
    for seq, rows in sorted(result.collapsed.items(), key=lambda kv: (-kv[1][0].level, kv[0])):
        lines.append(f"| {seq} | " + ", ".join(f"N{r.level}: {r.kanji or r.kana}【{r.kana}】" for r in rows) + " |")

    noted = [w for w in words if w.notes]
    lines += [
        "",
        f"## Spelling and reading adjustments ({len(noted)})",
        "",
        "| JMdict id | Level | Shown as | What changed |",
        "| --- | --- | --- | --- |",
    ]
    for w in sorted(noted, key=lambda w: (-w.jlpt, w.id)):
        lines.append(f"| {w.id} | N{w.jlpt} | {word_label(w)} | {'; '.join(w.notes)} |")

    lines += [
        "",
        f"## Kanji spellings with no furigana alignment ({len(result.missing_furigana)})",
        "",
        "The whole reading is placed over the whole spelling for these.",
        "",
        ", ".join(f"{word_label(w)} ({w.id})" for w in result.missing_furigana) or "None",
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


# ---------------------------------------------------------------- main


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="rebuild and fail if the committed database differs")
    parser.add_argument("--cache", type=Path, default=CACHE, help="download cache directory")
    args = parser.parse_args()

    result = build(load_sources(args.cache), parse_overrides(OVERRIDES))

    if args.check:
        with tempfile.TemporaryDirectory() as tmp:
            fresh = Path(tmp) / "dictionary.db"
            write_db(result, fresh)
            rebuilt = content_digest(fresh)
        recorded = reported_digest(REPORT)
        if rebuilt != recorded:
            print(f"build_report.md is stale: it records {recorded}, the build gives {rebuilt}", file=sys.stderr)
            return 1
        print(f"build_report.md is up to date ({rebuilt})")
        return 0

    write_db(result, OUTPUT)
    digest = content_digest(OUTPUT)
    write_report(result, digest, REPORT)
    print(
        f"wrote {OUTPUT.relative_to(REPO)}: {len(result.words)} JLPT words, {len(result.others)} others, "
        f"{OUTPUT.stat().st_size // 1024} KiB"
    )
    print(f"wrote {REPORT.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
