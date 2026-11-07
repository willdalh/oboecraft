# Reading clip tool

The reading clip tool makes the [reading clips](../design/display.md#reading-clip) with VOICEVOX: one sound file per name list entry that says the entry's reading, which the mod plays after a pass. It was built for [willdalh/oboecraft#25](https://github.com/willdalh/oboecraft/issues/25).

```
./gradlew :tools:generateReadingClips
```

It needs two programs that are installed by hand:

- **A VOICEVOX engine**, running at `http://localhost:50021`. The engine comes with the [VOICEVOX app](https://voicevox.hiroshiba.jp/), or runs on its own from the Docker image `voicevox/voicevox_engine:cpu-latest`, which starts the engine on that port: `docker run --rm -p 127.0.0.1:50021:50021 voicevox/voicevox_engine:cpu-latest`. Another address is given with `-Pvoicevox=http://host:port`.
- **FFmpeg** with libvorbis, on the `PATH`.

It reads the name list `fabric/src/main/resources/oboecraft/name_list.json` and writes:

| File | Contents |
|---|---|
| `fabric/src/client/resources/assets/oboecraft/sounds/reading/<item id>.ogg` | The clips |
| `fabric/src/client/resources/assets/oboecraft/sounds.json` | One sound event per entry, `oboecraft:reading.<item id>`, which plays its clip |
| `tools/reading-clips.json` | The clip list: what each clip was made from |
| `tools/build/reading-clips/review.md` | The review list of the clips it made |

The clips are client resources, since only the client plays sounds. The clip list is kept in the repository, but isn't part of the mod.

## Module

The tool is in the `tools` Gradle module, package `io.github.willdalh.oboecraft.tools.readingclips`. It reads the name list with `NameListJson` from the `json` module, the same code the mod reads it with.

## Steps

1. **Plan** (`ClipPlan`). An entry keeps its clip when the clip list has it with the same Japanese name and reading, in the same voice and format, and its file is there. Every other entry gets a new clip, so after a name list update only new and changed entries are made, and a first run makes them all. A new voice or format in the code makes every clip again. Clip files of items that aren't in the name list any more are deleted. Every item id must be a file name Minecraft allows in its resources (`a-z`, `0-9`, `_`, `.` and `-`), or the tool stops before it makes anything.
2. **What to say** (`SpokenText`). VOICEVOX is given the name as it is written, like オークの原木, since the words give it their pitch accent. But VOICEVOX reads kanji with its own dictionary, which can be wrong for the name, like 間 read あいだ in 試練の間, or 白色 read はくしょく for しろいろ. So the tool asks VOICEVOX how it would say the name, and how it would say the reading in katakana, and compares the sounds of each mora: its consonant and vowel. A long vowel counts as the vowel before it, since VOICEVOX writes こう as コオ in some words and コウ in others. If the sounds are the same, the name is said. Otherwise the reading in katakana is said, which VOICEVOX can only say one way, but whose pitch accent it guesses from the kana alone. The review list names these clips first.
3. **Speaking** (`Voicevox`). The engine makes the clip as a WAV file, from its `audio_query` and `synthesis` endpoints, in the voice 冥鳴ひまり, style ノーマル, with the engine's default speed, pitch and pauses. The voice is found by its id, so the tool stops if the engine doesn't have it.
4. **Encoding** (`OggEncoder`). FFmpeg turns the WAV file into Ogg Vorbis, the format Minecraft plays: mono, 24 kHz, at quality 1, about 4 to 10 KB per clip. It runs bit-exact, so the same WAV file always gives the same bytes and an unchanged clip never shows up as changed. Each clip is written to a `.part` file first, and moved in place once it is complete.
5. **Output.** Once every clip is made, `sounds.json` and the clip list are replaced, each only once the new file is fully written, and the clips of removed items are deleted. A run that stops halfway leaves the clips it made, which the next run makes again since the clip list doesn't have them yet.

## Clip list

`tools/reading-clips.json` has the voice, the format and the engine version, then one clip per line, in the name list's order:

```json
{
  "voice": {"speaker":"冥鳴ひまり","speakerUuid":"8eaad775-3119-417e-8cf4-2a10bfd592c8","style":"ノーマル"},
  "format": "Ogg Vorbis, quality 1, mono, 24000 Hz",
  "engineVersion": "0.26.1",
  "clips": [
    {"id":"block.minecraft.stone","name":"石","reading":"いし","spoken":"石"},
    {"id":"block.minecraft.trial_spawner","name":"トライアルスポナー","reading":"とらいあるすぽなー","spoken":"トライアルスポナー"}
  ]
}
```

`spoken` is the text VOICEVOX was given: the name, or the reading in katakana. The engine version is only recorded: a new engine doesn't make the clips again by itself.

## Reviewing

The review list has the clips that were said from their reading, with how VOICEVOX would have read the name, and then every clip the run made. A person who listens to them checks the pitch accent of the clips said from their reading. The sounds themselves are already checked against the reviewed reading by the tool.

## Tests

The tool's rules have JUnit tests in `tools/src/test`, with a stand-in for VOICEVOX, so they need no engine and no FFmpeg. The client game test `ReadingClipsClientGameTest` checks that every name list entry has a clip that Minecraft can play, so it fails when the name list changed and the tool wasn't run again.
