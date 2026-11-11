# AGENTS.md

Rules for coding agents working in this repo.

## Project

Oboecraft is a Minecraft mod centered on Japanese, also supporting German, Austrian German, Norwegian Bokmål and Dutch. It uses a spaced repetition system (SRS) on the items in the game.

`docs/idea.md` is the original vision for the mod. It is frozen: do not edit it unless asked. How the mod actually works is described in `docs/design/`.

## Git, issues and pull requests

- Never push, force-push, or commit to `main`. Always work on a branch.
- GitHub doesn't enforce these rules, since `main` has no branch protection. Follow them even when a command would succeed.
- Every change goes in as a pull request. Agents open PRs but never merge them.
- Branch names use a type prefix and a short kebab-case name: `feat/`, `fix/`, `docs/`, `refactor/` or `chore/`. If the work is based on an issue, put the issue number right after the prefix, like `feat/12-srs-levels`.
- Commit messages have a short subject in the imperative mood, like "Add lock timer to items". No Conventional Commit prefixes (`feat:` and so on).
- Keep each PR to one topic. The description says what changed and why. If the PR is based on an issue, include `Refs #12`.
- Issues are short and describe only the overall idea, without code references, since those get outdated. Write more detail, like an implementation plan, only when the user asks for it.

## Architecture

The code follows clean architecture, with a `core` module and a `fabric` adapter module. A `json` module holds the JSON file formats that the mod and its tools share, and a `tools` module holds tools that are run by hand to make the mod's data, like the name list tool. `docs/architecture/overview.md` describes the structure.

- Dependencies point inward: `json` depends on `core`, `fabric` and `tools` depend on both, never the other way around, and nothing depends on `tools`. `json` uses only Gson besides `core`.
- `core` never imports Minecraft or Fabric code.
- Put game rules (levels, intervals, answer checking, punishment, the name list) in `core`. Put everything that touches Minecraft or Fabric in `fabric`.
- Keep the core thin. It takes simple inputs, like item ids, counts and timestamps, and returns decisions. Minecraft concepts like inventories and slots stay in `fabric`.
- The server decides everything that matters. Client code only displays and never has the final say.

## Changing code

- Keep changes to what was asked, and fit them in as if the code had been designed that way from the start. Change, replace or delete existing code instead of adding next to it: no parallel code paths, compatibility shims, or dead code kept just in case.
- Read the surrounding code first. When the change needs it, update the code around it too, like callers, shared helpers or the function being extended.
- Don't refactor or improve unrelated code, even while fixing a bug. Mention what you noticed in the PR description instead.

## Code quality, safety and performance

Code quality, code safety and performance are all requirements for a PR. A PR that gives up one for another says so and explains why.

- **Code quality:** code can be understood without its author. Names say what things are, each function does one thing, and each piece of logic lives in one place. No unused or dead code.
- **Code safety:** input from outside the code's control is untrusted. It is checked before use, both that it is valid and that the sender is allowed to make the request. Shared state is only changed in ways that can't race. No error or invalid input leaves stored data lost or corrupt.
- **Performance:** code that runs often never blocks, and does only the work its input needs. Work whose result doesn't change is done once, not on every call.

## Commands

Run these from the repository root. The first run downloads Gradle, Java 25 and Minecraft, so it takes a while.

| Command | What it does |
|---|---|
| `./gradlew build` | Compiles, runs the unit tests of `core`, `json` and `tools` and the Fabric game tests, and builds the mod jar in `fabric/build/libs/` |
| `./gradlew :core:test` | Runs only the core unit tests |
| `./gradlew :fabric:genSources` | Generates decompiled Minecraft sources for checking the current APIs |
| `./gradlew runGameTest` | Runs only the Fabric game tests, on a headless server |
| `./gradlew runClientGameTest` | Runs the Fabric client game tests in a real client. Not part of `build`. It needs a display: without one, install `xvfb` and `mesa-vulkan-drivers` and run `VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientGameTest`, as CI does |
| `./gradlew runClient` | Starts the game with the mod |
| `./gradlew runServer` | Starts a local dedicated server with the mod, in `fabric/run/` |
| `./gradlew :tools:generateNameList` | Updates the name list from Minecraft's supported language files, keeping reviewed entries, and writes the entries to review to `tools/build/name-list/review.md`. Downloads from Mojang, PyPI and EDRDG (`www.edrdg.org`). See `docs/architecture/name-list-tool.md` |
| `./gradlew :tools:generateReadingClips` | Makes the reading clips for new and changed name list entries with VOICEVOX, and writes the clips to review to `tools/build/reading-clips/review.md`. Needs a VOICEVOX engine on `localhost:50021` and FFmpeg. See `docs/architecture/reading-clip-tool.md` |

The first `runServer` stops and asks you to accept Minecraft's EULA. Set `eula=true` in `fabric/run/eula.txt` to accept it, then run it again. That is the user's choice, so agents never accept the EULA for them.

The mod's settings files are in the run folder's `config/oboecraft/`: `fabric/run/config/oboecraft/server.json` for `runClient` and `runServer`. A missing server settings file is written with the defaults when the server starts. See `docs/architecture/settings-files.md`.

Versions of Minecraft, Fabric and libraries are in `gradle/libs.versions.toml`.

### Build and API pitfalls

- **Maven Central rate limits:** dependency downloads can fail with HTTP 429, both on the first build and when generating sources. Wait before retrying the same command. Fewer Gradle workers reduce concurrent downloads: try `./gradlew build --max-workers=2` or `./gradlew :fabric:genSources --max-workers=2`.
- **Check the current Minecraft APIs:** Minecraft 26.3 is newer than agents' training data, and many names have changed. Don't rely on remembered names or signatures. Run `./gradlew :fabric:genSources` and read the generated sources before using an unfamiliar API. Fabric API doesn't ship sources for this version; inspect its classes in the dependency jars, with a decompiler or `javap`, instead.

For game test and client game test pitfalls, see [Testing](docs/architecture/overview.md#testing).

### Name list

The files in `fabric/src/main/resources/oboecraft/name_lists/` (`ja_jp`, `de_de`, `de_at`, `no_no`, `nl_nl`) are generated by the name list tool. Every generated entry must be reviewed before shipping. The reviews are `tools/build/name-list/review.md` for Japanese and `<language>-review.md` beside it for the others. Plain-name corrections change `name` and keep `sourceName` unchanged so the tool can retain them. After a Minecraft update, run the tool, then review every entry in the review list, not only the ones with notes, and correct the file by hand. Running the tool again keeps the corrections. After Japanese name changes run the reading clip tool, see [Reading clips](#reading-clips). Credits for the data the name list is made with are in `CREDITS.md`, which ships in the mod jar. The readings and name parts are under CC BY-SA 4.0, see `docs/decisions/20261106-name-list-under-cc-by-sa.md`.

Austrian German (`de_at`) is a separate learning language with its own reviewed Minecraft name list and `de_at` progress folder. It uses the German answer rules. `de_ch` and other unsupported variants still fall back to Japanese.

Learning language follows only the exact supported game language codes; all other codes fall back to Japanese. Every player session holds one learning language, and all rules and item lookups must use it. Progress files are `oboecraft/progress/<language>/<uuid>.json`; never mix language states. See `docs/architecture/progress-files.md` for the legacy Japanese migration and downgrade stub.

### Reading clips

Every Japanese name list entry has a reading clip in `fabric/src/client/resources/assets/oboecraft/sounds/reading/`, made by the reading clip tool, and `ReadingClipsClientGameTest` fails if one is missing. After a change to the name list, run the tool, which only makes the clips of new and changed entries. The tool rewrites the reading clips in `sounds.json` and keeps the file's other sound events, like the tsukumogami's, which are written by hand. The voice is VOICEVOX:玄野武宏(CV:ガロ), whose credit is in `CREDITS.md`. A change of voice needs a voice whose terms allow its clips to ship in the published mod, and the credit in `CREDITS.md` and in the clip folder's `NOTICE.md` changes with it, see `docs/decisions/20261107-reading-clips-with-voicevox.md`.

### Tsukumogami sounds

The tsukumogami's sounds are in `fabric/src/client/resources/assets/oboecraft/sounds/tsukumogami/`, made once by hand from recordings and synthesis, and `TsukumogamiSoundsClientGameTest` fails if one of its sound events has no file. Every recording a sound is made from is under terms that allow it to ship in the published mod, and is credited in `CREDITS.md` with what was changed. A new or replaced sound changes its credit in the same PR.

## Releases

The mod is published only as a GitHub release, with the mod jar attached and GitHub's generated release notes.

- To make a release, run the Release workflow (`.github/workflows/release.yml`) on `main` from the Actions tab and pick the bump. It takes the highest tag like `v1.2.3`, raises it by the bump, builds the mod with that version and creates the release and its tag `v<version>` at the commit it built. Nobody edits a version file to release, and the workflow never commits or pushes to `main`. Merging to `main` never makes a release.
- The bump is `patch` by default. Pick `minor` for new features or anything that breaks compatibility with the previous version, such as a change to `ProtocolCheck.PROTOCOL_VERSION`, and `major` as the project decides.
- `version` in `gradle.properties` is `0.0.0-dev`, the version of local and PR builds. Leave it as it is. The workflow passes the real version to Gradle with `-Pversion`.
- Nobody pushes tags or creates releases by hand.
- Agents never start a release unless the user asks for one.

## Testing

- Core logic comes with unit tests in the same PR.
- Bug fixes come with a test that fails before the fix and passes after.
- All tests pass before a PR is opened (`./gradlew build`).
- After opening a PR, check its CI status with `gh pr checks` and fix any failing check. A PR with failing checks is not ready for review.
- Never delete or weaken a test just to make it pass. If a test is wrong, explain the change in the PR.

## Docs

Docs reflect code. Changes that touch game behavior, architecture, tools, or terminology update the matching doc in the same PR (or a tightly coupled follow-up PR linked from the original). This includes this file: new or changed tools, commands or conventions are added here.

| Path | Contents | Kept in sync with code |
|---|---|---|
| `README.md` | What the mod does, installing it and a short guide to playing | Yes |
| `CONTRIBUTING.md` | How to contribute, and the licenses contributions are under | Yes |
| `docs/idea.md` | Original vision | No, frozen |
| `docs/design/` | Game behavior as it actually works | Yes |
| `docs/architecture/` | How the code is structured | Yes |
| `docs/glossary.md` | Domain terms | Yes |
| `docs/decisions/` | Architecture decision records (ADRs) | No, append-only |
| `docs/rfcs/` | Proposals for significant changes | No, historical |

### Terminology

Code uses the terms from `docs/glossary.md` for names of classes, functions and variables. A new or renamed term updates the glossary in the same PR.

### ADRs

Write an ADR when a PR makes a decision that is all three of these:

1. **Hard to reverse:** changing your mind later has a real cost.
2. **Surprising without context:** a future reader would wonder why it was done this way.
3. **A real trade-off:** there were genuine alternatives, and one was picked for specific reasons.

Examples are the stack, the save format or a layer boundary. If any of the three is missing, skip the ADR. The ADR goes in the same PR.

ADRs are named `docs/decisions/YYYYMMDD-title.md`. Once accepted, an ADR is never edited, except for its status when a newer ADR replaces it.

```markdown
# ADR-YYYYMMDD: Title

## Status
Accepted | Superseded by ADR-YYYYMMDD

## Context
What situation prompted this decision.

## Decision
What we decided to do.

## Consequences
What becomes easier, harder, or different as a result.
```

### RFCs

Only write an RFC when asked. RFCs are named `docs/rfcs/YYYYMMDD-title.md`.

```markdown
# RFC-YYYYMMDD: Title

## Status
Draft | Under Review | Accepted | Rejected

## Summary
One paragraph.

## Motivation
Why this matters.

## Proposal
The change in detail.

## Alternatives Considered
What else was evaluated.

## Open Questions
Unresolved issues.
```

Lifecycle:

1. Open a PR with the RFC file, status Draft.
2. Mark it Under Review when it is ready for feedback.
3. Discussion happens in PR comments, and the file is updated to match the outcome.
4. On merge, the status becomes Accepted or Rejected.
5. An accepted RFC leads to implementation PRs and an ADR in `docs/decisions/`.
