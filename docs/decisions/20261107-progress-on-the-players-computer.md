# ADR-20261107: Progress kept on the player's computer

## Status
Accepted

## Context
Progress was shared across singleplayer worlds, but each server kept its own progress for each player, see [ADR-20261105](20261105-progress-save-format.md). A player who learns on a server and in singleplayer had to learn every item again in each place, and players want one progress that follows them. The server decides everything that matters, so moving where progress lives changes who is trusted with it. Once players have progress in one place, moving it again means moving their files, so this is hard to change later. No server used the mod yet, so no server progress needed moving.

## Decision
Each player has one progress per account on their computer, in the game folder, in the same format as before. The client sends it to the server while the player is configured, before they enter the world. The server holds it for the session, decides every change as before, and sends each change back, and the client saves it. The server stores no progress.

- Every world works this way: singleplayer, worlds opened to LAN and dedicated servers.
- The client locks its file while it is connected, so two games on one computer can't use it at the same time.
- Levels above a server's top level count as mastered there, as they already do when the settings drop levels.

Alternatives considered:

- A central online service that stores progress by account. Progress would follow the player across computers, but someone would have to host, secure and pay for it.
- Keeping progress on each server, with a setting to let players bring their own. Server owners could stop players who edit their file, but the mod would have two places for progress, and the server's saving code would stay for a case nobody had asked for.

## Consequences
- A player's progress is the same everywhere they play, but only on the computer that holds the file.
- The server trusts the progress the client sends, after checking that it is valid. A player can edit their file to master every item, so items never lock and are never removed. Server owners can't prevent that.
- The client trusts the progress the server sends back, so a server running a changed mod could change or empty a player's progress for every world, not only its own.
- The client writes progress files, which only the server did before. It does so on a background thread of its own, soon after each change and when the player leaves.
- The server has no progress files to save or back up.
