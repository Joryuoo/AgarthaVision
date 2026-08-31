# docs/_archive/ — superseded

**Everything under this folder is superseded. No agent should implement against anything in
here.**

It is retained for manual review only: so a human can see what the previous documentation
setup asked for before deciding whether any of it is worth carrying forward into the shelf.

## What is here

| Path | What it was | Status |
|---|---|---|
| `stages/01_scope/` | Stage 1 contract — analyse a backlog item, write a scoped plan | Superseded |
| `stages/02_implement/` | Stage 2 contract — execute the plan, write code, record notes | Superseded |
| `stages/03_qa/` | Stage 3 contract — lint, test, compliance checklist, draft the PR | Superseded |

Each stage carried a `CONTEXT.md` and an empty `output/` folder. The pipeline was a
three-stage scope → implement → QA loop with a human review gate between stages.

## Why it was replaced

The pipeline assumed a per-task workflow and pointed every stage at two large root files
(`CONTEXT.md`, `schema.ts`) that an agent then had to read in full. The current structure is
a router plus a shelf: `SESSION_INIT.md` routes by situation, and each shelf file answers one
question with `path:line` citations. See `../../SESSION_INIT.md`.

## Reading these files safely

- The **inputs tables** inside the stage contracts reference paths that no longer resolve.
  They are archived and stale — see the note at the top of each stage file.
- The **QA checklist** in `stages/03_qa/CONTEXT.md` was written against the old rule set. The
  live rules are `../constraints.md` and `../non-negotiables.md`; where the two disagree, the
  live files win.
- Nothing here has been verified against the current code. Treat every claim as a historical
  assertion, not a fact.
