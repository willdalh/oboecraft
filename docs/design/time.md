# Time

Intervals and bans run in real-world time, also when the player is offline.

## The server clock

The server's clock decides the time. In singleplayer that is the computer's clock, and changing it to cheat is left to the player.

All game code reads the current time from one clock. In the game it is the system clock. Tests replace it with a clock they control, so they can jump ahead ten minutes and check that an item has locked, without waiting.

The clock gives times to the millisecond, so they are the same in memory, in progress files, which store them in UTC, and in messages to the client.

## The client

The client's lock check needs the server's time, but the client's own clock can be wrong by minutes, and can jump while the game runs, for example when the computer corrects its clock. So every message from the server that carries SRS states or review replies also carries the server's current time. The client keeps the server's time from the latest message and works out the server's time now by adding how long ago that message arrived. It measures that with a timer that doesn't follow the computer's clock, like Java's `System.nanoTime`, so changing the client's clock has no effect.

A small error is fine here, since the server checks again and has the final say:

- If the client thinks an item is unlocked but the server finds it locked, the server stops the action and tells the client to open the answer menu. See [Both sides](lock-check.md#both-sides).
- If the client thinks an item is locked but the server finds it unlocked, the server refuses the review as not locked and sends the item's state and its time. The client closes the answer menu, and the action is allowed when the player tries it again.
