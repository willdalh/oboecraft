# Settings files

The settings are saved as JSON files with a format version, like the [progress files](progress-files.md). What each setting does is in [Settings](../design/settings.md). They were added in milestone 12.

## Where the files are

| File | Path | Read by |
|---|---|---|
| Server settings | `<config folder>/oboecraft/server.json` | `ServerSettingsFile` in `fabric`, with `SettingsJson` in `json` |
| Client settings | `<config folder>/oboecraft/client.json` | `ClientSettingsFile` in `fabric`'s `src/client` |

The config folder is Fabric's, `config/` in the game folder on a client and in the server's folder on a dedicated server. So singleplayer worlds and worlds opened to LAN share one server settings file on each computer, and a dedicated server has its own. In dev runs, the folders are `fabric/run/config/` for `runClient` and `runServer`, and the game test runs have their own.

## Server settings

```json
{
  "version": 2,
  "levels": [
    {"interval": "10m", "demotion": 1},
    {"interval": "1h", "demotion": 1}
  ],
  "banLength": "5m",
  "percentageFactor": 5,
  "dropCountsAsInteraction": false,
  "useOwnProgress": true
}
```

The file written with the defaults has all 8 default levels.

- Every field must be there, with no others, so a misspelled field is reported instead of being ignored. Each level has exactly `interval` and `demotion`.
- Durations are strings in core `DurationText`: whole numbers with the units `d`, `h`, `m` and `s`, from days down to seconds, each unit at most once, separated by spaces, like `1h 30m`. They are written with every unit that isn't zero, so a duration that isn't whole seconds is written rounded down.
- `percentageFactor` and `demotion` are whole numbers, and `dropCountsAsInteraction` and `useOwnProgress` are `true` or `false`.
- The values must pass the checks of core `Settings` and `LevelSettings`, see [Checks](../design/settings.md#checks).

`SettingsJson.parse` throws with a message that says what is wrong and, for a level, which one. `ServerSettingsFile.load` runs in Fabric's `SERVER_STARTING`, before any player joins. It writes the defaults when there is no file, and otherwise reads the file like `read`, which logs a file that can't be read or isn't valid and returns the defaults, leaving the file as it is. Both files are written with `AtomicFiles.write`, which writes a temporary file, flushes it to disk and moves it into place, like the progress files.

A file with a version the mod doesn't read is refused like an invalid one. Version 1 is the first. Version 2 adds `useOwnProgress`, and a version 1 file, which must have every field of version 1 and no others, is read with `useOwnProgress` on, the default, so a server owner's file from before keeps working. A later version that adds settings goes up, so an older mod uses its defaults instead of saving the file again without the new settings. Nothing saves the server file except the settings screen, which writes all fields of the newest version.

## Client settings

```json
{
  "version": 1,
  "clickableButtons": false
}
```

The fields must be exactly these. A missing file means the defaults, and an invalid file is logged and left as it is, and the defaults are used. The client reads it once when the game starts, and the settings screen writes it.

## Settings in the game

`ServerProgress` holds the server settings that every rule on the server reads, through `ProgressView.settings()`. `useSettings` replaces them and sends every online player their progress again in a `ProgressSync`, which carries the settings. `ClientProgress` keeps the settings from the latest `ProgressSync` of the current connection, so `Locks`, the displays and `ClientReviews` use the server's settings. See [Fabric adapter](fabric.md#shared-services).

`OboecraftClient` holds the client settings. The settings screen, `client.settings.SettingsScreen`, writes the files and then uses the new settings: the client settings at once, and the server settings on a running built-in server through `useSettings` on the server thread.
