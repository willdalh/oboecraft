# Implementation plan

This plan describes how Oboecraft is built: the decisions behind the code and the order of work. [`idea.md`](idea.md) describes what the mod should do. The decisions below are settled. Agents add new ones as they work, see [Working on a milestone](#working-on-a-milestone).

Where this plan and `idea.md` disagree, this plan wins. Where `docs/design/` and `docs/architecture/` describe how something works, they win over both.

The plan is a living document. Each milestone's status is updated as its PRs merge.

## Decisions

### Stack

See [ADR: Fabric, Java 25 and only the latest Minecraft version](decisions/20261105-stack.md).

- Mod loader: Fabric.
- Minecraft version: only the latest, 26.3 (released 2026-09-15). The mod moves to each new version once Fabric supports it. 26.4 is due in Q4 2026.
- Language: Java 25, which Minecraft 26.3 needs.
- Since 26.1, Minecraft ships unobfuscated, and Fabric uses Mojang's names only. The build uses Fabric Loom's non-remapping plugin `net.fabricmc.fabric-loom` (Loom 1.15 or newer, Gradle 9.4 or newer). Dependencies use `implementation`, not `modImplementation`, and the mod is built with `jar`, not `remapJar`.

### Architecture

See the [architecture overview](architecture/overview.md) for the details.

- Mod id `oboecraft`, package `io.github.willdalh.oboecraft`.
- Two Gradle modules: `core` (`...oboecraft.core`), plain Java with the game rules and no Minecraft, and `fabric` (`...oboecraft.fabric`), the adapter, which depends on `core`. The compiler enforces the boundary.
- The core is thin. It takes simple inputs like item ids, counts and timestamps and returns decisions. For example: level 3, 40 diamonds and 60 sticks, recipe 3:2, so remove 12 diamonds and 18 sticks. `fabric` gathers the inputs from the game and does the actual removing.

### Server and client

- The mod is installed on both the server and the client. Singleplayer runs on Minecraft's built-in server, so it uses the same code.
- The server decides everything that matters: SRS states, answer checking, levels, removing and bans.
- The client only displays: the answer menu, "???" names, timer bars, slot markers and the spoken reading.
- The server sends each player their SRS states and pushes updates when they change.
- Locks are checked on both sides. The client checks first and opens the answer menu at once, so a placed block doesn't flicker back. The server checks too and has the final say, which stops modified clients.
- The client sends the typed answer, and the server replies with a pass or fail. The Japanese name is only sent to the client when needed: in lessons and after a fail.
- Time is real-world time from the server clock. The SRS state stores when the item locks next as a timestamp, and the item is locked when the current time is at or past it, so no timers need to run. Singleplayer uses the computer's clock, and changing it to cheat is left to the player.
- The core never reads a clock and takes the current time as an input. `fabric` reads it from one clock that game tests replace. The client adds the difference between the server's clock and its own, which the server sends with the SRS states. See [Time](design/time.md).
- Client and server must run the same network protocol version of the mod. The server disconnects players who don't have the mod or have another protocol version.

### Dependencies and settings

- Fabric API is the only required dependency.
- Settings are stored in the mod's own JSON files. Server settings live on the server and are sent to players. The clickable buttons setting is saved on each player's computer.
- Mod Menu is supported as an optional dependency. When installed, it opens the mod's own settings screen.

### Testing, CI and running

- The repository is [willdalh/oboecraft](https://github.com/willdalh/oboecraft) on GitHub.
- Core rules get plain unit tests. The `fabric` module uses Fabric game tests where they pay off.
- GitHub Actions runs on every PR and every push to `main`: the build, the core tests and the Fabric game tests on a headless server. A PR with a failing check isn't merged. CI is set up in milestone 2.
- `./gradlew runClient` starts the game with the mod, and `./gradlew runServer` starts a local dedicated server. Both commands are added to AGENTS.md in milestone 2.
- The build uses the Gradle wrapper and a Java 25 Gradle toolchain, so Gradle and Java 25 are downloaded automatically, whatever Java version is installed. Loom needs Gradle itself to run on Java 25, so the Gradle daemon has a Java 25 toolchain too (`gradle/gradle-daemon-jvm.properties`).

### Name list

- The Japanese names come from Minecraft's `ja_jp.json`. That file has no readings, and dedicated servers don't have it.
- The mod ships a reviewed name list file with each item's id, Japanese name and reading. This puts Mojang's names inside the mod, which is a small legal grey area. If that becomes a problem, the server can download the lang file at start instead, and only the code that loads the file changes.
- A script generates the readings, and an agent then reviews them. A Japanese text splitter (for example Sudachi) splits each name into words and guesses their readings. JMdict, the dictionary data behind Jisho, used as an offline file, checks each word. Words where the two disagree, or that are missing from JMdict, go on a list to check closely.
- The script is re-run for each new Minecraft version, and only new items need review.
- Readings are stored per kanji (原 = げん, 木 = ぼく), so answers can mix kanji and kana. Words that can't be split per kanji, like 今日 = きょう, are stored as one unit.

### Items

- One item is one Japanese name. Each potion effect is its own item (治癒のポーション, 暗視のポーション), and the same goes for splash and lingering potions and tipped arrows, around 150 names in total. Healing I and II share a name, so they share an SRS state.
- Enchanted books, music discs, goat horns and fireworks are one item each, since their name is the same for every variant.
- Items not in the name list are never locked. This covers other mods' items and new vanilla items until the name list is updated. They work normally, show their normal name and have no SRS state.
- A stack maps to its item through the translation key of its item name, ignoring custom names. The item id is one of the item's translation keys, and the name list groups keys that share a Japanese name, like the music discs. See [Items](design/items.md) and [ADR: Items are identified by translation key](decisions/20261106-items-identified-by-translation-key.md).
- The protected items are a fixed list of item ids in the core, not a setting.

### SRS state and reviews

See [SRS state](design/srs-state.md), [Lock check](design/lock-check.md) and [Reviews](design/reviews.md).

- An SRS state is leveled, known or ignored. A leveled state has the level, when it locks next, a demoted flag, the wrong attempts and when the ban ends. An item with no saved state is leveled at level 0.
- Every action the mod may stop goes through one lock check in the core. It takes the item, what the player tries to do, the SRS state, the time, whether locks are active and the settings, and returns allow, review, banned or deny. Picking up is checked too, so ignored items are handled in the same place.
- A wrong attempt is saved in the SRS state. Closing the answer menu after a wrong attempt keeps it, so closing and reopening the menu can't be used to get unlimited attempts.
- A player has at most one review at a time. The server remembers its trigger: inventory, container or crafting. The trigger stays in `fabric`, which turns it into counts for the core's punishment math.
- The answer menu opens on top of an open container's screen, and the container stays open on the server for the whole review. If it closes on the server, the review is cancelled.
- A pass only unlocks the item. The player repeats the interaction.
- When the client asks to start a review, it says where the stack is, never which item it is. The server reads the item from the stack.

### Settings

See [Settings](design/settings.md).

- Server settings are one immutable core `Settings` value, with the defaults in `Settings.DEFAULTS`. Core rules take it as a parameter. Until milestone 12, the defaults are the only values.
- The levels setting is a list with an interval and a demotion per level, so the number of levels can change. The top level is one more than the list's length.
- The client's lock check needs the server settings, so milestone 12 sends them to players. Client settings, like clickable buttons, live in `fabric`.

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

- The mod is built to be published, with clean licenses and credits from the start.
- Server support is a priority from the start.
- The mod's code is under the MIT license ([`LICENSE`](../LICENSE)). The name list and voice files keep their own terms, listed in a credits file: Mojang for the Japanese names, JMdict for the readings and VOICEVOX for the voice.
- JMdict is licensed CC BY-SA 4.0, so the readings in the name list may need to be under CC BY-SA too. This is checked in milestone 5, when the script is written.

## Milestones

Most milestones are something you can play and see working. The agent working on a milestone decides whether it is done as one PR or several.

### 1. Docs foundation

**Status:** done ([#7](https://github.com/willdalh/oboecraft/pull/7))

Added the [architecture overview](architecture/overview.md), the [stack](decisions/20261105-stack.md) and [progress save format](decisions/20261105-progress-save-format.md) ADRs, the name list term in the [glossary](glossary.md), and the MIT license.

### 2. Project setup

**Status:** done ([#11](https://github.com/willdalh/oboecraft/pull/11))

Gradle wrapper, Java 25 toolchain, the two modules `core` and `fabric`, and non-remapping Fabric Loom. An empty mod that starts with `./gradlew runClient` and on a dedicated server with `./gradlew runServer`. One sample core test and one Fabric game test. GitHub Actions runs the build and tests on every PR. AGENTS.md gets the run and test commands, and `docs/architecture/` is updated to match the real code.

### 3a. Foundations design

**Status:** done ([#15](https://github.com/willdalh/oboecraft/pull/15))

Designed the foundations in [`docs/design/`](design/README.md) (items, SRS state, lock check, reviews, settings and time) and in [`docs/architecture/`](architecture/overview.md) ([core](architecture/core.md), [Fabric adapter](architecture/fabric.md) and [progress files](architecture/progress-files.md)), with the [translation key ADR](decisions/20261106-items-identified-by-translation-key.md). The decisions later milestones need are under [Decisions](#decisions).

Where it goes beyond the plan: a wrong attempt is saved, so it survives closing the answer menu, and the number of levels and each level's demotion are one setting. The Minecraft methods for reading a stack's translation key weren't checked against 26.3 yet. 3b checks them. Class names in the architecture docs are a guide for 3b, not fixed.

Milestone 3b lays the foundations the later milestones build on. Before any code, this milestone describes them in `docs/design/` and `docs/architecture/`, with ADRs for decisions that meet the criteria. Design them with the whole plan in mind, even where 3b only needs a small part:

- How an item stack maps to its name list entry, since one item is one Japanese name (see [Items](#items)), not one Minecraft item id.
- The SRS state, which later also needs demotion, bans, and known and ignored items.
- The review, which later gets two attempts, the extra actions, lessons and punishment. Punishment needs to know what started the review, like the recipe being crafted or the open container.
- One lock check that every interaction and both sides use, so creative mode, ignored items and bans can change it in one place.
- Settings: values like the intervals are read from one place that holds the defaults, so milestone 12 only adds the file, the sync and the screens.
- Time comes from a clock that tests can control.

Until 3b is merged, these docs describe what 3b builds. 3b updates them where the code ends up different.

### 3b. Playable loop

**Status:** not started

Built on the design from milestone 3a.

Punch a tree → get a locked oak log → try to place it → the answer menu opens → answer correctly → the oak log is unlocked and can be placed → it locks again after 10 minutes. Progress is saved, and the loop works on a dedicated server. A hand-written name list of about 10 items and simple answer checking (exact kana or kanji).

Parts:

1. Core levels, intervals and lock times.
2. Saving and loading progress.
3. Server/client sync of SRS states.
4. Blocking placing on both sides.
5. The answer menu and sending answers to the server.

### 4. Answer checking

**Status:** not started

First the agent decides the name list file format (readings per kanji, units like 今日). Then full answer checking in core: kana and kanji mixes, optional の, romaji (both styles, du/di/wo), long vowels, punctuation, and wrong kanji failing. Mostly core code with many tests, using hand-written entries in that format.

### 5. Name list script

**Status:** not started

A tool that reads `ja_jp.json`, splits the names with Sudachi, checks them with JMdict, and writes the name list and a list of words to check closely. Then an agent reviews all ~1,500 readings, which can be several PRs, for example by item group. This milestone also settles the JMdict CC BY-SA question and adds the credits.

### 6. All interactions

**Status:** not started

Crafting; chests and other containers; using, eating, attacking and mining; fuel and trades; the drop setting.

### 7. Display

**Status:** not started

"???" names in the hover text and above the hotbar, the timer bar on hover, and the slot marker when an item is close to locking.

### 8. Lessons, known and ignored items, word list

**Status:** not started

Lessons at level 0 (show the answer, no punishment), the Already known and Ignore actions, ignored items dropped and blocked, and the word list screen with Revert.

### 9. Punishment

**Status:** not started

The removal math in core, removing from the current view, removing crafting ingredients, protected items, demotion, and bans.

### 10. Voice and notification

**Status:** not started

Pick the VOICEVOX voice and generate the audio. Play it and show the notification at the top of the screen.

### 11. Remaining rules

**Status:** not started

Creative mode (no locks, SRS state kept), and anvil renaming removed for everything except name tags.

### 12. Settings

**Status:** not started

Server settings JSON synced to players, the client setting for clickable buttons, and the optional Mod Menu screen.

### 13. Release

**Status:** not started

A credits file (Mojang names, JMdict, VOICEVOX) and a release build.

## Working on a milestone

A milestone is implemented from this plan, [`idea.md`](idea.md) and the docs, without further instructions. The agent working on it makes the open decisions itself.

- Read `idea.md` and the whole plan before starting, not only your milestone. Build only what your milestone needs, but avoid choices that later milestones would have to undo.
- Work from `main`. Open PRs are not part of the plan, so don't follow them or build on them.
- Make the decisions the plan leaves open. Add the ones later milestones need to know about to the Decisions section, and write an ADR when a decision meets the three criteria in AGENTS.md.
- If a decision in this plan turns out to be wrong, change it here in a PR that explains why, instead of working around it in the code.
- Show that the milestone works with tests where you can: core unit tests for the rules, and Fabric game tests for what happens in the game. The PR description says what was only checked by hand, or not at all.
- Follow the Docs section of [AGENTS.md](../AGENTS.md): when behavior, structure or terms change, update `docs/design/`, `docs/architecture/` and `docs/glossary.md` in the same PR.
- In the milestone's last PR, set its status to done and add a short outcome under it: what was built, where it differs from the plan, and what later milestones should know.
- Some choices are the user's: accepting the EULA, the JMdict license question in milestone 5 and the VOICEVOX voice in milestone 10. For these, open an issue that explains the choice, and carry on with the work that doesn't depend on it.

## Open questions

- Which VOICEVOX voice to use. Decided in milestone 10.

## References

- [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html): the game ships unobfuscated, Fabric uses Mojang's names, and Loom has a non-remapping plugin.
- [Minecraft: Java Edition 26.3](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3): the version the mod targets.
- [VOICEVOX terms of use](https://voicevox.hiroshiba.jp/term/): credit is required.
- [チュートリアル:日本語入力](https://ja.minecraft.wiki/w/チュートリアル:日本語入力): Japanese input (IME) works on all operating systems since 26.3.
