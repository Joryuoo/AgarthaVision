# docs/ — the shelf (contract)

Everything here is **as-built reference**, verified against the code and cited to
`path:line`. An agent arrives here from `SESSION_INIT.md` with one question and should be
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
| `map/` | The edit map: objects, processes, change impact. See `map/CONTEXT.md` |
| `_archive/` | Superseded material. Read-only. See `_archive/CONTEXT.md` |

## What does not belong here

- Plans, roadmaps, sprint backlogs, task lists, or ClickUp content. Those live in ClickUp.
- Copies of code. Point at the file that owns the behaviour; do not restate it.
- Design rationale that is already an ADR-shaped decision in the code comments — link to it.
- The same fact in two files. One home per fact; everything else links.

## House rules

1. Every load-bearing claim carries a `path:line` citation. An uncited claim is a liability.
2. Where a document and the code disagree, the code wins. Record the discrepancy in one line
   rather than silently picking a side.
3. Anything named but not wired is a **ghost**. Label it. Never present it as live.
4. Keep files tight. Router + one shelf file + one card should fit in a few thousand tokens.
