# SRS state

Each player has one SRS state per [item](items.md). All of a player's SRS states together are their progress.

## Kinds of state

An SRS state is one of three kinds:

| Kind | Meaning | Locked? |
|---|---|---|
| Leveled | The item moves through the levels, from 0 up to mastered. Every item starts here. | See [Locking](#locking) |
| Known | The player marked it as already known. It has no level. | Never |
| Ignored | The player chose to leave it out. It has no level. | Can't be used at all, see [Lock check](lock-check.md) |

An item with no saved state is leveled at level 0. So progress only stores items the player has interacted with, and new items in the name list need no upgrade of old saves.

Revert puts a known, ignored or mastered item back to leveled at level 0, with nothing else kept.

Known, ignored and revert are added in milestone 8. Until then every item is leveled.

## Leveled state

A leveled state has these fields:

| Field | Meaning |
|---|---|
| Level | 0 means never unlocked. The top level means mastered. |
| Locks at | When the item locks next, as a point in real-world time. Empty at level 0 and when mastered. |
| Demoted | Set by a fail, cleared by the next pass. While set, a pass doesn't promote. |
| Wrong attempts | Wrong attempts so far in the current review, 0 or 1. Cleared by a pass or a fail. |
| Banned until | When the current ban ends. Empty if there has never been a ban. |

Demoted, wrong attempts and bans are used from milestone 9. Until then they are always unset.

## Levels and intervals

The levels and their intervals come from the [settings](settings.md). The defaults:

| Level | Interval | Demotion on a fail |
|---|---|---|
| 0 | Locked until the first pass | No punishment |
| 1 | 10 minutes | 1 level, but never below 1 |
| 2 | 1 hour | 1 level |
| 3 | 4 hours | 1 level |
| 4 | 1 day | 1 level |
| 5 | 3 days | 2 levels |
| 6 | 1 week | 2 levels |
| 7 | 2 weeks | 2 levels |
| 8 | 1 month (30 days) | 2 levels |
| 9 | Mastered, never locked again | Can't fail |

The top level is one more than the number of levels with an interval. Each interval is the time the item stays unlocked after a pass that leaves it at that level.

## Locking

A leveled item is locked when:

- it is at level 0, or
- it is below the top level and the current time is at or past its locks-at time, or
- it is below the top level and has no locks-at time.

A mastered item is never locked. Since the state holds a timestamp, no timers run: whether an item is locked is worked out from the state and the current time whenever it is needed. See [Time](time.md).

The last rule covers settings that add levels. If the top level goes from 9 to 10, an item mastered at 9 has no locks-at time and becomes locked, to be reviewed at its old level. If settings remove levels, a level above the new top level counts as mastered.

## Pass

On a pass, with `now` as the current time:

| Before | After |
|---|---|
| Level 0 | Level 1, locks at `now` + the level 1 interval |
| Demoted, at level `L` | Still level `L`, locks at `now` + the level `L` interval, demoted cleared |
| Level `L`, `L + 1` below the top | Level `L + 1`, locks at `now` + the level `L + 1` interval |
| Level `L`, `L + 1` is the top | Mastered, no locks-at time |

A pass also clears wrong attempts.

A pass only unlocks the item. It doesn't carry out the interaction that started the review, so the player does it again.

## Fail

A fail only happens from level 1 and up, see [Reviews](reviews.md). On a fail at level `L`:

- The level drops by the demotion for level `L`, but never below 1.
- Demoted is set.
- The item is banned until `now` + the ban length.
- Wrong attempts are cleared.
- Locks at is unchanged. It is in the past, so the item stays locked.

Punishment uses level `L`, the level before the demotion. Milestone 9 adds fails.

## Bans

A banned item can't be reviewed, and interactions with it are stopped, until the ban ends. A ban only matters while the item is locked, and an item is always locked during its ban, since bans only come from fails.
