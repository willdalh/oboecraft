# Core

The `core` module holds the game rules as plain Java, in `io.github.willdalh.oboecraft.core`. This page describes its parts and their names. What the rules do is in [`docs/design/`](../design/README.md).

The parts were designed in milestone 3a and built in milestone 3b. Each type has only what the milestones so far use. Later milestones add values, fields and rules to these types, as noted below.

## Rules for the core

- **Simple inputs, decisions out.** The core takes item ids, SRS states, counts, timestamps and settings, and returns decisions and new states. It never holds a player, a stack or a slot.
- **Immutable values.** SRS states, progress, settings and name list entries are records. A rule returns a new state instead of changing the old one. `fabric` stores the result.
- **No clock.** The core never reads the current time. Every rule that needs it takes an `Instant now`, so tests pass any time they like. See [Time](../design/time.md).
- **No files.** The core doesn't read or write files or JSON. `fabric` loads the name list and progress files and hands the core plain values. This keeps the core free of libraries, and only the loading code changes if a file format changes.
- **Settings as a parameter.** Rules that depend on a setting take a `Settings` value. The core never reaches for a global.

## Packages

Package names are short for `io.github.willdalh.oboecraft.core.item` and so on. The `Oboecraft` class, with the mod id, is in `core`.

| Package | Contents | Milestone |
|---|---|---|
| `core.item` | `ItemId`, `NameListEntry`, `NameList` | 3b |
| `core.srs` | `SrsState`, `Progress`, `SrsRules` | 3b |
| `core.lock` | `Interaction`, `LockDecision`, `LockCheck` | 3b |
| `core.review` | `ReviewRules`, `ReviewStart`, `ReviewResult`, `ReviewOutcome`, `AnswerChecker` | 3b |
| `core.settings` | `Settings`, `LevelSettings` | 3b |
| `core.punishment` | Removal rate, removal counts, protected items | 9 |

### Items

- `ItemId`: a record around the item id string, like `block.minecraft.oak_log`. Code passes `ItemId`, never a bare string, so it can't be mixed up with a translation key.
- `NameListEntry`: the item id, its translation keys, the Japanese name and the reading. It rejects an entry with no translation key, or a blank key, name or reading.
- `NameList`: all entries. Looks up an entry by translation key or by item id. It is built from a list of entries and rejects an item id or translation key that belongs to two entries.

The reading is a plain string until milestone 4 decides how readings are stored per kanji. Only `NameListEntry` and `AnswerChecker` change then.

Former ids, and moving states saved under a former id when progress is loaded (`Progress.withFormerIdsMerged(nameList)` in the design), are added with the first name list update that merges two entries. The hand-written name list has no merges, so 3b leaves them out. See [Items](../design/items.md#name-list-entries).

### SRS state

- `SrsState`: a sealed interface. So far it has one record, `Leveled`, with the level and the locks-at time. `Leveled.NEW` is level 0 with no locks-at time. Milestone 8 adds the `Known` and `Ignored` records, and milestone 9 adds the demoted flag, wrong attempts and banned-until time to `Leveled`, from [SRS state](../design/srs-state.md#leveled-state).
- `Progress`: one player's SRS states, a map from `ItemId` to `SrsState`. An item with no entry is `Leveled.NEW`, and storing `Leveled.NEW` removes the entry, so only items the player has interacted with are kept. It also keeps states for item ids that aren't in the current name list, so they survive a name list change. `with(item, state)` returns new progress with one state changed.
- `SrsRules`: `isLocked(state, now, settings)` and `pass(state, now, settings)`. Milestone 9 adds `fail`. Known, ignored and revert are plain new states and need no rule.

### Lock check

- `Interaction`: one value per interaction in [What the player tries to do](../design/lock-check.md#what-the-player-tries-to-do). So far only `PLACE`. Milestone 6 adds the rest.
- `LockDecision`: `ALLOW` or `REVIEW`. Milestone 8 adds `DENY` and milestone 9 adds `BANNED`.
- `LockCheck.check(item, interaction, state, now, locksActive, settings)`: the [rules](../design/lock-check.md#rules) in order. The item is an `Optional<ItemId>`, empty when the stack isn't in the name list. `locksActive` is false in creative and spectator mode, which `fabric` works out from the game mode, and on the client before the server has sent the player's SRS states.

Milestone 8 adds `LockCheck.checkPickUp(item, state, locksActive)`, the same rules for picking up, which isn't an interaction, so ignored items and creative mode are decided once.

`LockCheck` is the only place that decides whether an action is allowed. Every caller in `fabric`, on both sides, goes through it.

### Reviews

- `ReviewRules.start(state, now, settings)`: whether a review can start, as a `ReviewStart`: `Started` with the level, or `NotLocked`. It decides "not locked" with `SrsRules.isLocked`, so it agrees with the lock check when settings change the top level. Later milestones add whether it is a lesson and the attempts left to `Started`, and a `Banned` result.
- `ReviewRules.isAttempt(typed)`: whether typed text counts as an attempt. Blank text doesn't.
- `ReviewRules.answer(entry, state, typed, now, settings)`: the result of an answer as a `ReviewResult`, which holds the `ReviewOutcome` (`PASS` or `WRONG` so far) and the new state. Milestone 9 adds the fail outcome, `dontKnow`, and the level before the review, which punishment needs.
- `AnswerChecker.isCorrect(entry, typed)`: whether a typed answer is correct. In 3b it matches the Japanese name or the reading exactly, ignoring spaces at the start and end. Milestone 4 replaces the rules, not the signature.

The trigger isn't in the core. It is a Minecraft concept, about menus and slots, so `fabric` keeps it and turns it into counts for punishment.

### Settings

- `Settings`: a record with the [server settings](../design/settings.md#server-settings). `Settings.DEFAULTS` holds the defaults, and the record checks its values when it is built. `topLevel()` is the number of levels plus one, and `interval(level)` is a level's interval.
- `LevelSettings`: one level's interval. Milestone 9 adds the level's demotion.

Each setting is added to `Settings` by the milestone that first uses it: the levels in 3b, the drop setting in 6, and the ban length and `percentageFactor` in 9.

Client settings, like clickable buttons, only change what the client shows, so they live in `fabric`.

## Tests

Every rule gets JUnit tests in `core/src/test`, with hand-written name list entries and fixed `Instant` values.
