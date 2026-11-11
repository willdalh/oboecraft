# Architecture overview

This describes how the code is structured. It is kept in sync with the code.

More detail on each part:

- [Core](core.md): the packages and types of the game rules.
- [Fabric adapter](fabric.md): item mapping, the lock check entry point, progress, reviews, the answer menu, item displays, the notification and networking.
- [Progress files](progress-files.md): where progress is saved and the file format.
- [Settings files](settings-files.md): where the server and client settings are saved and their formats.
- [Name list file](name-list-file.md): the format of the name list that ships with the mod.
- [Name list tool](name-list-tool.md): the tool that writes the name list from Minecraft's supported language files.
- [Reading clip tool](reading-clip-tool.md): the tool that makes the reading clips with VOICEVOX.

The parts on those pages were designed in milestone 3a and built in milestone 3b. Milestone 4 added the name list file format and answer checking, milestone 5 the name list tool and the full name list, milestone 6 the lock check for every interaction, milestone 7 item displays, milestone 10 the notification after a pass, milestone 11 the game mode and anvil rules, milestone 12 the settings files, sync and screen, [willdalh/oboecraft#25](https://github.com/willdalh/oboecraft/issues/25) the reading clips and their tool, progress on the player's computer the client's progress files, see [ADR: Progress kept on the player's computer](../decisions/20261107-progress-on-the-players-computer.md), and [willdalh/oboecraft#51](https://github.com/willdalh/oboecraft/issues/51) the tsukumogami, a mob made with Minecraft's own models and animations.

## Core and adapters

The code follows clean architecture. The core holds the game rules as plain Java. Adapters connect the core to Minecraft and the mod loader. Dependencies point inward: adapters depend on the core, and the core never depends on adapters. This keeps the core testable without starting the game.

## Modules

The mod id is `oboecraft`. The code is split into four Gradle modules:

| Module | Package | Contents |
|---|---|---|
| `core` | `io.github.willdalh.oboecraft.core` | The game rules: levels and intervals, when an item is locked, answer checking, punishment and the name list. Plain Java with no Minecraft or Fabric imports. |
| `json` | `io.github.willdalh.oboecraft.json` | File formats in JSON that the mod and its tools share, the [name list file](name-list-file.md) (`NameListJson`) and the [server settings file](settings-files.md) (`SettingsJson`). Plain Java and Gson, with no Minecraft or Fabric. Depends on `core`. It only compiles against Gson, so the mod uses the copy Minecraft ships, at the same version. |
| `fabric` | `io.github.willdalh.oboecraft.fabric` | The adapter: everything that touches Minecraft and Fabric, like catching interactions, the answer menu and other screens, saving progress, networking and playing the reading aloud. Depends on `core` and `json`. |
| `tools` | `io.github.willdalh.oboecraft.tools` | Tools run by hand to make the mod's data: the [name list tool](name-list-tool.md) and the [reading clip tool](reading-clip-tool.md). Depends on `core` and `json`, and isn't part of the mod. |

Minecraft is not on the classpath of `core`, so the compiler enforces the boundary. Support for another mod loader would be another adapter module next to `fabric`.

The released mod is one jar, built by `fabric`. It contains the classes of `core` and `json`, so players install a single file, the reading clips, and the license and [credits](../../CREDITS.md). In dev runs (`runClient`, `runServer`, `runGameTest`), `core` and `json` belong to the `oboecraft` mod too, so things like looking up resources through the mod work the same in dev and in the released jar. For this, `core` and `json` apply Loom's companion plugin, which only exports its classpath and adds no Minecraft or Fabric dependencies.

## Source layout

```
build.gradle.kts            Settings shared by all modules: group, Java 25 toolchain
gradle.properties           The mod version of local and PR builds, and Gradle's settings
settings.gradle.kts         The two modules and the toolchain download plugin
gradle/libs.versions.toml   All dependency and plugin versions
core/
  src/main/java             Game rules
  src/test/java             Unit tests (JUnit)
json/
  src/main/java             File formats shared by the mod and its tools
  src/test/java             Unit tests (JUnit)
tools/
  src/main/java             The name list tool and the reading clip tool
  src/test/java             Unit tests (JUnit)
  reading-clips.json        What each reading clip was made from, for the reading clip tool
fabric/
  src/main/java             Code for both sides, and code that only runs on the server
  src/main/resources        fabric.mod.json, the mixin config, the mod's icon in assets/oboecraft/icon.png, and the
                            name lists in oboecraft/name_lists/<language>.json
  src/client/java           Code that only runs on the client
  src/client/resources      The client mixin config, the language files in assets/oboecraft/lang, the reading clips in
                            assets/oboecraft/sounds/reading with their sounds.json, which also has the tsukumogami's
                            sound events, the tsukumogami's sounds in assets/oboecraft/sounds/tsukumogami, and its
                            textures in assets/oboecraft/textures/entity
  src/gametest/java         Fabric game tests, on a headless server and in a client
  src/gametest/resources    fabric.mod.json for the test mod oboecraft-gametest
```

The `fabric` module splits its source sets by side. Code in `src/main` can't use client-only Minecraft classes, which don't exist on a dedicated server, so mistakes show up as compile errors instead of server crashes. Code in `src/client` can use everything in `src/main`.

The mod has two entrypoints, listed in `fabric.mod.json`:

| Entrypoint | Class | Runs on |
|---|---|---|
| `main` | `io.github.willdalh.oboecraft.fabric.OboecraftMod` | Server and client |
| `client` | `io.github.willdalh.oboecraft.fabric.client.OboecraftClient` | Client only |

The game tests are their own small mod, `oboecraft-gametest`, which is only loaded by `runGameTest` and `runClientGameTest` and is not part of the released jar.

Mixins are listed in `oboecraft.mixins.json` (package `fabric.mixin`, both sides) and `oboecraft.client.mixins.json` (package `fabric.client.mixin`, client only).

## Build

The build uses Gradle through the wrapper and Fabric Loom's non-remapping plugin, `net.fabricmc.fabric-loom`, since Minecraft ships unobfuscated. Loom needs Gradle itself to run on Java 25. `gradle/gradle-daemon-jvm.properties` asks for that, and the compile toolchain asks for Java 25 too. If Java 25 isn't installed, Gradle downloads it.

Dependency and plugin versions live in `gradle/libs.versions.toml`. The mod version is `0.0.0-dev` from `gradle.properties`, unless a release build passes its own with `-Pversion`. A fixed dev version can't be mistaken for a released one, and the build doesn't need git or the tags, which shallow checkouts don't have. The mod version and the Minecraft and Fabric Loader versions in `fabric.mod.json` are filled in from there when the jar is built, and the jar is named `oboecraft-<version>.jar`.

GitHub Actions runs two workflows. Build (`.github/workflows/build.yml`) builds and tests every PR and every push to `main`, and tests `.github/scripts/next-version.sh`. Release (`.github/workflows/release.yml`) only runs when someone starts it by hand on `main`. It works out the next version from the highest `vX.Y.Z` tag with `.github/scripts/next-version.sh`, builds the mod with it and publishes a GitHub release with the mod jar. The job that builds can only read the repository, and the job that creates the release can write to it but runs none of the project's code. The mod is published only there, not on Modrinth or CurseForge, see [AGENTS.md](../../AGENTS.md#releases).

## A thin core

The core makes decisions from simple inputs, like an item id, counts and timestamps, and returns results. The `fabric` module gathers the inputs from the game and carries out the results. Minecraft concepts like inventories, slots and containers are left out of the core.

Every action the mod may stop goes through one lock check in the core, on both sides, so rules like creative mode, ignored items and bans are decided in one place. See [Lock check](../design/lock-check.md).

For example, on a fail when crafting a diamond pickaxe at level 1, `fabric` counts 50 diamonds and 100 sticks in the current view and passes the counts and the recipe (3 diamonds, 2 sticks) to the core. The core answers: remove 15 diamonds and 20 sticks. `fabric` then removes them from the inventory and crafting grid.

## Server and client

The mod is installed on both the server and the client. Singleplayer runs on Minecraft's built-in server, so it uses the same code as a dedicated server.

- **The server decides everything that matters:** SRS states, answer checking, levels, punishment and bans, and when the tsukumogami appears and what it does.
- **The client only displays:** the answer menu, "???" names, timer bars and slot markers.

The server sends each player their SRS states when they join and pushes updates when they change.

Locks are checked on both sides. The client checks first, since it knows the SRS states, and opens the answer menu at once, so a placed block doesn't flicker back. The server checks too and has the final say, which stops modified clients.

Answers are checked on the server. The client sends the typed text, and the server replies with a pass or fail. The correct name is only sent to the client when it is needed: in lessons and after a fail.

## Time

Intervals and bans run in real-world time from the server clock. The SRS state stores when an item locks next as a timestamp, and an item is locked when the current time is at or past it. No timers need to run.

The core never reads a clock: its rules take the current time as an input. In `fabric`, all code reads the time from one clock, which game tests replace with one they control. The client works out the server's time from the time in the server's latest message, plus how long ago it arrived. See [Time](../design/time.md).

## Testing

Core rules get plain unit tests with JUnit, in `core/src/test`. The `fabric` module uses Fabric game tests where they pay off, in `fabric/src/gametest`. There are two kinds:

- **Game tests** run on a headless server. A game test is a public method annotated with `@GameTest` that takes a `GameTestHelper`, in a class listed under the `fabric-gametest` entrypoint of the test mod. Tests that need a player use one that joins over a connection that goes nowhere (`TestPlayers`), so the mod loads and saves its progress as for a real player.
- **Client game tests** run a real client and drive it with key presses and mouse clicks. A client game test is a class that implements `FabricClientGameTest`, listed under the `fabric-client-gametest` entrypoint. Singleplayer passes payloads without encoding them, so a game test encodes and decodes each payload too.

`./gradlew build` runs the unit tests of `core`, `json` and `tools`, and the game tests. The game test results are written as a JUnit report to `fabric/build/test-results/gameTest/`, and the game test server's logs to `fabric/build/run/gameTest/logs/`.

`./gradlew runClientGameTest` runs the client game tests. It needs a display. Without one, run it in a virtual display with Mesa's software Vulkan driver, as CI does: Minecraft can't get an OpenGL context from Xvfb's software GLX, which has no sRGB framebuffer, so it falls back to Vulkan. Screenshots the tests take are in `fabric/build/run/clientGameTest/screenshots/`.

GitHub Actions runs both on every PR and on every push to `main` (`.github/workflows/build.yml`), and uploads the reports, logs, screenshots and any crash reports.

### Game test pitfalls

- **Wait for progress-store startup before testing session locks.** Each `ProgressStore` queues legacy migration on its file thread. With two stores in one test, drain both queues with `end(...).join()` before starting either session, then assert the first upload succeeded. Otherwise a startup migration can temporarily hold the lock the first session needs.
- **Keep queried mobs inside the test structure.** The runner loads the structure's chunks before starting; an entity outside them may be hidden from world queries. The default empty structure is only 8×8×8. The 32-block threat-range test uses the larger `oboecraft-gametest:threat_range` structure so all its positions are loaded.
- **Test players can't take damage.** No client has loaded the world for a player made by `TestPlayers`, so ordinary damage won't kill them. To exercise death handling, call `player.die(player.damageSources().genericKill())` directly, as `TsukumogamiGameTest` does.
- **Test players join at the world's origin.** Move them into the test area before anything that needs nearby ground, such as finding a place for a mob to spawn. Use coordinates relative to the test structure, for example `player.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)))`.
- **The mod's clock and the world's time are shared by all game tests.** Change them only in a test that completes within one tick, and restore them in `finally`, even if an assertion fails. Don't leave them changed across scheduled ticks. `TsukumogamiGameTest.atMidnight` shows this: it restores the Overworld's previous time through `OverworldTime` and resets `OboecraftMod.clock()` to `InstantSource.system()`.

### Client game test pitfalls

- **Don't create item stacks in static fields.** Test classes load before the game is ready for `ItemStack` construction. Keep `Item` references in static fields and create fresh stacks inside the test, as `TsukumogamiClientGameTest` does.
- **Wait on what the client sees.** A `context.waitFor` predicate runs on the client thread. Don't call `computeOnServer` or otherwise ask the server for anything inside it. Do server setup through `runOnServer` or `computeOnServer` outside the wait, then wait for the result in client state, such as a synced entity, progress or the current screen.
- **A run includes every client game test.** The current runner can't select a single client test, and `runClientGameTest` is not a JUnit task that accepts `--tests`. Each run takes a few minutes, so allow time for the whole suite.

## Decisions

- [Stack](../decisions/20261105-stack.md)
- [Progress save format](../decisions/20261105-progress-save-format.md)
- [Items are identified by translation key](../decisions/20261106-items-identified-by-translation-key.md)
- [The name list's readings are under CC BY-SA 4.0](../decisions/20261106-name-list-under-cc-by-sa.md)
- [Reading clips are made ahead of time with VOICEVOX's 玄野武宏](../decisions/20261107-reading-clips-with-voicevox.md)
- [The tsukumogami is never saved with the world](../decisions/20261109-tsukumogami-never-saved.md)
