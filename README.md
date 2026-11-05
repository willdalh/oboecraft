# oboecraft

A Minecraft: Java Edition mod for learning Japanese through spaced repetition.

The project foundation targets Minecraft 26.3, Java 25 and Fabric. Gameplay is not implemented yet. Dedicated servers and worlds hosted by a friend are part of the planned support.

## Development

Use JDK 25. On Windows x64, `./scripts/Use-Java25.ps1` installs a verified local JDK and selects it for the current PowerShell session.

```powershell
.\scripts\Use-Java25.ps1
.\gradlew.bat build
.\gradlew.bat :fabric:runClient
```

On Linux and macOS, install JDK 25, then use `./gradlew` for the same tasks. See the [development guide](docs/development.md) for setup, server startup, tests and output files.

## Project docs

- [Confirmed gameplay rules](docs/design/README.md): reviews, attempts, inventory movement, crafting and punishment.
- [Implementation plan](docs/implementation-plan.md): platform decisions, playable milestones and early testing.
- [Architecture overview](docs/architecture/overview.md): core rules and the Minecraft adapter.
- [Glossary](docs/glossary.md): project terminology.
- [Original project idea](docs/idea.md): the frozen starting vision; later decisions are recorded in the design docs and implementation plan.
