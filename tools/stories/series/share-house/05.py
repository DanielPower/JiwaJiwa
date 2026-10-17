"""Alex's interview with the big manager: he starts today."""
from story import chapter

CHAPTER = chapter(
    "The interview",
    [("ここだ。", "Here it is."), ("人が多い。", "It's busy.")],
    [("店長が来た。", "The manager came over."), ("大きい人だ。", "He's a big guy.")],
    [("店長|「アレックスさん？」", "\"Alex?\""), ("アレックス|「はい！」", "\"Yes!\"")],
    [("店長|「ラーメン、好きです？」", "\"You like ramen?\""), ("アレックス|「はい、好きです！」", "\"Yes, I love it!\"")],
    [("店長|「今日から、お願いします。」", "\"You start today, then.\""), ("アレックス|「…はい！」", "\"…Yes!\"")],
    meets=[("店長", "runs the ramen shop where Alex works")],
)
