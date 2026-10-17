"""Alex and Yuki choose their ramen, with eggs and beer."""
from story import chapter

CHAPTER = chapter(
    "Which one?",
    [("アレックス|「これ、食べたい！」", "\"I want this one!\""), ("ユキ|「卵も？」", "\"Egg too?\""), ("アレックス|「うん、卵も。ビールも！」", "\"Yeah, egg too. And a beer!\"")],
    [("アレックス|「ユキは？」", "\"What about you?\""), ("ユキ|「これ。卵も。」", "\"This one. With egg.\"")],
    [("アレックス|「ビールも？」", "\"A beer too?\""), ("ユキ|「うん、ビールも！」", "\"Yeah, beer too!\"")],
    topic="mo",
)
