"""Alex meets Kenta, the sleepy housemate, one morning."""
from story import chapter

CHAPTER = chapter(
    "Morning",
    [("朝だ。", "It's morning."), ("外はきれいだ。", "It's nice out.")],
    [("アレックス|「おはよう！」", "\"Morning!\""), ("ケンタ|「…おはよう。」", "\"…Morning.\"")],
    [("アレックス|「ケンタ？」", "\"Kenta?\""), ("ケンタ|「うん。眠い…」", "\"Yeah. Sleepy…\"")],
    [("アレックス|「アレックスだよ。よろしく！」", "\"I'm Alex. Nice to meet you!\""), ("ケンタ|「よろしく…」", "\"Nice to meet you…\"")],
    [("アレックス|「お腹、すいた？」", "\"Hungry?\""), ("ケンタ|「…眠い。」", "\"…Sleepy.\"")],
    meets=[("ケンタ", "the room next door, games all night")],
)
