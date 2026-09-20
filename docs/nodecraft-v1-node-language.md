# NodeCraft V1 Node Language Rules

Design freeze: player-facing and AI-facing **graph language** for ports, numeric types,
angles, and spatial concepts.

Last updated: 2026-09-21

## Authority

| Concern | Source of truth |
|---------|-----------------|
| Node id / category inventory | Code `@NodeInfo` + generated [`NODE_LIBRARY.md`](./NODE_LIBRARY.md) — see [`architecture/docs-authority.md`](./architecture/docs-authority.md) |
| Port connectability / conversion policy | [`type-conversion-guidelines.md`](./type-conversion-guidelines.md) + `TypeConversionRegistry` |
| **This document** | How new (and remediated) nodes express values: types, port ids, units, overrides |

If a node’s current implementation disagrees with this freeze, **treat the gap as debt to
remediate** (with graph/preset migration). Do not copy the gap into new nodes.

Related audits that motivated this freeze: first-pass review of Float Input, Float Slider,
Integer Slider, Angle Slider, Coordinate Input, Vector Input, World Plane, Selected Block.

---

## Five frozen rules

### 1. Continuous parameters use `DOUBLE`

Manual entry and sliders that express the same continuous number must share one data type.

| Role | Type |
|------|------|
| Continuous scalar (radius, distance, scale, generic float param) | `DOUBLE` |
| Discrete count / index / block integer | `INTEGER` |
| `FLOAT` | Compatibility / legacy only — do not introduce on new ordinary ports |

**Do not** ship pairs like:

- Float Input → `FLOAT`
- Float Slider → `DOUBLE`

UI style must not change data semantics.

### 2. Port IDs follow a strict convention

Display names may be short (`Value`, `X`). **Internal port ids must be stable and uniform.**

Canonical patterns:

| Kind | Port id |
|------|---------|
| Scalar value out | `output_value` |
| Scalar value in | `input_value` |
| Component in | `input_x`, `input_y`, `input_z` |
| Component out | `output_x`, `output_y`, `output_z` |
| Geometry | `input_geometry`, `output_geometry` |
| Point | `input_point`, `output_point` |
| Vector | `input_vector`, `output_vector` |
| Block position | `input_block_pos`, `output_block_pos` (or domain-specific `output_position` when the semantic is clearly block grid — prefer `block_pos` for new nodes) |

**Forbidden for new scalar outputs:** bare `value` when peers use `output_value`.

Renames require graph/preset **migration** (alias old id → new id). Never rely on
ad-hoc remap only in one tool (AI schema, quick connect, etc.).

### 3. Angles are degrees in the graph language

All geometry / transform rotation-style ports that take an angle treat the number as
**degrees** (`90` means 90°).

| Allowed | Not allowed |
|---------|-------------|
| Angle sources output degrees as `DOUBLE` | Hiding unit choice in a property while still typing the port as bare `DOUBLE` |
| Explicit nodes: Degrees→Radians / Radians→Degrees when math APIs need radians | Downstream guessing from a hidden `DEGREES` / `RADIANS` property |

Do **not** introduce `NodeDataType.ANGLE` in V1 unless a later design revisit proves degrees-as-DOUBLE insufficient.

Minecraft building language prefers 45 / 90 / 180 / 360.

### 4. Player spatial language is three layers

Players and AI should primarily reason about:

| Concept | Meaning | Typical use | Internal representation (target) |
|---------|---------|-------------|----------------------------------|
| **Point** | Continuous location `(10.5, 64.0, 20.25)` | curves, profiles, geometry, transform | `POINT` → `PointData` |
| **Vector** | Direction or displacement `(1, 0, 0)` | move, extrude, normal, axis | `VECTOR` → `Vector3d` |
| **Block Position** | Integer grid `(10, 64, 20)` | get/set block, selection, snap | `BLOCK_POS` → `BlockPos` |

Conversions must stay **explicit** where policy matters (corner vs center, snap):

```
Block Position  →  Block To Point  →  Point
Point           →  Snap To Block   →  Block Position
```

See [`type-conversion-guidelines.md`](./type-conversion-guidelines.md) for
`IMPLICIT_SAFE` vs `EXPLICIT_REQUIRED`.

**Deprecate expanding player-facing use of:**

| Type / label | Status |
|--------------|--------|
| `COORDINATE` | Semantic alias of block grid — prefer `BLOCK_POS` in new UI and ports |
| `POSITION` | Semantic alias of vector-like continuous xyz — prefer `VECTOR` or `POINT` by role |

Keep aliases in `TypeConversionRegistry` for compatibility; do not grow new player docs
or AI examples around Coordinate / Position as first-class concepts.

Nodes named like `point_from_coordinates` that only emit integer `BlockPos` should be
repositioned as **Block Position Input** (id/display), with a separate true **Point**
input node for continuous xyz when needed.

### 5. Property fallback + port override is one pattern

Parameter nodes resolve values as:

```
connected input port  (if present and valid)
        ↓ else
node property / UI field
```

When a port drives a field:

- UI must show the field as **driven by connection** (disabled or clearly non-editable).
- Do not silently ignore a live connection without visible source state.

**Reference implementation:** Vector Input (`input_x/y/z` + property fallback).

**Exceptions** (e.g. Selected Block: interactive pick vs coordinate inputs) must expose an
explicit **Source Mode** (and show the active source in the node UI). Do not leave wires
connected while a hidden priority makes them inert.

---

## Supporting norms

### Avoid `ANY` for spatial origins / points

Prefer a concrete type (`POINT`) plus registry conversions, not `ANY` + runtime
`resolvePoint()` catch-all. Runtime tolerance is not a substitute for graph language.

### Dead properties

`@NodeProperty` must only expose settings that change observable behavior or UI.
Properties wired to dead code (`if (false)`, unused flags) confuse players and pollute
AI schema — remove or re-enable, do not leave half-alive.

### Minecraft-first defaults that stay

Examples that remain correct under this freeze:

- World Plane default `XZ` (horizontal ground) — keep.
- Explicit Block↔Point conversion nodes — keep and prefer over silent policy.

---

## Known gaps (first-pass sample)

These violated the freeze at audit time. Batch A items below are remediated in code.

| Gap | Rule | Status |
|-----|------|--------|
| Float Input outputs `FLOAT`; Float Slider outputs `DOUBLE` | §1 | Fixed (both `DOUBLE`) |
| Integer Slider port id `value` vs peers’ `output_value` | §2 | Fixed + V1→V2 migration |
| Angle Slider unit property converts to radians while port stays `DOUBLE` | §3 | Fixed (degrees only; legacy `unit` ignored — pre-release, no old-graph preserve) |
| Coordinate Input: int xyz, dual Coordinate/Block Pos, no `input_x/y/z` | §4, §5 | Fixed → Block Position Input + overrides |
| World Plane Origin typed as `ANY` | Supporting norm | Fixed → `POINT`; Block→Point explicit; Position→Point implicit; Vector→Point explicit |
| Selected Block: pick silently overrides connected X/Y/Z | §5 | Fixed — Source Mode + Active Source |
| Selected Block Position / Center types | §4 | Fixed — `BLOCK_POS` / `POINT` |
| Float / Integer slider dead UI properties | Dead properties | Fixed |

---

## Remediation batches (implementation order)

Document first; code follows this order unless a dependency forces otherwise.

### Batch A — low risk, high leverage — **done (2026-09-21)**

1. Float Input → `DOUBLE` (fields + port).
2. Integer Slider `value` → `output_value` + graph format **V1→V2** migration.
3. Remove dead slider UI properties (`showMinMaxLabels` / `showSettingsPanel` / `showRangeInfo` / unused Float Input range+label flags).
4. Angle Slider: degrees-only graph output; unit switch removed. Legacy `unit` state is
   ignored (pre-release: no radians behavior-preserving migration). Use Degrees To Radians
   when new graphs need radians.

### Batch B — language alignment — **done (2026-09-21)**

5. Coordinate Input → **Block Position Input** (`reference.points.block_position`); `input_x/y/z` INTEGER overrides like Vector Input; graph format **V2→V3** type rename.
6. World Plane Origin: `ANY` → `POINT`. Continuous locations use legacy `POSITION → POINT`
   implicit (Player Position) or identical `POINT`. `BLOCK_POS → POINT` and `VECTOR → POINT`
   require explicit conversion so Block Position / Look Direction cannot silently become Origin.

### Batch C — composition semantics — **done (2026-09-21)**

7. Selected Block Source Mode (`Auto` / `Picked` / `Coordinates`) + visible **Active Source**.
   - Auto: complete X/Y/Z connections win over pick; otherwise pick; otherwise coordinate values.
   - Pick storage and coordinate-input storage are separate — switching mode does not silently discard the other.
   - UI shows mode combo, active source label, and warnings when the unused source still exists.

### Batch D — spatial / angle freeze closure — **done (2026-09-21)**

8. `TypeConversionRegistry`: `BLOCK_POS/COORDINATE → POINT` and `VECTOR → POINT` are
   `EXPLICIT_REQUIRED`; `POSITION → POINT` remains implicit.
9. Angle Slider: degrees-only; ignore legacy `unit` (no old-file radians migration in pre-release).
10. Selected Block: Position → `BLOCK_POS`, Center → `POINT`.
11. `COORDINATE` / `POSITION` marked `@Deprecated` on `NodeDataType` (aliases kept for in-repo wiring until ports migrate).

**Pre-release compatibility policy:** prefer clean language breaks and update in-repo presets /
tests. Do not add graph migrations solely to preserve abandoned on-disk semantic variants.

### Deferred (phase 2)

- Collapse Selected Block advanced outputs behind Block Info / Deconstruct patterns.
- Retire `COORDINATE` / `POSITION` enum values after ports stop using them.
- Optional editor Convert → Block To Point insert assist.
- Player Position → canonical `POINT` once Move Geometry placement presets are rewritten.

---

## Checklist for new nodes

Before merging a new or remodeled node:

- [ ] Continuous scalars are `DOUBLE`, not `FLOAT`.
- [ ] Port ids match the table in §2 (or an existing domain convention already frozen for that family).
- [ ] Angle ports document and emit **degrees**.
- [ ] Spatial ports use Point / Vector / Block Position intentionally; no new Coordinate/Position-first API.
- [ ] Optional drives use property fallback + connection override with clear UI.
- [ ] No `@NodeProperty` that cannot affect the node.
- [ ] Presets / migrations updated if port ids or types change.
- [ ] AI schema implications considered (one semantic type per player concept).
