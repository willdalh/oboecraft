<p align="center">
  <img src="docs/images/icon.png" alt="Oboecraft icon: a log block with the kanji 覚 carved into it" width="160">
</p>

<h1 align="center">Oboecraft</h1>

<p align="center">
  A Fabric mod for Minecraft that teaches the Japanese names of the game's items with spaced repetition.
</p>

<p align="center">
  <img src="docs/images/answer-menu.jpg" alt="The answer menu over a cherry grove, with the answer typed in romaji" width="800">
</p>

Every item in the game starts out locked. You can pick a locked item up and move it around your inventory, but as soon as you try to use it, by placing it, crafting with it, eating it or wearing it, the game asks for its name in Japanese. A correct answer unlocks the item for a while. Each correct answer makes that while longer, from ten minutes up to a month, until the item is mastered and stays unlocked.

The answers are the names from Minecraft's own Japanese translation, so playing with the game language set to Japanese and learning the words go together. The name of the mod comes from 覚える (*oboeru*), "to memorize".

## How it plays

**Using a locked item opens the answer menu.** The menu shows the item's icon and level, but not its name. Kanji, kana and romaji are all accepted, and can be mixed: サクラの原木, さくらのげんぼく, `sakura no genboku` and `sakuraの原木` all pass for a cherry log.

**A correct answer shows the name as it is written.** The Japanese name, with its kanji, appears at the top of the screen for three seconds, so you see how it is written even when you answered in romaji.

<p align="center">
  <img src="docs/images/pass.jpg" alt="After a pass, サクラの原木 is shown at the top of the screen" width="800">
</p>

**Locked items have no names.** Their names are hidden as `???` in tooltips and above the hotbar. Unlocked items show how long they have left, and a small amber marker appears on the slot when an item is about to lock again.

<p align="center">
  <img src="docs/images/inventory.png" alt="Two inventory views: a locked wooden pickaxe named ??? and cherry planks with a lock timer. Amber markers show on the sticks and cobblestone, which lock soon." width="800">
</p>

**Failing costs items.** A review has two attempts. After two wrong answers, or after giving up with Don't know, part of the item is removed from your inventory, the item drops a level or two, and it is banned for five minutes. The menu then shows the correct name and its reading. At level 1, half of what you have of that item is removed. The share gets smaller at higher levels, so a mistake on a word you have known for weeks costs less than one you have barely learned. If the review started from a crafting recipe, the ingredients are removed instead.

<p align="center">
  <img src="docs/images/fail.jpg" alt="The answer menu after a fail, showing サクラの板材 and its reading" width="800">
</p>

**New items begin with a lesson.** The first review of an item is a lesson: the name can be shown, and wrong answers cost nothing. Items you already know can be marked as known and are unlocked for good. Items you don't want to learn can be ignored. The word list (J) shows known, ignored and mastered items, and any of them can be reverted to level 0.

## Answer checking

The rule is that the same sound passes and a different sound fails. Each name is split into parts with their readings, like サクラ, の, 原 (げん) and 木 (ぼく), and every part can be typed as written, in hiragana or katakana, or in romaji.

| Answer for サクラの原木 | Result | Why |
|---|---|---|
| `サクラの原木` | Pass | As written |
| `さくらのげんぼく` | Pass | All kana |
| `sakura no genboku` | Pass | Romaji |
| `サクラ原木` | Pass | The particle の is optional |
| `サクラの原き` | Fail | き isn't the reading of 木 here |
| `サクラの元木` | Fail | Same sound, but the wrong kanji |

Long vowels can be written several ways (`ōku`, `ooku`, `ouku` and `o-ku` for オーク), both romaji styles pass (`shi` and `si`, `tsu` and `tu`), and spaces, punctuation and case are ignored. The full rules are in [Answers](docs/design/answers.md).

The names and their readings are in a [name list](fabric/src/main/resources/oboecraft/name_list.json) of about 1,700 items, made from Minecraft's `ja_jp.json`, JMdict, KANJIDIC2 and Sudachi, and reviewed by hand.

## Levels

| Level | Unlocked for | Fail drops |
|---|---|---|
| 0 | Lesson | — |
| 1 | 10 minutes | Stays at 1 |
| 2 | 1 hour | 1 level |
| 3 | 4 hours | 1 level |
| 4 | 1 day | 1 level |
| 5 | 3 days | 2 levels |
| 6 | 1 week | 2 levels |
| 7 | 2 weeks | 2 levels |
| 8 | 1 month | 2 levels |
| 9 | Mastered | — |

The timers use real-world time, so items keep locking while you are offline. The levels, intervals, ban length and how much a fail removes are settings in `config/oboecraft/server.json`, and can also be changed in game through [Mod Menu](https://modrinth.com/mod/modmenu). See [Settings](docs/design/settings.md).

Locks are only active in survival and adventure mode. Creative and spectator mode ignore them.

## Multiplayer

The mod runs on both the server and the client. The server keeps each player's progress, checks every answer and decides every lock. The client only shows the answer menu and the hidden names, and it is sent an item's Japanese name only after a pass, a fail or a lesson. A client without the mod can't join a server that has it, and the mod does nothing on servers without it.

## Status

Oboecraft is in development and has no release yet. It targets Minecraft 26.3 with Fabric Loader and Fabric API, and only the latest Minecraft version is supported. The [implementation plan](docs/implementation-plan.md) lists the milestones.

## Building

Gradle needs Java 17 or newer to start. It downloads Java 25, Minecraft and the libraries itself, so the first build takes a while:

```bash
./gradlew build
```

The mod jar is written to `fabric/build/libs/`. Put it in the `mods` folder together with [Fabric API](https://modrinth.com/mod/fabric-api). To start the game with the mod from the source:

```bash
./gradlew runClient
```

[`AGENTS.md`](AGENTS.md) lists the other commands, including the unit tests and the game tests.

## Documentation

- [Design](docs/design/README.md): how the mod behaves, in detail
- [Architecture](docs/architecture/overview.md): how the code is structured
- [Glossary](docs/glossary.md): the terms used in the code and the docs
- [Decisions](docs/decisions/): architecture decision records
- [The original idea](docs/idea.md)

## License

The code is under the [MIT license](LICENSE). The readings and name parts in the name list are adapted from JMdict and KANJIDIC2 and are under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The Japanese names are from Minecraft's translation by Mojang Studios. See [`CREDITS.md`](CREDITS.md).

Oboecraft is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft.
