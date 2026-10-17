"""Yuki teaches Alex trash day; Kenta's trash is all coffee."""
from story import chapter

CHAPTER = chapter(
    "Trash day",
    [("朝、ユキが来た。", "In the morning, Yuki came by.")],
    [("ユキ|「アレックス、今日はゴミだよ。」", "\"Alex, it's trash day.\""), ("アレックス|「ゴミ？」", "\"Trash?\"")],
    [("ユキ|「うん。これは今日。」", "\"Yeah. This goes out today.\""), ("アレックス|「これも今日？」", "\"This too?\""), ("ユキ|「うん、これも。」", "\"Yep, that too.\"")],
    [("アレックス|「ケンタのゴミは？」", "\"What about Kenta's trash?\""), ("ユキ|「…あっち。」", "\"…Over there.\"")],
    [("アレックス|「多い！」", "\"That's a lot!\""), ("ユキ|「コーヒーのゴミだよ。」", "\"It's all coffee.\"")],
)
