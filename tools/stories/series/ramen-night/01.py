"""At the ramen shop, Alex and Yuki buy tickets from the machine."""
from story import chapter

CHAPTER = chapter(
    "The ticket machine",
    [("ユキ|「ここだよ。」", "\"This is it.\""), ("アレックス|「人が多いね！」", "\"So many people!\"")],
    [("ユキ|「ここのラーメン、うまいよ。」", "\"The ramen here is great.\"")],
    [("チケットを買う。", "You buy a ticket."), ("ユキはチケットを買う。", "Yuki buys hers.")],
    [("アレックス|「お腹すいた！」", "\"I'm so hungry!\""), ("ユキ|「ラーメン、食べたいね。」", "\"I want ramen.\"")],
    topic="wo",
)
