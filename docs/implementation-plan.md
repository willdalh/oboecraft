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
- The client only displays: the answer menu, "???" names, timer bars and slot markers.
- The server sends each player their SRS states and pushes updates when they change.
- Locks are checked on both sides. The client checks first and opens the answer menu at once, so a placed block doesn't flicker back. The server checks too and has the final say, which stops modified clients.
- The client sends the typed answer, and the server replies with a pass or fail. The Japanese name is only sent to the client when needed: in lessons and after a fail.
- Time is real-world time from the server clock. The SRS state stores when the item locks next as a timestamp, and the item is locked when the current time is at or past it, so no timers need to run. Singleplayer uses the computer's clock, and changing it to cheat is left to the player.
- The core never reads a clock and takes the current time as an input. `fabric` reads it from one clock that game tests replace. Every message from the server carries the server's time, and the client works out the server's time from the latest one with a timer that ignores changes to the client's clock. See [Time](design/time.md).
- The client's locks are only active once the server has sent the player's SRS states for this connection, so a server without the mod, or the moment after joining, never locks anything on the client.
- Client and server must run the same network protocol version of the mod. The server disconnects players who don't have the mod or have another protocol version. The version is part of the id of a configuration payload that the client registers, and goes up whenever a payload or its meaning changes. See [Fabric adapter](architecture/fabric.md#networking).
- Minecraft sends placing and using to the server even when the client's lock check stopped them, so the server checks every action again and tells the client to open the answer menu whenever it stops one and no review is going. The client ignores that while an answer menu, or any screen other than the open menu's, is open.
- The client gives each review an id when it opens the answer menu, and every message about the review carries it, so a late message never acts on a newer review or answer menu. Milestone 6 checked this for every interaction: a review starts from where the stack is, and if the server reads another item there, the answer menu closes.

### Dependencies and settings

- Fabric API is the only required dependency.
- Settings are stored in the mod's own JSON files. Server settings live on the server and are sent to players. The clickable buttons setting is saved on each player's computer.
- Mod Menu is supported as an optional dependency. When installed, it opens the mod's own settings screen.

### Testing, CI and running

- The repository is [willdalh/oboecraft](https://github.com/willdalh/oboecraft) on GitHub.
- Core rules get plain unit tests. The `fabric` module uses Fabric game tests where they pay off, and Fabric client game tests for what only a client can show, like the answer menu.
- GitHub Actions runs on every PR and every push to `main`: the build, the core tests, the Fabric game tests on a headless server, and the client game tests in a virtual display. A PR with a failing check isn't merged. CI is set up in milestone 2, and the client game tests are added in 3b.
- `./gradlew runClient` starts the game with the mod, and `./gradlew runServer` starts a local dedicated server. Both commands are added to AGENTS.md in milestone 2.
- The build uses the Gradle wrapper and a Java 25 Gradle toolchain, so Gradle and Java 25 are downloaded automatically, whatever Java version is installed. Loom needs Gradle itself to run on Java 25, so the Gradle daemon has a Java 25 toolchain too (`gradle/gradle-daemon-jvm.properties`).

### Name list

- The Japanese names come from Minecraft's `ja_jp.json`. That file has no readings, and dedicated servers don't have it.
- The mod ships a reviewed name list file with each item's id, Japanese name and reading. This puts Mojang's names inside the mod, which is a small legal grey area. If that becomes a problem, the server can download the lang file at start instead, and only the code that loads the file changes.
- A script generates the readings, and an agent then reviews them. A Japanese text splitter (for example Sudachi) splits each name into words and guesses their readings. JMdict, the dictionary data behind Jisho, used as an offline file, checks each word. Words where the two disagree, or that are missing from JMdict, go on a list to check closely.
- The script is re-run for each new Minecraft version, and only new items need review.
- Readings are stored per kanji (原 = げん, 木 = ぼく), so answers can mix kanji and kana. Words that can't be split per kanji, like 今日 = きょう, are stored as one unit.
- Each Japanese name is stored as a list of name parts: one kanji with its reading, kanji read together with their reading, a run of kana, or the particle の, which is its own part so it can be optional. In the file, a part in kana is a string and any other part is an array of its text and its reading in hiragana: `["オーク", "の", ["原", "げん"], ["木", "ぼく"]]`. The Japanese name and its reading are the parts joined, so they can't disagree. Letters and digits, like TNT, are a part with a reading too. See [Items](design/items.md#name-parts) and [Name list file](architecture/name-list-file.md).

Decided in milestone 5, see [Name list tool](architecture/name-list-tool.md):

- The tool is Java, in a `tools` Gradle module that depends on `core`, so every entry it writes passes the mod's own checks. It runs with `./gradlew :tools:generateNameList`. The name list file format is in a `json` module, with only Gson besides `core`, which both the mod and the tool use.
- Which translation keys name stacks comes from the game itself: Minecraft's data generator reports each item's default name, and the data in the server jar gives names like the abandoned camp maps. The names the game's code gives stacks from their components (potions, tipped arrows, shields by color, the lodestone compass and the named player head) are a hand-kept list, checked by a game test.
- KANJIDIC2, also from EDRDG, splits each word's reading between its kanji, with rendaku and a small っ allowed. Kanji whose readings don't add up are read together.
- The tool keeps an entry's reviewed parts as long as its Japanese name is unchanged, so a corrected entry stays corrected, and only new and changed names are guessed. Every generated entry is reviewed, not only the ones with notes.
- Air is left out of the name list. Every other Minecraft item is in it, including spawn eggs and items only found in creative mode.

### Items

- One item is one Japanese name. Each potion effect is its own item (治癒のポーション, 暗視のポーション), and the same goes for splash and lingering potions and tipped arrows, around 150 names in total. Healing I and II share a name, so they share an SRS state.
- Enchanted books, music discs, goat horns and fireworks are one item each, since their name is the same for every variant.
- Items not in the name list are never locked. This covers other mods' items and new vanilla items until the name list is updated. They work normally, show their normal name and have no SRS state.
- A stack maps to its item through the translation key of its item name, ignoring custom names. The item id is one of the item's translation keys, and the name list groups keys that share a Japanese name, like the music discs. See [Items](design/items.md) and [ADR: Items are identified by translation key](decisions/20261106-items-identified-by-translation-key.md).
- The protected items are a fixed list of item ids in the core, not a setting.

### SRS state and reviews

See [SRS state](design/srs-state.md), [Lock check](design/lock-check.md) and [Reviews](design/reviews.md).

- An SRS state is leveled, known or ignored. A leveled state has the level, when it locks next, a demoted flag, the wrong attempts and when the ban ends. An item with no saved state is leveled at level 0.
- Every action the mod may stop goes through one lock check in the core. It takes the item, what the player tries to do, the SRS state, the time, whether locks are active and the settings, and returns allow, review, banned or deny. Picking up isn't an interaction, but the same lock check decides it, so ignored items are handled in the same place.
- A wrong attempt is saved in the SRS state. Closing the answer menu after a wrong attempt keeps it, so closing and reopening the menu can't be used to get unlimited attempts.
- A player has at most one review at a time. The server works out its trigger itself, from the open menu and the slot: inventory, container or crafting. The trigger stays in `fabric`, which turns it into counts for the core's punishment math. A crafting trigger notes the recipe's ingredients when the review starts, and the server refuses clicks in the open menu while a review is going.
- The answer menu opens on top of an open container's screen, and the container stays open on the server for the whole review. If it closes on the server, the review is cancelled.
- A pass only unlocks the item. The player repeats the interaction.
- When the client asks to start a review, it says where the stack is, never which item it is or what the trigger is. The server reads both from its own state. It starts the review only at the location where it last stopped the player's action itself, or, for an action the client stopped without the server seeing it, after the lock check stops the stack at that location again. So a modified client can't start a review, or pick its trigger, at a location whose stack isn't stopped.
- Name list entries can split and merge between Minecraft versions. A merged entry lists the other item id as a former id, and loading progress keeps the state that has the player review sooner. Translation keys whose Japanese text has format arguments, like the named player head, are added to the entry of the item they name. See [Items](design/items.md).
- A progress file from a newer version of the mod is never overwritten, and the player is disconnected with a message. A broken file is kept as `.broken`, and the player is told in chat.

### Interactions

Decided in milestone 6, see [Lock check](design/lock-check.md#what-the-player-tries-to-do) and [Fabric adapter](architecture/fabric.md#the-lock-check-entry-point):

- The core lock check takes the interaction as an input, and allows dropping unless the drop setting, `Settings.dropCountsAsInteraction`, is on. It is off by default.
- An action is checked as the stacks it would interact with, each with its interaction and where it is. The first stack the lock check stops stops the whole action, and the answer menu opens for that stack.
- Using a stack on an entity is checked before the entity reacts, since the game doesn't tell apart an entity that uses the held item from one that doesn't. So right-clicking a villager or a boat with a locked item in hand opens the answer menu. Checking after the entity reacted would let a cow eat locked wheat.
- Attacking only counts for a stack with Minecraft's weapon component, and mining only for one with the tool component, so holding a locked block never stops a hit or breaking a block.
- A menu click is checked before Minecraft carries it out, from the slot and the kind of click. What a slot does is worked out from its class, its container and the menu's class, so a menu from another mod counts as a container. A shift-click in the inventory is checked where the menu's code moves the stack, in `moveItemStackTo`.
- Using a stack in the air is only checked for items whose use does something.
- During a review, the server refuses every packet that moves stacks in the open menu or out of the inventory.
- Ingredients are the input slots of menus that make their result from them, like the crafting grid and the stonecutter. A furnace's input and output are a container's slots, like its output in the trigger.
- The recipe book and choosing a trade move stacks themselves, and the server skips a locked stack there and opens the answer menu for it.
- A review starts from where the stack is: a slot of the inventory, a slot of the open menu, or the stack carried in it. The network protocol version went to 2.
- The answer menu replaces an open menu's screen without closing the menu, and skips the `removed` call that would empty the menu's result slot on the client. It doesn't draw the menu's screen behind it.

### Display

Decided in milestone 7, see [Display](design/display.md):

- Hidden names replace displayed tooltip titles, nested names in container and bundle previews, and the selected-item label above the hotbar. Other tooltip details and names elsewhere, including search indexes, stay as Minecraft shows them.
- The hover bar shows the remaining fraction of the current level's interval, with a translated time label rounded up to seconds and limited to the two largest nonzero units. Mastered items have no timer.
- Close to locking means the final 10% of the interval, including the boundary. The bar turns amber and a small amber marker appears at the top left of slots, including the hotbar. Both disappear at expiry, when the name becomes hidden.
- Displays use the current connection's synced progress and estimated server time, with the same game-mode and connection guards as interactions. Displays and interactions read settings from the same `Locks` instance, which milestone 12 supplies with synced settings.

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

Decided in milestone 4, see [Answers](design/answers.md):

- The answer is matched against the name's parts, trying every way the romaji can be read, instead of turning the answer into kana first. So `onna` passes for おんな, and a part written as it is, like TNT, is never mistaken for romaji.
- A long e can also be written with い, like `eiga` and `eega` for えいが, since えい is said as a long e.
- A macron or circumflex (`ō`, `ô`) and a dash (`o-ku`) write a long vowel. The dash is ー, as when typing on a Japanese keyboard.
- Full-width and half-width letters and kana count as the same.
- A long vowel is only matched inside a name part. Vowels in different parts are said separately, so 小売 (こ, うり) fails as `koori`, which is 氷.
- Hepburn's `m` for ん before b, p and m (`tempura`) isn't accepted, since a Japanese keyboard doesn't type it.

Since 26.3, Minecraft's window library has moved from GLFW to SDL3, and the keyboard's kanji conversion (IME) works on all operating systems. The answer menu uses Minecraft's normal text box to get it. Minecraft hands every key to the screen without checking the conversion, so the answer menu ignores Enter while the conversion has text that isn't confirmed. A client game test checks this with a simulated conversion. A real IME on each operating system is only checked by hand.

### Lessons and word list

- Level 0 reveals the name after Show answer, Don't know or a wrong answer, without ending the lesson or changing progress.
- Known and ignored states have no level. Revert is allowed only for them and mastered items, and resets to level 0.
- Progress format 2 is necessary for known and ignored states: format 1 readers reject unknown kinds as corrupt, so adding kinds without increasing the version would risk replacing progress after a downgrade.
- Word list and binding behavior are described in [Word list](design/word-list.md) and [Reviews](design/reviews.md).

### Punishment

Decided in milestone 9, see [Punishment](design/punishment.md):

- `percentageFactor` is in whole percentage points, and every removed count is worked out in integer arithmetic and rounded up, so a share like 15 of 50 is never rounded to 16 by a floating-point error.
- A crafting trigger's recipe is noted from the menu's input slots when the review starts: each stack in an input slot is one use of its item. This matches how the crafting grid, stonecutter and smithing table use their inputs. A loom's pattern and an anvil's extra repair material count as one use each.
- Ingredients that aren't items, and protected ingredients, count towards the recipe's total but are never removed, so the other ingredients only lose their own part.
- The current view of an inventory trigger includes the inventory's crafting grid and the carried stack. Results, including a trade's result, are never part of it. A punishment takes from the menu's own slots first, then the inventory, then the carried stack.
- After a fail, the answer menu stays open with the Japanese name and reading until the player closes it, and the review is already over on the server.
- A banned item shows how long its ban has left above the hotbar. Only the client shows it, from its own state: the server stops the action without a message.
- The progress format went to version 3 for the new fields. A format 2 reader would ignore them and save the file without them, losing every demotion, ban and wrong attempt after a downgrade, so an older mod must refuse the file instead. The network protocol went to 4.

### Notification

Decided in milestone 10, see [Display](design/display.md#notification):

- The notification is only the Japanese name, as written in the name list with its kanji, in plain white text with Minecraft's text shadow, centered at the top of the screen at twice the normal text size, or smaller when the name doesn't fit. It has no reading, icon, box or background. It goes below any boss bars.
- It is shown for 3 seconds of real time and fades out in the last second. A new pass replaces it. It is drawn after the screen, so a menu's screen doesn't dim it, and hidden with the HUD and while an answer menu is open.
- Only a pass shows it, also when the answer menu was closed before the pass arrived. After a fail the answer menu already shows the name and reading.

### Game modes and renaming

Decided in milestone 11, see [Reviews](design/reviews.md#game-modes) and [Items](design/items.md#renaming):

- Changing to creative or spectator mode cancels a review that is going, like closing the answer menu, so nothing is punished and the SRS state, with a saved wrong attempt, is kept. Changing between survival and adventure mode keeps it.
- A stack the server stopped just before the change can still start its review, since the server already decided the action needed one.
- The anvil leaves the name of every item except the name tag as it is. It neither renames it nor takes its custom name away, and charges nothing for naming. Repairing and combining enchantments are unchanged. Stacks that aren't items, like other mods' items, can't be renamed either. The rule is in core `Renaming`, and the anvil's name field only takes typing for a name tag.

### Progress

See [ADR: Progress saved as versioned JSON files](decisions/20261105-progress-save-format.md).

- Singleplayer: one progress file per Minecraft account, in the game folder outside the worlds.
- Dedicated server: one file per player, in the server's world folder.
- Open to LAN: the host keeps their shared progress, and guests' progress is saved in that world's folder.
- The files are JSON with a version number, so old saves can be upgraded.

### Release and licenses

- The mod is built to be published, with clean licenses and credits from the start.
- Server support is a priority from the start.
- The mod's code is under the MIT license ([`LICENSE`](../LICENSE)). The name list keeps its own terms, listed in the credits file [`CREDITS.md`](../CREDITS.md), which ships in the mod jar: Mojang for the Japanese names, and JMdict and KANJIDIC2 (EDRDG) and SudachiDict for the readings.
- JMdict and KANJIDIC2 are licensed CC BY-SA 4.0, so the readings and name parts in the name list are under CC BY-SA 4.0 too, separate from the MIT code. The Japanese names stay Mojang's. See [ADR: The name list's readings are under CC BY-SA 4.0](decisions/20261106-name-list-under-cc-by-sa.md) and [willdalh/oboecraft#20](https://github.com/willdalh/oboecraft/issues/20).

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

**Status:** done

Built on the design from milestone 3a. Built: the core rules for items, SRS states, the lock check, reviews and settings; a hand-written name list of 10 blocks; mapping stacks to items; progress files; sync of SRS states and the server's time to the client; blocking placing on both sides; the answer menu with Answer and Close; and the network protocol check. Game tests run the loop on a headless server, and a client game test runs it through a real client in singleplayer.

Where it differs from the 3a design, the docs were updated: each core type and setting only has what a milestone uses so far, and later milestones add values and fields; former ids wait for the first name list merge; the lock check takes the interaction once a rule depends on it (milestone 6); the answer menu only opens when no other screen is open, since replacing a container's screen breaks it, which milestone 6 has to solve to open it over containers; every review message carries a review id; the server tells the client to open the answer menu whenever it stops an action, since Minecraft sends the action even when the client stopped it; the payloads `ReviewAnswered` and `SubmitAnswer` replace `ReviewOutcome` and `ReviewAction`; and placing is checked in `ItemStack.useOn` instead of Fabric's `UseBlockCallback`, so opening a chest with a locked item in hand still works.

What later milestones should know: the Minecraft 26.3 methods for translation keys work, and some items, like shields with a base color and the lodestone compass, have their own keys (see [Items](design/items.md#translation-keys)). Client game tests need a display, see AGENTS.md. The server side of the loop is tested on Minecraft's headless game test server, which loads only the server's code, and the client side in singleplayer. A client connected to a real dedicated server over the network hasn't been run yet, since running a dedicated server needs Minecraft's EULA accepted, which is the user's choice. Each payload's encoding is tested on its own instead.

Punch a tree → get a locked oak log → try to place it → the answer menu opens → answer correctly → the oak log is unlocked and can be placed → it locks again after 10 minutes. Progress is saved, and the loop works on a dedicated server. A hand-written name list of about 10 items and simple answer checking (exact kana or kanji).

Parts:

1. Core levels, intervals and lock times.
2. Saving and loading progress.
3. Server/client sync of SRS states.
4. Blocking placing on both sides.
5. The answer menu and sending answers to the server.

### 4. Answer checking

**Status:** done ([#18](https://github.com/willdalh/oboecraft/pull/18))

Built: name parts in the core (`JapaneseName` and `NamePart`), the name list file format with readings per kanji, and full answer checking in the core: kanji and kana mixed per part, the optional particle の, romaji in both styles with du/di/wo, ん as n, nn or n', っ as a doubled consonant, long vowels written any way, and spaces, case, width and punctuation ignored. The 10 hand-written entries use the new format, with 松明 and 砂利 as one part each. Core unit tests cover every rule and example in [Answers](design/answers.md), and game tests check the loaded name list and a romaji answer through a review on the server.

Where it differs from the plan: a long e can be written with い or え, ō and o- write a long vowel, and full-width letters count, see [Answer checking](#answer-checking). The name list file has no separate field for the Japanese name, since it is the parts joined.

What later milestones should know: milestone 5's script writes `japaneseName` as name parts, with the particle の as its own part, readings in hiragana and kanji split per kanji only where each has its own reading. Where parts are split matters: a long vowel is only matched inside a part, so the script must not split one between two parts, and ヶ and ヵ need their reading given. The mod refuses a broken entry when it starts, naming the entry, but can't tell a wrong reading, see [Name list file](architecture/name-list-file.md#checks). The answer is still shown after a pass as the name and its reading, unchanged from 3b.

First the agent decides the name list file format (readings per kanji, units like 今日). Then full answer checking in core: kana and kanji mixes, optional の, romaji (both styles, du/di/wo), long vowels, punctuation, and wrong kanji failing. Mostly core code with many tests, using hand-written entries in that format.

### 5. Name list script

**Status:** done

Built: the name list tool in a new `tools` module (`./gradlew :tools:generateNameList`), described in [Name list tool](architecture/name-list-tool.md). It downloads Minecraft's `ja_jp.json` and server jar, runs the data generator to find every item name key, splits names with Sudachi, checks words with JMdict, splits readings per kanji with KANJIDIC2, and writes the name list and a review list. Every one of the 1,732 generated entries was reviewed and about 20 kinds of mistakes were corrected by hand, see [Reviewing](architecture/name-list-tool.md#reviewing). The name list now has every item of Minecraft 26.3, 1,738 entries. The name list file format moved from `fabric` into a new `json` module, so the mod and the tool read it with the same code. [`CREDITS.md`](../CREDITS.md) credits the sources and ships in the jar, and the readings and name parts are under CC BY-SA 4.0, see the [ADR](decisions/20261106-name-list-under-cc-by-sa.md). Game tests check that every stack the game names, and every item name its loot tables give, is in the name list.

Where it differs from the plan: the readings are split per kanji with KANJIDIC2, which the plan didn't name. The review was done in one PR, not several. The tool doesn't build the merge of two entries, since that needs former ids: it stops and says so, and the first update that merges entries builds them.

What later milestones should know: every vanilla item is now in the name list, so a test that needs a stack that isn't uses a stack named like another mod's item (`OtherModStacks` in the game tests). Some items share an entry because they share a name, like the brick item and the bricks block (レンガ) and all smithing templates (鍛冶型). After a Minecraft update, run the tool, review every generated entry and check `ItemNameKeys.CODE_NAMES`. Changes to the name list stay under CC BY-SA 4.0.

A tool that reads `ja_jp.json`, splits the names with Sudachi, checks them with JMdict, and writes the name list and a list of words to check closely. Then an agent reviews all ~1,500 readings, which can be several PRs, for example by item group. This milestone also settles the JMdict CC BY-SA question and adds the credits.

### 6. All interactions

**Status:** done

Built: the core `Interaction` and the drop setting, with rule 3 of the lock check; a check for every interaction on both sides (placing, using and eating, putting on armor, ammo, using on an entity, attacking with a weapon, a spear's stab, mining with a tool, dropping, and every kind of click in a menu, including crafting results, fuel, trades and armor slots); the recipe book and choosing a trade skip locked stacks; reviews started from a slot of the inventory or the open menu or the carried stack; the three triggers; starting a review only where the server stopped the action or the lock check stops the stack again; the answer menu over an open menu's screen; refusing clicks during a review; and cancelling a review when its menu closes on the server. Game tests cover each interaction on the server, what each slot of Minecraft's menus does, and the recipe book. A client game test opens the answer menu over a chest and over the inventory's crafting grid and checks that the menu stays open and its result slot is kept.

Where it differs from the plan: the recipe's ingredients for a crafting trigger aren't noted yet. Nothing reads them before milestone 9, which removes them, so milestone 9 notes them. Using on an entity, the weapon and tool rules, and how clicks are checked are new decisions, see [Interactions](#interactions). The answer menu doesn't draw the menu's screen behind it.

What later milestones should know: every hook goes through `Locks.firstStopped` and then `ServerLockHooks.stops` or `ClientReviews.stops`, which treat every decision that isn't Allow as Review. Milestones 8 and 9 add the decision to `StoppedStack` and show Deny and Banned there. Milestone 8's right-click in the inventory can start a review with `StackLocation.MenuSlot`, which the server already accepts. Milestone 9 gets the trigger from `ServerReviews`. Game tests can't change the settings, so the drop setting turned on is only covered by core tests until milestone 12. Not tested in a client: the client's hooks for using, attacking, mining and dropping, which share their rules with the server's, a spear's stab, and choosing a trade. A client connected to a dedicated server over the network still hasn't been run.

### 7. Display

**Status:** done

Built: `???` names in hover tooltips and above the hotbar, a draining hover bar with translated remaining time, and an amber slot marker in the final tenth of the interval. Names elsewhere and other tooltip information stay intact, including bundle previews. Displays follow the client's synced progress and estimated server time, and are off in creative/spectator mode, before progress sync, and for untracked stacks. Core tests cover timer boundaries and custom intervals; a client game test checks the real tooltip, HUD and decoration hooks across sync, expiry and game-mode changes and saves inventory screenshots.

Where it differs from the plan: the timer also has a time label. The plan left the marker threshold open; it is the final 10% of the interval, see [Display](#display). No new payloads or saved fields were needed.

What later milestones should know: `ItemDisplay` uses the shared `Locks` service and explicit core `DisplayRules` for Hidden, Timed and Normal states. Milestone 8 extends those rules for Known and Ignored; milestone 12 supplies one settings source for both interactions and displays. Tooltip masking is scoped to drawing, and raw tooltip getters retain their vanilla contract. Each GUI frame captures progress and time once, and slot markers do not build timers. The display is tested in a real singleplayer client; a client connected to a dedicated server and compatibility with other mods' tooltip renderers have not been checked.

### 8. Lessons, known and ignored items, word list

**Status:** done

Built: lesson answer reveal and unlimited unpunished attempts at level 0; Already known and Ignore; dropping ignored loose inventory stacks and blocking their interactions and pickups; inventory right-click reviews; Controls bindings; and a paginated word list with separate known, ignored and mastered tabs and server-validated Revert.

Differences: Don't know is available in lessons now, with higher-level fails left to milestone 9. The word list defaults to J (O is Minecraft's Friends key). Already known, Ignore and Show answer default to F7, F8 and F9 instead of Ctrl shortcuts, because vanilla stores single-key bindings and reports Ctrl+S as conflicting with walking backwards. Worn armor and container contents stay in use when ignoring an item.

Later milestones: protocol 3 carries tagged states and lesson results. Progress format 2 reads format 1 unchanged, but prevents older mods from treating known/ignored states as corrupt and overwriting progress. Settings still use defaults until milestone 12. See [Word list](design/word-list.md).

Lessons at level 0 (show the answer, no punishment), the Already known and Ignore actions, ignored items dropped and blocked, and the word list screen with Revert.

### 9. Punishment

**Status:** done

Built: two attempts with a wrong attempt saved in the SRS state, Don't know as a fail from level 1, and fails with punishment in the core (`SrsRules.fail`, `ReviewRules.dontKnow`, and `core.punishment` with the removal rate, removal counts, recipes and protected items); the demotion, ban length and `percentageFactor` settings; rule 8 of the lock check with Banned; the recipe noted for a crafting trigger; removing from the current view of each trigger on the server; the ban message on the client; and the answer menu showing the answer after a fail. Core tests cover every rule and example from [Punishment](design/punishment.md), and game tests run a fail through each trigger, a protected item, a ban and the pass after it. A client game test fails a review with Don't know in a real client, checks the removed stack and that a banned item neither opens the answer menu nor is placed.

Where it differs from the plan: the plan didn't say how a recipe's parts are counted, how rounding is done, or what the answer menu shows after a fail, see [Punishment](#punishment). Protected and other mods' ingredients keep the other ingredients' parts unchanged.

What later milestones should know: progress format 3 and protocol 4 carry the new state fields, and protocol 4 the attempts left. Milestone 10 can show the notification after a fail too, where the answer menu shows the answer now. Milestone 11's creative mode needs nothing new for bans: the lock check allows everything when locks aren't active. Milestone 12 reads the ban length, `percentageFactor` and each level's demotion into `Settings`, whose records already check them. A fail over a container of another mod, and the ban message on a dedicated server over the network, haven't been run.

The removal math in core, removing from the current view, removing crafting ingredients, protected items, demotion, and bans.

### 10. Notification

**Status:** done

Built: `PassNotification` on the client, which shows the Japanese name from the server's pass at the top of the screen for 3 seconds of real time, with a fade in the last second, and replaces the text above the hotbar. Core `NotificationTiming` holds the timing, with unit tests. The client game tests check that a pass shows オークの原木 below a boss bar, that its text is drawn, also at full brightness over the inventory screen, and that it goes away.

Where it differs from the plan: the plan left the look open. It is only the name as plain text, see [Notification](#notification). The reading is no longer shown after a pass, though `ReviewAnswered` still carries it. No payloads or saved fields changed.

What later milestones should know: the notification's length and look are fixed, not settings. It is drawn by `GuiMixin`, not as a Fabric HUD element, since a HUD element is drawn under screens. A notification next to other mods' HUD elements has not been checked.

Show the notification at the top of the screen after a pass, in place of the text above the hotbar. The spoken reading from `idea.md` is not part of this plan, see [willdalh/oboecraft#25](https://github.com/willdalh/oboecraft/issues/25).

### 11. Remaining rules

**Status:** done

Built: changing to creative or spectator mode cancels a review that is going, and the anvil only renames name tags, on both sides, with core `Renaming`. Creative mode already allowed every action and kept the SRS state since milestone 3b, through the lock check's rule 2, and displays are off there since milestone 7. Core tests cover `Renaming`. Game tests cover the game mode changes and the anvil's results for a name tag, a sword, a sword with a custom name and a repair. A client game test checks the anvil's name field for a sword and a name tag.

Where it differs from the plan: the plan only named creative mode. Spectator mode has no locks either, so it cancels a review too. Taking a custom name away with an anvil counts as renaming, and other mods' items can't be renamed, see [Game modes and renaming](#game-modes-and-renaming).

What later milestones should know: no payloads, saved fields or settings changed. The anvil's name field still shows the input's real name, also for a locked item, like other names outside tooltips and the hotbar label.

Creative mode (no locks, SRS state kept), and anvil renaming removed for everything except name tags.

### 12. Settings

**Status:** not started

Server settings JSON synced to players, the client setting for clickable buttons, and the optional Mod Menu screen.

### 13. Release

**Status:** not started

A release build. The credits file `CREDITS.md` exists since milestone 5, with the name list's license.

## Working on a milestone

A milestone is implemented from this plan, [`idea.md`](idea.md) and the docs, without further instructions. The agent working on it makes the open decisions itself.

- Read `idea.md` and the whole plan before starting, not only your milestone. Build only what your milestone needs, but avoid choices that later milestones would have to undo.
- Work from `main`. Open PRs are not part of the plan, so don't follow them or build on them.
- Make the decisions the plan leaves open. Add the ones later milestones need to know about to the Decisions section, and write an ADR when a decision meets the three criteria in AGENTS.md.
- If a decision in this plan turns out to be wrong, change it here in a PR that explains why, instead of working around it in the code.
- Show that the milestone works with tests where you can: core unit tests for the rules, and Fabric game tests for what happens in the game. The PR description says what was only checked by hand, or not at all.
- Follow the Docs section of [AGENTS.md](../AGENTS.md): when behavior, structure or terms change, update `docs/design/`, `docs/architecture/` and `docs/glossary.md` in the same PR.
- In the milestone's last PR, set its status to done and add a short outcome under it: what was built, where it differs from the plan, and what later milestones should know.
- Some choices are the user's: accepting the EULA and the JMdict license question in milestone 5. For these, open an issue that explains the choice, and carry on with the work that doesn't depend on it.

## References

- [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html): the game ships unobfuscated, Fabric uses Mojang's names, and Loom has a non-remapping plugin.
- [Minecraft: Java Edition 26.3](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3): the version the mod targets.
- [チュートリアル:日本語入力](https://ja.minecraft.wiki/w/チュートリアル:日本語入力): Japanese input (IME) works on all operating systems since 26.3.
