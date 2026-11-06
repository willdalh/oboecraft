# Name list file

The name list ships in the mod's resources as `fabric/src/main/resources/oboecraft/name_list.json`. `NameListFile` in `fabric` loads it once when the mod starts and turns it into a core `NameList`. What an entry means is in [Items](../design/items.md#name-list-entries).

The format was decided in milestone 4. So far the file is written by hand. Milestone 5's script writes it from `ja_jp.json`, and an agent then reviews it.

## Format

The file is a JSON array with one object per entry, one entry per line:

```json
[
  {"id": "block.minecraft.oak_log", "translationKeys": ["block.minecraft.oak_log"], "japaneseName": ["オーク", "の", ["原", "げん"], ["木", "ぼく"]]},
  {"id": "block.minecraft.crafting_table", "translationKeys": ["block.minecraft.crafting_table"], "japaneseName": [["作", "さ"], ["業", "ぎょう"], ["台", "だい"]]},
  {"id": "block.minecraft.torch", "translationKeys": ["block.minecraft.torch"], "japaneseName": [["松明", "たいまつ"]]}
]
```

| Field | Contents |
|---|---|
| `id` | The item id |
| `translationKeys` | Every translation key that maps to the item, at least one |
| `japaneseName` | The Japanese name as an array of [name parts](../design/items.md#name-parts), in order |

Each name part is either:

- a string, for a part in kana, like `"オーク"` or `"の"`. It is read as it is written, so it has no reading of its own, or
- an array of the part's text and its reading in hiragana, like `["原", "げん"]`, for every other part: kanji, kanji read together, or letters and digits.

The Japanese name is the parts' text joined, so the file has no separate field for it, and the name and its readings can't disagree.

Former ids are added with the first name list update that merges two entries, see [Items](../design/items.md#name-list-entries).

## Checks

The core checks each entry when it is built, so a broken file stops the mod from starting with a message saying which entry is wrong, instead of failing answers later:

- An entry has at least one translation key, and none is blank.
- No item id or translation key belongs to two entries.
- A name has at least one part.
- A part's text has at least one letter or digit.
- A reading is hiragana and ー only, and doesn't start with a small kana like ゃ.
- A string part is only kana and punctuation. A part with a kanji, a letter or a digit needs its reading.
- A part in kana can't be given another reading than its kana.

Parts must be split as [Items](../design/items.md#name-parts) describes, with the particle の as its own part. The checks can't tell whether a kanji's reading is right or whether kanji were split correctly. That is what the review in milestone 5 is for.
