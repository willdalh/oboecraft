# Architecture overview

This describes how the code is structured. It is kept in sync with the code.

## Core and adapters

The code follows clean architecture. The core holds the game rules as plain Java. Adapters connect the core to Minecraft and the mod loader. Dependencies point inward: adapters depend on the core, and the core never depends on adapters. This keeps the core testable without starting the game.

## Modules

The mod id is `oboecraft`. The code is split into two Gradle modules:

| Module | Package | Contents |
|---|---|---|
| `core` | `io.github.willdalh.oboecraft.core` | The game rules: levels and intervals, when an item is locked, answer checking, punishment and the name list. Plain Java with no Minecraft or Fabric imports. |
| `fabric` | `io.github.willdalh.oboecraft.fabric` | The adapter: everything that touches Minecraft and Fabric, like catching interactions, the answer menu and other screens, saving progress, networking and playing the reading aloud. Depends on `core`. |

Minecraft is not on the classpath of `core`, so the compiler enforces the boundary. Support for another mod loader would be another adapter module next to `fabric`.

The released mod is one jar, built by `fabric`. It contains `core`'s classes, so players install a single file.

## Source layout

```
build.gradle.kts            Settings shared by both modules: group, version, Java 25 toolchain
settings.gradle.kts         The two modules and the toolchain download plugin
gradle/libs.versions.toml   All dependency and plugin versions
core/
  src/main/java             Game rules
  src/test/java             Unit tests (JUnit)
fabric/
  src/main/java             Code for both sides, and code that only runs on the server
  src/main/resources        fabric.mod.json
  src/client/java           Code that only runs on the client
  src/gametest/java         Fabric game tests, run on a headless server
  src/gametest/resources    fabric.mod.json for the test mod oboecraft-gametest
```

The `fabric` module splits its source sets by side. Code in `src/main` can't use client-only Minecraft classes, which don't exist on a dedicated server, so mistakes show up as compile errors instead of server crashes. Code in `src/client` can use everything in `src/main`.

The mod has two entrypoints, listed in `fabric.mod.json`:

| Entrypoint | Class | Runs on |
|---|---|---|
| `main` | `io.github.willdalh.oboecraft.fabric.OboecraftMod` | Server and client |
| `client` | `io.github.willdalh.oboecraft.fabric.client.OboecraftClient` | Client only |

The game tests are their own small mod, `oboecraft-gametest`, which is only loaded by `runGameTest` and is not part of the released jar.

## Build

The build uses Gradle through the wrapper and Fabric Loom's non-remapping plugin, `net.fabricmc.fabric-loom`, since Minecraft ships unobfuscated. Loom needs Gradle itself to run on Java 25. `gradle/gradle-daemon-jvm.properties` asks for that, and the compile toolchain asks for Java 25 too. If Java 25 isn't installed, Gradle downloads it.

## A thin core

The core makes decisions from simple inputs, like an item id, counts and timestamps, and returns results. The `fabric` module gathers the inputs from the game and carries out the results. Minecraft concepts like inventories, slots and containers are left out of the core.

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

## Testing

Core rules get plain unit tests with JUnit, in `core/src/test`. The `fabric` module uses Fabric game tests where they pay off, in `fabric/src/gametest`. A game test is a public method annotated with `@GameTest` that takes a `GameTestHelper`, in a class listed under the `fabric-gametest` entrypoint of the test mod.

`./gradlew build` runs both kinds of tests. GitHub Actions runs it on every PR and on every push to `main` (`.github/workflows/build.yml`).

## Decisions

- [Stack](../decisions/20261105-stack.md)
- [Progress save format](../decisions/20261105-progress-save-format.md)
