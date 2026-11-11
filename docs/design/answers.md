# Answers

How the server decides whether a typed answer is correct. The correct answer is the item's name in the active learning language, see [Items](items.md). Milestone 4 built these rules.

The general rule: the same sound passes, a different sound fails.

## German, Austrian German, Norwegian Bokmål and Dutch

Austrian German uses the same answer rules as German, with names from its own Minecraft translation.

Case, spaces, hyphens and punctuation are ignored. Keyboard stand-ins ä/ö/ü → ae/oe/ue, ß → ss and æ/ø/å → ae/oe/aa are accepted. The accents é, è, ê, ë and ï may be omitted. Unicode composed and decomposed spellings agree. Plain a/o/u do not replace umlauts, and plain a/o do not replace å/ø. Any other wrong, missing or extra letter fails, with no typo tolerance. Minecraft's names contain no articles, so none are required. Japanese kana, romaji, long-vowel and particle rules never apply here.

## Japanese

Kanji must be the ones in the Japanese name, and kana must be the right ones.

## Parts of the name

The name list splits each Japanese name into [name parts](items.md#name-parts), each with its reading: one kanji, kanji that are only read together, a run of kana, or the particle の. The answer is matched part by part, and each part may be written:

- as it is written in the name, like 原,
- as its reading in hiragana or katakana, like げん or ゲン, or
- as its reading in romaji, like `gen`.

Different parts may be written in different ways, so kanji, kana and romaji can be mixed in one answer.

Examples for オークの原木, which has the parts オーク, の, 原 (げん) and 木 (ぼく):

| Answer | Result | Why |
|---|---|---|
| `オークの原木` | Pass | Exact |
| `おーくのげんぼく` | Pass | All hiragana |
| `オークの原ぼく` | Pass | Kanji and kana mixed, per kanji |
| `オーク原木` | Pass | の is optional |
| `ooku no genboku` | Pass | Romaji |
| `ookuの原木` | Pass | Romaji, kana and kanji mixed |
| `おおくのげんぼく`, `おうく…`, `ouku…`, `ōku…`, `o-ku…` | Pass | Long vowel written another way |
| `gen'boku`, `genboku`, `gennboku` | Pass | ん written as n', n or nn |
| `オークの減木` | Fail | Wrong kanji with the same reading, which would teach the wrong kanji |
| `オークのはらき` | Fail | Kana that aren't the reading of the kanji |

Kanji that are only read together, like 今日 (きょう), are one part. They are written all as kanji or all as kana: `今日` and `きょう` pass, `今ょう` fails.

Letters and digits in a name, like TNT, are a part with a reading too, so `TNT` and `てぃーえぬてぃー` both pass.

## The particle の

A part that is the particle の, whether the name writes it の or ノ, may be left out, or written as の, ノ or `no`. Only the particle is optional. The の in a word, like キノコ, is part of a run of kana and can't be left out, so `kiko` fails.

An answer can't add a の that the name doesn't have: `作業の台` fails for 作業台.

## Ignored differences

These are ignored in both the answer and the name:

- Spaces, and punctuation like ・, 、 and !.
- Upper and lower case.
- Full-width and half-width letters and kana: `ＴＮＴ` is `TNT`, and `ｵｰｸ` is `オーク`.
- Hiragana and katakana. The small ヵ and ヶ are the exception: in names they are counters, read か, が or こ, so they are a part with their own reading, not the small kana ゕ and ゖ.

An answer with nothing left after this, like `。` or `・`, isn't an attempt, see [Reviews](reviews.md#attempts).

An apostrophe is only kept after an `n`, where it ends ん in romaji. A dash, like `-`, is the long vowel mark ー, as when typing on a Japanese keyboard.

## Long vowels

A long vowel can be written in any of these ways, in kana or romaji, whatever way the name writes it:

| Vowel | Kana | Romaji |
|---|---|---|
| a | ー, あ | `a`, `-`, `ā`, `â` |
| i | ー, い | `i`, `-`, `ī`, `î` |
| u | ー, う | `u`, `-`, `ū`, `û` |
| e | ー, え, い | `e`, `i`, `-`, `ē`, `ê` |
| o | ー, お, う | `o`, `u`, `-`, `ō`, `ô` |

So オーク passes as `おおく`, `おうく`, `ooku`, `ouku` and `ōku`, and 作業台 (さぎょうだい) passes as `さぎょーだい` and `sagyoodai`. A long e can also be written with an い or an え, since えい is said as a long e: えいが passes as `ええが` and `eega`.

A missing long vowel fails: `oku` for オーク, and `sagyodai` for 作業台.

A long vowel is only ever inside a part. Vowels in different parts are said separately, so a vowel that starts a part must be written as that vowel. 小売 is 小 (こ) and 売 (うり): `kouri` passes, and `koori`, `ko-ri` and `こおり` fail, since こおり is 氷, another word. The same goes for 手入れ (て, い, れ), where `teere` fails. When the answer leaves out the particle の, the part after it starts fresh too: for ネザーの奥, `neza-oku` passes and `neza-uku` fails.

## Romaji

Romaji follows what a Japanese keyboard types, so every kana has a spelling:

- Both romaji styles pass: shi/si, chi/ti, tsu/tu, fu/hu, ji/zi, sha/sya, cha/tya, ja/zya/jya.
- づ is `du`, ぢ is `di` and を is `wo`. Hepburn `zu`, `ji` and `o` fail for those kana: `mikazuki` fails for みかづき, and `mikaduki` passes.
- ん is written `n`, `nn` or `n'`. Before a vowel or `y`, a single `n` starts the next syllable: `kinen` is きねん, and きんえん is `kinnen` or `kin'en`. こんや is `konnya` or `kon'ya`, and `konya` is こにゃ.
- A small っ is the next consonant doubled, like `ippon` for いっぽん, or `t` before `ch`, like `matcha`. `xtu` and `ltu` also type it.
- Small kana on their own are typed with `x` or `l`, like `xya` for ゃ, and sounds like ふぁ, てぃ and ゔ are `fa`, `thi` and `vu`.

When romaji can be read in more than one way, the answer passes if any way matches. `onna` passes for おんな.

## What fails

Every other difference is a different sound, so it fails:

- A missing dakuten: `はけつ` for バケツ.
- A big kana for a small one: `きやく` for きゃく.
- A missing small っ: `いぽん` or `ipon` for いっぽん.
- Different kana with the same sound: ず/づ, じ/ぢ and を/お. `みかずき` fails for みかづき, and `はなじ` for はなぢ.
- Wrong kanji, even with the same reading.
- Anything missing or extra, apart from the particle の.

## Where it runs

Answers are only checked on the server, by the core's `AnswerChecker`. The client sends the typed text and never decides a pass or fail. See [Reviews](reviews.md).
