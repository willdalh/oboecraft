# Settings

Values that the [idea](../idea.md) says should be settings are read from one place, which holds the defaults. Until milestone 12, the defaults are the only values. Milestone 12 adds the settings files, sends the server settings to players, and adds the screens, without changing the code that reads the values.

Each server setting is added to that place by the milestone that first uses it: the levels and their intervals in 3b, the drop setting in 6, and the demotions, the ban length and `percentageFactor` in 9.

## Server settings

On a server, the server owner controls these. In singleplayer they belong to the built-in server, and the player controls them.

| Setting | Default | Used by |
|---|---|---|
| Levels | 8 levels with an interval and a demotion each, see [Levels and intervals](srs-state.md#levels-and-intervals) | Pass, fail and locking |
| Ban length | 5 minutes | Fail |
| `percentageFactor` | 5 | Removal rate |
| Dropping counts as an interaction | Off | Lock check |

The removal rate on a fail at level `L` is `50% - (L - 1) × percentageFactor%`, and never below 0%. Since `percentageFactor` can't be negative, the rate is never above 50%.

The top level, mastered, is always one more than the number of levels in the setting.

The server sends its settings to each player when they join, since the client's lock check needs them too. Until milestone 12, both sides use the defaults.

Settings are checked when they are read. A level must have an interval longer than zero and a demotion of at least 0, there must be at least one level, the ban length must be at least 0, and `percentageFactor` must be at least 0. Invalid settings are rejected as a whole, and the defaults are used instead. Milestone 3b checks the levels and intervals, and each later setting is checked when it is added.

## Client settings

Each player chooses these on their own computer.

| Setting | Default | Used by |
|---|---|---|
| Clickable buttons | Off | The answer menu shows buttons for the actions |

## Not settings

These are fixed rules, not settings: the two attempts, level 0 being a lesson, the base removal rate of 50%, and the list of protected items.
