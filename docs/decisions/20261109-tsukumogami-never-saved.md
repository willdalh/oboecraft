# ADR-20261109: The tsukumogami is never saved with the world

## Status
Accepted

## Context
The tsukumogami follows a player at night while their overdue items pile up, see [Tsukumogami](../design/tsukumogami.md) and [RFC-20261109](../rfcs/20261109-tsukumogami.md). Minecraft saves mobs with the chunk they are in, so a mob outlives the reason it was made: it stays in a world after its owner logs out, comes back after a restart, and can end up in a chunk its owner never returns to. Whether a tsukumogami should exist already follows from things the server knows: the player's progress, what they carry, the time of day, the dimension, the game mode and the difficulty. Once worlds hold saved tsukumogami, changing this would mean cleaning them out of players' worlds, so it is hard to reverse.

## Decision
The tsukumogami's entity type is never saved (`noSave`). Whether a player's tsukumogami should exist is worked out again from their progress and the world, once a second at night and right after each of their reviews. Nothing about it is written to the world or the progress file.

- Its owner, its orbiting items and its health are set when it is made and kept up to date while it exists, never read back from a save.
- That it was killed tonight, and when it may come back after it was reviewed away or its owner arrived, are only kept in memory while the server runs. A restart forgets them.
- One whose chunk unloads is gone, and the next check makes a new one if it should still exist.

Alternatives considered:

- Saving it like other mobs, with its owner's UUID. It would come back after a restart without a check, but could be left behind in worlds, follow nobody, and disagree with the owner's progress, which lives on their computer and can change between sessions.
- Saving only that it was killed tonight, in the world or the player's data. A restart couldn't bring back a killed tsukumogami early, but the mod would need a new saved format for a few hours of state, and progress files are about learning, not the world.

## Consequences
- A world never holds a tsukumogami, so removing the mod or turning the tsukumogami off leaves nothing behind.
- Logging out, dying, leaving the Overworld or changing to creative mode removes it, and it comes back by the normal rules afterwards. Logging out never gets rid of it for good.
- A server restart in the middle of the night forgets that it was killed, so it can come back that night.
- Its health starts full whenever it is made again, for example after its chunk unloaded.
