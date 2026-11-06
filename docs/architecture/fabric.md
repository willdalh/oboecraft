# Fabric adapter

The `fabric` module connects the [core](core.md) to Minecraft, in `io.github.willdalh.oboecraft.fabric`. It gathers inputs from the game, asks the core for decisions, and carries them out. This page describes its parts. What the rules do is in [`docs/design/`](../design/README.md).

The parts were designed in milestone 3a and built in milestone 3b, for placing blocks. Milestone 6 added a hook for every interaction and the answer menu over open menus. Milestone 7 added item displays. Later milestones add hooks, payloads and screens to them.

## Shared services

`OboecraftMod` sets these up when the mod starts, on both sides, and hands them out through static getters:

| Service | What it does |
|---|---|
| Name list | `NameListFile` loads `oboecraft/name_list.json` once from the mod's own resources, through Fabric's mod container, so it works the same on a dedicated server, which has no client assets. `NameListJson` in the `json` module parses it into a core `NameList`. Its format is in [Name list file](name-list-file.md). |
| Item mapping | `ItemMapping` maps an `ItemStack` to its name list entry, see [below](#item-mapping). |
| Clock | `ModClock`, the one `InstantSource` that all game code reads the time from, to the millisecond. It reads the system clock, and game tests swap in a clock they control with `setSource`. |
| Settings | The current core `Settings`. It is `Settings.DEFAULTS` until milestone 12 loads the file on the server and sends it to the client. |
| Locks | `Locks`, the lock check entry point, see [below](#the-lock-check-entry-point). |
| Server progress and reviews | `ServerProgress` and `ServerReviews`, see below. They are only used on the server thread. |

None of these are global singletons that tests can't reach. Game tests get them from `OboecraftMod` and can replace the clock's source.

## Item mapping

`ItemMapping.entryOf(stack)` returns the stack's `Optional<NameListEntry>`:

1. Take the stack's item name, `ItemStack.getItemName()`, which ignores custom names from anvils and name tags. It is `Item.getName(stack)`, which reads the `item_name` data component, or builds the name from the stack's components for items like potions.
2. If `Component.getContents()` is a `TranslatableContents`, take `getKey()`. Otherwise the stack isn't an item.
3. Look the key up in the name list.

Milestone 3b checked these methods against Minecraft 26.3, and the game tests check the keys they give: `block.minecraft.oak_log` for an oak log, with or without a custom name, `item.minecraft.potion.effect.healing` for both Healing I and II, `item.minecraft.tipped_arrow.effect.night_vision`, and `block.minecraft.player_head.named` for a player head with a player's name. A few items get their own key from a component too: a shield with a base color is named through `item.minecraft.shield.<color>`, and a lodestone compass through `item.minecraft.lodestone_compass`. These keys are name list data, see [Items](../design/items.md#translation-keys). If a method doesn't work for some items, only this class changes.

## The lock check entry point

Everything in this section is in `fabric.lock`, in `src/main`, so both sides use the same code, unless it says otherwise.

An action is described as the stacks it would interact with, each a `CheckedStack`: the stack, its core `Interaction`, and its `StackLocation`. A `StackLocation` says where the stack is, so either side can find it again and the answer menu can open for it:

| Location | Meaning |
|---|---|
| `InventorySlot(index)` | A slot of the player's inventory: the hotbar, the main inventory or the offhand. Worn armor isn't in the current view, so it is never a location. A hand is the selected hotbar slot or the offhand. |
| `MenuSlot(containerId, slot)` | A slot of the player's open menu |
| `Carried(containerId)` | The stack the player carries with the mouse in the open menu |

`StackLocation.stackIn(player)` reads the stack on the caller's side, and is empty if the location isn't valid for the player, like a menu that isn't open or a slot out of range. So a location from the network is never trusted.

`Locks.firstStopped(view, player, stacks)` asks the lock check about each stack in order, and returns the first one it doesn't allow, as a `StoppedStack` with its item. For each stack it:

1. maps the stack to an item, and allows a stack that isn't one,
2. gets the item's SRS state from `view`, a `ProgressView` that `ServerProgress` and `ClientProgress` both implement,
3. gets the time from `view`: the clock on the server, and on the client the server's time worked out by `ClientProgress`, see [Time](../design/time.md#the-client),
4. works out whether locks are active from the player's game mode, and from `view.hasProgress`, which on the client is only true once `ProgressSync` has arrived for this connection, and
5. calls core `LockCheck.check` with the stack's interaction.

Two classes turn an action into its stacks, with the rules in [Lock check](../design/lock-check.md#what-the-player-tries-to-do):

- `HeldStacks`: placing, using (with the ammo of a bow or crossbow), using on an entity, attacking with a weapon, mining with a tool, and dropping the selected stack.
- `MenuClicks.stacksOf(menu, slot, button, input, player)`: a click in a menu, from Minecraft's `ContainerInput` kinds. A shift-click in the inventory is checked in `moveItemStackTo`, where the menu's own code moves the stack, with `MenuClicks.moveIntoMenu`. `MenuSlots` says what putting a stack into a slot and taking it out does, from the slot's class, its container and the menu's class, and whether a slot is a crafting result.

Every hook calls `firstStopped` through one of two classes, which only decide what stopping the action looks like:

- `ServerLockHooks` acts for a `ServerPlayer`. When it stops an action, it sends a stopped stack's inventory slot again, since the client may already have used it up, and unless a review is going, it sends `ShowReview` with the location. After a stopped menu click, Minecraft's own click handler sends the menu's real contents, so no resend is needed. Before `ShowReview`, it sends the stack's real place: the inventory slot, or the open menu's whole contents. While a review is going, `ServerLockHooks.refusedDuringReview` refuses menu clicks and, in `ServerGamePacketListenerImplMixin`, the recipe book, choosing a trade, menu buttons, dropping and swapping hands.
- `ClientReviews.stops` in `src/client` acts for the `LocalPlayer`. When it stops an action, it opens the answer menu at once and sends `StartReview`.

Hooks use mixins where Fabric API has no event at the right moment:

| Action | Server | Client |
|---|---|---|
| Placing | `ItemStackMixin` on `ItemStack.useOn` | `ClientItemStackMixin` on `ItemStack.useOn` |
| Using, equipping by use, ammo | `ItemStackMixin` on `ItemStack.use` | `ClientItemStackMixin` on `ItemStack.use` |
| Using on an entity | Fabric's `UseEntityCallback` | Fabric's `UseEntityCallback` |
| Attacking | Fabric's `AttackEntityCallback`, and `PiercingWeaponMixin` for a spear's stab | Fabric's `AttackEntityCallback`, and `MultiPlayerGameModeMixin` on `piercingAttack` |
| Mining | Fabric's `AttackBlockCallback` | Fabric's `AttackBlockCallback` |
| Dropping with the drop key | `ServerPlayerMixin` on `ServerPlayer.drop(boolean)` | `MultiPlayerGameModeMixin` on `dropItem` |
| Menu clicks | `AbstractContainerMenuMixin` on `AbstractContainerMenu.clicked` and `moveItemStackTo` | `MultiPlayerGameModeMixin` on `handleContainerInput`, and `ClientAbstractContainerMenuMixin` on `moveItemStackTo` |
| The recipe book | `ServerPlaceRecipeMixin`, which leaves locked ingredients out of what is available | None: only the server moves the ingredients |
| Choosing a trade | `MerchantMenuMixin`, which skips a locked payment | None: the server's menu contents undo the client's move |

Fabric's events run the callbacks of both sides on each side, so each callback checks whether its player is a `ServerPlayer` or a `LocalPlayer`. `ServerLockHooks.register` and `ClientLockHooks.register` register them.

Placing is checked in `ItemStack.useOn`, which is where a stack is used on a block, after the block itself had its chance to react. So right-clicking a chest or crafting table with a locked item in hand still opens it. Fabric's `UseBlockCallback` runs before the block reacts, so it would stop that too.

The client returns a failed result before it predicts placing or using, but Minecraft sends the use to the server anyway. So the server checks every use, and sends `ShowReview` whenever it stops one and no review is going. In the normal case, `StartReview` from the client has arrived first, so no `ShowReview` is sent. The client ignores `ShowReview` while an answer menu or any screen other than the open menu's is open. When the client allowed the action and the server didn't, for example because the item locked a moment before, `ShowReview` opens the answer menu.

Milestone 8 adds `Locks.checkPickUp(view, player, stack)` with core `LockCheck.checkPickUp`.

## Progress on the server

`ServerProgress` holds the progress of each player who is online, as core `Progress` values, keyed by UUID. Each entry belongs to the player's connection, not to the `ServerPlayer`, since respawning gives the same connection a new `ServerPlayer`:

- Loaded when the player joins (Fabric's `ServerPlayConnectionEvents.JOIN`), before anything can be checked, and sent in `ProgressSync`. A player whose progress isn't loaded counts as having none, so everything is locked for them.
- If an earlier session of the same player is still there when they join, its changes are saved first, and it is replaced.
- Saved when the player leaves (`DISCONNECT`, which only removes the entry of that connection), when the world saves (`ServerLifecycleEvents.AFTER_SAVE`), and when the server stops (`SERVER_STOPPING`). Only progress with unsaved changes is written. A failed save is logged and tried again at the next save.
- Every change goes through `update(player, item, state)`, which marks the player's progress as unsaved and sends the change in `StateUpdate`.

The file is read and written on the server thread. The files are a few kilobytes and are only touched when a player joins or leaves and when the world saves, which is when Minecraft saves its own player data on that thread too.

Saving and loading is in `ProgressFiles`, see [Progress files](progress-files.md).

## Progress on the client

`ClientProgress` holds what the server sent on the current connection: the SRS states, the server's time from the latest message, and the `System.nanoTime` value when it arrived. It keeps these in one immutable snapshot that also records the connection it came from. States from an earlier connection are never used, so the client's locks are off on a new connection until its `ProgressSync` arrives, even if a message from the old connection is handled late. The snapshot is also cleared when the player leaves a world or server. It never changes a state on its own: only messages from the server do.

## Item displays

`client.display.ItemDisplay` gathers the player and progress inputs and calls `Locks.display` or `Locks.closeToLocking`. The `Locks` instance owns item mapping, the game-mode/connection guards and settings for both displays and interactions. Display decisions come from core `DisplayRules`, independently of interaction permissions. Milestone 12 therefore changes one settings source for both paths.

`GuiMixin` captures one immutable `ProgressView` from `ClientProgress.capture()` around each GUI frame. This reads the current connection and volatile snapshot once and estimates server time once. Every display call in that frame reuses it. Calls outside normal frame extraction capture a view for that call. The slot marker asks only for the boolean threshold, without building a full display result or timer. Nothing survives the frame, so sync, connection and time changes are reflected on the next frame.

Tooltip masking is scoped to drawing on the client thread, using `TooltipDisplayContext`'s Java scoped values. Bindings nest, restore automatically on exceptions, and do not reach background search tasks:

- `ScreenTooltipMixin` scopes the UI's `Screen.getTooltipFromItem` helper, including recipe-button and ghost-slot tooltips. `ContainerTooltipMixin` scopes menu tooltip extraction with the hovered stack, and `GraphicsTooltipMixin` scopes direct stack tooltip assembly.
- Inside these scopes only, `ClientItemStackMixin` masks `getHoverName`. This covers the title and nested item names in container contents. `GraphicsTooltipMixin` also scopes deferred tooltip drawing, so bundle captions are masked when their names are read later. Outside drawing, names and raw `getTooltipLines` are unchanged, including for search indexes on background threads.
- `GraphicsTooltipMixin` appends a separate `ClientLockTimerTooltip` to the assembled client components. `ItemStack.getTooltipImage` and its returned type are unchanged; bundle previews and any other vanilla image component remain separate. The timer follows the normal content so a bundle's selected-item caption cannot overlap it.
- `HudMixin` masks only the selected-item label in `Hud.extractSelectedItemName`.
- `ClientDisplayHooks` registers Fabric's `ExtractItemDecorationsCallback` to draw the marker without replacing count, durability or cooldown drawing. `TimerText` formats two nonzero units with a translated separator.

All rendering classes are in `src/client`. Core tests check display classification, intervals and marker boundaries. `DisplayGameTest` checks that display and interactions use the same custom settings instance. `ItemDisplayClientGameTest` checks actual tooltip, HUD and inventory rendering, nested container and bundle names, unchanged raw tooltip/image getters, background search indexing, scope cleanup, captured frame inputs, and English/Japanese labels. It also checks state/time updates, modes and missing sync and saves inventory screenshots.

See [Display](../design/display.md) for the visual rules. No payloads or saved fields were added.

## Reviews on the server

`ServerReviews` holds each player's active review, so far the review id and the item. The client picks the review id when it opens an answer menu, and every message about the review carries it, both ways. So a late reply about an earlier review never acts on a newer answer menu, and a late answer or close for an earlier review never acts on a newer review. `ServerReviews`:

- starts a review when the client asks with `StartReview`, after reading the stack at the given location itself and asking core `ReviewRules.start`. When `ServerLockHooks.stops` sends `ShowReview`, it calls `serverStopped(player, location)`, and `ServerReviews` remembers that location until the next `StartReview`. A start at that location is the client answering the server's stop. A start anywhere else is for an action the client stopped itself, which the server never saw, so the lock check is asked again for the stack there, and a stack it allows is refused as not locked. The location doesn't say what the client tried, so the second check uses `USE`: the lock check treats every interaction alike except dropping, which the client only stops when the drop setting is on, and then like every other. The client only asks when it has no answer menu open, so a review that is still going is one the client has left, and the new one replaces it. A player whose progress isn't loaded, for example while they are being disconnected, is refused,
- passes each answer to core `ReviewRules`, stores the new state through `ServerProgress`, and sends the outcome,
- cancels the review when the player closes its answer menu, dies or leaves, and when the menu its trigger needs closes on the server. Every way a menu closes on the server ends in `ServerPlayer.doCloseContainer`, which `ServerPlayerMixin` hooks to call `ServerReviews.menuClosing`.

Each active review has its [trigger](../design/reviews.md#the-trigger), a sealed `Trigger` in `fabric.review` with `Inventory`, `Container(containerId)` and `Crafting(containerId)`. `Trigger.of(player, location)` works it out when the review starts, from the player's open menu and the location: a crafting result slot is `Crafting`, anything else with a menu open is `Container`, and with only the inventory open it is `Inventory`. `isReviewing(player)` tells the click hook to refuse clicks.

Milestone 9 adds the recipe's ingredients for a crafting trigger, and turns the trigger into counts for core punishment on a fail.

## The answer menu

`AnswerScreen` is in `src/client`. It is a `Screen` with Minecraft's normal `EditBox`, so the keyboard's kanji conversion (IME) works. It shows the stack's icon without its name, the item's level, the typing bar and a hint line with the keys. It doesn't pause the game.

The answer menu opens over the game, or over the screen of the player's open menu, like a chest or the inventory. Over any other screen, like the chat, it doesn't open, and the player tries again. Over a menu's screen, it replaces that screen without closing the menu: the client sends no close, so the menu stays open on the server, and `player.containerMenu` stays the same on the client. Opening a screen calls `removed()` on the one it replaces, and for a menu's screen that runs the menu's `removed` on the client, which empties result slots like the crafting grid's. So `GuiMixin` skips that call when an `AnswerScreen` replaces the screen it opened over. When the answer menu closes, it sets that screen again, which builds its widgets again, if the menu is still open. If the server closed the menu meanwhile, its close message already closed every screen. It doesn't draw the menu's screen behind it.

The screen only sends what the player does and shows what the server sends. It never decides a pass or fail. Closing it in any way other than the server ending the review sends `CloseReview`.

Since 26.3, Minecraft hands every key event from SDL3 to the screen, and doesn't check whether the IME is converting. The IME's text that isn't confirmed yet reaches the screen as a `PreeditEvent`, and the answer menu ignores every key while one is shown, since the keys belong to the conversion. So an Enter that confirms a conversion never submits the answer, an Esc that cancels it never closes the menu, and a Backspace never deletes confirmed text, on any platform, whether or not SDL3 also passes those keys on. The client game test checks Enter and Esc with a simulated conversion.

`ClientReviews` opens the answer menu and handles the server's review messages. After a pass it shows the Japanese name and reading above the hotbar, until milestone 10 adds the notification and the spoken reading.

## Networking

The mod uses Fabric's networking API with custom payloads, in `fabric.network`. Every payload is in `src/main`, so both sides know it. Fabric runs the receivers on the main thread of each side, and the server's receivers only act for the player who sent the payload.

Server to client:

| Payload | When | Contents |
|---|---|---|
| `ProgressSync` | The player joins | Every SRS state that isn't `Leveled.NEW` |
| `StateUpdate` | An SRS state changes, or before `ReviewRefused` because the item isn't locked | The item id and its state |
| `ShowReview` | The server stopped an action because the item is locked | The stack's location. The client opens the answer menu for it, unless an answer menu or another screen than the open menu's is open. |
| `ReviewStarted` | A review starts | The review id, item id and level. Later: whether it is a lesson and attempts left. If the item isn't the one the answer menu shows, the client's inventory was out of date, and the menu closes. |
| `ReviewRefused` | A review can't start | The review id, and why: not locked, the stack is gone or its location isn't valid, or the player's progress isn't loaded. A refusal because the item isn't locked comes right after a `StateUpdate` with its state. Later: banned. |
| `ReviewAnswered` | After an answer | The review id, pass or wrong, and the Japanese name and reading after a pass |
| `ReviewCancelled` | The server cancels the review, or the client answers in a review that isn't going | The review id |

Every server to client payload also carries the server's current time, so the client keeps its idea of the server's time up to date.

Client to server:

| Payload | When | Contents |
|---|---|---|
| `StartReview` | The client opens an answer menu | A new review id, and the stack's location. Never the item id or the trigger, which the server works out itself, and only at a location where an action was stopped. |
| `SubmitAnswer` | The player presses Enter with text in the typing bar | The review id, and the typed text, at most 64 characters. Longer text is refused when it is decoded. |
| `CloseReview` | The player closes the answer menu | The review id |

Milestones 8 and 9 add a payload for each other action, like Don't know. The server sends its settings in milestone 12.

A `StackLocation` is sent as its kind and its numbers. Decoding refuses a kind that doesn't exist, which disconnects the sender.

Both sides must run the same version of the mod's network protocol, since the item ids and payloads must match. `ProtocolCheck.PROTOCOL_VERSION` goes up whenever a payload or its meaning changes. The version is part of the id of a configuration payload, `oboecraft:protocol/<version>`, which is never sent: a client with the mod registers a receiver for it, so Fabric tells the server which version the client has. In Fabric's `ServerConfigurationConnectionEvents.CONFIGURE`, which runs once Fabric knows the client's payloads, the server disconnects a player whose client can't receive that id, with a message saying which protocol version the server needs. This covers clients without the mod and clients with another protocol version.

## Messages

Text the server sends, like the disconnect messages and the message about a broken progress file, uses `Component.translatableWithFallback`, so a player without the mod still sees the English text. The client's language files are in `src/client/resources/assets/oboecraft/lang`, in English and Japanese.
