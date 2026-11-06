# Core

The `core` module holds the game rules as plain Java, in `io.github.willdalh.oboecraft.core`. This page describes its parts and their names. What the rules do is in [`docs/design/`](../design/README.md).

The parts were designed in milestone 3a. Until milestone 3b is merged, this page describes what 3b builds, and 3b updates it where the code ends up different.

## Rules for the core

- **Simple inputs, decisions out.** The core takes item ids, SRS states, counts, timestamps and settings, and returns decisions and new states. It never holds a player, a stack or a slot.
- **Immutable values.** SRS states, settings and name list entries are records. A rule returns a new state instead of changing the old one. `fabric` stores the result.
- **No clock.** The core never reads the current time. Every rule that needs it takes an `Instant now`, so tests pass any time they like. See [Time](../design/time.md).
- **No files.** The core doesn't read or write files or JSON. `fabric` loads the name list and progress files and hands the core plain values. This keeps the core free of libraries, and only the loading code changes if a file format changes.
- **Settings as a parameter.** Rules that depend on a setting take a `Settings` value. The core never reaches for a global.

## Packages

Package names are short for `io.github.willdalh.oboecraft.core.item` and so on. The existing `Oboecraft` class, with the mod id, stays in `core`.

| Package | Contents | Milestone |
|---|---|---|
| `core.item` | `ItemId`, `NameListEntry`, `NameList` | 3b |
| `core.srs` | `SrsState`, `Progress`, `SrsRules` | 3b |
| `core.lock` | `Interaction`, `LockDecision`, `LockCheck` | 3b |
| `core.review` | `ReviewRules`, `ReviewStart`, `ReviewResult`, `AnswerChecker` | 3b |
| `core.settings` | `Settings`, `LevelSettings` | 3b |
| `core.punishment` | Removal rate, removal counts, protected items | 9 |

### Items

- `ItemId`: a record around the item id string, like `block.minecraft.oak_log`. Code passes `ItemId`, never a bare string, so it can't be mixed up with a translation key.
- `NameListEntry`: the item id, its translation keys, the Japanese name and the reading.
- `NameList`: all entries. Looks up an entry by translation key or by item id. It is built from a list of entries and rejects duplicate ids and translation keys that belong to two entries.

The reading is a plain string until milestone 4 decides how readings are stored per kanji. Only `NameListEntry` and `AnswerChecker` change then.

### SRS state

- `SrsState`: a sealed interface with three records, `Leveled`, `Known` and `Ignored`. `Leveled` has the level, locks-at time, demoted flag, wrong attempts and banned-until time from [SRS state](../design/srs-state.md#leveled-state). `Leveled.NEW` is level 0 with nothing else set.
- `Progress`: one player's SRS states, a map from `ItemId` to `SrsState`. An item with no entry is `Leveled.NEW`. It also keeps states for item ids that aren't in the current name list, so they survive a name list change.
- `SrsRules`: `isLocked(state, now, settings)`, `pass(state, now, settings)` and `fail(state, now, settings)`. Known, ignored and revert are plain new states and need no rule.

### Lock check

- `Interaction`: what the player tries to do, one value per kind in [What the player tries to do](../design/lock-check.md#what-the-player-tries-to-do). It includes `PICK_UP`.
- `LockDecision`: `ALLOW`, `REVIEW`, `BANNED` or `DENY`.
- `LockCheck.check(item, interaction, state, now, locksActive, settings)`: the [rules](../design/lock-check.md#rules) in order. The item is an `Optional<ItemId>`, empty when the stack isn't in the name list. `locksActive` is false in creative and spectator mode, which `fabric` works out from the game mode.

This is the only place that decides whether an action is allowed. Every caller in `fabric`, on both sides, goes through it.

### Reviews

- `ReviewRules.start(state, now)`: whether a review can start, as a `ReviewStart`: started (with the level, whether it is a lesson, and attempts left), not locked, or banned.
- `ReviewRules.answer(state, correct, now, settings)` and `ReviewRules.dontKnow(state, now, settings)`: the outcome as a `ReviewResult`, which holds the outcome from [Outcomes](../design/reviews.md#outcomes), the new state, and the level before the review, which punishment needs.
- `AnswerChecker.check(entry, typed)`: whether a typed answer is correct. In 3b it matches the Japanese name or the reading exactly. Milestone 4 replaces the rules, not the signature.

The trigger isn't in the core. It is a Minecraft concept, about menus and slots, so `fabric` keeps it and turns it into counts for punishment.

### Settings

- `Settings`: a record with the [server settings](../design/settings.md#server-settings). `Settings.DEFAULTS` holds the defaults, and the record checks its values when it is built.
- `LevelSettings`: one level's interval and demotion. `Settings` has a list of them, and the top level is the list's size plus one.

Client settings, like clickable buttons, only change what the client shows, so they live in `fabric`.

## Tests

Every rule gets JUnit tests in `core/src/test`, with hand-written name list entries and fixed `Instant` values.
