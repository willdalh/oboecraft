# Design

These docs describe how the mod behaves. They win over [`idea.md`](../idea.md) and the [implementation plan](../implementation-plan.md) where they disagree. How the code is structured is in [`docs/architecture/`](../architecture/overview.md).

The foundations were designed in milestone 3a, before any game code, with the whole plan in mind, and milestone 3b built the first parts. Milestone 4 added name parts and full answer checking, milestone 5 the full name list, milestone 6 the lock check for every interaction, milestone 7 item displays, milestone 8 lessons and the word list, milestone 9 punishment, milestone 10 the notification after a pass, milestone 11 the game mode and renaming rules, milestone 12 the settings files, sync and screen, [willdalh/oboecraft#25](https://github.com/willdalh/oboecraft/issues/25) the reading clip after a pass, progress that follows each player to every world and server, and [willdalh/oboecraft#51](https://github.com/willdalh/oboecraft/issues/51) the tsukumogami. Each doc says which milestone builds which part, so the docs never claim more than the mod does.

| Doc | Contents |
|---|---|
| [Items](items.md) | What an item is, the name list, how a stack maps to its item, and renaming |
| [SRS state](srs-state.md) | Levels, intervals, locking, demotion, bans, and known and ignored items |
| [Lock check](lock-check.md) | The one check that decides what happens when a player interacts with a stack |
| [Reviews](reviews.md) | The answer menu's rules: starting a review, attempts, actions, lessons and what punishment needs |
| [Punishment](punishment.md) | What a fail removes from the current view, protected items and bans |
| [Answers](answers.md) | Which typed answers are correct: kanji, kana and romaji, long vowels, ん and the optional の |
| [Progress](progress.md) | Where a player's progress is kept, how it reaches the server and what stops a player from joining |
| [Settings](settings.md) | The values that settings control, their defaults, the settings files and the settings screen |
| [Display](display.md) | Hidden names, the lock timer, the slot marker, and the notification and reading clip after a pass |
| [Time](time.md) | Where the current time comes from, on the server, on the client and in tests |
| [Tsukumogami](tsukumogami.md) | The mob that follows a player day and night while their overdue items pile up |

## What is built when

| Part | Milestone |
|---|---|
| Name list with 10 hand-written items, stack to item mapping | 3b |
| Levels, intervals, locking, saving progress, sync to the client | 3b |
| Lock check, used for placing blocks | 3b |
| Answer menu with Answer only, exact kana or kanji | 3b |
| Name parts with readings per kanji, full answer checking | 4 |
| The full name list | 5 |
| Lock check for every interaction | 6 |
| Hidden names, lock timer and slot marker | 7 |
| Lessons, Already known, Ignore, the word list | 8 |
| Two attempts, Don't know, punishment, demotion and bans | 9 |
| The notification after a pass | 10 |
| Creative mode cancels a review, the anvil only renames name tags | 11 |
| Settings files, sync and screens | 12 |
| The reading clip after a pass | [#25](https://github.com/willdalh/oboecraft/issues/25) |
| Progress kept on the player's computer and sent to every server | After the milestones |
| The tsukumogami | [#51](https://github.com/willdalh/oboecraft/issues/51) |
| The tsukumogami's own sounds | [#52](https://github.com/willdalh/oboecraft/issues/52) |

- [Word list](word-list.md): learning, known, ignored and mastered items, and Revert.
