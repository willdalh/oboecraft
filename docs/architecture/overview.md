# Architecture overview

This describes the planned structure, written before any code. Once code lands, this doc is kept in sync with it.

## Core and adapters

The code follows clean architecture. The core holds the game rules as plain Java. Adapters connect the core to Minecraft and the mod loader. Dependencies point inward: adapters depend on the core, and the core never depends on adapters. This keeps the core testable without starting the game.

## Modules

The mod id is `oboecraft`. The code is split into two Gradle modules:

| Module | Package | Contents |
|---|---|---|
| `core` | `io.github.willdalh.oboecraft.core` | The game rules: levels and intervals, when an item is locked, answer checking, punishment and the name list. Plain Java with no Minecraft or Fabric imports. |
| `fabric` | `io.github.willdalh.oboecraft.fabric` | The adapter: everything that touches Minecraft and Fabric, like catching interactions, the answer menu and other screens, saving progress, networking and playing the reading aloud. Depends on `core`. |

Minecraft is not on the classpath of `core`, so the compiler enforces the boundary. Support for another mod loader would be another adapter module next to `fabric`.

## A thin core

The core makes decisions from simple inputs, like an item id, counts and timestamps, and returns results. The `fabric` module gathers the inputs from the game and carries out the results. Minecraft concepts like inventories, slots and containers are left out of the core.

For example, on a fail when crafting a diamond pickaxe at level 1, `fabric` counts 50 diamonds and 100 sticks in the current view and passes the counts and the recipe (3 diamonds, 2 sticks) to the core. The core answers: remove 15 diamonds and 20 sticks. `fabric` then removes them from the inventory and crafting grid.

## Server and client

The mod targets Minecraft: Java Edition and is installed on both the server and the client. Support includes dedicated servers and worlds hosted by a friend, including Open to LAN. Singleplayer runs on Minecraft's built-in server, so it uses the same code as a dedicated server.

- **The server decides everything that matters:** SRS states, answer checking, attempts and their reset, levels, punishment and bans.
- **The client only displays:** the answer menu, "???" names, timer bars, slot markers and the spoken reading.

The server sends each player their SRS states when they join and pushes updates when they change.

Locks are checked on both sides. The client checks first, since it knows the SRS states, and opens the answer menu at once, so a placed block doesn't flicker back. The server checks too and has the final say, which stops modified clients.

For inventory and container slots, the lock check happens before picking up the stack or starting a drag. Ground pickup does not trigger a review. A pass unlocks the item but does not automatically repeat the interrupted action. Crafting ingredients and a locked result have separate review triggers. See the [confirmed gameplay rules](../design/README.md).

Answers are checked on the server. The client sends the typed text, and the server replies with a pass, a wrong answer with the remaining attempts, or a fail. A first wrong answer is not a fail. The Japanese name is only sent to the client when it is needed: in lessons and after a fail.

The server retains remaining attempts for each player and reviewed item when the answer menu closes. Esc only closes the client window: it does not submit an answer, end the review, consume an attempt or restart the reset window. Reopening the menu resumes the same attempt state.

## Time

Intervals, bans and the five-minute attempt reset run in real-world time from the server clock. The SRS state stores when an item locks next as a timestamp, and an item is locked when the current time is at or past it. Time checks use timestamps rather than running timers. The first wrong answer sets the attempt-reset deadline five minutes ahead. Attempts reset only for reviews that have not failed, whether the answer menu is open or closed.

## Testing

Core rules get plain unit tests. The `fabric` module uses Fabric game tests where they pay off.

## Decisions

- [Stack](../decisions/20261105-stack.md)
- [Progress save format](../decisions/20261105-progress-save-format.md)
