# Fabric adapter

The `fabric` module connects the [core](core.md) to Minecraft, in `io.github.willdalh.oboecraft.fabric`. It gathers inputs from the game, asks the core for decisions, and carries them out. This page describes its parts. What the rules do is in [`docs/design/`](../design/README.md).

The parts were designed in milestone 3a. Until milestone 3b is merged, this page describes what 3b builds, and 3b updates it where the code ends up different. Class names are a guide, and 3b may pick others that fit the code better.

## Shared services

`OboecraftMod` sets these up when the mod starts, on both sides:

| Service | What it does |
|---|---|
| Name list | Loaded once from the mod's own resources, through Fabric's mod container, so it works the same on a dedicated server, which has no client assets. The file is parsed here into core `NameListEntry` values. |
| Item mapping | Maps an `ItemStack` to its `ItemId`, see [below](#item-mapping). |
| Clock | One `InstantSource` that all game code reads the time from. It is `InstantSource.system()` in the game, and game tests swap in a clock they control. |
| Settings | Holds the current core `Settings`. It is `Settings.DEFAULTS` until milestone 12 loads the file on the server and sends it to the client. |
| Locks | The lock check entry point, see [below](#the-lock-check-entry-point). |

None of these are global singletons that tests can't reach. Game tests get them from `OboecraftMod` and can replace the clock.

## Item mapping

`ItemMapping.itemOf(stack)` returns the stack's `Optional<ItemId>`:

1. Take the stack's item name, `ItemStack.getItemName()`, which ignores custom names from anvils and name tags.
2. If its contents are a `TranslatableContents`, take the key. Otherwise the stack isn't an item.
3. Look the key up in the name list.

The exact Minecraft methods are checked against 26.3 when 3b writes this. If one doesn't work for some items, only this class changes.

## The lock check entry point

`Locks.check(player, stack, interaction)` is in `src/main`, so both sides use the same code. It:

1. maps the stack to an item,
2. gets the item's SRS state from the side's progress, through a `ProgressView` that `ServerProgress` and `ClientProgress` both implement,
3. gets the time: the clock on the server, the clock plus the server offset on the client,
4. works out whether locks are active from the player's game mode, and
5. calls core `LockCheck.check` and returns its decision.

Every interaction hook calls this and nothing else. A hook only decides what stopping the action looks like: returning a failed `InteractionResult`, cancelling a slot click, and so on.

Hooks use Fabric API events where they exist, like `UseBlockCallback` for placing, and mixins where they don't. On the client, a hook that gets **review** opens the answer menu and sends a start review request. On the server, a hook that gets **review** or **banned** for an action the client let through also tells the client what to show.

## Progress on the server

`ServerProgress` holds the progress of each player who is online, as core `Progress` values:

- Loaded when the player joins, before anything can be checked.
- Saved when the player leaves, when the world saves, and when the server stops.
- Every change goes through it, so it can mark the player's progress as unsaved and send the change to the client.

Saving and loading is in `ProgressFiles`, see [Progress files](progress-files.md).

## Progress on the client

`ClientProgress` holds the SRS states the server sent, and the difference between the server's clock and the client's. It is cleared when the player leaves a world or server. It never changes a state on its own: only messages from the server do.

## Reviews on the server

`ServerReviews` holds each player's active review: the item, the [trigger](../design/reviews.md#the-trigger), and the container menu it belongs to, if any. It:

- starts a review when the client asks, after checking the stack in the given slot or hand itself and asking core `ReviewRules.start`,
- passes each answer and action to core `ReviewRules` and `AnswerChecker`, stores the new state through `ServerProgress`, and sends the outcome,
- on a fail, turns the trigger into counts from the current view for core punishment, and removes the stacks (milestone 9), and
- cancels the review when the player closes the container on the server, leaves or dies.

The trigger is a sealed type in `fabric`: `Inventory`, `Container(containerId)` and `Crafting(containerId)`. In 3b it is always `Inventory`.

## The answer menu

`AnswerScreen` is in `src/client`. It is a `Screen` with Minecraft's normal `EditBox`, so the keyboard's kanji conversion works. It keeps the screen it opened over, like a chest's, and goes back to it when the review ends. It opens with `Minecraft.setScreen`, which doesn't close the container on the server.

The screen only sends what the player does and shows what the server sends. It never decides a pass or fail.

## Networking

The mod uses Fabric's networking API with custom payloads. Every payload is in `src/main`, so both sides know it.

Server to client:

| Payload | When | Contents |
|---|---|---|
| `ProgressSync` | The player joins | The server's current time and every SRS state that isn't `Leveled.NEW` |
| `StateUpdate` | An SRS state changes | The item id and the new state |
| `ReviewStarted` | A review starts | The item id, level, whether it is a lesson, attempts left |
| `ReviewRefused` | A review can't start | The item id and why: not locked, banned (with the time left), or the stack is gone |
| `ReviewResult` | After an answer or action | The outcome, and the Japanese name and reading when the client shows them |
| `ReviewCancelled` | The server cancels the review | The item id |
| `ShowReview` | The server stopped an action the client allowed | The item id and what to show: the answer menu or the ban message |

Client to server:

| Payload | When | Contents |
|---|---|---|
| `StartReview` | The client wants to start a review | Where the stack is, a slot in the open menu or a hand, and the trigger kind. Never the item id, which the server reads from the stack itself. |
| `ReviewAction` | The player answers or uses an action | The action, and the typed text for an answer |
| `CloseReview` | The player closes the answer menu | Nothing |

The server sends its settings in milestone 12.

Both sides must run the same version of the mod's network protocol, since the item ids and payloads must match. The server checks this when a player joins, and disconnects a player who doesn't have the mod or has another protocol version, with a message saying so.
