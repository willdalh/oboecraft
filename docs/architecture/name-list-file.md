# Name list files

The mod ships `fabric/src/main/resources/oboecraft/name_lists/<language>.json` for `ja_jp`, `de_de`, `no_no` and `nl_nl`. `ItemMappings` loads every list once through `NameListFile`, using Fabric's mod container on both client and dedicated server. `NameListJson` in `json` parses each into a core `NameList`. Each language independently groups keys by its translated names, as described in [Items](../design/items.md).

The lists ship with the mod because Minecraft's language files have no readings, and dedicated servers don't have them. This puts Mojang's names inside the mod, which is a small legal grey area. If that becomes a problem, the server can download the language files at start instead, and only the code that loads the lists changes.

## Format

Each file is a JSON array with one entry per line. Japanese `name` values are arrays of parts:

```json
[
  {"id": "block.minecraft.oak_log", "translationKeys": ["block.minecraft.oak_log"], "name": ["オーク", "の", ["原", "げん"], ["木", "ぼく"]]}
]
```

German, Bokmål and Dutch `name` values are strings:

```json
[
  {"id": "block.minecraft.oak_log", "translationKeys": ["block.minecraft.oak_log"], "name": "Eichenstamm", "sourceName": "Eichenstamm"}
]
```

`id` is the persistent item id and `translationKeys` contains at least one key. `sourceName` keeps the original Minecraft spelling for a plain name. Reviewers correct only `name` when a translation is broken. This lets the tool retain corrections until the source translation changes. When omitted on input it defaults to the name; the tool always writes it for plain names.

A Japanese part is a string for kana read as written, or an array `[text, reading]` for kanji, letters and digits. Readings use hiragana and the long vowel mark. The name is the joined parts' text and the reading is the joined readings, so they cannot disagree. The readings and parts alone are under CC BY-SA 4.0, see [CREDITS.md](../../CREDITS.md).

## Checks

`NameListJson` and the core reject missing or invalid ids, blank or missing keys, duplicate ids or keys, empty names, invalid kana parts and invalid readings. Japanese names need parts; the other lists require plain names. Readings cannot begin with kana that belongs to the previous part, and kana cannot be given a different reading. Errors identify the entry and stop loading the packaged list before it can be used to check answers.

Former ids remain deferred until a name list update merges existing entries. The tool stops before writing a merge, independently for each language. Every generated entry still needs review; structural checks cannot find a wrong name or kanji reading.
