# Writes app/src/main/assets/stories/path.json: python3 tools/stories/path.py app/src/main/assets/stories/path.json
# Then run StoryPathTest (./gradlew -PcoreOnly=true :core:test) to check every step can be reached.
# Each story: (title, title English, [paragraph, ...]); a paragraph is [(Japanese sentence, English), ...].
import json, sys
S = [
# path-01 (これは猫です。) was lesson 1 word for word, so it went; numbers stay put so read stories stay read.
None,
("犬？", "A dog?", [[("それは犬ですか。", "Is that a dog?"), ("はい、犬です。", "Yes, it's a dog.")]]),
("本", "A book", [[("あれは本ですか。", "Is that a book over there?"), ("いいえ、本じゃないです。", "No, it isn't a book.")]]),
("わたしの猫", "My cat", [[("これはわたしの猫です。", "This is my cat."), ("猫の名前はタマです。", "The cat's name is Tama.")]]),
("先生と学生", "Teacher and student", [[("わたしは田中です。", "I'm Tanaka."), ("学生です。", "I'm a student.")], [("山田さんは先生です。", "Mr. Yamada is a teacher.")]]),
("友達", "A friend", [[("鈴木さんは学生です。", "Suzuki is a student."), ("わたしも学生です。", "I'm a student too."), ("鈴木さんはわたしの友達です。", "Suzuki is my friend.")]]),
("このかばん", "This bag", [[("このかばんはわたしのです。", "This bag is mine."), ("そのかばんは鈴木さんのです。", "That bag is Suzuki's.")]]),
("だれの？", "Whose?", [[("あのかばんはだれのですか。", "Whose is that bag over there?"), ("山田先生のです。", "It's Mr. Yamada's.")]]),
("何？", "What?", [[("これは何ですか。", "What is this?"), ("それはりんごです。", "That's an apple."), ("タマのりんごじゃないです。", "It isn't Tama's apple.")]]),
("学校", "School", [[("ここは学校です。", "This is a school."), ("わたしと鈴木さんの学校です。", "It's my and Suzuki's school.")]]),
("どこ？", "Where?", [[("「トイレはどこですか。」", "\"Where is the toilet?\""), ("「あそこです。」", "\"Over there.\"")]]),
("本がたくさん", "Lots of books", [[("学校に本があります。", "There are books at school."), ("たくさんあります。", "There are lots of them.")]]),
("家に猫がいます", "There's a cat at home", [[("わたしの家に猫がいます。", "There's a cat at my house."), ("タマです。", "It's Tama."), ("犬もいます。", "There's a dog too.")]]),
("大きい犬", "A big dog", [[("犬は大きいです。", "The dog is big."), ("タマは小さいです。", "Tama is small.")]]),
("鈴木さんの家", "Suzuki's house", [[("鈴木さんの家は大きくないです。", "Suzuki's house isn't big."), ("でも、きれいです。", "But it's pretty.")]]),
("好き", "I like it", [[("わたしは猫が好きです。", "I like cats."), ("犬も好きです。", "I like dogs too."), ("タマは魚が好きです。", "Tama likes fish.")]]),
("食べます", "Eating", [[("タマは魚を食べます。", "Tama eats fish."), ("わたしはパンを食べます。", "I eat bread.")]]),
("飲みます", "Drinking", [[("わたしは水を飲みます。", "I drink water."), ("山田先生はコーヒーを飲みます。", "Mr. Yamada drinks coffee.")]]),
("食べません", "Doesn't eat it", [[("タマはパンを食べません。", "Tama doesn't eat bread."), ("りんごも食べません。", "Tama doesn't eat apples either."), ("魚を食べます。", "Tama eats fish.")], [("鈴木さんはお茶を飲みません。", "Suzuki doesn't drink tea.")]]),
("学校へ行きます", "Going to school", [[("わたしは毎日学校へ行きます。", "I go to school every day."), ("鈴木さんも行きます。", "Suzuki goes too.")]]),
("店で", "At the shop", [[("店でパンを買います。", "I buy bread at the shop."), ("りんごも買います。", "I buy apples too.")]]),
("きのう", "Yesterday", [[("きのう、店で魚を買いました。", "Yesterday I bought fish at the shop."), ("タマは魚を食べました。", "Tama ate the fish."), ("おいしかったです。", "It was delicious.")]]),
("行きませんでした", "Didn't go", [[("きのう、鈴木さんは学校へ行きませんでした。", "Yesterday Suzuki didn't go to school."), ("家で寝ました。", "Suzuki slept at home.")]]),
("今日は休み", "A day off", [[("今日は休みです。", "Today is a day off."), ("公園へ行きます。", "I'm going to the park.")]]),
("友達と", "With a friend", [[("鈴木さんと一緒に公園へ行きました。", "I went to the park with Suzuki."), ("公園に犬がいました。", "There was a dog in the park.")]]),
("白い犬", "A white dog", [[("公園で大きい犬を見ました。", "I saw a big dog at the park."), ("白い犬でした。", "It was a white dog.")]]),
("本を読みます", "Reading a book", [[("家で本を読みます。", "I read a book at home."), ("おもしろい本です。", "It's an interesting book.")]]),
("朝", "Morning", [[("朝、六時に起きます。", "I get up at six in the morning."), ("パンを食べて、お茶を飲みます。", "I eat bread and drink tea.")]]),
("行きませんか", "Shall we go?", [[("「あした、一緒に公園へ行きませんか。」", "\"Do you want to go to the park together tomorrow?\""), ("「いいですね。行きましょう。」", "\"Sounds good. Let's go.\"")]]),
("食べたい", "I want to eat", [[("今日は肉が食べたいです。", "Today I want to eat meat."), ("でも、肉は高いです。", "But meat is expensive.")]]),
("赤いりんご", "Red apples", [[("この店のりんごは安いです。", "The apples at this shop are cheap."), ("赤いりんごを三つ買いました。", "I bought three red apples.")]]),
("ください", "Please", [[("「すみません、このりんごをください。」", "\"Excuse me, these apples, please.\""), ("「はい、どうぞ。」", "\"Here you are.\"")]]),
("寒いから", "Because it's cold", [[("今日は寒いですから、家にいます。", "It's cold today, so I'm staying home."), ("暑い日は公園へ行きます。", "On hot days I go to the park.")]]),
("今", "Right now", [[("今、タマは寝ています。", "Tama is sleeping right now."), ("犬は外で遊んでいます。", "The dog is playing outside.")]]),
("いい天気", "Nice weather", [[("今日はいい天気です。", "It's nice weather today."), ("鈴木さんと外で遊びました。", "I played outside with Suzuki."), ("楽しかったです。", "It was fun.")]]),
("雨", "Rain", [[("今日は雨です。", "It's raining today."), ("傘を持って、学校へ行きます。", "I take an umbrella and go to school.")]]),
("電車", "The train", [[("駅で電車に乗ります。", "I get on the train at the station."), ("電車は大きいです。", "The train is big.")]]),
("手紙", "A letter", [[("友達に手紙を書きました。", "I wrote a letter to a friend."), ("あした出します。", "I'll send it tomorrow.")]]),
("母", "My mother", [[("母は料理が上手です。", "My mother is good at cooking."), ("母の料理はおいしいです。", "My mother's cooking is delicious.")]]),
("父", "My father", [[("父は毎朝新聞を読みます。", "My father reads the newspaper every morning."), ("コーヒーも飲みます。", "He drinks coffee too.")]]),
("わたしの部屋", "My room", [[("わたしの部屋は狭いです。", "My room is small."), ("でも、明るいです。", "But it's bright.")]]),
("新しい時計", "A new watch", [[("きのう、新しい時計を買いました。", "Yesterday I bought a new watch."), ("高かったです。", "It was expensive.")]]),
("誕生日", "Birthday", [[("今日は母の誕生日です。", "Today is my mother's birthday."), ("母に花をあげました。", "I gave my mother flowers.")]]),
("映画", "A movie", [[("夜、鈴木さんと映画を見ました。", "In the evening I watched a movie with Suzuki."), ("とてもおもしろかったです。", "It was very interesting.")]]),
("タマの一日", "Tama's day", [
  [("タマはわたしの猫です。", "Tama is my cat."), ("小さくて、白い猫です。", "She's a small white cat.")],
  [("朝、タマは魚を食べます。", "In the morning Tama eats fish."), ("それから、外で遊びます。", "Then she plays outside.")],
  [("夜、タマは家に帰ります。", "At night Tama comes home."), ("わたしの部屋で寝ます。", "She sleeps in my room.")],
  [("わたしはタマが大好きです。", "I love Tama.")]]),
]

# Each lesson: (after story number, title, title English, grammar ids taught, parts);
# a part is a string of English, or (Japanese sentence, English).
L = [
(0, "これは ～です", "This is ~", ["wa", "desu"], [
  "To say what something is, put the thing first, then は, then what it is, then です.",
  ("これは猫です。", "This is a cat."),
  "は marks what you're talking about, the topic. It's written は, but when it does this job it's said \"wa\".",
  "です is a polite \"is\". It always comes at the very end.",
]),
(1, "～ですか", "Asking questions", ["ka", "janai"], [
  "To make a question, add か to the end. No question mark is needed.",
  ("これは猫ですか。", "Is this a cat?"),
  "Answer with はい for yes,",
  ("はい、猫です。", "Yes, it's a cat."),
  "or いいえ for no. To say something isn't, use じゃないです in place of です.",
  ("いいえ、猫じゃないです。", "No, it isn't a cat."),
]),
(3, "わたしの ～", "Whose: の", ["no", "no-nominal"], [
  "の joins two nouns, like 's or \"of\": the first owns or describes the second.",
  ("これはわたしの猫です。", "This is my cat."),
  "When it's clear what you mean, the second noun can go. わたしの on its own means \"mine\".",
  ("これはわたしのです。", "This is mine."),
]),
(4, "～と～、～も", "And, too: と and も", ["to", "mo"], [
  "と joins nouns: \"A and B\".",
  ("わたしの猫と犬です。", "They're my cat and dog."),
  "も means \"too\" or \"also\". It takes the place of は.",
  ("これは猫です。", "This is a cat."),
  ("それも猫です。", "That's a cat too."),
]),
(11, "あります、います", "There is: が and に", ["ga", "ni"], [
  "あります says a thing is there. います is for people and animals.",
  "が marks what there is, and に marks where it is.",
  ("ここに本があります。", "There's a book here."),
  ("あそこに猫がいます。", "There's a cat over there."),
]),
(16, "～を ～ます", "Doing things: を", ["wo"], [
  "The verb comes at the end of the sentence. Polite verbs end in ます.",
  "を marks the thing the action is done to. It's written を and said \"o\".",
  ("わたしはりんごを食べます。", "I eat apples."),
  "To say you don't, change ます to ません.",
  ("犬はりんごを食べません。", "The dog doesn't eat apples."),
]),
(19, "～へ、～で", "Where: へ and で", ["e", "de"], [
  "へ marks where you're going. It's written へ, but said \"e\" here.",
  ("わたしは学校へ行きます。", "I go to school."),
  "で marks where something happens.",
  ("学校でパンを食べます。", "I eat bread at school."),
]),
(28, "～ませんか", "Inviting: ませんか and ね", ["masenka", "ne"], [
  "Asking with ～ませんか, \"won't you?\", is a polite way to invite someone.",
  ("「一緒に公園へ行きませんか。」", "\"Won't you come to the park with me?\""),
  "ね at the end asks the listener to agree, like \"isn't it?\" or \"right?\".",
  ("「いいですね。」", "\"That sounds good.\""),
]),
(32, "～ですから", "Because: から", ["kara-because"], [
  "から after a sentence gives a reason: \"because\". The reason comes first, then what happens.",
  ("今日は休みですから、公園へ行きます。", "It's a day off today, so I'm going to the park."),
]),
(33, "～ています", "Right now: ～ています", ["te-iru"], [
  "A verb's て form plus います says something is going on right now.",
  ("タマは魚を食べています。", "Tama is eating fish."),
  ("鈴木さんは本を読んでいます。", "Suzuki is reading a book."),
]),
]

def lesson(n, title, title_en, teaches, parts):
    lines, en, notes = [], [title_en], []
    for p in parts:
        if isinstance(p, str):
            notes.append({"before": len(lines), "text": p})
        else:
            lines.append(p[0]); en.append(p[1])
    return {"id": f"lesson-{n:02d}", "title": title, "body": "\n".join(lines), "translations": en, "teaches": teaches, "notes": notes}

out = []
lessons = {}
for n, (after, *rest) in enumerate(L, 1):
    lessons.setdefault(after, []).append(lesson(n, *rest))
out += lessons.get(0, [])
for i, story in enumerate(S, 1):
    if story is None:
        out += lessons.get(i, [])
        continue
    title, title_en, paras = story
    out.append({
        "id": f"path-{i:02d}",
        "title": title,
        "body": "\n\n".join("".join(s for s, _ in p) for p in paras),
        "translations": [title_en] + [e for p in paras for _, e in p],
    })
    out += lessons.get(i, [])
json.dump({"stories": out}, open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
print(len(out), "steps")
