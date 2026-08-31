# docs/map/ — the edit map (contract)

The map answers "what is this, and what does changing it hit?" It is split by grammar:

| Folder | Grammar | Holds |
|---|---|---|
| `objects/` | nouns | One card per core domain entity |
| `processes/` | verbs | One card per movement that actually runs in Phase 1 |
| `effects/` | consequences | The change-impact index: touch X, open these |

## Reading order

Start at `effects/CONTEXT.md` when you know what you are about to change but not what it
touches. Start at a card when you already know the entity or the flow.

## What does not belong here

- As-built behaviour copied out of the code. Cards point; they do not mirror.
- Anything aspirational. If it is Phase 2 or unimplemented, it is a ghost and says so.
- Screen-by-screen UI description. That is `../file-tree.md` plus the code.
