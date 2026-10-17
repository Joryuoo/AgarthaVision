# docs/map/processes/ — process cards (contract)

One card per movement that **actually runs in the Phase 1 code**: `sign-in`,
`register-patient`, `session-lifecycle`, `capture`, `infer`, `validate`, `delete-sample`,
`sync`, `report`. A new card is added to this list in the same change.

## Card shape

- **Frontmatter** — `type: process`, `status`, `verified`, `commit`. See `../CONTEXT.md`.
- **Input → Movement → Output** as the spine.
- **Movement** is numbered steps, each with a citation (`../../CONTEXT.md` house rule 1). If you cannot cite a
  step, the step does not go in the card.
- **consumes** / **produces** are links to object cards, not prose.
- **Hits / Does not hit** closes the card.

## What does not belong here

- Steps that only exist in a design document. Verify against the code or leave them out.
- Phase 2 flows. If a flow is deferred, ghost it in one line — do not describe it as live.
- Duplicate coverage. If two cards would describe the same step, one owns it and the other
  links.
