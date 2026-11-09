# Core

The `core` module holds the game rules as plain Java, in `io.github.willdalh.oboecraft.core`. This page describes its parts and their names. What the rules do is in [`docs/design/`](../design/README.md).

The parts were designed in milestone 3a and built in milestone 3b. Milestone 4 added name parts and full answer checking, milestone 6 the interaction and the drop setting, and milestone 9 fails, bans and punishment. Each type has only what the milestones so far use. Later milestones add values, fields and rules to these types, as noted below.

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
| | `JapaneseName`, `NamePart` | 4 |
| `core.srs` | `SrsState`, `Progress`, `SrsRules` | 3b |
| | `NotificationTiming` | 10 |
| `core.lock` | `LockDecision`, `LockCheck` | 3b |
| | `Interaction` | 6 |
| `core.review` | `ReviewRules`, `ReviewStart`, `ReviewResult`, `ReviewOutcome`, `AnswerChecker` | 3b |
| `core.settings` | `Settings`, `LevelSettings` | 3b |
| | `DurationText`, `LevelsText` | 12 |
| `core.text` | `JapaneseText`, `Romaji` | 4 |
| `core.punishment` | `Punishment`, `Recipe`, `ProtectedItems` | 9 |

### Items

- `ItemId`: a record around the item id string, like `block.minecraft.oak_log`. Code passes `ItemId`, never a bare string, so it can't be mixed up with a translation key. An id is at most `MAX_LENGTH`, 256 characters, longer than any translation key in the game, so a progress file and a payload hold the same ids.
- `NameListEntry`: the item id, its translation keys and its `JapaneseName`. It rejects an entry with no translation key, or a blank key.
- `JapaneseName`: the Japanese name as a list of `NamePart`s, see [Name parts](../design/items.md#name-parts). `text()` is the parts' text joined, like オークの原木, and `reading()` their readings joined, like おーくのげんぼく. For answer checking, a position in the name is a position in its reading: `partAt(position)` is the part there, as a `PlacedPart` with its normalized text and where it starts and ends, and `vowelBefore(position)` is the vowel just before it in the same part, or none at the start of a part. All of this is worked out once when the name is built, since it never changes. It rejects a name with no parts.
- `NamePart`: a record with a part's text and its reading. It rejects a reading that isn't hiragana and ー, a reading that starts with a small kana, ー or ん, a part in kana with a reading other than its kana, and text with no letter or digit. `NamePart.kana(text)` makes a part in kana, read as it is written, and rejects text that isn't kana.
- `NameList`: all entries, in order (`entries()`). Looks up an entry by translation key or by item id. It is built from a list of entries and rejects an item id or translation key that belongs to two entries.

Former ids, and moving states saved under a former id when progress is loaded (`Progress.withFormerIdsMerged(nameList)` in the design), are added with the first name list update that merges two entries. The name list has no merges yet, so they are left out, and the [name list tool](name-list-tool.md) stops if an update would merge two entries. See [Items](../design/items.md#name-list-entries).

### SRS state

- `SrsState`: a sealed interface. It has `Leveled`, with the level, locks-at time, demoted flag, wrong attempts and banned-until time from [SRS state](../design/srs-state.md#leveled-state), and stateless `Known` and `Ignored` records. `Leveled.NEW` is level 0 with no locks-at time. A second constructor takes only the level and locks-at time, for a state that was never failed. `Leveled` rejects a negative level and wrong attempts other than 0 or `MAX_WRONG_ATTEMPTS` (1). `isBannedAt(now)` is whether its latest ban ends after `now`.
- `Progress`: one player's SRS states, a map from `ItemId` to `SrsState`. An item with no entry is `Leveled.NEW`, and storing `Leveled.NEW` removes the entry, so only items the player has interacted with are kept. It also keeps states for item ids that aren't in the current name list, so they survive a name list change. `with(item, state)` returns new progress with one state changed.
- `SrsRules`: `isLocked(state, now, settings)`, `isBanned(state, now)`, which is only true for a locked item, `pass(state, now, settings)`, which doesn't promote a demoted item, and `fail(state, now, settings)`, which only accepts a leveled state from level 1 below the top level. `isLearning(state, settings)` is whether a state is on the word list's Learning tab, leveled from level 1 below the top level. `canRevert(state, settings)` restricts the word list's Known, Ignored and Mastered tabs and Revert to known, ignored and mastered states.
- `LearningOrder`: the orders of the word list's Learning tab, `URGENCY` and `LEVEL`, each with a `comparator(now, settings)` of leveled states, and `next()` for the sort button. Urgency puts the items that `SrsRules.isLocked` finds unlocked at `now` first, then the locked ones, and each part by locks-at time, with no locks-at time first. So unlocked items come least time left first, then locked items longest locked first, and an item moves from one part to the other when it locks.

`DisplayRules.of(state, now, settings)` returns a sealed `DisplayState`: `Hidden` for locked items, `Timed(LockTimer)` for unlocked items with an interval, or `Normal` for mastered items. The rules use `SrsRules.isLocked` once, without inventing an interaction. The type cannot represent a hidden item with a running timer. Milestones 8 and 9 extend these explicit display rules for their new states.

`LockTimer` holds the remaining time and level interval. `fractionRemaining()` clamps the bar to full and `closeToLocking()` checks the final tenth of the interval. `DisplayRules.closeToLocking` uses the same threshold for slot markers without constructing a full display state or timer. Durations are not converted to whole milliseconds or nanoseconds, so subsecond and very long configured intervals work too. The adapter decides whether displays are active and draws the result.

- `NotificationTiming`: how long the notification after a pass is shown (`SHOWN`, 3 seconds) and fades out (`FADE`, the last second). `isOver(elapsed)` and `opacity(elapsed)` take the real time since it was shown, see [Display](../design/display.md#notification).

### Lock check

- `LockDecision`: `ALLOW`, `REVIEW`, `BANNED` or `DENY`.
- `Interaction`: what the player tries to do, one value per kind in [What the player tries to do](../design/lock-check.md#what-the-player-tries-to-do), like `PLACE`, `INGREDIENT` or `DROP`. Which game action is which interaction is a Minecraft question, so `fabric` decides it.
- `LockCheck.check(state, interaction, now, locksActive, settings)`: the [rules](../design/lock-check.md#rules) for an item, in order. `locksActive` is false in creative and spectator mode, which `fabric` works out from the game mode, and on the client before the server has sent the player's SRS states. A stack that isn't in the name list has no SRS state, so `fabric` allows it before it gets here (rule 1). `DROP` is allowed unless `Settings.dropCountsAsInteraction()` is on (rule 3).

`Interaction.PICK_UP` goes through the same check: ignored items are denied, other items are allowed without a review. `ReviewRules.isLesson` identifies level 0; wrong lesson answers return `LESSON` without changing the state.

`LockCheck` is the only place that decides whether an action is allowed. Every caller in `fabric`, on both sides, goes through it.

### Reviews

- `ReviewRules.start(state, now, settings)`: whether a review can start, as a `ReviewStart`: `Started` with the level and the attempts left, `NotLocked`, or `Banned` with when the ban ends. It decides with `SrsRules.isLocked` and `isBanned`, so it agrees with the lock check when settings change the top level. `ATTEMPTS` is the two attempts of a review. A lesson always has both, since it doesn't count wrong answers.
- `ReviewRules.isAttempt(typed)`: whether typed text counts as an attempt. Text that normalizes to nothing, with `JapaneseText.normalize`, doesn't, so the client and server agree with answer checking about what is empty.
- `ReviewRules.answer(entry, state, typed, now, settings)` and `dontKnow(state, now, settings)`: the result as a `ReviewResult`, which holds the `ReviewOutcome` (`PASS`, `WRONG`, `LESSON` or `FAIL`), the new state, and the level before the review, which punishment needs. A wrong first answer returns `WRONG` with the wrong attempt saved in the state, and a wrong second answer or Don't know returns `FAIL` with `SrsRules.fail`. Both only accept a leveled state.
- `AnswerChecker.isCorrect(entry, typed)`: whether a typed answer is correct, with the rules in [Answers](../design/answers.md). See [Answer checking](#answer-checking).

The trigger isn't in the core. It is a Minecraft concept, about menus and slots, so `fabric` keeps it and turns it into counts for punishment.

### Punishment

The rules are in [Punishment](../design/punishment.md).

- `Punishment.removalPercent(level, settings)`: the removal rate in whole percent, from `BASE_REMOVAL_PERCENT` (50) and `Settings.percentageFactor`, never below 0. It rejects level 0, since lessons aren't punished.
- `Punishment.removedCount(item, inView, level, settings)`: how many of the reviewed item a fail removes from what the current view has, 0 for a protected item.
- `Punishment.removedIngredients(recipe, inView, level, settings)`: how many of each ingredient a fail on a crafted item removes, leaving out protected ingredients and those with nothing to remove.
- `Recipe`: what one craft uses, as a map from `ItemId` to uses and the total number of uses, which also counts ingredients that aren't items. It rejects uses below 1 and a total below the items' uses.
- `ProtectedItems.isProtected(item)`: the fixed list of protected item ids, `IDS`, built once.

Every count is `count × part × percent / (whole × 100)`, rounded up, in `long` integer arithmetic, so no share is rounded up because of a floating-point error. It is never more than the count.

### Answer checking

`AnswerChecker` normalizes the typed answer with `JapaneseText.normalize`, then searches for a way to read it as the Japanese name. A step in the search is a position in the name's whole reading together with a position in the answer. From each step it tries:

- at the start of a part, the part's normalized text, like 原, which jumps to the end of the part,
- at the start of the particle, a part whose normalized text is の, leaving it out,
- the next kana of the reading typed as kana, or, for a long vowel, any way of writing it,
- a romaji spelling from `Romaji` whose kana are the next kana of the reading, within the part,
- a single `n` for ん when the answer doesn't go on with a vowel, `y` or `'`, and a doubled consonant for っ.

The answer passes if a path reaches the end of both the reading and the answer. Romaji can be read in more than one way, like `onna`, so the search tries every way. Each step is tried at most once, so the work is at most the reading's length times the answer's length, and answers are at most 64 characters, see [Networking](fabric.md#networking).

A long vowel is only matched inside a part, against `JapaneseName.vowelBefore`, so a vowel at the start of a part, including the part after a left-out の, is always matched as itself. A romaji spelling never crosses into the next part, since a part can't start with a small kana.

The answer checker only keeps what depends on the answer: the states it has visited and those still to try. Everything about the name comes from `JapaneseName`, which works it out once.

### Text

`core.text` holds the rules for Japanese text that the name list and answer checking share:

- `JapaneseText.normalize(text)`: Unicode NFKC, lower case, katakana as hiragana (apart from the counters ヵ and ヶ), ō and ô as o and ー, dashes as ー, apostrophes only after `n`, and everything that isn't a letter or digit left out. Answers and the name's text are normalized the same way. See [Answers](../design/answers.md#ignored-differences).
- `JapaneseText.isKana`, `isSmallKana` and `vowelOf`: what kind of kana a character is, for `NamePart`'s checks and for long vowels. `canStartAPart` is the rule for the first kana of a part's reading, which `NamePart` checks and the name list tool follows when it splits readings. `VOWELS`, `kanaOfVowel` and `isVowel` are the five vowels in romaji and kana, from the one table `vowelOf` uses, and `Romaji` builds its rows from `VOWELS` too.
- `Romaji`: the spellings a Japanese keyboard types, like `shi` and `si` for し and `du` for づ, looked up by their first letter. It is built once, and rejects a spelling that is two different kana.

### Settings

- `LevelSettings`: one level's interval and demotion.
- `Settings`: a record with the [server settings](../design/settings.md#server-settings): the levels, `banLength`, `percentageFactor` and `dropCountsAsInteraction`. `Settings.DEFAULTS` holds the defaults, and the record checks its values when it is built. `topLevel()` is the number of levels plus one, and `interval(level)` and `demotion(level)` are a level's interval and demotion. `withLevels`, `withBanLength`, `withPercentageFactor` and `withDropCountsAsInteraction` return a copy with one setting changed.

- `Settings` and `LevelSettings` also check that values stay small enough to work with: at most `MAX_LEVELS` (100) levels, demotions up to 100, intervals and the ban length up to `MAX_DURATION` (1,000,000 days), and `percentageFactor` up to `MAX_PERCENTAGE_FACTOR` (100). Settings from a file or the network are only ever built through these records.
- `DurationText`: durations as people write them, like `10m` or `1h 30m`: `parse` reads whole numbers with `d`, `h`, `m` and `s` from the largest unit down, and `format` writes every unit that isn't zero. The settings file and the settings screen use it.
- `LevelsText`: the levels as the settings screen shows them, a list of intervals and a list of demotions separated by commas, with `intervals`, `demotions` and `parse`.

Each setting is added to `Settings` by the milestone that first uses it: the levels in 3b, the drop setting in 6, and the ban length and `percentageFactor` in 9. Milestone 12 added the limits and the text formats.

Client settings, like clickable buttons, only change what the client shows, so they live in `fabric`.

## Tests

Every rule gets JUnit tests in `core/src/test`, with hand-written name list entries and fixed `Instant` values.
