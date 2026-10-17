# The plan for the next stories

Approved by Daniel on 2026-10-17. Phase 1 finishes N5 and the casual core of N4: about 90 chapters across six stories and 30 grammar topics. Phase 2 (the rest of N4) gets its own outlines once Phase 1 is written.

Write the stories in this order, each in its own folder under `series/`, and list each in `stories.json` after the ones already there. Writing is cheap: keep going, and rewrite later if something reads wrong.

## Pace

- One grammar topic per chapter at most, and at least two chapters with no new grammar after each. Use each new topic again in the chapters after it.
- Chapters grow from where The Share House ends (about 30 words) to 150 to 300 words. Grow them a step at a time: a chapter under half the median of the five before it fails the check.
- New words come from `COVERAGE.md`, most common first, preferring words friends say. Bring back recent words often, so every word appears in at least two chapters.
- About one side story (`side: true`, nothing new at all) per ten chapters, following the chapter it continues.

## Grammar topics

Each topic gets an entry in `grammar.json` (in this order, after the ones there) when the chapter that teaches it is written: `id`, `title`, `title_en`, `points`, `summary`, and `parts` whose first line is the example shown before the chapter.

| # | Topic id | Title | Points | Story |
| --- | --- | --- | --- | --- |
| 1 | `n-desu` | ～んだ / ～の？ | n-desu, no-question | New Housemate |
| 2 | `filler` | あのー / えーと | filler | New Housemate |
| 3 | `janai` | ～じゃない / じゃん | janai, jan | New Housemate |
| 4 | `keredo` | ～けど / ～が | keredo, ga-but | New Housemate |
| 5 | `volitional` | ～よう / ～おう | volitional | New Housemate |
| 6 | `to-omou` | ～と思う / ～と | to-omou, to-quote | New Housemate |
| 7 | `tte` | ～って / ～という | tte, toiu | New Housemate |
| 8 | `kana` | かな / かも | kana, kamoshirenai | New Housemate |
| 9 | `mashou` | ～ましょう / ～ませんか | mashou, masenka, mashouka | Kenta's First Job |
| 10 | `te-mo-ii` | ～てもいい / ～てはいけない | te-mo-ii, te-wa-ikenai | Kenta's First Job |
| 11 | `naide` | ～ないで / ～ないでください | naide, naide-kudasai | Kenta's First Job |
| 12 | `potential` | ～える / ～られる | potential | The Band |
| 13 | `tara` | ～たら | tara | The Band |
| 14 | `nakereba` | ～なきゃ / ～なくてもいい | nakereba, nakute-mo-ii | The Band |
| 15 | `naru` | ～くなる / ～になる / ～く | naru, ku-adverb | The Band |
| 16 | `mitai` | ～みたい / ～そう | you, sou-looks | The Band |
| 17 | `te-shimau` | ～ちゃう / ～てしまう | te-shimau | The Band |
| 18 | `te-miru` | ～てみる | te-miru | The Band |
| 19 | `dake` | だけ / しか | dake, shika | The Band |
| 20 | `ni-iku` | ～に行く / へ | ni-iku, e | Miho's Manga |
| 21 | `yori` | より / ～のほうが | yori, hou-ga | Miho's Manga |
| 22 | `toki` | ～とき | toki | Miho's Manga |
| 23 | `mae-ni` | ～前に / ～た後で / ～てから | mae-ni, ato-de, te-kara | Miho's Manga |
| 24 | `ya` | や / ～たり / など | ya, tari, nado | Messages from Canada |
| 25 | `no-nominal` | ～の (the one, doing) | no-nominal | Messages from Canada |
| 26 | `te-kureru` | ～てくれる / ～てあげる / ～てもらう | te-kureru, te-ageru, te-morau | Yuki's Song |
| 27 | `node` | ～ので / ～し | node, shi | Yuki's Song |
| 28 | `noni` | ～のに | noni | Yuki's Song |
| 29 | `hou-ga-ii` | ～ほうがいい / ～すぎる | hou-ga-ii, sugiru | Yuki's Song |
| 30 | `ta-koto` | ～たことがある | ta-koto-ga-aru | Yuki's Song |

Phase 2, outlined later: passive and causative; ～ておく / ～てある; ～ていく / ～てくる; ～始める / ～終わる / ～続ける; ～やすい / ～にくい; ～てほしい; ～てくれませんか / ～なさい; ～つもり / ～ようと思う; ～ことにする / ～ことになる; ～ことがある / ～ことができる; ～ようになる / ～ようにする; ～ば / ～なら; ～と (whenever); ～ても; ～そうだ / ～らしい; ～はず; ～ところ / ～たばかり; ～ながら; ～までに; ～うちに; くらい / ほど; ずつ / ばかり; ～でも; ～ため; ～かどうか; ～について / ～として / ～にとって / ～によって; っけ / もん / さ; ぞ / ぜ / わ and the plain command.

## Stories

| Order | Id | Title | Chapters | Follows (`after`) | Topics |
| --- | --- | --- | --- | --- | --- |
| 1 | `new-housemate` | New Housemate | about 24 | `share-house` | 1 to 8 |
| 2 | `kentas-first-job` | Kenta's First Job | about 10 | `new-housemate/late-night` | 9 to 11 |
| 3 | `the-band` | The Band | about 24 | `new-housemate` | 12 to 19 |
| 4 | `mihos-manga` | Miho's Manga | about 12 | `the-band/mihos-notebook` | 20 to 23 |
| 5 | `messages-from-canada` | Messages from Canada | about 8 | `mihos-manga` | 24 to 25 |
| 6 | `yukis-song` | Yuki's Song | about 16 | `the-band`, `kentas-first-job`, `messages-from-canada` | 26 to 30 |

A story that follows a chapter can only use what's known by that chapter, and the main line must not depend on the side lines: The Band follows New Housemate alone, so Kenta's First Job's polite forms and topics can't appear in it. Yuki's Song follows everything before it.

### Cast

Returning: ユキ (Yuki), ケンタ (Kenta), ミホ (Miho), 店長 (the ramen shop manager), アレックス (Alex, by message until the end), 母 (Alex's mom).

New, each speaking only from the chapter whose `meets` introduces them:

- **ソラ** (Sora): takes Alex's old room; 19, from Hokkaido, starting university. Polite and nervous at first (あのー, えーと, です), slowly casual, so the learner hears the switch.
- **ダイチ** (Daichi): drummer Yuki meets at the live house. Loud and cheerful.
- **リン** (Rin): guitarist. Cool, says little, kinder than she looks. Katakana cast names are always read as names, so リン isn't 燐.
- **田中さん** (Tanaka): Kenta's senior at the game company. Strict, fair, always polite.

### New Housemate (spring)

Sora moves into Alex's room, lost and polite, and Yuki shows him around the way she once showed Alex. One night he hears her singing through the wall (歌うんだ？). Yuki has been writing songs since Alex left but only plays bass for other bands. Kenta is busy with his new job. Miho drags everyone to an open mic; Yuki says she'll try next time, and Sora signs her up anyway (ソラが「ユキさんが歌う」って). Ends with her first song on stage: shaky, then good.

### Kenta's First Job

Kenta's first weeks at the game company, with polite speech at work beside his usual speech at home; the contrast is the lesson. Lunch invitations (行きませんか), what he may and mustn't touch (触ってはいけない), Tanaka-san's rules (～ないでください). Ends with his name in a game's credits.

### The Band (summer)

Yuki forms a band with Daichi and Rin. Practice in a rented studio, whether they can play the summer festival (出られる？), what happens if they win (勝ったら), the heat, a broken string, Yuki trying to write lyrics, and only three songs to their name (三曲しかない). Ends on the festival stage in the same park as The Share House's festival.

### Miho's Manga

Miho draws a manga of her own for a magazine contest, with a heroine based on Yuki and her band. A trip to the publisher, comparing her pages with the pros', memories of drawing as a kid, and the work before and after the deadline. Ends with an honourable mention and a new editor's card.

### Messages from Canada

Short chapters of group-chat messages between Alex and the house: snow back home, his new job, what he did on the weekend (散歩したり、料理したり), the festival video. Ends with Alex saving for a ticket.

### Yuki's Song (autumn and winter)

The band gets a slot at a real live house in Shimokitazawa. Yuki finishes the song she started for Alex in The Share House. Sora makes flyers (作ってくれた), Miho draws the poster, Yuki practises too hard and gets sick (練習しすぎ), and nothing goes to plan even though they prepared (練習したのに). Ends with the show, and Alex in the crowd: he came back to hear it.
