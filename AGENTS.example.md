# Personal workspace rules — <Your Name>

Gitignored. This is the per-developer layer; project truth lives in `SESSION_INIT.md` and
`docs/`. Copy this file to `AGENTS.md` to customize your local setup.

---

## Project shelf — keep it current (every agent, every tool)

Keep this section in your copy. It is written for any coding agent and any member, and needs no
special skill or plugin.

- **Start at `SESSION_INIT.md`.** Open its routing table, then **one** file under `docs/`, then
  **one** card under `docs/map/`. Do not read all of `docs/`.
- **When you change code, update the card that describes it in the same change.** Find it from
  the routing table or `docs/map/effects/CONTEXT.md`. Then either:
  - re-check the card against the code and set its frontmatter `verified:` to today and
    `commit:` to the commit you checked, or
  - if you cannot re-check it, set `status: stale`.
- **Cite code by symbol, never by line:** `File.kt::functionName`. Only SQL migrations use
  `path:line`. Rules: `docs/CONTEXT.md`, "House rules".
- **The code wins.** If a card disagrees with the code, fix the card and say what was wrong in one
  line. Never change code to match a card.
- **New behaviour gets a home.** A new noun gets an object card, a new flow a process card, and
  both get a row in `docs/map/objects/CONTEXT.md` or `processes/CONTEXT.md` and, if it changes
  what something breaks, in `docs/map/effects/CONTEXT.md`. Copy the shape of a neighbouring card.

## Second brain / vault

- **Location:** `<Path to your Obsidian Vault>`
- **Purpose:** plans, architecture rationale, domain decisions, review notes — the *why*, and
  anything sensitive. Server topology lives here, never in the repo.
- **Entry point:** `<vault>/AGENTS.md` routes; `<vault>/CONTEXT.md` is the milestone pipeline.

## Vault conventions

- **Obsidian-native:** use `[[wikilinks]]` between notes.
- **Index registration:** register new root notes in `<vault>/AGENTS.md`.
- **Naming:** `kebab-case.md` or `SCREAMING-KEBAB.md`.
- **Dates:** absolute, e.g. `YYYY-MM-DD`.
- **Append, don't overwrite:** preserve history; add dated sections for updates.

## Local environment

- **OS / shell:** <Your OS> / <Your Shell>.
- **Local Postgres 17**, database name ends in `_dev`.
- Keep scripts cross-platform — TypeScript run by Bun, not `.sh` or `.ps1`.
- Never point a dev script at the server; it runs production workloads.
