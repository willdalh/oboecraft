# Progress files

Progress is saved as JSON files with a format version, see [ADR: Progress saved as versioned JSON files](../decisions/20261105-progress-save-format.md). `ProgressFiles` in `fabric` reads and writes them and turns them into core `Progress` values.

The format was designed in milestone 3a. Until milestone 3b is merged, this page describes what 3b builds, and 3b updates it where the code ends up different.

## Where the files are

One file per player, named by the player's UUID:

| Game | Path |
|---|---|
| Singleplayer, and the host of a world opened to LAN | `<game folder>/oboecraft/progress/<uuid>.json` |
| Dedicated server, and guests on a world opened to LAN | `<world folder>/oboecraft/progress/<uuid>.json` |

The server tells the host apart from LAN guests by whether the player owns the singleplayer world.

## Format

```json
{
  "version": 1,
  "items": {
    "block.minecraft.oak_log": {
      "kind": "leveled",
      "level": 2,
      "locksAt": "2026-11-06T13:10:00.000Z"
    },
    "block.minecraft.diamond_block": {
      "kind": "leveled",
      "level": 4,
      "locksAt": "2026-11-05T09:00:00.000Z",
      "demoted": true,
      "wrongAttempts": 1,
      "bannedUntil": "2026-11-06T12:05:00.000Z"
    },
    "item.minecraft.music_disc_13": { "kind": "known" },
    "block.minecraft.andesite": { "kind": "ignored" }
  }
}
```

- `items` is keyed by item id. Items at `Leveled.NEW` are left out.
- `kind` is `leveled`, `known` or `ignored`.
- Fields that are unset, false or 0 are left out.
- Times are ISO 8601 in UTC, to the millisecond, so they can be read and fixed by hand.
- Items whose ids aren't in the current name list are read and written back unchanged.

## Versions

`version` is the format version, starting at 1. When the format changes, the version goes up, and `ProgressFiles` gets a step that upgrades the previous version. An old file is upgraded step by step when it is read, and saved in the new format. A file with a newer version than the mod knows is not overwritten: the player gets empty progress for that session, and the error is logged.

## Writing safely

A file is written to a temporary file next to it and then moved into place, so a crash while saving never leaves half a file. A file that can't be read is renamed to `<uuid>.json.broken` and kept, the player starts with empty progress, and the error is logged.
