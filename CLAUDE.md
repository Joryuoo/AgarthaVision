# Working notes for Claude

Project docs are authoritative: `docs/constraints.md` (C1–C13),
`docs/non-negotiables.md`, `docs/CONTEXT.md`. This file holds only conventions that
those do not yet state, or that they state out of date.

## Icons — never generate glyph geometry

**Take icons from an existing set. Do not draw them, and do not hand-write SVG or
`ImageVector` path data.** (Ledon, Sprint 1.) Inventing path coordinates burns a large
amount of effort for a result that will not match its neighbours' stroke weight or
optical sizing anyway.

Two sources, and the split is by where the glyph appears:

- **House identity — bottom bar, brand marks:** a **Material Symbols (Rounded, fill 0)**
  export, added to `ui/icons/` as a Compose `ImageVector` alongside its neighbours.
  Export it from the Material Symbols site; the conversion is a paste, not a drawing.
- **In-screen affordances** (chevrons, back arrows, filter, flag): `material-icons-extended`
  directly, e.g. `Icons.Outlined.Inbox`. Already a dependency.

If the right export cannot be obtained in the moment, use an existing glyph from
`ui/icons/` as a placeholder and mark it, rather than drawing a new one.

**`docs/constraints.md` C11 (`:208-212`) is stale on this point.** It describes house
glyphs as "hand-authored 1.7-stroke outline drawables in `res/drawable/`", but
`res/drawable/` holds only `ic_launcher_*` and `ic_logo`, and `ui/icons/AgarthaIcons.kt`
states every UI glyph is a Material Symbols export. Follow the code and this note; C11's
wording needs a docs fix. The part of C11 that still holds is the *placement* rule — a
bottom-bar or brand glyph matches the house set, it does not come straight from
`Icons.*`.

## Commits

`[type][ClickUp-ID][Tabada]: Title`, lowercase type, enforced by `.husky/commit-msg`.
Sole author `kazuretsu <john.winston.tabada@gmail.com>` — **no `Co-Authored-By` and no
`Claude-Session` trailer.** Never put a model identifier in a commit message, PR body,
code comment, or any other pushed artifact.
