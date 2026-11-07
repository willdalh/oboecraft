# ADR-20261105: Progress saved as versioned JSON files

## Status
Superseded by ADR-20261107

## Context
Progress is shared across all singleplayer worlds, and each server keeps its own progress for each player. Minecraft's built-in ways to store player data, NBT and Fabric data attachments, save inside a single world. Once players have progress, changing how it is saved means converting their files, so this is hard to change later.

## Decision
Progress is saved as JSON files that include a format version number.

- Singleplayer: one file per Minecraft account, in the game folder outside the worlds.
- Dedicated server: one file per player, in the server's world folder.
- Open to LAN: the host keeps using their own shared progress. Guests' progress is saved in that world's folder, one file per player.

Alternative considered: NBT or Fabric data attachments. They are Minecraft's usual way to store player data, but they save inside one world, which would break sharing progress across singleplayer worlds.

## Consequences
- Creating or deleting a singleplayer world leaves progress untouched.
- The version number lets newer versions of the mod upgrade old files.
- The files are plain text and can be read and fixed by hand.
- We write our own code for loading, saving and upgrading progress.
- LAN guests' progress stays with the host's world and is separate from their own singleplayer progress.
