# Settings

Values that the [idea](../idea.md) says should be settings are read from one place, which holds the defaults. Server settings are one value that every rule takes as an input, on both sides. Client settings belong to each player's computer. Milestone 12 added the settings files, sending the server settings to players, and the settings screen.

## Server settings

On a dedicated server, the server owner controls these. In singleplayer and in a world opened to LAN they belong to the built-in server, and the player who runs it controls them.

| Setting | Default | Used by |
|---|---|---|
| Levels | 8 levels with an interval and a demotion each, see [Levels and intervals](srs-state.md#levels-and-intervals) | Pass, fail and locking |
| Ban length | 5 minutes | Fail |
| `percentageFactor` | 5, in whole percentage points | Removal rate, see [Punishment](punishment.md) |
| Dropping counts as an interaction | Off | Lock check |
| `useOwnProgress`: players use their own progress | On | Where each player's progress is, see [below](#own-progress-and-server-progress) |

The removal rate on a fail at level `L` is `50% - (L - 1) × percentageFactor%`, and never below 0%. Since `percentageFactor` can't be negative, the rate is never above 50%.

The top level, mastered, is always one more than the number of levels in the setting.

### Own progress and server progress

With `useOwnProgress` on, each player brings their **own progress**: one progress per account, which their game keeps on their computer and sends to the server when they join. So a player has the same progress in singleplayer, in worlds opened to LAN and on every server that uses own progress. The server still decides every review and every change, and sends each change back for the player's game to save. It stores nothing itself.

With it off, the server keeps **server progress** for each player in its world folder, and their own progress is left alone. A server owner turns it off when players shouldn't bring progress from elsewhere: a player can edit their own progress file to master every item, so their items never lock and are never removed on a fail.

The setting decides when a player joins. A change applies to the players who join after it, and moves no progress between the two places. A server that turns it on doesn't take the server progress it kept with it, and one that turns it off starts players from the server progress it kept before, or empty.

Settings belong to each server, so one player's own progress meets different levels in different places. A level above a server's top level counts as mastered there, see [Locking](srs-state.md#locking). The stored level only changes when that server changes the item. A locks-at time stays as the server that set it worked it out.

A player's game uses the file in one game at a time. If another game on the same computer is already connected with it, the player can't join until that game leaves. If the player's game can't send their progress, the player can't join: the file is from a newer version of Oboecraft, can't be read, or the game didn't answer within 30 seconds. A file that isn't valid progress is kept as `.broken`, the player starts with empty progress and is told in chat. See [Progress files](../architecture/progress-files.md).

### Checks

Settings are checked as a whole when they are read, from the file, from the screen or from the network:

- There must be from 1 to 100 levels.
- A level's interval must be longer than zero, and a level's demotion from 0 to 100.
- The ban length must be from 0 up.
- Intervals and the ban length can be at most 1,000,000 days, so the times worked out from them never overflow.
- `percentageFactor` must be from 0 to 100. Above 50, the removal rate is 0 from level 2 anyway.

### The settings file

The server reads its settings from `config/oboecraft/server.json` when it starts. On a dedicated server, that is in the server's folder. In singleplayer, it is in the game folder, so every singleplayer world uses the same settings, like the singleplayer progress. Durations are written like `10m`, `1h 30m` or `14d`, with the units `d`, `h`, `m` and `s`. See [Settings files](../architecture/settings-files.md) for the format.

- A missing file is written with the defaults, so the server owner has a file to edit.
- A file that can't be read or isn't valid settings is logged with what is wrong, and the defaults are used. The file is left as it is, so a typo never loses the owner's other values.
- A dedicated server reads the file again only when it starts. The settings screen changes the settings of a running singleplayer world or world opened to LAN at once.

### Sending them to players

The client's lock check and displays need the server settings, so the server sends them to each player together with their progress when they join, and to every player again when they change. The client uses the settings of the current connection only. Until they arrive, its locks aren't active, so they never run on settings from another server. The client never changes them.

## Client settings

Each player chooses these on their own computer. They are saved in `config/oboecraft/client.json` in the game folder. A missing or invalid file means the defaults, and an invalid file is logged and left as it is.

| Setting | Default | Used by |
|---|---|---|
| Clickable buttons | Off | The answer menu shows a button for each action it can do, besides the keys |

With clickable buttons on, the answer menu has buttons for Answer, Don't know, Already known, Ignore, Show answer (in lessons) and Close at the bottom of the screen, and after a fail only Close. A button does what its key does. Unlike the key, the Don't know button works with text in the typing bar, since a click can't be a typo.

## The settings screen

The mod has its own settings screen. [Mod Menu](https://modrinth.com/mod/modmenu), an optional mod, opens it from the mod list. Without Mod Menu, the settings are changed in the files.

The screen shows the clickable buttons setting and the server settings. The levels are two lists separated by commas: the intervals, like `10m, 1h, 4h`, and the demotions, like `1, 1, 2`, one of each per level.

- Away from a server, or in singleplayer or a world opened to LAN that the player runs, the server settings are this computer's settings file, and can be changed. Saving checks them first. Invalid settings aren't saved, and the screen stays open and says what is wrong. A running world uses saved settings at once and sends them to its players.
- On another server, the screen shows that server's settings and can't change them, since only the server owner can.

## Not settings

These are fixed rules, not settings: the two attempts, level 0 being a lesson, the base removal rate of 50%, the list of protected items, the notification after a pass, and that only name tags can be renamed.
