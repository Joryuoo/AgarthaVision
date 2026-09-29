# docs/ — the shelf (contract)

Everything here is **as-built reference**, verified against the code and cited to the symbol
that owns each claim. An agent arrives here from `SESSION_INIT.md` with one question and should be
able to answer it from one file.

## What belongs here

| File | Holds |
|---|---|
| `constraints.md` | The 13 rules, in full, each with the thing that actually enforces it |
| `non-negotiables.md` | The terse "never do this" list. No explanation |
| `stack.md` | Technologies and real version numbers, read off the build files |
| `commands.md` | Every runnable command that exists, and what it does |
| `features.md` | What ships today; Phase 2 and planned items marked as such |
| `CHANGELOG.md` | What changed, reconstructed from git history |
| `file-tree.md` | Annotated tree — what each area is for |
| `patient-pii-position.md` | Written privacy position on patient PII, RA 10173, consent, retention vs C8, and synthetic validation data (PB-26) |
| `map/` | The edit map: objects, processes, change impact. See `map/CONTEXT.md` |
| `_archive/` | Superseded material. Read-only. See `_archive/CONTEXT.md` |

## What does not belong here

- Plans, roadmaps, sprint backlogs, task lists, or ClickUp content. Those live in ClickUp.
- Copies of code. Point at the file that owns the behaviour; do not restate it.
- Design rationale that is already an ADR-shaped decision in the code comments — link to it.
- The same fact in two files. One home per fact; everything else links.

## House rules

1. Every load-bearing claim carries a citation. An uncited claim is a liability.
   - **Code: cite the symbol, never a line.** `path/File.kt::symbol`, or `File.kt::Class.member`
     when the file is already named nearby; a bare `` `::symbol` `` continues the last path.
     Kotlin paths are relative to `app/src/main/java/com/agarthavision/`. Build files, Python and
     TOML cite a block, key or function the same way (`app/build.gradle.kts` `buildTypes`,
     `inference/server.py::infer`). Line numbers go stale on the next edit; symbols survive it.
   - **SQL migrations: cite `path:line`.** An applied migration is never edited (C6), so its lines
     do not move. A pre-consolidation file always carries its `legacy-dev/` prefix — numbers
     0003, 0004 and 0006 name different files at the top level.
2. Where a document and the code disagree, the code wins. Record the discrepancy in one line
   rather than silently picking a side.
3. Anything named but not wired is a **ghost**. Label it. Never present it as live.
4. Keep files tight. Router + one shelf file + one card should fit in a few thousand tokens.
5. **A change to code updates the card that describes it, in the same PR.** Every card under
   `map/` opens with frontmatter — see `map/CONTEXT.md` — and its `verified` / `commit` say when
   someone last checked it against the code. A PR that touches a file a card cites either
   re-verifies the card (new date and commit) or sets `status: stale`.
