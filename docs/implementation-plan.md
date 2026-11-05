# Implementation plan

This plan describes how Oboecraft is built: the decisions behind the code and the order of work. The [confirmed gameplay rules](design/README.md) record later decisions, including inventory movement, crafting and attempt resets; they replace conflicting rules in the frozen [`idea.md`](idea.md). The decisions below are settled.

The plan is a living document. Each milestone's status is updated as its PRs merge.

## Decisions

### Stack

See [ADR: Fabric, Java 25 and only the latest Minecraft version](decisions/20261105-stack.md).

- Mod loader: Fabric.
- Edition and version policy: Minecraft: Java Edition, targeting the newest stable release supported by the selected mod loader.
- Minecraft version: only the latest, 26.3 (released 2026-09-15). The mod moves to each new version once Fabric supports it. 26.4 is due in Q4 2026.
- Language: Java 25, which Minecraft 26.3 needs.
- Since 26.1, Minecraft ships unobfuscated, and Fabric uses Mojang's names only. The build uses Fabric Loom's non-remapping plugin `net.fabricmc.fabric-loom` (Loom 1.15 or newer, Gradle 9.4 or newer). Dependencies use `implementation`, not `modImplementation`, and the mod is built with `jar`, not `remapJar`.

### Architecture

See the [architecture overview](architecture/overview.md) for the details.

- Mod id `oboecraft`, package `io.github.willdalh.oboecraft`.
- Two Gradle modules: `core` (`...oboecraft.core`), plain Java with the game rules and no Minecraft, and `fabric` (`...oboecraft.fabric`), the adapter, which depends on `core`. The compiler enforces the boundary.
- The core is thin. It takes simple inputs like item ids, counts and timestamps and returns decisions. For example: level 3, 40 diamonds and 60 sticks, recipe 3:2, so remove 12 diamonds and 18 sticks. `fabric` gathers the inputs from the game and does the actual removing.

### Server and client

- The mod is installed on both the server and the client. Support dedicated servers and worlds hosted by a friend, including Open to LAN. Singleplayer runs on Minecraft's built-in server, so it uses the same code.
- The server decides everything that matters: SRS states, answer checking, attempts and their reset, levels, removing and bans.
- The client only displays: the answer menu, "???" names, timer bars, slot markers and the spoken reading.
- The server sends each player their SRS states and pushes updates when they change.
- Locks are checked on both sides. The client checks first and opens the answer menu at once, so a placed block doesn't flicker back. The server checks too and has the final say, which stops modified clients.
- The client sends the typed answer, and the server replies with a pass, a wrong answer with the remaining attempts, or a fail. A first wrong answer is not a fail. The Japanese name is only sent to the client when needed: in lessons and after a fail.
- Time is real-world time from the server clock. The SRS state stores when the item locks next as a timestamp, and the item is locked when the current time is at or past it, so no timers need to run. Singleplayer uses the computer's clock, and changing it to cheat is left to the player.

### Dependencies and settings

- Fabric API is the only required dependency.
- Settings are stored in the mod's own JSON files. Server settings live on the server and are sent to players. The clickable buttons setting is saved on each player's computer.
- Mod Menu is supported as an optional dependency. When installed, it opens the mod's own settings screen.

### Testing, CI and running

- The repository is [willdalh/oboecraft](https://github.com/willdalh/oboecraft) on GitHub.
- Core rules get plain unit tests. The `fabric` module uses Fabric game tests where they pay off.
- Build playable versions when player feedback can resolve uncertain gameplay. Use those sessions to check the [gameplay examples](design/README.md#examples-for-early-playtesting) and adjust the design before adding more features.
- GitHub Actions runs on every PR: the build, the core tests and the Fabric game tests on a headless server. A PR with a failing check isn't merged. CI is set up in milestone 2.
- `./gradlew runClient` starts the game with the mod, and `./gradlew runServer` starts a local dedicated server. Both commands are added to AGENTS.md in milestone 2.
- JDK 25 must be configured before starting Gradle. The wrapper downloads Gradle 9.6.0, and both modules compile with a Java 25 toolchain. Windows x64 developers can use `scripts/Use-Java25.ps1` to download a verified workspace-local JDK and select it for the current PowerShell session. See [Development](development.md) for all commands.

### Name list

- The Japanese names come from Minecraft's `ja_jp.json`. That file has no readings, and dedicated servers don't have it.
- The mod ships a reviewed name list file with each item's id, Japanese name and reading. This puts Mojang's names inside the mod, which is a small legal grey area. If that becomes a problem, the server can download the lang file at start instead, and only the code that loads the file changes.
- A script generates the readings, and they are then reviewed by hand. A Japanese text splitter (for example Sudachi) splits each name into words and guesses their readings. JMdict, the dictionary data behind Jisho, used as an offline file, checks each word. Words where the two disagree, or that are missing from JMdict, go on a "check by hand" list.
- The script is re-run for each new Minecraft version, and only new items need review.
- Readings are stored per kanji (原 = げん, 木 = ぼく), so answers can mix kanji and kana. Words that can't be split per kanji, like 今日 = きょう, are stored as one unit.

### Items

- One item is one Japanese name. Each potion effect is its own item (治癒のポーション, 暗視のポーション), and the same goes for splash and lingering potions and tipped arrows, around 150 names in total. Healing I and II share a name, so they share an SRS state.
- Normal, damaged and enchanted diamond swords share one SRS state per player; damage and enchantments do not create separate items for learning.
- Enchanted books, music discs, goat horns and fireworks are one item each, since their name is the same for every variant.
- Items not in the name list are never locked. This covers other mods' items and new vanilla items until the name list is updated. They work normally, show their normal name and have no SRS state.

### Answer checking

Each part of the name may be written as its kanji, in kana (hiragana or katakana) or in romaji. The general rule: the same sound passes, a different sound fails. Kanji must be the ones in the Japanese name, and kana must be the right ones.

Examples for オークの原木 (reading おーくのげんぼく):

| Answer | Result | Why |
|---|---|---|
| `オークの原木` | Pass | Exact |
| `おーくのげんぼく` | Pass | All hiragana |
| `オークの原ぼく` | Pass | Kanji and kana mixed, per kanji |
| `オーク原木` | Pass | の is optional |
| `ooku no genboku` | Pass | Romaji |
| `おおくのげんぼく`, `おうく…`, `ouku…` | Pass | Long vowel written another way |
| `gen'boku`, `genboku`, `gennboku` | Pass | ん written as n', n or nn |
| `オークの減木` | Fail | Wrong kanji with the same reading, which would teach the wrong kanji |

More rules:

- Both romaji styles pass: shi/si, tsu/tu, fu/hu, ji/zi, sha/sya.
- In romaji, ん is written n, nn or n'. Before a vowel or y, a single n starts the next syllable, as when typing on a Japanese keyboard: `kinen` is きねん, and きんえん is `kinnen` or `kin'en`.
- Spaces, case and punctuation like ・ are ignored.
- A missing dakuten fails: `はけつ` for バケツ.
- Big kana for small fails: `きやく` for きゃく.
- A missing small っ fails.
- Different kana with the same sound fail: ず/づ, じ/ぢ and を/お. For example, `みかずき` for みかづき and `はなじ` for はなぢ fail.
- Romaji tells these kana apart too: づ is `du`, ぢ is `di` and を is `wo`, as when typing on a Japanese keyboard. Hepburn `zu`, `ji` and `o` fail for those kana: `mikazuki` fails for みかづき, and `mikaduki` passes.

Since 26.3, Minecraft's window library has moved from GLFW to SDL3, and the keyboard's kanji conversion (IME) works on all operating systems. The answer menu uses Minecraft's normal text box to get it. Test early that pressing Enter to confirm a conversion doesn't submit the answer.

### Voice

- The audio for each reading is pre-generated with VOICEVOX from the reviewed reading and shipped as sound files, around 1,500 clips and 10 MB. It works offline and sounds the same for everyone.
- VOICEVOX requires credit, in the form "VOICEVOX:<voice name>". Some voices are for non-commercial use only, so the chosen voice must allow use in a published mod. The voice is picked in milestone 10.

### Progress

See [ADR: Progress saved as versioned JSON files](decisions/20261105-progress-save-format.md).

- Singleplayer: one progress file per Minecraft account, in the game folder outside the worlds.
- Dedicated server: one file per player, in the server's world folder.
- Open to LAN: the host keeps their shared progress, and guests' progress is saved in that world's folder.
- The files are JSON with a version number, so old saves can be upgraded.

### Release and licenses

- The mod is built to be published, for example on Modrinth, with clean licenses and credits from the start.
- Server support is a priority from the start.
- The mod's code is under the MIT license ([`LICENSE`](../LICENSE)). The name list and voice files keep their own terms, listed in a credits file: Mojang for the Japanese names, JMdict for the readings and VOICEVOX for the voice.
- JMdict is licensed CC BY-SA 4.0, so the readings in the name list may need to be under CC BY-SA too. This is checked in milestone 5, when the script is written.

## Milestones

A milestone is something you can play and see working. A PR is one topic, small enough to review in about 15 minutes. The PR lists below are suggestions. Split further if a PR grows.

### 1. Docs foundation

**Status:** done ([#7](https://github.com/willdalh/oboecraft/pull/7))

Added the [architecture overview](architecture/overview.md), the [stack](decisions/20261105-stack.md) and [progress save format](decisions/20261105-progress-save-format.md) ADRs, the name list term in the [glossary](glossary.md), and the MIT license.

### 2. Project setup

**Status:** in progress; implementation and validation are on `chore/project-setup`, awaiting review and merge.

Gradle wrapper, Java 25 toolchain, the two modules `core` and `fabric`, and non-remapping Fabric Loom. An empty mod that starts with `./gradlew runClient` and on a dedicated server with `./gradlew runServer`. One sample core test and one Fabric game test. GitHub Actions runs the build and tests on every PR. AGENTS.md gets the run and test commands, and `docs/architecture/` is updated to match the real code.

### 3. Playable loop

**Status:** not started

Punch a tree → pick up a locked oak log from the ground without a review → try to place it → the answer menu opens → answer correctly → repeat the placing action → the oak log is placed → it locks again after 10 minutes. Progress is saved, and the loop works on a dedicated server and in a world hosted by a friend. A hand-written name list of about 10 items and simple answer checking (exact kana or kanji). Use this playable version for early feedback.

PRs:

1. Core levels, intervals and lock times.
2. Saving and loading progress.
3. Server/client sync of SRS states.
4. Blocking placing on both sides.
5. The answer menu and sending answers to the server.

### 4. Answer checking

**Status:** not started

The first PR agrees on the name list file format (readings per kanji, units like 今日). Then full answer checking in core: kana and kanji mixes, optional の, romaji (both styles, du/di/wo), long vowels, punctuation, and wrong kanji failing. Mostly core code with many tests, using hand-written entries in the agreed format.

### 5. Name list script

**Status:** not started

A tool that reads `ja_jp.json`, splits the names with Sudachi, checks them with JMdict, and writes the name list and a "check by hand" list. Then a review of all ~1,500 readings, which can be several PRs, for example by item group. This milestone also settles the JMdict CC BY-SA question and adds the credits.

### 6. All interactions

**Status:** not started

Several PRs: inventory and container slot pickup before dragging; crafting ingredient movement and a separate review for a locked result; using, eating, attacking and mining; fuel and trades; the drop setting. Ground pickup stays free of reviews. A pass unlocks the item, and the player repeats the interrupted action. Follow the [confirmed gameplay rules](design/README.md).

### 7. Display

**Status:** not started

"???" names in the hover text and above the hotbar, the timer bar on hover, and the slot marker when an item is close to locking.

### 8. Lessons, known and ignored items, word list

**Status:** not started

Lessons at level 0 (show the answer, no punishment), the Already known and Ignore actions, ignored items dropped and blocked, and the word list screen with Revert.

### 9. Punishment

**Status:** not started

Several PRs: the removal math in core, removing from the current view (including both inventory and open chest), removing crafting ingredients, protected items, demotion, and bans. Add the two-attempt rule and the five-minute attempt reset for reviews that have not failed. Esc only closes the answer menu without consuming an attempt; remaining attempts survive closing and reopening. The reset window starts at the first wrong answer and continues while the menu is closed. Verify that reopening does not restore attempts or restart the window, and that the reset never undoes a fail.

### 10. Voice and notification

**Status:** not started

Pick the VOICEVOX voice and generate the audio (one PR). Play it and show the notification at the top of the screen (another PR).

### 11. Remaining rules

**Status:** not started

Creative mode (no locks, SRS state kept), and anvil renaming removed for everything except name tags.

### 12. Settings

**Status:** not started

Server settings JSON synced to players, the client setting for clickable buttons, and the optional Mod Menu screen.

### 13. Release

**Status:** not started

A credits file (Mojang names, JMdict, VOICEVOX), the Modrinth page and a release build.

## In every milestone

Follow the Docs section of [AGENTS.md](../AGENTS.md): when behavior, structure or terms change, update `docs/design/`, `docs/architecture/` and `docs/glossary.md` in the same PR, and write an ADR when a decision meets its three criteria. Update the milestone's status in this plan when its PRs are merged.

## Open questions

- Which VOICEVOX voice to use. Decided in milestone 10.

## References

- [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html): the game ships unobfuscated, Fabric uses Mojang's names, and Loom has a non-remapping plugin.
- [Minecraft: Java Edition 26.3](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3): the version the mod targets.
- [VOICEVOX terms of use](https://voicevox.hiroshiba.jp/term/): credit is required.
- [チュートリアル:日本語入力](https://ja.minecraft.wiki/w/チュートリアル:日本語入力): Japanese input (IME) works on all operating systems since 26.3.
