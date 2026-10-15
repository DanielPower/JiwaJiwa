# Writes app/src/main/assets/stories/path.json: python3 tools/stories/path.py app/src/main/assets/stories/path.json
# Then run StoryPathTest (./gradlew -PcoreOnly=true :core:test) to check every chapter can be reached.
#
# A story is one or more chapters. Each chapter is a list of paragraphs, each a
# list of (Japanese sentence, English). A chapter may teach one grammar topic,
# shown before it's read, and introduce people. Chapter ids are the story id
# and number, so keep chapters in place once people have read them.
#
# Speech is casual from the start: Alex and his friends talk the way friends
# do. Staff and strangers are polite, and です/ます come in as their own topic.
import json, sys


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


def series(id, title, blurb, *chapters, after=()):
    out = []
    for n, c in enumerate(chapters, 1):
        ch = {
            "id": f"{id}-{n:02d}",
            "title": c["title"],
            "body": "\n\n".join("".join(s for s, _ in p) for p in c["paras"]),
            "translations": [e for p in c["paras"] for _, e in p],
        }
        if c["topic"]: ch["topic"] = c["topic"]
        if c["meets"]: ch["meets"] = c["meets"]
        out.append(ch)
    return {"id": id, "title": title, "blurb": blurb, "after": list(after), "chapters": out}


GRAMMAR = [
topic("ta", "～た", "Did", ["ta"],
  "た on the end of a verb puts it in the past, the way friends say it: 来る (come), 来た (came).",
  [("ついに来た！", "I'm finally here!"),
   "Verbs change their endings to say when, and whether, something happens. The plain past ends in た, or だ after some verbs.",
   ("お腹すいた。", "I'm hungry. (My stomach has emptied.)"),
   "This plain form is what you use with friends. The polite past, ～ました, comes later.",
  ]),
topic("da", "だ / です", "Is", ["desu"],
  "だ ends a sentence that says what something is: 成田だ, it's Narita. Friends say だ or leave it off; strangers and staff say です.",
  [("成田だ。", "It's Narita."),
   "だ comes at the very end of the sentence. In casual talk it's often left off, especially in questions.",
   ("いい部屋だ！", "It's a nice room!"),
   "です means the same thing, politely. You'll hear it from staff and strangers.",
  ]),
topic("wa", "は", "As for…", ["wa"],
  "は marks what you're talking about. It's written は but said \"wa\". On its own with a rising voice, it asks \"what about…?\"",
  [("駅は？", "Where's the station? (What about the station?)"),
   "The topic comes first, then は, then what you say about it.",
   ("外は山だ。", "Outside, it's mountains."),
  ]),
topic("ga", "が", "Who or what", ["ga"],
  "が points at who or what does something, or what a description is about: 人が多い, there are lots of people.",
  [("人が多い。", "There are a lot of people."),
   "は sets the topic for what follows; が picks out the one thing the sentence is about. Often either works, with a slightly different feel.",
   ("ビルが高い。", "The buildings are tall."),
  ]),
topic("nai", "～ない", "Not", ["nai"],
  "ない on a verb makes it negative, the way friends say it: わかる (understand), わからない (don't understand).",
  [("わからない。", "I don't know."),
   "The verb's ending changes before ない. For る verbs, drop る: 食べる, 食べない. For others, the last sound moves to the \"a\" row: わかる, わからない.",
   ("いない。", "Nobody's here."),
  ]),
topic("yo-ne", "よ / ね", "You know / right?", ["yo", "ne"],
  "よ at the end tells someone something they don't know yet. ね looks for agreement, like \"right?\" or \"huh\".",
  [("ユキだよ。", "I'm Yuki."),
   "よ is friendly emphasis: you're filling the listener in.",
   ("いい部屋だね。", "It's a nice room, huh."),
   "ね assumes the listener sees it the same way.",
  ]),
topic("no", "の", "'s, of", ["no"],
  "の joins two nouns, like 's or \"of\": アレックスの部屋, Alex's room.",
  [("アレックスの部屋。", "Alex's room."),
   "The first noun owns or describes the second.",
   ("ここのラーメン。", "The ramen here."),
  ]),
topic("tai", "～たい", "Want to", ["tai"],
  "たい on a verb's stem means you want to do it: 食べる, 食べたい, want to eat.",
  [("ラーメン、食べたい？", "Want some ramen?"),
   "Drop る from a る verb, or move other verbs to their \"i\" sound, and add たい: やる, やりたい.",
   "It changes like an adjective: 食べたくない is \"don't want to eat\".",
  ]),
topic("wo", "を", "Object", ["wo"],
  "を marks what a verb is done to: ラーメンを食べる, eat ramen. It's said \"o\".",
  [("ラーメンを食べる。", "I eat ramen."),
   "Friends often drop it: ラーメン、食べる？ means the same.",
  ]),
topic("mo", "も", "Too, also", ["mo"],
  "も means \"too\" or \"also\", in place of は, が or を: ビールも, beer too.",
  [("ビールも！", "Beer too!"),
   ("ケンタも眠い。", "Kenta's sleepy too."),
  ]),
topic("kara-made", "から / まで", "From, until", ["kara-from", "made"],
  "から marks where or when something starts; まで, where or when it ends: 朝まで, until morning.",
  [("朝までゲーム。", "Games until morning."),
   "They work with places and times, together or apart.",
  ]),
]

SERIES = [
series("arrival", "Arrival", "Alex has a year in Japan on a working holiday visa. First, he has to get there.",
  chapter("I'm here", [("来た。", "I'm here."), ("ついに来た！", "Finally here!")], topic="ta",
          meets=[("アレックス", "from Canada, in Japan for a year")]),
  chapter("Narita", [("成田だ。", "This is Narita."), ("大きいな。", "It's big."), ("人、多い！", "So many people!")], topic="da"),
  chapter("Which way?", [("駅は？", "Where's the station?"), ("あっち？", "That way?"), ("あ、あっちだ。", "Oh, it's that way.")], topic="wa"),
  chapter("Out the window", [("外は山だ。", "Outside, mountains."), ("山、山、山。", "Mountains, mountains, mountains."), ("きれい！", "Beautiful!")]),
  chapter("Tokyo", [("東京だ！", "Tokyo!"), ("人が多い。", "There are so many people."), ("ビルが高い。", "The buildings are tall.")], topic="ga"),
  chapter("Lost", [("ここ、どこ？", "Where am I?"), ("わからない。", "No idea.")], topic="nai"),
  chapter("The konbini", [("あ、コンビニだ！", "Oh, a konbini!")], [("「いらっしゃいませ！」", "\"Welcome!\"")], [("ここ、いい。", "I like this place.")]),
  chapter("The house", [("ここだ。", "This is it."), ("「アレックス？」", "\"Alex?\""), ("「うん。」", "\"Yeah.\"")]),
  chapter("Yuki", [("「ユキだよ。よろしく。」", "\"I'm Yuki. Nice to meet you.\""), ("「よろしく！」", "\"Nice to meet you!\"")], topic="yo-ne",
          meets=[("ユキ", "lives in the share house, makes coffee, plays bass")]),
  chapter("The room", [("「ここ、アレックスの部屋。」", "\"This is your room, Alex.\""), ("「いい部屋だね！」", "\"It's a nice room!\"")], topic="no"),
  chapter("Hungry", [("「お腹、すいた？」", "\"Hungry?\""), ("「すいた！」", "\"Starving!\"")]),
  chapter("Ramen?", [("「ラーメン、食べたい？」", "\"Want some ramen?\""), ("「食べたい！」", "\"Yes!\"")], topic="tai"),
),

series("ramen-night", "Ramen night", "Yuki knows a ramen shop. Alex is very hungry.",
  chapter("The ticket machine",
    [("「ここのラーメン、うまいよ。」", "\"The ramen here is great.\"")], [("チケットを買う。", "You buy a ticket.")],
    topic="wo"),
  chapter("Which one?",
    [("「これ、食べたい！」", "\"I want this one!\""), ("「卵も？」", "\"Egg too?\""),
     ("「うん、卵も。ビールも！」", "\"Yeah, egg too. And a beer!\"")],
    topic="mo"),
  chapter("Slurp",
    [("「うまい！」", "\"So good!\""), ("「でしょ？」", "\"Told you.\""), ("「熱い！」", "\"Hot!\""), ("「ゆっくり食べて。」", "\"Eat slowly.\"")]),
  chapter("The manager",
    [("店長が来た。", "The manager came over.")], [("「バイト、やりたい？」", "\"Want a part-time job?\""),
     ("「やりたい！」", "\"I do!\"")],
    meets=[("店長", "runs the ramen shop, needs help")]),
  after=["arrival"]),

series("share-house", "The Share House",
  "Alex has a year in Tokyo, a room in a Shimokitazawa share house, and housemates with opinions.",
  chapter("Morning",
    [("朝だ。", "It's morning."), ("「おはよう！」", "\"Morning!\""), ("「…おはよう。」", "\"…Morning.\""),
     ("「ケンタ？」", "\"Kenta?\""), ("「うん。眠い…」", "\"Yeah. Sleepy…\"")],
    meets=[("ケンタ", "the room next door, games all night")]),
  chapter("All night",
    [("「眠い？」", "\"Sleepy?\""), ("「うん。朝までゲームだった。」", "\"Yeah. I was gaming until morning.\""),
     ("「朝まで！？」", "\"Until morning?!\"")],
    [("「アレックス、ゲーム好き？」", "\"Alex, do you like games?\""), ("「好き！」", "\"I do!\""), ("「今夜、ゲームだ！」", "\"Games tonight, then!\"")],
    topic="kara-made"),
  chapter("Coffee",
    [("ユキは朝、コーヒーを飲む。", "Yuki drinks coffee in the morning.")],
    [("「アレックス、飲む？」", "\"Alex, want some?\""), ("「飲む！ありがとう。」", "\"Yes! Thanks.\"")],
    [("「ケンタは？」", "\"Kenta?\""), ("「…飲む。」", "\"…Yes.\"")],
    topic="wo"),
  after=["arrival"]),
]

json.dump({"series": SERIES, "grammar": GRAMMAR}, open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
print(sum(len(s["chapters"]) for s in SERIES), "chapters,", len(GRAMMAR), "grammar topics")
