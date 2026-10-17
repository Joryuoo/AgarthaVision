# docs/map/objects/ — object cards (contract)

One card per core domain noun: `Patient`, `Profile`, `Session`, `Sample`, `Prediction`,
`Detection`, `Finding`, `Report`, `StorageObject`, `PsgcBarangay`. `PsgcBarangay` is the only one
with no Supabase table — it earns a card because it is a real Room entity with a pinned external
vintage and a privacy rule attached. A new card is added to this list in the same change.

## Card shape — these sections, in this order

0. **Frontmatter** — `type: object`, `status`, `verified`, `commit`, `entity`. See `../CONTEXT.md`.

1. **One sentence** — what it is. Note product name vs. code/table name when they differ.
2. **Why this shape** — the load-bearing reason it looks like this. Not a field tour.
3. **Shape** — keys and constraints, each cited to `supabase/migrations/*.sql` by `path:line`,
   or to `schema.ts` and the Room entity by symbol (`../../CONTEXT.md` house rule 1).
4. **Connected to** — owns / owned by / joins / looks-like-but-is-not.
5. **If you change this** — as **Hits** and **Does not hit**, first-order only. "Does not
   hit" names the obvious wrong guess.
6. **Surfaces** — who reads and writes it: screens, sync, reports, or nothing.
7. **See** — the source file that owns the truth.

## What does not belong here

- A column-by-column transcription of the migration. Cite it instead.
- Behaviour. Behaviour is a process card.
- Fields invented for a future phase, unless explicitly labelled a ghost.
