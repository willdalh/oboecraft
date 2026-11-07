# Credits

Oboecraft's code is under the MIT license, see [`LICENSE`](LICENSE). The mod also ships, and was made with, data and voices from others, under their own terms.

## Japanese names

The Japanese names in the name list (`oboecraft/name_list.json` in the mod jar) are from Minecraft's Japanese translation (`ja_jp.json`), by Mojang Studios. Minecraft is a trademark of Mojang Synergies AB. Oboecraft is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft.

## Readings

The readings in the name list, and how the names are split into parts, were made with the following data, and then reviewed and corrected by hand. They are licensed under the [Creative Commons Attribution-ShareAlike 4.0 International license](https://creativecommons.org/licenses/by-sa/4.0/) (CC BY-SA 4.0), since they are adapted from JMdict and KANJIDIC2. This covers only the readings and parts, not the Japanese names, which are Mojang's, and not the code, which is MIT. See [ADR: The name list's readings are under CC BY-SA 4.0](docs/decisions/20261106-name-list-under-cc-by-sa.md).

- **JMdict**, which the readings were checked against, and **KANJIDIC2**, which split each word's reading between its kanji. These files are the property of the [Electronic Dictionary Research and Development Group](https://www.edrdg.org/), and are used in conformance with the Group's [licence](https://www.edrdg.org/edrdg/licence.html), the Creative Commons Attribution-ShareAlike 4.0 International license. See the [JMdict project](https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project) and the [KANJIDIC project](https://www.edrdg.org/wiki/index.php/KANJIDIC_Project).
- **SudachiDict** and **Sudachi**, by Works Applications, which split the names into words and read them, under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). See [SudachiDict](https://github.com/WorksApplications/SudachiDict) and [Sudachi](https://github.com/WorksApplications/Sudachi).

None of these files ship with the mod. Only the name list tool, which is run by hand to make the name list, uses them.

## Reading clips

VOICEVOX:冥鳴ひまり

The reading clips (`assets/oboecraft/sounds/reading/` in the mod jar), which say each item's reading after a pass, were made with [VOICEVOX](https://voicevox.hiroshiba.jp/) in the voice of [冥鳴ひまり](https://www.meimeihimari.com/), and are used under the [VOICEVOX terms of use](https://voicevox.hiroshiba.jp/term/) and the [terms of use of 冥鳴ひまり](https://www.meimeihimari.com/terms-of-use), which allow commercial and non-commercial use with the credit above. Anyone who uses the clips outside the mod must follow those terms too, and give the same credit. The clips are not under the MIT license or CC BY-SA 4.0. See [ADR: Reading clips are made ahead of time with VOICEVOX's 冥鳴ひまり](docs/decisions/20261107-reading-clips-with-voicevox.md).

VOICEVOX itself doesn't ship with the mod. Only the reading clip tool, which is run by hand to make the clips, uses it.
