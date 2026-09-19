# Historical docs archive

These notes are **not** current project policy.

They capture one-off fix reports, preset conversion war stories, and status dumps
from earlier milestones. Prefer the living sources of truth below.

## Current sources of truth

| Topic | Location |
|-------|----------|
| Roadmap / phases | [`../development/advancement-0.7-to-0.8.md`](../development/advancement-0.7-to-0.8.md) |
| Architecture freezes | [`../architecture/`](../architecture/) |
| **Docs authority (ids / taxonomy)** | [`../architecture/docs-authority.md`](../architecture/docs-authority.md) |
| Contracts | [`../contracts/`](../contracts/) |
| Node library catalog | [`../NODE_LIBRARY.md`](../NODE_LIBRARY.md) (generated; prefer over hand-written node lists) |
| Migration commands | [`../../tools/migration/README.md`](../../tools/migration/README.md) |
| Contributing | [`../../CONTRIBUTING.md`](../../CONTRIBUTING.md) |

## What belongs here

- Root / docs `FINAL-*`, `FIXES-*`, `*-COMPLETE*`, dated “status” / “critical fixes” dumps
- One-shot preset conversion action plans and completion reports
- Mentions of deleted root scripts (`fix_node_ids.py`, `run_converter_final.bat`, …) — historical only; do not restore them to the repo root

## What does **not** belong here

- Active architecture / contract / advancement docs
- Generated `NODE_LIBRARY*.md`
- Runnable migration / converter scripts (those live under `tools/migration` + Gradle tasks)

## Demoted living drafts (still under `docs/`, not this folder)

These stay in `docs/` with supersession banners — **design history**, not implementation SoT
(see [`../architecture/docs-authority.md`](../architecture/docs-authority.md)):

- `node-id-guidelines.md`
- `nodecraft-v1-category-id-guidelines.md`
- `NodeCraft-v1.0-节点分类树-定稿.md`
- `nodecraft-v1-node-alias-plan.md` (migration seed only)
