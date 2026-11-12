# ADR-20261110: Learning language follows the game language with separate progress folders

## Status
Accepted

## Context
Oboecraft now supports learning languages other than Japanese. Minecraft's translations group keys differently in each language, so the languages' item ids and SRS states cannot share a progress file. Players should see the same item names they answer with, and players learning different languages must coexist on one server. Existing Japanese progress must survive upgrades, interrupted migration and downgrades.

## Decision
The exact game language code of a supported learning language, like `ja_jp`, selects that learning language. Every other code falls back to Japanese, including regional variants of supported languages. Adding a learning language follows this decision and needs no new ADR. The client selects it when uploading progress on joining. The server validates it against the supported languages and holds it for that connection; changing the game language in-world announces the language for the next join. Server settings remain shared.

Each account has independent files under `oboecraft/progress/<language>/<uuid>.json`, with independent locks, broken files and format versions. The server receives and changes only the active language's progress. A language's name list determines grouping and answer checking.

At startup, legacy Japanese files and broken backups move to `ja_jp`. Migration locks both paths and preserves a complete copy before replacing anything. The old path becomes a stub with an unsupported format version, so older mods refuse it. If both old and new files exist, the more recently modified file wins and the other is preserved as `.broken`; destination wins ties.

## Consequences
Players choose learning content through a setting they already use, and languages cannot overwrite each other's progress. An explicit learning-language setting would allow a different game language but would make the visible names disagree with the answer; adding that later would require a new display policy. A change within a world deliberately waits for the next join to avoid switching progress during reviews or locks.

Language codes become persistent folder names. Older mods cannot load migrated progress without a deliberate manual recovery. Migration keeps conflicting files for manual merging rather than guessing how states from independently played files should combine. Japanese parts, readings and presentation remain available while plain names need no Japanese text processing.
