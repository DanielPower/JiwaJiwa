"""Yuki shows Alex his room and its view."""
from story import chapter

CHAPTER = chapter(
    "The room",
    [("ユキ|「ここ、アレックスの部屋。」", "\"This is your room, Alex.\""), ("アレックス|「いい部屋だね！」", "\"It's a nice room!\"")],
    [("ユキ|「外、きれいだね。」", "\"Nice view, huh.\""), ("アレックス|「うん。ビルが高い。」", "\"Yeah. The buildings are tall.\"")],
    [("アレックス|「ユキの部屋は？」", "\"Where's your room?\""), ("ユキ|「あっちだよ。」", "\"Over there.\"")],
    topic="no",
)
