# docs/map/ — the edit map (contract)

The map answers "what is this, and what does changing it hit?" It is split by grammar:

| Folder | Grammar | Holds |
|---|---|---|
| `objects/` | nouns | One card per core domain entity |
| `processes/` | verbs | One card per movement that actually runs in Phase 1 |
| `effects/` | consequences | The change-impact index: touch X, open these |

## Card frontmatter

Every card opens with YAML frontmatter, per the ICM object and process templates:

```yaml
---
type: object            # or process
status: verified        # stub | verified | stale
verified: 2026-09-29    # the day it was last checked against the code
commit: feaa4803        # the commit it was checked against
entity: path/to/owning/File.kt   # objects only: the file that owns the truth
---
```

- `verified` needs a date, a commit and citations. Without all three the card is `stub`.
- `stale` is honest and allowed: it means the code moved and nobody re-checked the card yet.
  Trust a stale card's *why*; re-read the code for its *what*.
- A PR that edits a file a card cites updates the card or marks it `stale` (`../CONTEXT.md`).

## Reading order

Start at `effects/CONTEXT.md` when you know what you are about to change but not what it
touches. Start at a card when you already know the entity or the flow.

## What does not belong here

- As-built behaviour copied out of the code. Cards point; they do not mirror.
- Anything aspirational. If it is Phase 2 or unimplemented, it is a ghost and says so.
- Screen-by-screen UI description. That is `../file-tree.md` plus the code.
