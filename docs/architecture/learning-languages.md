# Adding a learning language

`LearningLanguage` in `core` is the list of learning languages, and the source of truth for developers. The name list tool, the name lists, progress folders and the client game tests all follow it. Adding a language involves:

1. **The language entry:** add it to `LearningLanguage` with its exact game language code and the locale its names sort by. Extend `LearningLanguageTest`.
2. **The name list:** run `./gradlew :tools:generateNameList`, review every entry in `tools/build/name-list/<language>-review.md` and correct the file by hand, see [Name list tool](name-list-tool.md).
3. **The UI label:** add `oboecraft.language.<code>` to every file in `fabric/src/client/resources/assets/oboecraft/lang/`.
4. **The protocol version:** raise `ProtocolCheck.PROTOCOL_VERSION`, so older peers never receive an unknown learning language.
5. **Tests:** cover the language's progress and uploads in the game tests, and any answer or sort rule of its own in `core`. Run `./gradlew build` and the client game tests.
6. **Docs:** add the language to the two language lists, the README's "Learning languages" table and [Items](../design/items.md#learning-language), and to `CREDITS.md`. Add any facts of its own, like its keyboard spellings in [Answers](../design/answers.md), its sort order in [Word list](../design/word-list.md), or its groupings and entry count in [Items](../design/items.md).

A language with readings, unlike a [plain-name language](../glossary.md), needs more than this checklist.
