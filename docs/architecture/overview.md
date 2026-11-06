# Architecture overview

This describes how the code is structured. It is kept in sync with the code.

More detail on each part:

- [Core](core.md): the packages and types of the game rules.
- [Fabric adapter](fabric.md): item mapping, the lock check entry point, progress, reviews, the answer menu and networking.
- [Progress files](progress-files.md): where progress is saved and the file format.
- [Name list file](name-list-file.md): the format of the name list that ships with the mod.
- [Name list tool](name-list-tool.md): the tool that writes the name list from Minecraft's Japanese names.

The parts on those pages were designed in milestone 3a and built in milestone 3b. Milestone 4 added the name list file format and answer checking, and milestone 5 the name list tool and the full name list.

## Core and adapters

The code follows clean architecture. The core holds the game rules as plain Java. Adapters connect the core to Minecraft and the mod loader. Dependencies point inward: adapters depend on the core, and the core never depends on adapters. This keeps the core testable without starting the game.

## Modules

The mod id is `oboecraft`. The code is split into three Gradle modules:

| Module | Package | Contents |
|---|---|---|
| `core` | `io.github.willdalh.oboecraft.core` | The game rules: levels and intervals, when an item is locked, answer checking, punishment and the name list. Plain Java with no Minecraft or Fabric imports. |
| `fabric` | `io.github.willdalh.oboecraft.fabric` | The adapter: everything that touches Minecraft and Fabric, like catching interactions, the answer menu and other screens, saving progress, networking and playing the reading aloud. Depends on `core`. |
| `tools` | `io.github.willdalh.oboecraft.tools` | Tools run by hand to make the mod's data, like the [name list tool](name-list-tool.md). Depends on `core`, and isn't part of the mod. |

Minecraft is not on the classpath of `core`, so the compiler enforces the boundary. Support for another mod loader would be another adapter module next to `fabric`.

The released mod is one jar, built by `fabric`. It contains `core`'s classes, so players install a single file, and the license and [credits](../../CREDITS.md). In dev runs (`runClient`, `runServer`, `runGameTest`), `core` belongs to the `oboecraft` mod too, so things like looking up resources through the mod work the same in dev and in the released jar. For this, `core` applies Loom's companion plugin, which only exports its classpath and adds no Minecraft or Fabric dependencies.

## Source layout

```
build.gradle.kts            Settings shared by both modules: group, version, Java 25 toolchain
settings.gradle.kts         The two modules and the toolchain download plugin
gradle/libs.versions.toml   All dependency and plugin versions
core/
  src/main/java             Game rules
  src/test/java             Unit tests (JUnit)
tools/
  src/main/java             The name list tool
  src/test/java             Unit tests (JUnit)
fabric/
  src/main/java             Code for both sides, and code that only runs on the server
  src/main/resources        fabric.mod.json, the mixin config, and the name list in oboecraft/name_list.json
  src/client/java           Code that only runs on the client
  src/client/resources      The client mixin config, and the language files in assets/oboecraft/lang
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

All versions live in `gradle/libs.versions.toml`. The mod version and the Minecraft and Fabric Loader versions in `fabric.mod.json` are filled in from there when the jar is built.

## A thin core

The core makes decisions from simple inputs, like an item id, counts and timestamps, and returns results. The `fabric` module gathers the inputs from the game and carries out the results. Minecraft concepts like inventories, slots and containers are left out of the core.

Every action the mod may stop goes through one lock check in the core, on both sides, so rules like creative mode, ignored items and bans are decided in one place. See [Lock check](../design/lock-check.md).

For example, on a fail when crafting a diamond pickaxe at level 1, `fabric` counts 50 diamonds and 100 sticks in the current view and passes the counts and the recipe (3 diamonds, 2 sticks) to the core. The core answers: remove 15 diamonds and 20 sticks. `fabric` then removes them from the inventory and crafting grid.

## Server and client

The mod is installed on both the server and the client. Singleplayer runs on Minecraft's built-in server, so it uses the same code as a dedicated server.

- **The server decides everything that matters:** SRS states, answer checking, levels, punishment and bans.
- **The client only displays:** the answer menu, "???" names, timer bars, slot markers and the spoken reading.

The server sends each player their SRS states when they join and pushes updates when they change.

Locks are checked on both sides. The client checks first, since it knows the SRS states, and opens the answer menu at once, so a placed block doesn't flicker back. The server checks too and has the final say, which stops modified clients.

Answers are checked on the server. The client sends the typed text, and the server replies with a pass or fail. The Japanese name is only sent to the client when it is needed: in lessons and after a fail.

## Time

Intervals and bans run in real-world time from the server clock. The SRS state stores when an item locks next as a timestamp, and an item is locked when the current time is at or past it. No timers need to run.

The core never reads a clock: its rules take the current time as an input. In `fabric`, all code reads the time from one clock, which game tests replace with one they control. The client works out the server's time from the time in the server's latest message, plus how long ago it arrived. See [Time](../design/time.md).

## Testing

Core rules get plain unit tests with JUnit, in `core/src/test`. The `fabric` module uses Fabric game tests where they pay off, in `fabric/src/gametest`. There are two kinds:

- **Game tests** run on a headless server. A game test is a public method annotated with `@GameTest` that takes a `GameTestHelper`, in a class listed under the `fabric-gametest` entrypoint of the test mod. Tests that need a player use one that joins over a connection that goes nowhere (`TestPlayers`), so the mod loads and saves its progress as for a real player.
- **Client game tests** run a real client and drive it with key presses and mouse clicks. A client game test is a class that implements `FabricClientGameTest`, listed under the `fabric-client-gametest` entrypoint. Singleplayer passes payloads without encoding them, so a game test encodes and decodes each payload too.

`./gradlew build` runs the core tests, the tools' tests and the game tests. The game test results are written as a JUnit report to `fabric/build/test-results/gameTest/`, and the game test server's logs to `fabric/build/run/gameTest/logs/`.

`./gradlew runClientGameTest` runs the client game tests. It needs a display. Without one, run it in a virtual display with Mesa's software Vulkan driver, as CI does: Minecraft can't get an OpenGL context from Xvfb's software GLX, which has no sRGB framebuffer, so it falls back to Vulkan. Screenshots the tests take are in `fabric/build/run/clientGameTest/screenshots/`.

GitHub Actions runs both on every PR and on every push to `main` (`.github/workflows/build.yml`), and uploads the reports, logs, screenshots and any crash reports.

## Decisions

- [Stack](../decisions/20261105-stack.md)
- [Progress save format](../decisions/20261105-progress-save-format.md)
- [Items are identified by translation key](../decisions/20261106-items-identified-by-translation-key.md)
