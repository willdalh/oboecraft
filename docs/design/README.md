# Design

These docs describe how the mod behaves. They win over [`idea.md`](../idea.md) and the [implementation plan](../implementation-plan.md) where they disagree. How the code is structured is in [`docs/architecture/`](../architecture/overview.md).

The foundations were designed in milestone 3a, before any game code, with the whole plan in mind, and milestone 3b built the first parts. Each doc says which milestone builds which part, so the docs never claim more than the mod does.

| Doc | Contents |
|---|---|
| [Items](items.md) | What an item is, the name list, and how a stack maps to its item |
| [SRS state](srs-state.md) | Levels, intervals, locking, demotion, bans, and known and ignored items |
| [Lock check](lock-check.md) | The one check that decides what happens when a player interacts with a stack |
| [Reviews](reviews.md) | The answer menu's rules: starting a review, attempts, actions, lessons and what punishment needs |
| [Settings](settings.md) | The values that settings control and their defaults |
| [Time](time.md) | Where the current time comes from, on the server, on the client and in tests |

## What is built when

| Part | Milestone |
|---|---|
| Name list with 10 hand-written items, stack to item mapping | 3b |
| Levels, intervals, locking, saving progress, sync to the client | 3b |
| Lock check, used for placing blocks | 3b |
| Answer menu with Answer only, exact kana or kanji | 3b |
| Name list format and full answer checking | 4 |
| The full name list | 5 |
| Lock check for every interaction | 6 |
| Lessons, Already known, Ignore, the word list | 8 |
| Two attempts, Don't know, punishment, demotion and bans | 9 |
| Creative mode | 11 |
| Settings files, sync and screens | 12 |
