"""Hanami with Miho; Alex and Yuki are friends, and they take photos."""
from story import chapter

CHAPTER = chapter(
    "Hanami",
    [("今日は休みだ。", "It's my day off.")],
    [("春の公園は、いい。", "The park in spring is lovely."), ("桜は、今日もきれいだ。", "The cherry blossoms are beautiful again today.")],
    [("ミホさんが来た。", "Miho came.")],
    [("ミホ|「アレックスさん、桜は好きですか？」", "\"Alex, do you like cherry blossoms?\""), ("アレックス|「好きです！」", "\"I do!\"")],
    [("ミホ|「ユキさんは、友達ですか？」", "\"Is Yuki your friend?\""), ("ユキ|「友達だよ！」", "\"We're friends!\"")],
    [("アレックス|「写真、撮りたい！」", "\"I want to take a picture!\""), ("ユキ|「いいね！撮って！」", "\"Sure! Take one!\"")],
    [("ミホ|「ケンタさんは、どこですか？」", "\"Where's Kenta?\""), ("ユキ|「…ケンタは、眠い。」", "\"…Kenta's sleepy.\"")],
    [("写真の桜も、きれいだ。", "The cherry blossoms in the photo are pretty too.")],
    topic="ka",
)
