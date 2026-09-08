# Personal workspace rules — <Your Name>

Gitignored. This is the per-developer layer; project truth lives in `SESSION_INIT.md` and
`docs/`. Copy this file to `AGENTS.md` to customize your local setup.

---

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
