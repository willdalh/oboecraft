# Progress files

Progress is saved as JSON files with a format version, see [ADR: Progress saved as versioned JSON files](../decisions/20261105-progress-save-format.md). They are kept on the player's computer, see [ADR: Progress kept on the player's computer](../decisions/20261107-progress-on-the-players-computer.md). `ProgressFiles` in `fabric` reads and writes them and turns them into core `Progress` values, and the client's `ProgressStore` decides when, see [Fabric adapter](fabric.md#progress-on-the-client).

Format 3 stores leveled, known and ignored states, and for a leveled state also whether it is demoted, its wrong attempts and its ban. Format 1 and 2 files are read unchanged and written as format 3 on the next save. Format 2 added known and ignored states, since older code treats an unknown kind as a broken file. Format 3 added `demoted`, `wrongAttempts` and `bannedUntil` in milestone 9: a format 2 reader would ignore them and save the file again without them, so after a downgrade every demotion, ban and wrong attempt would be lost. With the new version, an older mod refuses the file instead of replacing it, see [Versions](#versions).

## Where the files are

One file per account and learning language on the player's computer, named by the account's UUID: `<game folder>/oboecraft/progress/<language>/<uuid>.json`. `<language>` is the learning language's code, like `ja_jp`; each folder has independent file versions, locks and broken-file handling. Servers keep no progress files.

While the player is in a world, the game holds a lock on `<uuid>.json.lock` next to the file, with Java's `FileChannel.tryLock`, so another game on the same computer can't send the same progress and save over this game's changes. The lock file stays when the game leaves, and holds nothing.

## Format

```json
{
  "version": 3,
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

- `items` is keyed by item id. Items at `Leveled.NEW` are left out. An item id longer than 256 characters makes the file broken, since no item has one.
- `kind` is `leveled`, `known` or `ignored`. A kind this version of the mod doesn't know makes the file broken, see below.
- Fields that are unset, false or 0 are left out. `wrongAttempts` is 0 or 1, and any other value makes the file broken.
- Times are ISO 8601 in UTC, to the millisecond, so they can be read and fixed by hand.
- Items are written in the order of their ids, so a file changes little between saves.
- Items whose ids aren't in the current name list are read and written back unchanged. Once the name list has former ids, states saved under a former id are moved to the current item id when the file is read, see [Items](../design/items.md#name-list-entries).

## Versions

`version` is the format version, starting at 1. When the format changes, the version goes up, and `ProgressFiles` gets a step that upgrades the previous version. An old file is upgraded step by step when it is read, and saved in the new format.

A file with a newer version than the mod knows was saved by a newer version of the mod, after a downgrade. It is never overwritten, and the client tells the server, which disconnects the player with a message saying their progress needs a newer version of Oboecraft. Playing on with empty progress would lock every item, and nothing from the session could be saved without losing the newer file. In singleplayer this takes the player back to the title screen with the same message.

## Writing safely

A file is written to a temporary file next to it, flushed to disk and then moved into place, so a crash or power loss while saving never leaves half a file.

A file that isn't valid progress is renamed to `<uuid>.json.broken` and kept, or `<uuid>.json.broken.1` and so on if that name is taken, so an earlier broken file is never overwritten. The error is logged. The player starts with empty progress and is told in chat, when they join, that their progress file couldn't be read and the name the broken file was kept under, so they can fix it by hand.

Progress too large to send, with more states or bytes than `UploadProgress` carries, is left alone, and the client tells the server, which disconnects the player with a message asking them to check their game's log. It is checked with `UploadProgress.fits` before it is sent, since a payload that is too large would otherwise fail to encode and close the connection without a message.

If the file can't be read at all, for example because of its permissions, or a broken file can't be moved, the player is disconnected with a message asking them to check their game's log, and the file is left alone. Playing on would save over a file whose contents are unknown.

The client only reads and writes the file on one background thread of its own, so two saves of one file never race.

## Moving existing Japanese progress

`ProgressMigration` runs on the store's background thread at startup for every account in the old folder. A Japanese join retries migration if startup could not acquire a lock. It holds both the old and Japanese `.lock` files; a running older game delays migration, and a Japanese join is refused as in use until that lock is released.

An old `progress/<uuid>.json` is copied as complete bytes to a temporary file beside `progress/ja_jp/<uuid>.json`, flushed and replaced atomically as normal saving does. The original modification time is retained. Only then is the old path replaced atomically with a JSON stub whose `version` is 2147483647. Older mods reject that version with their existing newer-version message. Migration recognizes its stub and can be repeated after a crash. No original progress is deleted before a complete preserved copy exists.

If both files exist, their filesystem modification times decide the winner (the destination wins ties). The other is copied to an unused Japanese `.broken` path before replacement. All old `.broken` and numbered `.broken` files move to unused names beside the Japanese file, preserving even invalid UTF-8 bytes. No backup is overwritten. Errors leave the original data available and prevent using an unknown Japanese file. Other language files are untouched.
