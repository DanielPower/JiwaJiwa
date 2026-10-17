"""The manager offers Alex a part-time job, and he jumps at it."""
from story import chapter

CHAPTER = chapter(
    "The manager",
    [("店長が来た。", "The manager came over.")],
    [("店長|「ラーメン、うまいです？」", "\"How's the ramen?\""), ("アレックス|「うまい！」", "\"Great!\"")],
    [("アレックス|「人が多いね。」", "\"Busy, huh.\""), ("店長|「多いです。」", "\"It is.\"")],
    [("店長|「バイト、やります？」", "\"Would you like a part-time job?\""), ("アレックス|「…バイト？」", "\"…A job?\"")],
    [("店長|「ここのバイトです。」", "\"A job here.\""), ("アレックス|「やりたい！やります！」", "\"I want it! I'll do it!\"")],
    topic="masu",
    meets=[("店長", "runs the ramen shop, needs help")],
)
