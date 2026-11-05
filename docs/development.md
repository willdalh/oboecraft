# Development

The project foundation is implemented; locking, reviews and other gameplay are still planned. The build uses Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.2+26.3, Loom 1.17.21 and Gradle 9.6.0. Versions are pinned in `gradle.properties`, `build.gradle` and `gradle/wrapper/gradle-wrapper.properties`.

## Java and first build

JDK 25 is required to run Gradle and compile the project. Configuring a compilation toolchain does not install the Java runtime needed to start Gradle. The wrapper downloads the pinned Gradle distribution on the first run; the first build also downloads Minecraft and dependencies.

On Windows x64, open PowerShell in the repository and run:

```powershell
.\scripts\Use-Java25.ps1
java -version
.\gradlew.bat build
```

The script downloads Temurin 25.0.5+8 from Adoptium, verifies its SHA-256 checksum and extracts it under `.tools/java`. It sets `JAVA_HOME` and `PATH` for the current PowerShell process. Run it again in each new session. You can instead configure an existing JDK 25 yourself.

On Linux or macOS, install [Temurin JDK 25](https://adoptium.net/temurin/releases/?version=25), set `JAVA_HOME` to that installation and put its `bin` directory on `PATH`:

```sh
java -version
./gradlew build
```

If Gradle reports Java 8 or another older runtime, fix `JAVA_HOME` and `PATH` before retrying. No global Gradle installation is needed.

## Build and tests

Use `./gradlew.bat` in PowerShell and `./gradlew` on Linux or macOS.

| Task | Purpose |
|---|---|
| `build` | Compile both modules, package the mod, run core unit tests and headless server game tests. |
| `:core:test` | Run pure Java tests without Minecraft or Fabric on the core's runtime classpath. |
| `:fabric:runGameTest` | Start a disposable Minecraft test server and run the Fabric game tests. |
| `:fabric:jar` | Build the mod jar, including the core library. |
| `clean` | Remove generated build output. |

Core tests cover namespaced item identifiers and verify that Minecraft and Fabric are absent from the core's runtime classpath. The game test loads Oboecraft on a server and passes an oak-log registry identifier into the core. It is a setup smoke test, not a gameplay test.

Automated game tests accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) in the Gradle test configuration and use a separate test world. They do not use a player's saves. Game-test registration lives under `fabric/src/gametest`, so test classes and their entrypoint are excluded from the published mod.

JUnit HTML reports are written to `core/build/reports/tests/test/index.html`. The mod jar is `fabric/build/libs/oboecraft-0.1.0-SNAPSHOT.jar`; the `-sources.jar` is for source browsing. Install the mod jar with the matching Fabric Loader and Fabric API on both client and server. The core is bundled and needs no separate installation.

## Run the game

Start a development client:

```sh
./gradlew :fabric:runClient
```

Start a dedicated development server without a graphical console:

```sh
./gradlew :fabric:runServer --args=nogui
```

The first normal server run creates `fabric/run/eula.txt` and exits until its EULA is accepted. Read the linked terms, set `eula=true` in that file if you agree, then run the command again. Enter `stop` in the server console to shut down cleanly.

Development worlds, logs and downloaded assets stay under ignored directories. Do not point a development run at a personal world. Both client and server should log `Oboecraft initialized`. At this stage, items behave like vanilla Minecraft.

## Continuous integration

GitHub Actions runs `build` with JDK 25 on Linux and Windows for every PR and for pushes to `main`. This includes the core unit tests and a headless Minecraft game-test server. CI uploads test reports and logs, and the Linux job uploads the mod jars. A graphical client startup is a separate manual check; the server game test does not verify rendering or keyboard input.

## References

- [Fabric for Minecraft 26.3](https://fabricmc.net/2026/09/15/263.html)
- [Fabric automated testing](https://docs.fabricmc.net/develop/automatic-testing)
- [Gradle wrapper](https://docs.gradle.org/9.6.0/userguide/gradle_wrapper.html)
