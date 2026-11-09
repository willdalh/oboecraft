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

VOICEVOX:玄野武宏

The reading clips (`assets/oboecraft/sounds/reading/` in the mod jar), which say each item's reading after a pass, were made with [VOICEVOX](https://voicevox.hiroshiba.jp/) in the voice of [玄野武宏](https://www.virvoxproject.com/), and are used under the [VOICEVOX terms of use](https://voicevox.hiroshiba.jp/term/) and the [terms of use of 玄野武宏, by the VirVox Project](https://www.virvoxproject.com/voicevoxの利用規約), which allow commercial and non-commercial use with the credit above. Anyone who uses the clips outside the mod must follow those terms too, and give the same credit. The clips are not under the MIT license or CC BY-SA 4.0. See [ADR: Reading clips are made ahead of time with VOICEVOX's 玄野武宏](docs/decisions/20261107-reading-clips-with-voicevox.md).

VOICEVOX itself doesn't ship with the mod. Only the reading clip tool, which is run by hand to make the clips, uses it.

## Tsukumogami sounds

The tsukumogami's sounds (`assets/oboecraft/sounds/tsukumogami/` in the mod jar) were made for the mod from the recordings below, which were cut, layered, slowed down and filtered, and converted to Ogg Vorbis. `arrive.ogg` is under CC BY 2.1 JP, see below, and the other sounds are under the MIT license, like the code.

The temple bell (`arrive.ogg`) is the bell of Chōmei-ji (長命寺) in Ōmihachiman, Shiga, recorded in 2006 by Jnn, from [Bonsyou5599.ogg](https://commons.wikimedia.org/wiki/File:Bonsyou5599.ogg) on Wikimedia Commons, under the [Creative Commons Attribution 2.1 Japan license](https://creativecommons.org/licenses/by/2.1/jp/) (CC BY 2.1 JP). It was trimmed, lowered by 10%, faded out and converted, and a synthesized strike was added in front of it. `arrive.ogg` is under the same license.

The wind chime (`fade.ogg`) is synthesized and has no other source.

The other sounds were made from these recordings, all under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) on OpenGameArt, which needs no credit. They are credited here anyway:

- Steps (`step1.ogg` to `step4.ogg`): [Different steps on wood, stone, leaves, gravel and mud](https://opengameart.org/content/different-steps-on-wood-stone-leaves-gravel-and-mud) by TinyWorlds and [Footsteps Leather, Cloth, Armor](https://opengameart.org/content/footsteps-leather-cloth-armor) by HaelDB.
- Idle (`ambient1.ogg` to `ambient3.ogg`): the squeaking wood of [100 CC0 metal and wood SFX](https://opengameart.org/content/100-cc0-metal-and-wood-sfx) by rubberduck and [Ghost breath](https://opengameart.org/content/ghost-breath) by qubodup.
- The wind-up and a hit with no effect (`attack.ogg`, `no_effect.ogg`): 100 CC0 metal and wood SFX by rubberduck.
- The strike (`hit.ogg`): [Thwack Sounds](https://opengameart.org/content/thwack-sounds) by AntumDeluge and [75 CC0 breaking / falling / hit sfx](https://opengameart.org/content/75-cc0-breaking-falling-hit-sfx) by rubberduck.
- Hurt (`hurt1.ogg`, `hurt2.ogg`): 100 CC0 metal and wood SFX by rubberduck and the cloth steps by HaelDB.
- Death (`death.ogg`): 100 CC0 metal and wood SFX and 75 CC0 breaking / falling / hit sfx by rubberduck, and the cloth steps by HaelDB.
