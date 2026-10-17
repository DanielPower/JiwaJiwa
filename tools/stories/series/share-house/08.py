"""On his day off, Alex finds a manga shop where Miho works."""
from story import chapter

CHAPTER = chapter(
    "The manga shop",
    [("今日は休み。", "It's my day off.")],
    [("駅のあっちに来た。", "I went past the station.")],
    [("マンガの店に来た。", "I came to the manga shop.")],
    [("「いらっしゃいませ！」", "\"Welcome!\"")],
    [("マンガが多い！", "So much manga!"), ("店は大きくない。", "The shop isn't big."), ("でも、いい店だ。", "But it's a good one.")],
    [("店の人はミホさん。", "The clerk is Miho.")],
    topic="ni",
    meets=[("ミホ", "works at the manga shop, has recommendations")],
)
