# ADR-20261107: Reading clips are made ahead of time with VOICEVOX's 冥鳴ひまり

## Status
Accepted

## Context
After a pass, the mod says the item's reading, so the player hears how the name is said ([willdalh/oboecraft#25](https://github.com/willdalh/oboecraft/issues/25)). The issue settled how: the clips are made ahead of time with VOICEVOX from the reviewed readings and ship with the mod, one per name list entry, around 1,700. That works offline and sounds the same for everyone, where speech made in the game would need a speech engine on every player's computer. It left the voice open.

VOICEVOX's software can be used for anything, but each voice has its own terms for the speech made with it, and every voice asks for a credit in the form "VOICEVOX:<voice name>". The mod is published as a GitHub release, its code is under MIT, so anyone may fork and redistribute it, also commercially, and the clips are files inside the jar, not speech in a video. Of the voices in VOICEVOX 0.26.1:

- No.7 and ユーレイちゃん are for non-commercial use only, unless their makers agree first.
- もち子さん excludes audio works, audio material and games.
- 青山龍星 and 後鬼 need their makers' agreement first when a company is involved, and ぞん子 for commercial use. Voidoll needs it for any use by a company.
- The others allow commercial and non-commercial use with the credit alone.

Among the voices whose terms ask for nothing but the credit, the voice should suit a learner: clear, calm and close to standard Japanese, not a character voice with a strong manner of speaking, like ずんだもん, or a whispering or emotional style.

## Decision
Every reading clip is said by 冥鳴ひまり in the style ノーマル. Her terms allow commercial and non-commercial use with the credit "VOICEVOX:冥鳴ひまり", without an application or report, and only forbid uses against public order, that harm others, that promote a particular ideology or group, or that she finds inappropriate. A name list entry says nothing like that. Her normal style is a calm, clear adult voice, often used for narration, which suits hearing a word to learn it.

The credit is in `CREDITS.md`, which ships in the mod jar, with links to the VOICEVOX terms and hers. The clips are under those terms, not under MIT or CC BY-SA 4.0.

The reading clip tool makes the clips with a VOICEVOX engine run by hand, and keeps each clip until its entry's name or reading changes. It names the voice in its code, by VOICEVOX's id for it, so the voice can't change by accident.

## Consequences
- The clips need nothing on the player's computer and no network, and every player hears the same clip.
- The mod jar grows by about 10 MB of clips, and the repository holds them as binary files. A clip changes only when its entry does, since the encoding is bit-exact.
- A name list update needs a run of the tool after the name list tool, with VOICEVOX and FFmpeg installed. A client game test fails while an entry has no clip.
- Changing the voice makes every clip again, which takes about half an hour, and changes the credit. The new voice's terms must allow its clips to ship in a published mod that others may redistribute.
- Anyone who uses the clips outside the mod has to follow VOICEVOX's terms and 冥鳴ひまり's, and give the credit.
