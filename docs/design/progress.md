# Progress

A player's progress is all their SRS states in one learning language. It follows the player: one progress per Minecraft account and learning language, which their game keeps on their computer and brings to every world they play in, singleplayer, worlds opened to LAN and servers alike. See [ADR: Progress kept on the player's computer](../decisions/20261107-progress-on-the-players-computer.md).

## Joining

When the player joins, their game sends their learning language and only that language’s progress to the server before they enter the world. The server holds it while they play, decides every review and every change as before, and sends each change back. The player's game saves it soon after each change and when the player leaves, and tells the player in chat if it can't. The server keeps no progress of its own.

An account can only be on its way into a world once at a time. Joining the same server again while the first connection is still joining disconnects the first, as Minecraft does with a player who logs in twice.

The player can't join when their game can't send their progress, and is told why:

- The learning language is unsupported. The server refuses unknown language codes rather than substituting Japanese.
- The progress was saved by a newer version of Oboecraft.
- The progress file can't be read. The player is asked to check their game's log.
- Another game on the same computer is in a world with the same progress, so the two games never save over each other. The player can join again once that game has left.
- The progress is too large to send: more than 65,536 items, or more than 1 MiB less 16 KiB. Only a file edited by hand, or a changed server, can make it that large. The player is asked to check their game's log, and the file is left alone.
- The game didn't send it within 30 seconds.

A progress file that isn't valid progress is kept as `.broken`, so it can be fixed by hand. The player starts with empty progress and is told in chat.

## Languages and migration

The [learning language](items.md#learning-language) stays fixed for one connection. Its items, locks, reviews, bans, word list and tsukumogami overdue count use only that language's progress. Other languages are never uploaded or changed. A broken file affects only its language.

Existing Japanese progress moves into the Japanese folder at startup. The old path becomes a stub that makes older mods refuse to load it as too new, so downgrading cannot silently start empty progress. Existing broken files move with it. If both old and new files exist, the newer file by modification time wins; the other is kept as `.broken` for a manual merge. See [Progress files](../architecture/progress-files.md#moving-existing-japanese-progress).

## Trust

The server checks that the progress it gets is valid, but can't tell whether the player changed it. A player can edit their file to master every item, so their items never lock and are never removed on a fail. That only takes away from their own learning.

The other way round, the player's game saves whatever progress the server sends. A server running a changed mod could empty or change a player's progress everywhere they play, not only on that server. Players only lose that if they join such a server.

## Settings on each server

[Settings](settings.md) belong to each server and are shared by all learning languages, so a player's progress meets other levels and intervals in other places. A level above a server's top level counts as mastered there, see [Locking](srs-state.md#locking). The stored level only changes when that server changes the item, and a locks-at time stays as the server that set it worked it out.
