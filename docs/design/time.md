# Time

Intervals and bans run in real-world time, also when the player is offline.

## The server clock

The server's clock decides the time. In singleplayer that is the computer's clock, and changing it to cheat is left to the player.

All game code reads the current time from one clock. In the game it is the system clock. Tests replace it with a clock they control, so they can jump ahead ten minutes and check that an item has locked, without waiting.

Times are stored to the millisecond, in UTC.

## The client

The client's lock check needs the server's time, but the client's own clock can be wrong by minutes. So when the server sends a player their SRS states, it also sends its current time. The client remembers the difference between that and its own clock, and adds it whenever it needs the server's time.

A small error is fine here, since the server checks again and has the final say. An item locking a second early or late on the client only decides whether the answer menu opens at once or after the server's reply.
