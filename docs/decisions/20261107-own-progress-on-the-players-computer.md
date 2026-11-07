# ADR-20261107: Own progress kept on the player's computer

## Status
Accepted

## Context
Progress was shared across singleplayer worlds, but each server kept its own progress for each player, see [ADR-20261105](20261105-progress-save-format.md). A player who learns on a server and in singleplayer had to learn every item again in each place, and players want one progress that follows them. The server decides everything that matters, so moving where progress lives changes who is trusted with it. Once players have progress in one place, moving it again means moving their files, so this is hard to change later.

## Decision
Each player has one own progress per account on their computer, in the game folder, in the same format as before. When a server uses own progress, the client sends it while the player is configured, before they enter the world. The server holds it for the session, decides every change as before, and sends each change back, and the client saves it. The server stores nothing.

- Every world works this way, also singleplayer and the host of a world opened to LAN.
- A server setting, `useOwnProgress`, is on by default. With it off, the server keeps server progress in its world folder, as before, and the client's file is left alone.
- The client locks its file while it is connected, so two games on one computer can't use it at the same time.
- Levels above a server's top level count as mastered there, as they already do when the settings drop levels.

Alternatives considered:

- A central online service that stores progress by account. Progress would follow the player across computers, but someone would have to host, secure and pay for it.
- Keeping progress on each server and copying it between them. Every server would need to reach the others, and two copies of one item would have to be merged.

## Consequences
- A player's progress is the same in singleplayer and on every server that uses own progress, but only on the computer that holds the file.
- The server trusts the progress the client sends, after checking that it is valid. A player can edit their file to master every item, so items never lock and are never removed on that server. Server owners who don't want that turn `useOwnProgress` off.
- The client writes files, which only the server did before. It does so on a background thread of its own, soon after each change and when the player leaves.
- A server that changes to or from own progress doesn't move any progress between the two places.
