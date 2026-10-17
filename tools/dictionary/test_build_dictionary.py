"""Tests for build_dictionary.py on small hand-made inputs.

Run with: python3 -m unittest discover tools/dictionary
"""

import sqlite3
import tempfile
import unittest
from pathlib import Path

import build_dictionary as bd


def kanji(text, common=True, tags=()):
    return {"text": text, "common": common, "tags": list(tags)}


def kana(text, applies=("*",), tags=()):
    return {"text": text, "common": True, "tags": list(tags), "appliesToKanji": list(applies)}


def sense(*glosses, misc=(), kanji=("*",), kana=("*",)):
    return {
        "partOfSpeech": ["n"],
        "appliesToKanji": list(kanji),
        "appliesToKana": list(kana),
        "misc": list(misc),
        "info": [],
        "gloss": [{"text": g} for g in glosses],
    }


def entry(id, kanjis, kanas, senses):
    return {"id": str(id), "kanji": kanjis, "kana": kanas, "sense": senses}


HAMIGAKI = entry(
    1601040,
    [kanji("歯磨き"), kanji("歯磨", common=False)],
    [kana("はみがき")],
    [sense("toothpaste")],
)

MADOUSHI = entry(
    2000001,
    [kanji("魔導士", common=False), kanji("魔導師", common=False, tags=["rK"])],
    [kana("まどうし")],
    [sense("sorcerer")],
)

IRU = entry(1577980, [kanji("居る", common=False, tags=["rK"])], [kana("いる")], [sense("to be")])


def character(literal, meanings, on=(), kun=(), strokes=1, grade=None, frequency=None):
    readings = [{"type": "ja_on", "value": r} for r in on] + [{"type": "ja_kun", "value": r} for r in kun]
    readings.append({"type": "pinyin", "value": "x"})
    return {
        "literal": literal,
        "misc": {"grade": grade, "strokeCounts": [strokes], "frequency": frequency},
        "readingMeaning": {
            "groups": [{"readings": readings, "meanings": [{"lang": "en", "value": m} for m in meanings]}]
        },
    }


HA = character("歯", ["tooth"], on=["シ"], kun=["よわい", "は"], strokes=12, grade=3, frequency=1000)
MIGAKU = character("磨", ["grind", "polish"], on=["マ"], kun=["みが.く", "す.る"], strokes=16, grade=8)


class SelectWordTest(unittest.TestCase):
    def test_curated_spelling_replaces_rare_list_spelling(self):
        rows = [bd.JlptRow("1601040", "はみがき", "歯磨", 2)]
        w = bd.select_word(HAMIGAKI, rows, {"はみがき": [("歯磨き", 2)]}, {})
        self.assertEqual((w.kanji, w.kana, w.usually_kana), ("歯磨き", "はみがき", False))
        self.assertIn("spelling 歯磨 -> 歯磨き", w.notes)

    def test_list_spelling_kept_without_curated_match(self):
        rows = [bd.JlptRow("1601040", "はみがき", "歯磨", 2)]
        w = bd.select_word(HAMIGAKI, rows, {}, {})
        self.assertEqual(w.kanji, "歯磨")
        self.assertFalse(w.usually_kana)

    def test_kana_only_list_row_with_common_kanji_is_shown_in_kanji(self):
        rows = [bd.JlptRow("1601040", "はみがき", "", 5)]
        w = bd.select_word(HAMIGAKI, rows, {}, {})
        self.assertFalse(w.usually_kana)
        self.assertEqual(w.kanji, "歯磨き")
        self.assertEqual(w.forms[0], ("歯磨き", False))

    def test_usually_kana_word_stays_in_kana_but_keeps_kanji(self):
        e = entry(1415000, [kanji("沢山")], [kana("たくさん")], [sense("many", misc=["uk"])])
        w = bd.select_word(e, [bd.JlptRow("1415000", "たくさん", "", 5)], {}, {})
        self.assertTrue(w.usually_kana)
        self.assertEqual(w.kanji, "沢山")
        self.assertEqual(w.forms[0], ("たくさん", True))

    def test_rare_kanji_is_dropped(self):
        rows = [bd.JlptRow("1577980", "いる", "居る", 5)]
        w = bd.select_word(IRU, rows, {"いる": [("いる", 5)]}, {})
        self.assertIsNone(w.kanji)
        self.assertTrue(w.usually_kana)
        self.assertNotIn(("居る", False), w.forms)

    def test_easiest_level_wins(self):
        rows = [bd.JlptRow("1601040", "はみがき", "歯磨き", 4), bd.JlptRow("1601040", "はみがき", "歯磨", 1)]
        w = bd.select_word(HAMIGAKI, rows, {}, {})
        self.assertEqual(w.jlpt, 4)

    def test_unknown_reading_falls_back_to_jmdict(self):
        rows = [bd.JlptRow("1601040", "はみかき", "歯磨き", 3)]
        w = bd.select_word(HAMIGAKI, rows, {}, {})
        self.assertEqual(w.kana, "はみがき")
        self.assertTrue(any("not in JMdict" in n for n in w.notes))

    def test_kanji_word_ranked_by_kanji_spelling(self):
        ha = entry(1313000, [kanji("歯")], [kana("は")], [sense("tooth")])
        rows = [bd.JlptRow("1313000", "は", "歯", 5)]
        w = bd.select_word(ha, rows, {}, {("は", "は"): 2, ("歯", "は"): 900})
        self.assertEqual(w.freq_rank, 900)

    def test_frequency_readings_compared_in_hiragana(self):
        ranks = bd.parse_frequency("Word,Form,Rank\n眼鏡,メガネ,4876\nめがね,めがね,46385\nドア,ドア,1481\n眼鏡,めがね,9000\n")
        self.assertEqual(ranks[("眼鏡", "めがね")], 4876)
        door = entry(1, [], [kana("ドア")], [sense("door")])
        w = bd.select_word(door, [bd.JlptRow("1", "ドア", "", 5)], {}, ranks)
        self.assertEqual(w.freq_rank, 1481)

    def test_senses_filtered_by_spelling(self):
        e = entry(
            1,
            [kanji("会う"), kanji("遭う")],
            [kana("あう")],
            [sense("to meet"), sense("to have an accident", kanji=["遭う"])],
        )
        w = bd.select_word(e, [bd.JlptRow("1", "あう", "会う", 5)], {}, {})
        self.assertEqual([s["gloss"][0]["text"] for s in w.senses], ["to meet"])


class BuildTest(unittest.TestCase):
    def source(self):
        n5 = "jmdict_seq,kana,kanji,waller_definition\n1601040,はみがき,歯磨,toothpaste\n1577980,いる,居る,to be\n"
        n1 = "jmdict_seq,kana,kanji,waller_definition\n1601040,はみがき,歯磨,toothpaste\n,い,依,depending on\n"
        return {
            "spec": {"x": {"description": "d", "license": "l", "homepage": "h"}},
            "jmdict": {"version": "1", "dictDate": "2026-01-01", "tags": {"n": "noun"}, "words": [HAMIGAKI, IRU]},
            "furigana": [
                {"text": "歯磨き", "reading": "はみがき", "furigana": [{"ruby": "歯", "rt": "は"}, {"ruby": "磨", "rt": "みが"}, {"ruby": "き"}]}
            ],
            "frequency": "Word,Form,Rank\nいる,いる,18\n歯磨き,ハミガキ,27099\n",
            "jlpt": {5: n5, 1: n1},
            "spellings": [["歯磨き", "freq", {"reading": "はみがき", "frequency": {"displayValue": "N5"}}]],
            "kanjidic": {"databaseVersion": "1", "dictDate": "2026-01-01", "characters": [MIGAKU, HA]},
            "kanji_jlpt": {"歯": {"jlpt_new": 3}, "磨": {"jlpt_new": None}},
            "strokes": {"歯": ["M1,1", "M2,2"]},
        }

    def test_build_orders_collapses_and_writes(self):
        result = bd.build(self.source(), {})
        self.assertEqual([w.id for w in result.words], [1577980, 1601040])
        self.assertEqual(len(result.unlinked), 1)
        self.assertIn(1601040, result.collapsed)
        self.assertEqual(result.furigana[1601040], [["歯", "は"], ["磨", "みが"], ["き", None]])
        self.assertEqual(result.missing_furigana, [])

        with tempfile.TemporaryDirectory() as tmp:
            db = Path(tmp) / "d.db"
            bd.write_db(result, db)
            first = bd.content_digest(db)
            bd.write_db(result, db)
            self.assertEqual(first, bd.content_digest(db))

    def test_override_excludes_and_relevels(self):
        result = bd.build(self.source(), {"1577980": {"exclude": "1"}, "1601040": {"jlpt": "3"}})
        self.assertEqual([(w.id, w.jlpt) for w in result.words], [(1601040, 3)])
        # An excluded word is still there to look up.
        self.assertEqual([(w.id, w.jlpt) for w in result.others], [(1577980, 0)])

    def test_override_puts_a_word_on_the_lists(self):
        src = self.source()
        src["jmdict"]["words"].append(MADOUSHI)
        result = bd.build(src, {"2000001": {"jlpt": "4"}})
        word = next(w for w in result.words if w.id == 2000001)
        self.assertEqual((word.kanji, word.kana, word.jlpt), ("魔導士", "まどうし", 4))
        self.assertNotIn(2000001, [w.id for w in result.others])

    def test_words_off_the_lists_are_kept_for_lookups(self):
        src = self.source()
        src["jmdict"]["words"].append(MADOUSHI)
        result = bd.build(src, {})
        self.assertEqual([w.id for w in result.words], [1577980, 1601040])
        self.assertEqual([w.id for w in result.others], [2000001])
        with tempfile.TemporaryDirectory() as tmp:
            db = Path(tmp) / "d.db"
            bd.write_db(result, db)
            con = sqlite3.connect(db)
            row = con.execute("SELECT kanji, kana, jlpt, study_order FROM word WHERE id = 2000001").fetchone()
            con.close()
        self.assertEqual(row, ("魔導士", "まどうし", 0, None))


class KanjiTest(unittest.TestCase):
    def test_kanji_get_readings_meanings_level_and_strokes(self):
        result = bd.build(BuildTest().source(), {})
        self.assertEqual([k.literal for k in result.kanji], ["歯", "磨"])
        ha, migaku = result.kanji
        self.assertEqual((ha.meanings, ha.onyomi, ha.kunyomi), (["tooth"], ["シ"], ["よわい", "は"]))
        self.assertEqual((ha.strokes, ha.grade, ha.jlpt, ha.freq_rank), (12, 3, 3, 1000))
        self.assertEqual(ha.stroke_paths, ["M1,1", "M2,2"])
        self.assertEqual((migaku.jlpt, migaku.stroke_paths), (0, None))
        with tempfile.TemporaryDirectory() as tmp:
            db = Path(tmp) / "d.db"
            bd.write_db(result, db)
            con = sqlite3.connect(db)
            row = con.execute("SELECT kunyomi, jlpt, stroke_paths FROM kanji WHERE literal = '歯'").fetchone()
            con.close()
        self.assertEqual(row, ('["よわい", "は"]', 3, '["M1,1", "M2,2"]'))

    def test_kanjivg_strokes_come_from_the_main_file_in_order(self):
        svg = '<svg><g><path id="s1" kvg:type="㇐" d="M10,20c1,2"/><path id="s2" d="M30,40"/></g></svg>'
        strokes = bd.parse_kanjivg({
            "kanji/06b6f.svg": svg,
            "kanji/06b6f-Kaisho.svg": '<svg><path d="M0,0"/></svg>',
        })
        self.assertEqual(strokes, {"歯": ["M10,20c1,2", "M30,40"]})


class SelectOtherTest(unittest.TestCase):
    def test_common_spelling_and_every_form(self):
        w = bd.select_other(MADOUSHI, {("魔導士", "まどうし"): 40000})
        self.assertEqual((w.kanji, w.kana, w.usually_kana, w.jlpt), ("魔導士", "まどうし", False, 0))
        self.assertEqual(w.freq_rank, 40000)
        self.assertEqual([f for f, _ in w.forms], ["魔導士", "まどうし", "魔導師"])

    def test_usually_kana(self):
        e = entry(3, [kanji("流石")], [kana("さすが")], [sense("as expected", misc=["uk"])])
        w = bd.select_other(e, {})
        self.assertTrue(w.usually_kana)
        self.assertEqual(w.forms[0], ("さすが", True))

    def test_kana_only(self):
        e = entry(4, [], [kana("ふわふわ")], [sense("fluffy")])
        w = bd.select_other(e, {})
        self.assertIsNone(w.kanji)
        self.assertTrue(w.usually_kana)


if __name__ == "__main__":
    unittest.main()
