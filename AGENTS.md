# AGENTS.md

Rules for coding agents working in this repo.

## Project

Oboecraft is a Minecraft mod for learning Japanese. It uses a spaced repetition system (SRS) on the items in the game.

`docs/idea.md` is the original vision for the mod. It is frozen: do not edit it unless asked. How the mod actually works is described in `docs/design/`.

## Git and pull requests

- Never push, force-push, or commit to `main`. Always work on a branch.
- Every change goes in as a pull request. Agents open PRs but never merge them.
- Branch names use a type prefix and a short kebab-case name: `feat/`, `fix/`, `docs/`, `refactor/` or `chore/`. If the work is based on an issue, put the issue number right after the prefix, like `feat/12-srs-levels`.
- Commit messages have a short subject in the imperative mood, like "Add lock timer to items". No Conventional Commit prefixes (`feat:` and so on).
- Keep each PR to one topic. The description says what changed and why. If the PR is based on an issue, include `Refs #12`.

## Architecture

Follow clean architecture. Dependencies point inward.

- **Core (domain):** the game rules, such as SRS levels, lock timing, answer checking, punishment and the word list. Plain code with no Minecraft or mod loader imports.
- **Adapters:** everything that touches Minecraft, the mod loader, the UI, saving data and text-to-speech. Adapters depend on the core. The core never depends on adapters.

This keeps the core testable without starting the game.

## Testing

- Core logic comes with unit tests in the same PR.
- Bug fixes come with a test that fails before the fix and passes after.
- All tests pass before a PR is opened.
- Never delete or weaken a test just to make it pass. If a test is wrong, explain the change in the PR.

## Docs

Docs reflect code. Changes that touch game behavior, architecture, tools, or terminology update the matching doc in the same PR (or a tightly coupled follow-up PR linked from the original). This includes this file: new or changed tools, commands or conventions are added here.

| Path | Contents | Kept in sync with code |
|---|---|---|
| `docs/idea.md` | Original vision | No, frozen |
| `docs/design/` | Game behavior as it actually works | Yes |
| `docs/architecture/` | How the code is structured | Yes |
| `docs/glossary.md` | Domain terms | Yes |
| `docs/decisions/` | Architecture decision records (ADRs) | No, append-only |
| `docs/rfcs/` | Proposals for significant changes | No, historical |

### Terminology

Code uses the terms from `docs/glossary.md` for names of classes, functions and variables. A new or renamed term updates the glossary in the same PR.

### ADRs

Write an ADR when a PR makes a decision that is hard to undo or had real alternatives, such as the stack, the save format or a layer boundary. The ADR goes in the same PR.

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
