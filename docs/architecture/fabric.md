# Fabric adapter

The `fabric` module connects the [core](core.md) to Minecraft, in `io.github.willdalh.oboecraft.fabric`. It gathers inputs from the game, asks the core for decisions, and carries them out. This page describes its parts. What the rules do is in [`docs/design/`](../design/README.md).

The parts were designed in milestone 3a and built in milestone 3b, for placing blocks. Milestone 6 added a hook for every interaction and the answer menu over open menus. Milestone 7 added item displays, milestone 9 fails, punishment and bans, milestone 10 the notification after a pass, milestone 11 the game mode and anvil rules, [willdalh/oboecraft#25](https://github.com/willdalh/oboecraft/issues/25) the reading clip after a pass, and progress on the player's computer the configuration payloads and the client's `ProgressStore`. Later milestones add hooks, payloads and screens to them.

## Shared services

`OboecraftMod` sets these up when the mod starts, on both sides, and hands them out through static getters:

| Service | What it does |
|---|---|
| Name list | `NameListFile` loads `oboecraft/name_list.json` once from the mod's own resources, through Fabric's mod container, so it works the same on a dedicated server, which has no client assets. `NameListJson` in the `json` module parses it into a core `NameList`. Its format is in [Name list file](name-list-file.md). |
| Item mapping | `ItemMapping` maps an `ItemStack` to its name list entry, see [below](#item-mapping). |
| Clock | `ModClock`, the one `InstantSource` that all game code reads the time from, to the millisecond. It reads the system clock, and game tests swap in a clock they control with `setSource`. |
| Settings | No service of its own: each side's `ProgressView` has the server settings it uses, `ServerProgress` the server's own, read from the [settings file](settings-files.md) when the server starts, and `ClientProgress` the ones the server sent. `OboecraftClient` holds the client settings. |
| Locks | `Locks`, the lock check entry point, see [below](#the-lock-check-entry-point). |
| Server progress and reviews | `ProgressUploads`, `ServerProgress` and `ServerReviews`, see below. `ServerProgress` and `ServerReviews` are only used on the server thread, and `ProgressUploads` also on the network threads. |

None of these are global singletons that tests can't reach. Game tests get them from `OboecraftMod` and can replace the clock's source.

## Item mapping

`ItemMapping.entryOf(stack)` returns the stack's `Optional<NameListEntry>`:

1. Take the stack's item name, `ItemStack.getItemName()`, which ignores custom names from anvils and name tags. It is `Item.getName(stack)`, which reads the `item_name` data component, or builds the name from the stack's components for items like potions.
2. If `Component.getContents()` is a `TranslatableContents`, take `getKey()`. Otherwise the stack isn't an item.
3. Look the key up in the name list.

Milestone 3b checked these methods against Minecraft 26.3, and the game tests check the keys they give: `block.minecraft.oak_log` for an oak log, with or without a custom name, `item.minecraft.potion.effect.healing` for both Healing I and II, `item.minecraft.tipped_arrow.effect.night_vision`, and `block.minecraft.player_head.named` for a player head with a player's name.

A few items get their own key from a component too: a shield with a base color is named through `item.minecraft.shield.<color>`, and a lodestone compass through `item.minecraft.lodestone_compass`. These keys are name list data, see [Items](../design/items.md#translation-keys). If a method doesn't work for some items, only this class changes.

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

- `ServerLockHooks` acts for a `ServerPlayer`. When it stops an action, it sends a stopped stack's inventory slot again, since the client may already have used it up, and unless a review is going, it sends `ShowReview` with the location when the decision is Review. A Banned decision stops the action without `ShowReview`: the client tells the player about the ban from its own state. After a stopped menu click, Minecraft's own click handler sends the menu's real contents, so no resend is needed. Before `ShowReview`, it sends the stack's real place: the inventory slot, or the open menu's whole contents. While a review is going, `ServerLockHooks.refusedDuringReview` refuses menu clicks and, in `ServerGamePacketListenerImplMixin`, the recipe book, choosing a trade, menu buttons, dropping and swapping hands.
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

`Locks.mayPickUp` asks the shared lock check with `PICK_UP`. Item entity and arrow pickup mixins leave denied pickups on the ground. `StoppedStack` carries its decision so a denial or a ban never opens a review.

## Progress on the server

The player's client keeps their progress and sends it while the player is configured. The server holds it for the session and never saves it.

`ProgressUploads` gets each player's progress before they enter the world. It keeps at most one connection per account between configuration and the world, so the progress a player enters the world with is always the one their own connection sent. In Fabric's `ServerConfigurationConnectionEvents.CONFIGURE`, after the protocol check, `request` makes a configuration task for the player, which the event adds, and disconnects an earlier connection of the same account that hasn't entered the world yet, with Minecraft's message for a duplicate login. This happens every time the player is configured, also when they go back to configuration from the world, so the server always has the progress the client saved last. Minecraft runs the tasks one after another, and the player only enters the world once every task has finished. The task sends `RequestProgress` when it starts, and only then waits for an answer, so an answer sent before the request, or a second answer, is ignored:

- `UploadProgress` finishes the task. `ProgressUploads` holds the progress by UUID until the player joins.
- `ProgressUnavailable` disconnects the player with a message for its reason: the file is from a newer version of the mod, it can't be read, or another game on the player's computer uses it.
- No answer within 30 seconds of the request, counted in the task's ticks, disconnects the player.

A player who leaves while configured is forgotten in `ServerConfigurationConnectionEvents.DISCONNECT`. Fabric runs the configuration of a player, its receivers and often that event on the network threads, while the server ticks the configuration tasks on the server thread. So `ProgressUploads` keeps its connections in a concurrent map, the receivers only record the client's first answer with `answered`, and the task acts on it when the server ticks it: it finishes, or disconnects the player.

`ServerProgress` holds the progress of each player who is online, as core `Progress` values, keyed by UUID. Each session belongs to the player's connection, not to the `ServerPlayer`, since respawning gives the same connection a new `ServerPlayer`:

- Started when the player joins (Fabric's `ServerPlayConnectionEvents.JOIN`), before anything can be checked, with the progress `ProgressUploads` holds for them, and sent back in `ProgressSync` with the settings. A player whose progress isn't held counts as having none, so everything is locked for them. Only players placed in the world without being configured, like the game tests' players, have none.
- A session of the same player that hasn't ended when they join again is replaced.
- Ended when the player leaves (`DISCONNECT`, which only ends the session of that connection).
- Every change goes through `update(player, item, state)`, which sends the change in `StateUpdate`, so the client can save it.

## Progress on the client

`fabric.progress.ProgressStore` keeps the player's progress file in the game folder, see [Progress files](progress-files.md). It is in `src/main`, though only the client uses it, since it uses no client code and server game tests can test it there. `OboecraftClient` creates one, and each session of it belongs to one network `Connection`, which stays the same from configuration into the world. `ClientCommonPacketListenerImplAccessor` reads it from the configuration listener. Anything another connection asks is ignored, so a message or disconnect of an earlier connection that is handled late never touches the current session:

- `start`, when `RequestProgress` arrives, locks the file, reads it and sends `UploadProgress` or `ProgressUnavailable`. A missing file is empty progress. A broken file is kept as `.broken` and is empty progress too, and the player is told in chat once in the world (`ClientPlayConnectionEvents.JOIN`). A session that is still going, when the server configures the player again, ends first, so its changes are read back.
- `changed`, after each `ProgressSync` and each `StateUpdate` that `ClientProgress` applied to the current connection's progress, saves that progress 2 seconds later, so the changes that arrive in that time are saved once, as the latest progress. A failed save is logged, tried again 2 seconds later, and the player is told in chat the first time in a session.
- `end`, when the player leaves, also while configured (`ClientConfigurationConnectionEvents.DISCONNECT`), saves the latest changes and unlocks the file. `close`, when the game stops (`ClientLifecycleEvents.CLIENT_STOPPING`), ends the session and waits up to 10 seconds for the save. The game stops before it leaves the world, so the connection's own `end` comes after `close`, and is ignored like every later call.

All of its file work runs in order on one background thread of its own, `Oboecraft progress store`, so the game never waits for the disk, two saves never race, and a session's last save is done before the next session reads the file. Fabric runs configuration receivers on the network thread, so `start` hands the work to that thread at once. A failure on that thread is logged.

`ProgressStoreGameTest` checks what the client sends for a missing, broken and newer file, the lock between two instances, the saves, a failed save, calls from another connection, and calls after the game stopped, and `ServerProgressGameTest` how the server holds the progress for the session. Test players aren't configured, so `TestPlayers` starts their session with empty progress, as a client without a file sends. `ProgressStoreClientGameTest` runs the whole path in a real singleplayer client: the file is sent while the player is configured, locked while they play, and holds the server's change after they leave. `ProgressUploadsGameTest` runs `ProgressUploads` with configuration connections that go nowhere, answering from a thread of its own as Fabric does: the progress, answers before the request and second answers, a client that can't send its progress, the timeout, and a second connection of the same account.

`ClientProgress` holds what the server sent on the current connection: the SRS states, the server's time from the latest message, and the `System.nanoTime` value when it arrived. It keeps these in one immutable snapshot that also records the connection it came from. States from an earlier connection are never used, so the client's locks are off on a new connection until its `ProgressSync` arrives, even if a message from the old connection is handled late. The snapshot is also cleared when the player leaves a world or server. It never changes a state on its own: only messages from the server do.

## Item displays

`client.display.ItemDisplay` gathers the player and progress inputs and calls `Locks.display` or `Locks.closeToLocking`. The `Locks` instance owns item mapping and the game-mode/connection guards for both displays and interactions, and both read the settings from the same `ProgressView`. Display decisions come from core `DisplayRules`, independently of interaction permissions.

`GuiMixin` captures one immutable `ProgressView` from `ClientProgress.capture()` around each GUI frame. This reads the current connection and volatile snapshot once and estimates server time once. Every display call in that frame reuses it. Calls outside normal frame extraction capture a view for that call. The slot marker asks only for the boolean threshold, without building a full display result or timer. Nothing survives the frame, so sync, connection and time changes are reflected on the next frame.

Tooltip masking is scoped to drawing on the client thread, using `TooltipDisplayContext`'s Java scoped values. Bindings nest, restore automatically on exceptions, and do not reach background search tasks:

- `ScreenTooltipMixin` scopes the UI's `Screen.getTooltipFromItem` helper, including recipe-button and ghost-slot tooltips. `ContainerTooltipMixin` scopes menu tooltip extraction with the hovered stack, and `GraphicsTooltipMixin` scopes direct stack tooltip assembly.
- Inside these scopes only, `ClientItemStackMixin` masks `getHoverName`. This covers the title and nested item names in container contents. `GraphicsTooltipMixin` also scopes deferred tooltip drawing, so bundle captions are masked when their names are read later. Outside drawing, names and raw `getTooltipLines` are unchanged, including for search indexes on background threads.
- `GraphicsTooltipMixin` appends a separate `ClientLockTimerTooltip` to the assembled client components. `ItemStack.getTooltipImage` and its returned type are unchanged; bundle previews and any other vanilla image component remain separate. The timer follows the normal content so a bundle's selected-item caption cannot overlap it.
- `HudMixin` masks only the selected-item label in `Hud.extractSelectedItemName`.
- `ClientDisplayHooks` registers Fabric's `ExtractItemDecorationsCallback` to draw the marker without replacing count, durability or cooldown drawing. `TimerText` formats two nonzero units with a translated separator.

All rendering classes are in `src/client`. Core tests check display classification, intervals and marker boundaries. `DisplayGameTest` checks that display and interactions use the same custom settings from the view. `ItemDisplayClientGameTest` checks actual tooltip, HUD and inventory rendering, nested container and bundle names, unchanged raw tooltip/image getters, background search indexing, scope cleanup, captured frame inputs, and English/Japanese labels. It also checks state/time updates, modes and missing sync and saves inventory screenshots.

See [Display](../design/display.md) for the visual rules. No payloads or saved fields were added.

## Reviews on the server

`ServerReviews` holds each player's active review, the review id, item and trigger. The client picks the review id when it opens an answer menu, and every message about the review carries it, both ways. So a late reply about an earlier review never acts on a newer answer menu, and a late answer or close for an earlier review never acts on a newer review. `ServerReviews`:

- starts a review when the client asks with `StartReview`, after reading the stack at the given location itself and asking core `ReviewRules.start`. The lock check is asked again for the stack there, whether the server stopped the action and sent `ShowReview` or the client stopped it itself, and a stack it allows, like any stack in creative mode, is refused as not locked. The location doesn't say what the client tried, so the second check uses `USE`: the lock check treats every interaction alike except dropping, which the client only stops when the drop setting is on, and then like every other. The client only asks when it has no answer menu open, so a review that is still going is one the client has left, and the new one replaces it. A player without a progress session, for example while they are being disconnected, is refused,
- passes each answer and Don't know to core `ReviewRules`, stores the new state through `ServerProgress`, and sends the outcome. A wrong first answer is stored as a wrong attempt. On a fail, it first removes part of the current view, see below, then stores the failed state and ends the review. A start of a banned item is refused as banned, after a `StateUpdate` with its state,
- cancels the review when the player closes its answer menu, dies or leaves, when the menu its trigger needs closes on the server, and when the player changes to a game mode where `Locks.active` is false, like creative mode. Every way a menu closes on the server ends in `ServerPlayer.doCloseContainer`, which `ServerPlayerMixin` hooks to call `ServerReviews.menuClosing`, and every game mode change goes through `ServerPlayer.setGameMode`, which it hooks to call `ServerReviews.gameModeChanged`.

Each active review has its [trigger](../design/reviews.md#the-trigger), a sealed `Trigger` in `fabric.review` with `Inventory`, `Container(containerId)` and `Crafting(containerId)`. `Trigger.of(player, location)` works it out when the review starts, from the player's open menu and the location: a crafting result slot is `Crafting`, anything else with a menu open is `Container`, and with only the inventory open it is `Inventory`. `isReviewing(player)` tells the click hook to refuse clicks.

A `Crafting` trigger also holds the core `Recipe` it noted when the review started: `Trigger.of` counts each stack in the menu's input slots as one use of its item. The input slots, and the container slots of the current view, are the slots `MenuSlots.isInCurrentView` accepts: every slot of the menu that isn't the player's inventory, an armor slot, like a mount's saddle and armor, or a result, including a trade's result.

On a fail, `CurrentView.of(player, trigger, itemMapping)` gathers the slots of the [current view](../design/punishment.md#the-current-view): the slots of the trigger's menu that `MenuSlots.isInCurrentView` accepts, or with an inventory trigger those of the inventory's own menu, which are its crafting grid, then the inventory's 36 slots and the offhand, and the carried stack. Worn armor is never in it. `counts()` adds up each item there, which `ServerReviews` hands core `Punishment` with the level before the fail, and `remove(counts)` takes the result from the stacks in that order, through `Container.setItem` so a crafting grid updates its result, and sends the changed slots.

## The answer menu

`AnswerScreen` is in `src/client`. It is a `Screen` with Minecraft's normal `EditBox`, so the keyboard's kanji conversion (IME) works. It shows the stack's icon without its name, the item's level, the typing bar and a hint line with the keys. It doesn't pause the game.

The answer menu opens over the game, or over the screen of the player's open menu, like a chest or the inventory. Over any other screen, like the chat, it doesn't open, and the player tries again. Over a menu's screen, it replaces that screen without closing the menu: the client sends no close, so the menu stays open on the server, and `player.containerMenu` stays the same on the client. Opening a screen calls `removed()` on the one it replaces, and for a menu's screen that runs the menu's `removed` on the client, which empties result slots like the crafting grid's. So `GuiMixin` skips that call when an `AnswerScreen` replaces the screen it opened over. When the answer menu closes, it sets that screen again, which builds its widgets again, if the menu is still open. If the server closed the menu meanwhile, its close message already closed every screen. It doesn't draw the menu's screen behind it.

The screen only sends what the player does and shows what the server sends. It never decides a pass or fail. Closing it in any way other than the server ending the review sends `CloseReview`.

Since 26.3, Minecraft hands every key event from SDL3 to the screen, and doesn't check whether the IME is converting. The IME's text that isn't confirmed yet reaches the screen as a `PreeditEvent`, and the answer menu ignores every key while one is shown, since the keys belong to the conversion. So an Enter that confirms a conversion never submits the answer, an Esc that cancels it never closes the menu, and a Backspace never deletes confirmed text, on any platform, whether or not SDL3 also passes those keys on. The client game test checks Enter and Esc with a simulated conversion.

`ClientReviews` opens the answer menu and handles the server's review messages. After a pass it closes the answer menu, hands the Japanese name to the notification, see [below](#the-notification), and plays the item's reading clip, see [Reading clips](#reading-clips). After a fail the answer menu stays open with the Japanese name and reading, ignores every action, and closes with Answer or Close without telling the server, since the review is over. It tracks which keys are held, since Minecraft sends a held key's repeats as presses, and only a new press closes it, at least half a second after the fail. Don't know only acts while the typing bar is empty, so Delete reaches the text box otherwise. When the client's lock check decides Banned, when right-clicking a banned stack in the inventory, and after a refusal because of a ban, it shows how long the ban has left above the hotbar, from the item's banned-until time and the estimated server time.

The answer menu reads the client settings when it builds its widgets. With clickable buttons on, it adds a button for each action it can do now, labeled with the action's name from the Controls screen, and builds its widgets again when the server's level arrives or the review fails, since the buttons depend on them. A button runs the same code as its key and gives the typing bar its focus back. The buttons are as wide as the longest label, in one row, or more rows on a narrow screen.

## The notification

`client.display.PassNotification` holds the Japanese name the notification shows and when it was shown, from Minecraft's monotonic `Util.getMillis`. `ClientReviews` calls `show` with the name from `ReviewAnswered` after a pass, before it looks for the review's answer menu, and a new name replaces the old one. Core `NotificationTiming` says when it is over and how opaque it is, so the timing doesn't depend on client ticks.

`GuiMixin` draws it in `Gui.extractRenderState`, after the screen and before the saving indicator, the toasts and the debug overlay, so it is on top of a menu's screen. It is skipped while `Hud.isHidden()` or an `AnswerScreen` is open. The name is drawn with `GuiGraphicsExtractor.text`, scaled to twice the normal size or less so it fits the GUI width with a margin. Its top is below the boss bars: `BossHealthOverlayAccessor` gives the number of bars, and the layout follows vanilla's, 19 GUI pixels per bar from y 12 until a third of the screen's height. Disconnecting clears it. See [Display](../design/display.md#notification) for the visual rules.

`NotificationTimingTest` checks the timing. `AnswerMenuClientGameTest` checks that a pass with a boss bar shown puts オークの原木 in the notification, that its white text is in the screenshot, and that it is gone again. `MenuClientGameTest` passes a review from the inventory and checks that the text is drawn at full brightness over the inventory screen.

## Reading clips

`client.ReadingClips` plays an item's reading clip after a pass. `ClientReviews` calls `play` with the item id from `ReviewAnswered`, next to the notification. Each clip is a sound event of its own, `oboecraft:reading.<item id>`, which the [reading clip tool](reading-clip-tool.md) lists in the mod's `sounds.json` and which plays `assets/oboecraft/sounds/reading/<item id>.ogg`. The events are only in `sounds.json`, not in Minecraft's sound event registry, since only the client plays them and nothing refers to them by holder. An id that isn't a valid sound event name, or has no event, plays nothing.

The clip is a `SimpleSoundInstance` in `SoundSource.VOICE`, relative to the listener and with no attenuation, so it isn't placed in the world. Minecraft loads a clip's file the first time it plays, so the clips cost no memory until they are heard. `ReadingClips` keeps the instance that is playing, so a new pass, or disconnecting, stops it first.

`ReadingClipsClientGameTest` checks that every name list entry has a sound event whose file is in the resources. That a pass plays the clip isn't checked by a test, since a test can't hear it, and a client in a virtual display may have no sound device to play it on.

## Anvil renaming

An anvil only renames name tags, see [Items](../design/items.md#renaming). `AnvilMenuMixin`, in `src/main` so both sides work out the same result, changes how `AnvilMenu.createResult` reads the typed name. It asks `AnvilRenaming.mayRename` once for each result, which goes by the Minecraft item, `Items.NAME_TAG`, not the name list entry, since a data pack or command can give any stack a name tag's item name. For an input it refuses, the typed name reads as the input's own name and never as blank, so the result neither gets a new name nor loses its custom name, and no level is added for naming. Every way a name reaches the anvil, including the client's rename packet, ends in `createResult`, so a modified client can't rename anything else. On the client, `AnvilScreenMixin` shows a locked item's name in the name field as `???`, through `ItemDisplay.name`, and makes the field editable only for a name tag whose name isn't hidden. `AnvilGameTest` checks the server's results, and `MenuClientGameTest` checks the name field in a real anvil screen and sends a rename packet for a sword, as a modified client could.

## Settings

`ServerProgress.useSettings` replaces the server settings and sends every online player a `ProgressSync` with their progress and the new settings, and `OboecraftMod` calls it with `ServerSettingsFile.load` in Fabric's `SERVER_STARTING`. Every rule on the server reads `ServerProgress.settings()`, and on the client `ClientProgress.settings()`, which is the defaults until a `ProgressSync` arrives for this connection, while locks aren't active anyway. The files are in [Settings files](settings-files.md).

`client.settings.SettingsScreen` is the settings screen. Mod Menu opens it through `ModMenuIntegration`, the `modmenu` entrypoint in `fabric.mod.json`. The mod only compiles against Mod Menu's API (`clientCompileOnly`), so Fabric only loads that class when Mod Menu is installed, and `fabric.mod.json` suggests Mod Menu. The screen edits the server settings file when the player isn't on another server (`Minecraft.getConnection()` is null or a singleplayer server runs), and on a running built-in server it calls `useSettings` on the server thread after saving. On another server it shows `ClientProgress.settings()` without letting them change. The levels are edited as two lists separated by commas, read and written by core `LevelsText`.

`SettingsGameTest` checks the server settings file, missing, invalid and saved, and that the drop setting and the ban length change the rules on the server. `SettingsClientGameTest` saves invalid and valid settings through the screen in singleplayer, checks that the built-in server and the client use the saved settings and that the files hold them, and uses the answer menu's buttons.

## Networking

The mod uses Fabric's networking API with custom payloads, in `fabric.network`. Every payload is in `src/main`, so both sides know it. Fabric runs the play receivers on the main thread of each side, the configuration receivers on the network threads, and the server's receivers only act for the player who sent the payload.

While the player is configured, before they enter the world:

| Payload | Way | When | Contents |
|---|---|---|---|
| `RequestProgress` | Server to client | The player is configured | Nothing |
| `UploadProgress` | Client to server | In answer to `RequestProgress` | Every SRS state of the player's progress that isn't `Leveled.NEW`, at most 65,536. It is registered with Fabric's `registerLarge`, which splits it into parts, up to 1 MiB less 16 KiB in all, so the server can send it back in `ProgressSync`, whose largest payload is 1 MiB. |
| `ProgressUnavailable` | Client to server | In answer to `RequestProgress`, when the client can't send the progress | Why. A reason that doesn't exist fails to decode. |

The two answers are the `ProgressAnswer`s, so the server's receivers hand either to `ProgressUploads.answered`.

When the player is in the world, server to client:

| Payload | When | Contents |
|---|---|---|
| `ProgressSync` | The player joins, and the server settings change | Every SRS state that isn't `Leveled.NEW`, and the server settings. A settings value that fails the core checks fails to decode. |
| `StateUpdate` | An SRS state changes, or before `ReviewRefused` because the item isn't locked | The item id and its state |
| `ShowReview` | The server stopped an action because the item is locked | The stack's location. The client opens the answer menu for it, unless an answer menu or another screen than the open menu's is open. |
| `ReviewStarted` | A review starts | The review id, item id, level and attempts left. Level 0 is a lesson. If the item isn't the one the answer menu shows, the client's inventory was out of date, and the menu closes. |
| `ReviewRefused` | A review can't start | The review id, and why: not locked, the stack is gone or its location isn't valid, or the player has no progress session. Or the item is banned. A refusal because the item isn't locked or is banned comes right after a `StateUpdate` with its state. |
| `ReviewAnswered` | After an answer or Don't know | The review id, pass, wrong, lesson or fail, and the item id, Japanese name and reading after a pass, a fail or a lesson reveal |
| `ReviewCancelled` | The server cancels the review, or the client answers in a review that isn't going | The review id |

Every server to client payload also carries the server's current time, so the client keeps its idea of the server's time up to date.

When the player is in the world, client to server:

| Payload | When | Contents |
|---|---|---|
| `StartReview` | The client opens an answer menu | A new review id, and the stack's location. Never the item id or the trigger, which the server works out itself, and only at a location where an action was stopped. |
| `SubmitAnswer` | The player presses Enter with text in the typing bar | The review id, and the typed text, at most 64 characters. Longer text is refused when it is decoded. |
| `CloseReview` | The player closes the answer menu | The review id |

`ReviewAction` carries a review id and a checked action enum (show answer, don't know, known, ignore). Show answer only reveals the answer at level 0. Don't know reveals it at level 0, and is a fail from level 1. `RevertItem` carries an item id, checked against the sender's progress and the name list.

Protocol 7 adds the configuration payloads for the player's progress. Protocol 6 adds the item id to the answer in `ReviewAnswered`, for the reading clip. Protocol 5 adds the server settings to `ProgressSync`. Protocol 3 adds tagged SRS states and the lesson result. Protocol 4 adds the demoted flag, wrong attempts and banned-until time to leveled states, the attempts left to `ReviewStarted`, the fail outcome, and the banned refusal. `ReviewAnswered` carries the item id, Japanese name and reading after a pass, a fail or a lesson reveal. Known and Ignore end the review with `ReviewCancelled` after the state update.

`ReviewKeys` registers Controls bindings. Answer hints are wrapped once during screen initialization and when the server changes the review level. Inventory right-click intercepts an empty-cursor pickup before vanilla splits the stack. `WordListScreen` reads the immutable client progress snapshot, filters into separate known, ignored and mastered tabs, and refreshes when the server changes that snapshot. The filtered, sorted entries are cached until the progress snapshot or selected tab changes; paging and selection only rebuild widgets. It sends Revert requests and never edits progress locally.

A `StackLocation` is sent as its kind and its numbers. Decoding refuses a kind that doesn't exist, which disconnects the sender.

Both sides must run the same version of the mod's network protocol, since the item ids and payloads must match. `ProtocolCheck.PROTOCOL_VERSION` goes up whenever a payload or its meaning changes. The version is part of the id of a configuration payload, `oboecraft:protocol/<version>`, which is never sent: a client with the mod registers a receiver for it, so Fabric tells the server which version the client has. In Fabric's `ServerConfigurationConnectionEvents.CONFIGURE`, which runs once Fabric knows the client's payloads, the server disconnects a player whose client can't receive that id, with a message saying which protocol version the server needs. This covers clients without the mod and clients with another protocol version.

## Messages

Text the server sends, like the disconnect messages, uses `Component.translatableWithFallback`, so a player without the mod still sees the English text. The client's language files are in `src/client/resources/assets/oboecraft/lang`, in English and Japanese.
