"""Yuki suggests the big-bowl ramen shop past the station."""
from story import chapter

CHAPTER = chapter(
    "Ramen?",
    [("ユキ|「ラーメン、食べたい？」", "\"Want some ramen?\""), ("アレックス|「食べたい！」", "\"Yes!\"")],
    [("ユキ|「駅のあっちのラーメン、いいよ。」", "\"The ramen place past the station is good.\"")],
    [("アレックス|「大きい？」", "\"Big bowls?\""), ("ユキ|「大きいよ。」", "\"Big.\""), ("アレックス|「食べたい、食べたい！」", "\"I want it, I want it!\"")],
    topic="tai",
)
