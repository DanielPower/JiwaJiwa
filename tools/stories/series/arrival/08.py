"""Alex finds the share house, and someone calls his name."""
from story import chapter

CHAPTER = chapter(
    "The house",
    [("駅は、あっち。", "The station's that way."), ("コンビニは、ここ。", "The konbini's here.")],
    [("ここだ。", "This is it."), ("大きいな。", "It's big."), ("きれいだ。", "It's nice.")],
    [("「アレックス？」", "\"Alex?\"")],
    [("アレックス|「うん、アレックスだよ！」", "\"Yeah, I'm Alex!\"")],
    topic="yo-ne",
)
