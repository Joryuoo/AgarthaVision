# AGENTS

This file is the entry point for contributors and automated agents.

## Source of Truth

All project rules, standards, and implementation requirements live in:
- docs/00_PROJECT_OVERVIEW.md
- docs/01_ENVIRONMENT_SETUP.md
- docs/02_PROJECT_ARCHITECTURE.md
- docs/03_MOBILE_APP_PLAN.md
- docs/04_CLOUD_BACKEND_PLAN.md
- docs/05_DESIGN_SYSTEM_KOMOUI.md
- docs/06_GIT_WORKFLOW_AND_CI.md
- docs/07_TEAM_CONVENTIONS.md
- docs/components.md

Everything you do must align with those documents. If anything is missing or unclear, update the relevant doc first and use that as the authority for subsequent changes.

## Precedence Rules

When there is overlap or conflict between docs:
- docs/05_DESIGN_SYSTEM_KOMOUI.md is the definitive design system source of truth and explicitly supersedes docs/components.md.docs/components.md just supplements docs/05_DESIGN_SYSTEM_KOMOUI.md
- Otherwise, follow the most specific document for the task (for example, architecture rules come from docs/02_PROJECT_ARCHITECTURE.md, workflow rules from docs/06_GIT_WORKFLOW_AND_CI.md, and coding standards from docs/07_TEAM_CONVENTIONS.md).

## Required Compliance

You must follow all documented rules without exceptions. In particular:
- Architecture, layering, and package boundaries must match docs/02_PROJECT_ARCHITECTURE.md.
- Implementation sequencing, acceptance criteria, and feature scope must follow docs/03_MOBILE_APP_PLAN.md.
- UI design tokens, components, and styling rules must follow docs/05_DESIGN_SYSTEM_KOMOUI.md (not docs/components.md).
- Git branching, commit conventions, and CI expectations must follow docs/06_GIT_WORKFLOW_AND_CI.md.
- Kotlin style, naming, KDoc requirements, and testing standards must follow docs/07_TEAM_CONVENTIONS.md.
- Environment setup and tooling decisions must follow docs/01_ENVIRONMENT_SETUP.md.

## Non-negotiables

- Do not invent new conventions. If a rule is not in docs/, add it there first.
- Do not diverge from version catalog and Gradle guidance in docs/02_PROJECT_ARCHITECTURE.md and docs/01_ENVIRONMENT_SETUP.md.
- Do not hardcode UI colors or typography; use the tokens and typography from docs/05_DESIGN_SYSTEM_KOMOUI.md.
- Do not bypass commitlint or branch rules from docs/06_GIT_WORKFLOW_AND_CI.md.

## How to Work

1. Read the relevant file(s) in docs/ before making changes.
2. Implement exactly what the docs specify.
3. If you must deviate, propose the change by updating docs/ first, then implement.
