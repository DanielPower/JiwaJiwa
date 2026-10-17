"""Rain hits Alex's laundry and he rushes out, too loud for Kenta."""
from story import chapter

CHAPTER = chapter(
    "Laundry day",
    [("今日は休み。", "It's my day off.")],
    [("洗濯は外だ。", "My laundry's hanging outside.")],
    [("ゲームをやった。", "I played games.")],
    [("ユキ|「アレックス、雨、降ってるよ！」", "\"Alex, it's raining!\"")],
    [("アレックス|「洗濯…外だ！」", "\"My laundry… it's outside!\"")],
    [("アレックス|「ちょっと待って！」", "\"Hang on!\"")],
    [("外だ。", "Outside."), ("雨が降ってる！", "It's pouring!"), ("洗濯、洗濯、洗濯！", "Laundry, laundry, laundry!")],
    [("ケンタ|「アレックス、うるさい。」", "\"Alex, you're so loud.\""), ("アレックス|「ケンタ、ごめん！」", "\"Sorry, Kenta!\"")],
    topic="te-iru",
)
