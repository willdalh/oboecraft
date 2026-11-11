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
| Tsukumogami threshold | 5 | The overdue count the [tsukumogami](tsukumogami.md) appears at. 0 turns it off |
| Tsukumogami review freeze | 60 seconds | How long the tsukumogami stands still during one review. 0 means it never does |

The removal rate on a fail at level `L` is `50% - (L - 1) × percentageFactor%`, and never below 0%. Since `percentageFactor` can't be negative, the rate is never above 50%.

The top level, mastered, is always one more than the number of levels in the setting.

### Checks

Settings are checked as a whole when they are read, from the file, from the screen or from the network:

- There must be from 1 to 100 levels.
- A level's interval must be longer than zero, and a level's demotion from 0 to 100.
- The ban length must be from 0 up.
- Intervals and the ban length can be at most 1,000,000 days, so the times worked out from them never overflow.
- `percentageFactor` must be from 0 to 100. Above 50, the removal rate is 0 from level 2 anyway.
- The tsukumogami threshold must be from 0 to 1,000.
- The tsukumogami review freeze must be from 0 to 1 hour, so the answer menu can't keep the tsukumogami still all night.

### The settings file

The server reads its settings from `config/oboecraft/server.json` when it starts. On a dedicated server, that is in the server's folder. In singleplayer, it is in the game folder, so every singleplayer world uses the same settings. Durations are written like `10m`, `1h 30m` or `14d`, with the units `d`, `h`, `m` and `s`. See [Settings files](../architecture/settings-files.md) for the format.

- A missing file is written with the defaults, so the server owner has a file to edit.
- A file from before the tsukumogami, which has no tsukumogami settings, is read with their defaults. It isn't written again until the settings screen saves it.
- A file that can't be read or isn't valid settings is logged with what is wrong, and the defaults are used. The file is left as it is, so a typo never loses the owner's other values.
- A dedicated server reads the file again only when it starts. The settings screen changes the settings of a running singleplayer world or world opened to LAN at once.

### Sending them to players

The client's lock check and displays need the server settings, so the server sends them to each player together with their progress when they join, and to every player again when they change. The client uses the settings of the current connection only. Until they arrive, its locks aren't active, so they never run on settings from another server. The client never changes them.

## Client settings

Each player chooses these on their own computer. They are saved in `config/oboecraft/client.json` in the game folder. A missing or invalid file means the defaults, and an invalid file is logged and left as it is.

| Setting | Default | Used by |
|---|---|---|
| Clickable buttons | Off | The answer menu shows a button for each action it can do, besides the keys |
| Review item size | 100% | The item image above the answer field, for every item; adjustable from 50% to 400% |

With clickable buttons on, the answer menu has buttons for Answer, Don't know, Already known, Ignore, Show answer (in lessons) and Close at the bottom of the screen. Once the name is revealed, Show answer becomes Show reading; after a fail, Show reading and Close are available. Show reading is disabled once the reading is visible. A button does what its key does. Unlike the key, the Don't know button works with text in the typing bar, since a click can't be a typo.

Review item size changes only the image above the typing field. It applies equally to every reviewed item, across worlds and servers on this computer. Inventory icons, the held item, the word list and other item displays keep their normal size. The image stays centered above the level label and answer field; if a small window leaves less space above them, the image is limited to that space.

Screenshots show the [default 100% size](../images/review-item-size/default.png), the [same item at 300%](../images/review-item-size/large.png), [another item using the same 300% setting](../images/review-item-size/large-sword.png), and the [size slider](../images/review-item-size/settings.png).

## The settings screen

The mod has its own settings screen. [Mod Menu](https://modrinth.com/mod/modmenu), an optional mod, opens it from the mod list. Without Mod Menu, the settings are changed in the files.

The screen shows the clickable buttons setting, a review item size slider, and the server settings, the tsukumogami's threshold and review freeze included, each with a tooltip that says what 0 does. Done saves and applies the client settings; Cancel leaves them unchanged. The levels are two lists separated by commas: the intervals, like `10m, 1h, 4h`, and the demotions, like `1, 1, 2`, one of each per level.

- Away from a server, or in singleplayer or a world opened to LAN that the player runs, the server settings are this computer's settings file, and can be changed. Saving checks them first. Invalid settings aren't saved, and the screen stays open and says what is wrong, in place of the server settings' heading. A running world uses saved settings at once and sends them to its players.
- If this computer's server settings file can't be read or isn't valid, the screen shows the defaults that are used instead, read-only, and says that the file isn't valid. It never saves over the file, so the owner can fix the typo by hand.
- On another server, the screen shows that server's settings and can't change them, since only the server owner can.
- The client settings file is only written when the client settings were changed.

## Not settings

These are fixed rules, not settings: the two attempts, level 0 being a lesson, the base removal rate of 50%, the list of protected items, the notification after a pass, that only name tags can be renamed, and the tsukumogami's health, damage, speed and delays.
