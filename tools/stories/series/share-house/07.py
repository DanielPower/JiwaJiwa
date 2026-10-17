"""Game night in Kenta's messy room lasts until morning."""
from story import chapter

CHAPTER = chapter(
    "Game night",
    [("ケンタの部屋。", "Kenta's room, that night."), ("ゴミが多い。", "There's a lot of trash."), ("ゲームも多い。", "And a lot of games.")],
    [("ケンタ|「アレックス、これやる？」", "\"Alex, want to play this?\""), ("アレックス|「やる！」", "\"Sure!\"")],
    [("アレックス|「…負けた。」", "\"…I lost.\""), ("アレックス|「もう一回！」", "\"Again!\"")],
    [("アレックス|「…負けた！」", "\"…Lost again!\""), ("ケンタ|「もう一回？」", "\"One more?\""), ("アレックス|「もう一回！」", "\"One more!\"")],
    [("朝までゲームだった。", "We played until morning.")],
)
