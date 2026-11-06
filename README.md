# Oboecraft

A Minecraft mod for learning Japanese through spaced repetition. See [the project idea](docs/idea.md).

## Playing

Every item in the game starts out locked. You can move a locked item around in your inventory, but to place it, use it, craft with it or put it in a chest, you first type its Japanese name, the name it has in the Japanese version of Minecraft. A correct answer unlocks every stack of that item for a while. Each correct answer makes the time longer, from 10 minutes up to a month, until the item is mastered and never locks again. A wrong answer costs you part of the item.

### What you need

- Minecraft: Java Edition 26.3
- [Fabric Loader](https://fabricmc.net/use/) 0.20.1 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft 26.3

### Installing

1. Install Fabric Loader for Minecraft 26.3 with the [Fabric installer](https://fabricmc.net/use/).
2. Download Fabric API for Minecraft 26.3.
3. Download `oboecraft-<version>.jar` from the [latest release](https://github.com/willdalh/oboecraft/releases/latest).
4. Put both jars in the `mods` folder of your Minecraft folder, then start the game with the Fabric profile in the launcher.

On a server, install Fabric Loader, Fabric API and Oboecraft on the server too. Every player needs the same version of Oboecraft as the server, and players without it can't join.

Your progress in singleplayer is shared by all your worlds, so a new world doesn't start over. Each server keeps its own progress for each player.

### How to play

The mod is made for playing with the game in Japanese: choose 日本語 under Options → Language. Typing in Japanese with your keyboard's Japanese input works too.

- A locked item's name is hidden as `???`. Using it opens the answer menu. You can also right-click a locked item in your inventory to open it.
- Type the item's Japanese name in kanji, kana or romaji, or a mix, and press Enter. For an oak log, `オークの原木`, `おーくのげんぼく` and `ookunogenboku` are all correct. Spaces and the particle の are optional.
- The first time you meet an item, the answer menu is a lesson. Press F9 to see the answer, then type it. Nothing is lost in a lesson.
- After that, you get two attempts. Two wrong answers, or Delete for "Don't know", is a fail: part of the item is removed from your inventory and the open container, the item goes down a level, and it can't be answered again for 5 minutes. A fail at level 1 removes half, and higher levels remove less. The answer is shown after a fail.
- A correct answer shows the name with its reading. Hovering over an unlocked item shows the time until it locks again, and its slot gets a marker when it is close.
- Press F7 for "Already known" to unlock an item you know forever, or F8 to ignore an item you don't want to learn. Ignored items are dropped and can't be picked up. Press J to open the word list, where you can put known, ignored and mastered items back to level 0.
- Esc closes the answer menu without losing anything, and the item stays locked.
- In creative and spectator mode nothing is locked, and your progress is kept.

All keys can be changed under Options → Controls, in the Oboecraft section.

## License

The code is under the [MIT license](LICENSE). The Japanese names come from Minecraft, and the readings in the name list are under CC BY-SA 4.0. See [`CREDITS.md`](CREDITS.md).

## Development

[AGENTS.md](AGENTS.md) has the commands to build, test and run the mod, and [`docs/`](docs/) describes how it works.
