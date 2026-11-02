# Speech pattern rules

Open **Pronunciation & patterns** from the reader tools or Settings. Rules affect speech and audio export across books; they do not edit the displayed or stored source text. Adding, editing, toggling or deleting a rule restarts the current section if normal reading is playing.

Choose an example, or enter your own rule:

| Match mode | Find | Action | Result |
| --- | --- | --- | --- |
| Pattern | `[n]` | Skip | Omits `[1]`, `[12]`, and other bracketed numbers |
| Pattern | `[n]` | Say instead `reference $1` | `[12]` becomes “reference 12” |
| Pattern | `_{text}_` | Skip | Omits `_terro_` and other underscore-marked phrases |
| Pattern | `*{text}*` | Skip | Omits `*an editor note*` |
| Pattern | `_{text}_` | Say instead `$1` | Keeps the words and removes the underscores |
| Text | `SQL` | Say instead `sequel` | Changes a literal word's pronunciation |
| Regex | `\[([0-9, –-]+)\]` | Skip | Omits references such as `[1, 2–4]` |

**Text** retains the existing whole-word/phrase behavior. Matching ignores case unless **Match case** is enabled. Dollar signs in a Text replacement stay literal.

**Pattern** supports these capture tokens in order:

- `{number}`: one or more Unicode decimal digits.
- `{word}`: letters, combining marks, digits, underscores and apostrophes.
- `{text}`: nonempty text within the current passage/line. When followed by a closing marker, it stops at the nearest matching marker. A final `{text}` consumes the rest of the line.

`[n]` is a convenience spelling of `[{number}]`. Other characters, including stars and brackets, match literally. Use `{{` and `}}` for literal braces. Patterns do not parse nested Markdown or balanced brackets automatically. For example, double-star markup needs its own `**{text}**` rule. Matches cannot cross the app's spoken-passage boundaries.

**Regex** accepts RE2 expressions for advanced matches, including character classes, alternatives, repetition and capture groups. RE2 does not support look-around or backreferences inside the matching expression. This restriction avoids the exponential backtracking behavior possible with unrestricted Java regex. See [RE2/J](https://github.com/google/re2j). Expressions that match empty input are rejected; other zero-width matches are ignored.

For Pattern and Regex **Say instead**, use `$1`, `$2`, or `${1}` for captured parts, `$0` for the whole match, and `$$` for a literal dollar sign. Missing optional captures become empty text. Unknown capture numbers are rejected before saving. Patterns/replacements are limited to 512 characters and 32 capture groups; the existing Text editor keeps its 120-character field limits.

Use **Try your rules** to check a passage before saving. Its final speech-text preview includes a valid unsaved draft and all enabled saved rules. It reports applied, non-overlapping matches. The speaker button auditions nonempty prepared text with the selected base voice; it is disabled when the rules exclude everything. Preview input is limited to 4,000 characters. This preview does not simulate a book's full cast or measure acoustic timing.

Rules match the original passage once. A replacement is not run through the rules again. At overlapping locations the earliest match wins; at the same start the longest wins; an equal-length tie goes to the most recently saved rule. A replacement still passes through the normal speech text cleanup, symbol handling and selected engine frontend.

Pattern and Regex omissions insert a separator where necessary to avoid joining two adjacent words. Text rules retain their previous literal replacement behavior. If a rule removes all content and leaves only sentence punctuation, that passage is silent. Current sentence progression and selected-text completion handle this without submitting empty speech. Replacements retain source-span associations for highlighting; they do not provide exact acoustic word timestamps.

Pattern replacements also separate inserted words from adjacent words when replacing enclosing markers: `Before[12]After` with `[n] → reference $1` becomes `Before reference 12 After`. A text pattern inside a word, such as `our → or`, can still change `colour` to `color`. Nonempty Regex replacements are exact; type any desired spaces in the replacement. Pattern and Regex replacements preserve those typed spaces when saved, although final speech cleanup can normalize whitespace.

Saved rules can be edited, switched off or removed. Backups carry the mode and case option; older backups without them remain literal rules. Invalid new pattern rules are rejected during backup validation before restore writes occur.
