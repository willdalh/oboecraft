# ADR-20261106: The name list's readings are under CC BY-SA 4.0

## Status
Accepted

## Context
The name list that ships in the mod jar has each item's Japanese name split into name parts, with a reading for each part. The name list tool made the readings with JMdict, which checked each word, and KANJIDIC2, which split each word's reading between its kanji. An agent then reviewed and corrected them by hand. JMdict and KANJIDIC2 belong to the Electronic Dictionary Research and Development Group (EDRDG), and are licensed under Creative Commons Attribution-ShareAlike 4.0 (CC BY-SA 4.0).

Neither dictionary file ships with the mod. But the per-kanji splits come straight from KANJIDIC2's readings, and JMdict decided which readings were flagged, so the name list could count as material adapted from EDRDG's data. The rest of the mod's code is under the MIT license.

The alternatives were:

- Treat the readings as facts and only credit EDRDG. Simpler, but it relies on a legal reading of "facts" that may not hold for a compiled list.
- Ask EDRDG first. Their answer would settle it, but the mod should be safe whatever the answer.

## Decision
The readings and the split into name parts in the name list file are licensed under CC BY-SA 4.0, credited to EDRDG's JMdict and KANJIDIC2. The Japanese names themselves are Mojang's, from Minecraft's Japanese translation, and aren't licensed by the mod. The code, including the name list tool, stays under the MIT license.

`CREDITS.md` says this, links the license and ships in the mod jar next to the name list.

## Consequences
- The mod meets CC BY-SA 4.0 however its terms are read: EDRDG is credited, the license is linked, and the changes (the review by hand) are said.
- Anyone who reuses or changes the name list must keep it under CC BY-SA 4.0 and credit EDRDG. That covers the mod itself in later versions too.
- The license doesn't reach the MIT code. The name list is a separate file that the code reads.
- A released name list can't be taken back from CC BY-SA. If EDRDG ever says it isn't needed, later versions can relax it, but going the other way after a release would have been much harder.
