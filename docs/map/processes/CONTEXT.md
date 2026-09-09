# docs/map/processes/ — process cards (contract)

One card per movement that **actually runs in the Phase 1 code**. Five exist: `capture`,
`infer`, `validate`, `sync`, `report`.

## Card shape

- **Input → Movement → Output** as the spine.
- **Movement** is numbered steps, each with a `path:line` citation. If you cannot cite a
  step, the step does not go in the card.
- **consumes** / **produces** are links to object cards, not prose.
- **Hits / Does not hit** closes the card.

## What does not belong here

- Steps that only exist in a design document. Verify against the code or leave them out.
- Phase 2 flows. If a flow is deferred, ghost it in one line — do not describe it as live.
- Duplicate coverage. If two cards would describe the same step, one owns it and the other
  links.
