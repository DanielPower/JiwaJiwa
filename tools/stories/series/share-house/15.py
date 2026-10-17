"""Alex's mom calls from Canada; he tells her about friends and work."""
from story import chapter

CHAPTER = chapter(
    "A call from home",
    [("今夜、電話が来た。", "Tonight I got a phone call."), ("カナダの母だ。", "It's my mom, in Canada.")],
    [("母|「アレックス、元気？」", "\"Alex, how are you?\""), ("アレックス|「元気だよ！」", "\"I'm good!\"")],
    [("母|「日本、好き？」", "\"Do you like Japan?\""), ("アレックス|「好き！ラーメンがうまい！」", "\"I love it! The ramen is great!\"")],
    [("母|「友達は？」", "\"And friends?\""), ("アレックス|「ユキ、ケンタ、ミホさん。いい友達だよ。」", "\"Yuki, Kenta, Miho. They're good friends.\"")],
    [("母|「バイトは？」", "\"And the job?\""), ("アレックス|「バイトも好き。店長は、いい人だよ。」", "\"I like the job too. The manager's a good guy.\"")],
    [("アレックス|「桜の写真も、撮ったよ！」", "\"I took pictures of the cherry blossoms, too!\"")],
    [("母は元気だ。", "Mom's doing well.")],
    meets=[("母", "Alex's mom, back in Canada")],
)
