# AGENTS.md

Rules for coding agents working in this repo.

## Project

Oboecraft is a Minecraft mod for learning Japanese. It uses a spaced repetition system (SRS) on the items in the game.

`docs/idea.md` is the original vision for the mod. It is frozen: do not edit it unless asked. How the mod actually works is described in `docs/design/`.

## Git and pull requests

- Never push, force-push, or commit to `main`. Always work on a branch.
- GitHub doesn't enforce these rules, since `main` has no branch protection. Follow them even when a command would succeed.
- Every change goes in as a pull request. Agents open PRs but never merge them.
- Branch names use a type prefix and a short kebab-case name: `feat/`, `fix/`, `docs/`, `refactor/` or `chore/`. If the work is based on an issue, put the issue number right after the prefix, like `feat/12-srs-levels`.
- Commit messages have a short subject in the imperative mood, like "Add lock timer to items". No Conventional Commit prefixes (`feat:` and so on).
- Keep each PR to one topic. The description says what changed and why. If the PR is based on an issue, include `Refs #12`.

## Architecture

The code follows clean architecture, with a `core` module and a `fabric` adapter module. `docs/architecture/overview.md` describes the structure.

- Dependencies point inward: `fabric` depends on `core`, never the other way around.
- `core` never imports Minecraft or Fabric code.
- Put game rules (levels, intervals, answer checking, punishment, the name list) in `core`. Put everything that touches Minecraft or Fabric in `fabric`.
- Keep the core thin. It takes simple inputs, like item ids, counts and timestamps, and returns decisions. Minecraft concepts like inventories and slots stay in `fabric`.
- The server decides everything that matters. Client code only displays and never has the final say.

## Commands

Run these from the repository root. The first run downloads Gradle, Java 25 and Minecraft, so it takes a while.

| Command | What it does |
|---|---|
| `./gradlew build` | Compiles, runs the core unit tests and the Fabric game tests, and builds the mod jar in `fabric/build/libs/` |
| `./gradlew :core:test` | Runs only the core unit tests |
| `./gradlew runGameTest` | Runs only the Fabric game tests, on a headless server |
| `./gradlew runClient` | Starts the game with the mod |
| `./gradlew runServer` | Starts a local dedicated server with the mod, in `fabric/run/` |

The first `runServer` stops and asks you to accept Minecraft's EULA. Set `eula=true` in `fabric/run/eula.txt` to accept it, then run it again. That is the user's choice, so agents never accept the EULA for them.

Versions of Minecraft, Fabric and libraries are in `gradle/libs.versions.toml`.

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
| `docs/idea.md` | Original vision | No, frozen |
| `docs/implementation-plan.md` | Decisions and milestones for building the mod | Yes, milestone status updated as PRs merge |
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
