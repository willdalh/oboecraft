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
  "version": 3,
  "levels": [
    {"interval": "10m", "demotion": 1},
    {"interval": "1h", "demotion": 1}
  ],
  "banLength": "5m",
  "percentageFactor": 5,
  "dropCountsAsInteraction": false,
  "tsukumogamiAppearThreshold": 10,
  "tsukumogamiFadeThreshold": 5,
  "tsukumogamiReviewFreeze": "1m"
}
```

The file written with the defaults has all 8 default levels.

- Every field must be there, with no others, so a misspelled field is reported instead of being ignored. Each level has exactly `interval` and `demotion`.
- Durations are strings in core `DurationText`: whole numbers with the units `d`, `h`, `m` and `s`, from days down to seconds, each unit at most once, separated by spaces, like `1h 30m`. They are written with every unit that isn't zero, so a duration that isn't whole seconds is written rounded down.
- `percentageFactor`, `demotion`, `tsukumogamiAppearThreshold` and `tsukumogamiFadeThreshold` are whole numbers, `dropCountsAsInteraction` is `true` or `false`, and `tsukumogamiReviewFreeze` is a duration, `0s` for none.
- The values must pass the checks of core `Settings` and `LevelSettings`, see [Checks](../design/settings.md#checks).

`SettingsJson.parse` throws with a message that says what is wrong and, for a level, which one. `ServerSettingsFile.load` runs in Fabric's `SERVER_STARTING`, before any player joins. It writes the defaults when there is no file, and otherwise reads the file like `read`, which logs a file that can't be read or isn't valid and returns the defaults, leaving the file as it is. Both files are written with `AtomicFiles.write`, which writes a temporary file, flushes it to disk and moves it into place, like the progress files.

A file with another version is refused like an invalid one. A later version that adds settings goes up, so an older mod uses its defaults instead of saving the file again without the new settings. Nothing saves the server file except the settings screen, which writes all fields of the version it knows. The screen reads the file with `ServerSettingsFile.readIfValid`, which gives nothing for a file that can't be read or isn't valid, and then shows the defaults read-only and never saves, so neither a typo nor a file of a later version is replaced with the defaults.

Version 3 replaced `tsukumogamiThreshold` with `tsukumogamiAppearThreshold` and `tsukumogamiFadeThreshold`, for [willdalh/oboecraft#81](https://github.com/willdalh/oboecraft/issues/81). Version 2 added `tsukumogamiThreshold` and `tsukumogamiReviewFreeze`, for the [tsukumogami](../design/tsukumogami.md). Both older versions are still read, and reading doesn't write the file again. The settings screen saves it as version 3.

- Version 2 has exactly the fields above, with `tsukumogamiThreshold` in place of the two thresholds. Its threshold becomes the appear threshold, and the fade threshold is the default, 5, if that is below it. Otherwise the fade threshold is one below the appear threshold, or 0 for an appear threshold of 0, so the tsukumogami fades where it did before.
- Version 1, the first, has exactly the fields above without the tsukumogami's, and gets their defaults.

## Client settings

```json
{
  "version": 2,
  "clickableButtons": false,
  "reviewItemScalePercent": 100
}
```

Version 2 has exactly these fields. `reviewItemScalePercent` is a whole number from 50 to 400. The default 100 preserves the original 16-pixel image in GUI coordinates. The answer menu scales only this item's drawing and keeps it centered above the answer field, limited to the available space.

Version 1, with exactly `version` and `clickableButtons`, is also accepted: its clickable buttons value is kept and the review item size defaults to 100%. Reading does not rewrite the file; the next save writes version 2. Unknown fields, unsupported versions and invalid values are rejected. A missing file means the defaults, and an invalid file is logged and left as it is, and the defaults are used. The client reads it once when the game starts, and the settings screen writes it, only when the player changed the client settings, so Done leaves an invalid file as it is.

## Settings in the game

`ServerProgress` holds the server settings that every rule on the server reads, through `ProgressView.settings()`. `useSettings` replaces them and sends every online player their progress again in a `ProgressSync`, which carries the settings. `ClientProgress` keeps the settings from the latest `ProgressSync` of the current connection, so `Locks`, the displays and `ClientReviews` use the server's settings. See [Fabric adapter](fabric.md#shared-services).

`OboecraftClient` holds the client settings. The settings screen, `client.settings.SettingsScreen`, writes the files and then uses the new settings: the client settings at once, and the server settings on a running built-in server through `useSettings` on the server thread.

The settings screen is already full at the smallest GUI size used by the client game tests. Adding another setting needs a new layout, not just another row. After changing the layout, run `./gradlew runClientGameTest` and inspect the `settings-screen` screenshot taken by `SettingsClientGameTest` in `fabric/build/run/clientGameTest/screenshots/`. Check that labels, fields, toggles and the Done and Cancel buttons fit without overlapping or being cut off. See [Testing](overview.md#testing) for running the client tests with a virtual display.
